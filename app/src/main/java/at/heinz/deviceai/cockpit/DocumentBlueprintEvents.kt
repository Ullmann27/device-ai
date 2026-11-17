package at.heinz.deviceai.cockpit

/**
 * Additive document-blueprint events. They extend [CockpitAnimationEvent.Extension] so existing
 * exhaustive `when` branches keep compiling and unknown renderers ignore them by design.
 *
 * Emitters must only construct these from a real document pipeline fact (intake, extraction,
 * analysis, draft text, or an authorised owner action). Nothing here invents steps or buttons.
 */
enum class DocumentDraftAction { OPEN, CONTINUE, SAVE }

data class DocumentPipelineStepEvent(
    override val turnId: String,
    override val atMillis: Long,
    val key: String,
    val label: String,
    val state: StepState
) : CockpitAnimationEvent.Extension(KIND) {
    companion object {
        const val KIND = "document-pipeline"
        const val KEY_OPEN = "open"
        const val KEY_CAPTURE = "capture"
        const val KEY_ANALYZE = "analyze"
        const val KEY_KEYPOINTS = "keypoints"
        const val KEY_DRAFT = "draft"

        val CANONICAL: Map<String, String> = linkedMapOf(
            KEY_OPEN to "Dokument öffnen",
            KEY_CAPTURE to "Inhalte erfassen",
            KEY_ANALYZE to "Analysieren",
            KEY_KEYPOINTS to "Kernaussagen",
            KEY_DRAFT to "Entwurf erstellen"
        )

        /** Resolves a real step key/label to a canonical blueprint key, or null when it is unrelated. */
        fun canonicalKey(key: String, label: String): String? {
            val cleanedKey = key.trim().lowercase()
            if (cleanedKey in CANONICAL) return cleanedKey
            val cleanedLabel = label.trim()
            return CANONICAL.entries.firstOrNull { (_, title) ->
                cleanedLabel.equals(title, ignoreCase = true) ||
                    cleanedLabel.startsWith("$title:", ignoreCase = true)
            }?.key
        }
    }
}

data class DocumentDraftReadyEvent(
    override val turnId: String,
    override val atMillis: Long,
    val text: String,
    val coveredChars: Int? = null,
    val totalChars: Int? = null,
    val actions: Set<DocumentDraftAction> = emptySet()
) : CockpitAnimationEvent.Extension(KIND) {
    companion object {
        const val KIND = "document-draft"
    }
}
