package at.heinz.deviceai.notifications

import android.content.Context
import android.content.Intent
import android.provider.Settings
import org.json.JSONObject

/**
 * Owner settings for spoken notification announcements, stored in the private preferences file
 * [PREFERENCES]. Only the owner's choices (switches, package names, modes) are stored; notification
 * content never is.
 */
object NotificationAnnouncementSettings {
    const val PREFERENCES = "continuum-notifications"
    private const val KEY_SETTINGS = "announcement-settings-v1"
    private const val ENABLED_LISTENERS = "enabled_notification_listeners"

    /** Mode proposed for the messenger suggestions; the owner can change it per app. */
    val SUGGESTED_MESSENGER_MODE = AnnouncementMode.SENDER

    /**
     * Well-known messenger packages offered as suggestions only. Nothing is enabled until the owner
     * confirms; banking and all other apps are picked by the owner from the installed-app list.
     */
    val knownMessengerPackages: List<String> get() = NotificationAnnouncementPolicy.KNOWN_MESSENGER_PACKAGES

    @Volatile
    private var cached: Pair<String?, AnnouncementSettings>? = null

    fun load(context: Context): AnnouncementSettings {
        val raw = preferences(context).getString(KEY_SETTINGS, null)
        cached?.let { if (it.first == raw) return it.second }
        return AnnouncementSettingsCodec.decode(raw).also { cached = raw to it }
    }

    fun save(context: Context, settings: AnnouncementSettings) {
        val raw = AnnouncementSettingsCodec.encode(settings)
        preferences(context).edit().putString(KEY_SETTINGS, raw).apply()
        cached = raw to AnnouncementSettingsCodec.decode(raw)
        // Turning announcements off also forgets what is still buffered in memory.
        if (!settings.enabled) ContinuumNotificationBus.clear()
    }

    fun update(context: Context, change: (AnnouncementSettings) -> AnnouncementSettings): AnnouncementSettings {
        val updated = AnnouncementSettingsCodec.normalize(change(load(context)))
        save(context, updated)
        return updated
    }

    fun setEnabled(context: Context, enabled: Boolean): AnnouncementSettings =
        update(context) { it.copy(enabled = enabled) }

    /** [AnnouncementMode.OFF] removes the rule for [packageName]. */
    fun setAppMode(context: Context, packageName: String, mode: AnnouncementMode): AnnouncementSettings =
        update(context) {
            it.copy(apps = if (mode == AnnouncementMode.OFF) it.apps - packageName else it.apps + (packageName to AppRule(mode)))
        }

    /** Suggested rules for those known messengers that are in [installedPackages]. Not saved. */
    fun messengerSuggestions(
        installedPackages: Set<String>,
        mode: AnnouncementMode = SUGGESTED_MESSENGER_MODE,
    ): Map<String, AppRule> =
        knownMessengerPackages.filter { it in installedPackages }.associateWith { AppRule(mode) }

    /** Owner-facing German name of a mode for the settings UI. */
    fun modeLabel(mode: AnnouncementMode): String = when (mode) {
        AnnouncementMode.OFF -> "Aus"
        AnnouncementMode.APP_ONLY -> "Nur App nennen"
        AnnouncementMode.SENDER -> "App und Absender nennen"
        AnnouncementMode.FULL -> "Inhalt vorlesen"
    }

    /** True when the owner granted notification access to this app's listener in Android settings. */
    fun isAccessGranted(context: Context): Boolean {
        val enabled = try {
            Settings.Secure.getString(context.contentResolver, ENABLED_LISTENERS)
        } catch (_: RuntimeException) {
            null
        }
        return AnnouncementSettingsCodec.listsComponent(
            enabled, context.packageName, ContinuumNotificationListener::class.java.name,
        )
    }

    /** Opens Android's notification-access screen; access can only be granted there by the owner. */
    fun accessSettingsIntent(): Intent =
        Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    private fun preferences(context: Context) =
        context.applicationContext.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
}

/** JSON form of [AnnouncementSettings] and parsing helpers; free of Android framework calls. */
internal object AnnouncementSettingsCodec {
    const val MAX_APPS = 64
    private const val VERSION = 1
    private val PACKAGE_NAME = Regex("[A-Za-z][A-Za-z0-9_]*(?:\\.[A-Za-z0-9_]+)+")

    fun encode(settings: AnnouncementSettings): String {
        val normalized = normalize(settings)
        val apps = JSONObject()
        normalized.apps.forEach { (packageName, rule) -> apps.put(packageName, rule.mode.name) }
        return JSONObject()
            .put("v", VERSION)
            .put("enabled", normalized.enabled)
            .put("quietWhenNotInConversation", normalized.quietWhenNotInConversation)
            .put("maxPerMinute", normalized.maxPerMinute)
            .put("apps", apps)
            .toString()
    }

    /** Anything unreadable falls back to the private default: disabled, no apps. */
    fun decode(raw: String?): AnnouncementSettings {
        if (raw.isNullOrBlank()) return AnnouncementSettings()
        return try {
            val json = JSONObject(raw)
            if (json.optInt("v", 0) != VERSION) return AnnouncementSettings()
            val apps = LinkedHashMap<String, AppRule>()
            json.optJSONObject("apps")?.let { stored ->
                for (packageName in stored.keys()) {
                    val mode = AnnouncementMode.entries.firstOrNull { it.name == stored.optString(packageName, "") }
                    if (mode != null) apps[packageName] = AppRule(mode)
                }
            }
            normalize(
                AnnouncementSettings(
                    enabled = json.optBoolean("enabled", false),
                    apps = apps,
                    quietWhenNotInConversation = json.optBoolean("quietWhenNotInConversation", true),
                    maxPerMinute = json.optInt("maxPerMinute", NotificationAnnouncementPolicy.DEFAULT_MAX_PER_MINUTE),
                ),
            )
        } catch (_: Exception) {
            AnnouncementSettings()
        }
    }

    /** Drops OFF rules and invalid package names, bounds the number of apps and the rate limit. */
    fun normalize(settings: AnnouncementSettings): AnnouncementSettings = settings.copy(
        apps = settings.apps.entries.asSequence()
            .filter { it.value.mode != AnnouncementMode.OFF && it.key.length <= 255 && PACKAGE_NAME.matches(it.key) }
            .take(MAX_APPS)
            .associate { it.key to it.value },
        maxPerMinute = settings.maxPerMinute.coerceIn(1, NotificationAnnouncementPolicy.MAX_PER_MINUTE_CEILING),
    )

    /**
     * True when the colon-separated flattened component list of Settings.Secure
     * "enabled_notification_listeners" names [className] in [packageName]; both the full
     * ("pkg/pkg.Class") and the short ("pkg/.Class") spelling are accepted.
     */
    fun listsComponent(flattened: String?, packageName: String, className: String): Boolean {
        if (flattened.isNullOrBlank()) return false
        return flattened.split(':').any { entry ->
            val slash = entry.indexOf('/')
            if (slash <= 0) return@any false
            val entryPackage = entry.substring(0, slash).trim()
            val entryClass = entry.substring(slash + 1).trim()
            entryPackage == packageName &&
                (if (entryClass.startsWith(".")) entryPackage + entryClass else entryClass) == className
        }
    }
}
