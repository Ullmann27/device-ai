package at.heinz.deviceai.chief

import java.io.DataInputStream
import java.io.File
import java.nio.file.Files
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ConcurrentLinkedQueue

/** Independent checks frozen from public contract, before candidate source inspection. */
object RegistryIndependentChecks {
    private var passed = 0
    private var failed = 0
    private fun must(condition: Boolean, message: String) { check(condition) { message } }
    private inline fun rejected(block: () -> Unit) {
        var rejected = false
        try { block() } catch (_: IllegalArgumentException) { rejected = true }
        catch (_: IllegalStateException) { rejected = true }
        catch (_: java.io.IOException) { rejected = true }
        must(rejected, "operation was accepted")
    }
    private fun test(name: String, block: (File) -> Unit) {
        val root = Files.createTempDirectory("synapse-independent-").toFile()
        try { block(root); passed++; println("PASS $name") }
        catch (t: Throwable) { failed++; println("FAIL $name: ${t.javaClass.simpleName}: ${t.message}"); t.printStackTrace(System.out) }
        finally { root.deleteRecursively() }
    }
    private fun store(root: File) = ChiefBotProfileStore(root)
    private fun create(s: ChiefBotProfileStore, id: String) = s.create(id, "Name $id", "Research role", "Careful style")
    private fun all(s: ChiefBotProfileStore, size: Int = 17): List<ChiefBotProfile> {
        val result = mutableListOf<ChiefBotProfile>()
        var cursor: String? = null
        val seenCursors = mutableSetOf<String>()
        do {
            val page = s.page(cursor, size)
            must(page.items.size <= size, "page exceeds requested limit")
            result += page.items
            cursor = page.nextAfterId
            if (cursor != null) must(seenCursors.add(cursor), "pagination cursor cycle")
        } while (cursor != null)
        must(result.map { it.agentId }.distinct().size == result.size, "duplicate identities across pages")
        return result
    }
    private fun child(vararg args: String): Process = ProcessBuilder(
        File(System.getProperty("java.home"), "bin/java").path,
        "-Xmx256m", "-cp", System.getProperty("java.class.path"),
        RegistryIndependentChecks::class.java.name, *args
    ).redirectErrorStream(true).start()

