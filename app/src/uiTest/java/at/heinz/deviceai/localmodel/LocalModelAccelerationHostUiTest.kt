package at.heinz.deviceai.localmodel

import android.content.Context
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * The accelerator switch and its crash-loop markers in real SharedPreferences (Robolectric). A second store on the
 * same preferences stands for the next process. The native GPU start itself is NOT_RUN.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class LocalModelAccelerationHostUiTest {
    private val app: Context get() = RuntimeEnvironment.getApplication()
    private fun preferences() = app.getSharedPreferences(LocalModelAcceleration.PREFERENCES, Context.MODE_PRIVATE)
    private fun nextProcess() = LocalModelAccelerationController(LocalModelAccelerationHost.store(preferences()))
    private val both = setOf(LocalModelBackend.CPU, LocalModelBackend.GPU)

    @Before fun clean() {
        assertTrue(preferences().edit().clear().commit())
    }

    @Test fun aFreshInstallationIsCpuWithoutMarkersOrNotice() {
        val controller = nextProcess()
        assertEquals(LocalModelBackend.CPU, controller.requested)
        assertNull(controller.notice)
        assertEquals(LocalModelAcceleration.Plan(LocalModelBackend.CPU, null), controller.plan(both))
        assertTrue(preferences().all.isEmpty())
    }

    @Test fun theOwnerChoiceSurvivesAProcessRestart() {
        nextProcess().request(LocalModelBackend.GPU)
        assertEquals("GPU", preferences().getString(LocalModelAcceleration.KEY_REQUESTED, null))
        assertEquals(LocalModelBackend.GPU, nextProcess().requested)
        assertEquals(LocalModelAcceleration.Plan(LocalModelBackend.GPU, null), nextProcess().plan(both))
        nextProcess().request(LocalModelBackend.CPU)
        assertEquals(LocalModelBackend.CPU, nextProcess().requested)
    }

    @Test fun theLoadMarkerIsOnDiskDuringTheGpuStartAndAKilledProcessComesBackOnCpu() {
        nextProcess().request(LocalModelBackend.GPU)
        var markerOnDiskDuringStart = false
        nextProcess().load(both) {
            // Read through a separate store: what the next process would find if this one died here.
            markerOnDiskDuringStart = LocalModelAccelerationHost.store(preferences()).gpuLoadPending
            "engine"
        }
        assertTrue(markerOnDiskDuringStart)
        assertFalse(preferences().getBoolean(LocalModelAcceleration.KEY_GPU_LOAD_PENDING, true))

        // The process is killed inside the next GPU start: only the marker write happened.
        assertTrue(preferences().edit().putBoolean(LocalModelAcceleration.KEY_GPU_LOAD_PENDING, true).commit())
        val restarted = nextProcess()
        val attempts = mutableListOf<LocalModelBackend>()
        val loaded = restarted.load(both) { attempts += it; "engine" }
        assertEquals(listOf(LocalModelBackend.CPU), attempts)
        assertEquals(LocalModelAcceleration.Notice.GPU_LOAD_INTERRUPTED, loaded.notice)
        assertEquals("CPU", preferences().getString(LocalModelAcceleration.KEY_REQUESTED, null))
        assertFalse(preferences().getBoolean(LocalModelAcceleration.KEY_GPU_LOAD_PENDING, true))
        // The notice is still there for the model centre after yet another restart.
        assertEquals(LocalModelAcceleration.Notice.GPU_LOAD_INTERRUPTED, nextProcess().notice)
    }

    @Test fun aFailedGpuStartIsStoredAsCpuWithItsNotice() {
        nextProcess().request(LocalModelBackend.GPU)
        val loaded = nextProcess().load(both) { backend ->
            check(backend != LocalModelBackend.GPU) { "Failed to create engine" }
            "engine"
        }
        assertEquals(LocalModelBackend.CPU, loaded.backend)
        assertEquals(LocalModelBackend.CPU, nextProcess().requested)
        assertEquals(LocalModelAcceleration.Notice.GPU_START_FAILED, nextProcess().notice)
    }

    @Test fun theFirstGpuAnswerIsProvenPerModelFileAcrossRestarts() {
        val sha = "d".repeat(64)
        nextProcess().request(LocalModelBackend.GPU)
        val first = nextProcess()
        assertTrue(first.generationNeedsMarker(LocalModelBackend.GPU, sha))
        first.generationStarting()
        assertTrue(preferences().getBoolean(LocalModelAcceleration.KEY_GPU_GENERATION_PENDING, false))
        first.generationEnded(sha, completed = true)
        assertFalse(preferences().getBoolean(LocalModelAcceleration.KEY_GPU_GENERATION_PENDING, true))
        assertFalse(nextProcess().generationNeedsMarker(LocalModelBackend.GPU, sha))
        assertTrue(nextProcess().generationNeedsMarker(LocalModelBackend.GPU, "e".repeat(64)))
    }

    @Test fun unreadableStoredValuesMeanCpuAndNoNotice() {
        assertTrue(preferences().edit().putString(LocalModelAcceleration.KEY_REQUESTED, "NPU")
            .putString(LocalModelAcceleration.KEY_NOTICE, "SOMETHING_ELSE").commit())
        assertEquals(LocalModelBackend.CPU, nextProcess().requested)
        assertNull(nextProcess().notice)
    }

    @Test fun theProcessUsesOneControllerForRuntimeAndModelCentre() {
        assertSame(LocalModelAccelerationHost.controller(app), LocalModelAccelerationHost.controller(app))
    }
}
