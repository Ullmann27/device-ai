package at.heinz.deviceai.devicetools

import android.content.Context
import at.heinz.deviceai.cognitive.OwnerAppLookupIntent
import java.util.Locale

/**
 * The owner's three switches of the device tools. All are OFF until he says otherwise:
 *  - [cloudNames]: with the cloud brain, results that carry names of his files or apps may go to it;
 *  - [cloudContent]: with the cloud brain, content read from his device (a file's text, the clipboard) may go to it;
 *  - [documentsWithoutAsking]: a document he ordered in his own sentence is created without the confirmation dialog.
 * The on-device model needs none of the first two: nothing leaves the device there.
 */
internal interface DeviceToolSettings {
    var cloudNames: Boolean
    var cloudContent: Boolean
    var documentsWithoutAsking: Boolean
}

internal class MemoryDeviceToolSettings(override var cloudNames: Boolean = false, override var cloudContent: Boolean = false,
                                        override var documentsWithoutAsking: Boolean = false) : DeviceToolSettings

/** App-private preferences; nothing here is synced, exported or sent. */
internal class StoredDeviceToolSettings(context: Context) : DeviceToolSettings {
    private val preferences = context.applicationContext.getSharedPreferences(NAME, Context.MODE_PRIVATE)
    override var cloudNames: Boolean
        get() = preferences.getBoolean(KEY_NAMES, false)
        set(value) { preferences.edit().putBoolean(KEY_NAMES, value).apply() }
    override var cloudContent: Boolean
        get() = preferences.getBoolean(KEY_CONTENT, false)
        set(value) { preferences.edit().putBoolean(KEY_CONTENT, value).apply() }
    override var documentsWithoutAsking: Boolean
        get() = preferences.getBoolean(KEY_DOCUMENTS, false)
        set(value) { preferences.edit().putBoolean(KEY_DOCUMENTS, value).apply() }

    companion object {
        const val NAME = "continuum-device-tools"
        const val KEY_NAMES = "cloud_names_consent"
        const val KEY_CONTENT = "cloud_content_consent"
        const val KEY_DOCUMENTS = "documents_without_asking"
    }
}

/**
 * The typed or spoken commands for those switches. Fail closed: only the exact command shapes below switch anything;
 * a question, a negation or a sentence with something else in it is not a command and keeps its ordinary route.
 */
internal object DeviceToolCommand {
    enum class Command { NAMES_ON, NAMES_OFF, CONTENT_ON, CONTENT_OFF, DOCS_ON, DOCS_OFF, STATUS, FILE_ACCESS }

    const val SAY_NAMES_ON = "Dateinamen an Claude erlauben"
    const val SAY_NAMES_OFF = "Dateinamen an Claude sperren"
    const val SAY_CONTENT_ON = "Dateiinhalte an Claude erlauben"
    const val SAY_CONTENT_OFF = "Dateiinhalte an Claude sperren"
    const val SAY_DOCS_ON = "Dokumente ohne Rückfrage anlegen an"
    const val SAY_DOCS_OFF = "Dokumente ohne Rückfrage anlegen aus"
    const val SAY_STATUS = "Werkzeugfreigaben zeigen"
    const val SAY_FILE_ACCESS = "Dateizugriff einrichten"

    private const val NAMES = "(?:datei-?\\s?(?:und\\s+app-?)?namen|app-?namen|dateinamen\\s+und\\s+app-?namen)"
    private const val CONTENT = "(?:datei-?inhalte?|inhalte\\s+(?:von\\s+|meiner\\s+)?dateien)"
    private const val TARGET = "(?:claude|die\\s+cloud|das\\s+cloud-?gehirn)"
    private const val MY = "(?:(?:die|meine)\\s+)?"
    private const val ALLOW = "(?:erlauben|freigeben|zulassen)"
    private const val DENY = "(?:sperren|verbieten|widerrufen|nicht\\s+mehr\\s+erlauben)"
    private const val SEE = "(?:sehen|lesen|bekommen|erhalten)"

    private fun switchPatterns(subject: String): Pair<List<Regex>, List<Regex>> = listOf(
        Regex("^$MY$subject\\s+(?:an|für)\\s+$TARGET\\s+$ALLOW$"),
        Regex("^erlaube\\s+$TARGET\\s+$MY$subject$"),
        Regex("^$TARGET\\s+darf\\s+$MY$subject\\s+$SEE$")
    ) to listOf(
        Regex("^$MY$subject\\s+(?:an|für)\\s+$TARGET\\s+$DENY$"),
        Regex("^verbiete\\s+$TARGET\\s+$MY$subject$"),
        Regex("^$TARGET\\s+darf\\s+keine\\s+$subject\\s+(?:mehr\\s+)?$SEE$")
    )

    private val namePatterns = switchPatterns(NAMES)
    private val contentPatterns = switchPatterns(CONTENT)
    private const val DOCS = "dokumente\\s+ohne\\s+rückfrage"
    private val docs = listOf(
        Regex("^$DOCS\\s+(?:in\\s+(?:den\\s+)?downloads?\\s+)?(?:anlegen|erstellen|speichern)\\s+(an|ein|aus|ab|einschalten|ausschalten)$"),
        Regex("^(?:schalte\\s+)?$DOCS\\s+(an|ein|aus|ab|einschalten|ausschalten)$"))
    private val statusPatterns = listOf(
        Regex("^(?:zeig(?:e)?\\s+(?:mir\\s+)?(?:die\\s+)?)?werkzeugfreigaben(?:\\s+(?:zeigen|anzeigen))?\\??$"),
        Regex("^welche\\s+werkzeugfreigaben\\s+(?:sind\\s+aktiv|gelten|habe\\s+ich)\\??$"))
    private val fileAccess = listOf(
        Regex("^dateizugriff\\s+(?:einrichten|erlauben|öffnen|freigeben)$"),
        Regex("^zugriff\\s+auf\\s+alle\\s+dateien\\s+(?:einrichten|erlauben|öffnen|freigeben)$"))

