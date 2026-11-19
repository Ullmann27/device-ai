package at.heinz.deviceai.gmail

import at.heinz.deviceai.briefing.ContinuumBriefingCommands
import at.heinz.deviceai.cognitive.OwnerExportCommand
import at.heinz.deviceai.cognitive.OwnerLibraryCommand
import at.heinz.deviceai.cognitive.OwnerNewsCommand
import at.heinz.deviceai.cognitive.OwnerShareCommand
import at.heinz.deviceai.sending.OwnerSendIntentParser
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.File

/**
 * `regression-mail.jsonl` (development set) and `regression-mail-check.jsonl` (held-out check set) of the Gmail lane:
 * which sentence – in which open mail context – is an order of this lane, and which keeps its present route (`NONE`).
 * Both files were committed before the parser. A case with `keeps` also asserts that the named lane really takes it.
 * The check set is measured, not tuned: its misses are pinned in [KNOWN_CHECK_MISSES].
 */
class OwnerMailRegressionTest {
    private val root: File = listOf("docs/evaluation/continuum-eval-v1", "../docs/evaluation/continuum-eval-v1")
        .map(::File).first { it.isDirectory }

    private fun cases(name: String): List<JSONObject> = File(root, name).readLines(Charsets.UTF_8)
        .filter { it.isNotBlank() }.map(::JSONObject)

    private fun route(input: String, context: String): String =
        OwnerMailCommands.route(input, "Jarvis", MailContext.of(context))?.code ?: "NONE"

    private fun kept(lane: String, input: String): Boolean = when (lane) {
        "SEND" -> OwnerSendIntentParser.parse(input, "Jarvis") != null
        "SHARE" -> OwnerShareCommand.parse(input, "Jarvis") != null
        "DESK" -> OwnerLibraryCommand.parseOpen(input, "Jarvis") != null
        "EXPORT" -> (OwnerExportCommand.parse(input, "Jarvis") ?: OwnerExportCommand.parseMake(input, "Jarvis")) != null
        "BRIEFING" -> ContinuumBriefingCommands.parse(input, "Jarvis") != null
        "NEWS" -> OwnerNewsCommand.parse(input, "Jarvis") != null
        else -> error("unknown lane $lane")
    }

    private fun misses(name: String): Map<String, String> {
        val all = cases(name)
        val misses = LinkedHashMap<String, String>()
        all.forEach { case ->
            assertEquals("mail_route", case.getString("runner"))
            val input = case.getString("input")
            val got = route(input, case.getString("context"))
            val wanted = case.getJSONObject("expect").getString("route")
            if (got != wanted) misses[case.getString("id")] = "got $got, expected $wanted · „$input“ [${case.getString("context")}]"
            case.optString("keeps").takeIf { it.isNotEmpty() }?.let { lane ->
                if (!kept(lane, input)) misses[case.getString("id") + "/keeps"] = "$lane does not take „$input“"
            }
        }
        println("$name: ${all.size - misses.size} / ${all.size} cases hold")
        return misses
    }

    @Test fun everyDevelopmentCaseHoldsOnTheRealParser() {
        val total = cases("regression-mail.jsonl").size
        assertTrue("development set unexpectedly small: $total", total >= 120)
        val misses = misses("regression-mail.jsonl")
        if (misses.isNotEmpty()) fail("${misses.size} of $total development cases failed:\n" + misses.entries.joinToString("\n") { "${it.key}: ${it.value}" })
    }

    @Test fun theCheckSetScoreIsPinnedAsItIs() {
        val total = cases("regression-mail-check.jsonl").size
        assertTrue("check set unexpectedly small: $total", total >= 60)
        val misses = misses("regression-mail-check.jsonl")
        assertEquals("check set misses changed:\n" + misses.entries.joinToString("\n") { "${it.key}: ${it.value}" },
            KNOWN_CHECK_MISSES, misses.keys)
    }

    private val SHARED_WITH_THE_COMPUTER_LANE = setOf("regression-computer-check.jsonl:COMC-012", "regression-computer.jsonl:COM-020")

    /** Sentences of the other sets do not become mail orders (no open mail context, as in every other lane's chat). */
    @Test fun noUtteranceOfTheOtherSetsBecomesAMailOrder() {
        val moved = mutableListOf<String>()
        var checked = 0
        fun check(source: String, utterance: String) {
            checked++
            // „Suche in Gmail nach …“ is shared on purpose: with a connected account the API lane answers it, without one
            // it stays with the computer lane (OwnerMailDirector.handles). The computer sets pin that second half.
            if (source in SHARED_WITH_THE_COMPUTER_LANE) return
            OwnerMailCommands.standalone(utterance, "Jarvis")?.let { moved += "$source „$utterance“ → ${it.code}" }
        }
        root.listFiles { file -> file.name.endsWith(".jsonl") && !file.name.startsWith("regression-mail") }!!.sortedBy { it.name }.forEach { file ->
            cases(file.name).forEach { case ->
                val id = file.name + ":" + case.optString("id")
                listOf("input", "question", "utterance", "request").forEach { key ->
                    (case.opt(key) as? String)?.takeIf { it.isNotEmpty() }?.let { check(id, it) }
                }
                case.optJSONArray("turns")?.let { turns ->
                    (0 until turns.length()).forEach { index ->
                        when (val turn = turns.get(index)) {
                            is String -> check(id, turn)
                            is JSONObject -> if (turn.optString("role", "user") == "user") turn.optString("text").takeIf { it.isNotEmpty() }?.let { check(id, it) }
                        }
                    }
                }
            }
        }
        assertTrue("too few utterances checked: $checked", checked >= 500)
        assertEquals("existing utterances that would become a mail order:\n" + moved.joinToString("\n"),
            KNOWN_MOVED_UTTERANCES, moved.map { it.substringBefore(' ') }.toSet())
    }

    @Test fun theDevelopmentAndTheCheckSetShareNoCase() {
        fun keys(name: String) = cases(name).map { it.getString("context") + "|" + it.getString("input").trim().lowercase() }
        val development = keys("regression-mail.jsonl")
        val check = keys("regression-mail-check.jsonl")
        assertEquals(development.size, development.toSet().size)
        assertEquals(check.size, check.toSet().size)
        assertTrue((development.toSet() intersect check.toSet()).toString(), (development.toSet() intersect check.toSet()).isEmpty())
        val ids = (cases("regression-mail.jsonl") + cases("regression-mail-check.jsonl")).map { it.getString("id") }
        assertEquals(ids.size, ids.toSet().size)
    }

    private companion object {
        /**
         * Measured, not tuned: a „dass“ clause with a separable verb („dass ich morgen anrufe“) is not turned into direct
         * speech (IndirectSpeech of the send lane refuses those on purpose); the draft keeps the owner's word order and
         * the owner sees the full text before anything is sent.
         */
        val KNOWN_CHECK_MISSES: Set<String> = setOf("MAILC-061", "MAILC-063")
        val KNOWN_MOVED_UTTERANCES: Set<String> = emptySet()
    }
}
