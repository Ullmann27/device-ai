package at.heinz.deviceai.cognitive

import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class AiContextRedactionReuseTest {
    private val policy = AiContextPolicy(includeToolCatalog = false)

    private fun body(context: CompiledAiContext): String = context.text.substringAfter("## USER REQUEST\n")

    @Test
    fun assignmentsBearerThresholdAndNonMatchesKeepTheirExactText() {
        val keys = listOf("password", "PaSsWd", "token", "Secret", "credential", "Authorization",
            "ApiKey", "api_key", "api-key")
        val assignments = keys.joinToString("\n") { "$it \t: synthetic_value" }
        val tail = "\nxPassword=visible tokenized=visible\nBearer 1234567\nbeARer 12345678!"
        val result = AiContextCompiler(policy, { 100L })
            .compile(ContextIntegrityFixture.input(assignments + tail))

        assertEquals(keys.joinToString("\n") { "$it=[REDACTED]" } +
            "\nxPassword=visible tokenized=visible\nBearer 1234567\nBearer [REDACTED]!", body(result))
    }

    @Test
    fun assignmentReplacementStillPrecedesBearerReplacement() {
        val input = ContextIntegrityFixture.input("Maren authorization=Bearer abcdefgh\nNicht senden.")
        val result = AiContextCompiler(policy, { 100L }).compile(input)

        // Existing rule order consumes the assignment value "Bearer" first.
        assertEquals("Maren authorization=[REDACTED] abcdefgh\nNicht senden.", body(result))
    }

    @Test
    fun unicodeAndUnpairedSurrogatesOutsideMatchesRemainIntact() {
        val raw = "Å🧠\uD800 token=synthetic_value \uDC00 中 e\u0301\nBearer 12345678\uD800"
        val expected = "Å🧠\uD800 token=[REDACTED] \uDC00 中 e\u0301\nBearer [REDACTED]\uD800"
        val result = AiContextCompiler(policy, { 100L }).compile(ContextIntegrityFixture.input(raw))

        assertEquals(expected, body(result))
        assertTrue(expected.toByteArray(Charsets.UTF_8).contentEquals(body(result).toByteArray(Charsets.UTF_8)))
    }

    @Test
    fun reusedAndFreshInstancesDoNotKeepEarlierRequestOrFailureState() {
        val smallPolicy = policy.copy(maxBytes = 1_024)
        val reused = AiContextCompiler(smallPolicy, { 100L })
        repeat(24) { index ->
            val input = ContextIntegrityFixture.input("Maren-$index token=synthetic_$index Nicht senden.")
            val result = reused.compile(input)
            assertEquals("Maren-$index token=[REDACTED] Nicht senden.", body(result))
            assertEquals(result, AiContextCompiler(smallPolicy, { 100L }).compile(input))
        }
        val failure = assertThrows(IllegalStateException::class.java) {
            reused.compile(ContextIntegrityFixture.input("x".repeat(2_000)))
        }
        assertEquals("MANDATORY_CONTEXT_EXCEEDS_BUDGET", failure.message)
        assertEquals("token=[REDACTED]", body(reused.compile(ContextIntegrityFixture.input("token=after_failure"))))
    }

    @Test
    fun sharedPatternsKeepParallelInputsIsolated() {
        val reused = AiContextCompiler(policy, { 100L })
        val pool = Executors.newFixedThreadPool(4)
        try {
            val requests = List(96) { index ->
                Callable {
                    val input = ContextIntegrityFixture.input(
                        "Maren-$index secret=synthetic_$index\nBearer abcdefgh$index\nÅ🧠\uD800-$index"
                    )
                    val result = if (index % 2 == 0) reused.compile(input)
                    else AiContextCompiler(policy, { 100L }).compile(input)
                    val expected = "Maren-$index secret=[REDACTED]\nBearer [REDACTED]\nÅ🧠\uD800-$index"
                    expected to body(result)
                }
            }
            pool.invokeAll(requests, 10, TimeUnit.SECONDS).forEach { future ->
                val (expected, actual) = future.get()
                assertEquals(expected, actual)
            }
        } finally {
            pool.shutdownNow()
            assertTrue(pool.awaitTermination(5, TimeUnit.SECONDS))
        }
    }

    @Test
    fun redactedOptionalSectionsKeepWholeBodiesAndExactBudgetDecisions() {
        val memory = ContextIntegrityFixture.memory("secret-memory",
            "Maren " + "ä".repeat(500) + " token=synthetic_memory Nicht Graz.")
        val input = ContextIntegrityFixture.input("Wo ist Maren?", listOf(memory),
            dossier = ContextIntegrityFixture.dossier("Maren api_key=synthetic_source Nicht Graz."))
            .copy(activeTaskSummary = "password=synthetic_task Nicht senden.")
        val complete = AiContextCompiler(policy, { 100L }).compile(input)
        val bytes = complete.text.toByteArray(Charsets.UTF_8).size
        val exact = AiContextCompiler(policy.copy(maxBytes = bytes), { 100L }).compile(input)
        val omitted = AiContextCompiler(policy.copy(maxBytes = bytes - 1), { 100L }).compile(input)

        assertEquals(complete, exact)
        assertEquals(listOf(memory.id), exact.includedMemoryIds)
        assertEquals(1, exact.includedResearchSourceUrls.size)
        assertFalse(exact.text.contains("synthetic_"))
        assertEquals(listOf(memory.id), omitted.includedMemoryIds)
        assertTrue(omitted.includedResearchSourceUrls.isEmpty())
        assertFalse(omitted.text.contains("## RESEARCH EVIDENCE"))
        assertTrue(omitted.truncated)
    }
}
