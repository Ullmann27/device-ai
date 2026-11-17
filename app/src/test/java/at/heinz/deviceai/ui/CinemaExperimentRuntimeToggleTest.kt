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

    @Test fun freshCinema52InstallStartsWithAllWiredEffectsEnabled() {
        val prefs = JarvisCinematicSettings.preferences(context)
        prefs.edit().clear().commit()
        assertTrue("Cinema is the Code52 default, not an opt-in", JarvisCinematicSettings.cinemaOptIn(context))
        JarvisCinematicSettings.activateCinema52ByDefault(context)
        assertTrue(JarvisCinematicSettings.depthExperimentEnabled(context))
        assertTrue(JarvisCinematicSettings.enabled(context))
        assertTrue(JarvisCinematicSettings.Experiment.entries.all {
            !it.wired || JarvisCinematicSettings.experimentEnabled(context, it)
        })
    }

    @Test fun inPlaceUpgradeRestoresPreviouslyDisabledCinemaEffectsExactlyOnce() {
        val prefs = JarvisCinematicSettings.preferences(context)
        prefs.edit().clear()
            .putBoolean(JarvisCinematicSettings.KEY, false)
            .putBoolean(JarvisCinematicSettings.DEPTH_EXPERIMENT_KEY, false)
            .putBoolean("exp52.EXP_03_DEPTH_GLASS", false)
            .putBoolean("exp52.EXP_15_FRAME_BUDGET", false)
            .commit()
        assertFalse("Prove the old stored OFF state", JarvisCinematicSettings.cinemaOptIn(context))
        JarvisCinematicSettings.activateCinema52ByDefault(context)
        assertTrue(JarvisCinematicSettings.cinemaOptIn(context))
        assertTrue(JarvisCinematicSettings.Experiment.entries.all {
            !it.wired || JarvisCinematicSettings.experimentEnabled(context, it)
        })

        // Reopening the app must not silently rewrite a later, deliberate per-effect choice.
        JarvisCinematicSettings.setExperiment(context,
            JarvisCinematicSettings.Experiment.EXP_03_DEPTH_GLASS, false)
        JarvisCinematicSettings.activateCinema52ByDefault(context)
        assertFalse(JarvisCinematicSettings.experimentEnabled(context,
            JarvisCinematicSettings.Experiment.EXP_03_DEPTH_GLASS))
        assertTrue(JarvisCinematicSettings.cinemaOptIn(context))
    }

    @Test fun alwaysOnCinemaStillHonorsThermalPowerSavingAndReducedMotion() {
        JarvisCinematicSettings.activateCinema52ByDefault(context)
        val optIn = JarvisCinematicSettings.cinemaOptIn(context)
        val exp15 = JarvisCinematicSettings.experimentEnabled(context,
            JarvisCinematicSettings.Experiment.EXP_15_FRAME_BUDGET)
        assertTrue(optIn && exp15)
        val warm = JarvisRenderQuality.adaptiveAnimation(
            foreground = true, powerSave = false, thermalStatus = 2,
            reducedMotion = false, inferenceActive = true,
            cinemaOptIn = optIn, experimentEnabled = exp15)
        assertTrue(warm.animate)
        assertTrue(warm.frameDelayMillis >= 66L)
        assertTrue(warm.particleCount <= 12)
        assertTrue(warm.orbitSegments <= 40)
        val stop = JarvisRenderQuality.adaptiveAnimation(
            foreground = true, powerSave = true, thermalStatus = 3,
            reducedMotion = true, inferenceActive = true,
            cinemaOptIn = optIn, experimentEnabled = exp15)
        assertFalse("Critical safety must still stop decorative motion", stop.animate)
    }

    /**
     * Code54 B2: the EXP_06 label changed („Fensterlicht und Lesefaden am Avatar“), its stored key did not. An owner
     * who never touched the switch keeps both effects on (fresh install and already migrated install); a deliberate
     * OFF stays OFF across the one-time migration. OFF/ON gates the face light exactly as the hologram reads it each
     * frame (ContinuumHologramView → FilamentAvatarRenderer → CinemaWindowLightState.resolve), even while the gaze
     * bridge has published a window light.
     */
    @Test fun exp06DefaultPersistenceAndFaceLightGate() {
        val exp06 = JarvisCinematicSettings.Experiment.EXP_06_FACE_LIGHT
        assertTrue(exp06.wired)
        assertTrue(exp06.label == "Fensterlicht und Lesefaden am Avatar")
        val prefs = JarvisCinematicSettings.preferences(context)
        prefs.edit().clear().commit()
        assertTrue("fresh install, never touched", JarvisCinematicSettings.experimentEnabled(context, exp06))
        JarvisCinematicSettings.activateCinema52ByDefault(context)
        prefs.edit().remove("exp52.EXP_06_FACE_LIGHT").commit()
        JarvisCinematicSettings.activateCinema52ByDefault(context)
        assertTrue("migrated install without a stored choice", JarvisCinematicSettings.experimentEnabled(context, exp06))

        JarvisCinematicSettings.setExperiment(context, exp06, false)
        assertFalse("stored under the unchanged key", prefs.getBoolean("exp52.EXP_06_FACE_LIGHT", true))
        JarvisCinematicSettings.activateCinema52ByDefault(context)
        assertFalse("a deliberate OFF survives the migration", JarvisCinematicSettings.experimentEnabled(context, exp06))

        val accent = 0xFF33CCFF.toInt()
        try {
            at.heinz.deviceai.avatar3d.CinemaWindowLightState.apply(accent, .4f, open = true, gazeY = -.2f)
            val off = at.heinz.deviceai.avatar3d.CinemaWindowLightState.resolve(
                JarvisCinematicSettings.experimentEnabled(context, exp06), accent, true, .4f, -.2f)
            assertFalse("EXP_06 OFF: no window light on the face, even with a published bridge light", off.open)
            JarvisCinematicSettings.setExperiment(context, exp06, true)
            val on = at.heinz.deviceai.avatar3d.CinemaWindowLightState.resolve(
                JarvisCinematicSettings.experimentEnabled(context, exp06), accent, true, .4f, -.2f)
            assertTrue("EXP_06 ON: the window lights the face again", on.open)
            assertTrue(on.accentArgb == accent)
        } finally {
            at.heinz.deviceai.avatar3d.CinemaWindowLightState.reset()
        }
    }

}
