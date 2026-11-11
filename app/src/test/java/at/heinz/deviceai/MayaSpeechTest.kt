package at.heinz.deviceai

import at.heinz.deviceai.cognitive.ApprovedSpeechChunkQueue
import at.heinz.deviceai.cognitive.JarvisVoiceProfile
import at.heinz.deviceai.cognitive.NetworkTurnConstraint
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

class MayaSpeechTest {
    private val key = "synthetic_test_key_only"
    private fun await(latch: CountDownLatch) = assertTrue("worker timeout", latch.await(3, TimeUnit.SECONDS))
    private open class Source : MayaSpeech.Request {
        override fun synthesize(text: String, key: String, allowed: () -> Boolean) = byteArrayOf(0, 0, 1, 0)
        override fun close() {}
    }
    private open class Output : MayaSpeech.Player {
        override fun play(pcm: ByteArray, allowed: () -> Boolean, started: () -> Unit) { started() }
        override fun close() {}
    }
    private open class Events : MayaSpeech.Listener {
        val events = CopyOnWriteArrayList<String>()
        val done = CountDownLatch(1)
        override fun started(id: String, pcm: ByteArray) { events += "start:$id" }
        override fun finished(id: String) { events += "done:$id"; done.countDown() }
        override fun failed(id: String) { events += "failed:$id"; done.countDown() }
    }

    @Test fun disabledPermissionInvalidKeyAndOversizedTextNeverReachNetwork() {
        val requests = AtomicInteger()
        MayaSpeech({ requests.incrementAndGet(); Source() }, ::Output, { it() }, Events()).use {
            assertFalse(it.speak("a", "Hallo", key) { false })
            assertFalse(it.speak("a", "Hallo", "bad\nkey") { true })
            assertFalse(it.speak("a", "a".repeat(801), key) { true })
            assertEquals(0, requests.get())
        }
    }

    @Test fun offlineOwnerTurnNeverCreatesCloudRequestEvenWhenGloballyOnline() {
        val requests = AtomicInteger()
        MayaSpeech({ requests.incrementAndGet(); Source() }, ::Output, { it() }, Events()).use { speech ->
            for (turn in listOf("Antworte offline.", "Ohne Internet.", "Bitte ohne Cloud.")) {
                assertFalse(speech.speak("offline", "Freigegebene Antwort", key) {
                    !NetworkTurnConstraint.forbidsExternalSpeech(turn)
                })
            }
            assertEquals(0, requests.get())
        }
    }

    @Test fun laterUnrestrictedTurnUsesSelectedVoiceWithoutCarryingPriorVeto() {
        val sent = CopyOnWriteArrayList<String>()
        val events = Events()
        MayaSpeech({ object : Source() {
            override fun synthesize(text: String, key: String, allowed: () -> Boolean): ByteArray {
                sent += text
                return super.synthesize(text, key, allowed)
            }
        } }, ::Output, { it() }, events).use { speech ->
            assertFalse(speech.speak("private", "Lokale Antwort", key) {
                !NetworkTurnConstraint.forbidsExternalSpeech("Ohne Cloud.")
            })
            assertTrue(speech.speak("next", "Neue freigegebene Antwort", key) {
                !NetworkTurnConstraint.forbidsExternalSpeech("Was ist Photosynthese?")
            })
            await(events.done)
            assertEquals(listOf("Neue freigegebene Antwort"), sent)
            assertEquals(listOf("start:next", "done:next"), events.events)
        }
    }

    @Test fun arbitraryLongReplyUsesExistingQueueAndMayaForEveryChunk() {
        val queue = ApprovedSpeechChunkQueue(64)
        val text = (1..100).joinToString(" ") { "Das ist die ausführliche Antwort Nummer $it." }
        val pieces = JarvisVoiceProfile.speakableChunks(text, maxChunks = 64).pieces
        val sent = CopyOnWriteArrayList<String>()
        val done = CountDownLatch(1)
        assertTrue(queue.beginTurn("turn"))
        pieces.forEachIndexed { i, piece -> assertTrue(queue.appendApproved("turn", "c$i", piece, "visible")) }
        queue.markLastChunkSubmitted("turn")
        lateinit var speech: MayaSpeech
        fun next() {
            val chunk = queue.takeNextReady("turn")!!
            assertTrue(speech.speak(chunk.chunkId, chunk.approvedText, key) { true })
        }
        speech = MayaSpeech({ object : Source() {
            override fun synthesize(text: String, key: String, allowed: () -> Boolean): ByteArray {
                sent += text
                return super.synthesize(text, key, allowed)
            }
        } }, ::Output, { it() }, object : Events() {
            override fun finished(id: String) {
                if (queue.onChunkFinished("turn", id) == ApprovedSpeechChunkQueue.Completion.MORE_CHUNKS) next()
                else done.countDown()
            }
        })
        speech.use { next(); await(done); assertEquals(pieces, sent); assertEquals(text, sent.joinToString(" ")) }
    }

