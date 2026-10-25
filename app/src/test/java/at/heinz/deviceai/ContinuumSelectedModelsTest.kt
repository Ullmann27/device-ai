package at.heinz.deviceai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ContinuumSelectedModelsTest {
    @Test fun originalModelIdentityAndHashArePresent() {
        assertEquals("gemma4-e4b-it", ContinuumSelectedModels.LOCAL_ID)
        assertEquals("official-0b2a8980", ContinuumSelectedModels.LOCAL_VERSION)
        assertEquals("gemma-4-E4B-it.litertlm", ContinuumSelectedModels.LOCAL_FILENAME)
        assertTrue(ContinuumSelectedModels.LOCAL_SHA256.matches(Regex("[a-f0-9]{64}")))
        assertTrue(ContinuumSelectedModels.LOCAL_SOURCE.startsWith("https://huggingface.co/"))
        assertEquals("deepseek-ai/DeepSeek-V4.1-Flash-1017", ContinuumSelectedModels.REMOTE_MODEL_ID)
    }

    @Test fun modelChoiceDoesNotClaimModelIsInstalledOrServing() {
        assertTrue(ContinuumSelectedModels.gemmaStatus(emptyList(), null).contains("noch nicht importiert"))
        assertTrue(ContinuumSelectedModels.gemmaStatus(listOf("qwen3"), "qwen3").contains("noch nicht importiert"))
        assertTrue(ContinuumSelectedModels.REMOTE_STATUS.contains("kein verbundener Inferenzserver"))
        assertFalse(ContinuumSelectedModels.REMOTE_STATUS.contains("BEREIT"))
        assertTrue(ContinuumSelectedModels.RETIREMENT_GATE.contains("erst entfernen"))
    }

    @Test fun importedAndLoadedStatusAreDifferent() {
        assertTrue(ContinuumSelectedModels.gemmaStatus(listOf("gemma4-e4b-it"), null)
            .contains("importiert, derzeit nicht geladen"))
        assertTrue(ContinuumSelectedModels.gemmaStatus(listOf("gemma4-e4b-it"), "gemma4-e4b-it")
            .contains("lokal geladen"))
        assertTrue(ContinuumSelectedModels.gemmaStatus(emptyList(), "gemma4-e4b-it")
            .contains("lokal geladen"))
        assertFalse(ContinuumSelectedModels.gemmaStatus(emptyList(), "qwen3")
            .contains("lokal geladen"))
    }
}
