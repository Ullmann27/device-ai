package at.heinz.deviceai.chief

import at.heinz.deviceai.cognitive.*
import at.heinz.deviceai.localmodel.*
import java.io.File
import java.nio.file.Files
import java.security.MessageDigest
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Independent public-API cases frozen before candidate inspection/execution. No model is loaded. */
object ConversationIndependentChecks {
    private var passed = 0
    private var failed = 0
    private val model = ImportedLocalModel("review-model", "v1", "a".repeat(64), 1024,
        LocalModelCapabilities(true, false, false, false, false, 32768, null, setOf(LocalModelBackend.CPU)))
    private class Fixture(val root: File) {
        val store = ChiefBotProfileStore(File(root, "bots"))
        val signer = object : IdentitySigner {
            override fun sign(payload: ByteArray) = MessageDigest.getInstance("SHA-256").digest("SYNTHETIC-REVIEW-KEY".toByteArray() + payload)
            override fun verify(payload: ByteArray, signature: ByteArray) = sign(payload).contentEquals(signature)
        }
        val runtime = ContinuumRuntimeStore(File(root, "owner-runtime"), signer,
            ownerIdentityReference = "synthetic:review-owner", nowMillis = { 1000L },
            instanceIdFactory = { ContinuumInstanceId("continuum-review-owner") })
        val genesis = runtime.openOrCreate()
        val state = runtime.checkpoint(genesis, memories = listOf(MemoryRecord(
            id = "owner-private", type = MemoryType.SEMANTIC,
            content = "project storage OWNER_ONLY_SENTINEL_742", provenance = ProvenanceType.USER_PROVIDED,
            confidence = 1.0, createdAt = 900, updatedAt = 900, privacyClass = PrivacyClass.DEVICE_LOCAL_ONLY)))
        init { store.seedDefaults(3) }
        fun controller() = ChiefBotConversation(store)
        fun note(id: String, value: String) {
            val p = store.get(id)!!; store.saveWorkingContext(id, id, value, p.profileVersion)
        }
        fun turn(c: ChiefBotConversation, id: String = "bot-kora", text: String = "project storage", session: String = "review-session") =
            c.prepare(id, text, state, model, session)
    }
    private fun test(name: String, block: (Fixture) -> Unit) {
        val root = Files.createTempDirectory("genesis-independent-").toFile()
        try { block(Fixture(root)); passed++; println("PASS $name") }
        catch (t: Throwable) { failed++; println("FAIL $name: ${t.javaClass.simpleName}: ${t.message}"); t.printStackTrace(System.out) }
        finally { root.deleteRecursively() }
    }
    private inline fun rejected(block: () -> Unit) {
        var rejected = false
        try { block() } catch (_: IllegalArgumentException) { rejected = true }
        catch (_: IllegalStateException) { rejected = true }
        catch (_: java.io.IOException) { rejected = true }
        check(rejected) { "operation unexpectedly admitted" }
    }
    @JvmStatic fun main(args: Array<String>) {
        test("selected-private-notes-and-history-exclude-other-bots-and-owner-facts") { f ->
            f.note("bot-kora", "KORA_PRIVATE_NOTE_391"); f.note("bot-lint", "LINT_PRIVATE_NOTE_862")
            val c = f.controller(); c.select("bot-kora")
            val a = f.turn(c)
            check("KORA_PRIVATE_NOTE_391" in a.prompt)
            check("LINT_PRIVATE_NOTE_862" !in a.prompt && "OWNER_ONLY_SENTINEL_742" !in a.prompt)
            check("REGISTERED TOOLS" !in a.prompt)
            check(c.complete(a, "KORA_REPLY_172", model, "review-session"))
            c.select("bot-lint")
            val b = f.turn(c, "bot-lint")
            check("LINT_PRIVATE_NOTE_862" in b.prompt)
            check("KORA_PRIVATE_NOTE_391" !in b.prompt && "KORA_REPLY_172" !in b.prompt && "OWNER_ONLY_SENTINEL_742" !in b.prompt)
            check(f.store.loadConversation("bot-kora", "bot-kora").turns.single().response == "KORA_REPLY_172")
            check(f.store.loadConversation("bot-lint", "bot-lint").turns.isEmpty())
            rejected { f.store.loadConversation("bot-lint", "bot-kora") }
        }
        test("owner-seed-genome-and-model-metadata-canaries-stay-out-of-bot-prompt") { f ->
            val genome = f.state.archive.genome.copy(
                operatorAuthorityPolicy = "OWNER_POLICY_PRIVATE_573",
                memoryPolicy = "OWNER_MEMORY_POLICY_819", toolPolicy = "OWNER_TOOL_POLICY_382")
            val identity = f.state.archive.identityState.evolve {
                it.copy(cognitiveGenomeHash = genome.hash(), chosenName = "OWNER_NAME_PRIVATE_446")
            }
            val privateState = f.runtime.checkpoint(f.state, identityState = identity, genome = genome)
            val c = f.controller(); c.select("bot-kora")
            val a = c.prepare("bot-kora", "public generic question", privateState, model, "review-session")
            for (canary in listOf("OWNER_POLICY_PRIVATE_573", "OWNER_MEMORY_POLICY_819", "OWNER_TOOL_POLICY_382",
                "OWNER_NAME_PRIVATE_446", privateState.seed.instanceId.value, privateState.seed.currentStateRoot.value,
                privateState.seed.ownerIdentityReference, model.id, model.sha256)) {
                check(canary !in a.prompt) { "private governance/model canary leaked: $canary" }
            }
            check("bot-kora" in a.prompt)
        }
        test("rename-keeps-stable-history-without-new-authority") { f ->
            val c = f.controller(); c.select("bot-kora"); val a = f.turn(c)
            check(c.complete(a, "PERSISTENT_KORA_REPLY", model, "review-session"))
            val old = f.store.get("bot-kora")!!
            f.store.rename(old.agentId, "Neue Kora", old.profileVersion)
            val next = f.turn(c)
            check(next.agentId == old.agentId && next.displayName == "Neue Kora" && "PERSISTENT_KORA_REPLY" in next.prompt)
            val renamed = f.store.get(old.agentId)!!
            check(renamed.memoryNamespace == old.memoryNamespace && renamed.workspaceId == old.workspaceId)
            check(renamed.permissionScope.isEmpty() && renamed.verifiedCapabilities.isEmpty())
        }
        test("switch-and-late-completion-cannot-corrupt-current-bot") { f ->
            val c = f.controller(); c.select("bot-kora"); val old = f.turn(c)
            c.select("bot-lint"); val current = f.turn(c, "bot-lint")
            check(!c.isCurrent(old, model, "review-session"))
            check(!c.complete(old, "LATE_KORA_REPLY", model, "review-session"))
            check(c.isCurrent(current, model, "review-session"))
            check(c.complete(current, "CURRENT_LINT_REPLY", model, "review-session"))
            check(f.store.loadConversation("bot-kora", "bot-kora").turns.isEmpty())
            check(f.store.loadConversation("bot-lint", "bot-lint").turns.single().response == "CURRENT_LINT_REPLY")
        }
        test("cancelled-old-frame-cannot-cancel-or-complete-new-turn") { f ->
            val c = f.controller(); c.select("bot-kora"); val old = f.turn(c)
            check(c.cancel(old)); val fresh = f.turn(c, text = "fresh input")
            check(!c.cancel(old)); check(!c.complete(old, "stale", model, "review-session"))
            check(c.isCurrent(fresh, model, "review-session"))
            check(c.complete(fresh, "fresh response", model, "review-session"))
            check(f.store.loadConversation("bot-kora", "bot-kora").turns.size == 1)
        }
        test("model-digest-version-and-session-mismatch-reject-output") { f ->
            val c = f.controller(); c.select("bot-kora")
            for ((different, session) in listOf(
                model.copy(sha256 = "b".repeat(64)) to "review-session",
                model.copy(version = "v2") to "review-session",
                model to "other-session")) {
                val a = f.turn(c)
                check(!c.isCurrent(a, different, session))
                check(!c.complete(a, "wrong binding", different, session))
                c.invalidate(); c.select("bot-kora")
            }
            val a = f.turn(c); check(!c.complete(a, "unloaded model", null, "review-session"))
            check(f.store.loadConversation("bot-kora", "bot-kora").turns.isEmpty())
        }
        test("controller-restart-does-not-reuse-old-ticket") { f ->
            val first = f.controller(); first.select("bot-kora"); val old = f.turn(first)
            val restarted = f.controller(); restarted.select("bot-kora"); val fresh = f.turn(restarted)
            check(!restarted.complete(old, "old-process response", model, "review-session"))
            check(restarted.isCurrent(fresh, model, "review-session"))
            check(restarted.complete(fresh, "new-process response", model, "review-session"))
        }
        test("single-pending-slot-survives-concurrent-prepares") { f ->
            val c = f.controller(); c.select("bot-kora")
            val start = CountDownLatch(1); val accepted = ConcurrentLinkedQueue<ChiefBotConversationTurn>()
            val failures = ConcurrentLinkedQueue<Throwable>()
            val threads = (0..7).map { i -> Thread {
                start.await()
                try { accepted.add(f.turn(c, text = "request $i")) }
                catch (t: Throwable) { failures.add(t) }
            }.also(Thread::start) }
            start.countDown(); threads.forEach { it.join(3000); check(!it.isAlive) }
            check(accepted.size == 1 && failures.size == 7) { "accepted=${accepted.size} rejected=${failures.size}" }
            check(c.complete(accepted.single(), "sole reply", model, "review-session"))
        }
        test("failed-prepare-and-oversized-output-do-not-poison-state") { f ->
            val c = f.controller(); c.select("bot-kora")
            rejected { f.turn(c, text = "x".repeat(70_000)) }
            val a = f.turn(c)
            val admitted = runCatching { c.complete(a, "x".repeat(70_000), model, "review-session") }.getOrDefault(false)
            check(!admitted)
            check(f.store.loadConversation("bot-kora", "bot-kora").turns.isEmpty())
            c.invalidate(); c.select("bot-kora"); val next = f.turn(c)
            check(c.complete(next, "normal", model, "review-session"))
        }
        test("notes-withdrawal-invalidates-inflight-and-derived-history") { f ->
            f.note("bot-kora", "WITHDRAWABLE_NOTE_667")
            val c = f.controller(); c.select("bot-kora"); val first = f.turn(c)
            check(c.complete(first, "derived WITHDRAWABLE_NOTE_667", model, "review-session"))
            val second = f.turn(c); f.note("bot-kora", "")
            check(!c.isCurrent(second, model, "review-session"))
            check(!c.complete(second, "late WITHDRAWABLE_NOTE_667", model, "review-session"))
            c.invalidate(); c.select("bot-kora"); val fresh = f.turn(c)
            check("WITHDRAWABLE_NOTE_667" !in fresh.prompt)
            check(f.store.loadConversation("bot-kora", "bot-kora").turns.isEmpty())
            check(f.store.workingContext("bot-kora", "bot-kora") == null)
        }
        test("profile-edit-during-generation-rejects-old-frame") { f ->
            val c = f.controller(); c.select("bot-kora"); val a = f.turn(c)
            val old = f.store.get("bot-kora")!!
            f.store.rename("bot-kora", "Renamed while generating", old.profileVersion)
            check(!c.isCurrent(a, model, "review-session"))
            check(!c.complete(a, "old profile output", model, "review-session"))
            check(f.store.loadConversation("bot-kora", "bot-kora").turns.isEmpty())
        }
        test("reset-preserves-notes-and-excludes-old-conversation") { f ->
            f.note("bot-kora", "KEEP_NOTE_231")
            val c = f.controller(); c.select("bot-kora"); val a = f.turn(c)
            check(c.complete(a, "CLEAR_HISTORY_910", model, "review-session"))
            c.reset("bot-kora"); val fresh = f.turn(c)
            check("KEEP_NOTE_231" in fresh.prompt && "CLEAR_HISTORY_910" !in fresh.prompt)
            check(f.store.loadConversation("bot-kora", "bot-kora").turns.isEmpty())
        }
        test("more-than-twenty-identities-do-not-imply-model-runs") { f ->
            val c = f.controller()
            repeat(24) { i ->
                val id = "additional-${i.toString().padStart(3, '0')}"
                f.store.create(id, "Profile $i", "No certified ability", "Calm")
                c.select(id); val a = f.turn(c, id)
                check(a.agentId == id); check(c.cancel(a))
                check(f.store.loadConversation(id, id).turns.isEmpty())
            }
            check(f.store.page(null, 100).items.size == 27)
        }
        println("RESULT passed=$passed failed=$failed; synthetic software routing only; local_model_inference=NOT_RUN")
        check(failed == 0)
    }
}
