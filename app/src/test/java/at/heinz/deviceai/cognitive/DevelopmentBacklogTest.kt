package at.heinz.deviceai.cognitive

import at.heinz.deviceai.learning.StrictJson
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.File
import java.nio.file.Files
import java.nio.file.attribute.FileTime
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * The Kotlin backlog must behave exactly like .github/continuum/development-backlog.mjs:
 * the shared fixtures were produced by the Node implementation
 * (node .github/continuum/backlog-fixtures.mjs write) and are checked here byte by byte.
 */
class DevelopmentBacklogTest {
    private fun resource(name: String): ByteArray =
        javaClass.getResourceAsStream("/continuum/backlog/$name")?.use { it.readBytes() } ?: error("missing fixture $name")

    @Suppress("UNCHECKED_CAST")
    private fun json(name: String): Any? = StrictJson.parse(String(resource(name), Charsets.UTF_8))

    @Suppress("UNCHECKED_CAST")
    private fun Any?.map() = this as Map<String, Any?>

    @Suppress("UNCHECKED_CAST")
    private fun Any?.list() = this as List<Any?>

    private fun withDir(block: (File) -> Unit) {
        val dir = Files.createTempDirectory("backlog-test").toFile()
        try { block(dir) } finally { dir.deleteRecursively() }
    }

    private fun code(block: () -> Unit): String? = try { block(); null } catch (e: BacklogException) { e.code }

    @Test fun conformanceVectorsMatchTheNodeImplementation() {
        val cases = json("conformance.json").map()["cases"].list()
        assertEquals(5, cases.size)
        for (raw in cases) {
            val case = raw.map()
            val start = case["start"].map()
            var doc: Map<String, Any?> = DevelopmentBacklog.emptyDocument(start["at"] as Long, start["actor"] as String)
            case["steps"].list().forEachIndexed { k, rawStep ->
                val step = rawStep.map()
                val outcome = try {
                    doc = DevelopmentBacklog.applyOp(doc, step["op"].map()).doc
                    "ok"
                } catch (e: BacklogException) {
                    e.code
                }
                assertEquals("${case["name"]} step $k", step["expect"], outcome)
            }
            val final = case["final"].map()
            assertEquals("${case["name"]} revision", final["revision"], doc["revision"])
            assertEquals("${case["name"]} integrity", final["integrity"], DevelopmentBacklog.integrityOf(doc))
            val states = doc["tasks"].list().associate { it.map()["id"] to it.map()["state"] }
            assertEquals("${case["name"]} states", final["states"].map(), states)
        }
    }

    @Test fun fixtureDocumentsRoundTripByteIdentically() {
        for (name in listOf("valid-v2.json", "unknown-fields.json", "v1-migrated.json", "roundtrip-node.json")) {
            val bytes = resource(name)
            val decoded = DevelopmentBacklog.decode(bytes)
            assertNull(name, decoded.migratedFrom)
            assertArrayEquals(name, bytes, DevelopmentBacklog.encode(decoded.doc))
        }
    }

    @Test fun unknownFieldsSurviveOperationsAndRewrites() {
        val doc = DevelopmentBacklog.decode(resource("unknown-fields.json")).doc
        val changed = DevelopmentBacklog.applyOp(doc, mapOf("op" to "setPriority", "actor" to "android-app", "at" to 70_000L,
            "id" to "task-beta", "priority" to 5L)).doc
        val again = DevelopmentBacklog.decode(DevelopmentBacklog.encode(changed)).doc
        assertEquals(mapOf("flags" to listOf(1L, "x", true, null), "nested" to mapOf("k" to "v")), again["futureFeature"])
        val gamma = again["tasks"].list().map { it.map() }.first { it["id"] == "task-gamma" }
        assertEquals("bleibt erhalten", gamma["note"])
        assertEquals((doc["revision"] as Long) + 1, again["revision"])
    }

