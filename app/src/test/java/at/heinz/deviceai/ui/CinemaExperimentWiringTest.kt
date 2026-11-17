package at.heinz.deviceai.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CinemaExperimentWiringTest {
    @Test fun ownerCanOnlyToggleExperimentsActuallyAttachedToCurrentScene() {
        val all = JarvisCinematicSettings.Experiment.entries
        assertEquals(15, all.size)
        val connected = all.filter { it.wired }
        assertEquals(15, connected.size)
        assertTrue(connected.contains(JarvisCinematicSettings.Experiment.EXP_02_RIM))
        assertTrue(connected.contains(JarvisCinematicSettings.Experiment.EXP_14_GLASS_SHADER))
        assertTrue(JarvisCinematicSettings.Experiment.EXP_11_ORB_LINK.wired)
        assertTrue(JarvisCinematicSettings.Experiment.EXP_03_DEPTH_GLASS.wired)
        assertTrue(JarvisCinematicSettings.Experiment.EXP_07_GAZE.wired)
        assertTrue(JarvisCinematicSettings.Experiment.EXP_10_SENTENCE.wired)
        assertTrue(JarvisCinematicSettings.Experiment.EXP_15_FRAME_BUDGET.wired)
    }
}
