package at.heinz.deviceai

import at.heinz.deviceai.briefing.ContinuumBriefingDirector
import at.heinz.deviceai.cognitive.InMemoryOwnerInitiativeStore
import at.heinz.deviceai.cognitive.InMemoryOwnerSharedWorkStore
import at.heinz.deviceai.cognitive.OwnerConversationTone
import at.heinz.deviceai.cognitive.OwnerInitiative
import at.heinz.deviceai.cognitive.OwnerInitiativeLevel
import at.heinz.deviceai.cognitive.OwnerSharedWorkRecord
import at.heinz.deviceai.cognitive.OwnerSmallTalk
import at.heinz.deviceai.cognitive.OwnerSuggestion
import at.heinz.deviceai.cognitive.OwnerSuggestionKind
import at.heinz.deviceai.cognitive.OwnerSuggestionProvider
import at.heinz.deviceai.cognitive.SharedWorkAction
import at.heinz.deviceai.cognitive.SharedWorkEntry
import at.heinz.deviceai.cognitive.SharedWorkKind
import at.heinz.deviceai.cognitive.SharedWorkState
import at.heinz.deviceai.cognitive.WorkRecall
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.File
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * The Human Dialogue Benchmark (experiment E3, `dialogue-benchmark.jsonl`): German situations of the owner's
 * conversation – greeting by situation, return with unfinished work, „Wo waren wir?“, small talk without claimed
 * feelings, corrections and interruptions, colloquial sentences, initiative per level, never inventing, and
 * repetition over sequences. Every situation runs against the productive deterministic code – [OwnerTalk] with the
 * real record, policy, parsers and wording – on a fake clock and fake stores, in the Activity's order of lanes
 * (open question of a lane → conversation manner → the work journal's own recall).
 *
 * The rows marked `"set": "heldout"` were written from the owner's wishes before the implementation existed and are
 * measured, not tuned: [KNOWN_HELDOUT_MISSES] pins what does not hold today. The development rows all hold. Besides
 * each row's own expectations, every sentence said in any row is checked for a claimed feeling.
 */
class DialogueBenchmarkTest {
    private val root: File = listOf("docs/evaluation/continuum-eval-v1", "../docs/evaluation/continuum-eval-v1")
        .map(::File).first { it.isDirectory }
    private val zone = ZoneId.of("Europe/Vienna")

    private fun at(iso: String): Long = LocalDateTime.parse(iso).atZone(zone).toInstant().toEpochMilli()

    private fun cases(): List<JSONObject> = File(root, "dialogue-benchmark.jsonl").readLines(Charsets.UTF_8)
        .filter { it.isNotBlank() }.map(::JSONObject)

    private class Step(val route: String, val text: String, val shape: String?, val offer: String?, val orders: List<String>)

    /** One situation's world: the productive [OwnerTalk] on a fake clock, with everything the Activity would lend it. */
    private inner class World(private val setup: JSONObject) : OwnerTalkHost {
        var now = at(setup.getString("nowIso"))
        var busy = setup.optBoolean("busy", false)
        var openQuestion = setup.optBoolean("openQuestion", false)
        var turn = 0L
        val said = ArrayList<String>()
        val orders = ArrayList<String>()
        val store = InMemoryOwnerTalkStore()
        val record = OwnerSharedWorkRecord(InMemoryOwnerSharedWorkStore()) { now }
        val initiative = OwnerInitiative(InMemoryOwnerInitiativeStore()) { now }
        val talk = OwnerTalk(this, store, record, initiative)
        val questions = OwnerOpenQuestions(session = { sessionId }, turn = { turn })
            .lane("LANE", awaiting = { openQuestion }) { openQuestion = false; acceptOwnerTurn(it); true }
        private var offeredOn: java.time.LocalDate? = null

        override val sessionId = "chat-1"
        override val assistantName: String? = "Jarvis"
        override fun nowMillis(): Long = now
        override fun zone(): ZoneId = this@DialogueBenchmarkTest.zone
        override fun acceptOwnerTurn(request: String) { turn++; talk.noteOwnerTurn(request) }
        override fun reply(request: String, text: String) { said += text }
        override fun greet(text: String) { said += text }
        override fun activity(line: String) {}
        override fun quiet(): Boolean = !busy && !openQuestion
        override fun confirmedFacts(): List<String> = emptyList()
        override fun submit(order: String, onHeldResult: Boolean) { orders += order }
        private val messages = setup.optInt("openMessages", 0)
        override fun openMessages(): OwnerOpenMessageCount? =
            if (setup.has("openMessages")) OwnerOpenMessageCount(messages, "m$messages") else null

        init {
            store.tone = OwnerConversationTone.valueOf(setup.optString("tone", "PERSONAL"))
            initiative.setLevel(OwnerInitiativeLevel.valueOf(setup.optString("level", "NORMAL")))
            store.returnGreeting = setup.optBoolean("greeting", true)
            if (!setup.has("name")) store.name = OwnerTalkName("Heinz", "Nenn mich Heinz", now - 90L * 24 * 3_600_000L)
            else if (!setup.isNull("name")) store.name = OwnerTalkName(setup.getString("name"), "Nenn mich " + setup.getString("name"), now)
            store.lastSeenMillis = if (setup.isNull("lastSeenIso")) 0L else at(setup.getString("lastSeenIso"))
            store.lastTurnMillis = when {
                !setup.has("lastTalkIso") -> store.lastSeenMillis
                setup.isNull("lastTalkIso") -> 0L
                else -> at(setup.getString("lastTalkIso"))
            }
            store.greetingCount = setup.optInt("greetingsBefore", 0)
            val work = setup.optJSONArray("sharedWork") ?: JSONArray()
            for (index in 0 until work.length()) {
                val row = work.getJSONObject(index)
                val ended = at(row.getString("endedIso"))
                record.note(SharedWorkEntry("e$index", SharedWorkKind.valueOf(row.getString("kind")),
                    SharedWorkAction.valueOf(row.getString("action")), row.optString("title"), SharedWorkState.valueOf(row.getString("state")),
                    if (row.has("startedIso")) at(row.getString("startedIso")) else ended, ended, row.optString("lane", "Test"),
                    resume = if (row.isNull("resume")) null else row.optString("resume").ifEmpty { null },
                    pausedAt = if (row.isNull("pausedAt")) null else row.optString("pausedAt").ifEmpty { null }))
            }
            // The briefing lane's offer: once per calendar day, as the lane keeps it.
            if (setup.optBoolean("briefingOffer", false)) talk.greetingExtra = {
                val today = java.time.Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
                if (offeredOn == today) null else { offeredOn = today; ContinuumBriefingDirector.OFFER_TEXT }
            }
            val providers = ArrayList<OwnerSuggestionProvider>()
            setup.optJSONObject("appointment")?.let { appointment ->
                providers += OwnerSuggestionProvider { time ->
                    OwnerContextSuggestions.appointment(appointment.getString("title"), at(appointment.getString("startIso")), time, zone)
                }
            }
            // A provider the app does not have yet (maps, calendar entry): the policy slot is exercised with a fake.
            setup.optJSONObject("fakeSuggestion")?.let { fake ->
                providers += OwnerSuggestionProvider {
                    OwnerSuggestion(OwnerSuggestionKind.valueOf(fake.getString("kind")), fake.getString("text"),
                        if (fake.isNull("order")) null else fake.optString("order").ifEmpty { null }, fake.optString("subject").ifEmpty { null })
                }
            }
            talk.contextSuggestions = providers
            val declined = setup.optJSONArray("declined") ?: JSONArray()
            for (index in 0 until declined.length()) initiative.declined(OwnerSuggestionKind.valueOf(declined.getString(index)))
        }

        fun step(step: JSONObject): Step {
            val minutes = step.optLong("afterMinutes", 0L)
            val event = step.optString("event")
            // Before a return he was away: the app went to the background at the moment he left.
            if (event == "return" && minutes > 0L) talk.onLeave()
            now += minutes * 60_000L
            val saidBefore = said.size
            val ordersBefore = orders.size
            var shape: String? = null
            var offer: String? = null
            fun text() = said.drop(saidBefore).joinToString(" ")
            val route = when {
                event == "return" -> {
                    talk.onReturn()
                    if (said.size > saidBefore) { shape = talk.lastGreetingShape; offer = talk.lastOfferKind?.name ?: "NONE"; "GREETING" } else "NO_GREETING"
                }
                event == "leave" -> { talk.onLeave(); "LEFT" }
                event == "ack" -> { said += talk.acknowledge(); "ACK" }
                event == "result" -> {
                    val result = step.getJSONObject("result")
                    val hint = if (result.getString("kind") == "RESEARCH") OwnerResultHint.Research(result.getInt("sources")) else OwnerResultHint.Other
                    if (talk.afterResult(hint)) { offer = talk.lastOfferKind?.name ?: "NONE"; "SUGGESTION" } else "NO_SUGGESTION"
                }
                event == "order" -> {
                    turn++
                    talk.noteOwnerTurn(step.getString("utterance"))
                    val clause = talk.returnClause(sessionId, step.optString("answer", "Erledigt."))
                    if (clause != null) { said += clause; "CLAUSE" } else "NO_CLAUSE"
                }
                else -> {
                    val utterance = step.getString("utterance")
                    when {
                        // The Activity's order: a lane's open question first, then the conversation manner.
                        questions.serve(utterance) -> "LANE_QUESTION"
                        talk.handle(utterance) -> {
                            val took = checkNotNull(talk.lastRoute)
                            if (took == OwnerTalkRoute.GREETING) { shape = talk.lastGreetingShape; offer = talk.lastOfferKind?.name ?: "NONE" }
                            if (took == OwnerTalkRoute.OFFER_ACCEPTED && orders.size == ordersBefore) error("accepted without an order")
                            took.name
                        }
                        // … and further down the work journal's own recall, here with an empty journal.
                        WorkRecall.parse(utterance) != null -> {
                            acceptOwnerTurn(utterance)
                            said += WorkRecall.answer(emptyList(), WorkRecall.parse(utterance)!!, now, java.util.TimeZone.getTimeZone(zone))
                            "JOURNAL_RECALL"
                        }
                        else -> { acceptOwnerTurn(utterance); "NOT_TAKEN" }
                    }
                }
            }
            return Step(route, text(), shape, offer, orders.drop(ordersBefore))
        }
    }

    private fun strings(value: JSONArray?): List<String> = (0 until (value?.length() ?: 0)).map { value!!.getString(it) }

    /** A question the owner has to answer: a sentence of three or more words that ends in „?“ („Na, Heinz?“ is a greeting tag). */
    private fun questions(text: String): Int = text.split(Regex("(?<=[.!?])\\s+")).count { sentence ->
        sentence.trim().endsWith("?") && sentence.trim().split(Regex("\\s+")).size >= 3
    }

    /** Null = the situation holds; otherwise what was different. */
    private fun verdict(case: JSONObject): String? {
        val world = World(case.getJSONObject("setup"))
        val steps: List<JSONObject> = case.optJSONArray("steps")?.let { rows -> (0 until rows.length()).map(rows::getJSONObject) }
            ?: listOf(JSONObject().apply {
                if (case.has("utterance")) put("utterance", case.getString("utterance"))
                if (case.has("event")) put("event", case.getString("event"))
                if (case.has("result")) put("result", case.getJSONObject("result"))
                if (case.has("answer")) put("answer", case.getString("answer"))
            })
        val expect = case.getJSONObject("expect")
        val results = ArrayList<Step>()
        for (step in steps) {
            val result = world.step(step)
            results += result
            if (step.has("expectRoute") && step.getString("expectRoute") != result.route)
                return "step ${results.size}: route ${result.route}, expected ${step.getString("expectRoute")}"
        }
        results.forEach { if (OwnerSmallTalk.claimsFeeling(it.text)) return "claims a feeling: „${it.text}“" }
        // What is judged is the app's last reaction; „leave“ is the owner going away, which nothing can answer.
        val last = results.lastOrNull { it.route != "LEFT" } ?: results.last()
        val route = expect.getString("route")
        if (last.route != route) return "route ${last.route}, expected $route · „${last.text}“"
        if (expect.has("offer") && (last.offer ?: "NONE") != expect.getString("offer")) return "offer ${last.offer}, expected ${expect.getString("offer")} · „${last.text}“"
        if (expect.has("order")) {
            if (expect.isNull("order")) { if (last.orders.isNotEmpty()) return "an order was handed over: ${last.orders}" }
            else if (last.orders != listOf(expect.getString("order"))) return "orders ${last.orders}, expected „${expect.getString("order")}“"
        }
        strings(expect.optJSONArray("must")).forEach { pattern ->
            if (!Regex(pattern, RegexOption.IGNORE_CASE).containsMatchIn(last.text)) return "missing /$pattern/ in „${last.text}“"
        }
        strings(expect.optJSONArray("mustNot")).forEach { pattern ->
            Regex(pattern, RegexOption.IGNORE_CASE).find(last.text)?.let { return "must not say „${it.value}“: „${last.text}“" }
        }
        if (expect.has("maxQuestions") && questions(last.text) > expect.getInt("maxQuestions"))
            return "${questions(last.text)} questions, at most ${expect.getInt("maxQuestions")}: „${last.text}“"
        if (expect.has("level") && world.initiative.level.name != expect.getString("level")) return "level ${world.initiative.level}, expected ${expect.getString("level")}"
        // Sequences: the steps that took the expected route, in order.
        val line = results.filter { it.route == route }
        if (expect.has("said") && line.size != expect.getInt("said")) return "${line.size} × $route, expected ${expect.getInt("said")}: ${results.map { it.route }}"
        if (expect.optBoolean("noImmediateRepeat")) line.zipWithNext().forEach { (first, second) ->
            if (first.text == second.text) return "said twice in a row: „${first.text}“"
        }
        if (expect.has("maxSameShape")) {
            var run = 0
            var before: String? = null
            line.forEach { step ->
                run = if (step.shape != null && step.shape == before) run + 1 else 1
                before = step.shape
                if (run > expect.getInt("maxSameShape")) return "the structure ${step.shape} $run times in a row"
            }
        }
        if (expect.has("minDistinct") && line.map { it.text }.toSet().size < expect.getInt("minDistinct"))
            return "${line.map { it.text }.toSet().size} different wordings, expected at least ${expect.getInt("minDistinct")}"
        return null
    }

    private fun misses(rows: List<JSONObject>): Map<String, String> {
        val misses = LinkedHashMap<String, String>()
        rows.forEach { case ->
            val result = runCatching { verdict(case) }.getOrElse { "ERROR ${it.javaClass.simpleName}: ${it.message}" }
            if (result != null) misses[case.getString("id")] = result
        }
        return misses
    }

    private fun table(title: String, rows: List<JSONObject>, misses: Map<String, String>): String = buildString {
        append("### ").append(title).append("\n\n| Kategorie | bestanden | gesamt |\n|---|---:|---:|\n")
        rows.groupBy { it.getString("category") }.toSortedMap().forEach { (category, list) ->
            append("| ").append(category).append(" | ").append(list.count { it.getString("id") !in misses }).append(" | ").append(list.size).append(" |\n")
        }
        append("| **Summe** | **").append(rows.size - misses.size).append("** | **").append(rows.size).append("** |\n")
        misses.forEach { append("\n- MISS ").append(it.key).append(": ").append(it.value) }
        append('\n')
    }

    private fun development() = cases().filter { it.optString("set") != "heldout" }
    private fun heldOut() = cases().filter { it.optString("set") == "heldout" }

    @Test fun theBenchmarkHasTheAgreedSizeInEveryCategory() {
        val all = cases()
        assertTrue("benchmark unexpectedly small: ${all.size}", all.size >= 220)
        assertTrue("held-out part unexpectedly small: ${heldOut().size}", heldOut().size >= 60)
        val ids = all.map { it.getString("id") }
        assertEquals(ids.size, ids.toSet().size)
        val wanted = mapOf("greeting" to 30, "unfinished" to 20, "recall" to 25, "smalltalk" to 20, "correction" to 25,
            "colloquial" to 30, "proactivity" to 30, "never_invent" to 20, "repetition" to 20)
        val have = all.groupingBy { it.getString("category") }.eachCount()
        wanted.forEach { (category, count) -> assertTrue("$category: ${have[category]} of $count", (have[category] ?: 0) >= count) }
        assertEquals(wanted.keys, have.keys)
    }

    @Test fun everyDevelopmentSituationHoldsOnTheRealCode() {
        val rows = development()
        val misses = misses(rows)
        val report = table("Entwicklungsteil", rows, misses)
        println(report)
        write("dialogue-benchmark-dev.md", report)
        if (misses.isNotEmpty()) fail("${misses.size} of ${rows.size} development situations failed:\n" +
            misses.entries.joinToString("\n") { "${it.key}: ${it.value}" })
    }

    /** Measured, not tuned: a new miss – or one that silently went away – fails this test, so the report stays true. */
    @Test fun theHeldOutPartKeepsItsMeasuredScore() {
        val rows = heldOut()
        val misses = misses(rows)
        val report = table("Held-out-Teil", rows, misses)
        println(report)
        write("dialogue-benchmark-heldout.md", report)
        assertEquals("held-out misses changed:\n" + misses.entries.joinToString("\n") { "${it.key}: ${it.value}" },
            KNOWN_HELDOUT_MISSES, misses.keys.toSet())
    }

    /** The two parts share no situation: same category, setup and steps never appear in both. */
    @Test fun theDevelopmentAndTheHeldOutPartShareNoSituation() {
        fun key(case: JSONObject) = listOf(case.getJSONObject("setup").toString(), case.optString("utterance"), case.optString("event"),
            case.optJSONArray("steps")?.toString().orEmpty(), case.optJSONObject("result")?.toString().orEmpty()).joinToString("|")
        val shared = development().map(::key).toSet() intersect heldOut().map(::key).toSet()
        assertTrue(shared.toString(), shared.isEmpty())
    }

    private fun write(name: String, text: String) {
        runCatching { File("build/reports/dialogue-benchmark").apply { mkdirs() }.resolve(name).writeText(text, Charsets.UTF_8) }
    }

    private companion object {
        /**
         * Held-out situations that do not hold today, with the reason in docs/research/DIALOG-BENCHMARK-20261119.md.
         * H-COL-09 („wo waren wir, öffne dann das pdf“): the row expects that nobody takes the sentence; the conversation
         * manner declines it as intended, but the work journal's own recall – which the row's author had not in mind –
         * takes the compound sentence (recall plus document request), as it did before. The row stays as written.
         */
        val KNOWN_HELDOUT_MISSES: Set<String> = setOf("H-COL-09")
    }
}
