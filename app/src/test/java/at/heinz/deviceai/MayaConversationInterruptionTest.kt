package at.heinz.deviceai

import at.heinz.deviceai.cognitive.ContinuousConversationMode
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class MayaConversationInterruptionTest {
    @Test fun ownerStopPausesHandsFreeWithoutProviderCallback() {
        val mode = ContinuousConversationMode()
        assertTrue(mode.start("owner"))
        mode.recognized(checkNotNull(mode.listeningTurn("owner")), "owner", "Hallo")
        val playing = CountDownLatch(1)
        val stopped = CountDownLatch(1)
        val callbacks = AtomicInteger()
        MayaSpeech({ object : MayaSpeech.Request {
            override fun synthesize(text: String, key: String, allowed: () -> Boolean) = byteArrayOf(0, 0)
            override fun close() {}
        } }, { object : MayaSpeech.Player {
            override fun play(pcm: ByteArray, allowed: () -> Boolean, started: () -> Unit) {
                started()
                playing.countDown()
                assertTrue(stopped.await(3, TimeUnit.SECONDS))
            }
            override fun close() { stopped.countDown() }
        } }, { it() }, object : MayaSpeech.Listener {
            override fun started(id: String, pcm: ByteArray) {}
            override fun finished(id: String) { callbacks.incrementAndGet() }
            override fun failed(id: String) { callbacks.incrementAndGet() }
        }).use { speech ->
            assertTrue(speech.speak("reply", "Antwort", "synthetic_test_key_only") { true })
            mode.assistantSpeechQueued("reply")
            assertTrue(playing.await(3, TimeUnit.SECONDS))
            assertEquals(ContinuousConversationMode.Decision.PAUSE,
                mode.stopAssistantSpeech(speech::stop))
            assertEquals(ContinuousConversationMode.State.PAUSED, mode.state)
            assertEquals(0, callbacks.get())
            assertNull(mode.listeningTurn("owner"))
            assertEquals(ContinuousConversationMode.Decision.IGNORE,
                mode.assistantSpeechFinished("reply", false))
            assertEquals(ContinuousConversationMode.Decision.LISTEN, mode.ownerTap("owner"))
        }
    }

    @Test fun stopWithoutBoundSpeechDoesNotResumeMicrophone() {
        val mode = ContinuousConversationMode()
        var stops = 0
        assertEquals(ContinuousConversationMode.Decision.IGNORE, mode.stopAssistantSpeech { stops++ })
        assertFalse(mode.active)
        mode.start("owner")
        mode.recognized(checkNotNull(mode.listeningTurn("owner")), "owner", "Frage")
        assertEquals(ContinuousConversationMode.Decision.IGNORE, mode.stopAssistantSpeech { stops++ })
        assertEquals(ContinuousConversationMode.State.THINKING, mode.state)
        assertNull(mode.listeningTurn("owner"))
        assertEquals(2, stops)
    }
}
