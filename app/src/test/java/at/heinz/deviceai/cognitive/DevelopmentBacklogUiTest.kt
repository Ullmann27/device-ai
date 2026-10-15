package at.heinz.deviceai.cognitive
import org.junit.Assert.*
import org.junit.Test
import java.nio.file.Files
class DevelopmentBacklogUiTest {
    @Test fun selectedTaskIsBoundToRevisionAndNodeExportCannotRunLocalModel() {
        val dir=Files.createTempDirectory("backlog-ui").toFile()
        try {
            val store=DevelopmentBacklogStore(java.io.File(dir,"b.json"))
            val task=store.create("Integrate component")
            val ui=DevelopmentUiController { "test" }
            ui.selectBacklog(task, DevelopmentExecutionMode.NODE_EXPORT)
            assertEquals(task.id,ui.state.backlogTaskId)
            assertNull(ui.start(true))
            assertTrue(ui.state.status.contains("Node"))
            ui.selectBacklog(task, DevelopmentExecutionMode.LOCAL_REVIEW)
            ui.setGoal("Different goal")
            assertNull(ui.state.backlogTaskId)
        } finally { dir.deleteRecursively() }
    }
}
