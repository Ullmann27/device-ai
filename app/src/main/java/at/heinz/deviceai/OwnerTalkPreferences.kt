package at.heinz.deviceai

import android.content.Context
import at.heinz.deviceai.cognitive.OwnerConversationTone

/**
 * [OwnerTalkStore] in the app's private preferences. It holds the application context only, so the process-wide
 * cloud brain can read the tone without keeping an Activity alive. Nothing here is sent anywhere.
 */
internal class OwnerTalkPreferences(context: Context) : OwnerTalkStore {
    private val preferences = context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    override var tone: OwnerConversationTone
        get() = OwnerConversationTone.parse(preferences.getString("tone", null)) ?: OwnerConversationTone.DEFAULT
        set(value) { preferences.edit().putString("tone", value.name).apply() }

    override var returnGreeting: Boolean
        get() = preferences.getBoolean("return_greeting", true)
        set(value) { preferences.edit().putBoolean("return_greeting", value).apply() }

    override var name: OwnerTalkName?
        get() {
            val name = preferences.getString("name", null)?.takeIf { it.isNotBlank() } ?: return null
            return OwnerTalkName(name, preferences.getString("name_statement", null).orEmpty(), preferences.getLong("name_at", 0L))
        }
        set(value) {
            preferences.edit().apply {
                if (value == null) remove("name").remove("name_statement").remove("name_at")
                else putString("name", value.name).putString("name_statement", value.statement).putLong("name_at", value.atMillis)
            }.apply()
        }

    override var addressByName: Boolean
        get() = preferences.getBoolean("address_by_name", true)
        set(value) { preferences.edit().putBoolean("address_by_name", value).apply() }

    override var nameToCloud: Boolean
        get() = preferences.getBoolean("name_to_cloud", false)
        set(value) { preferences.edit().putBoolean("name_to_cloud", value).apply() }

    override var lastSeenMillis: Long
        get() = preferences.getLong("last_seen", 0L)
        set(value) { preferences.edit().putLong("last_seen", value).apply() }

    override var greetingVariant: Int
        get() = preferences.getInt("greeting_variant", -1)
        set(value) { preferences.edit().putInt("greeting_variant", value).apply() }

    override var greetingCount: Int
        get() = preferences.getInt("greeting_count", 0)
        set(value) { preferences.edit().putInt("greeting_count", value).apply() }

    override var lastTurnMillis: Long
        get() = preferences.getLong("last_turn", 0L)
        set(value) { preferences.edit().putLong("last_turn", value).apply() }

    override var previousTalkMillis: Long
        get() = preferences.getLong("previous_talk", 0L)
        set(value) { preferences.edit().putLong("previous_talk", value).apply() }

    override var greetingShapes: String
        get() = preferences.getString("greeting_shapes", null).orEmpty()
        set(value) { preferences.edit().putString("greeting_shapes", value.take(80)).apply() }

    companion object {
        const val FILE = "continuum-owner-talk"
    }
}

/**
 * The shared-work record and the initiative policy's memory in the app's private preferences (application context
 * only). Both are bounded texts; nothing here is synced, exported or sent.
 */
internal class OwnerSharedWorkPreferences(context: Context) : at.heinz.deviceai.cognitive.OwnerSharedWorkStore {
    private val preferences = context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)
    override fun load(): String? = preferences.getString("entries", null)
    override fun save(text: String?) {
        preferences.edit().apply { if (text == null) remove("entries") else putString("entries", text) }.apply()
    }

    companion object {
        const val FILE = "continuum-owner-shared-work"
    }
}

internal class OwnerInitiativePreferences(context: Context) : at.heinz.deviceai.cognitive.OwnerInitiativeStore {
    private val preferences = context.applicationContext.getSharedPreferences(OwnerTalkPreferences.FILE, Context.MODE_PRIVATE)

    override var level: at.heinz.deviceai.cognitive.OwnerInitiativeLevel
        get() = at.heinz.deviceai.cognitive.OwnerInitiativeLevel.parse(preferences.getString("initiative_level", null))
            ?: at.heinz.deviceai.cognitive.OwnerInitiativeLevel.DEFAULT
        set(value) { preferences.edit().putString("initiative_level", value.name).apply() }

    override var memory: String?
        get() = preferences.getString("initiative_memory", null)
        set(value) { preferences.edit().apply { if (value == null) remove("initiative_memory") else putString("initiative_memory", value) }.apply() }
}
