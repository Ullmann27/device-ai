package at.heinz.deviceai.chief

import at.heinz.deviceai.cognitive.*
import at.heinz.deviceai.localmodel.*
import java.io.*
import java.nio.file.Files
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

/** Separate migration/restart/corruption acceptance, synthetic fixtures only. */
object ConversationStorageChecks {
    private var passed = 0
    private var failed = 0
    private val model = ImportedLocalModel("review-model", "v1", "a".repeat(64), 1024,
        LocalModelCapabilities(true, false, false, false, false, 32768, null, setOf(LocalModelBackend.CPU)))
    private val signer = object : IdentitySigner {
        override fun sign(payload: ByteArray) = MessageDigest.getInstance("SHA-256").digest("REVIEW-ONLY".toByteArray() + payload)
        override fun verify(payload: ByteArray, signature: ByteArray) = sign(payload).contentEquals(signature)
    }
    private fun runtime(root: File) = ContinuumRuntimeStore(File(root, "owner"), signer,
        ownerIdentityReference = "synthetic:review", instanceIdFactory = { ContinuumInstanceId("review-owner") }).openOrCreate()
    private fun test(name: String, block: (File) -> Unit) {
        val root = Files.createTempDirectory("genesis-storage-").toFile()
        try { block(root); passed++; println("PASS $name") }
        catch (t: Throwable) { failed++; println("FAIL $name: ${t.javaClass.simpleName}: ${t.message}"); t.printStackTrace(System.out) }
        finally { root.deleteRecursively() }
    }
    private fun child(cp: String, main: String, vararg args: String) {
        val p = ProcessBuilder(File(System.getProperty("java.home"), "bin/java").path,
            "-Xmx256m", "-cp", cp, main, *args).redirectErrorStream(true).start()
        if (!p.waitFor(15, TimeUnit.SECONDS)) { p.destroyForcibly(); error("bounded child timed out") }
        val output = p.inputStream.bufferedReader().readText()
        check(p.exitValue() == 0) { output }
    }
    private inline fun reject(block: () -> Unit) {
        var rejected = false
        try { block() } catch (_: IllegalArgumentException) { rejected = true }
        catch (_: IllegalStateException) { rejected = true }
        catch (_: IOException) { rejected = true }
        check(rejected)
    }
    private fun changePointer(file: File, before: String, after: String) {
        val input = DataInputStream(file.inputStream())
        val magic = input.readUTF(); val version = input.readInt(); val size = input.readInt()
        val payload = ByteArray(size); input.readFully(payload); input.close()
        val a = before.toByteArray(); val b = after.toByteArray(); check(a.size == b.size)
        var matches = 0
        for (i in 0..payload.size-a.size) if (a.indices.all { payload[i+it] == a[it] }) { b.copyInto(payload, i); matches++ }
        check(matches == 1)
        DataOutputStream(file.outputStream()).use {
            it.writeUTF(magic); it.writeInt(version); it.writeInt(payload.size); it.write(payload)
            it.write(MessageDigest.getInstance("SHA-256").digest(payload))
        }
    }
    @JvmStatic fun main(args: Array<String>) {
        if (args.firstOrNull() == "read-history") {
            val store = ChiefBotProfileStore(File(args[1]))
            check(store.loadConversation(args[2], args[2]).turns.single().response == args[3])
            check(store.get(args[2])!!.currentTaskId == null)
            println("RESTORED_COMMITTED_HISTORY")
            return
        }
        test("actual-v1-fixture-migrates-without-losing-identities-or-notes") { root ->
            val bots = File(root, "bots")
            val legacyCp = checkNotNull(System.getProperty("genesis.review.v1fixtureClasspath")) { "v1 fixture classpath missing" }
            child(legacyCp, LegacyProfileFixture::class.java.name, bots.path)
            val file = File(bots, "bot-profiles-v1.bin")
            DataInputStream(file.inputStream()).use { it.readUTF(); check(it.readInt() == 1) }
            val oldBytes = file.readBytes()
            val store = ChiefBotProfileStore(bots)
            val old = store.get("bot-kora")!!
            check(store.page(null, 50).items.map { it.agentId }.toSet() == setOf("bot-atlas", "bot-kora", "bot-lint"))
            check(store.workingContext("bot-kora", "bot-kora") == "LEGACY_PRIVATE_NOTE_174")
            check(store.loadConversation("bot-kora", "bot-kora").turns.isEmpty())
            check(file.readBytes().contentEquals(oldBytes)) { "read-only migration unexpectedly rewrote v1" }
            val c = ChiefBotConversation(store); c.select("bot-kora")
            val turn = c.prepare("bot-kora", "new conversation", runtime(root), model, "migration-session")
            check(c.complete(turn, "V2_COMMITTED_REPLY_936", model, "migration-session"))
            val now = store.get("bot-kora")!!
            check(now.agentId == old.agentId && now.contextObjectId == old.contextObjectId && now.memoryNamespace == old.memoryNamespace && now.workspaceId == old.workspaceId)
            DataInputStream(file.inputStream()).use { it.readUTF(); check(it.readInt() == 2) }
            child(System.getProperty("java.class.path"), ConversationStorageChecks::class.java.name,
                "read-history", bots.path, "bot-kora", "V2_COMMITTED_REPLY_936")
        }
        test("validly-hashed-foreign-conversation-object-cannot-be-rebound") { root ->
            val bots = File(root, "bots"); val store = ChiefBotProfileStore(bots); store.seedDefaults(3)
            val c = ChiefBotConversation(store); val state = runtime(root)
            for (id in listOf("bot-kora", "bot-lint")) {
                c.select(id); val turn = c.prepare(id, "own task", state, model, "binding-session")
                check(c.complete(turn, "private reply for $id", model, "binding-session"))
            }
            val a = store.get("bot-kora")!!.conversationObjectId!!
            val b = store.get("bot-lint")!!.conversationObjectId!!
            val first = bots.walkTopDown().single { it.name == "$a.obj" }
            val second = bots.walkTopDown().single { it.name == "$b.obj" }
            first.copyTo(File(second.parentFile, "$a.obj"))
            changePointer(File(bots, "bot-profiles-v1.bin"), b, a)
            reject { ChiefBotProfileStore(bots).loadConversation("bot-lint", "bot-lint") }
            check(ChiefBotProfileStore(bots).loadConversation("bot-kora", "bot-kora").turns.single().response == "private reply for bot-kora")
        }
        test("missing-conversation-object-does-not-silently-reset") { root ->
            val bots = File(root, "bots"); val store = ChiefBotProfileStore(bots); store.seedDefaults(3)
            val c = ChiefBotConversation(store); c.select("bot-kora")
            val turn = c.prepare("bot-kora", "task", runtime(root), model, "missing-session")
            check(c.complete(turn, "committed reply", model, "missing-session"))
            val profile = store.get("bot-kora")!!
            bots.walkTopDown().single { it.name == profile.conversationObjectId + ".obj" }.delete()
            reject { store.loadConversation("bot-kora", "bot-kora") }
            check(store.get("bot-kora")!!.conversationObjectId == profile.conversationObjectId)
        }
        println("RESULT passed=$passed failed=$failed; persistence software tests; actual model NOT_RUN")
        check(failed == 0)
    }
}
