package at.heinz.deviceai.cognitive

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** R5: independent holdouts on the R4 source; these tests precede the production fix. */
class ContextCredentialFollowupR5Test {
    @Test fun standaloneBearerAtLineEndDoesNotEatNextLineNegation() {
        for (separator in listOf("\n", "\r\n")) {
            val result = compileTask("Bearer${separator}Nevermore approve the action.")
            assertTrue("separator=${separator.toList()}", result.contains("Nevermore approve the action."))
        }
    }

    @Test fun oauthUnderscoreAliasesAreClassifiedAsCredentialFields() {
        for (key in listOf("access_token", "refresh_token", "session_token", "auth_token",
            "client_secret", "app_secret")) {
            val secret = "R5_SYNTHETIC_${key.uppercase()}"
            val result = compileTask("${key}=${secret}\nNever approve the operation.")
            assertFalse("key=$key", result.contains(secret))
            assertTrue("key=$key", result.contains("Never approve the operation."))
        }
    }

    @Test fun quotedOauthAliasFieldsCannotLeakViaTaskOrResearch() {
        val task = compileTask("{\"access_token\":\"R5_JSON_SYNTHETIC\",\"keep\":\"Never approve\"}")
        assertFalse(task.contains("R5_JSON_SYNTHETIC"))
        assertTrue(task.contains("Never approve"))

        val evidence = compiler().compile(ContextIntegrityFixture.input(
            "zircon", dossier = ContextIntegrityFixture.dossier(
                "zircon {\"client_secret\":\"R5_RESEARCH_SYNTHETIC\"}\nNever approve the operation."
            )
        ))
        assertFalse(evidence.text.contains("R5_RESEARCH_SYNTHETIC"))
        assertTrue(evidence.text.contains("Never approve the operation."))
    }

    @Test fun memoryAndContradictionProjectionsProtectOauthAliases() {
        val memory = ContextIntegrityFixture.memory("r5-alias",
            "zircon access_token=R5_MEMORY_SYNTHETIC\nNever approve the action.")
        val result = compiler().compile(ContextIntegrityFixture.input("zircon",
            listOf(memory)))
        assertFalse(result.text.contains("R5_MEMORY_SYNTHETIC"))
        assertTrue(result.text.contains("Never approve the action."))

        val contradiction = memory.copy(validationState = ValidationState.CONTRADICTED)
        val contradicted = compiler().compile(ContextIntegrityFixture.input("zircon",
            listOf(contradiction), contradictions = listOf(contradiction.content)))
        assertFalse(contradicted.text.contains("R5_MEMORY_SYNTHETIC"))
        assertTrue(contradicted.text.contains("Never approve the action."))
    }

    @Test fun existingSchemesAndUnrelatedNamesDoNotRegress() {
        val result = compileTask("Authorization: Bearer R5_EXISTING_SYNTHETIC\n" +
            "x-token=R5_X_SYNTHETIC\naccess_tokenizer=public\n" +
            "Never approve the action.")
        assertFalse(result.contains("R5_EXISTING_SYNTHETIC"))
        assertFalse(result.contains("R5_X_SYNTHETIC"))
        assertTrue(result.contains("access_tokenizer=public"))
        assertTrue(result.contains("Never approve the action."))
    }

    private fun compiler() =
        AiContextCompiler(AiContextPolicy(includeToolCatalog = false), nowMillis = { 10L })

    private fun compileTask(text: String): String =
        compiler().compile(ContextIntegrityFixture.input("zircon")
            .copy(activeTaskSummary = text)).text
}
