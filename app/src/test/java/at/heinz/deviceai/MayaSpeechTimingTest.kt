package at.heinz.deviceai

import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class MayaSpeechTimingTest {
    private val pcm = ByteArray(200)
    private val ranges = listOf(
        MayaSpeechAlignment.Range(0, 2, 0, 20),
        MayaSpeechAlignment.Range(3, 5, 30, 60)
    )
    private class Request(val audio: MayaSpeech.Synthesis) : MayaSpeech.Request {
        override fun synthesize(text: String, key: String, allowed: () -> Boolean) = audio.pcm
        override fun synthesizeWithTiming(text: String, key: String, allowed: () -> Boolean) = audio
        override fun close() {}
    }
    private open class Player(private val frames: List<Int>) : MayaSpeech.Player {
        override fun play(pcm: ByteArray, allowed: () -> Boolean, started: () -> Unit) { error("Timing path required") }
        override fun play(pcm: ByteArray, allowed: () -> Boolean, started: () -> Unit, progress: (Int) -> Unit) {
            started()
            frames.forEach { if (allowed()) progress(it) }
        }
        override fun close() {}
    }
    private class Listener : MayaSpeech.Listener {
        val events = CopyOnWriteArrayList<String>()
        val done = CountDownLatch(1)
        override fun started(id: String, pcm: ByteArray) { events += "start:$id" }
        override fun range(id: String, start: Int, end: Int, frame: Int) { events += "$id:$start-$end@$frame" }
        override fun finished(id: String) { events += "done:$id"; done.countDown() }
        override fun failed(id: String) { events += "fail:$id"; done.countDown() }
    }

    @Test fun callbacksUseObservedPlaybackFramesAndOriginalOffsets() {
        val listener = Listener()
        MayaSpeech({ Request(MayaSpeech.Synthesis(pcm, ranges)) },
            { Player(listOf(0, 10, 35, 45, 100)) }, { it() }, listener).use {
            assertTrue(it.speak("turn", "Hi du", "synthetic_test_key") { true })
            assertTrue(listener.done.await(3, TimeUnit.SECONDS))
            assertEquals(listOf("start:turn", "turn:0-2@0", "turn:3-5@35", "done:turn"), listener.events)
        }
    }

    @Test fun delayedUiDispatchDropsAlreadyExpiredWordCues() {
        val callbacks = CopyOnWriteArrayList<() -> Unit>()
        val queued = CountDownLatch(4)
        val listener = Listener()
        MayaSpeech({ Request(MayaSpeech.Synthesis(pcm, ranges)) },
            { Player(listOf(0, 35, 100)) }, { callbacks += it; queued.countDown() }, listener).use {
            it.speak("turn", "Hi du", "synthetic_test_key") { true }
            assertTrue(queued.await(3, TimeUnit.SECONDS))
            callbacks.forEach { callback -> callback() }
            assertEquals(listOf("start:turn", "done:turn"), listener.events)
        }
    }

    @Test fun stoppedTurnDropsQueuedAlignmentAndCompletion() {
        val callbacks = CopyOnWriteArrayList<() -> Unit>()
        val queued = CountDownLatch(4)
        val listener = Listener()
        MayaSpeech({ Request(MayaSpeech.Synthesis(pcm, ranges)) },
            { Player(listOf(0, 35, 100)) }, { callbacks += it; queued.countDown() }, listener).use {
            it.speak("turn", "Hi du", "synthetic_test_key") { true }
            assertTrue(queued.await(3, TimeUnit.SECONDS))
            it.stop()
            callbacks.forEach { callback -> callback() }
            assertTrue(listener.events.isEmpty())
        }
    }
}
