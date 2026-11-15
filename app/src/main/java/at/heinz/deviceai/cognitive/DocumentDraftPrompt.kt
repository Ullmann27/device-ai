package at.heinz.deviceai.cognitive

/**
 * The local-model task for „erstelle mir eine Zusammenfassung als Entwurf“ (owner flow 06.11.2026). It carries only the
 * text that Android extracted from the owner's PDF, between markers, and tells the model to use nothing else. It fits
 * the complete-task budget of [LocalChatPromptBudget] (characters and UTF-8 bytes); a longer document is cut on a word
 * boundary and [coveredChars] says how much of it the draft is based on – the surface shows that, nothing is implied.
 */
internal data class DocumentDraftPrompt(val request: String, val question: String, val coveredChars: Int, val totalChars: Int,
    val firstPageOnly: Boolean = false) {
    val complete: Boolean get() = coveredChars >= totalChars

    companion object {
        private const val OPEN = "<<<DOKUMENT"
        private const val CLOSE = "DOKUMENT>>>"

        fun build(name: String, text: String, budget: Int = LocalChatPromptBudget.MAX_TASK_CHARACTERS,
            firstPageOnly: Boolean = false): DocumentDraftPrompt? {
            val clean = text.trim()
            if (clean.isEmpty()) return null
            val shown = name.trim().ifEmpty { "Dokument" }.take(80)
            val head = (if (firstPageOnly) "Fasse ausschließlich Seite 1 des Dokuments „$shown“ auf Deutsch zusammen. " +
                "Keine Informationen aus anderen Seiten verwenden. " else
                "Erstelle einen Entwurf: eine Zusammenfassung des Dokuments „$shown“ auf Deutsch. ") +
                "Verwende ausschließlich Aussagen aus dem Text zwischen den Markierungen; erfinde nichts und " +
                "kennzeichne Unklares als offen. Gliederung: Kernaussagen als Stichpunkte, danach eine kurze Zusammenfassung.\n" +
                "$OPEN\n"
            val tail = "\n$CLOSE"
            val room = budget - bytes(head) - bytes(tail)
            if (room < 200) return null
            val excerpt = fit(clean, room)
            return DocumentDraftPrompt(if (firstPageOnly) "Entwurf: Seite 1 von „$shown“" else
                "Entwurf: Zusammenfassung von „$shown“", head + excerpt + tail,
                excerpt.length, clean.length, firstPageOnly)
        }

        private fun bytes(s: String) = s.toByteArray(Charsets.UTF_8).size

        /** The longest prefix within [maxBytes] UTF-8 bytes, cut on whitespace when that keeps most of it. */
        private fun fit(text: String, maxBytes: Int): String {
            if (bytes(text) <= maxBytes) return text
            var end = 0
            var used = 0
            while (end < text.length) {
                val cp = text.codePointAt(end)
                val size = String(Character.toChars(cp)).toByteArray(Charsets.UTF_8).size
                if (used + size > maxBytes) break
                used += size
                end += Character.charCount(cp)
            }
            val space = text.lastIndexOf(' ', end).coerceAtLeast(text.lastIndexOf('\n', end))
            return text.substring(0, if (space >= end * 3 / 4) space else end).trimEnd()
        }
    }
}
