package at.heinz.deviceai.cognitive

import at.heinz.deviceai.localmodel.ImportedLocalModel
import at.heinz.deviceai.localmodel.LocalModelBackend
import at.heinz.deviceai.localmodel.LocalModelCapabilities
import org.junit.Assert.*
import org.junit.Test

class LocalGenerationDeliveryGuardTest {
    @Test fun queuedCurrentPartialCanReachPresentation() {
        val fixture = Fixture()
        fixture.queued.run()
        assertEquals(listOf("Erhaltener Teiltext."), fixture.delivered)
    }

    @Test fun stopAfterGenerationButBeforeQueuedDeliverySuppressesPresentation() {
        val fixture = Fixture()
        fixture.cancellation.cancel()
        fixture.queued.run()
        assertTrue(fixture.delivered.isEmpty())
    }

    @Test fun changedSessionBeforeQueuedDeliverySuppressesPresentation() {
        val fixture = Fixture()
        fixture.currentSession = "session-b"
        fixture.queued.run()
        assertTrue(fixture.delivered.isEmpty())
    }

    @Test fun changedWeightsBeforeQueuedDeliverySuppressesPresentation() {
        val fixture = Fixture()
        fixture.currentModel = model('b')
        fixture.queued.run()
        assertTrue(fixture.delivered.isEmpty())
    }

    @Test fun unloadedModelBeforeQueuedDeliverySuppressesPresentation() {
        val fixture = Fixture()
        fixture.currentModel = null
        fixture.queued.run()
        assertTrue(fixture.delivered.isEmpty())
    }

    @Test fun newerRequestBeforeQueuedDeliverySuppressesPresentation() {
        val fixture = Fixture()
        fixture.activeCancellation = DeliberationCancellation()
        fixture.queued.run()
        assertTrue(fixture.delivered.isEmpty())
    }

    private class Fixture {
        val cancellation = DeliberationCancellation()
        var activeCancellation: DeliberationCancellation? = cancellation
        var currentSession = "session-a"
        var currentModel: ImportedLocalModel? = model('a')
        val delivered = mutableListOf<String>()
        private val guard = LocalGenerationDeliveryGuard(cancellation, "session-a", checkNotNull(currentModel))
        private val partial = LocalGenerationCapture().apply {
            append("Erhaltener Teiltext.", 10)
            poll(120_010)
        }.snapshot()
        val queued = Runnable {
            assertFalse(partial.responseComplete)
            if (guard.isCurrent(activeCancellation, currentSession, currentModel)) delivered += partial.response
        }
    }

    companion object {
        private fun model(sha: Char) = ImportedLocalModel("fixture-model", "1", sha.toString().repeat(64),
            1_000L, LocalModelCapabilities(true, false, false, false, true, 8_192, null,
                setOf(LocalModelBackend.CPU)))
    }
}
