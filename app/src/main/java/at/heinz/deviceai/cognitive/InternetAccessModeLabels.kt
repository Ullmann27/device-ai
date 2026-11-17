package at.heinz.deviceai.cognitive

/**
 * Owner-visible labels for the Internet settings dialog.
 * The word "Standard" may mark only the mode that [InternetConsentPolicy.restoredMode]
 * returns for a missing preference (fresh install). This object never changes that default.
 */
internal object InternetAccessModeLabels {
    fun option(mode: InternetAccessMode): String = when (mode) {
        InternetAccessMode.ASK -> "Auf Nachfrage"
        InternetAccessMode.ALWAYS_ON -> "Dauerhaft erlauben · ALWAYS_ON (Standard)"
        InternetAccessMode.OFF -> "Aus (kein Webzugriff)"
    }

    fun dialogOptions(modes: List<InternetAccessMode>): Array<String> =
        modes.map(::option).toTypedArray()

    /** The single mode whose settings label is marked as Standard. */
    fun advertisedDefault(): InternetAccessMode {
        val marked = InternetAccessMode.entries.filter { option(it).contains("Standard") }
        check(marked.size == 1) { "Exactly one Internet mode may be labelled Standard." }
        return marked.single()
    }
}
