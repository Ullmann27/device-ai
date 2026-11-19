package at.heinz.deviceai.localmodel

import android.content.Context

/** Which language model answers through the one [LocalModelRuntime] interface. LOCAL is the default. */
enum class BrainKind { LOCAL, CLAUDE }

/**
 * Owner-selectable Claude models. [id] is sent to the API exactly as written (no date suffix).
 * [serverSideFallback]: Claude Haiku 5.5 has no server-side refusal fallback, so the `fallbacks`
 * parameter is never sent for it.
 */
enum class ClaudeModel(val id: String, val label: String, val serverSideFallback: Boolean) {
    OPUS_5_5("claude-opus-5-5", "Claude Opus 5.5", true),
    SONNET_5_5("claude-sonnet-5-5", "Claude Sonnet 5.5", true),
    HAIKU_5_5("claude-haiku-5-5", "Claude Haiku 5.5", false),
    FABLE_5_2("claude-fable-5-2", "Claude Fable 5.2", true);

    companion object {
        val DEFAULT = OPUS_5_5
        fun fromId(id: String?): ClaudeModel? = entries.firstOrNull { it.id == id }
    }
}

/** `output_config.effort`; always sent explicitly because the API default differs between models. */
enum class ClaudeEffort(val wire: String, val label: String) {
    LOW("low", "niedrig"), MEDIUM("medium", "mittel"), HIGH("high", "hoch");

    companion object {
        /** Dialogue turns without tools. */
        val DIALOGUE = LOW
        /** Tool turns, tool continuations and isolated reasoning passes. */
        val WORK = MEDIUM
        fun parse(raw: String?): ClaudeEffort? = entries.firstOrNull { it.name == raw }
    }
}

/**
 * Everything the owner decided about the cloud brain. Nothing here is a secret: the API key lives
 * only in [SecretStore]. [consentGiven] is the explicit confirmation of the data-transfer notice;
 * without it no request is ever sent, whatever [brain] says.
 */
data class ClaudeBrainSettings(
    val brain: BrainKind = BrainKind.LOCAL,
    val model: ClaudeModel = ClaudeModel.DEFAULT,
    /** null = automatic: [ClaudeEffort.DIALOGUE] for dialogue, [ClaudeEffort.WORK] for work turns. */
    val effortOverride: ClaudeEffort? = null,
    val consentGiven: Boolean = false,
    val consentAtMillis: Long = 0L,
    /**
     * Separate, explicit opt-in: memory records the app classifies as DEVICE_LOCAL_ONLY (confirmed owner
     * facts, device observations, earlier runs) may be part of a prompt for Claude. Off by default.
     */
    val shareLocalMemory: Boolean = false,
    /** Local monthly estimate limit in whole US cents; requests are blocked once it is reached. */
    val monthlyLimitCents: Int = DEFAULT_MONTHLY_LIMIT_CENTS,
    /**
     * Two separate owner switches, both off by default (owner order 19.11.2026: „Ja bau das ein“): Anthropic's own
     * web search and page fetch, and its code-execution sandbox. They cost extra API credit and only ever take effect
     * while the cloud brain is selected and the consent is stored ([ClaudeServerToolPolicy]).
     */
    val cloudWebSearch: Boolean = false,
    val cloudCodeExecution: Boolean = false
) {
    init {
        require(monthlyLimitCents in MIN_MONTHLY_LIMIT_CENTS..MAX_MONTHLY_LIMIT_CENTS)
        // The cloud brain and the memory opt-in cannot even be represented without the stored consent.
        require(brain == BrainKind.LOCAL || consentGiven) { "Claude needs the owner's stored consent." }
        require(!shareLocalMemory || consentGiven) { "Sharing local memory needs the owner's stored consent." }
        require(!(cloudWebSearch || cloudCodeExecution) || consentGiven) { "Cloud tools need the owner's stored consent." }
    }

    /** The cloud brain is the selected runtime. It may still be unusable (no key, no credit, offline). */
    val claudeSelected: Boolean get() = brain == BrainKind.CLAUDE

    fun effortFor(work: Boolean): ClaudeEffort =
        effortOverride ?: if (work) ClaudeEffort.WORK else ClaudeEffort.DIALOGUE

    companion object {
        const val DEFAULT_MONTHLY_LIMIT_CENTS = 2_500
        const val MIN_MONTHLY_LIMIT_CENTS = 100
        const val MAX_MONTHLY_LIMIT_CENTS = 100_000
    }
}

