package at.heinz.deviceai

import java.io.Closeable
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit

/** Provider adapter; the activity owns the sole speech queue. */
internal class MayaSpeech(
    private val request: () -> Request,
    private val player: () -> Player,
    private val dispatch: (() -> Unit) -> Unit,
    private val listener: Listener
) : Closeable {
    data class Synthesis(val pcm: ByteArray, val ranges: List<MayaSpeechAlignment.Range> = emptyList())
    interface Request : Closeable {
        fun synthesize(text: String, key: String, allowed: () -> Boolean): ByteArray
        fun synthesizeWithTiming(text: String, key: String, allowed: () -> Boolean): Synthesis =
            Synthesis(synthesize(text, key, allowed))
    }
    interface Player : Closeable {
        fun play(pcm: ByteArray, allowed: () -> Boolean, started: () -> Unit)
        fun play(pcm: ByteArray, allowed: () -> Boolean, started: () -> Unit, progress: (Int) -> Unit) =
            play(pcm, allowed, started)
    }
    interface Listener {
        fun started(id: String, pcm: ByteArray)
        fun range(id: String, start: Int, end: Int, frame: Int) {}
        fun finished(id: String)
        fun failed(id: String)
    }
    companion object {
        const val VOICE_ID = "ii0s2u4R3UFnxKL6DOrz"
        const val VOICE_NAME = "Maya – Warm Californian"
        const val SAMPLE_RATE = 24000
        const val MAX_PCM_BYTES = SAMPLE_RATE * 2 * 90
        fun validKey(key: String) = key.length in 16..512 && key.all { it.isLetterOrDigit() && it.code < 128 || it == '_' || it == '-' }
    }
    private class Job(val id: String, val allowed: () -> Boolean) {
        @Volatile var cancelled = false
        @Volatile var playbackFrame = 0
        private var resource: Closeable? = null
        @Synchronized fun attach(value: Closeable): Boolean {
            if (cancelled) { value.close(); return false }
            resource = value
            return true
        }
        @Synchronized fun cancel() {
            cancelled = true
            runCatching { resource?.close() }
            resource = null
        }
        fun live() = !cancelled && allowed()
    }
    private val worker = ThreadPoolExecutor(1, 1, 0, TimeUnit.MILLISECONDS, LinkedBlockingQueue<Runnable>())
    @Volatile private var current: Job? = null
    @Volatile private var closed = false

    @Synchronized fun speak(id: String, text: String, key: String, allowed: () -> Boolean): Boolean {
        if (closed || id.isBlank() || text.isBlank() || text.length > 800 || !validKey(key) || !allowed()) return false
        stop()
        val job = Job(id, allowed)
        current = job
        worker.execute {
            try {
                check(job.live())
                val synthesis = request().use { source ->
                    check(job.attach(source) && job.live())
                    source.synthesizeWithTiming(text, key, job::live)
                }
                val pcm = synthesis.pcm
                check(pcm.isNotEmpty() && pcm.size <= MAX_PCM_BYTES && pcm.size % 2 == 0)
                check(job.live())
                val cursor = MayaSpeechAlignment.Cursor(
                    MayaSpeechAlignment.validated(text, pcm.size / 2, synthesis.ranges))
                player().use { output ->
                    check(job.attach(output) && job.live())
                    output.play(pcm, job::live, { emit(job) { listener.started(id, pcm) } }) { frame ->
                        if (frame >= job.playbackFrame) job.playbackFrame = frame
                        cursor.advance(frame)?.let { range ->
                            emit(job) {
                                val currentFrame = job.playbackFrame
                                if (currentFrame in range.startFrame until range.endFrame)
                                    listener.range(id, range.start, range.end, currentFrame)
                            }
                        }
                    }
                }
                finish(job, false)
            } catch (_: Exception) {
                finish(job, true)
            }
        }
        return true
    }

    private fun finish(job: Job, failed: Boolean) {
        dispatch {
            if (current === job && !job.cancelled) {
                if (failed || !job.allowed()) listener.failed(job.id) else listener.finished(job.id)
            }
        }
    }

    private fun emit(job: Job, action: () -> Unit) {
        dispatch { if (current === job && job.live()) action() }
    }

    @Synchronized fun stop() {
        current?.cancel()
        current = null
        worker.queue.clear()
    }

    @Synchronized override fun close() {
        closed = true
        stop()
        worker.shutdownNow()
    }
}
