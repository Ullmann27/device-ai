package at.heinz.deviceai.chief

import at.heinz.deviceai.learning.StrictJson
import org.json.JSONArray
import org.json.JSONObject
import java.nio.CharBuffer
import java.nio.charset.CodingErrorAction
import java.security.MessageDigest
import java.util.Collections

/** One repository-defined exercise. Expected answers never belong to its model prompt. */
class ChiefBotPracticeExercise(
    val id: String,
    val agentId: String,
    val title: String,
    val prompt: String,
    expected: Map<String, String>,
    val explanation: String,
    val transfer: Boolean = false
) {
    val expected: Map<String, String> = Collections.unmodifiableMap(LinkedHashMap(expected))

    init {
        require(id.matches(Regex("[a-z0-9][a-z0-9._:-]{0,79}"))) { "BOT_PRACTICE_ID_INVALID" }
        require(ChiefBotPractice.supports(agentId)) { "BOT_PRACTICE_AGENT_UNSUPPORTED" }
        require(title.isNotBlank() && title.length <= 160 && title.none { it.isISOControl() })
        practiceTextBytes(title, 640)
        require(prompt.isNotBlank() && explanation.isNotBlank())
        practiceTextBytes(prompt, 2_400)
        practiceTextBytes(explanation, 1_200)
        ChiefBotPractice.validateExpected(this.expected)
        val expectedText = ChiefBotPractice.expectedJson(this.expected)
        practiceTextBytes(expectedText, ChiefBotPractice.MAX_EXPECTED_BYTES)
        val feedbackBytes = listOf(id, agentId, title, expectedText, explanation)
            .sumOf { practiceTextBytes(it, ChiefBotPracticeFeedback.MAX_TEXT_BYTES) } +
            ChiefBotPracticeOutcome.INVALID_RESPONSE.name.length + 64 + 1
        require(feedbackBytes <= ChiefBotPracticeFeedback.MAX_TEXT_BYTES) { "BOT_PRACTICE_FEEDBACK_TOO_LARGE" }
    }
}

enum class ChiefBotPracticeOutcome { CORRECT, INCORRECT, INVALID_RESPONSE }

/** Local exercise feedback, not model-authored text, owner knowledge or a verified capability. */
data class ChiefBotPracticeFeedback internal constructor(
    val exerciseId: String,
    val agentId: String,
    val title: String,
    val transfer: Boolean,
    val outcome: ChiefBotPracticeOutcome,
    val responseSha256: String,
    val expectedJson: String,
    val explanation: String
) {
    private val expected = ChiefBotPractice.readExpected(expectedJson)

    init {
        require(exerciseId.matches(Regex("[a-z0-9][a-z0-9._:-]{0,79}")))
        require(ChiefBotPractice.supports(agentId))
        require(title.isNotBlank() && title.length <= 160 && title.none { it.isISOControl() })
        practiceTextBytes(title, 640)
        require(responseSha256.matches(Regex("[a-f0-9]{64}")))
        require(explanation.isNotBlank())
        practiceTextBytes(explanation, 1_200)
        require(textBytes <= MAX_TEXT_BYTES) { "BOT_PRACTICE_FEEDBACK_TOO_LARGE" }
    }

    internal val textBytes: Int get() = listOf(exerciseId, agentId, title, outcome.name,
        responseSha256, expectedJson, explanation).sumOf { practiceTextBytes(it, MAX_TEXT_BYTES) } + 1

    internal fun matchesResponse(response: String): Boolean =
        responseSha256 == ChiefBotPractice.responseHash(response) &&
            outcome == ChiefBotPractice.classifyResponse(response, expected)

    fun presentation(): String = buildString {
        append(if (transfer) "Transferaufgabe" else "Fachübung").append(" · ").append(title).append('\n')
        append(when (outcome) {
            ChiefBotPracticeOutcome.CORRECT -> "Die Antwort stimmt mit der festgelegten Übungsregel überein."
            ChiefBotPracticeOutcome.INCORRECT -> "Die Antwort weicht von der festgelegten Übungsregel ab."
            ChiefBotPracticeOutcome.INVALID_RESPONSE ->
                "Das Antwortformat ist ungültig. Erwartet wird genau ein JSON-Objekt mit den geforderten Stringfeldern."
        })
        append("\nSollantwort: ").append(expectedJson)
        append('\n').append(explanation)
        append("\nPrivate Übungsrückmeldung; sie bewertet diese Antwort, keine allgemeine Fähigkeit.")
    }

    companion object { const val MAX_TEXT_BYTES = 4_096 }
}

/** Explicit selection and deterministic feedback only. No scheduler, inference, tools or weight updates. */
object ChiefBotPractice {
    internal const val MAX_EXPECTED_BYTES = 2_400
    private const val MAX_RESPONSE_BYTES = 6_000
    private val command = Regex("^(?:training|übung)(?:[ \\t]+([0-9]+))?$", RegexOption.IGNORE_CASE)
    private val key = Regex("[a-z][a-z0-9_]{0,39}")

    /** UI capability hint; this does not build a curriculum or execute any exercise oracle. */
    fun supports(agentId: String): Boolean = agentId == "bot-kora" || agentId == "bot-link" || agentId == "bot-iris"

