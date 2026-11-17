package at.heinz.deviceai

import at.heinz.deviceai.avatar.AvatarDirector
import at.heinz.deviceai.avatar.AvatarEvent
import at.heinz.deviceai.avatar.AvatarSink
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

class MayaAvatarTimingIntegrationTest {
    private val text = "Mama Opa"
    private val pcm = ByteArray(24_000) { index ->
        if (index % 2 == 0) 0 else when (index / 2) {
            in 2_400 until 4_800 -> 64
            in 7_200 until 12_000 -> 32
            else -> 0
        }.toByte()
    }
    private val ranges = listOf(
        MayaSpeechAlignment.Range(0, 4, 2_400, 4_800),
        MayaSpeechAlignment.Range(5, 8, 7_200, 12_000)
    )

    private fun source() = object : MayaSpeech.Request {
        override fun synthesize(text: String, key: String, allowed: () -> Boolean) = pcm
        override fun synthesizeWithTiming(text: String, key: String, allowed: () -> Boolean) =
            MayaSpeech.Synthesis(pcm, ranges)
        override fun close() {}
    }

    private class Bridge : MayaSpeech.Listener {
        val now = AtomicLong(1_000)
        val events = CopyOnWriteArrayList<AvatarEvent>()
        val director = AvatarDirector(AvatarSink(events::add), now::get)
        val signal = TtsHologramSignal()
        val observed = CopyOnWriteArrayList<Pair<Int, Float>>()
        val completion = CountDownLatch(1)
        @Volatile var failed = false
        @Volatile var current = "reply"
        init { signal.activate(current) }

        override fun started(id: String, pcm: ByteArray) {
            if (id != current) return
            signal.begin(id, MayaSpeech.SAMPLE_RATE, 2, 1)
            var offset = 0
            while (offset < pcm.size) {
                val end = (offset + 1_920).coerceAtMost(pcm.size)
                signal.append(id, pcm.copyOfRange(offset, end))
                offset = end
            }
            if (director.speechStarted(id, "Mama Opa")) signal.anchor(id, 0, now.get())
        }

        override fun range(id: String, start: Int, end: Int, frame: Int) {
            if (id != current || !director.wordRange(id, start, end, frame)) return
            signal.anchor(id, frame, now.get())
            observed += frame to checkNotNull(signal.measuredLevel(id, now.get()))
        }

        override fun finished(id: String) { completion.countDown() }
        override fun failed(id: String) { failed = true; completion.countDown() }
    }

    @Test fun playedFramesDriveCurrentWordAndMeasuredPcm() {
        val bridge = Bridge()
        val player = object : MayaSpeech.Player {
            override fun play(pcm: ByteArray, allowed: () -> Boolean, started: () -> Unit) = error("timing required")
            override fun play(pcm: ByteArray, allowed: () -> Boolean, started: () -> Unit, progress: (Int) -> Unit) {
                started()
                progress(0)
                assertTrue(bridge.events.none { it is AvatarEvent.VisemeCue })
                bridge.now.set(1_130)
                progress(3_120)
                progress(3_180)
                progress(2_400)
                bridge.now.set(1_330)
                progress(7_920)
            }
            override fun close() {}
        }
        MayaSpeech(::source, { player }, { it() }, bridge).use { speech ->
            assertTrue(speech.speak("reply", text, "synthetic_test_key_only") { true })
            assertTrue(bridge.completion.await(3, TimeUnit.SECONDS))
            assertFalse(bridge.failed)
            assertEquals(listOf(3_120, 7_920), bridge.observed.map { it.first })
            assertEquals(.5f, bridge.observed[0].second, .001f)
            assertEquals(.25f, bridge.observed[1].second, .001f)
            val cues = bridge.events.filterIsInstance<AvatarEvent.VisemeCue>()
            assertTrue(cues.any { it.atMillis == 1_130L })
            assertTrue(cues.any { it.atMillis == 1_330L })
            assertTrue(cues.all { it.utteranceId == "reply" })
        }
    }

    @Test fun delayedUiDropsExpiredWordsBeforeTheyReachTheAvatar() {
        val bridge = Bridge()
        val dispatched = LinkedBlockingQueue<() -> Unit>()
        val queued = CountDownLatch(4)
        val player = object : MayaSpeech.Player {
            override fun play(pcm: ByteArray, allowed: () -> Boolean, started: () -> Unit) = error("timing required")
            override fun play(pcm: ByteArray, allowed: () -> Boolean, started: () -> Unit, progress: (Int) -> Unit) {
                started()
                progress(3_120)
                progress(7_920)
            }
            override fun close() {}
        }
        MayaSpeech(::source, { player }, { dispatched.add(it); queued.countDown() }, bridge).use { speech ->
            speech.speak("reply", text, "synthetic_test_key_only") { true }
            assertTrue(queued.await(3, TimeUnit.SECONDS))
            repeat(4) { checkNotNull(dispatched.poll(3, TimeUnit.SECONDS))() }
            assertFalse(bridge.failed)
            assertEquals(listOf(7_920), bridge.observed.map { it.first })
            assertEquals(.25f, bridge.observed.single().second, .001f)
        }
    }

    @Test fun stopAndReplacementDiscardQueuedRangesAndOldAnchors() {
        val bridge = Bridge()
        val dispatched = LinkedBlockingQueue<() -> Unit>()
        val released = CountDownLatch(1)
        val player = object : MayaSpeech.Player {
            override fun play(pcm: ByteArray, allowed: () -> Boolean, started: () -> Unit) = error("timing required")
            override fun play(pcm: ByteArray, allowed: () -> Boolean, started: () -> Unit, progress: (Int) -> Unit) {
                started()
                progress(3_120)
                assertTrue(released.await(3, TimeUnit.SECONDS))
            }
            override fun close() { released.countDown() }
        }
        MayaSpeech(::source, { player }, { dispatched.add(it) }, bridge).use { speech ->
            speech.speak("reply", text, "synthetic_test_key_only") { true }
            checkNotNull(dispatched.poll(3, TimeUnit.SECONDS))()
            val oldRange = checkNotNull(dispatched.poll(3, TimeUnit.SECONDS))
            speech.stop()
            bridge.director.speechEnded("reply", true)
            bridge.signal.stop()
            bridge.current = "replacement"
            bridge.signal.activate(bridge.current)
            bridge.started(bridge.current, pcm)
            val before = bridge.events.toList()
            oldRange()
            bridge.range("reply", 0, 4, 3_120)
            assertEquals(before, bridge.events)
            assertTrue(bridge.observed.isEmpty())
            assertEquals(0f, bridge.signal.measuredLevel(bridge.current, bridge.now.get())!!, 0f)
            assertNull(bridge.signal.measuredLevel("reply", bridge.now.get()))
        }
    }
}
