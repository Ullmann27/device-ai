package at.heinz.deviceai.localmodel

import at.heinz.deviceai.ToolProposal
import at.heinz.deviceai.ToolRegistry
import at.heinz.deviceai.cognitive.JarvisUnifiedEvidenceContract
import at.heinz.deviceai.cognitive.LocalGenerationCompletion
import at.heinz.deviceai.cognitive.LocalGenerationFailureCause
import at.heinz.deviceai.cognitive.OwnerLanguageStyle
import at.heinz.deviceai.cognitive.ScreenObservationPrompt
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Request shaping, local-only gates, the price table, the monthly limit, the secret store and the brain switch. */
class ClaudeBrainPolicyTest {
    private fun source(name: String): String = listOf("app/src/main/java/at/heinz/deviceai/$name",
        "src/main/java/at/heinz/deviceai/$name").map(::File).first { it.exists() }.readText()

    // ---- tools ------------------------------------------------------------------------------------------------

    @Test fun claudeGetsTheRegisteredToolsWithTheSameNamesAndArguments() {
        val definitions = ClaudeToolCatalog.definitions()
        val registered = ToolRegistry.all().filterNot { it.name == "read_app_screen" }
        assertEquals(registered.map { it.name }.sorted(), definitions.map { it.name })
        for (definition in definitions) {
            val spec = ToolRegistry.get(definition.name)!!
            assertEquals((spec.required + spec.optional).sorted(), definition.arguments.map { it.name })
            assertEquals(spec.required.sorted(), definition.required)
            assertTrue(definition.description.contains(spec.description))
        }
        // The screen reader returns device-local evidence and is not declared to a cloud model.
        assertTrue(definitions.none { it.name in ClaudeLocalOnlyGuard.LOCAL_ONLY_TOOLS })
    }

