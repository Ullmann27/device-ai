package at.heinz.deviceai.cognitive

import org.junit.Assert.*
import org.junit.Test

class LocalSpeechSessionTest {
    private class FakePlatform : LocalSpeechSession.Platform {
        var available = true
        var permission = true
        var failCreate: RuntimeException? = null
        val recognizers = mutableListOf<FakeRecognizer>()
        val timers = mutableListOf<Timer>()
        var shutdowns = 0
        override fun available() = available
        override fun microphoneGranted() = permission
        override fun create(callbacks: LocalSpeechSession.Callbacks): LocalSpeechSession.Recognizer {
            failCreate?.let { throw it }
            return FakeRecognizer(callbacks).also { recognizers += it }
        }
        override fun scheduleTimeout(task: () -> Unit): () -> Unit {
            val timer = Timer(task); timers += timer
            return { timer.cancelled = true }
        }
        override fun shutdown() { shutdowns++ }
    }
    private class Timer(val fire: () -> Unit, var cancelled: Boolean = false)
    private class FakeRecognizer(val callbacks: LocalSpeechSession.Callbacks) : LocalSpeechSession.Recognizer {
        var starts = 0; var cancels = 0; var destroys = 0
        override fun start() { starts++ }
        override fun cancel() { cancels++; callbacks.failed(LocalSpeechInputPolicy.Failure.SERVICE_FAILURE) }
        override fun destroy() { destroys++ }
    }
    private class Fixture {
        val platform = FakePlatform()
        var now = 0L
        val session = LocalSpeechSession(platform) { now }
        val states = mutableListOf<Boolean>(); val text = mutableListOf<String>(); val errors = mutableListOf<String>()
        fun start() = session.start(states::add, text::add, errors::add)
        val recognizer get() = platform.recognizers.last()
    }
    @Test fun listeningIsOnlyShownAfterTheRecognizerConfirmsReady() {
        val f = Fixture()
        f.start()
        assertTrue("A start request is not live microphone readiness", f.states.isEmpty())
        val r = f.recognizer
        assertEquals(1, r.starts)
        r.callbacks.ready()
        assertEquals(listOf(true), f.states)
        r.callbacks.result("Hallo")
        assertEquals(listOf("Hallo"), f.text)
        assertEquals(listOf(true, false), f.states)
    }

    @Test fun startupFailureDoesNotDisplayFalseListening() {
        val f = Fixture()
        f.platform.failCreate = IllegalStateException("no local recognizer")
        f.start()
        assertEquals(listOf(false), f.states)
        assertTrue(f.text.isEmpty())
        assertEquals(LocalSpeechInputPolicy.message(LocalSpeechInputPolicy.Failure.START_FAILED),
            f.errors.single())
    }

