package at.heinz.deviceai

import at.heinz.deviceai.localmodel.ImportedLocalModel

/**
 * Owner-selected model catalog is UI metadata only.
 * Neither a selection nor a valid source hash proves a model is loaded or running.
 * No transport, file I/O, model download, cloud request or tool rights are granted here.
 */
internal object ContinuumSelectedModels {
    const val LOCAL_ID = "gemma4-e4b-it"
    const val LOCAL_VERSION = "official-0b2a8980"
    const val LOCAL_FILENAME = "gemma-4-E4B-it.litertlm"
    const val LOCAL_SHA256 = "0b2a8980ce155fd97673d8e820b4d29d9c7d99b8fa6806f425d969b145bd52e0"
    const val LOCAL_SOURCE = "https://huggingface.co/litert-community/gemma-4-E4B-it-litert-lm"
    const val REMOTE_MODEL_ID = "deepseek-ai/DeepSeek-V4.1-Flash-1017"
    const val REMOTE_SOURCE = "https://huggingface.co/deepseek-ai/DeepSeek-V4.1-Flash-1017"

    /**
     * Smaller sibling for a speed comparison (docs/research/LOCAL-MODEL-SPEED-20261118.md). Import prefill only:
     * the id keeps the Gemma 4 output budget of [at.heinz.deviceai.localmodel.LocalModelGenerationPolicy], the
     * SHA-256 and size are those of the published file (Hugging Face LFS record, re-hashed on the build host on
     * 18.11.2026). It is not the restored default and says nothing about answer quality or speed on the device.
     */
    const val FAST_ID = "gemma4-e2b-it"
    const val FAST_VERSION = "official-18193810"
    const val FAST_FILENAME = "gemma-4-E2B-it.litertlm"
    const val FAST_SHA256 = "181938105e0eefd105961417e8da75903eacda102c4fce9ce90f50b97139a63c"
    const val FAST_BYTES = 2_588_147_712L
    const val FAST_SOURCE = "https://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm"
    const val FAST_IMPORT_NOTE =
        "Angaben für Gemma 4 E2B ($FAST_FILENAME, rund 2,6 GB) sind vorausgefüllt. Das Modell ist kleiner als " +
        "Gemma 4 E4B; der Hersteller nennt höhere Geschwindigkeit, die Antwortqualität kann geringer sein. " +
        "Beides ist auf deinem Gerät nicht gemessen – vergleiche mit der Evaluation im Modellzentrum. " +
        "Lade die Originaldatei selbst herunter; Continuum prüft sie beim Import gegen diesen SHA-256. " +
        "E2B wird nach einem App-Neustart nicht automatisch geladen."

    /** Imported is not loaded; a same-name file with another hash is never presented as the published E2B. */
    fun fastStatus(importedModels: Collection<ImportedLocalModel>, loadedModel: ImportedLocalModel?): String = when {
        loadedModel?.let(::isPublishedFast) == true ->
            "Gemma 4 E2B: lokal geladen; Geschwindigkeit und Antwortqualität mit der Evaluation messen."
        importedModels.any(::isPublishedFast) -> "Gemma 4 E2B: importiert, derzeit nicht geladen."
        loadedModel?.id == FAST_ID || importedModels.any { it.id == FAST_ID } ->
            "Gemma 4 E2B: Kennung vorhanden, Originaldatei nicht nachgewiesen; der gespeicherte SHA-256 weicht ab."
        else -> "Gemma 4 E2B (kleiner, zum Geschwindigkeitsvergleich): nicht importiert."
    }

    private fun isPublishedFast(model: ImportedLocalModel): Boolean =
        model.id == FAST_ID && model.sha256 == FAST_SHA256 && model.byteCount == FAST_BYTES

    /** Selection is not installation. "Loaded" is a live native-runtime observation only. */
    fun gemmaStatus(importedModels: Collection<ImportedLocalModel>, loadedModel: ImportedLocalModel?): String =
        when {
            loadedModel?.let(::isSelectedGemma) == true -> "Gemma 4 E4B: lokal geladen; Antwortqualität noch separat prüfen."
            importedModels.any(::isSelectedGemma) -> "Gemma 4 E4B: importiert, derzeit nicht geladen."
            loadedModel?.id == LOCAL_ID || importedModels.any { it.id == LOCAL_ID } ->
                "Gemma 4 E4B: Kennung vorhanden, Originaldatei nicht nachgewiesen; der gespeicherte SHA-256 weicht ab."
            else -> "Gemma 4 E4B: ausgewählt, noch nicht importiert."
        }

    private fun isSelectedGemma(model: ImportedLocalModel): Boolean =
        model.id == LOCAL_ID && model.sha256 == LOCAL_SHA256

    /** A configured checkpoint by itself is not an operating GPU server. */
    const val REMOTE_STATUS =
        "DeepSeek V4.1 Flash-1017: Forschungsmodell ausgewählt; kein verbundener Inferenzserver nachgewiesen."

    const val RETIREMENT_GATE =
        "Qwen erst entfernen, wenn Gemma importiert, gestartet und mit einer echten Antwort geprüft ist. " +
        "Persönliche Erinnerungen und Lerngewichte bleiben unverändert."
}
