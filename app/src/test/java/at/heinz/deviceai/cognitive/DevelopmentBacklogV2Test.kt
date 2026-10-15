package at.heinz.deviceai.cognitive

import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.nio.file.Files

class DevelopmentBacklogV2Test {
    @Test fun realNodeAndroidNodeRoundTripAndConflict() {
        val dir = Files.createTempDirectory("backlog-wire").toFile()
        try {
            val script = File("../.github/continuum/backlog-interop.mjs").canonicalFile
            fun node(mode: String, input: String = ""): String {
                val process = ProcessBuilder("node", script.path, mode).redirectError(ProcessBuilder.Redirect.INHERIT).start()
                process.outputStream.bufferedWriter().use { it.write(input) }
                val out = process.inputStream.bufferedReader().readText()
                assertEquals(0, process.waitFor())
                return out
            }
            val store = DevelopmentBacklogStore(File(dir, "android.json"))
            store.importBridge(node("create"))
            val task = store.items.single()
            assertEquals("Grüße 🧠 / Test", task.title)
            store.update(task.id, "BLOCKED", "Android geprüft")
            val returned = node("advance", store.exportBridge())
            store.importBridge(returned)
            assertEquals("OPEN", store.items.single().status)
            assertEquals(3, store.items.single().revision)
            assertEquals(DevelopmentBacklogWire.canonical(DevelopmentBacklogWire.decode(returned).getJSONArray("tasks")),
                DevelopmentBacklogWire.canonical(DevelopmentBacklogWire.decode(store.exportBridge()).getJSONArray("tasks")))
            val remote = node("advance", returned)
            store.prioritize(task.id, 99, 1, 1)
            val before = store.exportBridge()
            try { store.importBridge(remote); fail("Conflict accepted") } catch (_: IllegalArgumentException) {}
            assertEquals(before, store.exportBridge())
            store.importBridge(remote, resolution = "incoming")
            assertEquals("BLOCKED", DevelopmentBacklogStore(File(dir,"android.json")).items.single().status)
        } finally { dir.deleteRecursively() }
    }
    @Test fun concurrentStoresReloadAndCorruptionRequiresExplicitRecovery() {
        val dir=Files.createTempDirectory("backlog-recovery").toFile()
        try {
            val file=File(dir,"b.json")
            val a=DevelopmentBacklogStore(file); val b=DevelopmentBacklogStore(file)
            a.create("one"); b.create("two")
            assertEquals(2, a.items.size)
            val id=a.items.first().id
            a.update(id,"CLAIMED")
            try { a.update(id,"DONE"); fail("Unverified DONE") } catch (_: IllegalArgumentException) {}
            file.writeText("broken")
            try { a.items; fail("Silent reset") } catch (_: Exception) {}
            a.recover()
            assertEquals(2,a.items.size)
        } finally { dir.deleteRecursively() }
    }
}
