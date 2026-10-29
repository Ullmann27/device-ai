package at.heinz.deviceai

import java.util.Locale

/**
 * Recognizes explicit questions about weaknesses of this installed Continuum app.
 * Recognition alone never authorizes Git access, source edits, a network request,
 * training, or a device action.
 */
internal object ContinuumSelfInspectionQuery {
    private val project = Regex("""\b(?:continuum|kontinuum|cotino|unsere[rmn]?\s+(?:app|ki|software)|deine[rmn]?\s+(?:app|ki|software)|dein(?:em|en|er)?\s+code|unser(?:em|en|er)?\s+code)\b""")
    private val weaknesses = Regex("""\b(?:schwachstellen|schwächen|fehler|probleme|codeprobleme|codefehler|bugs|verbesserungspotenzial)\b""")
    private val examination = Regex("""\b(?:wo|welche|was|zeige|analysiere|untersuche|prüfe|finde|nenne|erkläre|identifiziere)\b""")

    fun matches(text: String): Boolean {
        if (text.length !in 6..500) return false
        val normalized = text.lowercase(Locale.GERMAN).trim()
            .replace(Regex("""\s+"""), " ")
        return project.containsMatchIn(normalized) &&
            weaknesses.containsMatchIn(normalized) &&
            examination.containsMatchIn(normalized)
    }
}
