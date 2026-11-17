package at.heinz.deviceai

import android.os.Looper
import android.os.SystemClock
import at.heinz.deviceai.cognitive.AiContextCompiler
import at.heinz.deviceai.cognitive.ContextIntegrityFixture
import at.heinz.deviceai.cockpit.CockpitAnimationBus
import at.heinz.deviceai.cockpit.CockpitAnimationEvent
import at.heinz.deviceai.cockpit.MEMORY_TURN
import at.heinz.deviceai.learning.ContinuumMemoryEventHub
import at.heinz.deviceai.ui.BrainOrbAnimationRenderer
import at.heinz.deviceai.ui.JarvisSequenceEffects
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.ExecutorService
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.annotation.LooperMode

/**
 * Actual Activity construction, compiler getter, memory subscription and Android Handler dispatch.
 * Deliberately does not call full onCreate/onDestroy or initialize unrelated chat/model fields.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
@GraphicsMode(GraphicsMode.Mode.LEGACY)
@LooperMode(LooperMode.Mode.PAUSED)
class MemoryRecallActivityUiTest {
    @Test fun constructionDefersCompilerUntilAttachedAccessAndRecallReachesTheMainLooper() = activity { activity ->
        val delegate = get(activity, "contextCompiler\$delegate") as Lazy<*>
        assertFalse("Construction must not access filesDir through the compiler", delegate.isInitialized())

        presentation(activity) { effects, seen, loopers ->
            subscribeMemoryEvents(activity)
            assertFalse("Subscribing must not initialize or run the compiler", delegate.isInitialized())
            drainMainLooper()
            assertTrue("A subscription is not a recall", seen.isEmpty())
            assertCalm(effects)

            val compiler = compiler(activity)
            assertTrue(delegate.isInitialized())
            assertSame(compiler, compiler(activity))
            val result = compiler.compile(memoryInput())
            awaitDispatch(activity)
            assertEquals(listOf("activity-memory"), result.includedMemoryIds)
            assertTrue("The background hub may only post to the Activity's main looper", seen.isEmpty())
            assertCalm(effects)

            drainMainLooper()
            val event = seen.single() as CockpitAnimationEvent.RecallHits
            assertEquals(MEMORY_TURN, event.turnId)
            assertEquals(1, event.count)
            assertTrue(event.queryHash.matches(Regex("[a-f0-9]{64}")))
            assertFalse(event.toString().contains("Maren"))
            assertFalse(event.toString().contains("Kieferntal"))
            assertEquals(listOf(Looper.getMainLooper()), loopers.toList())
            assertEquals(1, effects.recallCount)
            val score = checkNotNull(event.topScore)
            assertTrue(score > 0f && score <= 1f)
            assertEquals(score, effects.recallBrightness(SystemClock.uptimeMillis()), 0f)
            assertEquals("Recall must not pretend to confirm a stored fact", 0f,
                effects.orbPulseLevel(SystemClock.uptimeMillis()), 0f)
            assertEquals(0, effects.flightDocuments)
        }
    }

    @Test fun actualSubscriptionTeardownDropsQueuedRecallAndResubscriptionRemainsLive() = activity { activity ->
        presentation(activity) { effects, seen, loopers ->
            val compiler = compiler(activity)
            subscribeMemoryEvents(activity)
            compiler.compile(memoryInput())
            awaitDispatch(activity)
            assertTrue(seen.isEmpty())

            // Invoke the exact closures installed by the real Activity method. Keep the bus
            // renderer attached so removing the test observer cannot hide a stale flush.
            closeCockpitSubscriptions(activity)
            drainMainLooper()
            assertTrue("Queued work from the closed mapper must not reach the live renderer", seen.isEmpty())
            assertCalm(effects)

            compiler.compile(memoryInput())
            awaitDispatch(activity)
            drainMainLooper()
            assertTrue("Compiling while unsubscribed must not revive the old mapper", seen.isEmpty())
            assertCalm(effects)

            subscribeMemoryEvents(activity)
            val result = compiler.compile(memoryInput())
            awaitDispatch(activity)
            assertEquals(listOf("activity-memory"), result.includedMemoryIds)
            assertTrue(seen.isEmpty())
            drainMainLooper()
            assertEquals(1, seen.size)
            assertEquals(1, (seen.single() as CockpitAnimationEvent.RecallHits).count)
            assertEquals(listOf(Looper.getMainLooper()), loopers.toList())
            assertEquals(1, effects.recallCount)
            assertTrue(effects.recallBrightness(SystemClock.uptimeMillis()) > 0f)
        }
    }

    private fun memoryInput() = ContextIntegrityFixture.input("Maren synthetic-private-query", listOf(
        ContextIntegrityFixture.memory("activity-memory", "Maren lives in the synthetic Kieferntal."),
    ))

    private fun activity(block: (AgentActivity) -> Unit) {
        // get() constructs and attaches the Activity, without the unrelated full startup path.
        val activity = Robolectric.buildActivity(AgentActivity::class.java).get()
        try { block(activity) } finally {
            val worker = ContinuumMemoryEventHub.forApp(activity.filesDir).dispatchThreadForTest()
            closeCockpitSubscriptions(activity)
            val executors = listOf("localAgentExecutor", "backlogExecutor", "learningIoExecutor")
                .map { get(activity, it) as ExecutorService }
            executors.forEach { it.shutdownNow() }
            drainMainLooper()
            worker?.join(5_000L)
            assertFalse("The unobserved memory dispatcher must terminate", worker?.isAlive == true)
            executors.forEach { assertTrue("Activity test executor must terminate", it.awaitTermination(5, TimeUnit.SECONDS)) }
        }
    }

    private fun presentation(
        activity: AgentActivity,
        block: (JarvisSequenceEffects, List<CockpitAnimationEvent>, List<Looper?>) -> Unit,
    ) {
        val effects = JarvisSequenceEffects()
        val seen = CopyOnWriteArrayList<CockpitAnimationEvent>()
        val loopers = CopyOnWriteArrayList<Looper?>()
        val bus = get(activity, "cockpitBus") as CockpitAnimationBus
        bus.subscribe(BrainOrbAnimationRenderer(effects, SystemClock::uptimeMillis)).use {
            bus.subscribe { event -> seen += event; loopers += Looper.myLooper() }.use {
                block(effects, seen, loopers)
            }
        }
    }

    private fun assertCalm(effects: JarvisSequenceEffects) {
        assertEquals(0, effects.recallCount)
        assertEquals(0f, effects.recallBrightness(SystemClock.uptimeMillis()), 0f)
        assertEquals(0f, effects.orbPulseLevel(SystemClock.uptimeMillis()), 0f)
        assertEquals(0, effects.flightDocuments)
        assertFalse(effects.hypothesisVisible(SystemClock.uptimeMillis()))
    }

    private fun awaitDispatch(activity: AgentActivity) =
        ContinuumMemoryEventHub.forApp(activity.filesDir).awaitIdle(5_000L)

    private fun drainMainLooper() =
        shadowOf(Looper.getMainLooper()).idleFor(101L, TimeUnit.MILLISECONDS)

    private fun compiler(activity: AgentActivity): AiContextCompiler = AgentActivity::class.java
        .getDeclaredMethod("getContextCompiler").apply { isAccessible = true }.invoke(activity) as AiContextCompiler

    private fun subscribeMemoryEvents(activity: AgentActivity) {
        AgentActivity::class.java.getDeclaredMethod("subscribeMemoryEvents")
            .apply { isAccessible = true }.invoke(activity)
    }

    @Suppress("UNCHECKED_CAST")
    private fun closeCockpitSubscriptions(activity: AgentActivity) {
        val field = AgentActivity::class.java.getDeclaredField("cockpitSubscriptions").apply { isAccessible = true }
        val subscriptions = field.get(activity) as List<AutoCloseable>
        subscriptions.forEach { it.close() }
        field.set(activity, emptyList<AutoCloseable>())
    }

    private fun get(activity: AgentActivity, name: String): Any? = AgentActivity::class.java
        .getDeclaredField(name).apply { isAccessible = true }.get(activity)
}
