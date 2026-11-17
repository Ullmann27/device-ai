package at.heinz.deviceai.learning

import android.content.Context
import android.content.Intent
import android.os.BatteryManager
import at.heinz.deviceai.BootstrapMemoryStore
import at.heinz.deviceai.cognitive.ContinuumAutonomyControl
import at.heinz.deviceai.cognitive.ContinuumAutonomyLevel
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowStatFs

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class NeuralPracticeSchedulerGateUiTest {
    private lateinit var context: Context
    private lateinit var memory: BootstrapMemoryStore
    private lateinit var scheduler: NeuralPracticeScheduler
    private lateinit var original: ByteArray
    private val safe = ContinuumLearningResources(80, false, 0, 1_000_000_000L)
    private var observed = safe
    private var elapsed = 0L
    private var reads = 0
    private var recoverAfterRead = false
    private var failRead = false

    @Before fun prepareApprovedFixture() {
        context = RuntimeEnvironment.getApplication()
        for (name in listOf("device_ai_neural_practice_v1", "device_ai_bootstrap_v1"))
            assertTrue(context.getSharedPreferences(name, Context.MODE_PRIVATE).edit().clear().commit())
        assertTrue(ContinuumAutonomyControl.setLevel(context,
            ContinuumAutonomyLevel.RESEARCH_AND_PROPOSALS))
        val lab = NeuralDialogue()
        lab.teach("Servus lieber Freund", 0, true)
        assertEquals(1, lab.lessonCount())
        original = lab.save()
        memory = BootstrapMemoryStore(context)
        assertTrue(memory.saveNeuralSnapshot(original, "Synthetic fixture"))
        scheduler = NeuralPracticeScheduler(context, { elapsed }, {
            reads++
            if (failRead) throw IllegalStateException("Synthetic unavailable telemetry")
            observed.also { if (recoverAfterRead) observed = safe }
        })
        assertTrue(scheduler.enable())
    }

    @After fun releaseScheduledJob() {
        if (::scheduler.isInitialized) scheduler.disable()
    }

    private fun assertNoCommit() {
        assertArrayEquals(original, memory.neuralSnapshot())
        assertEquals("Synthetic fixture", memory.neuralReport())
        assertEquals(0, scheduler.attempts)
        assertEquals(0, scheduler.improvements)
    }

    private fun stopAfterFirstProgress(stop: () -> Unit) {
        val steps = mutableListOf<Int>()
        val status = scheduler.practiceOnce(progress = {
            steps += it.step
            if (it.step == 1) stop()
        })
        assertEquals(listOf(1), steps)
        assertTrue(status.contains("abgebrochen"))
        assertNoCommit()
    }

    @Test fun disabledPracticeDoesNotStartOrReadResources() {
        scheduler.disable()
        var steps = 0
        scheduler.practiceOnce(progress = { steps++ })
        assertEquals(0, steps)
        assertEquals(0, reads)
        assertNoCommit()
    }

    @Test fun ownerPauseOverridesExistingPracticeConsent() {
        assertTrue(ContinuumAutonomyControl.setLevel(context, ContinuumAutonomyLevel.PAUSED))
        var steps = 0
        scheduler.practiceOnce(progress = { steps++ })
        assertEquals(0, steps)
        assertEquals(0, reads)
        assertTrue(scheduler.enabled)
        assertNoCommit()
    }

    @Test fun moderateThermalStatusPreventsTraining() {
        observed = safe.copy(thermalStatus = 2)
        var steps = 0
        scheduler.practiceOnce(progress = { steps++ })
        assertEquals(0, steps)
        assertEquals(1, reads)
        assertNoCommit()
    }

    @Test fun unavailableResourcesFailClosed() {
        failRead = true
        var steps = 0
        scheduler.practiceOnce(progress = { steps++ })
        assertEquals(0, steps)
        assertEquals(1, reads)
        assertNoCommit()
    }

    @Test fun disablingDuringProgressStopsNextUpdate() {
        stopAfterFirstProgress { scheduler.disable() }
        assertFalse(scheduler.enabled)
        assertEquals(1, reads)
    }

    @Test fun pausingDuringProgressStopsNextUpdate() {
        stopAfterFirstProgress {
            assertTrue(ContinuumAutonomyControl.setLevel(context, ContinuumAutonomyLevel.PAUSED))
        }
        assertEquals(1, reads)
    }

    @Test fun thermalFailureStaysLatchedAfterResourceRecovery() {
        stopAfterFirstProgress {
            observed = safe.copy(thermalStatus = 2)
            recoverAfterRead = true
            elapsed = 1_500L
        }
        assertEquals(safe, observed)
        assertEquals(2, reads)
    }

    @Test fun oneShotCallerCancellationCannotResumeOrRecordAnAttempt() {
        var pulse = false
        var observedCancellation = false
        val steps = mutableListOf<Int>()
        val status = scheduler.practiceOnce(cancelled = {
            pulse.also {
                if (it) {
                    observedCancellation = true
                    pulse = false
                }
            }
        }, progress = {
            steps += it.step
            if (it.step == 1) pulse = true
        })
        assertTrue(observedCancellation)
        assertFalse(pulse)
        assertEquals(listOf(1), steps)
        assertTrue(status.contains("abgebrochen"))
        assertNoCommit()
    }

    @Test fun sharedDeadlineStopsBeforeAnotherUpdate() {
        stopAfterFirstProgress { elapsed = ContinuumOfflineLearningBudget.MAX_WALL_MILLIS }
        assertEquals(1, reads)
    }

    @Test fun newAllowedRunTrainsWithOneResourceSampleAndPreservesExclusion() {
        assertTrue(ContinuumAutonomyControl.setLevel(context, ContinuumAutonomyLevel.PAUSED))
        scheduler.practiceOnce()
        assertNoCommit()
        assertTrue(ContinuumAutonomyControl.setLevel(context,
            ContinuumAutonomyLevel.RESEARCH_AND_PROPOSALS))
        val steps = mutableListOf<Int>()
        var nested: String? = null
        scheduler.practiceOnce(progress = {
            steps += it.step
            if (it.step == 1) nested = scheduler.practiceOnce()
        })
        assertTrue(steps.isNotEmpty())
        assertTrue(steps.all { it in 1..200 })
        assertEquals("Bereits ein Lernzyklus aktiv.", nested)
        assertEquals(1, reads)
        assertEquals(1, scheduler.attempts)
        assertTrue(scheduler.improvements in 0..1)
    }

    @Test fun publicConstructorUsesActualAndroidResourceSampler() {
        ShadowStatFs.registerStats(context.filesDir, 1_000_000, 900_000, 900_000)
        context.sendStickyBroadcast(Intent(Intent.ACTION_BATTERY_CHANGED)
            .putExtra(BatteryManager.EXTRA_LEVEL, 19)
            .putExtra(BatteryManager.EXTRA_SCALE, 100))
        var steps = 0
        NeuralPracticeScheduler(context).practiceOnce(progress = { steps++ })
        assertEquals(0, steps)
        assertNoCommit()
    }
}