    @Test fun recognizedTextDeliveredExactlyOnceAndResourcesReleased() {
        val f = Fixture(); f.start(); val r = f.recognizer
        r.callbacks.ready(); r.callbacks.result("Hallo\n Heinz")
        r.callbacks.result("duplicate"); r.callbacks.failed(LocalSpeechInputPolicy.Failure.NO_MATCH)
        assertEquals(listOf("Hallo  Heinz"), f.text); assertTrue(f.errors.isEmpty())
        assertEquals(1, r.starts); assertEquals(0, r.cancels); assertEquals(1, r.destroys)
        assertTrue(f.platform.timers.single().cancelled); assertFalse(f.states.last())
    }
    @Test fun cancellationDoesNotReportFailureOrRestartAfterLateCallbacksAndTimeout() {
        val f = Fixture(); f.start(); val r = f.recognizer
        f.session.cancel(); f.session.cancel()
        val states = f.states.toList()
        r.callbacks.ready(); r.callbacks.result("late"); r.callbacks.failed(LocalSpeechInputPolicy.Failure.NO_MATCH)
        f.platform.timers.single().fire()
        assertEquals(states, f.states); assertTrue(f.text.isEmpty()); assertTrue(f.errors.isEmpty())
        assertEquals(1, r.destroys); assertEquals(1, r.cancels); assertEquals(1, r.starts)
    }
    @Test fun replacedTurnIgnoresOldErrorsResultsAndTimerWithoutDestroyingNewRecognizer() {
        val f = Fixture(); f.start(); val old = f.recognizer; f.start(); val current = f.recognizer
        old.callbacks.failed(LocalSpeechInputPolicy.Failure.PERMISSION); old.callbacks.ready(); old.callbacks.result("old")
        f.platform.timers.first().fire()
        assertEquals(0, current.destroys); assertTrue(f.errors.isEmpty()); assertTrue(f.text.isEmpty())
        current.callbacks.result("current"); assertEquals(listOf("current"), f.text)
    }
    @Test fun emptyResultIsActionableNoMatch() {
        val f = Fixture(); f.start(); f.recognizer.callbacks.result("\n  ")
        assertTrue(f.text.isEmpty()); assertEquals(listOf(LocalSpeechInputPolicy.message(LocalSpeechInputPolicy.Failure.NO_MATCH)), f.errors)
    }
    @Test fun noInputAndPermissionLanguageAndServiceFailuresRemainDistinct() {
        val failures = listOf(LocalSpeechInputPolicy.Failure.NO_MATCH, LocalSpeechInputPolicy.Failure.NO_INPUT,
            LocalSpeechInputPolicy.Failure.PERMISSION, LocalSpeechInputPolicy.Failure.LANGUAGE_UNSUPPORTED,
            LocalSpeechInputPolicy.Failure.LANGUAGE_UNAVAILABLE, LocalSpeechInputPolicy.Failure.SERVICE_UNAVAILABLE)
        val messages = failures.map { failure ->
            val f = Fixture(); f.start(); f.recognizer.callbacks.failed(failure)
            assertEquals(1, f.recognizer.destroys); assertTrue(f.text.isEmpty()); f.errors.single()
        }
        assertEquals(failures.size, messages.toSet().size)
        assertTrue(messages.all { it.contains("erneut") || it.contains("prüfen") || it.contains("tippen") })
    }
    @Test fun revokedMicrophoneNeverDeliversTranscript() {
        val f = Fixture(); f.start(); f.platform.permission = false; f.recognizer.callbacks.result("private")
        assertTrue(f.text.isEmpty()); assertEquals(LocalSpeechInputPolicy.message(LocalSpeechInputPolicy.Failure.PERMISSION), f.errors.single())
    }
    @Test fun missingPermissionNeverCreatesRecognizerOrTimer() {
        val f = Fixture(); f.platform.permission = false; f.start()
        assertTrue(f.platform.recognizers.isEmpty()); assertTrue(f.platform.timers.isEmpty())
        assertTrue(f.errors.single().contains("Mikrofonberechtigung")); assertEquals(listOf(false), f.states)
    }
    @Test fun unavailableServiceNeverCreatesRecognizerOrTimer() {
        val f = Fixture(); f.platform.available = false; f.start()
        assertTrue(f.platform.recognizers.isEmpty()); assertTrue(f.platform.timers.isEmpty())
        assertEquals(LocalSpeechInputPolicy.message(LocalSpeechInputPolicy.Failure.SERVICE_UNAVAILABLE), f.errors.single())
    }
    @Test fun replacementInsidePreflightStateCallbackSuppressesOldError() {
        val f = Fixture(); f.platform.available = false
        f.session.start({ state -> if (!state) { f.platform.available = true; f.start() } }, f.text::add, f.errors::add)
        assertTrue(f.errors.isEmpty()); assertEquals(1, f.platform.recognizers.size)
        f.recognizer.callbacks.result("replacement"); assertEquals(listOf("replacement"), f.text)
    }
    @Test fun unavailableServiceDoesNotRequestPermissionForAbsentRecognizer() {
        val f = Fixture(); f.platform.available = false; f.platform.permission = false; f.start()
        assertTrue(f.platform.recognizers.isEmpty())
        assertEquals(LocalSpeechInputPolicy.message(LocalSpeechInputPolicy.Failure.SERVICE_UNAVAILABLE), f.errors.single())
    }
    @Test fun creationSecurityFailureIsPermissionFailure() {
        val f = Fixture(); f.platform.failCreate = SecurityException(); f.start()
        assertEquals(LocalSpeechInputPolicy.message(LocalSpeechInputPolicy.Failure.PERMISSION), f.errors.single())
        assertFalse(f.states.last()); assertTrue(f.platform.timers.isEmpty())
    }
    @Test fun creationFailureNeverRetriesAutomatically() {
        val f = Fixture(); f.platform.failCreate = IllegalStateException(); f.start()
        assertEquals(LocalSpeechInputPolicy.message(LocalSpeechInputPolicy.Failure.START_FAILED), f.errors.single())
        assertTrue(f.platform.timers.isEmpty()); assertTrue(f.platform.recognizers.isEmpty())
    }
    @Test fun deadlineReleasesAndReportsOnceEvenWhenTimerRacesWithResult() {
        val f = Fixture(); f.start(); val r = f.recognizer; f.now = 31_000_000_000L
        r.callbacks.result("expired"); f.platform.timers.single().fire()
        assertTrue(f.text.isEmpty()); assertEquals(listOf(LocalSpeechInputPolicy.message(LocalSpeechInputPolicy.Failure.TIMEOUT)), f.errors)
        assertEquals(1, r.destroys)
    }
    @Test fun timeoutActivelyCancelsAndLateResultsCannotReviveTurn() {
        val f = Fixture(); f.start(); val r = f.recognizer; f.platform.timers.single().fire(); r.callbacks.result("late")
        assertTrue(f.text.isEmpty()); assertEquals(1, f.errors.size); assertEquals(1, r.cancels); assertEquals(1, r.destroys)
    }
    @Test fun fullShutdownCancelsActiveTurnAndEndsPlatformLifetime() {
        val f = Fixture(); f.start(); val r = f.recognizer
        f.session.shutdown(); f.session.shutdown()
        assertEquals(2, f.platform.shutdowns)
        assertEquals(1, r.cancels); assertEquals(1, r.destroys)
        assertTrue(f.platform.timers.single().cancelled)
        assertTrue(f.errors.isEmpty())
    }

