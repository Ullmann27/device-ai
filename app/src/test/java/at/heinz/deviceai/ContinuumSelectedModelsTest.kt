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

    private fun fast(hash: String = ContinuumSelectedModels.FAST_SHA256, bytes: Long = ContinuumSelectedModels.FAST_BYTES) =
        ImportedLocalModel(ContinuumSelectedModels.FAST_ID, ContinuumSelectedModels.FAST_VERSION, hash, bytes,
            LocalModelCapabilities(true, true, true, false, false, 32_003, null,
                setOf(LocalModelBackend.CPU, LocalModelBackend.GPU)))

    @Test fun smallerGemmaPrefillIsImportableAndKeepsTheGemma4OutputBudget() {
        // Published file identity (Hugging Face LFS record, re-hashed on the build host on 18.11.2026).
        assertEquals("gemma-4-E2B-it.litertlm", ContinuumSelectedModels.FAST_FILENAME)
        assertEquals("181938105e0eefd105961417e8da75903eacda102c4fce9ce90f50b97139a63c", ContinuumSelectedModels.FAST_SHA256)
        assertEquals(2_588_147_712L, ContinuumSelectedModels.FAST_BYTES)
        assertTrue(ContinuumSelectedModels.FAST_SOURCE.startsWith("https://huggingface.co/litert-community/"))
        assertTrue(ContinuumSelectedModels.FAST_VERSION.endsWith(ContinuumSelectedModels.FAST_SHA256.take(8)))
        // The same identifier rule as the import dialog and the catalog.
        val identifier = Regex("[A-Za-z0-9][A-Za-z0-9._-]{0,63}")
        assertTrue(ContinuumSelectedModels.FAST_ID.matches(identifier))
        assertTrue(ContinuumSelectedModels.FAST_VERSION.matches(identifier))
        // A distinct catalog entry next to the selected E4B, never mistaken for it.
        assertFalse(ContinuumSelectedModels.FAST_ID == ContinuumSelectedModels.LOCAL_ID)
        assertFalse(ContinuumGemmaRestorePolicy.isSelectedOriginal(fast()))
        assertEquals(at.heinz.deviceai.localmodel.LocalModelGenerationPolicy.maxOutputTokens(ContinuumSelectedModels.LOCAL_ID),
            at.heinz.deviceai.localmodel.LocalModelGenerationPolicy.maxOutputTokens(ContinuumSelectedModels.FAST_ID))
        assertTrue(ContinuumSelectedModels.FAST_BYTES < at.heinz.deviceai.localmodel.LocalModelCatalog.DEFAULT_MAX_MODEL_BYTES)
        // Fold7-class memory, cool device: the load admission does not refuse the smaller file.
        val gib = 1024L * 1024 * 1024
        assertEquals(null, at.heinz.deviceai.localmodel.LocalModelLoadAdmission.refusal(fast(),
            at.heinz.deviceai.localmodel.LocalModelResourceSnapshot(12 * gib, 4 * gib, false, 0)))
    }

    @Test fun smallerGemmaStatusNeverClaimsSpeedQualityOrAForeignFile() {
        assertTrue(ContinuumSelectedModels.fastStatus(emptyList(), null).contains("nicht importiert"))
        assertTrue(ContinuumSelectedModels.fastStatus(listOf(fast()), null).contains("importiert, derzeit nicht geladen"))
        assertTrue(ContinuumSelectedModels.fastStatus(listOf(fast()), fast()).contains("lokal geladen"))
        assertTrue(ContinuumSelectedModels.fastStatus(emptyList(), fast()).contains("messen"))
        for (wrong in listOf(fast(hash = "f".repeat(64)), fast(bytes = 1))) {
            val status = ContinuumSelectedModels.fastStatus(listOf(wrong), wrong)
            assertFalse(status.contains("lokal geladen"))
            assertTrue(status.contains("Originaldatei nicht nachgewiesen"))
        }
        // The selected E4B does not count as E2B and vice versa.
        assertTrue(ContinuumSelectedModels.fastStatus(listOf(model("gemma4-e4b-it")), model("gemma4-e4b-it")).contains("nicht importiert"))
        assertTrue(ContinuumSelectedModels.gemmaStatus(listOf(fast()), fast()).contains("noch nicht importiert"))
        val note = ContinuumSelectedModels.FAST_IMPORT_NOTE
        assertTrue(note.contains("nicht gemessen"))
        assertTrue(note.contains("SHA-256"))
        assertTrue(note.contains("nicht automatisch geladen"))
        assertFalse(note.contains("schneller als"))
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
