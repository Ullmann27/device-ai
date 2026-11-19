package at.heinz.deviceai.notifications

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** What happens with a message that is neither clearly important nor clearly advertising. */
enum class UnclearHandling {
    /**
     * Default. The message is offered in one short sentence that says it could not be classified. Reason: staying
     * silent fails where the owner cannot notice it (a letter from an institute the word lists do not know), while a
     * short question fails where he notices at once and can end it with one sentence („Melde … nie“).
     */
    BRIEF,
    SILENT,
}

/**
 * The owner's choices for „Eingehende Nachrichten melden“. Private by default: [enabled] is false, so no notification
 * of any app is looked at. Only choices are stored (switches, package names, his two word lists) – never content.
 */
data class IncomingMessageSettings(
    val enabled: Boolean = false,
    /** Packages whose notifications are looked at. The owner's list; empty until he switches the feature on. */
    val apps: List<String> = emptyList(),
    val groups: Boolean = false,
    /** Also report notifications that arrived without sound (muted chats). */
    val silentNotifications: Boolean = false,
    val unclear: UnclearHandling = UnclearHandling.BRIEF,
    /** „Immer melden“: sender, group or subject words. */
    val always: List<String> = emptyList(),
    /** „Nie melden“. */
    val never: List<String> = emptyList(),
    val quietHours: Boolean = true,
    val quietFromMinute: Int = 22 * 60,
    val quietUntilMinute: Int = 7 * 60,
    /** Continuum's own notification with „Vorlesen“ / „Später“ while the app is not in front. */
    val backgroundCard: Boolean = true,
    /** One short spoken sentence from the service while the app is not in front. Off by default. */
    val backgroundVoice: Boolean = false,
) {
    val rules: ImportanceRules get() = ImportanceRules(always, never, groups)

    /** True inside the quiet hours; a window over midnight (22–7) is handled, equal ends mean „never“. */
    fun quietAt(minuteOfDay: Int): Boolean {
        if (!quietHours || quietFromMinute == quietUntilMinute) return false
        return if (quietFromMinute < quietUntilMinute) minuteOfDay in quietFromMinute until quietUntilMinute
        else minuteOfDay >= quietFromMinute || minuteOfDay < quietUntilMinute
    }
}

/** JSON form of [IncomingMessageSettings]; free of Android framework calls. Unreadable input is the private default. */
internal object IncomingMessageSettingsCodec {
    const val MAX_APPS = 24
    const val MAX_LIST = 40
    const val MAX_ENTRY = 60
    private const val VERSION = 1
    private val PACKAGE_NAME = Regex("[A-Za-z][A-Za-z0-9_]*(?:\\.[A-Za-z0-9_]+)+")

    fun encode(settings: IncomingMessageSettings): String = normalize(settings).let { s ->
        JSONObject().put("v", VERSION).put("enabled", s.enabled).put("apps", JSONArray(s.apps))
            .put("groups", s.groups).put("silent", s.silentNotifications).put("unclear", s.unclear.name)
            .put("always", JSONArray(s.always)).put("never", JSONArray(s.never))
            .put("quietHours", s.quietHours).put("quietFrom", s.quietFromMinute).put("quietUntil", s.quietUntilMinute)
            .put("backgroundCard", s.backgroundCard).put("backgroundVoice", s.backgroundVoice).toString()
    }

