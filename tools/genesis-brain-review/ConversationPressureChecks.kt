package at.heinz.deviceai.chief

import at.heinz.deviceai.cognitive.*
import at.heinz.deviceai.localmodel.*
import java.io.File
import java.io.DataInputStream
import java.io.DataOutputStream
import java.nio.ByteBuffer
import java.nio.file.Files
import java.security.MessageDigest

/** Source-review boundary probes, no neural or hardware-performance claims. */
object ConversationPressureChecks {
    private var passed = 0
    private var failed = 0
    private val model = ImportedLocalModel("synthetic", "v1", "a".repeat(64), 1,
        LocalModelCapabilities(true, false, false, false, false, 32768, null, setOf(LocalModelBackend.CPU)))
    private fun test(name: String, block: (File, ChiefBotProfileStore, ChiefBotConversation, ContinuumRuntimeState) -> Unit) {
        val root = Files.createTempDirectory("genesis-pressure-").toFile()
        try {
            val store = ChiefBotProfileStore(File(root, "bots")); store.seedDefaults(3)
            val c = ChiefBotConversation(store); c.select("bot-kora")
            val signer = object : IdentitySigner {
                override fun sign(payload: ByteArray) = MessageDigest.getInstance("SHA-256").digest(payload)
                override fun verify(payload: ByteArray, signature: ByteArray) = sign(payload).contentEquals(signature)
            }
            val state = ContinuumRuntimeStore(File(root, "owner"), signer,
                ownerIdentityReference = "synthetic:review", instanceIdFactory = { ContinuumInstanceId("pressure-fixture") }).openOrCreate()
            block(root, store, c, state); passed++; println("PASS $name")
        } catch (t: Throwable) { failed++; println("FAIL $name: ${t.javaClass.simpleName}: ${t.message}"); t.printStackTrace(System.out) }
        finally { root.deleteRecursively() }
    }
    private fun resourceMutation(root: File, maxCalls: Int? = null, deadline: Long? = null) {
        val file = File(root, "bots/bot-profiles-v1.bin")
        val input = DataInputStream(file.inputStream())
        val magic = input.readUTF(); val version = input.readInt(); val size = input.readInt()
        check(version == 2)
        val payload = ByteArray(size); input.readFully(payload); input.close()
        val data = DataInputStream(payload.inputStream()); val count = data.readInt()
        var modified = false
        repeat(count) {
            val id = data.readUTF(); data.readLong(); repeat(6) { data.readUTF() }
            val offset = payload.size - data.available()
            if (id == "bot-kora") {
                maxCalls?.let { ByteBuffer.wrap(payload).putInt(offset, it) }
                deadline?.let { ByteBuffer.wrap(payload).putLong(offset + 8, it) }
                modified = true
            }
            data.readInt(); data.readInt(); data.readLong(); data.readLong(); data.readUTF()
            if (data.readBoolean()) data.readUTF()
            if (data.readBoolean()) data.readUTF()
            data.readLong()
        }
        check(modified)
        DataOutputStream(file.outputStream()).use {
            it.writeUTF(magic); it.writeInt(version); it.writeInt(payload.size); it.write(payload)
            it.write(MessageDigest.getInstance("SHA-256").digest(payload))
        }
    }
    @JvmStatic fun main(args: Array<String>) {
        test("zero-profile-model-budget-blocks-preparation-without-poisoning-slot") { root, store, c, state ->
            resourceMutation(root, maxCalls = 0)
            var blocked = false
            try { c.prepare("bot-kora", "must not dispatch", state, model, "budget-session") }
            catch (_: IllegalStateException) { blocked = true }
            catch (_: IllegalArgumentException) { blocked = true }
            check(blocked) { "zero model-call profile admitted a turn" }
            check(store.loadConversation("bot-kora", "bot-kora").turns.isEmpty())
            resourceMutation(root, maxCalls = 1)
            val permitted = c.prepare("bot-kora", "fresh granted budget", state, model, "budget-session")
            check(c.cancel(permitted))
        }
        test("expired-profile-deadline-rejects-current-and-completion") { root, store, c, state ->
            resourceMutation(root, deadline = 1)
            val frame = runCatching { c.prepare("bot-kora", "deadline probe", state, model, "time-session") }.getOrNull()
            if (frame != null) {
                Thread.sleep(20)
                check(!c.isCurrent(frame, model, "time-session")) { "expired frame still current" }
                check(!c.complete(frame, "late completion", model, "time-session")) { "expired output persisted" }
            }
            check(store.loadConversation("bot-kora", "bot-kora").turns.isEmpty())
        }
        test("accepted-output-becomes-stale-after-note-withdrawal") { _, store, c, state ->
            val p = store.get("bot-kora")!!
            store.saveWorkingContext("bot-kora", "bot-kora", "PRIVATE_DEPENDENCY_452", p.profileVersion)
            val t = c.prepare("bot-kora", "my task", state, model, "s1")
            check(c.complete(t, "derived PRIVATE_DEPENDENCY_452", model, "s1"))
            check(c.isCurrent(t, model, "s1"))
            val current = store.get("bot-kora")!!
            store.saveWorkingContext("bot-kora", "bot-kora", "", current.profileVersion)
            check(!c.isCurrent(t, model, "s1")) { "committed display still valid after withdrawal" }
            check(store.loadConversation("bot-kora", "bot-kora").turns.isEmpty())
        }
        test("valid-unicode-history-stays-within-actual-serialization-budget") { _, store, c, state ->
            repeat(12) { i ->
                val t = c.prepare("bot-kora", "fresh bounded request $i", state, model, "emoji-session")
                val response = "\uD83D\uDE42".repeat(1_400)
                check(response.toByteArray(Charsets.UTF_8).size == 5_600)
                check(c.complete(t, response, model, "emoji-session")) { "valid bounded Unicode response rejected at $i" }
            }
            check(store.loadConversation("bot-kora", "bot-kora").turns.isNotEmpty())
        }
        test("real-object-budget-saturation-preserves-last-commit-and-withdrawal") { root, store, c, state ->
            var committed = 0
            var full = false
            while (committed < 160) {
                val before = store.get("bot-kora")!!
                val t = c.prepare("bot-kora", "capacity probe $committed", state, model, "capacity-session")
                try {
                    check(c.complete(t, "answer-$committed:" + "x".repeat(5_900), model, "capacity-session"))
                    committed++
                } catch (e: IllegalArgumentException) {
                    check(e.message == "BOT_CONVERSATION_STORE_FULL" || e.message == "Continuum object store capacity exceeded.") { "unexpected rejection: ${e.message}" }
                    check(store.get("bot-kora") == before) { "failed persistence changed committed pointer" }
                    full = true
                    break
                }
            }
            check(full && committed > 10) { "store did not reach expected bounded saturation" }
            val bytes = File(root, "bots/conversations").walkTopDown().filter(File::isFile).sumOf(File::length)
            check(bytes <= 4L * 1024 * 1024)
            c.reset("bot-kora")
            check(store.loadConversation("bot-kora", "bot-kora").turns.isEmpty())
            check(ChiefBotProfileStore(File(root, "bots")).loadConversation("bot-kora", "bot-kora").turns.isEmpty())
            println("OBSERVATION committedBeforeFull=$committed retainedConversationBytes=$bytes resetClearedPointer=true oldObjectsRetained=true")
        }
        println("RESULT passed=$passed failed=$failed")
        check(failed == 0)
    }
}
