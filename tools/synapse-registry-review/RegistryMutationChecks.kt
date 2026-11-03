package at.heinz.deviceai.chief

import java.io.*
import java.nio.ByteBuffer
import java.nio.file.Files
import java.security.MessageDigest

/** Additional source-review negatives. These are not undisclosed model holdouts. */
object RegistryMutationChecks {
    private var passed = 0
    private var failed = 0
    private inline fun reject(block: () -> Unit) {
        var didReject = false
        try { block() } catch (_: IllegalArgumentException) { didReject = true }
        catch (_: IllegalStateException) { didReject = true }
        catch (_: IOException) { didReject = true }
        check(didReject) { "accepted invalid persisted data" }
    }
    private fun test(name: String, block: (File) -> Unit) {
        val root = Files.createTempDirectory("synapse-mutation-").toFile()
        try { block(root); passed++; println("PASS $name") }
        catch (t: Throwable) { failed++; println("FAIL $name: ${t.javaClass.simpleName}: ${t.message}") }
        finally { root.deleteRecursively() }
    }
    private fun mutate(file: File, transform: (ByteArray) -> Unit) {
        val data = DataInputStream(file.inputStream())
        val magic = data.readUTF(); val version = data.readInt(); val size = data.readInt()
        val payload = ByteArray(size); data.readFully(payload); data.close(); transform(payload)
        DataOutputStream(file.outputStream()).use { out ->
            out.writeUTF(magic); out.writeInt(version); out.writeInt(payload.size); out.write(payload)
            out.write(MessageDigest.getInstance("SHA-256").digest(payload))
        }
    }
    private fun replaceAll(payload: ByteArray, before: String, after: String) {
        val a = before.toByteArray(); val b = after.toByteArray(); check(a.size == b.size)
        var count = 0
        for (i in 0..payload.size - a.size) if (a.indices.all { payload[i + it] == a[it] }) {
            b.copyInto(payload, i); count++
        }
        check(count > 0)
    }
    private fun invalid(root: File, transform: (ByteArray) -> Unit) {
        val s = ChiefBotProfileStore(root); s.seedDefaults(3)
        val file = File(root, "bot-profiles-v1.bin")
        mutate(file, transform); val bytes = file.readBytes()
        reject { ChiefBotProfileStore(root).page() }
        reject { ChiefBotProfileStore(root).seedDefaults(3) }
        check(file.readBytes().contentEquals(bytes)) { "invalid snapshot overwritten" }
    }
    private fun fieldOffset(payload: ByteArray, field: String): Int {
        val input = DataInputStream(payload.inputStream()); input.readInt(); input.readUTF()
        if (field == "version") return payload.size - input.available()
        input.readLong(); repeat(6) { input.readUTF() }
        val start = payload.size - input.available()
        return start + when (field) { "modelCalls" -> 0; "toolCalls" -> 4; "deadline" -> 8; "workspace" -> 16; else -> error("unknown") }
    }
    @JvmStatic fun main(args: Array<String>) {
        test("checksum-valid-duplicate-id-and-all-bindings") { root -> invalid(root) { replaceAll(it, "bot-kora", "bot-lint") } }
        test("checksum-valid-foreign-workspace-binding") { root -> invalid(root) { replaceAll(it, "workspace-bot-kora", "workspace-bot-lint") } }
        test("checksum-valid-traversal-namespace") { root -> invalid(root) { replaceAll(it, "bot-bot-kora", "../../escape") } }
        test("checksum-valid-invalid-profile-version") { root -> invalid(root) { ByteBuffer.wrap(it).putLong(fieldOffset(it, "version"), 0) } }
        test("checksum-valid-invalid-resource-bounds") { root ->
            for ((field, value) in listOf("modelCalls" to 9, "toolCalls" to 17)) {
                invalid(File(root, field)) { ByteBuffer.wrap(it).putInt(fieldOffset(it, field), value) }
            }
            for ((field, value) in listOf("deadline" to 0L, "workspace" to -1L)) {
                invalid(File(root, field)) { ByteBuffer.wrap(it).putLong(fieldOffset(it, field), value) }
            }
        }
        test("smaller-read-budget-does-not-reset-existing-store") { root ->
            val s = ChiefBotProfileStore(root); s.seedDefaults(20)
            val file = File(root, "bot-profiles-v1.bin"); val original = file.readBytes()
            reject { ChiefBotProfileStore(root, maxProfiles = 3).seedDefaults(3) }
            reject { ChiefBotProfileStore(root, maxBytes = 1024).page() }
            check(file.readBytes().contentEquals(original))
            check(ChiefBotProfileStore(root).page(null, 50).items.size == 20)
        }
        test("missing-or-corrupt-private-object-fails-closed") { root ->
            val s = ChiefBotProfileStore(root); val p = s.create("alpha", "A", "R", "S")
            val saved = s.saveWorkingContext("alpha", "alpha", "private-note", p.profileVersion)
            val objectFile = root.walkTopDown().single { it.name == saved.contextObjectId + ".obj" }
            val original = objectFile.readBytes(); objectFile.writeBytes(original.copyOf(5))
            reject { s.workingContext("alpha", "alpha") }
            check(objectFile.readBytes().contentEquals(original.copyOf(5)))
            objectFile.delete(); reject { s.workingContext("alpha", "alpha") }
        }
        test("full-context-store-still-allows-pointer-withdrawal") { root ->
            val s = ChiefBotProfileStore(root, maxContextBytes = 256, maxContextStoreBytes = 1024)
            var p = s.create("alpha", "A", "R", "S")
            var writes = 0
            while (writes < 10) {
                try { p = s.saveWorkingContext("alpha", "alpha", "${writes}:" + "x".repeat(200), p.profileVersion); writes++ }
                catch (_: IllegalArgumentException) { break }
            }
            check(writes in 1..9) { "test never saturated bounded object store" }
            val cleared = s.saveWorkingContext("alpha", "alpha", "", p.profileVersion)
            check(cleared.contextObjectId == null && s.workingContext("alpha", "alpha") == null)
            check(ChiefBotProfileStore(root, maxContextBytes = 256, maxContextStoreBytes = 1024).workingContext("alpha", "alpha") == null)
        }
        println("RESULT passed=$passed failed=$failed")
        check(failed == 0)
    }
}