    fun decode(raw: String?): IncomingMessageSettings {
        if (raw.isNullOrBlank()) return IncomingMessageSettings()
        return try {
            val json = JSONObject(raw)
            if (json.optInt("v", 0) != VERSION) return IncomingMessageSettings()
            fun list(name: String): List<String> = json.optJSONArray(name)?.let { array ->
                (0 until array.length()).mapNotNull { array.opt(it) as? String }
            } ?: emptyList()
            val defaults = IncomingMessageSettings()
            normalize(IncomingMessageSettings(
                enabled = json.optBoolean("enabled", false),
                apps = list("apps"),
                groups = json.optBoolean("groups", false),
                silentNotifications = json.optBoolean("silent", false),
                unclear = UnclearHandling.entries.firstOrNull { it.name == json.optString("unclear") } ?: defaults.unclear,
                always = list("always"),
                never = list("never"),
                quietHours = json.optBoolean("quietHours", defaults.quietHours),
                quietFromMinute = json.optInt("quietFrom", defaults.quietFromMinute),
                quietUntilMinute = json.optInt("quietUntil", defaults.quietUntilMinute),
                backgroundCard = json.optBoolean("backgroundCard", defaults.backgroundCard),
                backgroundVoice = json.optBoolean("backgroundVoice", false),
            ))
        } catch (_: Exception) {
            IncomingMessageSettings()
        }
    }

    /** Valid package names only, bounded lists, cleaned list entries, minutes within one day. */
    fun normalize(settings: IncomingMessageSettings): IncomingMessageSettings = settings.copy(
        apps = settings.apps.filter { it.length <= 255 && PACKAGE_NAME.matches(it) }.distinct().take(MAX_APPS),
        always = entries(settings.always),
        never = entries(settings.never),
        quietFromMinute = settings.quietFromMinute.coerceIn(0, 24 * 60 - 1),
        quietUntilMinute = settings.quietUntilMinute.coerceIn(0, 24 * 60 - 1),
    )

    private fun entries(raw: List<String>): List<String> = raw.asSequence()
        .map { UntrustedNotificationText.clean(it).take(MAX_ENTRY).trim() }
        .filter { it.length >= 2 }.distinctBy { it.lowercase() }.take(MAX_LIST).toList()
}

/** The owner's settings in the private preferences file [PREFERENCES]. */
object IncomingMessagePreferences {
    const val PREFERENCES = "continuum-incoming-messages"
    private const val KEY_SETTINGS = "incoming-settings-v1"

    /** Suggested when the owner switches the feature on for the first time; he can change the list at any time. */
    val SUGGESTED_APPS: List<String> = listOf("com.whatsapp", "com.whatsapp.w4b", "com.google.android.gm")

    /** Shown before the switch is turned on, word for word. */
    const val EXPLANATION = "Wenn du das einschaltest, sieht Continuum über die Continuum-Bedienungshilfe die " +
        "Benachrichtigungen der Apps, die du hier auswählst – Absender und den Text, den die Benachrichtigung zeigt. " +
        "Damit fragt Continuum dich, ob es eine neue Nachricht vorlesen soll. Alles bleibt auf diesem Gerät: nichts " +
        "davon geht an ein Sprachmodell oder ins Internet, nichts wird protokolliert, und offene Nachrichten werden " +
        "nur im Arbeitsspeicher gehalten und nach zwölf Stunden verworfen. Benachrichtigungen anderer Apps werden " +
        "nicht gelesen. Gesendet wird nur, was du diktierst und bestätigst."

    @Volatile
    private var cached: Pair<String?, IncomingMessageSettings>? = null

    fun load(context: Context): IncomingMessageSettings {
        val raw = preferences(context).getString(KEY_SETTINGS, null)
        cached?.let { if (it.first == raw) return it.second }
        return IncomingMessageSettingsCodec.decode(raw).also { cached = raw to it }
    }

    fun save(context: Context, settings: IncomingMessageSettings): IncomingMessageSettings {
        val raw = IncomingMessageSettingsCodec.encode(settings)
        preferences(context).edit().putString(KEY_SETTINGS, raw).apply()
        return IncomingMessageSettingsCodec.decode(raw).also { cached = raw to it }
    }

    fun update(context: Context, change: (IncomingMessageSettings) -> IncomingMessageSettings): IncomingMessageSettings =
        save(context, change(load(context)))

    private fun preferences(context: Context) =
        context.applicationContext.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
}
