package at.heinz.deviceai.avatar3d

import android.graphics.ColorSpace
import android.hardware.HardwareBuffer
import android.os.Handler
import android.os.Looper
import android.os.Parcel
import at.heinz.deviceai.AndroidLiveFrame
import at.heinz.deviceai.cognitive.*
import com.google.android.filament.*
import java.util.concurrent.atomic.AtomicBoolean

/** Immutable buffers; bounded native references. */
internal class CockpitLiveTexture(
    private val engine: Engine,
    private val material: MaterialInstance
) : AutoCloseable {
    private var stream: Stream? = null
    private var texture: Texture? = null
    private var feed: LiveMonitorFeed<AndroidLiveFrame>? = null
    private val handler = Handler(Looper.getMainLooper())
    private val pending = mutableSetOf<OwnedBuffer>()
    private var binding: LiveFrameBinding? = null
    private var sequence = -1L
    private var sourceViewport: LiveTargetBounds? = null
    private var imported: LiveMonitorSubmission.ImportedFrame? = null
    private var sourceWidth = 1
    private var sourceHeight = 1
    private var click: CockpitWorkEvent? = null
    private val workSequence = CockpitPresentationSequence()
    private var available = false
    val pixelsVisible get() = available
    private var closed = false
    private var failure: String? = null
    var label = "Keine Bildquelle verbunden"
        private set

    init { createStream() }

    private class OwnedBuffer(val buffer: HardwareBuffer) : AutoCloseable {
        private val closed = AtomicBoolean(false)
        override fun close() { if (closed.compareAndSet(false, true)) buffer.close() }
    }

    fun bind(value: LiveMonitorFeed<AndroidLiveFrame>?) {
        if (feed === value) return
        clearPixels()
        releaseStream()
        feed = value
        feed?.monitorSubmission?.invalidate()
        workSequence.reset()
        failure = null
        if (!closed) createStream()
    }

    private fun createStream() {
        val source = Stream.Builder().width(1).height(1).build(engine)
        stream = source
        texture = Texture.Builder().sampler(Texture.Sampler.SAMPLER_EXTERNAL)
            .format(Texture.InternalFormat.RGB8).width(1).height(1).levels(1).build(engine).also {
                it.setExternalStream(engine, source)
                material.setParameter("videoTexture", it, TextureSampler(
                    TextureSampler.MinFilter.LINEAR, TextureSampler.MagFilter.LINEAR,
                    TextureSampler.WrapMode.CLAMP_TO_EDGE))
            }
        material.setParameter("frameAvailable", false)
        material.setParameter("pointerAvailable", false)
        material.setParameter("sourceUv", 0f, 0f, 1f, 1f)
        material.setParameter("contentScale", 1f, 1f)
        material.setParameter("pointerUv", .5f, .5f)
        material.setParameter("pointerPhase", 0f)
    }

    /** Called once, before beginFrame. */
    fun update(frame: CockpitStageFrame, nowNanos: Long) {
        if (closed) return
        val producer = feed ?: run {
            clearPixels()
            label = "Keine Bildquelle verbunden"
            return
        }
        val state = producer.status(nowNanos)
        if (state.state != LiveMonitorState.LIVE || state.binding == null) {
            val hadImage = binding != null
            clearPixels()
            if (hadImage) { releaseStream(); createStream() }
            label = state.label
            return
        }
        if (binding != null && binding != state.binding) {
            clearPixels()
            releaseStream()
            createStream()
        }
        if ((imported == null || sequence != state.sequence || binding != state.binding) &&
            pending.size < MAX_GPU_REFERENCES) {
            producer.acquire()?.use { lease ->
                val current = producer.status(System.nanoTime())
                if (!matches(current, lease.binding, lease.sequence)) return@use
                if (lease.frame.colorSpace != ColorSpace.get(ColorSpace.Named.SRGB)) {
                    clearPixels()
                    failure = "Farbraum wird nicht unterstützt"
                    return@use
                }
                val duplicate = runCatching { retain(lease.frame.hardwareBuffer) }.getOrNull()
                if (duplicate == null) {
                    clearPixels()
                    failure = "Bild kann nicht übernommen werden"
                    return@use
                }
                val owned = OwnedBuffer(duplicate)
                val sampledAt = System.nanoTime()
                val latest = producer.status(sampledAt)
                if (!matches(latest, lease.binding, lease.sequence)) { owned.close(); return@use }
                pending += owned
                try {
                    stream!!.setDimensions(lease.frame.width, lease.frame.height)
                    stream!!.setAcquiredImage(owned.buffer, handler, Runnable {
                        owned.close()
                        pending.remove(owned)
                    })
                    binding = lease.binding
                    sequence = lease.sequence
                    sourceViewport = lease.meta.sourceViewport
                    sourceWidth = lease.frame.width
                    sourceHeight = lease.frame.height
                    imported = producer.monitorSubmission.imported(latest, sampledAt)
                    failure = null
                } catch (_: RuntimeException) {
                    pending.remove(owned)
                    owned.close()
                    clearPixels()
                    failure = "3D-Bildimport fehlgeschlagen"
                }
            }
        }
        val now = System.nanoTime()
        val current = producer.status(now)
        available = producer.monitorSubmission.usable(imported, current, now)
        if (!available) {
            producer.monitorSubmission.invalidate()
            imported = null
        }
        material.setParameter("frameAvailable", available)
        label = if (available && producer.monitorSubmission.isReady(current, now)) current.label
            else failure ?: "Bild wird übernommen"
        val contentAspect = frame.monitorWidth * (CockpitStageFrame.CONTENT_RIGHT - CockpitStageFrame.CONTENT_LEFT) /
            (frame.monitorHeight * (CockpitStageFrame.CONTENT_BOTTOM - CockpitStageFrame.CONTENT_TOP))
        val sourceAspect = sourceWidth.toFloat() / sourceHeight
        val fitX = minOf(1f, sourceAspect / contentAspect)
        val fitY = minOf(1f, contentAspect / sourceAspect)
        material.setParameter("contentScale", fitX, fitY)
        updatePointer(current, System.nanoTime())
    }

    fun frameSubmitted(nowNanos: Long) {
        val producer = feed ?: return
        val candidate = imported
        val current = producer.status(nowNanos)
        if (!available || candidate == null ||
            !producer.monitorSubmission.submitted(candidate, current, nowNanos)) {
            clearPixels()
            label = if (current.state == LiveMonitorState.LIVE) failure ?: "Bild wird übernommen" else current.label
        } else label = current.label
    }

    fun frameFailed() {
        clearPixels()
        label = "3D-Bild nicht verfügbar"
    }

    fun workEvent(event: CockpitWorkEvent) {
        val current = feed?.status(System.nanoTime()) ?: return
        val bound = current.binding ?: return
        if (!workSequence.accept(event, bound, System.nanoTime())) return
        if (event.stage in END_STAGES) { click = null; return }
        val target = event.target ?: return
        val viewport = sourceViewport ?: return
        if (event.stage == CockpitWorkEvent.Stage.DISPATCHED && event.action == CockpitWorkEvent.Action.CLICK &&
            System.nanoTime() - event.occurredAtNanos in 0..150_000_000L && available &&
            matches(current, binding, sequence) && event.frameSequence == sequence &&
            target.x in viewport.left..viewport.right && target.y in viewport.top..viewport.bottom) click = event
    }

    private fun updatePointer(state: LiveMonitorStatus, nowNanos: Long) {
        val event = click
        val viewport = sourceViewport
        val elapsed = event?.let { nowNanos - it.occurredAtNanos } ?: -1L
        val valid = available && event != null && viewport != null && elapsed in 0..220_000_000L &&
            event.frameSequence == sequence && state.binding?.captureSession == event.sessionId
        material.setParameter("pointerAvailable", valid)
        if (!valid) { click = null; return }
        val target = requireNotNull(event?.target)
        val source = requireNotNull(viewport)
        material.setParameter("pointerUv", (target.x - source.left) / (source.right - source.left),
            (target.y - source.top) / (source.bottom - source.top))
        material.setParameter("pointerPhase", elapsed / 220_000_000f)
    }

    private fun clearPixels() {
        feed?.monitorSubmission?.invalidate()
        available = false
        binding = null
        sequence = -1L
        sourceViewport = null
        imported = null
        click = null
        material.setParameter("frameAvailable", false)
        material.setParameter("pointerAvailable", false)
    }

    private fun releaseStream() {
        texture?.let(engine::destroyTexture)
        texture = null
        stream?.let(engine::destroyStream)
        stream = null
        engine.flushAndWait()
        pending.toList().forEach(OwnedBuffer::close)
        pending.clear()
    }

    override fun close() {
        if (closed) return
        closed = true
        clearPixels()
        feed = null
        releaseStream()
    }

    companion object {
        const val MAX_GPU_REFERENCES = 2
        private val END_STAGES = setOf(CockpitWorkEvent.Stage.STOPPED, CockpitWorkEvent.Stage.ERROR,
            CockpitWorkEvent.Stage.PAUSED, CockpitWorkEvent.Stage.ASK_OWNER, CockpitWorkEvent.Stage.COMPLETED)
        internal fun matches(state: LiveMonitorStatus, binding: LiveFrameBinding?, sequence: Long) =
            state.state == LiveMonitorState.LIVE && binding != null && state.binding == binding &&
                sequence > 0L && state.sequence == sequence

        private fun retain(buffer: HardwareBuffer): HardwareBuffer {
            val parcel = Parcel.obtain()
            try {
                buffer.writeToParcel(parcel, 0)
                parcel.setDataPosition(0)
                return requireNotNull(HardwareBuffer.CREATOR.createFromParcel(parcel))
            } finally { parcel.recycle() }
        }
    }
}
