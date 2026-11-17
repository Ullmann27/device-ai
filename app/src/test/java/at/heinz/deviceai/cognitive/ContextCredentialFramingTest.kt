package at.heinz.deviceai.cognitive

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class ContextCredentialFramingTest {
    @Test fun memoryLineBreaksPreserveTheWholeFollowingNegation() {
        for (separator in listOf("\n", "\r\n", "\r", "\t")) {
            val record = memory("zircon Authorization: Bearer R4_FRAME_SECRET${separator}Never approve the action.")
            val result = compiler().compile(ContextIntegrityFixture.input("zircon", listOf(record)))
            assertEquals(listOf("framed"), result.includedMemoryIds)
            assertSecretAbsent(result)
            assertTrue(result.text.contains("Never approve the action."))
        }
    }

    @Test fun sanitizedTabsCannotSeparateAuthorizationFromItsCredential() {
        val record = memory("zircon Authorization:\tBearer\tR4_FRAME_SECRET\nNever approve the action.")
        val result = compiler().compile(ContextIntegrityFixture.input("zircon", listOf(record)))
        assertSecretAbsent(result)
        assertTrue(result.text.contains("Never approve the action."))
    }

    @Test fun contradictionProjectionPreservesItsFollowingNegation() {
        val record = memory("zircon password=R4_FRAME_SECRET\nNever approve the action.")
            .copy(validationState = ValidationState.CONTRADICTED)
        val result = compiler().compile(ContextIntegrityFixture.input("zircon", listOf(record),
            contradictions = listOf(record.content)))
        assertTrue(result.includedMemoryIds.isEmpty())
        assertSecretAbsent(result)
        assertTrue(result.text.contains("Never approve the action."))
    }

    @Test fun researchSnippetPreservesItsFollowingNegation() {
        val result = compiler().compile(ContextIntegrityFixture.input("zircon", dossier =
            ContextIntegrityFixture.dossier("Authorization: Basic R4_FRAME_SECRET\nNever approve the action.")))
        assertEquals(listOf("https://example.org/evidence"), result.includedResearchSourceUrls)
        assertSecretAbsent(result)
        assertTrue(result.text.contains("Never approve the action."))
    }

    @Test fun literalDisplayMarkersInsideCredentialsCannotEscapeRedaction() {
        for (value in listOf("R4_FRAME_SECRET[U+000A]R4_FRAME_TAIL", "\"R4_FRAME_SECRET[U+0009]R4_FRAME_TAIL\"")) {
            val result = compiler().compile(ContextIntegrityFixture.input("zircon", listOf(
                memory("zircon token=$value\nNever approve the action."))))
            assertSecretAbsent(result)
            assertFalse(result.text.contains("R4_FRAME_TAIL"))
            assertTrue(result.text.contains("Never approve the action."))
        }
    }

    @Test fun malformedCredentialsCannotCloseInAnotherMemoryField() {
        var observations = 0
        val compiler = compiler().withRecallObserver { _, _, _ -> observations++ }
        val first = memory("zircon token=\"R4_FRAME_SECRET")
        val second = memory("zircon the next record says \"Never approve the action.\"").copy(id = "second")
        val error = assertThrows(IllegalStateException::class.java) {
            compiler.compile(ContextIntegrityFixture.input("zircon", listOf(first, second)))
        }
        assertEquals("UNTERMINATED_CREDENTIAL_VALUE", error.message)
        assertEquals(0, observations)
    }

    @Test fun malformedResearchTitlesCannotConsumeTheSnippet() {
        val dossier = ContextIntegrityFixture.dossier("zircon \"Never approve the action.\"")
        val malformed = dossier.copy(sources = dossier.sources.map {
            it.copy(title = "secret=\"R4_FRAME_SECRET")
        })
        val error = assertThrows(IllegalStateException::class.java) {
            compiler().compile(ContextIntegrityFixture.input("zircon", dossier = malformed))
        }
        assertEquals("UNTERMINATED_CREDENTIAL_VALUE", error.message)
    }

    @Test fun redactedSourceFieldsCannotConsumeTheEvidenceFrame() {
        val record = memory("zircon Never approve the action.").copy(
            sourceReference = "token=R4_FRAME_SECRET",
            validationEvidenceReferences = listOf("secret=R4_REFERENCE_SECRET"))
        val result = compiler().compile(ContextIntegrityFixture.input("zircon", listOf(record)))
        assertSecretAbsent(result)
        assertFalse(result.text.contains("R4_REFERENCE_SECRET"))
        assertTrue(result.text.contains("<<<UNTRUSTED_EVIDENCE>>>"))
        assertTrue(result.text.contains("<<<END_UNTRUSTED_EVIDENCE>>>"))
        assertTrue(result.text.contains("Never approve the action."))
    }

    private fun compiler() = AiContextCompiler(AiContextPolicy(includeToolCatalog = false), nowMillis = { 10L })
    private fun memory(text: String) = ContextIntegrityFixture.memory("framed", text)
    private fun assertSecretAbsent(result: CompiledAiContext) = assertFalse(result.text.contains("R4_FRAME_SECRET"))
}
