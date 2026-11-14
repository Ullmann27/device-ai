package at.heinz.deviceai.avatar3d

import at.heinz.deviceai.ui.CinematicWorkstationLayout
import at.heinz.deviceai.ui.CockpitMonitorMotion
import at.heinz.deviceai.ui.JarvisShellLayout.Slot
import org.junit.Assert.*
import org.junit.Test

class CockpitBunkerTransformsTest {
    @Test fun monitorFlightNeverMovesRoomOrDeskAcrossWindows() {
        for ((width, height) in listOf(240f to 500f, 360f to 780f, 412f to 915f,
            600f to 800f, 884f to 760f, 1280f to 800f, 1536f to 960f)) {
            for (font in listOf(1f, 1.5f, 2f)) {
                val shell = CinematicWorkstationLayout.create(width, height, font)
                val stage = shell[Slot.AVATAR]!!
                val monitor = shell[Slot.WORK_PROCESS]!!
                val frame = CockpitStageFrame(stage.width, stage.height,
                    monitor.left - stage.left, monitor.top - stage.top,
                    monitor.right - stage.left, monitor.bottom - stage.top)
                val room = CockpitBunkerTransforms.room(frame)
                val desk = CockpitBunkerTransforms.desk(frame)
                for (step in 0..100) {
                    val moved = CockpitMonitorMotion.pose(frame, step / 100f)
                    assertArrayEquals(room, CockpitBunkerTransforms.room(moved), 0f)
                    assertArrayEquals(desk, CockpitBunkerTransforms.desk(moved), 0f)
                }
                for (matrix in listOf(room, desk)) {
                    assertTrue(matrix.all(Float::isFinite))
                    assertTrue(matrix[0] > 0f)
                    assertEquals(matrix[0], matrix[5], 0f)
                    assertEquals(matrix[0], matrix[10], 0f)
                }
            }
        }
    }
}
