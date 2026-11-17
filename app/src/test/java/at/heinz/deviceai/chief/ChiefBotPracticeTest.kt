package at.heinz.deviceai.chief

import org.junit.Assert.*
import org.junit.Test

/** Developer contract checks with literal response fixtures; no local model is called or trained. */
class ChiefBotPracticeTest {
    private fun exercise(expected: Map<String, String> = linkedMapOf("decision" to "KEEP", "count" to "2")) =
        ChiefBotPracticeExercise("developer-contract-v1", "bot-kora", "Begrenzter Vergleich",
            "Gib decision und count als Strings zurück.", expected, "CORRECTION_ONLY_TEXT")

    private fun rejects(block: () -> Unit) {
        try { block(); fail("Expected a rejected contract") }
        catch (_: IllegalArgumentException) {}
        catch (_: java.nio.charset.CharacterCodingException) {}
    }

    @Test fun onlyExplicitWholeCommandsSelectTheNamedBotsCurriculum() {
        for (agent in listOf("bot-kora", "bot-link", "bot-iris")) {
            assertTrue(ChiefBotPractice.supports(agent))
            val first = requireNotNull(ChiefBotPractice.select(agent, "Training"))
            assertEquals(agent, first.agentId)
            assertEquals(first.id, ChiefBotPractice.select(agent, "Übung 1")!!.id)
            assertEquals(first.id, ChiefBotPractice.select(agent, "  TRAINING 1  ")!!.id)
            assertTrue(ChiefBotPractice.select(agent, "Training 6")!!.transfer)
            for (ordinary in listOf("Erkläre Training 1", "Training 1 und ändere Dateien", "Training 1\nTraining 2", "Training1"))
                assertNull(ChiefBotPractice.select(agent, ordinary))
        }
        assertFalse(ChiefBotPractice.supports("bot-forge"))
        assertNull(ChiefBotPractice.select("bot-forge", "Training 1"))
        assertFalse(ChiefBotPractice.supports("Kora"))
    }

    @Test fun invalidExerciseNumbersCannotSilentlySelectAnotherTask() {
        for (request in listOf("Training 0", "Training 7", "Übung 999999999999999999999999999999"))
            rejects { ChiefBotPractice.select("bot-kora", request) }
    }

    @Test fun expectedAnswersAreImmutableAndAbsentFromTheModelPrompt() {
        val mutable = linkedMapOf("decision" to "ORACLE_ONLY_VALUE")
        val task = exercise(mutable)
        mutable["decision"] = "MUTATED"
        assertEquals("ORACLE_ONLY_VALUE", task.expected["decision"])
        try { (task.expected as MutableMap<String, String>)["decision"] = "MUTATED"; fail("Mutable oracle") }
        catch (_: UnsupportedOperationException) {}
        val prompt = ChiefBotPractice.promptFor(task)
        assertTrue(prompt.contains(task.prompt))
        assertTrue(prompt.contains("decision"))
        assertFalse(prompt.contains("ORACLE_ONLY_VALUE"))
        assertFalse(prompt.contains(task.explanation))
    }

    @Test fun exactStringValuesAllowJsonWhitespaceKeyOrderAndValidEscapes() {
        val answer = " \n{\"count\":\"2\",\"decision\":\"\\u004bEEP\"}\t"
        val feedback = ChiefBotPractice.evaluate(exercise(), answer)
        assertEquals(ChiefBotPracticeOutcome.CORRECT, feedback.outcome)
        assertTrue(feedback.matchesResponse(answer))
        assertFalse("The receipt binds the original bytes, not a rewritten answer", feedback.matchesResponse(answer.trim()))
    }

    @Test fun differentStringValuesFailWithoutNormalizationOrPartialCredit() {
        for (answer in listOf("""{"decision":"keep","count":"2"}""",
            """{"decision":"KEEP ","count":"2"}""", """{"decision":"KEEP","count":"02"}"""))
            assertEquals(ChiefBotPracticeOutcome.INCORRECT, ChiefBotPractice.evaluate(exercise(), answer).outcome)
    }

    @Test fun missingExtraOrNonStringFieldsAreInvalidIncludingToolProposals() {
        val invalid = listOf(
            """{"decision":"KEEP"}""", """{"decision":"KEEP","count":"2","tool":"shell"}""",
            """{"decision":"KEEP","count":2}""", """{"decision":"KEEP","count":true}""",
            """{"decision":"KEEP","count":null}""", """{"decision":"KEEP","count":{"value":"2"}}""",
            """{"decision":"KEEP","count":["2"]}""", "[]", "true", "null"
        )
        invalid.forEach { answer -> assertEquals(answer, ChiefBotPracticeOutcome.INVALID_RESPONSE,
            ChiefBotPractice.evaluate(exercise(), answer).outcome) }
    }

    @Test fun malformedJsonDuplicateKeysAndEveryTrailingPayloadAreRejected() {
        val valid = """{"decision":"KEEP","count":"2"}"""
        val invalid = listOf(
            "$valid suffix", "$valid {}", "```json\n$valid\n```", "Antwort: $valid",
            """{"decision":"KEEP","count":"1","count":"2"}""",
            "{'decision':'KEEP','count':'2'}", """{"decision":"KEEP","count":"2",}""",
            """{/* comment */"decision":"KEEP","count":"2"}""", "", "   ",
            """{"decision":"KEEP","count":"\uD800"}"""
        )
        invalid.forEach { answer -> assertEquals(answer, ChiefBotPracticeOutcome.INVALID_RESPONSE,
            ChiefBotPractice.evaluate(exercise(), answer).outcome) }
    }

    @Test fun exerciseAndAnswerBudgetsAreCheckedBeforeFeedbackIsCreated() {
        rejects { exercise((0..8).associate { "key_$it" to "value" }) }
        rejects { exercise(mapOf("Tool Name" to "value")) }
        rejects { exercise(mapOf("answer" to "x".repeat(161))) }
        rejects { ChiefBotPracticeExercise("x", "bot-kora", "Title", "ä".repeat(1201), mapOf("x" to "y"), "Reason") }
        rejects { ChiefBotPractice.evaluate(exercise(), "x".repeat(6001)) }
        rejects { ChiefBotPractice.evaluate(exercise(), "ä".repeat(3001)) }
        rejects { ChiefBotPractice.evaluate(exercise(), "\uD800") }
    }

    @Test fun feedbackRemainsASeparateBoundedCorrectionForOneResponse() {
        val task = exercise()
        val answer = """{"decision":"DROP","count":"2"}"""
        val feedback = ChiefBotPractice.evaluate(task, answer)
        assertEquals(task.id, feedback.exerciseId)
        assertEquals(task.agentId, feedback.agentId)
        assertTrue(feedback.presentation().contains(task.explanation))
        assertTrue(feedback.presentation().contains("Sollantwort"))
        assertTrue(feedback.textBytes <= ChiefBotPracticeFeedback.MAX_TEXT_BYTES)
        assertTrue(feedback.matchesResponse(answer))
        assertFalse(feedback.copy(outcome = ChiefBotPracticeOutcome.CORRECT).matchesResponse(answer))
    }
}