    fun parse(utterance: String, assistantName: String? = null): Command? {
        if (utterance.length !in 8..120 || utterance.any { it.isISOControl() }) return null
        val text = OwnerAppLookupIntent.stripAddress(utterance.trim(), assistantName).lowercase(Locale.GERMAN)
            .replace(Regex("^bitte\\s+"), "").replace(Regex("\\s+bitte(?=[.!]*$)"), "").replace(Regex("\\s+"), " ").trim()
        statusPatterns.forEach { if (it.matches(text.trimEnd('.', '!'))) return Command.STATUS }
        // A question mark never switches anything.
        if ('?' in text) return null
        val plain = text.trimEnd('.', '!')
        if (namePatterns.first.any { it.matches(plain) }) return Command.NAMES_ON
        if (namePatterns.second.any { it.matches(plain) }) return Command.NAMES_OFF
        if (contentPatterns.first.any { it.matches(plain) }) return Command.CONTENT_ON
        if (contentPatterns.second.any { it.matches(plain) }) return Command.CONTENT_OFF
        docs.firstNotNullOfOrNull { it.matchEntire(plain) }?.let { match ->
            return if (match.groupValues[1] in setOf("an", "ein", "einschalten")) Command.DOCS_ON else Command.DOCS_OFF
        }
        if (fileAccess.any { it.matches(plain) }) return Command.FILE_ACCESS
        return null
    }

    /** Applies a switch command to [settings] and words the new state. [Command.FILE_ACCESS] is the host's to open. */
    fun apply(command: Command, settings: DeviceToolSettings): String = when (command) {
        Command.NAMES_ON -> { settings.cloudNames = true
            "Erlaubt: Wenn Claude als Gehirn gewählt ist, dürfen Ergebnisse mit den Namen deiner Dateien und Apps (Suchen, " +
                "Ordnerlisten, App-Listen) an Claude gehen. Inhalte von Dateien bleiben davon getrennt gesperrt. " +
                "Zurücknehmen: „$SAY_NAMES_OFF“." }
        Command.NAMES_OFF -> { settings.cloudNames = false
            "Gesperrt: Namen deiner Dateien und Apps gehen nicht mehr an Claude. Das lokale Modell kann weiter suchen und " +
                "auflisten – dabei verlässt nichts dein Gerät." }
        Command.CONTENT_ON -> { settings.cloudContent = true
            "Erlaubt: Wenn Claude als Gehirn gewählt ist, darf der Inhalt, den ich auf deinen Auftrag von deinem Gerät lese " +
                "(Text einer Datei, Fundzeilen, Zwischenablage), an Claude gehen. Zurücknehmen: „$SAY_CONTENT_OFF“." }
        Command.CONTENT_OFF -> { settings.cloudContent = false
            "Gesperrt: Inhalte von deinem Gerät gehen nicht mehr an Claude. Das lokale Modell kann Dateien weiter lesen – " +
                "dabei verlässt nichts dein Gerät." }
        Command.DOCS_ON -> { settings.documentsWithoutAsking = true
            "An: Ein Dokument, das du in deinem Auftrag selbst verlangst, lege ich ohne Rückfrage als neue Datei im " +
                "Download-Ordner an – höchstens ${DeviceToolPolicy.MAX_DOCUMENTS_PER_TURN} je Auftrag, nie über eine " +
                "vorhandene Datei. Zurücknehmen: „$SAY_DOCS_OFF“." }
        Command.DOCS_OFF -> { settings.documentsWithoutAsking = false
            "Aus: Vor jedem neuen Dokument frage ich wieder nach deiner Freigabe." }
        Command.STATUS -> status(settings, null)
        Command.FILE_ACCESS -> ""
    }

    /** The present state of all three switches and, when known, of Android's file grant. */
    fun status(settings: DeviceToolSettings, filesAccess: Boolean?): String = buildString {
        fun line(label: String, on: Boolean, say: String) =
            append("– ").append(label).append(": ").append(if (on) "erlaubt" else "gesperrt").append(" · ändern mit „").append(say).append("“\n")
        append("Werkzeugfreigaben\n")
        line("Namen von Dateien und Apps an Claude", settings.cloudNames, if (settings.cloudNames) SAY_NAMES_OFF else SAY_NAMES_ON)
        line("Inhalte von deinem Gerät an Claude", settings.cloudContent, if (settings.cloudContent) SAY_CONTENT_OFF else SAY_CONTENT_ON)
        append("– Dokumente ohne Rückfrage anlegen: ").append(if (settings.documentsWithoutAsking) "an" else "aus")
            .append(" · ändern mit „").append(if (settings.documentsWithoutAsking) SAY_DOCS_OFF else SAY_DOCS_ON).append("“\n")
        filesAccess?.let {
            append("– Android „Zugriff auf alle Dateien“: ").append(if (it) "erlaubt" else "nicht erlaubt · „$SAY_FILE_ACCESS“ öffnet die Seite").append('\n')
        }
        append("Mit dem lokalen Modell verlässt bei keinem Werkzeug etwas dein Gerät; die beiden Cloud-Freigaben gelten nur, " +
            "wenn Claude als Gehirn gewählt ist.")
    }
}
