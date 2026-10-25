package at.heinz.deviceai

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

    /** Selection is not installation. "Loaded" is a live native-runtime observation only. */
    fun gemmaStatus(importedModelIds: Collection<String>, loadedModelId: String?): String =
        when {
            loadedModelId == LOCAL_ID -> "Gemma 4 E4B: lokal geladen; Antwortqualität noch separat prüfen."
            LOCAL_ID in importedModelIds -> "Gemma 4 E4B: importiert, derzeit nicht geladen."
            else -> "Gemma 4 E4B: ausgewählt, noch nicht importiert."
        }

    /** A configured checkpoint by itself is not an operating GPU server. */
    const val REMOTE_STATUS =
        "DeepSeek V4.1 Flash-1017: Forschungsmodell ausgewählt; kein verbundener Inferenzserver nachgewiesen."

    const val RETIREMENT_GATE =
        "Qwen erst entfernen, wenn Gemma importiert, gestartet und mit einer echten Antwort geprüft ist. " +
        "Persönliche Erinnerungen und Lerngewichte bleiben unverändert."
}