    @Test fun invalidDocumentsAreRejectedWithTheSharedCodes() {
        val cases = json("invalid.json").list()
        assertTrue(cases.size >= 15)
        for (raw in cases) {
            val case = raw.map()
            val name = case["file"] as String
            assertEquals(name, case["code"], code { DevelopmentBacklog.decode(resource(name)) })
        }
    }

    @Test fun plannerBacklogV1IsMigratedExactlyLikeNode() {
        val v1 = resource("v1-planner.json")
        assertEquals("UNSUPPORTED_VERSION", code { DevelopmentBacklog.decode(v1) })
        val migrated = DevelopmentBacklog.decode(v1, 1_700_000_000_000L, "migration")
        assertEquals(1, migrated.migratedFrom)
        assertArrayEquals(resource("v1-migrated.json"), DevelopmentBacklog.encode(migrated.doc))
        val tasks = migrated.doc["tasks"].list().map { it.map() }
        assertTrue(tasks.isNotEmpty() && tasks.all { it["state"] == "PROPOSED" && it["kind"] == "INVESTIGATE" })
        // nothing of the v1 input is dropped: planner fields live on in subject / migratedFrom
        assertTrue(tasks.all { (it["subject"].map()["file"] as String).startsWith("app/src/") && it["subject"].map()["action"] == "INVESTIGATE_ONLY" })
        assertEquals("review-required", migrated.doc["migratedFrom"].map()["mode"])
    }

    @Test fun canonicalJsonMatchesJavaScriptEscapingAndOrdering() {
        val s = "\u0000\u0001\b\t\n\u000b\u000c\r\u001f\u007f \"\\/é😀"
        assertEquals("\"\\u0000\\u0001\\b\\t\\n\\u000b\\f\\r\\u001f\u007f \\\"\\\\/é😀\"", DevelopmentBacklog.canonical(s))
        assertEquals("{\"A\":{},\"a\":[true,null,0,\"x\"],\"aa\":0,\"b\":1,\"é\":\"\"}",
            DevelopmentBacklog.canonical(linkedMapOf("b" to 1L, "a" to listOf(true, null, 0L, "x"), "A" to emptyMap<String, Any?>(), "é" to "", "aa" to 0L)))
        assertEquals("INVALID_FIELD", code { DevelopmentBacklog.canonical(1.5) })
        assertEquals("INVALID_FIELD", code { DevelopmentBacklog.canonical(DevelopmentBacklog.MAX_SAFE + 1) })
        assertEquals("INVALID_FIELD", code { DevelopmentBacklog.canonical("\ud800") })
        assertEquals("ab", DevelopmentBacklog.clip("ab😀", 3))
        assertEquals("a b", DevelopmentBacklog.singleLine("a\nb", 10))
    }

    // ------------------------------------------------------------------
    // File store
    // ------------------------------------------------------------------
    private fun propose(id: String, at: Long = 1L) = mapOf("op" to "propose", "actor" to "test", "at" to at,
        "task" to mapOf("id" to id, "title" to "Aufgabe $id", "kind" to "TEST", "source" to "android"))

    @Test fun writesAtomicallyKeepsBackupAndVerifiesWhatItWrote() = withDir { dir ->
        val store = DevelopmentBacklogStore(File(dir, "backlog.json"), "test", clock = { 5L })
        val first = store.apply(listOf(propose("task-one")))
        assertTrue(first.written)
        assertEquals(1L, first.doc["revision"])
        val second = store.apply(listOf(propose("task-two", 2L)))
        assertEquals(2L, second.doc["revision"])
        val backup = DevelopmentBacklog.decode(File(dir, "backlog.json.bak").readBytes()).doc
        assertEquals(1L, backup["revision"])
        assertFalse(File(dir, "backlog.json.lock").exists())
        // unchanged content is not rewritten
        val same = store.update { it }
        assertFalse(same.written)
        assertEquals(listOf("backlog.json", "backlog.json.bak"), dir.list()!!.sorted())
    }

