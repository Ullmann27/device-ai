package at.heinz.deviceai.localmodel

/**
 * The one runtime every caller holds. It forwards each call to the on-device runtime or to Claude, according to
 * the owner's "Gehirn" setting, read at the moment of the call. There is no fallback in either direction: when
 * Claude is selected, a turn goes to [ClaudeModelRuntime] and fails there with an honest problem code if Claude
 * cannot answer; it is never quietly answered by the local model or sent anywhere else.
 *
 * Loading and unloading model files is the on-device runtime's lifecycle. While Claude is selected a load is
 * refused (no multi-GB engine next to a cloud brain) and an unload releases a still-loaded on-device model.
 * STOP reaches both sides, so a generation that was running when the owner switched is stopped as well.
 */
internal class SelectableModelRuntime(
    private val local: LocalModelRuntime,
    private val claude: LocalModelRuntime,
    private val claudeSelected: () -> Boolean
) : LocalModelRuntime {
    private val active: LocalModelRuntime get() = if (claudeSelected()) claude else local

    override val state: LocalModelState get() = active.state
    override val loadedModel: ImportedLocalModel? get() = active.loadedModel
    override val inferenceProfiles: List<LocalInferenceProfile> get() = active.inferenceProfiles
    override val computeThreads: Int? get() = active.computeThreads
    override val maxPromptUtf8Bytes: Int? get() = active.maxPromptUtf8Bytes
    override val tokenAwarePromptUtf8Bytes: Int? get() = active.tokenAwarePromptUtf8Bytes
    override val toolFreePromptUtf8Bytes: Int? get() = active.toolFreePromptUtf8Bytes
    // Accelerator, context window and backend notice belong to whichever runtime answers; without these the
    // evaluation report would lose the on-device backend behind this selector.
    override val activeBackend: LocalModelBackend? get() = active.activeBackend
    override val contextTokens: Int? get() = active.contextTokens
    override val backendNotice: String? get() = active.backendNotice

    override fun load(id: String, version: String, callback: (Result<ImportedLocalModel>) -> Unit) =
        if (claudeSelected()) callback(Result.failure(IllegalStateException(ClaudeModelRuntime.BRAIN_ACTIVE)))
        else local.load(id, version, callback)

    override fun unload(callback: (Result<Unit>) -> Unit) {
        if (!claudeSelected()) return local.unload(callback)
        claude.cancel()
        // Nothing loaded on the device: nothing to release.
        if (local.state == LocalModelState.EMPTY) callback(Result.success(Unit)) else local.unload(callback)
    }

    override fun stream(prompt: String, callback: LocalInferenceCallback) = active.stream(prompt, callback)

    override fun streamTextOnly(prompt: String, callback: LocalInferenceCallback) =
        active.streamTextOnly(prompt, callback)

    // Keeping the native conversation is an on-device ability only: the Claude runtime answers through the
    // interface default (complete prompt, nothing kept), and a release always reaches the on-device runtime.
    override fun streamDialogue(request: LocalDialogueRequest, callback: LocalInferenceCallback) =
        active.streamDialogue(request, callback)

    override fun releaseDialogue() = local.releaseDialogue()

    override fun streamThinking(prompt: String, callback: LocalInferenceCallback) =
        active.streamThinking(prompt, callback)

    override fun respondToTools(responses: List<LocalToolResponse>, callback: LocalInferenceCallback) =
        active.respondToTools(responses, callback)

    override fun cancel() {
        runCatching { claude.cancel() }
        local.cancel()
    }
}

/**
 * Everything the app knows about the optional cloud brain, in one process-wide place ([LocalModelHost]):
 * the owner's settings, the encrypted key, the monthly counter and the runtime. The UI talks to this class only.
 */