    @Test fun declaredArgumentTypesAreTheOnesTheLocalRuntimeDeclaresAndTheValidatorAccepts() {
        // Drift alarm: the on-device runtime's own mapping, read from its source.
        val liteRt = source("localmodel/LiteRtLmLocalModelRuntime.kt")
        assertTrue(liteRt.contains("""argument in setOf("lines", "limit", "seconds") -> "integer""""))
        assertTrue(liteRt.contains("""argument == "userOnly" -> "boolean""""))
        assertTrue(liteRt.contains("""argument == "roots" -> "array""""))
        assertEquals("integer", ClaudeToolCatalog.jsonType("lines"))
        assertEquals("boolean", ClaudeToolCatalog.jsonType("userOnly"))
        assertEquals("array", ClaudeToolCatalog.jsonType("roots"))
        // A flag the validator only accepts as text stays a string, with the rule in its description.
        assertEquals("string", ClaudeToolCatalog.jsonType("includeOwnCachePreview"))
        val flag = ClaudeToolCatalog.definitions().single { it.name == "diagnose_device" }
            .arguments.single { it.name == "includeOwnCachePreview" }
        assertTrue(flag.description.contains("\"true\""))

        // A value of every declared type passes the app's own proposal validation (no type rejection).
        for (definition in ClaudeToolCatalog.definitions()) {
            val arguments = definition.arguments.associate { argument ->
                argument.name to when (argument.jsonType) {
                    "integer" -> 5
                    "boolean" -> true
                    "array" -> listOf("Download")
                    else -> when (argument.name) {
                        "url" -> "https://example.org/seite"
                        "packageOrName" -> "com.example.app"
                        "includeOwnCachePreview" -> "true"
                        "path", "source", "destination" -> "Download/datei.apk"
                        else -> "wert"
                    }
                }
            }
            assertEquals(definition.name, null, ToolProposal(definition.name, arguments).validate())
        }
    }

    // ---- prompt frame -----------------------------------------------------------------------------------------

    private fun literals(kotlin: String): String = Regex(""""((?:[^"\\]|\\.)*)"""").findAll(kotlin)
        .joinToString("") { it.groupValues[1].replace("\\n", "\n").replace("\\\"", "\"") }

    @Test fun stablePrefixConstantsEqualThePromptTextTheActivityReallyBuilds() {
        val activity = source("AgentActivity.kt")
        val body = activity.substringAfter("fun buildPromptBody(compiledText: String?): String = buildString {")
            .substringBefore("val requestedPromptCap =")
        val head = body.substringAfter("append(if (textOnlyTurn)").substringBefore("botTask?.let {")
        assertEquals(ClaudePromptFrame.HEAD_TEXT_ONLY + ClaudePromptFrame.HEAD_TOOLS, literals(head))
        val instructions = body.substringAfter("append(at.heinz.deviceai.cognitive.OwnerLanguageStyle.INSTRUCTION +")
            .substringBefore("// A deliberately narrow research-only temporal projection.")
        assertEquals(ClaudePromptFrame.OWNER_TURN_INSTRUCTIONS, OwnerLanguageStyle.INSTRUCTION + literals(instructions))
    }

    @Test fun onlyAnExactStablePrefixIsSplitOffAsSystem() {
        val rest = "Aktueller Auftrag:\nWie spät ist es?\n"
        for (head in listOf(ClaudePromptFrame.HEAD_TEXT_ONLY, ClaudePromptFrame.HEAD_TOOLS)) {
            val stable = head + ClaudePromptFrame.OWNER_TURN_INSTRUCTIONS
            val frame = ClaudePromptFrame.frame(stable + rest, isolatedWorker = false)
            assertEquals(listOf(ClaudePromptFrame.RUNTIME_PREFACE, stable), frame.systemBlocks)
            assertEquals(rest, frame.user)
            // Nothing is lost or reordered.
            assertEquals(stable + rest, frame.systemBlocks[1] + frame.user)
        }
        // A bot turn inserts its profile after the head line: not split.
        val bot = ClaudePromptFrame.HEAD_TOOLS + "Dieser ausdrücklich erteilte Auftrag gehört zum ausgewählten Fachbot. " +
            ClaudePromptFrame.OWNER_TURN_INSTRUCTIONS + rest
        assertEquals(ClaudePromptFrame.Frame(listOf(ClaudePromptFrame.RUNTIME_PREFACE), bot),
            ClaudePromptFrame.frame(bot, isolatedWorker = false))
        // The stable block alone is no user message.
        val alone = ClaudePromptFrame.HEAD_TOOLS + ClaudePromptFrame.OWNER_TURN_INSTRUCTIONS
        assertEquals(alone, ClaudePromptFrame.frame(alone, isolatedWorker = false).user)
        val worker = ClaudePromptFrame.frame("Daten prüfen.", isolatedWorker = true)
        assertEquals(listOf(ClaudePromptFrame.RUNTIME_PREFACE, ClaudePromptFrame.ISOLATED_WORKER), worker.systemBlocks)
        assertEquals("Daten prüfen.", worker.user)
        // Neither system text grants anything or names a secret.
        assertFalse(ClaudePromptFrame.RUNTIME_PREFACE.contains("sk-ant"))
        assertTrue(ClaudePromptFrame.RUNTIME_PREFACE.contains("A tool call is a proposal"))
    }

    // ---- local-only data --------------------------------------------------------------------------------------

    @Test fun realScreenObservationFrameAndPayloadAreRecognisedAsDeviceLocal() {
        val now = 1_000_000L
        val texts = listOf("Kontostand", "1.234,56 EUR")
        val content = texts.joinToString(" ")
        val payload = JSONObject().put("schema", "CONTINUUM_SCREEN_DATA_V1").put("complete", true)
            .put("provenance", "EXTERNAL_APP_UNTRUSTED_EPHEMERAL").put("package", "com.example.bank")
            .put("sourceId", "app-screen:com.example.bank:7").put("revision", 1)
            .put("observedAtMillis", now).put("expiresAtMillis", now + 30_000)
            .put("privacy", JarvisUnifiedEvidenceContract.Privacy.DEVICE_LOCAL_ONLY.name)
            .put("texts", JSONArray(texts)).put("sha256", JarvisUnifiedEvidenceContract.hash(content)).toString()
        val frame = ScreenObservationPrompt.frame(payload, now + 1, 7L)
        assertNotNull("fixture must be a valid screen frame", frame)
        assertTrue(ClaudeLocalOnlyGuard.carriesLocalOnlyData(frame!!))
        assertTrue(ClaudeLocalOnlyGuard.carriesLocalOnlyData(payload))
        assertEquals(ClaudeBrainFailure.LOCAL_ONLY_SCREEN_DATA, ClaudeLocalOnlyGuard.promptRefusal("Frage\n$frame"))
        assertEquals(ClaudeBrainFailure.LOCAL_ONLY_SCREEN_DATA, ClaudeLocalOnlyGuard.toolResponseRefusal(
            listOf(LocalToolResponse("read_app_screen", "{}"))))
        assertEquals(ClaudeBrainFailure.LOCAL_ONLY_SCREEN_DATA, ClaudeLocalOnlyGuard.toolResponseRefusal(
            listOf(LocalToolResponse("get_app_info", payload.take(7_000)))))
        assertNull(ClaudeLocalOnlyGuard.promptRefusal("Wie voll ist der Akku?"))
        assertNull(ClaudeLocalOnlyGuard.toolResponseRefusal(listOf(LocalToolResponse("get_battery_info", "{\"level\":80}"))))
    }

    @Test fun notificationContentHasNoPathIntoAModelPrompt() {
        // The existing guard is structural: the announcement code never calls the runtime. Keep it that way.
        val activity = source("AgentActivity.kt")
        val section = activity.substringAfter("private fun onIncomingNotification(")
            .substringBefore("private fun afterNotificationAnnouncement()")
        assertTrue(section.length > 1_000)
        assertFalse(section.contains("localRuntime"))
        assertFalse(section.contains("runLocal("))
        assertFalse(section.contains("claudeBrain"))
    }

    // ---- failures ---------------------------------------------------------------------------------------------

    @Test fun everyProblemCodeHasAGermanOwnerMessageAndNoneFallsBackSilently() {
        val codes = ClaudeBrainFailure::class.java.declaredFields
            .filter { it.type == String::class.java && java.lang.reflect.Modifier.isStatic(it.modifiers) }
            .mapNotNull { field -> field.isAccessible = true; field.get(null) as? String }
            .filter { it.startsWith("CLAUDE_") }
        assertTrue(codes.size >= 20)
        for (code in codes) {
            assertTrue(code, ClaudeBrainFailure.isClaude(code))
            val message = ClaudeBrainFailure.ownerMessage(code)
            assertTrue(code, message.length > 40)
            assertFalse(code, message.contains(code))
            assertFalse(code, message.contains("sk-ant"))
            if (ClaudeBrainFailure.offersSwitchBack(code)) {
                assertTrue(code, message.contains("Lokal (Gemma, auf dem Gerät)"))
                assertTrue(code, message.contains("Ich wechsle nicht von selbst"))
            }
        }
        assertTrue(ClaudeBrainFailure.ownerMessage(ClaudeBrainFailure.NO_CREDIT).contains("Guthaben"))
        assertTrue(ClaudeBrainFailure.ownerMessage(ClaudeBrainFailure.NO_CREDIT).contains("aufgebraucht"))
        assertFalse(ClaudeBrainFailure.isClaude("LOCAL_MODEL_PAUSED_THERMAL"))
        assertFalse(ClaudeBrainFailure.isClaude(null))
        // A cut cloud answer is never described as a fault of the local runtime.
        assertEquals(LocalGenerationFailureCause.CLOUD_INTERRUPTED,
            LocalGenerationFailureCause.classify(LocalGenerationCompletion.FAILED, ClaudeBrainFailure.STREAM_INTERRUPTED))
        assertEquals(LocalGenerationFailureCause.RUNTIME_FAILED,
            LocalGenerationFailureCause.classify(LocalGenerationCompletion.FAILED, "native failure"))
    }

    // ---- prices and monthly limit -----------------------------------------------------------------------------

    @Test fun priceTableMatchesTheStatedListPrices() {
        fun cost(model: String, input: Long, output: Long) =
            ClaudePricing.costNanoUsd(model, model, ClaudeTokenUsage(input, output))
        val million = 1_000_000L
        val usd = 1_000_000_000L
        assertEquals(4 * usd, cost("claude-opus-5-5", million, 0))
        assertEquals(20 * usd, cost("claude-opus-5-5", 0, million))
        assertEquals(2 * usd, cost("claude-sonnet-5-5", million, 0))
        assertEquals(10 * usd, cost("claude-sonnet-5-5", 0, million))
        assertEquals(usd / 10, cost("claude-haiku-5-5", 100_000, 0) * 10)
        assertEquals(usd / 2, cost("claude-haiku-5-5", 0, million))
        assertEquals(8 * usd, cost("claude-fable-5-2", million, 0))
        assertEquals(40 * usd, cost("claude-fable-5-2", 0, million))
        // Haiku 5.5 uses its second rate card for a prompt above 100 000 tokens.
        assertEquals(usd / 2, cost("claude-haiku-5-5", million, 0))
        // A model without an entry is priced like the model the owner selected.
        assertEquals(cost("claude-opus-5-5", 1_000, 1_000),
            ClaudePricing.costNanoUsd("claude-unknown-9", "claude-opus-5-5", ClaudeTokenUsage(1_000, 1_000)))
        assertTrue(ClaudePricing.describe().contains("Stand 03.11.2026, kann sich ändern"))
        assertTrue(ClaudePricing.describe().contains("Claude Opus 5.5: 4,00 / 20,00 USD"))
    }

    @Test fun monthlyCounterAddsUpResetsEachMonthAndBlocksAtTheLimit() {
        var month = "2026-11"
        val storage = InMemoryClaudeLedgerStorage()
        val ledger = ClaudeUsageLedger(storage) { month }
        assertFalse(ledger.limitReached(2_500))
        assertTrue(ledger.describe(2_500).contains("Noch keine Anfrage in diesem Monat."))
        ledger.record("claude-opus-5-5", "claude-opus-5-5", ClaudeTokenUsage(10_000, 2_000, 5_000, 1_000))
        ledger.record("claude-opus-5-5", "claude-opus-5-5", ClaudeTokenUsage(1_000, 500), estimated = true)
        ledger.record("claude-opus-4-8", "claude-opus-5-5", ClaudeTokenUsage(100, 100), countsAsRequest = false)
        ledger.record("claude-next-1", "claude-opus-5-5", ClaudeTokenUsage(100, 0))
        val now = ledger.snapshot()
        assertEquals(3L, now.requests)
        assertEquals(1L, now.estimatedRequests)
        assertEquals(1L, now.unpricedRequests)
        assertEquals(ClaudeUsageLedger.ModelTotals(2, 11_000, 2_500, 5_000, 1_000,
            11_000 * 4_000L + 2_500 * 20_000L + 5_000 * 200L + 1_000 * 5_000L), now.models["claude-opus-5-5"])
        // The stored counter survives a new instance (process restart).
        assertEquals(now, ClaudeUsageLedger(storage) { month }.snapshot())
        val text = ledger.describe(2_500)
        assertTrue(text.contains("Monat 2026-11"))
        assertTrue(text.contains("25,00 USD Monatslimit"))
        assertTrue(text.contains("Schätzung dieser App auf diesem Gerät, keine Rechnung"))
        assertTrue(text.contains("vorsichtig geschätzt"))

        // Exactly at the limit counts as reached; one cent below does not. Rounded up to whole cents.
        val exact = ClaudeUsageLedger(InMemoryClaudeLedgerStorage()) { month }
        exact.record("claude-opus-5-5", "claude-opus-5-5", ClaudeTokenUsage(0, 50_000))   // 1.00 USD
        assertEquals(100L, exact.snapshot().costCents)
        assertTrue(exact.limitReached(100))
        assertFalse(exact.limitReached(101))
        exact.record("claude-haiku-5-5", "claude-haiku-5-5", ClaudeTokenUsage(1, 0))      // a fraction of a cent
        assertEquals(101L, exact.snapshot().costCents)

        month = "2026-12"
        assertEquals(0L, ledger.snapshot().requests)
        assertFalse(ledger.limitReached(100))
    }

    @Test fun settingsDefaultsAreLocalAndEffortFollowsTheKindOfTurn() {
        val defaults = ClaudeBrainSettings()
        assertEquals(BrainKind.LOCAL, defaults.brain)
        assertFalse(defaults.claudeSelected)
        assertFalse(defaults.consentGiven)
        assertFalse(defaults.shareLocalMemory)
        assertEquals(ClaudeModel.OPUS_5_5, defaults.model)
        assertEquals(2_500, defaults.monthlyLimitCents)
        assertEquals(ClaudeEffort.LOW, defaults.effortFor(work = false))
        assertEquals(ClaudeEffort.MEDIUM, defaults.effortFor(work = true))
        assertEquals(ClaudeEffort.HIGH, defaults.copy(effortOverride = ClaudeEffort.HIGH).effortFor(work = false))
        assertEquals(listOf("claude-opus-5-5", "claude-sonnet-5-5", "claude-haiku-5-5", "claude-fable-5-2"),
            ClaudeModel.entries.map { it.id })
        assertEquals(listOf(true, true, false, true), ClaudeModel.entries.map { it.serverSideFallback })
        assertTrue(ClaudeConsentNotice.TEXT.contains("api.anthropic.com"))
        for (stated in listOf("deine Nachricht", "Gesprächsverlauf", "Gedächtnis", "Dokumenten", "Werkzeugen", "Guthaben",
            "Bildschirminhalte anderer Apps", "widerrufen", "CLAUDE · CLOUD", "wechselt nie von selbst"))
            assertTrue(stated, ClaudeConsentNotice.TEXT.contains(stated))
    }

    // ---- secret store -----------------------------------------------------------------------------------------

    /** Real AES-GCM with an in-memory key: the store's logic on the JVM. The Android Keystore itself is not here. */
    private class SoftwareCipher : SecretCipher {
        private var key: SecretKey? = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
        var destroyed = 0
        override fun encrypt(plain: ByteArray): SecretCipher.Sealed {
            val iv = ByteArray(12).also(SecureRandom()::nextBytes)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, checkNotNull(key), GCMParameterSpec(128, iv))
            return SecretCipher.Sealed(iv, cipher.doFinal(plain))
        }
        override fun decrypt(sealed: SecretCipher.Sealed): ByteArray {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, checkNotNull(key), GCMParameterSpec(128, sealed.iv))
            return cipher.doFinal(sealed.data)
        }
        override fun destroyKey() { destroyed++; key = null }
    }

    private class MapStorage : SecretStorage {
        val values = linkedMapOf<String, String>()
        var failWrites = false
        override fun read(name: String): String? = values[name]
        override fun write(values: Map<String, String?>): Boolean {
            if (failWrites) return false
            values.forEach { (key, value) -> if (value == null) this.values.remove(key) else this.values[key] = value }
            return true
        }
    }

    private val testKey = FakeAnthropicEndpoint.TEST_KEY

    @Test fun secretIsStoredEncryptedReadBackAndNeverPartOfAnyDescription() {
        val storage = MapStorage()
        val cipher = SoftwareCipher()
        val store = SecretStore("claude_api_key", cipher, storage, ClaudeApiKey::plausible)
        assertNull(store.load())
        assertFalse(store.isPresent())
        assertTrue(store.save(testKey))
        assertEquals(testKey, store.load()!!.reveal())
        assertTrue(store.isPresent())
        // At rest: two encoded values, neither contains the key or a recognisable part of it.
        assertEquals(setOf("claude_api_key_iv", "claude_api_key_data"), storage.values.keys)
        for (stored in storage.values.values) {
            assertFalse(stored.contains(testKey))
            assertFalse(stored.contains("sk-ant"))
            assertFalse(String(java.util.Base64.getDecoder().decode(stored), Charsets.ISO_8859_1).contains("sk-ant"))
        }
        // A new encryption of the same key differs (fresh nonce).
        val first = storage.values.toMap()
        assertTrue(store.save(testKey))
        assertFalse(first == storage.values)
        for (description in listOf(store.toString(), store.load().toString(), OwnerSecret(testKey).toString(),
            ClaudeApiKey.status(true), ClaudeApiKey.status(false)))
            assertFalse(description, description.contains("sk-ant") || description.contains(testKey))
        assertEquals("OwnerSecret(redacted)", store.load().toString())
    }

    @Test fun damagedOrForeignCiphertextReadsAsNoSecretAndClearRemovesEverything() {
        val storage = MapStorage()
        val cipher = SoftwareCipher()
        val store = SecretStore("claude_api_key", cipher, storage, ClaudeApiKey::plausible)
        assertTrue(store.save(testKey))
        val data = storage.values.getValue("claude_api_key_data")
        storage.values["claude_api_key_data"] = (if (data[0] == 'A') "B" else "A") + data.substring(1)
        assertNull("tampered ciphertext must not decrypt", store.load())
        storage.values["claude_api_key_data"] = "not base64 !!"
        assertNull(store.load())
        // Ciphertext from another device's key (restored preferences without the keystore key).
        storage.values["claude_api_key_data"] = data
        assertNull(SecretStore("claude_api_key", SoftwareCipher(), storage, ClaudeApiKey::plausible).load())
        assertEquals(testKey, store.load()!!.reveal())

        assertTrue(store.clear())
        assertTrue(storage.values.isEmpty())
        assertEquals(1, cipher.destroyed)
        assertNull(store.load())
    }

    @Test fun implausibleKeysAndFailedWritesAreNotStored() {
        val storage = MapStorage()
        val store = SecretStore("claude_api_key", SoftwareCipher(), storage, ClaudeApiKey::plausible)
        for (rejected in listOf("", "   ", "sk-or-v1-0000000000000000", "sk-ant-", "sk-ant-mit leerzeichen", "Bearer sk-ant-test-0000")) {
            assertFalse(rejected, ClaudeApiKey.plausible(rejected))
            assertFalse(rejected, store.save(rejected))
        }
        assertTrue(storage.values.isEmpty())
        assertTrue(ClaudeApiKey.plausible(testKey))
        storage.failWrites = true
        assertFalse(store.save(testKey))
        assertNull(store.load())
    }

    // ---- brain switch -----------------------------------------------------------------------------------------

    private class RecordingRuntime(private val name: String, model: ImportedLocalModel?) : LocalModelRuntime {
        override var state = if (model == null) LocalModelState.EMPTY else LocalModelState.READY
        override var loadedModel: ImportedLocalModel? = model
        val calls = mutableListOf<String>()
        override fun load(id: String, version: String, callback: (Result<ImportedLocalModel>) -> Unit) { calls += "load:$id" }
        override fun unload(callback: (Result<Unit>) -> Unit) {
            calls += "unload"
            state = LocalModelState.EMPTY
            loadedModel = null
            callback(Result.success(Unit))
        }
        override fun stream(prompt: String, callback: LocalInferenceCallback) { calls += "stream:$prompt" }
        override fun streamTextOnly(prompt: String, callback: LocalInferenceCallback) { calls += "text:$prompt" }
        override fun streamThinking(prompt: String, callback: LocalInferenceCallback) { calls += "thinking:$prompt" }
        override fun respondToTools(responses: List<LocalToolResponse>, callback: LocalInferenceCallback) { calls += "tools" }
        override fun cancel() { calls += "cancel" }
        override fun toString() = name
    }

    private val gemma = ImportedLocalModel("gemma-4-e4b", "1", "a".repeat(64), 3_650_000_000L,
        LocalModelCapabilities(true, false, false, false, true, 32_768, null, setOf(LocalModelBackend.CPU)))

    private fun brain(settings: ClaudeSettingsStore = InMemoryClaudeSettingsStore(), storage: MapStorage = MapStorage()) =
        ClaudeBrain(settings, SecretStore("claude_api_key", SoftwareCipher(), storage, ClaudeApiKey::plausible),
            ClaudeUsageLedger(InMemoryClaudeLedgerStorage()), now = { 1_700_000_000_000L })

    @Test fun everyCallGoesToTheSelectedBrainAndSwitchingBackNeedsNoRestart() {
        val local = RecordingRuntime("local", gemma)
        val claude = RecordingRuntime("claude", ClaudeModelRuntime.descriptor(ClaudeModel.OPUS_5_5))
        var claudeSelected = false
        val runtime = SelectableModelRuntime(local, claude) { claudeSelected }

        fun drive() {
            runtime.stream("a") {}
            runtime.streamTextOnly("b") {}
            runtime.streamThinking("c") {}
            runtime.respondToTools(listOf(LocalToolResponse("get_battery_info", "{}"))) {}
        }
        drive()
        assertEquals(listOf("stream:a", "text:b", "thinking:c", "tools"), local.calls)
        assertTrue(claude.calls.isEmpty())
        assertEquals(gemma, runtime.loadedModel)

        claudeSelected = true
        local.calls.clear()
        drive()
        assertEquals(listOf("stream:a", "text:b", "thinking:c", "tools"), claude.calls)
        assertTrue("nothing reaches the local model while Claude is selected", local.calls.isEmpty())
        assertTrue(ClaudeModelRuntime.isCloud(runtime.loadedModel))
        assertEquals(LocalModelState.READY, runtime.state)
        // A local model file is not loaded next to the cloud brain.
        var load: Result<ImportedLocalModel>? = null
        runtime.load("gemma-4-e4b", "1") { load = it }
        assertEquals(ClaudeModelRuntime.BRAIN_ACTIVE, load!!.exceptionOrNull()!!.message)
        assertTrue(local.calls.isEmpty())
        // STOP reaches both sides.
        runtime.cancel()
        assertEquals("cancel", claude.calls.last())
        assertEquals(listOf("cancel"), local.calls)

        claudeSelected = false
        local.calls.clear()
        claude.calls.clear()
        runtime.stream("zurück") {}
        assertEquals(listOf("stream:zurück"), local.calls)
        assertTrue(claude.calls.isEmpty())
        assertEquals(gemma, runtime.loadedModel)
    }

    @Test fun unloadWhileClaudeIsSelectedReleasesAStillLoadedLocalModelOnly() {
        val local = RecordingRuntime("local", gemma)
        val claude = RecordingRuntime("claude", ClaudeModelRuntime.descriptor(ClaudeModel.OPUS_5_5))
        val runtime = SelectableModelRuntime(local, claude) { true }
        var result: Result<Unit>? = null
        runtime.unload { result = it }
        assertTrue(result!!.isSuccess)
        assertEquals(listOf("unload"), local.calls)
        assertEquals(listOf("cancel"), claude.calls)
        // Nothing loaded any more: a second unload touches nothing native.
        runtime.unload { result = it }
        assertEquals(listOf("unload"), local.calls)
        assertTrue(ClaudeModelRuntime.isCloud(runtime.loadedModel))
    }

    @Test fun claudeCanOnlyBeSelectedAfterExplicitConsentAndRevokingSwitchesBack() {
        val stored = InMemoryClaudeSettingsStore()
        val brain = brain(stored)
        val changes = mutableListOf<BrainKind>()
        brain.onBrainChanged = { changes += it }
        assertEquals(ClaudeBrain.MARKER_LOCAL, brain.marker())
        assertTrue(brain.mayUseLocalOnlyMemory)

        assertFalse("no consent, no cloud brain", brain.selectBrain(BrainKind.CLAUDE))
        assertFalse(brain.claudeSelected)
        assertFalse(brain.setShareLocalMemory(true))

        assertTrue(brain.confirmConsent(shareLocalMemory = false))
        assertFalse("consent alone selects nothing", brain.claudeSelected)
        assertEquals(1_700_000_000_000L, brain.settings.consentAtMillis)
        assertTrue(brain.selectBrain(BrainKind.CLAUDE))
        assertTrue(brain.claudeSelected)
        assertEquals(ClaudeBrain.MARKER_CLAUDE, brain.marker())
        assertEquals("CLAUDE · CLOUD", brain.marker())
        assertFalse("device-local memory stays local without the separate opt-in", brain.mayUseLocalOnlyMemory)
        assertTrue(brain.setShareLocalMemory(true))
        assertTrue(brain.mayUseLocalOnlyMemory)
        assertEquals(listOf(BrainKind.CLAUDE), changes)
        assertEquals(BrainKind.CLAUDE, stored.load().brain)

        assertTrue(brain.selectBrain(BrainKind.LOCAL))
        assertEquals(listOf(BrainKind.CLAUDE, BrainKind.LOCAL), changes)
        assertTrue(brain.selectBrain(BrainKind.CLAUDE))
        assertTrue(brain.revokeConsent())
        assertFalse(brain.claudeSelected)
        assertFalse(brain.settings.consentGiven)
        assertFalse(brain.settings.shareLocalMemory)
        assertEquals(BrainKind.LOCAL, changes.last())
        assertFalse(brain.selectBrain(BrainKind.CLAUDE))

        assertTrue(brain.setModel(ClaudeModel.HAIKU_5_5))
        assertEquals("claude-haiku-5-5", brain.runtime.loadedModel.id)
        assertTrue(brain.setEffortOverride(ClaudeEffort.HIGH))
        assertTrue(brain.setMonthlyLimitCents(500))
        assertFalse(brain.setMonthlyLimitCents(0))
        assertFalse(brain.setMonthlyLimitCents(10_000_000))
        assertEquals(500, brain.settings.monthlyLimitCents)
        assertTrue(brain.describeUsage().contains("5,00 USD Monatslimit"))
    }

    @Test fun keyIsStoredOnlyWhenPlausibleCanBeDeletedAndNeverShowsInDescriptions() {
        val storage = MapStorage()
        val brain = brain(storage = storage)
        assertFalse(brain.hasKey())
        assertFalse(brain.saveKey("falscher-schluessel"))
        assertFalse(brain.hasKey())
        assertTrue(brain.saveKey("  $testKey \n"))
        assertTrue(brain.hasKey())
        assertFalse(brain.toString().contains("sk-ant"))
        assertFalse(brain.settings.toString().contains("sk-ant"))
        assertFalse(brain.runtime.toString().contains("sk-ant"))
        // A second instance over the same storage cannot read it without the same cipher key (other device).
        assertFalse(brain(storage = storage).hasKey())
        assertTrue(brain.deleteKey())
        assertFalse(brain.hasKey())
        assertTrue(storage.values.isEmpty())
    }

    @Test fun aCloudSelectionWithoutConsentCannotEvenBeRepresented() {
        for (invalid in listOf<() -> ClaudeBrainSettings>(
            { ClaudeBrainSettings(brain = BrainKind.CLAUDE) },
            { ClaudeBrainSettings(shareLocalMemory = true) },
            { ClaudeBrainSettings(brain = BrainKind.CLAUDE, consentGiven = true).copy(consentGiven = false) })) {
            try {
                invalid()
                org.junit.Assert.fail("settings without consent must be rejected")
            } catch (_: IllegalArgumentException) { }
        }
        assertTrue(ClaudeBrainSettings(brain = BrainKind.CLAUDE, consentGiven = true).claudeSelected)
    }
}