    @Test fun corruptFileIsRecoveredFromBackupAndQuarantinedNeverDeleted() = withDir { dir ->
        val file = File(dir, "backlog.json")
        val store = DevelopmentBacklogStore(file, "test", clock = { 9L })
        store.apply(listOf(propose("task-one")))
        store.apply(listOf(propose("task-two", 2L)))
        val damaged = file.readBytes().also { it[it.size / 2] = (it[it.size / 2].toInt() xor 0x20).toByte() }
        file.writeBytes(damaged)
        val read = store.read()
        assertNotNull(read.recovered)
        assertNull("read() never modifies files", read.recovered!!.quarantined)
        assertArrayEquals(damaged, file.readBytes())
        val result = store.apply(listOf(propose("task-three", 3L)))
        assertNotNull(result.recovered)
        val quarantined = File(result.recovered!!.quarantined!!)
        assertArrayEquals("corrupt file preserved for inspection", damaged, quarantined.readBytes())
        val ids = result.doc["tasks"].list().map { it.map()["id"] }
        assertEquals(listOf("task-one", "task-three"), ids)
        assertEquals(2L, result.doc["revision"])
    }

    @Test fun corruptFileWithoutValidBackupFailsClosedAndTouchesNothing() = withDir { dir ->
        val file = File(dir, "backlog.json")
        file.writeText("{\"schema\":\"continuum-development-backlog\"")
        val store = DevelopmentBacklogStore(file, "test")
        assertEquals("CORRUPT", code { store.read() })
        assertEquals("CORRUPT", code { store.apply(listOf(propose("task-one"))) })
        assertEquals("{\"schema\":\"continuum-development-backlog\"", file.readText())
        assertEquals(listOf("backlog.json"), dir.list()!!.toList())
    }

    @Test fun futureSchemaVersionIsNeverOverwrittenOrRecoveredAway() = withDir { dir ->
        val file = File(dir, "backlog.json")
        val future = resource("invalid-future-version.json")
        file.writeBytes(future)
        File(dir, "backlog.json.bak").writeBytes(resource("valid-v2.json"))
        val store = DevelopmentBacklogStore(file, "test")
        assertEquals("UNSUPPORTED_VERSION", code { store.read() })
        assertEquals("UNSUPPORTED_VERSION", code { store.apply(listOf(propose("task-one"))) })
        assertArrayEquals(future, file.readBytes())
    }

    @Test fun revisionConflictWritesNothing() = withDir { dir ->
        val file = File(dir, "backlog.json")
        val store = DevelopmentBacklogStore(file, "test")
        store.apply(listOf(propose("task-one")))
        val before = file.readBytes()
        assertEquals("CONFLICT", code { store.apply(listOf(propose("task-two")), expectedRevision = 0L) })
        assertArrayEquals(before, file.readBytes())
        assertEquals(2L, store.apply(listOf(propose("task-two")), expectedRevision = 1L).doc["revision"])
    }

    @Test fun failingOperationBatchIsAllOrNothingOrReportedPerOperation() = withDir { dir ->
        val file = File(dir, "backlog.json")
        val store = DevelopmentBacklogStore(file, "test")
        assertEquals("NOT_FOUND", code {
            store.apply(listOf(propose("task-one"), mapOf("op" to "accept", "actor" to "test", "at" to 2L, "id" to "task-missing")))
        })
        assertFalse("nothing written for a rejected batch", file.exists())
        val lenient = store.apply(listOf(propose("task-one"), mapOf("op" to "accept", "actor" to "test", "at" to 2L, "id" to "task-missing")),
            stopOnError = false)
        assertEquals(listOf(true, false), lenient.results.map { it.ok })
        assertEquals("NOT_FOUND", lenient.results[1].code)
        assertEquals(1L, lenient.doc["revision"])
    }