/** Small persistence port so the runtime and its tests do not depend on Android. */
interface ClaudeSettingsStore {
    fun load(): ClaudeBrainSettings
    /** Returns false when the change could not be stored; the previous settings then stay in force. */
    fun save(settings: ClaudeBrainSettings): Boolean
}

class InMemoryClaudeSettingsStore(initial: ClaudeBrainSettings = ClaudeBrainSettings()) : ClaudeSettingsStore {
    @Volatile private var current = initial
    override fun load(): ClaudeBrainSettings = current
    override fun save(settings: ClaudeBrainSettings): Boolean { current = settings; return true }
}

/** App-private preferences. A value that cannot be read falls back to the safe default (LOCAL, no consent). */
class PreferencesClaudeSettingsStore(context: Context) : ClaudeSettingsStore {
    private val preferences = context.applicationContext.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)

    override fun load(): ClaudeBrainSettings = runCatching {
        val consent = preferences.getBoolean(KEY_CONSENT, false)
        ClaudeBrainSettings(
            // A stored CLAUDE selection without the stored consent is not honoured.
            brain = if (consent && preferences.getString(KEY_BRAIN, null) == BrainKind.CLAUDE.name) BrainKind.CLAUDE
                else BrainKind.LOCAL,
            model = ClaudeModel.fromId(preferences.getString(KEY_MODEL, null)) ?: ClaudeModel.DEFAULT,
            effortOverride = ClaudeEffort.parse(preferences.getString(KEY_EFFORT, null)),
            consentGiven = consent,
            consentAtMillis = preferences.getLong(KEY_CONSENT_AT, 0L),
            shareLocalMemory = consent && preferences.getBoolean(KEY_SHARE_MEMORY, false),
            monthlyLimitCents = preferences.getInt(KEY_LIMIT, ClaudeBrainSettings.DEFAULT_MONTHLY_LIMIT_CENTS)
                .coerceIn(ClaudeBrainSettings.MIN_MONTHLY_LIMIT_CENTS, ClaudeBrainSettings.MAX_MONTHLY_LIMIT_CENTS),
            cloudWebSearch = consent && preferences.getBoolean(KEY_CLOUD_WEB, false),
            cloudCodeExecution = consent && preferences.getBoolean(KEY_CLOUD_CODE, false)
        )
    }.getOrDefault(ClaudeBrainSettings())

    override fun save(settings: ClaudeBrainSettings): Boolean = preferences.edit()
        .putString(KEY_BRAIN, settings.brain.name)
        .putString(KEY_MODEL, settings.model.id)
        .apply { if (settings.effortOverride == null) remove(KEY_EFFORT) else putString(KEY_EFFORT, settings.effortOverride.name) }
        .putBoolean(KEY_CONSENT, settings.consentGiven)
        .putLong(KEY_CONSENT_AT, settings.consentAtMillis)
        .putBoolean(KEY_SHARE_MEMORY, settings.shareLocalMemory)
        .putInt(KEY_LIMIT, settings.monthlyLimitCents)
        .putBoolean(KEY_CLOUD_WEB, settings.cloudWebSearch)
        .putBoolean(KEY_CLOUD_CODE, settings.cloudCodeExecution)
        .commit()

    private companion object {
        const val PREFERENCES = "continuum.claude.brain"
        const val KEY_BRAIN = "brain"
        const val KEY_MODEL = "model"
        const val KEY_EFFORT = "effort"
        const val KEY_CONSENT = "consent"
        const val KEY_CONSENT_AT = "consent_at"
        const val KEY_SHARE_MEMORY = "share_local_memory"
        const val KEY_LIMIT = "monthly_limit_cents"
        const val KEY_CLOUD_WEB = "cloud_web_search"
        const val KEY_CLOUD_CODE = "cloud_code_execution"
    }
}
