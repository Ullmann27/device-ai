package at.heinz.deviceai.cognitive

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class ContextCredentialHoldoutTest {
    @Test fun legacyHyphenatedCredentialNamesRemainProtected() {
        for (key in listOf("x-token", "client-secret", "legacy-password", "dev.api_key", "namespace:passwd")) {
            val result = compile("$key=R4_LEGACY_PREFIX_SECRET\nKEEP_LEGACY_CORRECTION")
            assertFalse(key, result.text.contains("R4_LEGACY_PREFIX_SECRET"))
            assertTrue(result.text.contains("KEEP_LEGACY_CORRECTION"))
        }
    }

    @Test fun quotedValueMatrixPreservesNeighbouringCorrections() {
        val keys = listOf("password", "passwd", "token", "secret", "credential", "authorization", "api_key", "api-key", "apikey")
        var cases = 0
        for (key in keys) for (quote in listOf('"', '\'')) for (space in listOf("", " \t")) {
            for (middle in listOf(" violet ", "\\nested\\path", "\"quoted\"", "'quoted'", " Ω🌌\nnext ")) {
                val value = "R4_MATRIX_HEAD" + middle + "R4_MATRIX_TAIL"
                val escaped = value.replace("\\", "\\\\").replace(quote.toString(), "\\$quote")
                val raw = "{$quote$key$quote:$space$quote$escaped$quote, \"keep\":\"Never approve R4_KEEP_$cases\"}"
                val result = compile(raw)
                assertFalse("case=$cases", result.text.contains("R4_MATRIX_HEAD"))
                assertFalse("case=$cases", result.text.contains("R4_MATRIX_TAIL"))
                assertTrue(result.text.contains("Never approve R4_KEEP_$cases"))
                assertFalse(result.truncated)
                cases++
            }
        }
        assertEquals(180, cases)
    }

    @Test fun missingValuesDoNotConsumeTheFollowingLine() {
        val raw = "password= \nNever approve the first action.\nsecret:\t\nNever approve the second action."
        assertTrue(compile(raw).text.contains(raw))
    }

    @Test fun nestedCredentialLabelsStayInsideTheirQuotedValue() {
        val text = compile("secret=\"R4_OUTER_HEAD password=R4_INNER_SECRET R4_OUTER_TAIL\"\nKEEP_OUTER_CORRECTION").text
        listOf("R4_OUTER_HEAD", "R4_INNER_SECRET", "R4_OUTER_TAIL").forEach { assertFalse(text.contains(it)) }
        assertTrue(text.contains("KEEP_OUTER_CORRECTION"))
    }

    @Test fun malformedQuotedValuesRejectWithoutPublishingRecall() {
        var observations = 0
        val compiler = compiler().withRecallObserver { _, _, _ -> observations++ }
        for (raw in listOf("secret=\"R4_UNCLOSED", "password='R4_UNCLOSED", "token=\"R4_UNCLOSED\\")) {
            val error = assertThrows(IllegalStateException::class.java) {
                compiler.compile(ContextIntegrityFixture.input("zircon").copy(activeTaskSummary = raw))
            }
            assertEquals("UNTERMINATED_CREDENTIAL_VALUE", error.message)
        }
        assertEquals(0, observations)
    }

    @Test(timeout = 10_000) fun longQuotedSecretsDoNotLoseTheFollowingCorrection() {
        val memory = ContextIntegrityFixture.memory("long-secret", "zircon secret=\"" +
            "synthetic ".repeat(4_000) + "R4_LONG_SECRET_END\"\nNever approve this action.")
        val result = compiler().compile(ContextIntegrityFixture.input("zircon", listOf(memory)))
        assertEquals(listOf("long-secret"), result.includedMemoryIds)
        assertFalse(result.text.contains("R4_LONG_SECRET_END"))
        assertFalse(result.text.contains("synthetic"))
        assertTrue(result.text.contains("Never approve this action."))
        assertFalse(result.truncated)
    }

    private fun compiler() = AiContextCompiler(AiContextPolicy(maxBytes = 64 * 1_024,
        includeToolCatalog = false), nowMillis = { 10L })

    private fun compile(text: String) = compiler().compile(
        ContextIntegrityFixture.input("zircon").copy(activeTaskSummary = text))
}
