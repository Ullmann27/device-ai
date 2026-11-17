package at.heinz.deviceai.evaluation

import at.heinz.deviceai.BootstrapBrain
import at.heinz.deviceai.cognitive.ContinuousConversationMode
import at.heinz.deviceai.cognitive.EvalProbe
import at.heinz.deviceai.notifications.AnnouncementDecision
import at.heinz.deviceai.notifications.AnnouncementMode
import at.heinz.deviceai.notifications.AnnouncementSettings
import at.heinz.deviceai.notifications.AnnouncementState
import at.heinz.deviceai.notifications.AppRule
import at.heinz.deviceai.notifications.IncomingNotification
import at.heinz.deviceai.notifications.NotificationAnnouncementPolicy
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.File
import java.security.MessageDigest
import java.time.Instant
import java.time.ZoneId

/**
 * docs/evaluation/continuum-eval-v1: the Continuum evaluation set.
 *
 * `regression.jsonl` is executed here against the real routing, object-memory, clock, recall and voice
 * command code – a failing line is a real regression. `model-dev.jsonl` and `model-holdout.jsonl` need
 * the on-device model and are NOT executed on the host; this test only guards their structure, the
 * category coverage, and that the holdout stays byte-identical and disjoint from the development set.
 */
class ContinuumEvalDatasetTest {
    private val root: File = listOf("docs/evaluation/continuum-eval-v1", "../docs/evaluation/continuum-eval-v1")
        .map(::File).first { it.isDirectory }

    private fun cases(name: String): List<JSONObject> = File(root, name).readLines(Charsets.UTF_8)
        .filter { it.isNotBlank() }.map(::JSONObject)

    private fun strings(json: JSONObject, key: String): List<String> =
        json.optJSONArray(key)?.let { array -> (0 until array.length()).map(array::getString) } ?: emptyList()

    @Test fun everyDeterministicRegressionCaseHoldsOnTheRealCode() {
        val failures = mutableListOf<String>()
        val all = cases("regression.jsonl")
        assertTrue("regression set unexpectedly small: ${all.size}", all.size >= 40)
        all.forEach { case ->
            val id = case.getString("id")
            val expect = case.getJSONObject("expect")
            fun check(ok: Boolean, message: String) { if (!ok) failures += "$id: $message" }
            fun text(answer: String?) {
                if (expect.optBoolean("abstain")) { check(answer == null, "expected abstention, got: $answer"); return }
                check(answer != null, "expected an answer, got none")
                strings(expect, "contains").forEach { check(answer?.contains(it) == true, "missing „$it“ in: $answer") }
                strings(expect, "not_contains").forEach { check(answer?.contains(it) != true, "forbidden „$it“ in: $answer") }
            }
            when (case.getString("runner")) {
                "route" -> {
                    val input = case.getString("input")
                    val computer = EvalProbe.computer(input)
                    if (!expect.isNull("computer")) check(computer == expect.getString("computer") ||
                        (expect.getString("computer") == "NOT_READY" && !computer.startsWith("READY:")),
                        "computer route $computer, expected ${expect.getString("computer")}")
                    strings(expect, "forbidden_apps").forEach {
                        check(!computer.startsWith("READY:$it:"), "forbidden app $it was selected ($computer)")
                    }
                    if (!computer.startsWith("READY:") && expect.has("tool")) {
                        val tool = BootstrapBrain.decide(input, emptyList()).tool
                        val wanted = if (expect.isNull("tool")) null else expect.getString("tool")
                        check(tool == wanted, "tool $tool, expected $wanted")
                    }
                }
                "object_state" -> text(EvalProbe.objectAnswer(strings(case, "turns"), case.getString("question")))
                "clock" -> text(EvalProbe.clock(case.getString("input"), Instant.parse(case.getString("now")),
                    ZoneId.of(case.getString("zone"))))
                "owner_recall" -> text(BootstrapBrain.decide(case.getString("input"), strings(case, "facts")).message)
                "voice_command" -> {
                    val command = ContinuousConversationMode.command(case.getString("input"))?.name
                    val wanted = if (expect.isNull("command")) null else expect.getString("command")
                    check(command == wanted, "voice command $command, expected $wanted")
                }
                else -> failures += "$id: unknown runner ${case.getString("runner")}"
            }
        }
        if (failures.isNotEmpty()) fail("${failures.size} of ${all.size} regression cases failed:\n" +
            failures.joinToString("\n"))
    }

