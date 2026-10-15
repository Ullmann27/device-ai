package at.heinz.deviceai.cognitive

import org.junit.Assert.*
import org.junit.Test
import java.nio.file.Files

/** Real JVM persistence; no Android stub APIs. */
class DevelopmentBacklogStoreTest {
    @Test fun importExportAndRestart() {
        val dir = Files.createTempDirectory("continuum-backlog").toFile()
        try {
            val file = java.io.File(dir, "backlog.json")
            val store = DevelopmentBacklogStore(file)
            val task = store.create("Test persistent developer state", 90, 15, 20)
            assertEquals("OPEN", task.status)
            store.update(task.id, "CLAIMED", "Owner approved")
            val restored = DevelopmentBacklogStore(file)
            assertEquals("CLAIMED", restored.items.single().status)
            val bridge = restored.exportBridge()
            val second = DevelopmentBacklogStore(java.io.File(dir, "second.json"))
            second.importBridge(bridge)
            assertEquals(restored.items, second.items)
        } finally { dir.deleteRecursively() }
    }

    @Test fun corruptedBridgeFailsClosed() {
        val dir = Files.createTempDirectory("continuum-backlog-corrupt").toFile()
        try {
            val file = java.io.File(dir, "backlog.json")
            val store = DevelopmentBacklogStore(file)
            store.create("Safety check")
            // Alter the integrity digest; this is not a publisher signature.
            val invalid = store.exportBridge().replace(Regex("[0-9a-f]{64}"), "0".repeat(64))
            try { store.importBridge(invalid); fail("Corrupt payload accepted") }
            catch (_: IllegalArgumentException) { /* rejected */ }
            assertEquals(1, DevelopmentBacklogStore(file).items.size)
        } finally { dir.deleteRecursively() }
    }

    @Test fun rejectsStaleGenerationAndInvalidStatus() {
        val dir = Files.createTempDirectory("continuum-backlog-versions").toFile()
        try {
            val store = DevelopmentBacklogStore(java.io.File(dir, "backlog.json"))
            val old = store.exportBridge()
            val task = store.create("Later generation")
            store.importBridge(old) // Empty remote data never deletes a local task.
            assertEquals(1, store.items.size)
            try { store.update(task.id, "DONE"); fail("Invalid transition accepted") }
            catch (_: IllegalArgumentException) { /* rejected */ }
        } finally { dir.deleteRecursively() }
    }
}
