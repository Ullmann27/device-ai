package at.heinz.deviceai

import at.heinz.deviceai.localmodel.ImportedLocalModel
import at.heinz.deviceai.localmodel.LocalModelState

/**
 * Decide whether the original, previously imported Gemma 4 can be restored
 * after a genuine process restart. The persisted owner choice is a preference,
 * NOT proof that the model is loaded or that native generation works.
 *
 * Never select a same-name but differently hashed checkpoint, never initiate
 * a download, and never load a second engine into an already busy runtime.
 * The actual runtime still verifies the original model file's SHA-256.
 */
internal object ContinuumGemmaRestorePolicy {
    const val PREFERENCES = "continuum_gemma_restore_v1"
    const val KEY_ENABLED = "restore_original_gemma_on_reopen"

    fun isSelectedOriginal(model: ImportedLocalModel): Boolean =
        model.id == ContinuumSelectedModels.LOCAL_ID &&
            model.version == ContinuumSelectedModels.LOCAL_VERSION &&
            model.sha256 == ContinuumSelectedModels.LOCAL_SHA256

    fun candidate(
        enabled: Boolean,
        state: LocalModelState,
        loaded: ImportedLocalModel?,
        imported: Collection<ImportedLocalModel>
    ): ImportedLocalModel? {
        if (!enabled || loaded != null || state != LocalModelState.EMPTY) return null
        return imported.firstOrNull(::isSelectedOriginal)
    }
}
