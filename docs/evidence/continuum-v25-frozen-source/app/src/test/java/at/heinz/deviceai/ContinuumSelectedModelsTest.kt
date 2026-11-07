package at.heinz.deviceai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import at.heinz.deviceai.localmodel.ImportedLocalModel
import at.heinz.deviceai.localmodel.LocalModelBackend
import at.heinz.deviceai.localmodel.LocalModelCapabilities

class ContinuumSelectedModelsTest {
    private fun model(id: String, hash: String = ContinuumSelectedModels.LOCAL_SHA256) =
        ImportedLocalModel(id, "test-version", hash, 100,
            LocalModelCapabilities(true, false, false, false, false, 4096, null,
                setOf(LocalModelBackend.CPU)))
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
        assertTrue(ContinuumSelectedModels.gemmaStatus(listOf(model("qwen3")), model("qwen3")).contains("noch nicht importiert"))
        assertTrue(ContinuumSelectedModels.REMOTE_STATUS.contains("kein verbundener Inferenzserver"))
        assertFalse(ContinuumSelectedModels.REMOTE_STATUS.contains("BEREIT"))
        assertTrue(ContinuumSelectedModels.RETIREMENT_GATE.contains("erst entfernen"))
    }

    @Test fun importedAndLoadedStatusAreDifferent() {
        assertTrue(ContinuumSelectedModels.gemmaStatus(listOf(model("gemma4-e4b-it")), null)
            .contains("importiert, derzeit nicht geladen"))
        assertTrue(ContinuumSelectedModels.gemmaStatus(listOf(model("gemma4-e4b-it")), model("gemma4-e4b-it"))
            .contains("lokal geladen"))
        assertTrue(ContinuumSelectedModels.gemmaStatus(emptyList(), model("gemma4-e4b-it"))
            .contains("lokal geladen"))
        assertFalse(ContinuumSelectedModels.gemmaStatus(emptyList(), model("qwen3"))
            .contains("lokal geladen"))
    }

    @Test fun catalogNameAloneCannotImpersonateSelectedGemmaBytes() {
        val wrong = model("gemma4-e4b-it", "f".repeat(64))
        for (status in listOf(
            ContinuumSelectedModels.gemmaStatus(listOf(wrong), null),
            ContinuumSelectedModels.gemmaStatus(listOf(wrong), wrong),
            ContinuumSelectedModels.gemmaStatus(emptyList(), wrong)
        )) {
            assertFalse(status.contains("lokal geladen"))
            assertTrue(status.contains("Originaldatei nicht nachgewiesen"))
        }
    }

    @Test fun aDifferentLoadedVersionDoesNotTurnVerifiedImportIntoLoadedOriginal() {
        val correct = model("gemma4-e4b-it")
        val wrong = model("gemma4-e4b-it", "f".repeat(64))
        assertTrue(ContinuumSelectedModels.gemmaStatus(listOf(wrong, correct), wrong)
            .contains("importiert, derzeit nicht geladen"))
        assertTrue(ContinuumSelectedModels.gemmaStatus(listOf(wrong, correct), correct)
            .contains("lokal geladen"))
    }
}
