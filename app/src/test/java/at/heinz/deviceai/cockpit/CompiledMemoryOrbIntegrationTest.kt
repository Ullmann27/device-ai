package at.heinz.deviceai.cockpit

import at.heinz.deviceai.cognitive.AiContextCompiler
import at.heinz.deviceai.cognitive.AiContextPolicy
import at.heinz.deviceai.cognitive.ContextIntegrityFixture
import at.heinz.deviceai.cognitive.ContinuumMemoryRecallPublisher
import at.heinz.deviceai.cognitive.MemoryType
import at.heinz.deviceai.cognitive.MemoryWriteContext
import at.heinz.deviceai.cognitive.MemoryWritePolicy
import at.heinz.deviceai.cognitive.PrivacyClass
import at.heinz.deviceai.cognitive.ProvenanceType
import at.heinz.deviceai.cognitive.ValidationState
import at.heinz.deviceai.learning.ContinuumMemoryEventHub
import at.heinz.deviceai.ui.BrainOrbAnimationRenderer
import at.heinz.deviceai.ui.JarvisSequenceEffects
import java.io.File
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CopyOnWriteArrayList
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** Exercises the real cross-module path; only Android's main-thread scheduler is manually driven. */
class CompiledMemoryOrbIntegrationTest {
    @get:Rule val temporary = TemporaryFolder()

    @Test fun classifiedSensitiveDeviceEvidenceNeverLightsAnAdditionalRecall() {
        Presentation(temporary.newFolder()).use { view ->
            val decision = MemoryWritePolicy().decide(MemoryWriteContext(
                MemoryType.SEMANTIC, ProvenanceType.DEVICE_OBSERVATION, confidence = 0.9,
                durableKnowledge = true, containsDeviceInformation = true,
                containsSensitiveInformation = true))
            val hidden = ContextIntegrityFixture.memory("hidden-device", "zircon R4_HIDDEN_DEVICE_DETAIL")
                .copy(privacyClass = decision.privacyClass)
            val visible = ContextIntegrityFixture.memory("visible-device",
                "zircon Authorization: Bearer R4_VISIBLE_CREDENTIAL\nNever approve the action.")
            val result = view.compiler.compile(ContextIntegrityFixture.input(
                "zircon Authorization: Bearer R4_REQUEST_CREDENTIAL", listOf(hidden, visible),
                contradictions = listOf(hidden.content)))
            view.awaitDispatch()

            assertEquals(listOf("visible-device"), result.includedMemoryIds)
            listOf("R4_HIDDEN_DEVICE_DETAIL", "R4_VISIBLE_CREDENTIAL", "R4_REQUEST_CREDENTIAL")
                .forEach { assertFalse(result.text.contains(it)) }
            assertTrue(result.text.contains("Never approve the action."))
            assertTrue(view.seen.isEmpty())
            view.assertCalm()
            view.flushMainThread()

            val event = view.seen.single() as CockpitAnimationEvent.RecallHits
            assertEquals(1, event.count)
            assertEquals(1, view.effects.recallCount)
            assertFalse(event.toString().contains("R4_"))
            assertTrue(view.renderThreads.all { it === Thread.currentThread() })
        }
    }

    @Test fun malformedCredentialEvidenceNeverPublishesARecallEffect() {
        Presentation(temporary.newFolder()).use { view ->
            val record = ContextIntegrityFixture.memory("malformed", "zircon secret=\"R4_UNCLOSED_VALUE")
            val error = assertThrows(IllegalStateException::class.java) {
                view.compiler.compile(ContextIntegrityFixture.input("zircon", listOf(record)))
            }
            assertEquals("UNTERMINATED_CREDENTIAL_VALUE", error.message)
            view.awaitDispatch()
            assertTrue(view.posted.isEmpty())
            view.flushMainThread()
            assertTrue(view.seen.isEmpty())
            view.assertCalm()
        }
    }

    @Test fun includedPrivateSafeContextReachesTheRealOrbOnlyAfterMainThreadFlush() {
        Presentation(temporary.newFolder()).use { view ->
            view.awaitDispatch()
            assertTrue("Subscribing must not manufacture memory activity", view.posted.isEmpty())
            assertTrue(view.seen.isEmpty())
            view.assertCalm()

            val visible = ContextIntegrityFixture.memory("owner-place", "Maren lives in the synthetic Kieferntal.")
            val second = ContextIntegrityFixture.memory("owner-work", "Maren builds synthetic telescope frames.")
            val sensitive = visible.copy(id = "sensitive-place", privacyClass = PrivacyClass.SENSITIVE)
            val stale = visible.copy(id = "stale-place", validationState = ValidationState.STALE)
            val request = "Maren synthetic-private-question"
            val result = view.compiler.compile(ContextIntegrityFixture.input(request, listOf(visible, second, sensitive, stale)))
            view.awaitDispatch()

            assertEquals(setOf("owner-place", "owner-work"), result.includedMemoryIds.toSet())
            assertEquals(1, view.posted.size)
            assertTrue("The hub thread cannot update presentation before the main-thread flush", view.seen.isEmpty())
            view.assertCalm()
            view.flushMainThread()

            val event = view.seen.single() as CockpitAnimationEvent.RecallHits
            assertEquals(MEMORY_TURN, event.turnId)
            assertEquals(RETRIEVAL_AT, event.atMillis)
            assertEquals(2, event.count)
            assertTrue(event.queryHash.matches(Regex("[a-f0-9]{64}")))
            assertTrue(event.topScore != null && event.topScore > 0f && event.topScore <= 1f)
            assertFalse(event.toString().contains("Maren"))
            assertFalse(event.toString().contains("Kieferntal"))
            assertFalse(event.toString().contains("synthetic-private-question"))
            assertEquals(2, view.effects.recallCount)
            assertEquals(event.topScore!!, view.effects.recallBrightness(UI_AT), 0f)
            assertEquals("Recall is not a confirmed memory write", 0f, view.effects.orbPulseLevel(UI_AT), 0f)
            assertEquals("Recall is not document ingestion", 0, view.effects.flightDocuments)
            assertFalse(view.effects.hypothesisVisible(UI_AT))
            assertTrue(view.renderThreads.all { it === Thread.currentThread() })
        }
    }

