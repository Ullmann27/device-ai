package at.heinz.deviceai.learning

import java.io.File
import java.lang.management.ManagementFactory
import java.nio.channels.FileChannel
import java.nio.file.Files
import java.nio.file.StandardOpenOption
import java.security.MessageDigest
import java.util.IdentityHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

object GammaChallenger {
    private val type = LearningExperienceType.INTENT_LABEL
    private var checks = 0
    private fun checkCase(name: String, block: () -> Unit) {
        block(); checks++; println("PASS\t$name")
    }
    private fun fixture(n: Int, payload: Int = 96) = List(n) { i ->
        LearningExperienceFactory.create(
            if (i % 2 == 0) type else LearningExperienceType.values()[i % 7],
            LearningProvenance.SYNTHETIC_FIXTURE, LearningTrustClass.OWNER_CONFIRMED,
            LearningStatus.values()[(i / 7) % 6],
            "gamma-b-$i-" + "s".repeat(payload), annotation = (i % 2).toString(),
            now = (i / 5).toLong()
        ).copy(updatedAt = (n + i / 11).toLong())
    }
    private fun expected(items: List<LearningExperience>, t: LearningExperienceType, limit: Int) =
        items.filter { it.type == t && it.status !in setOf(LearningStatus.REJECTED, LearningStatus.REVOKED) }
            .sortedWith(compareBy<LearningExperience> { it.updatedAt }.thenBy { it.createdAt }.thenBy { it.id })
            .takeLast(limit)
    private fun equal(store: FileLearningExperienceStore, t: LearningExperienceType = type, n: Int = 128) {
        val wanted = expected(store.all(), t, n)
        check(store.replay(t, n) == wanted) { "Selection differs for $t/$n" }
    }
    private fun fails(block: () -> Unit) {
        check(runCatching(block).isFailure) { "Expected fail-closed exception" }
    }
    private fun awaitFile(file: File) {
        val end = System.nanoTime() + TimeUnit.SECONDS.toNanos(20)
        while (!file.exists() && System.nanoTime() < end) Thread.sleep(5)
        check(file.exists()) { "Timed out waiting for ${file.name}" }
    }
    private fun child(vararg args: String): Process = ProcessBuilder(
        File(System.getProperty("java.home"), "bin/java").path, "-Xmx384m", "-cp",
        System.getProperty("java.class.path"), GammaChallenger::class.java.name, *args
    ).inheritIO().start()
    private fun retainedRecords(root: Any): Int {
        val seen = IdentityHashMap<Any, Boolean>()
        var total = 0
        fun walk(value: Any?) {
            if (value == null || seen.put(value, true) != null) return
            if (value is LearningExperience) { total++; return }
            when (value) {
                is Iterable<*> -> value.forEach { walk(it) }
                is Map<*, *> -> value.forEach { (k, v) -> walk(k); walk(v) }
                is Array<*> -> value.forEach { walk(it) }
                else -> if (value.javaClass.name.startsWith("at.heinz.deviceai.learning.")) {
                    value.javaClass.declaredFields.filter { !java.lang.reflect.Modifier.isStatic(it.modifiers) }
                        .forEach { it.isAccessible = true; walk(it.get(value)) }
                }
            }
        }
        walk(root); return total
    }
    private fun correctness(root: File) {
        val dir = File(root, "ledger")
        val store = FileLearningExperienceStore(dir)
        val records = fixture(1024)
        check(store.appendMissing(records) == records.size)
        checkCase("selection-ties-status-type-limit-rotation") {
            for (t in LearningExperienceType.values()) for (n in listOf(1, 128, 7, 64, 2, 127))
                check(store.replay(t, n) == expected(records, t, n))
            fails { store.replay(type, 0) }; fails { store.replay(type, 129) }
        }
        checkCase("caller-returned-list-ownership") {
            val before = store.replay(type, 128).toList()
            val returned = store.replay(type, 128)
            val mutable = runCatching { (returned as MutableList<LearningExperience>).clear() }.isSuccess
            check(store.replay(type, 128) == before) { "Caller list mutation poisoned future replay; mutable=$mutable" }
            println("DETAIL\tcaller-list-mutable=$mutable")
        }
        checkCase("same-instance-independent-instance-status-changes") {
            val victim = store.replay(type, 128).last()
            check(store.updateStatus(victim.id, LearningStatus.REVOKED, 9000))
            equal(store)
            check(store.replay(type).none { it.id == victim.id })
            val second = FileLearningExperienceStore(dir)
            check(second.updateStatus(victim.id, LearningStatus.CONFIRMED, 9001))
            equal(store)
            check(store.replay(type).last().id == victim.id)
        }
        checkCase("real-concurrent-cross-instance-writer") {
            val victim = store.replay(type, 128).last()
            val ready = CountDownLatch(1)
            val failure = AtomicReference<Throwable?>()
            val writer = Thread {
                try {
                    val second = FileLearningExperienceStore(dir)
                    ready.await()
                    repeat(8) { i -> second.updateStatus(victim.id,
                        if (i % 2 == 0) LearningStatus.CONFIRMED else LearningStatus.REVOKED, 10000L + i) }
                } catch (t: Throwable) { failure.set(t) }
            }
            writer.start(); ready.countDown()
            repeat(12) { store.replay(type, 128) }
            writer.join(20000); check(!writer.isAlive); failure.get()?.let { throw it }
            check(store.replay(type, 128).none { it.id == victim.id }); equal(store)
        }
        checkCase("real-child-jvm-lease-and-revocation") {
            val victim = store.replay(type, 128).last()
            val held = File(root, "held"); val release = File(root, "release"); val done = File(root, "done")
            val holder = child("hold", dir.path, held.path, release.path)
            awaitFile(held)
            val result = AtomicReference<List<LearningExperience>?>()
            val readFailure = AtomicReference<Throwable?>()
            val reader = Thread { try { result.set(store.replay(type, 128)) } catch (t: Throwable) { readFailure.set(t) } }
            reader.start()
            val writer = child("revoke", dir.path, victim.id, done.path)
            Thread.sleep(100)
            check(result.get() == null && !done.exists()) { "OS lease bypassed" }
            release.writeText("release")
            check(holder.waitFor(20, TimeUnit.SECONDS) && holder.exitValue() == 0)
            check(writer.waitFor(20, TimeUnit.SECONDS) && writer.exitValue() == 0)
            reader.join(20000); check(!reader.isAlive); readFailure.get()?.let { throw it }
            check(result.get() != null && done.exists())
            check(store.replay(type, 128).none { it.id == victim.id }); equal(store)
        }
        val selected = store.replay(type, 128).map { it.id }.toSet()
        val segment = File(dir, "experiences-v3").listFiles()!!.filter { it.extension == "seg" }
            .first { LearningExperienceCodec.decode(it.readBytes()).none { r -> r.id in selected } }
        checkCase("unselected-segment-preserved-size-mtime-corruption") {
            val bytes = segment.readBytes(); val mtime = Files.getLastModifiedTime(segment.toPath())
            val corrupt = bytes.clone(); corrupt[corrupt.size / 2] = (corrupt[corrupt.size / 2].toInt() xor 1).toByte()
            segment.writeBytes(corrupt); Files.setLastModifiedTime(segment.toPath(), mtime)
            check(segment.length() == bytes.size.toLong())
            check(Files.getLastModifiedTime(segment.toPath()) == mtime)
            fails { store.replay(type, 128) }
            segment.writeBytes(bytes); Files.setLastModifiedTime(segment.toPath(), mtime); equal(store)
        }
        checkCase("unselected-segment-delete-symlink-and-restoration") {
            val bytes = segment.readBytes(); check(segment.delete())
            fails { store.replay(type, 128) }
            val outside = File(root, "synthetic-segment-copy"); outside.writeBytes(bytes)
            Files.createSymbolicLink(segment.toPath(), outside.toPath())
            fails { store.replay(type, 128) }
            Files.delete(segment.toPath()); segment.writeBytes(bytes); equal(store)
        }
        checkCase("manifest-corruption-and-primary-loss") {
            val primary = File(dir, "experiences-v2.bin"); val bytes = primary.readBytes()
            val corrupt = bytes.clone(); corrupt[corrupt.lastIndex] = (corrupt.last().toInt() xor 1).toByte()
            primary.writeBytes(corrupt); fails { store.replay(type, 128) }
            primary.writeBytes(bytes); equal(store)
            check(primary.delete()); fails { store.replay(type, 128) }
            primary.writeBytes(bytes); equal(store)
        }
        checkCase("whole-store-deletion-and-disjoint-recreation") {
            store.replay(type, 128); check(dir.deleteRecursively())
            check(store.replay(type, 128).isEmpty())
            val fresh = LearningExperienceFactory.create(type, LearningProvenance.SYNTHETIC_FIXTURE,
                LearningTrustClass.SYNTHETIC, LearningStatus.CONFIRMED, "fresh-store-generation", now = 90000)
            check(FileLearningExperienceStore(dir).append(fresh))
            check(store.replay(type, 128) == listOf(fresh))
        }
        checkCase("retained-record-and-unchanged-storage-caps") {
            check(FileLearningExperienceStore.MAX_DURABLE_RECORDS == 4096)
            check(Lex3LearningExperienceStore.MAX_TOTAL_BYTES == 32L * 1024 * 1024)
            val retained = retainedRecords(store)
            check(retained <= 128) { "Retained $retained records" }
            val larger = FileLearningExperienceStore(File(root, "retention"))
            larger.appendMissing(records)
            for (t in LearningExperienceType.values()) {
                larger.replay(t, 128)
                val n = retainedRecords(larger)
                check(n <= 128) { "Retained $n records after $t" }
                println("DETAIL\tretained-$t=$n")
            }
        }
        println("CORRECTNESS\tchecks=$checks")
    }
    private fun benchmark(root: File) {
        val bean = ManagementFactory.getThreadMXBean() as com.sun.management.ThreadMXBean
        bean.isThreadAllocatedMemoryEnabled = true
        val tid = Thread.currentThread().id
        var sink = 0
        fun sample(label: String, n: Int, iterations: Int, block: () -> Unit) {
            repeat(iterations) { i ->
                val allocated = bean.getThreadAllocatedBytes(tid); val start = System.nanoTime()
                block()
                val nanos = System.nanoTime() - start
                println("SAMPLE\t$label\t$n\t$i\t$nanos\t${bean.getThreadAllocatedBytes(tid) - allocated}")
            }
        }
        // JVM warm-up on a separate synthetic ledger, not included in measurements.
        val warm = FileLearningExperienceStore(File(root, "jvm-warmup"))
        warm.appendMissing(fixture(256, 256)); repeat(12) { sink += warm.replay(type, 128).size }
        for (n in listOf(256, 1024, 4096)) {
            val dir = File(root, "bench-$n")
            val store = FileLearningExperienceStore(dir)
            val items = fixture(n, 256); check(store.appendMissing(items) == n)
            sample("cold-construction-replay", n, 3) { sink += FileLearningExperienceStore(dir).replay(type, 128).size }
            store.replay(type, 128)
            sample("warm-same-type", n, 6) { sink += store.replay(type, 64).size }
            sample("caller-source48-intent96", n, 4) {
                sink += store.replay(LearningExperienceType.SOURCE_TEXT, 48).size
                sink += store.replay(type, 96).size
            }
            var turn = 0
            sample("alternating-type-limit", n, 4) {
                sink += store.replay(LearningExperienceType.values()[turn++ % 7], if (turn % 2 == 0) 1 else 128).size
            }
            val victim = items.first { it.type == type }
            sample("mutation-and-replay", n, 3) {
                turn++
                store.updateStatus(victim.id, if (turn % 2 == 0) LearningStatus.CONFIRMED else LearningStatus.REVOKED,
                    100000L + turn)
                sink += store.replay(type, 64).size
            }
            println("RETAINED\t$n\t${retainedRecords(store)}")
        }
        println("SINK\t$sink")
    }
    @JvmStatic fun main(args: Array<String>) {
        when (args.firstOrNull()) {
            "hold" -> {
                FileChannel.open(File(args[1], "experiences.lock").toPath(), StandardOpenOption.WRITE).use { c ->
                    c.lock().use { File(args[2]).writeText("held"); awaitFile(File(args[3])) }
                }
            }
            "revoke" -> {
                check(FileLearningExperienceStore(File(args[1])).updateStatus(args[2], LearningStatus.REVOKED, 20000))
                File(args[3]).writeText("committed")
            }
            else -> {
                val root = Files.createTempDirectory("gamma-challenger-b-").toFile()
                println("ENV\tjava=${System.getProperty("java.version")}\tvm=${System.getProperty("java.vm.name")}")
                try {
                    if (args.firstOrNull() != "bench") correctness(root)
                    if (args.firstOrNull() != "check") benchmark(root)
                } finally { check(root.deleteRecursively()) }
            }
        }
    }
}
