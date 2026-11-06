package at.heinz.deviceai.cognitive

import at.heinz.deviceai.localmodel.ImportedLocalModel
import at.heinz.deviceai.localmodel.LocalModelBackend
import at.heinz.deviceai.localmodel.LocalModelCapabilities
import org.junit.Assert.*
import org.junit.Test

class AiContextIdentityRegressionTest {
    private fun input(): AiContextInput {
        val base = ContextIntegrityFixture.input("Wer bist du?")
        return base.copy(identityState = base.identityState.evolve { it.copy(chosenName = "Jarvis") },
            model = ImportedLocalModel("actual-local-model", "v7", "a".repeat(64), 1,
                LocalModelCapabilities(true, false, false, false, false, 4_096, null, setOf(LocalModelBackend.CPU))))
    }

    @Test fun actualNameAndModelAreRequiredBeforeOptionalMemoryAndTools() {
        val policy = AiContextPolicy(maxBytes = 6_000, includeToolCatalog = false)
        val input = input().copy(availableSkillNames = List(40) { "skill-" + it + "-" + "x".repeat(80) })
        val text = AiContextCompiler(policy).compile(input).text
        val identity = text.substringBefore("\n\n## USER REQUEST")
        assertTrue(identity.contains("assistant_name=\"Jarvis\""))
        assertTrue(identity.contains("active_local_model=actual-local-model@v7"))
        assertTrue(identity.contains("active_model_sha256=" + "a".repeat(64)))
    }

    @Test fun mandatoryIdentityIsAtomicAtItsActualByteBoundary() {
        val input = input().copy(activeTaskSummary = "Vollständige Pflichtangabe. ".repeat(20))
        val policy = AiContextPolicy(maxBytes = 6_000, includeToolCatalog = false)
        val context = AiContextCompiler(policy).compile(input)
        assertTrue(context.text.contains("assistant_name=\"Jarvis\""))
        val required = context.text.substringBefore("\n\n## ACTIVE LOCAL MODEL")
        val bytes = required.toByteArray(Charsets.UTF_8).size
        assertTrue(bytes >= 1_024)
        val exact = AiContextCompiler(policy.copy(maxBytes = bytes)).compile(input)
        assertTrue(exact.text.contains("assistant_name=\"Jarvis\""))
        assertTrue(exact.text.contains("active_local_model=actual-local-model@v7"))
        val failure = assertThrows(IllegalStateException::class.java) {
            AiContextCompiler(policy.copy(maxBytes = bytes - 1)).compile(input)
        }
        assertEquals("MANDATORY_CONTEXT_EXCEEDS_BUDGET", failure.message)
    }

    @Test fun isolatedBotFrameDoesNotReceiveTheOwnersNameOrProfile() {
        val input = input().let { base -> base.copy(identityState = base.identityState.evolve {
            it.copy(communicationProfile = mapOf("style" to "detailed")) }) }
        val policy = AiContextPolicy(includeToolCatalog = false, includeIdentityDetails = false,
            memoryLimit = 0, researchSourceLimit = 0)
        val text = AiContextCompiler(policy).compile(input).text
        assertTrue(text.contains("identity=selected_private_bot_profile"))
        assertFalse(text.contains("Jarvis"))
        assertFalse(text.contains("communication_style"))
        assertFalse(text.contains(input.identityState.identityId))
    }
}
