package at.heinz.deviceai.cognitive

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class RecoveredContextCredentialBoundaryTest {
    @Test fun compoundCredentialAliasesKeepTheFollowingCorrection() {
        for (prefix in listOf("access", "refresh", "session", "auth", "client", "app")) {
            val suffix = if (prefix in listOf("client", "app")) "secret" else "token"
            for (separator in listOf("_", "-")) for (uppercase in listOf(false, true)) {
                val key = "$prefix$separator$suffix".let { if (uppercase) it.uppercase() else it }
                val text = compile("$key=SYNTH_ALIAS_00000001\nNever approve the action.").text
                assertFalse(key, text.contains("SYNTH_ALIAS_00000001"))
                assertTrue(key, text.contains("Never approve the action."))
            }
        }
    }

    @Test fun aliasesAreRedactedAcrossThePublicContextInputs() {
        val secrets = listOf("SYNTH_REQUEST_000001", "SYNTH_TASK_000001", "SYNTH_MEMORY_000001",
            "SYNTH_CONFLICT_000001", "SYNTH_RESEARCH_000001")
        val input = ContextIntegrityFixture.input(
            "zircon access_token=${secrets[0]}",
            memories = listOf(ContextIntegrityFixture.memory("alias-memory", "zircon session_token=${secrets[2]}")),
            contradictions = listOf("zircon auth_token=${secrets[3]}"),
            dossier = ContextIntegrityFixture.dossier("zircon app_secret=${secrets[4]}\nNever approve research.")
        ).copy(activeTaskSummary = "refresh_token=${secrets[1]}\nNever approve the action.")
        val result = compiler().compile(input)
        secrets.forEach { assertFalse(it, result.text.contains(it)) }
        assertEquals(listOf("alias-memory"), result.includedMemoryIds)
        assertEquals(listOf("https://example.org/evidence"), result.includedResearchSourceUrls)
        assertTrue(result.text.contains("Never approve the action."))
        assertTrue(result.text.contains("Never approve research."))
        assertFalse(result.truncated)
    }

    @Test fun authorizationSchemesCannotConsumeAWrappedCredential() {
        for (header in listOf("Authorization", "Proxy-Authorization")) {
            for (scheme in listOf("Bearer", "Basic")) for (newline in listOf("\n", "\r\n", "\r")) {
                for (value in listOf("SYNTH_WRAPPED_000001", "\"SYNTH_WRAPPED_000001\"", "Z9")) {
                    rejected("$header: $scheme \t${newline}$value\nNever approve the action.")
                }
            }
        }
    }

    @Test fun authorizationValuesCannotStartOnAnotherPhysicalLine() {
        for (header in listOf("Authorization", "Proxy-Authorization")) {
            for (newline in listOf("\n", "\r\n", "\r")) {
                for (value in listOf("Bearer SYNTH_WRAPPED_000001", "Basic Z9", "\"SYNTH_WRAPPED_000001\"")) {
                    rejected("$header: \t${newline}$value\nNever approve the action.")
                }
            }
        }
    }

    @Test fun ambiguousBareBearerLinesRejectWithoutPublishingRecall() {
        for (newline in listOf("\n", "\r\n", "\r")) {
            rejected("  bEaReR \t${newline}SYNTH_BARE_00000001\nNever approve the action.")
        }
    }

    @Test fun inlineBearerRetainsAllPreviouslyRecognizedVerticalWhitespace() {
        val separators = listOf("\n", "\r\n", "\r", "\u000B", "\u000C", "\n\n", "\r\n \t\r\n", "\u000B\t\u000C")
        for (prefix in listOf("prefix ", "Keep this example: ", "(")) {
            for (separator in separators) {
                rejected("${prefix}Bearer \t${separator}SYNTH_INLINE_00000001\nNever approve the action.")
            }
        }
    }

    @Test fun completeHeadersPreservePhysicalFollowingLines() {
        for (header in listOf("Authorization", "Proxy-Authorization")) {
            for (scheme in listOf("Bearer", "Basic")) for (newline in listOf("\n", "\r\n", "\r")) {
                val following = "${newline}Never approve the action.${newline}Keep the correction."
                val text = compile("$header:\t$scheme\tSYNTH_COMPLETE_000001$following").text
                assertFalse(text.contains("SYNTH_COMPLETE_000001"))
                assertTrue(text.contains(following))
            }
        }
    }

    @Test fun quotedAliasesAndAuthorizationRetainTheirExplicitBoundary() {
        for (field in listOf("client_secret=", "Authorization: Bearer ", "Proxy-Authorization: Basic ")) {
            for (quote in listOf('"', '\'')) for (newline in listOf("\n", "\r\n")) {
                val value = "SYNTH_QUOTED_HEAD${newline}Bearer${newline}SYNTH_QUOTED_TAIL"
                val text = compile("$field$quote$value$quote\nNever approve the action.").text
                assertFalse(text.contains("SYNTH_QUOTED_HEAD"))
                assertFalse(text.contains("SYNTH_QUOTED_TAIL"))
                assertTrue(text.contains("Never approve the action."))
            }
        }
    }

    @Test fun ordinaryFollowupsAndMissingAssignmentsRemainLiteral() {
        val raw = "Bearer\nDo not authorize the action.\n" +
            "prefix Bearer\nDo not authorize another action.\n" +
            "tokenBearer\nSYNTH_UNRELATED_LITERAL\n" +
            "access_token= \nNever approve the first action.\n" +
            "client_secret:\t\nNever approve the second action.\n" +
            "access_tokenizer=keep_alias client_secretary=keep_name"
        assertTrue(compile(raw).text.contains(raw))
    }

    @Test fun malformedQuotedAliasCannotConsumeAnotherField() {
        rejected("client_secret=\"SYNTH_UNCLOSED_000001\nNever approve the action.")
    }

    private fun compiler() = AiContextCompiler(AiContextPolicy(includeToolCatalog = false), nowMillis = { 10L })

    private fun compile(text: String) = compiler().compile(
        ContextIntegrityFixture.input("zircon").copy(activeTaskSummary = text))

    private fun rejected(text: String) {
        var observations = 0
        val compiler = compiler().withRecallObserver { _, _, _ -> observations++ }
        val input = ContextIntegrityFixture.input("zircon", listOf(
            ContextIntegrityFixture.memory("safe-control", "zircon Never approve the action.")))
            .copy(activeTaskSummary = text)
        val error = assertThrows(IllegalStateException::class.java) { compiler.compile(input) }
        assertEquals("UNTERMINATED_CREDENTIAL_VALUE", error.message)
        assertEquals(0, observations)
    }
}
