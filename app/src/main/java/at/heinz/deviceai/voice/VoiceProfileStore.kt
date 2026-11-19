package at.heinz.deviceai.voice

/**
 * Persists [VoiceProfileSelection] in the existing "continuum-voice" preferences. The engine reuses the existing
 * `maya_enabled` key, so an installation without the new keys reads exactly as before: former voice, former
 * settings. No secret is stored here (the API key stays in SecureTokenStore).
 */
class VoiceProfileStore(private val values: Values) {
    /** Minimal key-value seam (SharedPreferences in the app, a map in tests). */
    interface Values {
        fun string(key: String): String?
        fun int(key: String, default: Int): Int
        fun boolean(key: String, default: Boolean): Boolean
        fun write(strings: Map<String, String?>, ints: Map<String, Int>, booleans: Map<String, Boolean>): Boolean
    }

    fun load(): VoiceProfileSelection {
        val id = values.string(KEY_VOICE_ID)?.takeIf(VoiceProfiles::validVoiceId)
        return VoiceProfileSelection(
            engine = if (values.boolean(KEY_ENGINE_ELEVENLABS, false)) VoiceEngine.ELEVENLABS else VoiceEngine.DEVICE,
            elevenVoiceId = id,
            elevenVoiceName = if (id == null) null else values.string(KEY_VOICE_NAME)?.take(MAX_NAME),
            preset = ProsodyPreset.fromName(values.string(KEY_PRESET)),
            rateStep = values.int(KEY_RATE_STEP, 0).coerceIn(VoiceProfiles.MIN_RATE_STEP, VoiceProfiles.MAX_RATE_STEP),
            calmStep = values.int(KEY_CALM_STEP, 0).coerceIn(0, VoiceProfiles.MAX_CALM_STEP))
    }

    /** Writes the whole selection; the engine goes to the existing `maya_enabled` key. */
    fun save(selection: VoiceProfileSelection): Boolean {
        val id = selection.elevenVoiceId?.takeIf(VoiceProfiles::validVoiceId)
        return values.write(
            mapOf(KEY_VOICE_ID to id, KEY_VOICE_NAME to if (id == null) null else selection.elevenVoiceName?.take(MAX_NAME),
                KEY_PRESET to selection.preset.name),
            mapOf(KEY_RATE_STEP to selection.rateStep, KEY_CALM_STEP to selection.calmStep),
            mapOf(KEY_ENGINE_ELEVENLABS to (selection.engine == VoiceEngine.ELEVENLABS)))
    }

    companion object {
        const val PREFS = "continuum-voice"
        const val KEY_ENGINE_ELEVENLABS = "maya_enabled"
        const val KEY_VOICE_ID = "voice_profile_eleven_voice_id"
        const val KEY_VOICE_NAME = "voice_profile_eleven_voice_name"
        const val KEY_PRESET = "voice_profile_preset"
        const val KEY_RATE_STEP = "voice_profile_rate_step"
        const val KEY_CALM_STEP = "voice_profile_calm_step"
        private const val MAX_NAME = 80

        fun android(context: android.content.Context): VoiceProfileStore {
            val prefs = context.getSharedPreferences(PREFS, android.content.Context.MODE_PRIVATE)
            return VoiceProfileStore(object : Values {
                override fun string(key: String) = prefs.getString(key, null)
                override fun int(key: String, default: Int) = prefs.getInt(key, default)
                override fun boolean(key: String, default: Boolean) = prefs.getBoolean(key, default)
                override fun write(strings: Map<String, String?>, ints: Map<String, Int>,
                                   booleans: Map<String, Boolean>): Boolean {
                    val edit = prefs.edit()
                    strings.forEach { (k, v) -> if (v == null) edit.remove(k) else edit.putString(k, v) }
                    ints.forEach { (k, v) -> edit.putInt(k, v) }
                    booleans.forEach { (k, v) -> edit.putBoolean(k, v) }
                    return edit.commit()
                }
            })
        }
    }
}