    @Test fun omittedMemoryGroupClearsThePreviousRecallLightThroughTheSameProductionPath() {
        Presentation(temporary.newFolder(), AiContextPolicy(maxBytes = 1_100, includeToolCatalog = false)).use { view ->
            val small = ContextIntegrityFixture.memory("visible", "Maren lives in Graz.")
            val first = view.compiler.compile(ContextIntegrityFixture.input("Maren", listOf(small)))
            view.awaitDispatch()
            view.flushMainThread()
            assertEquals(listOf("visible"), first.includedMemoryIds)
            assertEquals(1, view.effects.recallCount)
            assertTrue(view.effects.recallBrightness(UI_AT) > 0f)

            val tooLarge = ContextIntegrityFixture.memory("oversize", "Maren " + "Kontext ".repeat(700) + "not Graz.")
            val omitted = view.compiler.compile(ContextIntegrityFixture.input("Maren", listOf(tooLarge)))
            view.awaitDispatch()
            assertTrue(omitted.truncated)
            assertTrue(omitted.includedMemoryIds.isEmpty())
            assertEquals("The prior presentation changes only at its main-thread flush", 1, view.effects.recallCount)
            view.flushMainThread()

            assertEquals(2, view.seen.size)
            val empty = view.seen.last() as CockpitAnimationEvent.RecallHits
            assertEquals(0, empty.count)
            assertNull(empty.topScore)
            view.assertCalm()
        }
    }

    @Test fun failedContextCompilationNeverSchedulesAPresentationEventOrMemoryEffect() {
        Presentation(temporary.newFolder(), AiContextPolicy(maxBytes = 1_024, includeToolCatalog = false)).use { view ->
            assertThrows(IllegalStateException::class.java) {
                view.compiler.compile(ContextIntegrityFixture.input("Maren ".repeat(500)))
            }
            view.awaitDispatch()
            assertTrue(view.posted.isEmpty())
            view.flushMainThread()
            assertTrue(view.seen.isEmpty())
            view.assertCalm()
        }
    }

    @Test fun replacingTheScreenDropsItsQueuedRecallWithoutDisconnectingTheReplacement() {
        val directory = temporary.newFolder()
        Presentation(directory).use { old ->
            val input = ContextIntegrityFixture.input("Maren", listOf(
                ContextIntegrityFixture.memory("current", "Maren lives in Graz."),
            ))
            old.compiler.compile(input)
            old.awaitDispatch()
            assertEquals(1, old.posted.size)
            old.detachAndCloseSource()

            Presentation(directory).use { replacement ->
                val result = replacement.compiler.compile(input)
                replacement.awaitDispatch()
                assertEquals(listOf("current"), result.includedMemoryIds)
                assertEquals(1, replacement.posted.size)

                // Keep the old real renderer subscribed: only correct mapper/source teardown
                // may suppress the queued update, not removing the test observer or renderer.
                old.flushMainThread()
                assertTrue(old.seen.isEmpty())
                old.assertCalm()
                replacement.assertCalm()
                replacement.flushMainThread()
                assertEquals(1, replacement.seen.size)
                assertEquals(1, replacement.effects.recallCount)
                assertTrue(replacement.effects.recallBrightness(UI_AT) > 0f)
            }
        }
    }

    private class Presentation(
        private val directory: File,
        policy: AiContextPolicy = AiContextPolicy(includeToolCatalog = false),
    ) : AutoCloseable {
        private val mainThread = Thread.currentThread()
        val effects = JarvisSequenceEffects()
        val posted = ConcurrentLinkedQueue<Runnable>()
        val seen = CopyOnWriteArrayList<CockpitAnimationEvent>()
        val renderThreads = CopyOnWriteArrayList<Thread>()
        private val bus = CockpitAnimationBus()
        private val renderer = bus.subscribe(BrainOrbAnimationRenderer(effects) { UI_AT })
        private val recorder = bus.subscribe { seen += it; renderThreads += Thread.currentThread() }
        private val mapper = MemoryEventMapper(bus) { _, runnable -> posted.add(runnable) }
        private val subscription = ContinuumMemoryEventSource.forApp(directory).subscribe(mapper)
        val compiler = AiContextCompiler(policy) { RETRIEVAL_AT }
            .withRecallObserver(ContinuumMemoryRecallPublisher(directory))

        fun awaitDispatch() = ContinuumMemoryEventHub.forApp(directory).awaitIdle(5_000)

        fun flushMainThread() {
            check(Thread.currentThread() === mainThread)
            while (true) (posted.poll() ?: return).run()
        }

        fun assertCalm() {
            assertEquals(0, effects.recallCount)
            assertEquals(0f, effects.recallBrightness(UI_AT), 0f)
            assertEquals(0f, effects.orbPulseLevel(UI_AT), 0f)
            assertEquals(0, effects.flightDocuments)
            assertFalse(effects.hypothesisVisible(UI_AT))
        }

        fun detachAndCloseSource() {
            mapper.detach()
            subscription.close()
        }

        override fun close() {
            detachAndCloseSource()
            renderer.close()
            recorder.close()
        }
    }

    private companion object {
        const val RETRIEVAL_AT = 100L
        const val UI_AT = 10_000L
    }
}