    @Test fun lifecycleReleaseCancelsEveryTurnAndRemovesTimer() {
        val f = Fixture(); repeat(4) { f.start(); f.session.cancel() }
        assertTrue(f.platform.recognizers.all { it.destroys == 1 && it.cancels == 1 })
        assertTrue(f.platform.timers.all { it.cancelled }); assertTrue(f.errors.isEmpty())
    }
    @Test fun cancellationInsideTerminalStateCallbackPreventsPendingTextDelivery() {
        val f = Fixture()
        f.session.start({ state -> if (!state) f.session.cancel() }, f.text::add, f.errors::add)
        f.recognizer.callbacks.result("cancelled during completion")
        assertTrue(f.text.isEmpty()); assertTrue(f.errors.isEmpty())
    }
    @Test fun replacementInsideTerminalStateCallbackPreventsOldResultDelivery() {
        val f = Fixture()
        f.session.start({ state -> if (!state) f.start() }, f.text::add, f.errors::add)
        val old = f.recognizer; old.callbacks.result("old result")
        assertTrue(f.text.isEmpty()); assertEquals(2, f.platform.recognizers.size)
        f.recognizer.callbacks.result("new result"); assertEquals(listOf("new result"), f.text)
    }
    @Test fun replacementDuringOldCancellationWinsOverOuterStart() {
        val f = Fixture()
        f.session.start({ state -> if (!state) f.start() }, f.text::add, f.errors::add)
        f.start()
        assertEquals("one original and one replacement, no third revived turn", 2, f.platform.recognizers.size)
        f.recognizer.callbacks.result("replacement"); assertEquals(listOf("replacement"), f.text)
    }
}
