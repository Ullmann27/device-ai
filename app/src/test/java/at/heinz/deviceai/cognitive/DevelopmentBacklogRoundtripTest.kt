package at.heinz.deviceai.cognitive

import at.heinz.deviceai.learning.StrictJson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

/**
 * Kotlin half of the Node -> Kotlin -> Node round trip.
 *
 * In CI (android.yml) the directory comes from CONTINUUM_BACKLOG_ROUNDTRIP_DIR:
 * Node wrote backlog.json just before this test, and Node verifies this test's
 * output right after it (backlog-fixtures.mjs roundtrip-verify). Without the
 * variable the committed Node output (roundtrip-node.json) is used, so the
 * Kotlin half always runs.
 */
class DevelopmentBacklogRoundtripTest {
    private fun resource(name: String): ByteArray =
        javaClass.getResourceAsStream("/continuum/backlog/$name")?.use { it.readBytes() } ?: error("missing fixture $name")

    @Suppress("UNCHECKED_CAST")
    private fun Any?.map() = this as Map<String, Any?>

    @Suppress("UNCHECKED_CAST")
    private fun Any?.list() = this as List<Map<String, Any?>>

    @Test fun kotlinChangesNodeWrittenBacklogWithTheSharedRules() {
        val live = System.getenv("CONTINUUM_BACKLOG_ROUNDTRIP_DIR")?.takeIf { it.isNotBlank() }
        val dir = live?.let(::File) ?: Files.createTempDirectory("backlog-roundtrip").toFile().also {
            File(it, "backlog.json").writeBytes(resource("roundtrip-node.json"))
            File(it, "node-state.json").writeBytes(resource("roundtrip-node-state.json"))
        }
        try {
            val nodeState = StrictJson.parse(File(dir, "node-state.json").readText()).map()
            val plan = StrictJson.parse(String(resource("roundtrip.json"), Charsets.UTF_8)).map()
            val store = DevelopmentBacklogStore(File(dir, "backlog.json"), "android-roundtrip")

            val before = store.read()
            assertEquals("Node output must verify unchanged", nodeState["integrity"], before.doc["integrity"])
            assertEquals(nodeState["revision"], before.doc["revision"])

            val result = store.apply(plan["ops"].list())
            val expected = plan["expected"].map()
            assertEquals((nodeState["revision"] as Long) + (expected["revisionDelta"] as Long), result.doc["revision"])
            val states = result.doc["tasks"].list().associate { it["id"] to it["state"] }
            for ((id, state) in expected["states"].map()) assertEquals(id, state, states[id])
            assertEquals("unknown Node field survives the Kotlin rewrite",
                mapOf("writtenBy" to "node", "items" to listOf(1L, 2L, 3L)), result.doc["roundtripMarker"])

            File(dir, "kotlin-ops.json").writeText(DevelopmentBacklog.canonical(plan["opLog"]) + "\n")
            File(dir, "kotlin-state.json").writeText(DevelopmentBacklog.canonical(linkedMapOf(
                "revision" to result.doc["revision"], "integrity" to result.doc["integrity"])) + "\n")
            assertTrue(File(dir, "backlog.json.bak").exists())
        } finally {
            if (live == null) dir.deleteRecursively()
        }
    }
}
