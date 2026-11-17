package at.heinz.deviceai.cockpit

import android.graphics.Rect
import android.view.ViewGroup
import at.heinz.deviceai.avatar.AvatarEvent
import at.heinz.deviceai.avatar.AvatarExpression
import at.heinz.deviceai.avatar.AvatarSink
import at.heinz.deviceai.avatar.AvatarSinkRegistry
import at.heinz.deviceai.avatar.AvatarState
import at.heinz.deviceai.avatar.GazeTarget
import at.heinz.deviceai.avatar.NoOpAvatarSink
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.ArrayDeque

/** Real slot dispatch with a manual main-thread queue; no Android runtime or timing sleeps. */
class JarvisAvatarDispatchRegressionTest {
    private val queue = ArrayDeque<Runnable>()
    private lateinit var previousSink: AvatarSink
    private var previousRenderer: JarvisAvatarRenderer? = null
    private val speech = AvatarEvent.SpeechStarted("dispatch-test", "Synthetic test speech", 100L)
    private val state = AvatarEvent.StateChanged(
        AvatarState.THINKING, AvatarExpression.FOCUSED, GazeTarget.THINKING_AWAY, 101L
    )

    @Before fun setUp() {
        previousSink = AvatarSinkRegistry.sink
        previousRenderer = JarvisAvatarSlot.renderer
        AvatarSinkRegistry.sink = NoOpAvatarSink
        JarvisAvatarSlot.register(null)
    }

    @After fun tearDown() {
        queue.clear()
        AvatarSinkRegistry.sink = previousSink
        JarvisAvatarSlot.register(previousRenderer)
    }

    private fun sink(): AvatarSink = JarvisAvatarSlot.fanOut { queue.addLast(it) }

    private fun drain() {
        while (queue.isNotEmpty()) queue.removeFirst().run()
    }

    @Test fun registryFailureDoesNotPreventRendererDelivery() {
        val renderer = RecordingRenderer()
        JarvisAvatarSlot.register(renderer)
        AvatarSinkRegistry.sink = AvatarSink { throw IllegalStateException("Synthetic registry failure") }

        sink().onAvatarEvent(speech)
        assertTrue("Renderer delivery must remain queued on the main thread", renderer.events.isEmpty())
        assertEquals(1, queue.size)
        drain()

        assertEquals(listOf(speech), renderer.events)
    }

    @Test fun replacementDropsOldQueuedEventAndReceivesOnlyNewEvents() {
        val old = RecordingRenderer()
        val current = RecordingRenderer()
        val dispatch = sink()
        JarvisAvatarSlot.register(old)
        dispatch.onAvatarEvent(speech)
        JarvisAvatarSlot.register(current)
        dispatch.onAvatarEvent(state)

        drain()

        assertTrue("An old renderer must not receive an event after replacement", old.events.isEmpty())
        assertEquals(listOf(state), current.events)
        assertEquals(listOf(AvatarState.THINKING), current.states)
    }

    @Test fun unregisterDropsQueuedEvent() {
        val renderer = RecordingRenderer()
        JarvisAvatarSlot.register(renderer)
        sink().onAvatarEvent(speech)
        JarvisAvatarSlot.register(null)

        drain()

        assertTrue(renderer.events.isEmpty())
    }

    @Test fun detachAndReregisterSameInstanceDoesNotReviveOldEvent() {
        val renderer = RecordingRenderer()
        val dispatch = sink()
        JarvisAvatarSlot.register(renderer)
        dispatch.onAvatarEvent(speech)
        JarvisAvatarSlot.register(null)
        JarvisAvatarSlot.register(renderer)
        dispatch.onAvatarEvent(state)

        drain()

        assertEquals("Only the new registration may receive events", listOf(state), renderer.events)
    }

    @Test fun registeringSameInstanceAgainStartsANewRegistration() {
        val renderer = RecordingRenderer()
        val dispatch = sink()
        JarvisAvatarSlot.register(renderer)
        dispatch.onAvatarEvent(speech)
        JarvisAvatarSlot.register(renderer)
        dispatch.onAvatarEvent(state)

        drain()

        assertEquals(listOf(state), renderer.events)
    }

    @Test fun coarseStateFailureDoesNotLoseFullExpressionAndGazeEvent() {
        val renderer = RecordingRenderer {
            throw IllegalStateException("Synthetic coarse state failure")
        }
        JarvisAvatarSlot.register(renderer)
        sink().onAvatarEvent(state)

        drain()

        assertEquals(listOf(AvatarState.THINKING), renderer.states)
        assertEquals(listOf(state), renderer.events)
    }

    @Test fun reregistrationDuringCoarseCallbackInvalidatesRemainingFullEvent() {
        lateinit var renderer: RecordingRenderer
        renderer = RecordingRenderer { JarvisAvatarSlot.register(renderer) }
        JarvisAvatarSlot.register(renderer)
        sink().onAvatarEvent(state)

        drain()

        assertEquals(listOf(AvatarState.THINKING), renderer.states)
        assertTrue("A callback must not continue into a new registration", renderer.events.isEmpty())
    }

    @Test fun normalStateDeliveryPreservesRegistryAndRendererContract() {
        val registryEvents = mutableListOf<AvatarEvent>()
        val renderer = RecordingRenderer()
        AvatarSinkRegistry.sink = AvatarSink { registryEvents += it }
        JarvisAvatarSlot.register(renderer)

        sink().onAvatarEvent(state)

        assertEquals(listOf(state), registryEvents)
        assertTrue(renderer.states.isEmpty())
        assertTrue(renderer.events.isEmpty())
        drain()
        assertEquals(listOf(AvatarState.THINKING), renderer.states)
        assertEquals(listOf(state), renderer.events)
    }

    @Test fun absentRendererStillReceivesRegistryEventWithoutQueueing() {
        val registryEvents = mutableListOf<AvatarEvent>()
        AvatarSinkRegistry.sink = AvatarSink { registryEvents += it }

        sink().onAvatarEvent(speech)

        assertEquals(listOf(speech), registryEvents)
        assertTrue(queue.isEmpty())
    }

    private class RecordingRenderer(private val onState: () -> Unit = {}) : JarvisAvatarRenderer {
        val states = mutableListOf<AvatarState>()
        val events = mutableListOf<AvatarEvent>()
        override fun attach(host: ViewGroup, slot: Rect) = Unit
        override fun detach() = Unit
        override fun onAnimationEvent(event: CockpitAnimationEvent) = Unit
        override fun onAvatarState(state: AvatarState) {
            states += state
            onState()
        }
        override fun onAvatarEvent(event: AvatarEvent) { events += event }
    }
}