    @Test fun freshLockTimesOutAndStaleLockIsBroken() = withDir { dir ->
        val file = File(dir, "backlog.json")
        val lock = File(dir, "backlog.json.lock").apply { writeText("{\"token\":\"someone-else\"}\n") }
        val impatient = DevelopmentBacklogStore(file, "test", staleLockMillis = 60_000, waitMillis = 200)
        assertEquals("LOCK_TIMEOUT", code { impatient.apply(listOf(propose("task-one"))) })
        assertTrue("a foreign lock is never removed while fresh", lock.exists())
        assertFalse(file.exists())
        Files.setLastModifiedTime(lock.toPath(), FileTime.fromMillis(System.currentTimeMillis() - 120_000))
        val result = impatient.apply(listOf(propose("task-one")))
        assertTrue(result.written)
        assertFalse(lock.exists())
        assertTrue(dir.list()!!.none { it.contains(".stale-") })
    }

    @Test fun interruptedWriteLeftoversAreCleanedAndNeverRead() = withDir { dir ->
        val file = File(dir, "backlog.json")
        val store = DevelopmentBacklogStore(file, "test", staleLockMillis = 1_000)
        store.apply(listOf(propose("task-one")))
        val leftover = File(dir, "backlog.json.tmp-deadbeef").apply { writeText("{\"half\":") }
        leftover.setLastModified(System.currentTimeMillis() - 10_000)
        assertEquals(1L, store.read().doc["revision"])
        store.apply(listOf(propose("task-two")))
        assertFalse(leftover.exists())
    }

    @Test fun concurrentWritersInSeparateStoresLoseNoUpdate() = withDir { dir ->
        val file = File(dir, "backlog.json")
        val threads = 6
        val perThread = 4
        val pool = Executors.newFixedThreadPool(threads)
        val start = CountDownLatch(1)
        val errors = java.util.Collections.synchronizedList(ArrayList<Throwable>())
        repeat(threads) { t ->
            pool.execute {
                val store = DevelopmentBacklogStore(file, "writer-$t", waitMillis = 60_000)
                start.await()
                repeat(perThread) { k ->
                    try { store.apply(listOf(propose("task-$t-$k", (t * 100 + k).toLong()))) } catch (e: Throwable) { errors += e }
                }
            }
        }
        start.countDown()
        pool.shutdown()
        assertTrue(pool.awaitTermination(120, TimeUnit.SECONDS))
        if (errors.isNotEmpty()) fail("writer failed: ${errors.first()}")
        val doc = DevelopmentBacklogStore(file, "reader").read().doc
        assertEquals((threads * perThread).toLong(), doc["revision"])
        assertEquals(threads * perThread, doc["tasks"].list().size)
        assertFalse(File(dir, "backlog.json.lock").exists())
    }

    @Test fun plannerV1FileIsMigratedOnFirstWriteAndOriginalKept() = withDir { dir ->
        val file = File(dir, "backlog.json")
        val v1 = resource("v1-planner.json")
        file.writeBytes(v1)
        val store = DevelopmentBacklogStore(file, "migration", clock = { 1_700_000_000_000L })
        val read = store.read()
        assertEquals(1, read.migratedFrom)
        assertArrayEquals("reading never rewrites", v1, file.readBytes())
        val result = store.update { it }
        assertTrue(result.written)
        assertEquals(1, result.migratedFrom)
        assertArrayEquals(resource("v1-migrated.json"), file.readBytes())
        assertArrayEquals(v1, File(dir, "backlog.json.v1.json").readBytes())
    }

    @Test fun taskViewsExposeStateForTheUi() {
        val doc = DevelopmentBacklog.decode(resource("valid-v2.json")).doc
        val views = DevelopmentBacklog.views(doc).associateBy { it.id }
        assertEquals("DONE", views.getValue("task-alpha").state)
        assertEquals(1, views.getValue("task-alpha").evidenceCount)
        assertTrue(views.getValue("task-alpha").terminal)
        assertEquals("READY", views.getValue("task-beta").state)
        // retry cleared the review failure; the reason stays in the history
        assertNull(views.getValue("task-beta").errorCode)
        assertEquals("neuer Anlauf nach Analyse", views.getValue("task-beta").lastReason)
        assertEquals(0L, views.getValue("task-beta").attempts)
        assertEquals("task-beta", DevelopmentBacklog.nextEligible(doc)?.get("id"))
    }
}
