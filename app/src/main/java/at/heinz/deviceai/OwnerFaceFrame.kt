package at.heinz.deviceai

import java.nio.ByteBuffer
import java.util.concurrent.atomic.AtomicBoolean

internal interface OwnerFaceInference : AutoCloseable {
    fun observe(frame: OwnerFaceFrame): OwnerFaceObservation
}

/** Ownership transfers to the consumer. */
internal class OwnerFaceFrame(
    val width: Int,
    val height: Int,
    val rotationDegrees: Int,
    val observedAtElapsedMillis: Long,
    val sessionToken: Long,
    val yuv420: ByteArray
) : AutoCloseable {
    private val closed = AtomicBoolean(false)
    val isClosed: Boolean get() = closed.get()

    fun valid(): Boolean = !isClosed && width in 2..640 && height in 2..640 &&
        width % 2 == 0 && height % 2 == 0 && rotationDegrees in listOf(0, 90, 180, 270) &&
        observedAtElapsedMillis >= 0 && sessionToken > 0 && yuv420.size == width * height * 3 / 2

    override fun close() { if (closed.compareAndSet(false, true)) yuv420.fill(0) }
}

internal data class OwnerFaceYuvPlane(val buffer: ByteBuffer, val rowStride: Int, val pixelStride: Int)

internal object OwnerFaceYuvCopy {
    fun copy(width: Int, height: Int, planes: List<OwnerFaceYuvPlane>): ByteArray? {
        if (width !in 2..640 || height !in 2..640 || width % 2 != 0 || height % 2 != 0 || planes.size != 3) return null
        val output = ByteArray(width * height * 3 / 2)
        var destination = 0
        try {
            planes.forEachIndexed { index, plane ->
                val w = if (index == 0) width else width / 2
                val h = if (index == 0) height else height / 2
                val input = plane.buffer.asReadOnlyBuffer()
                val base = input.position().toLong()
                val row = plane.rowStride.toLong()
                val step = plane.pixelStride.toLong()
                val end = base + (h - 1) * row + (w - 1) * step
                if (step !in 1..4 || row < (w - 1) * step + 1 || end >= input.limit() || end < base) {
                    output.fill(0)
                    return null
                }
                repeat(h) { y ->
                    repeat(w) { x -> output[destination++] = input.get((base + y * row + x * step).toInt()) }
                }
            }
            return output
        } catch (_: RuntimeException) {
            output.fill(0)
            return null
        }
    }
}
