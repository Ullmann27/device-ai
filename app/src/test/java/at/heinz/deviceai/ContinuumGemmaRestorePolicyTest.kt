package at.heinz.deviceai

import at.heinz.deviceai.localmodel.ImportedLocalModel
import at.heinz.deviceai.localmodel.LocalModelBackend
import at.heinz.deviceai.localmodel.LocalModelCapabilities
import at.heinz.deviceai.localmodel.LocalModelState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ContinuumGemmaRestorePolicyTest {
    private val capabilities = LocalModelCapabilities(
        text = true, vision = false, audio = false, video = false,
        functionCalling = false, maxContextTokens = 4096,
        minimumRuntimeVersion = null, supportedBackends = setOf(LocalModelBackend.CPU)
    )

    private fun original(
        id: String = ContinuumSelectedModels.LOCAL_ID,
        version: String = ContinuumSelectedModels.LOCAL_VERSION,
        sha: String = ContinuumSelectedModels.LOCAL_SHA256
    ): ImportedLocalModel = ImportedLocalModel(
        id, version, sha, 3_659_530_240L, capabilities
    )

    @Test fun reopenOriginalOnlyWhenEnabledEmptyAndNotYetLoaded() {
        val pinned = original()
        assertEquals(pinned, ContinuumGemmaRestorePolicy.candidate(
            true, LocalModelState.EMPTY, null, listOf(pinned)
        ))
        assertTrue(ContinuumGemmaRestorePolicy.isSelectedOriginal(pinned))
    }

    @Test fun explicitUnloadPreferenceWinsOverImportedOriginal() {
        assertNull(ContinuumGemmaRestorePolicy.candidate(
            false, LocalModelState.EMPTY, null, listOf(original())
        ))
    }

    @Test fun noImportMeansNoDownloadAndNoRestoreCandidate() {
        assertNull(ContinuumGemmaRestorePolicy.candidate(
            true, LocalModelState.EMPTY, null, emptyList()
        ))
    }

    @Test fun doNotUseDifferentModelDespiteMatchingDisplayName() {
        val forged = listOf(
            original(sha = "a".repeat(64)),
            original(version = "older-build"),
            original(id = "gemma4-e4b-unknown")
        )
        forged.forEach { candidate ->
            assertFalse(ContinuumGemmaRestorePolicy.isSelectedOriginal(candidate))
        }
        assertNull(ContinuumGemmaRestorePolicy.candidate(
            true, LocalModelState.EMPTY, null, forged
        ))
    }

    @Test fun neverCreateSecondNativeEngineDuringActiveLifecycle() {
        for (state in LocalModelState.entries.filter { it != LocalModelState.EMPTY }) {
            assertNull(ContinuumGemmaRestorePolicy.candidate(
                true, state, null, listOf(original())
            ))
        }
        assertNull(ContinuumGemmaRestorePolicy.candidate(
            true, LocalModelState.EMPTY, original(), listOf(original())
        ))
    }

    @Test fun correctOriginalSelectedEvenWhenForeignFileAppearsFirst() {
        val pinned = original()
        assertEquals(pinned, ContinuumGemmaRestorePolicy.candidate(
            true, LocalModelState.EMPTY, null,
            listOf(original(sha = "a".repeat(64)), pinned)
        ))
    }

    @Test fun persistedOwnerChoiceHasFixedPrivateKeys() {
        assertEquals("continuum_gemma_restore_v1", ContinuumGemmaRestorePolicy.PREFERENCES)
        assertEquals("restore_original_gemma_on_reopen", ContinuumGemmaRestorePolicy.KEY_ENABLED)
    }
}
