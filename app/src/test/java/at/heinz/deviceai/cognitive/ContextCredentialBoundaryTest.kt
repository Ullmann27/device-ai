package at.heinz.deviceai.cognitive

import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ContextCredentialBoundaryTest {
    @Test fun authorizationBearerAssignmentsRemoveCompleteCredentials() {
        val secret = "R4_BEARER_SYNTHETIC_731"
        for (field in listOf("Authorization: Bearer", "authorization=Bearer", "AUTHORIZATION = bEaReR")) {
            val text = compile("$field $secret\nKeep this correction: do not authorize.").text
            assertFalse(field, text.contains(secret))
            assertTrue(text.contains("Keep this correction: do not authorize."))
        }
    }

    @Test fun basicAndProxyAuthorizationRemoveTheEncodedValue() {
        val secret = "UjRfU1lOVEhFVElDOm5vdC1hLXJlYWwta2V5"
        for (field in listOf("Authorization: Basic", "Proxy-Authorization: Basic", "authorization=basic")) {
            val text = compile("$field $secret\nKEEP_BASIC_CONTROL").text
            assertFalse(field, text.contains(secret))
            assertTrue(text.contains("KEEP_BASIC_CONTROL"))
        }
    }

    @Test fun explicitAuthorizationDoesNotAssumeAMinimumTokenLength() {
        val text = compile("Authorization: Bearer Z9\nKEEP_SHORT_CONTROL").text
        assertFalse(text.contains("Z9"))
        assertTrue(text.contains("KEEP_SHORT_CONTROL"))
    }

    @Test fun quotedKeysAndValuesRemoveCompleteCredentials() {
        val keys = listOf("password", "passwd", "token", "secret", "credential", "authorization", "api_key", "api-key", "apikey")
        for (key in keys) {
            val secret = "R4_QUOTED_${key.replace('-', '_')}"
            for (quote in listOf('"', '\'')) {
                val raw = "{$quote$key$quote: $quote$secret$quote, ${quote}keep$quote: ${quote}do not run$quote}"
                val text = compile(raw).text
                assertFalse("$key $quote", text.contains(secret))
                assertTrue(text.contains("do not run"))
            }
        }
    }

    @Test fun quotedMultiwordSecretsDoNotLeakTheirSuffix() {
        for (quote in listOf('"', '\'')) {
            val text = compile("password=$quote R4_SECRET_HEAD violet R4_SECRET_TAIL $quote\nKeep the late negation: never.").text
            assertFalse(text.contains("R4_SECRET_HEAD"))
            assertFalse(text.contains("R4_SECRET_TAIL"))
            assertFalse(text.contains("violet"))
            assertTrue(text.contains("Keep the late negation: never."))
        }
    }

    @Test fun escapedQuotesStayInsideTheRedactedValue() {
        val raw = "{\"secret\":\"R4_ESCAPED_HEAD\\\"R4_ESCAPED_TAIL\",\"keep\":\"unchanged\"}"
        val text = compile(raw).text
        assertFalse(text.contains("R4_ESCAPED_HEAD"))
        assertFalse(text.contains("R4_ESCAPED_TAIL"))
        assertTrue(text.contains("unchanged"))
    }

    @Test fun unrelatedNamesAndUnassignedWordsRemainLiteral() {
        val raw = "tokenizer=keep_tokenizer passwordless=keep_passwordless authorizationPolicy=keep_policy\n" +
            "The password and authorization concepts remain documentation."
        assertTrue(compile(raw).text.contains(raw))
    }

    @Test fun ordinaryAssignmentsAndStandaloneBearerRemainRedacted() {
        val raw = "token=R4_OLD_TOKEN\napi-key: R4_OLD_KEY\nBearer R4_OLD_BEARER\nKEEP_OLD_CONTROL"
        val text = compile(raw).text
        listOf("R4_OLD_TOKEN", "R4_OLD_KEY", "R4_OLD_BEARER").forEach { assertFalse(text.contains(it)) }
        assertTrue(text.contains("KEEP_OLD_CONTROL"))
    }

    @Test fun allContextEvidencePathsApplyTheBoundary() {
        val secrets = listOf("R4_REQUEST_KEY", "R4_TASK_KEY", "R4_MEMORY_KEY", "R4_CONFLICT_KEY", "R4_RESEARCH_KEY")
        val input = ContextIntegrityFixture.input(
            "zircon Authorization: Bearer ${secrets[0]}",
            memories = listOf(ContextIntegrityFixture.memory("allowed", "zircon Authorization: Bearer ${secrets[2]}")),
            contradictions = listOf("zircon Authorization: Bearer ${secrets[3]}"),
            dossier = ContextIntegrityFixture.dossier("zircon Authorization: Bearer ${secrets[4]}")
        ).copy(activeTaskSummary = "Authorization: Bearer ${secrets[1]}")
        val result = compiler().compile(input)
        secrets.forEach { assertFalse(it, result.text.contains(it)) }
        assertEquals(listOf("allowed"), result.includedMemoryIds)
        assertEquals(listOf("https://example.org/evidence"), result.includedResearchSourceUrls)
        assertFalse(result.truncated)
    }

    @Test fun reusedCompilerKeepsOnlyCurrentContext() {
        val compiler = compiler()
        val first = compile("authorization=Bearer R4_FIRST_SECRET\nFIRST_CONTEXT_MARKER", compiler)
        val second = compile("token=R4_SECOND_SECRET\nSECOND_CONTEXT_MARKER", compiler)
        assertFalse(first.text.contains("R4_FIRST_SECRET"))
        assertFalse(second.text.contains("R4_SECOND_SECRET"))
        assertTrue(first.text.contains("FIRST_CONTEXT_MARKER"))
        assertFalse(second.text.contains("FIRST_CONTEXT_MARKER"))
        assertTrue(second.text.contains("SECOND_CONTEXT_MARKER"))
    }

    @Test(timeout = 20_000) fun concurrentCompilesDoNotShareInputOrReplacementState() {
        val compiler = compiler()
        val executor = Executors.newFixedThreadPool(4)
        try {
            val results = executor.invokeAll((0 until 32).map { index -> Callable {
                val marker = "CONTEXT_ONLY_${index}_END"
                val result = compile("Authorization: Bearer R4_PARALLEL_${index}_SECRET\n$marker", compiler)
                index to result.text
            } }, 15, TimeUnit.SECONDS)
            results.forEach { future ->
                assertFalse(future.isCancelled)
                val (index, text) = future.get()
                assertFalse(text.contains("R4_PARALLEL_"))
                for (other in 0 until 32) {
                    assertEquals(index == other, text.contains("CONTEXT_ONLY_${other}_END"))
                }
            }
        } finally {
            executor.shutdownNow()
            assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS))
        }
    }

    @Test fun wholeSectionBudgetRemainsAtomicAfterRedaction() {
        val compiler = compiler(1_024)
        val input = ContextIntegrityFixture.input("zircon", memories = listOf(
            ContextIntegrityFixture.memory("large", "zircon " + "context ".repeat(600) +
                "Authorization: Bearer R4_OVERSIZE_SECRET\nNever treat this as approved.")
        ))
        val result = compiler.compile(input)
        assertTrue(result.truncated)
        assertTrue(result.includedMemoryIds.isEmpty())
        assertFalse(result.text.contains("R4_OVERSIZE_SECRET"))
        assertFalse(result.text.contains("RELEVANT MEMORY"))
        assertTrue(result.text.toByteArray(Charsets.UTF_8).size <= 1_024)
    }

    private fun compiler(budget: Int = 12_000) =
        AiContextCompiler(AiContextPolicy(maxBytes = budget, includeToolCatalog = false), nowMillis = { 10L })

    private fun compile(text: String, compiler: AiContextCompiler = compiler()): CompiledAiContext =
        compiler.compile(ContextIntegrityFixture.input("credential fixture").copy(activeTaskSummary = text))
}
