package at.heinz.deviceai.avatar

import at.heinz.deviceai.cognitive.CockpitWorkEvent
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

/** Uses the existing UI sound player. */
class ComputerWorkCuePlayer(
    private val post: (() -> Unit) -> Unit,
    private val playClick: () -> Unit,
    private val stopClick: () -> Unit,
    private val nanos: () -> Long = System::nanoTime
) : ComputerWorkSounds {
    private val generation = AtomicLong()
    private val closed = AtomicBoolean(false)

    override fun dispatched(event: CockpitWorkEvent) {
        if (closed.get() || event.stage != CockpitWorkEvent.Stage.DISPATCHED ||
            event.action !in setOf(CockpitWorkEvent.Action.CLICK, CockpitWorkEvent.Action.SET_TEXT)) return
        val ticket = generation.get()
        post {
            val age = nanos() - event.occurredAtNanos
            if (!closed.get() && generation.get() == ticket && age in 0..150_000_000L) playClick()
        }
    }

    override fun stop() {
        generation.incrementAndGet()
        post(stopClick)
    }

    override fun close() {
        closed.set(true)
        stop()
    }
}
