package at.heinz.deviceai.learning

/**
 * Explains the scope of the read-only personal-learning preview without
 * implying that research observations were lost or restored.
 * This class performs no I/O and never changes trust labels or records.
 */
internal object PersonalLearningPreviewCopy {
    fun explainReviewOnly(count: Int): String {
        require(count >= 0) { "Negative preview count" }
        return "\nWeitere protokollierte Datensätze (in der Sicherung vorhanden): $count" +
            "\nDiese Einträge sind Forschungs-, Trainings- oder andere nicht als Owner-Fakt " +
            "übernehmbare Datensätze. Die Vorschau löscht sie nicht und importiert sie nicht." +
            "\nDies ist KEINE vollständige Wiederherstellung: Chats, Startkern-Fakten, " +
            "Einstellungen, Quellenarchive, Modellgewichte und gerätegebundene Identität " +
            "sind nicht Bestandteil dieses Lernprotokolls." +
            "\nEs wurden keine Zieldaten geschrieben. Ein Restore wurde nicht durchgeführt."
    }
}