class ClaudeBrain internal constructor(
    private val settingsStore: ClaudeSettingsStore,
    private val keyStore: SecretStore,
    val ledger: ClaudeUsageLedger,
    endpoint: ClaudeModelRuntime.Endpoint = ClaudeModelRuntime.Endpoint(),
    private val now: () -> Long = System::currentTimeMillis
) {
    @Volatile private var current: ClaudeBrainSettings = settingsStore.load()
    /** Decrypted once and kept only in memory; cleared when the owner deletes the key. */
    @Volatile private var key: OwnerSecret? = null
    @Volatile private var keyLoaded = false
    /** Set by [LocalModelHost]: stops a running generation of whichever runtime loses the selection. */
    @Volatile internal var onBrainChanged: (BrainKind) -> Unit = {}

    /**
     * How the owner wants to be talked to, as one constant block for the system frame (null = none). Set once by
     * the app from the same style source the on-device prompt uses, so the brain does not change the manner. The
     * provider must not hold an Activity: this object lives as long as the process.
     */
    @Volatile var ownerStyle: () -> String? = { null }

    /**
     * The constant blocks of the owner's chat turn, most stable first: what the app can do, which model answers
     * (for the direct question only), how to talk. None of them changes between two turns.
     */
    val runtime = ClaudeModelRuntime(settings = { current }, apiKey = ::loadedKey, ledger = ledger, endpoint = endpoint,
        ownerFrame = { listOf(ClaudeAbilityCatalogue.text(), ClaudeAbilityCatalogue.runningOn(current.model),
            runCatching { ownerStyle() }.getOrNull()) },
        // Last, and written from the plan the request really declares: web search and code execution over the cloud.
        cloudToolsFrame = ClaudeAbilityCatalogue::cloudTools,
        serverTools = { ClaudeServerToolPolicy.decide(current, draftTurn, webTurn::grant) },
        onServerToolOutcome = { outcome -> synchronized(outcomeLock) { turnOutcome = turnOutcome + outcome } })

    /**
     * The web side of the running owner turn: the app's internet mode and a one-turn approval. The cloud's web
     * search is only ever declared within what this allows ([ClaudeServerToolPolicy]).
     */
    val webTurn = ClaudeWebTurn()

    private val outcomeLock = Any()
    private var turnOutcome = ClaudeServerToolOutcome()

    @Volatile private var draftTurn = false

    /**
     * A model turn for the owner's sentence [request] starts. Nothing of an earlier turn carries over.
     * [documentDraft]: its answer becomes the text of a document, so it gets no server tool.
     */
    fun beginOwnerTurn(request: String, documentDraft: Boolean = false) {
        webTurn.begin(request)
        draftTurn = documentDraft
        synchronized(outcomeLock) { turnOutcome = ClaudeServerToolOutcome() }
    }

    /** What the server tools did in the turn that just ended (sources, counts), or null. Handed out once. */
    fun takeServerToolOutcome(): ClaudeServerToolOutcome? = synchronized(outcomeLock) {
        turnOutcome.takeUnless { it.none }.also { turnOutcome = ClaudeServerToolOutcome() }
    }

    /** The cloud's own web search would be used for a web request right now, if the internet consent allows it. */
    val cloudWebReady: Boolean get() = current.claudeSelected && current.consentGiven && current.cloudWebSearch && hasKey()

    /** Both switches need the stored consent to be turned on; turning off always works. */
    fun setCloudWebSearch(on: Boolean): Boolean =
        (!on || current.consentGiven) && store(current.copy(cloudWebSearch = on))

    fun setCloudCodeExecution(on: Boolean): Boolean =
        (!on || current.consentGiven) && store(current.copy(cloudCodeExecution = on))

    val settings: ClaudeBrainSettings get() = current

    /** True while Claude is the selected brain: the permanent "CONTINUUM · CLOUD" marker follows this. */
    val claudeSelected: Boolean get() = current.claudeSelected

    /** May DEVICE_LOCAL_ONLY memory be part of a prompt? Always true for the local brain. */
    val mayUseLocalOnlyMemory: Boolean get() = !current.claudeSelected || current.shareLocalMemory

    fun hasKey(): Boolean = loadedKey() != null

    /**
     * Selecting Claude needs the explicit consent ([confirmConsent] first); selecting LOCAL always works.
     * Returns false when the request was not allowed or could not be stored.
     */
    fun selectBrain(brain: BrainKind): Boolean {
        if (brain == BrainKind.CLAUDE && !current.consentGiven) return false
        if (brain == current.brain) return true
        if (!store(current.copy(brain = brain))) return false
        onBrainChanged(brain)
        return true
    }

    /** The owner confirmed the data-transfer notice. [shareLocalMemory] is the separate, optional opt-in. */
    fun confirmConsent(shareLocalMemory: Boolean): Boolean =
        store(current.copy(consentGiven = true, consentAtMillis = now(), shareLocalMemory = shareLocalMemory))

    /** Revoking the consent switches back to the local brain at once. */
    fun revokeConsent(): Boolean {
        val wasClaude = current.claudeSelected
        val stored = store(current.copy(consentGiven = false, consentAtMillis = 0L, shareLocalMemory = false,
            brain = BrainKind.LOCAL, cloudWebSearch = false, cloudCodeExecution = false))
        if (stored && wasClaude) onBrainChanged(BrainKind.LOCAL)
        return stored
    }

    fun setShareLocalMemory(share: Boolean): Boolean =
        current.consentGiven && store(current.copy(shareLocalMemory = share))

    fun setModel(model: ClaudeModel): Boolean {
        if (model == current.model) return true
        // A turn that started on the other model is bound to it and must not continue on this one.
        runtime.cancel()
        return store(current.copy(model = model))
    }

    fun setEffortOverride(effort: ClaudeEffort?): Boolean = store(current.copy(effortOverride = effort))

    fun setMonthlyLimitCents(cents: Int): Boolean =
        cents in ClaudeBrainSettings.MIN_MONTHLY_LIMIT_CENTS..ClaudeBrainSettings.MAX_MONTHLY_LIMIT_CENTS &&
            store(current.copy(monthlyLimitCents = cents))

    /** Stores the owner's key encrypted. False for an implausible key or a failed write; nothing is sent. */
    fun saveKey(candidate: String): Boolean {
        val trimmed = candidate.trim()
        if (!ClaudeApiKey.plausible(trimmed) || !keyStore.save(trimmed)) return false
        runtime.forgetCredentials()
        key = OwnerSecret(trimmed)
        keyLoaded = true
        return true
    }

    fun deleteKey(): Boolean {
        runtime.forgetCredentials()
        key = null
        keyLoaded = true
        return keyStore.clear()
    }

    /** "CONTINUUM · CLOUD" or "LOKAL": the marker text every status surface shows. */
    fun marker(): String = if (current.claudeSelected) MARKER_CLOUD else MARKER_LOCAL

    fun describeUsage(): String = ledger.describe(current.monthlyLimitCents)

    private fun loadedKey(): OwnerSecret? {
        if (!keyLoaded) synchronized(this) {
            if (!keyLoaded) {
                key = keyStore.load()
                keyLoaded = true
            }
        }
        return key
    }

    @Synchronized private fun store(next: ClaudeBrainSettings): Boolean {
        if (!settingsStore.save(next)) return false
        current = next
        return true
    }

    override fun toString(): String = "ClaudeBrain(brain=${current.brain}, model=${current.model.id}, key=redacted)"

    companion object {
        /**
         * The permanent marker while the cloud brain answers (owner order 19.11.2026: „… möchte ich nicht stehen
         * haben Claude, sondern Continuum im Chat“). Who is talking is always Continuum; „· CLOUD“ is the owner's
         * privacy signal that answers leave the device, and must never be dropped from it.
         */
        const val MARKER_CLOUD = "CONTINUUM · CLOUD"
        const val MARKER_LOCAL = "LOKAL"
    }
}