    @JvmStatic fun main(args: Array<String>) {
        if (args.firstOrNull() == "writer") {
            val s = store(File(args[1]))
            repeat(args[3].toInt()) { create(s, "${args[2]}-${it.toString().padStart(4, '0')}") }
            println("WRITER_OK")
            return
        }
        if (args.firstOrNull() == "restart") {
            val s = store(File(args[1]))
            must(all(s).size == 3, "fresh process identity count changed")
            val p = s.get("kora") ?: error("kora absent")
            must(p.displayName == "Neue Kora", "renamed display did not persist")
            must(s.workingContext("kora", "kora") == "PRIVATE-KORA-REVIEW-NOTE", "private note did not persist")
            must(s.workingContext("lint", "lint") == null, "private note leaked to LINT")
            println("RESTART_OK")
            return
        }
        test("three-identities-and-fresh-process-restart") { root ->
            val s = store(root); s.seedDefaults(3)
            val ids = all(s).map { it.agentId }.toSet()
            must(ids == setOf("atlas", "kora", "lint"), "three initial identities differ: $ids")
            val original = s.get("kora")!!
            val withNote = s.saveWorkingContext("kora", "kora", "PRIVATE-KORA-REVIEW-NOTE", original.profileVersion)
            val renamed = s.rename("kora", "Neue Kora", withNote.profileVersion)
            must(renamed.agentId == withNote.agentId && renamed.memoryNamespace == withNote.memoryNamespace &&
                renamed.workspaceId == withNote.workspaceId && renamed.permissionScope == withNote.permissionScope &&
                renamed.verifiedCapabilities == withNote.verifiedCapabilities && renamed.contextObjectId == withNote.contextObjectId,
                "rename changed identity bindings or authority")
            val process = child("restart", root.path)
            val output = process.inputStream.bufferedReader().readText()
            must(process.waitFor() == 0 && "RESTART_OK" in output, "fresh process failed: $output")
        }
        test("default-profiles-have-no-execution-or-authority") { root ->
            val s = store(root); s.seedDefaults(20)
            all(store(root)).forEach { p ->
                must(p.permissionScope.isEmpty() && p.verifiedCapabilities.isEmpty(), "seed acquired grants/certifications")
                must(p.currentTaskId == null && p.lastVerifiedResult == null && p.lifecycleState == "REGISTERED", "seed fabricates execution")
                must(p.memoryNamespace == "bot-${p.agentId}" && p.workspaceId == "workspace-${p.agentId}", "binding not stable")
            }
            must(all(s).size == 20, "20-profile seed incomplete")
            s.seedDefaults(20); must(all(s).size == 20, "seed is not idempotent")
        }
        test("cross-bot-context-and-summary-isolation") { root ->
            val s = store(root); val a = create(s, "alpha"); create(s, "bravo")
            val token = "ONLY-ALPHA-SECRET-NOTE"
            val saved = s.saveWorkingContext("alpha", "alpha", token, a.profileVersion)
            must(s.workingContext("alpha", "alpha") == token, "own context missing")
            must(s.workingContext("bravo", "bravo") == null, "cross-context read")
            rejected { s.workingContext("bravo", "alpha") }
            rejected { s.saveWorkingContext("bravo", "alpha", "overwrite", saved.profileVersion) }
            must(token !in s.page(null, 20).toString(), "private text appears in catalogue")
            must(s.workingContext("alpha", "alpha") == token, "rejected overwrite changed note")
        }
        test("duplicate-identity-and-unsafe-ids") { root ->
            val s = store(root); val a = create(s, "alpha")
            rejected { create(s, "alpha") }
            listOf("../alpha", "/tmp/alpha", "alpha/beta", "alpha\\beta", "alpha\u0000tail", "ALPHA", "a", "x".repeat(65)).forEach { id ->
                rejected { create(s, id) }
            }
            must(s.get("alpha") == a && all(s).size == 1, "invalid creations changed committed state")
        }
        test("stale-version-does-not-clobber-concurrent-edit") { root ->
            val first = store(root); val original = create(first, "alpha"); val second = store(root)
            val updated = first.rename("alpha", "Accepted first", original.profileVersion)
            rejected { second.rename("alpha", "Lost update", original.profileVersion) }
            must(store(root).get("alpha") == updated, "stale writer changed accepted state")
        }
        test("bounded-profile-count-and-context-preserve-state") { root ->
            val s = ChiefBotProfileStore(root, maxProfiles = 3); s.seedDefaults(3)
            val snapshot = all(s)
            rejected { create(s, "overflow") }
            val p = s.get("kora")!!
            rejected { s.saveWorkingContext("kora", "kora", "x".repeat(20_000), p.profileVersion) }
            must(all(store(root)) == snapshot, "budget rejection damaged existing profiles")
            rejected { s.page(null, 0) }; rejected { s.page(null, Int.MAX_VALUE) }
        }
        test("unknown-schema-truncation-bitrot-trailing-data-fail-closed") { root ->
            store(root).seedDefaults(3)
            val file = File(root, "bot-profiles-v1.bin"); val original = file.readBytes()
            val schemaOffset = DataInputStream(original.inputStream()).use { input -> input.readUTF(); original.size - input.available() }
            val unknown = original.copyOf().also { java.nio.ByteBuffer.wrap(it, schemaOffset, 4).putInt(99) }
            val variants = listOf(original.copyOf(original.size / 2), original.copyOf().also { it[it.size - 1] = (it.last().toInt() xor 1).toByte() }, original + 0, unknown)
            variants.forEachIndexed { i, corrupt ->
                file.writeBytes(corrupt)
                rejected { store(root).page(null, 50) }
                rejected { store(root).seedDefaults(3) }
                must(file.readBytes().contentEquals(corrupt), "invalid file $i overwritten by load/seed")
            }
        }
        test("symlink-root-primary-and-lock-are-rejected") { root ->
            val outside = File(root, "outside").apply { mkdir() }
            val linkRoot = File(root, "linked")
            Files.createSymbolicLink(linkRoot.toPath(), outside.toPath())
            rejected { store(linkRoot).seedDefaults(3) }
            must(outside.listFiles().orEmpty().isEmpty(), "symlink root created outside files")
            for (filename in listOf("bot-profiles-v1.bin", ".bot-profiles.lock")) {
                val local = File(root, "local-${filename.hashCode()}").apply { mkdir() }
                val target = File(outside, "sentinel-${filename.hashCode()}").apply { writeText("UNCHANGED") }
                Files.createSymbolicLink(File(local, filename).toPath(), target.toPath())
                rejected { store(local).seedDefaults(3) }
                must(target.readText() == "UNCHANGED", "symlink target changed")
            }
        }
        test("concurrent-instances-no-lost-successful-creates") { root ->
            val start = CountDownLatch(1); val errors = ConcurrentLinkedQueue<Throwable>()
            val threads = (0..3).map { writer -> Thread {
                try { val s = store(root); start.await(); repeat(15) { create(s, "writer$writer-${it.toString().padStart(4, '0')}") } }
                catch (t: Throwable) { errors.add(t) }
            }.apply { start() } }
            start.countDown(); threads.forEach { it.join(30_000); must(!it.isAlive, "writer deadlocked") }
            must(errors.isEmpty(), "writers failed: $errors")
            must(all(store(root)).size == 60, "successful creations lost")
        }
        test("concurrent-processes-no-lost-successful-creates") { root ->
            val processes = listOf(child("writer", root.path, "proca", "20"), child("writer", root.path, "procb", "20"))
            processes.forEach { p ->
                val output = p.inputStream.bufferedReader().readText()
                must(p.waitFor() == 0 && "WRITER_OK" in output, "child write failed: $output")
            }
            must(all(store(root)).size == 40, "cross-process updates lost")
        }
        test("one-thousand-paginated-profiles-survive-reopen") { root ->
            val start = System.nanoTime(); val s = store(root)
            repeat(1_000) { create(s, "synthetic-${it.toString().padStart(4, '0')}") }
            val list = all(s, 37); val reopened = all(store(root), 53)
            must(list.size == 1_000 && list == reopened, "1000-profile pagination/reopen mismatch")
            must(list.map { it.agentId } == list.map { it.agentId }.sorted(), "pagination not lexical")
            val elapsed = (System.nanoTime() - start) / 1_000_000
            println("METRIC synthetic1000_total_ms=$elapsed profiles=1000 model_calls=0")
            must(elapsed < 120_000, "metadata budget exceeded")
        }
        println("RESULT passed=$passed failed=$failed")
        check(failed == 0) { "$failed independent checks failed" }
    }
}