    @Test fun stopClosesRequestAndOldCompletionCannotSpeakOverNewReply() {
        val entered = CountDownLatch(1)
        val released = CountDownLatch(1)
        val count = AtomicInteger()
        val events = Events()
        MayaSpeech({ if (count.getAndIncrement() == 0) object : Source() {
            override fun synthesize(text: String, key: String, allowed: () -> Boolean): ByteArray {
                entered.countDown(); await(released)
                return super.synthesize(text, key, allowed)
            }
            override fun close() { released.countDown() }
        } else Source() }, ::Output, { it() }, events).use {
            it.speak("old", "Alte Antwort", key) { true }
            await(entered)
            it.stop()
            it.speak("new", "Neue Antwort", key) { true }
            await(events.done)
            assertEquals(listOf("start:new", "done:new"), events.events)
        }
    }

    @Test fun permissionRevokedDuringDownloadEndsWithoutPlayback() {
        val allowed = AtomicBoolean(true)
        val players = AtomicInteger()
        val events = Events()
        MayaSpeech({ object : Source() {
            override fun synthesize(text: String, key: String, allowedNow: () -> Boolean): ByteArray {
                allowed.set(false)
                return byteArrayOf(0, 0)
            }
        } }, { players.incrementAndGet(); Output() }, { it() }, events).use {
            it.speak("revoked", "Hallo", key, allowed::get)
            await(events.done)
            assertEquals(listOf("failed:revoked"), events.events)
            assertEquals(0, players.get())
        }
    }

    @Test fun failedProviderIsNotRetriedOrReportedAsSpoken() {
        val calls = AtomicInteger()
        val events = Events()
        MayaSpeech({ object : Source() {
            override fun synthesize(text: String, key: String, allowed: () -> Boolean): ByteArray {
                calls.incrementAndGet(); error("HTTP failure")
            }
        } }, ::Output, { it() }, events).use {
            it.speak("error", "Hallo", key) { true }; await(events.done)
            assertEquals(1, calls.get())
            assertEquals(listOf("failed:error"), events.events)
        }
    }

    @Test fun malformedOrExcessiveAudioNeverStartsAvatarOrPlayer() {
        for (pcm in listOf(byteArrayOf(), byteArrayOf(1), ByteArray(MayaSpeech.MAX_PCM_BYTES + 2))) {
            val events = Events()
            val players = AtomicInteger()
            MayaSpeech({ object : Source() {
                override fun synthesize(text: String, key: String, allowed: () -> Boolean) = pcm
            } }, { players.incrementAndGet(); Output() }, { it() }, events).use {
                it.speak("bad", "Hallo", key) { true }; await(events.done)
                assertEquals(0, players.get())
                assertEquals(listOf("failed:bad"), events.events)
            }
        }
    }

    @Test fun delayedUiCallbacksAreDiscardedAfterStop() {
        val callbacks = CopyOnWriteArrayList<() -> Unit>()
        val queued = CountDownLatch(2)
        val events = Events()
        MayaSpeech(::Source, ::Output, { callbacks += it; queued.countDown() }, events).use {
            it.speak("old", "Hallo", key) { true }; await(queued)
            it.stop(); callbacks.forEach { callback -> callback() }
            assertTrue(events.events.isEmpty())
        }
    }

    @Test fun stopDuringPlaybackClosesAudioAndSuppressesCompletion() {
        val playing = CountDownLatch(1)
        val stopped = CountDownLatch(1)
        val events = Events()
        val speech = MayaSpeech(::Source, { object : Output() {
            override fun play(pcm: ByteArray, allowed: () -> Boolean, started: () -> Unit) {
                started(); playing.countDown(); await(stopped)
            }
            override fun close() { stopped.countDown() }
        } }, { it() }, events)
        speech.speak("a", "Hallo", key) { true }; await(playing)
        speech.close(); await(stopped)
        assertFalse(speech.speak("b", "Noch einmal", key) { true })
        assertEquals(listOf("start:a"), events.events)
    }
}
