package at.heinz.deviceai.avatar3d

import at.heinz.deviceai.ui.ComputerPanelState
import at.heinz.deviceai.ui.ContinuumCockpitPresentation.Row
import at.heinz.deviceai.ui.ContinuumDashboardState
import at.heinz.deviceai.ui.ContinuumPainter
import at.heinz.deviceai.ui.ContinuumIcons.Icon
import at.heinz.deviceai.ui.JarvisCockpitPanels
import at.heinz.deviceai.ui.JarvisShellLayout.Box
import kotlin.math.ceil

/** Context pages, never capture authority. */
internal class CockpitPanelContent private constructor(
    val key: Key,
    val observationRevision: Long,
    val draw: (ContinuumPainter, Box, Boolean) -> Unit,
    val requestFrame: () -> Unit
) {
    data class Key(
        val taskId: String, val sessionId: Long,
        val planning: List<Row>, val results: List<Row>,
        val density: Float, val fontScale: Float
    )

    companion object {
        fun from(state: ContinuumDashboardState, density: Float, fontScale: Float,
                 requestFrame: () -> Unit = {}): CockpitPanelContent? {
            val computer: ComputerPanelState = state.computer ?: return null
            require(density.isFinite() && density > 0f)
            val font = if (fontScale.isFinite()) fontScale.coerceIn(.85f, 2f) else 1f
            val agents = state.cockpit.workers?.takeIf { it.any { worker -> worker.running } }
                ?.let { JarvisCockpitPanels.agentRows(state).take(2) }.orEmpty()
            val key = Key(computer.taskId, computer.sessionId,
                (computer.planningRows() + agents).toList(), computer.resultRows().toList(), density, font)
            return CockpitPanelContent(key, computer.snapshot.observationRevision, { painter, box, result ->
                JarvisCockpitPanels.drawComputerRows(painter, box, if (result) "Ergebnis" else "Auftrag",
                    Icon.DOCUMENT, if (result) key.results else key.planning, true)
            }, requestFrame)
        }
    }
}

/** Bounded upload ownership and presentation. */
internal class CockpitPanelUploadState {
    data class Request(val key: CockpitPanelContent.Key, val frameWidth: Float, val frameHeight: Float,
                       val width: Int, val height: Int, val logicalWidth: Float, val logicalHeight: Float)
    class Ticket internal constructor(val request: Request)

    var requested: Request? = null
        private set
    var inFlight: Ticket? = null
        private set
    private var uploaded: Request? = null
    private var submitted: Request? = null
    private var closed = false
    private var observationRevision = -1L
    private var submittedRevision = -1L

    val drawable: Boolean get() = requested != null && uploaded == requested && inFlight == null && !closed

    fun request(content: CockpitPanelContent?, frame: CockpitStageFrame) {
        val key = content?.key
        val allowed = !closed && key != null && frame.valid() &&
            frame.monitorWidth / frame.width <= .70f &&
            frame.width * .20f / key.density >= 100f &&
            frame.height * .30f / key.density >= 64f
        if (!allowed) {
            requested = null
            submitted = null
            return
        }
        if (observationRevision != content?.observationRevision) {
            observationRevision = requireNotNull(content).observationRevision
            submitted = null
        }
        val current = requested
        if (current != null && current.key == key && current.frameWidth == frame.width && current.frameHeight == frame.height) return
        val actual = requireNotNull(key)
        val bufferScale = minOf(1f, 1280f / maxOf(frame.width, frame.height))
        val scale = minOf(2f * bufferScale, 768f / maxOf(frame.width * .20f, frame.height * .30f))
        requested = Request(actual, frame.width, frame.height,
            maxOf(1, ceil(frame.width * .20f * scale).toInt()),
            maxOf(1, ceil(frame.height * .30f * scale).toInt()),
            frame.width * .20f / actual.density, frame.height * .30f / actual.density)
        submitted = null
    }

    fun begin(): Ticket? {
        val next = requested ?: return null
        if (closed || inFlight != null || uploaded == next) return null
        return Ticket(next).also { inFlight = it }
    }

    fun complete(ticket: Ticket) {
        if (closed || inFlight !== ticket) return
        inFlight = null
        uploaded = ticket.request
    }

    fun frameSubmitted() {
        submitted = if (drawable) requested else null
        submittedRevision = observationRevision
    }
    fun frameFailed() { submitted = null }

    fun presented(content: CockpitPanelContent?, frame: CockpitStageFrame?): Boolean {
        val ready = submitted ?: return false
        return !closed && drawable && content != null && content.key == ready.key &&
            content.observationRevision == submittedRevision && frame != null && frame.valid() &&
            frame.monitorWidth / frame.width <= .70f && frame.width == ready.frameWidth && frame.height == ready.frameHeight
    }

    fun close() {
        closed = true
        requested = null
        submitted = null
        uploaded = null
        inFlight = null
    }
}
