package at.heinz.deviceai.codingagents

import android.content.Context
import at.heinz.deviceai.localmodel.AndroidKeystoreSecretCipher
import at.heinz.deviceai.localmodel.ClaudeApiKey
import at.heinz.deviceai.localmodel.ClaudeBrain
import at.heinz.deviceai.localmodel.ClaudeTokenUsage
import at.heinz.deviceai.localmodel.LocalModelHost
import at.heinz.deviceai.localmodel.OwnerSecret
import at.heinz.deviceai.localmodel.PreferencesSecretStorage
import at.heinz.deviceai.localmodel.SecretStore
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/** App-private preferences for the one JSON document of the coding agents (settings, resource ids, run list). */
class PreferencesCodingAgentStorage(context: Context) : CodingAgentStorage {
    private val preferences = context.applicationContext.getSharedPreferences("continuum.codingagents", Context.MODE_PRIVATE)
    override fun read(): String? = preferences.getString("state", null)
    override fun write(value: String): Boolean = preferences.edit().putString("state", value).commit()
}

/**
 * The app's existing Claude account, used as it is: the owner's key from the brain's encrypted store, the brain's
 * data-transfer consent, its monthly limit and its one monthly counter. Nothing of it is duplicated or changed here.
 */
class BrainCodingAgentAccount(private val brain: ClaudeBrain, private val keyStore: SecretStore,
                              private val nanoTime: () -> Long = System::nanoTime) : CodingAgentClaudeAccount {
    @Volatile private var cached: OwnerSecret? = null
    @Volatile private var cachedAt = 0L

    /** Decrypted at most every few seconds, so a key the owner replaced or deleted under „Gehirn“ is picked up. */
    override fun apiKey(): OwnerSecret? {
        if (!brain.hasKey()) { cached = null; return null }
        val now = nanoTime()
        cached?.takeIf { now - cachedAt < KEY_CACHE_NANOS }?.let { return it }
        return synchronized(this) {
            cached = runCatching { keyStore.load() }.getOrNull()
            cachedAt = now
            cached
        }
    }

    override fun consentGiven(): Boolean = brain.settings.consentGiven
    override fun monthlyLimitCents(): Int = brain.settings.monthlyLimitCents
    override fun monthSpentCents(): Long = brain.ledger.snapshot().costCents

    override fun record(model: String, tokens: CodingAgentTokens) {
        brain.ledger.record(model, model, ClaudeTokenUsage(tokens.input, tokens.output, tokens.cacheRead, tokens.cacheWrite))
    }

    private companion object { const val KEY_CACHE_NANOS = 3_000_000_000L }
}

/**
 * The one controller of the process. A run outlives the Activity that started it: the controller keeps following the
 * session while the app process lives, and a later process finds the run in the stored list.
 */
object AndroidCodingAgents {
    @Volatile private var controller: CodingAgentController? = null

    fun controller(context: Context): CodingAgentController = controller ?: synchronized(this) {
        controller ?: build(context.applicationContext).also { controller = it }
    }

    /** JVM and Robolectric tests install a controller with a scripted cloud; null restores the production one. */
    internal fun replaceForTest(replacement: CodingAgentController?) { controller = replacement }

    fun tokenStore(context: Context): SecretStore = SecretStore(
        name = "github_agent_token",
        cipher = AndroidKeystoreSecretCipher("continuum_github_agent_token_v1"),
        storage = PreferencesSecretStorage(context, "continuum.codingagents.secret"),
        accepts = CodingAgentGitHubToken::plausible)

    private fun build(app: Context): CodingAgentController {
        val number = AtomicInteger()
        val workers = Executors.newCachedThreadPool { task ->
            Thread(task, "coding-agent-" + number.incrementAndGet()).apply { isDaemon = true }
        }
        val clock: ScheduledExecutorService = Executors.newSingleThreadScheduledExecutor { task ->
            Thread(task, "coding-agent-timer").apply { isDaemon = true }
        }
        val clients = object {
            private var current: SdkCodingAgentCloud? = null
            @Synchronized fun of(key: OwnerSecret): CodingAgentCloud = current?.takeIf { it.uses(key) } ?: run {
                current?.close()
                SdkCodingAgentCloud(key).also { current = it }
            }
        }
        return CodingAgentController(
            store = CodingAgentStore(PreferencesCodingAgentStorage(app)),
            tokenStore = tokenStore(app),
            account = BrainCodingAgentAccount(LocalModelHost.brain(app), ClaudeApiKey.store(app)),
            cloudFor = clients::of,
            github = HttpCodingAgentGitHub(),
            background = workers,
            timer = CodingAgentTimer { millis, action ->
                val scheduled = clock.schedule({ runCatching { action() } }, millis, TimeUnit.MILLISECONDS)
                return@CodingAgentTimer { scheduled.cancel(false) }
            })
    }
}