    /**
     * `regression-voice.jsonl` (Code63 voice round): conversational register, end-of-turn detection, drive-mode
     * commands and notification announcements, executed against the productive classes.
     */
    @Test fun everyVoiceRegressionCaseHoldsOnTheRealCode() {
        val failures = mutableListOf<String>()
        val all = cases("regression-voice.jsonl")
        assertTrue("voice regression set unexpectedly small: ${all.size}", all.size >= 32)
        all.forEach { case ->
            val id = case.getString("id")
            val expect = case.getJSONObject("expect")
            fun check(ok: Boolean, message: String) { if (!ok) failures += "$id: $message" }
            when (case.getString("runner")) {
                "register" -> EvalProbe.register(case.getString("input")).let {
                    check(it == expect.getString("register"), "register $it, expected ${expect.getString("register")}")
                }
                "turn_completion" -> EvalProbe.unfinished(case.getString("input")).let {
                    check(it == expect.getBoolean("unfinished"), "unfinished=$it, expected ${expect.getBoolean("unfinished")}")
                }
                "drive_command" -> {
                    val wanted = if (expect.isNull("command")) null else expect.getString("command")
                    val command = EvalProbe.driveCommand(case.getString("input"))
                    check(command == wanted, "drive command $command, expected $wanted")
                }
                "notification" -> {
                    val mode = AnnouncementMode.valueOf(case.getString("mode"))
                    val decision = NotificationAnnouncementPolicy().decide(
                        IncomingNotification(key = id, packageName = case.getString("app"),
                            appLabel = case.getString("label"), title = case.getString("title"),
                            text = case.getString("text"), category = null, isOngoing = false,
                            isGroupSummary = false, postedAtMs = 0L, isOwnPackage = false),
                        AnnouncementSettings(enabled = true,
                            apps = mapOf(case.getString("app") to AppRule(mode)), quietWhenNotInConversation = false),
                        AnnouncementState(conversationActive = true, driveModeActive = false,
                            assistantSpeaking = false, listening = false, nowMs = 1_000L))
                    val spoken = (decision as? AnnouncementDecision.Announce)?.spokenText
                    check((spoken != null) == expect.getBoolean("announced"), "announced=${spoken != null}: $decision")
                    strings(expect, "contains").forEach { check(spoken?.contains(it) == true, "missing „$it“ in: $spoken") }
                    strings(expect, "not_contains").forEach { check(spoken?.contains(it) != true, "forbidden „$it“ in: $spoken") }
                }
                else -> failures += "$id: unknown runner ${case.getString("runner")}"
            }
        }
        if (failures.isNotEmpty()) fail("${failures.size} of ${all.size} voice regression cases failed:\n" +
            failures.joinToString("\n"))
    }

    @Test fun everyCaseHasAnIdACategoryAndAFormalCriterion() {
        val seen = mutableSetOf<String>()
        listOf("regression.jsonl", "regression-voice.jsonl", "model-dev.jsonl", "model-holdout.jsonl").forEach { file ->
            cases(file).forEach { case ->
                val id = case.getString("id")
                assertTrue("duplicate id $id", seen.add(id))
                assertTrue("$id: unknown category", case.getString("category") in CATEGORIES)
                val expect = case.getJSONObject("expect")
                if (file.startsWith("regression")) assertTrue("$id: runner", case.getString("runner") in RUNNERS)
                else {
                    assertTrue("$id: criterion", expect.getString("type") in CRITERIA)
                    val turns = case.getJSONArray("turns")
                    assertTrue("$id: turns", turns.length() >= 1)
                    assertEquals("$id: last turn must be the owner's", "user",
                        turns.getJSONObject(turns.length() - 1).getString("role"))
                    // A content criterion without expected values would accept any answer.
                    if (expect.getString("type") in setOf("contains_all", "contains_any", "numeric"))
                        assertTrue("$id: expected values", expect.getJSONArray("values").length() > 0)
                    if (expect.getString("type") == "tool_call") assertTrue("$id: tool", !expect.isNull("tool"))
                }
            }
        }
    }

    @Test fun everyRequiredCategoryIsCoveredInDevelopmentAndHoldout() {
        listOf("model-dev.jsonl", "model-holdout.jsonl").forEach { file ->
            val covered = cases(file).map { it.getString("category") }.toSet()
            assertEquals("$file misses categories", emptySet<String>(), CATEGORIES - covered)
        }
    }

    @Test fun theHoldoutIsPinnedAndDisjointFromTheDevelopmentSet() {
        val holdout = File(root, "model-holdout.jsonl")
        val digest = MessageDigest.getInstance("SHA-256").digest(holdout.readBytes())
            .joinToString("") { "%02x".format(it) }
        val pinned = File(root, "model-holdout.sha256").readText().trim().substringBefore(' ')
        assertEquals("The holdout changed. A changed holdout invalidates earlier comparisons: version it " +
            "(continuum-eval-v2) instead of editing it.", pinned, digest)
        fun ownerTexts(file: String) = cases(file).flatMap { case ->
            val turns = case.getJSONArray("turns")
            (0 until turns.length()).map(turns::getJSONObject).filter { it.getString("role") == "user" }
                .map { it.getString("text").lowercase().replace(Regex("[^\\p{L}\\p{N}]+"), " ").trim() }
        }.filter { it.length >= 12 }.toSet()
        val shared = ownerTexts("model-dev.jsonl") intersect ownerTexts("model-holdout.jsonl")
        assertEquals("owner utterances shared by development and holdout set", emptySet<String>(), shared)
    }

    private companion object {
        val CATEGORIES = setOf("long_conversation", "dialect", "ambiguity", "pronoun", "back_reference", "correction",
            "negation", "temporal", "multi_step", "math", "document", "tool_choice", "interruption",
            "incomplete_info", "uncertainty_abstention", "contradiction", "abort_resume")
        val RUNNERS = setOf("route", "object_state", "clock", "owner_recall", "voice_command",
            "register", "turn_completion", "drive_command", "notification")
        val CRITERIA = setOf("contains_all", "contains_any", "numeric", "abstain", "asks_back", "tool_call", "no_tool")
    }
}