    fun select(agentId: String, request: String): ChiefBotPracticeExercise? {
        if (!supports(agentId)) return null
        val match = command.matchEntire(request.trim()) ?: return null
        val number = match.groupValues[1].takeIf(String::isNotEmpty)?.toIntOrNull()
            ?: if (match.groupValues[1].isEmpty()) 1 else throw IllegalArgumentException("BOT_PRACTICE_NUMBER_OUT_OF_RANGE")
        val exercises = when (agentId) {
            "bot-iris" -> ChiefAvatarPracticeCurriculum.exercises()
            else -> ChiefEngineeringPracticeCurriculum.exercises(agentId)
        }
        check(exercises.size in 1..16 && exercises.all { it.agentId == agentId } &&
            exercises.map { it.id }.distinct().size == exercises.size) { "BOT_PRACTICE_CURRICULUM_INVALID" }
        require(number in 1..exercises.size) { "BOT_PRACTICE_NUMBER_OUT_OF_RANGE" }
        return exercises[number - 1]
    }

    /** The public problem and required keys only; the expected map and correction remain outside inference. */
    fun promptFor(exercise: ChiefBotPracticeExercise): String {
        val data = JSONObject().put("exerciseId", exercise.id).put("title", exercise.title)
            .put("task", exercise.prompt).put("requiredKeys", JSONArray(exercise.expected.keys.sorted()))
            .put("transfer", exercise.transfer)
        return "\nBOT_PRACTICE_TASK_JSON — ausdrücklich angeforderte lokale Fachübung:\n$data\n" +
            "Bearbeite nur diese Aufgabe. Antworte ausschließlich mit genau einem JSON-Flachobjekt: " +
            "genau die geforderten Schlüssel, ausschließlich Stringwerte. Keine Einleitung, Codeblöcke, " +
            "Werkzeugaufrufe, Aktionen oder Zeichen nach dem JSON-Objekt.\nEND_BOT_PRACTICE_TASK\n"
    }

    /** Called for a real terminal answer by the existing conversation completion path. */
    fun evaluate(exercise: ChiefBotPracticeExercise, response: String): ChiefBotPracticeFeedback {
        practiceTextBytes(response, MAX_RESPONSE_BYTES)
        return ChiefBotPracticeFeedback(exercise.id, exercise.agentId, exercise.title, exercise.transfer,
            classifyResponse(response, exercise.expected), responseHash(response),
            expectedJson(exercise.expected), exercise.explanation)
    }

    internal fun classifyResponse(response: String, expected: Map<String, String>): ChiefBotPracticeOutcome {
        val parsed = try {
            practiceTextBytes(response, MAX_RESPONSE_BYTES)
            StrictJson.parse(response, maxChars = MAX_RESPONSE_BYTES)
        } catch (_: IllegalArgumentException) {
            return ChiefBotPracticeOutcome.INVALID_RESPONSE
        } catch (_: java.nio.charset.CharacterCodingException) {
            return ChiefBotPracticeOutcome.INVALID_RESPONSE
        }
        if (parsed !is Map<*, *> || parsed.keys != expected.keys || parsed.values.any { it !is String })
            return ChiefBotPracticeOutcome.INVALID_RESPONSE
        return if (expected.all { (name, value) -> parsed[name] == value }) ChiefBotPracticeOutcome.CORRECT
            else ChiefBotPracticeOutcome.INCORRECT
    }

    internal fun validateExpected(expected: Map<String, String>) {
        require(expected.size in 1..8 && expected.keys.all { key.matches(it) }) { "BOT_PRACTICE_EXPECTED_KEYS_INVALID" }
        require(expected.values.all { it.isNotEmpty() && it.length <= 160 && it.none { c -> c.isISOControl() } }) {
            "BOT_PRACTICE_EXPECTED_VALUES_INVALID"
        }
        expected.values.forEach { practiceTextBytes(it, 640) }
    }

    internal fun expectedJson(expected: Map<String, String>): String = JSONObject().also { data ->
        expected.toSortedMap().forEach { (name, value) -> data.put(name, value) }
    }.toString()

    internal fun readExpected(json: String): Map<String, String> {
        practiceTextBytes(json, MAX_EXPECTED_BYTES)
        val parsed = StrictJson.parse(json, maxChars = MAX_EXPECTED_BYTES)
        require(parsed is Map<*, *> && parsed.keys.all { it is String } && parsed.values.all { it is String })
        @Suppress("UNCHECKED_CAST")
        val expected = parsed as Map<String, String>
        validateExpected(expected)
        return Collections.unmodifiableMap(LinkedHashMap(expected))
    }

    internal fun responseHash(response: String): String = MessageDigest.getInstance("SHA-256")
        .digest(response.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it.toInt() and 0xff) }
}

private fun practiceTextBytes(text: String, maximum: Int): Int {
    require(text.length <= maximum) { "BOT_PRACTICE_TEXT_TOO_LARGE" }
    val size = Charsets.UTF_8.newEncoder().onMalformedInput(CodingErrorAction.REPORT)
        .onUnmappableCharacter(CodingErrorAction.REPORT).encode(CharBuffer.wrap(text)).remaining()
    require(size <= maximum) { "BOT_PRACTICE_TEXT_TOO_LARGE" }
    return size
}
