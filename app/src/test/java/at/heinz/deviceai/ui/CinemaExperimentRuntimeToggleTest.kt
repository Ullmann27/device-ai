package at.heinz.deviceai.ui

import android.graphics.RectF
import org.robolectric.RuntimeEnvironment
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class CinemaExperimentRuntimeToggleTest {
    private val context get() = RuntimeEnvironment.getApplication()

    @Before fun cinemaOptedIn() {
        JarvisCinematicSettings.preferences(context).edit().clear()
            .putBoolean(JarvisCinematicSettings.KEY, true)
            .putBoolean(JarvisCinematicSettings.DEPTH_EXPERIMENT_KEY, true)
            .apply()
    }

    @Test fun exp03OnOffChangesWhetherAProtectedPlateIsRecessed() {
        val sheet = RectF(80f, 120f, 480f, 760f)
        val onRect = RectF(); val offRect = RectF()
        assertTrue(JarvisCinematicSettings.experimentEnabled(context,
            JarvisCinematicSettings.Experiment.EXP_03_DEPTH_GLASS))
        assertTrue(JarvisProtectedDepthSurfaces.recess(sheet, 3f, onRect))
        JarvisCinematicSettings.setExperiment(context,
            JarvisCinematicSettings.Experiment.EXP_03_DEPTH_GLASS, false)
        assertFalse(JarvisCinematicSettings.experimentEnabled(context,
            JarvisCinematicSettings.Experiment.EXP_03_DEPTH_GLASS))
        // Off keeps the generic sheet; on recedes it.
        offRect.set(sheet)
        assertNotEquals(onRect, offRect)
        assertTrue(onRect.width() < offRect.width())
    }

    @Test fun exp15OnOffChangesTheResolvedAnimationBudget() {
        assertTrue(JarvisCinematicSettings.experimentEnabled(context,
            JarvisCinematicSettings.Experiment.EXP_15_FRAME_BUDGET))
        val on = JarvisRenderQuality.adaptiveAnimation(true, false, 2, false, false,
            JarvisCinematicSettings.cinemaOptIn(context),
            JarvisCinematicSettings.experimentEnabled(context,
                JarvisCinematicSettings.Experiment.EXP_15_FRAME_BUDGET))
        JarvisCinematicSettings.setExperiment(context,
            JarvisCinematicSettings.Experiment.EXP_15_FRAME_BUDGET, false)
        assertFalse(JarvisCinematicSettings.experimentEnabled(context,
            JarvisCinematicSettings.Experiment.EXP_15_FRAME_BUDGET))
        val off = JarvisRenderQuality.adaptiveAnimation(true, false, 2, false, false,
            JarvisCinematicSettings.cinemaOptIn(context),
            JarvisCinematicSettings.experimentEnabled(context,
                JarvisCinematicSettings.Experiment.EXP_15_FRAME_BUDGET))
        assertTrue(on.frameDelayMillis > off.frameDelayMillis)
        assertTrue(on.particleCount < off.particleCount)
    }

    @Test fun cinemaMasterOffDisablesTheNewExperiments() {
        JarvisCinematicSettings.preferences(context).edit()
            .putBoolean(JarvisCinematicSettings.DEPTH_EXPERIMENT_KEY, false).apply()
        assertFalse(JarvisCinematicSettings.cinemaOptIn(context))
        assertFalse(JarvisCinematicSettings.experimentEnabled(context,
            JarvisCinematicSettings.Experiment.EXP_03_DEPTH_GLASS))
        assertFalse(JarvisCinematicSettings.experimentEnabled(context,
            JarvisCinematicSettings.Experiment.EXP_07_GAZE))
        assertFalse(JarvisCinematicSettings.experimentEnabled(context,
            JarvisCinematicSettings.Experiment.EXP_10_SENTENCE))
        assertFalse(JarvisCinematicSettings.experimentEnabled(context,
            JarvisCinematicSettings.Experiment.EXP_15_FRAME_BUDGET))
    }
}
