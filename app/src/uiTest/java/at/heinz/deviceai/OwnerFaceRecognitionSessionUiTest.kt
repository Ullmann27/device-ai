package at.heinz.deviceai

import android.os.Handler
import android.os.Looper
import java.io.File
import java.nio.file.Files
import java.util.ArrayDeque
import java.util.concurrent.AbstractExecutorService
import java.util.concurrent.CountDownLatch
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.annotation.LooperMode

/** Synthetic inference, real sessions/storage. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
@GraphicsMode(GraphicsMode.Mode.LEGACY)
@LooperMode(LooperMode.Mode.PAUSED)
class OwnerFaceRecognitionSessionUiTest {
    private class Running(task: Runnable) {
        private val failure = AtomicReference<Throwable?>()
        private val thread = Thread({
            try { task.run() } catch (error: Throwable) { failure.set(error) }
        }, "face-session-test-worker").apply { isDaemon = true; start() }

        fun finish() {
            thread.join(5_000)
            assertFalse("Worker must finish", thread.isAlive)
            failure.get()?.let { throw AssertionError("Worker failed", it) }
        }
    }

    private class QueuedExecutor : AbstractExecutorService() {
        private val tasks = ArrayDeque<Runnable>()
        private var stopped = false
        val pending: Int get() = synchronized(tasks) { tasks.size }
        override fun execute(command: Runnable) = synchronized(tasks) {
            if (stopped) throw RejectedExecutionException("Test queue closed")
            tasks.addLast(command)
        }
        override fun shutdown() = synchronized(tasks) { stopped = true }
        override fun shutdownNow(): MutableList<Runnable> = synchronized(tasks) {
            stopped = true
            tasks.toMutableList().also { tasks.clear() }
        }
        override fun isShutdown() = synchronized(tasks) { stopped }
        override fun isTerminated() = synchronized(tasks) { stopped && tasks.isEmpty() }
        override fun awaitTermination(timeout: Long, unit: TimeUnit) = isTerminated()
        fun startNext() = Running(synchronized(tasks) {
            check(tasks.isNotEmpty()) { "No queued work" }
            tasks.removeFirst()
        })
        fun runNext() { startNext().finish() }
        fun runAll() { while (pending > 0) runNext() }
    }

    private class Inference : OwnerFaceInference {
        var calls = 0
        var closed = false
        var faces = 1
        var axis = 0
        var nonFinite = false
        var beforeObserve: (() -> Unit)? = null
        var lastObservation: OwnerFaceObservation? = null
        override fun observe(frame: OwnerFaceFrame): OwnerFaceObservation {
            calls++
            beforeObserve?.invoke()
            val feature = FloatArray(OwnerFaceModelContract.FEATURE_SIZE) { if (it == axis) 1f else 0f }
            if (nonFinite) feature[0] = Float.NaN
            return OwnerFaceObservation(faces, frame.observedAtElapsedMillis,
                OwnerFaceQuality(320, 240, 70f, 20f, 140f, 180f, .98f, 48f, .05f, .1f, 110.0, 150.0),
                feature).also { lastObservation = it }
        }
        override fun close() { closed = true }
    }

    private class Fixture(val directory: File, val key: SecretKey, profileName: String?) {
        val queue = QueuedExecutor()
        val clock = AtomicLong(10_000)
        val inference = Inference()
        val states = mutableListOf<OwnerFaceStatus>()
        var keyGate: (() -> Unit)? = null
        var factoryGate: (() -> Unit)? = null
        var factoryCalls = 0
        val store = OwnerFaceProfileStore(directory, { keyGate?.invoke(); key })
        val face: AndroidOwnerFaceRecognition
        init {
            if (profileName != null) store.save(OwnerFaceProfile(profileName, 1_000,
                OwnerFaceModelContract.RECOGNIZER_SHA256, List(OwnerFacePolicy.ENROLLMENT_SAMPLES) {
                    FloatArray(OwnerFaceModelContract.FEATURE_SIZE) { index -> if (index == 0) 1f else 0f }
                }))
            face = AndroidOwnerFaceRecognition(store, {
                factoryCalls++
                factoryGate?.invoke()
                inference
            }, queue, Handler(Looper.getMainLooper()), clock::get, { 100_000L }) {
                assertEquals(Looper.getMainLooper(), Looper.myLooper())
                states += it
            }
        }
        fun ready() { queue.runNext(); drain() }
        fun compare() {
            assertTrue(face.beginRecognition(true))
            queue.runNext()
            drain()
            assertEquals(OwnerFacePhase.RECOGNIZING, face.status.phase)
        }
        fun enroll(name: String = "NewFixture") {
            assertTrue(face.beginEnrollment(name, true))
            queue.runNext()
            drain()
            assertEquals(OwnerFacePhase.ENROLLING, face.status.phase)
        }
        fun frame(token: Long = face.sessionToken, time: Long = clock.get()) =
            OwnerFaceFrame(2, 2, 0, time, token, ByteArray(6) { 7 })
        fun submit(delta: Long = 500): OwnerFaceFrame {
            clock.addAndGet(delta)
            return frame().also(face::acceptFrame)
        }
        fun observe(delta: Long = 500, deliverUi: Boolean = true): OwnerFaceFrame {
            val frame = submit(delta)
            assertEquals(1, queue.pending)
            queue.runNext()
            if (deliverUi) drain()
            assertWiped(frame)
            assertTrue(inference.lastObservation!!.feature!!.all { it == 0f })
            return frame
        }
        fun advance(millis: Long) {
            clock.addAndGet(millis)
            shadowOf(Looper.getMainLooper()).idleFor(millis, TimeUnit.MILLISECONDS)
        }
        fun close() {
            keyGate = null
            factoryGate = null
            inference.beforeObserve = null
            face.close()
            queue.runAll()
        }
    }

    private val fixtures = mutableListOf<Fixture>()
    private fun fixture(profileName: String? = "StoredFixture", shared: Fixture? = null): Fixture = Fixture(
        shared?.directory ?: Files.createTempDirectory("face-session-test").toFile(),
        shared?.key ?: KeyGenerator.getInstance("AES").apply { init(256) }.generateKey(),
        profileName
    ).also { fixtures += it }

    @After fun cleanup() {
        fixtures.forEach { it.close() }
        drain()
        fixtures.map { it.directory }.distinct().forEach { it.deleteRecursively() }
    }

    @Test fun explicitConsentAndEnrollmentAreRequired() {
        val f = fixture(profileName = null)
        assertFalse(f.face.beginEnrollment("Fixture", true))
        f.ready()
        assertEquals(OwnerFacePhase.NOT_ENROLLED, f.face.status.phase)
        assertFalse(f.face.beginRecognition(true))
        assertFalse(f.face.beginEnrollment("Fixture", false))
        assertFalse(f.face.beginEnrollment("\u0000Invalid", true))
        assertFalse(f.face.wantsFrames)
        assertEquals(0, f.factoryCalls)
        val unrequested = f.frame(token = 1)
        f.face.acceptFrame(unrequested)
        assertWiped(unrequested)
        assertEquals(0, f.inference.calls)
    }

    @Test fun frequentFramesAreDroppedWithoutBreakingMatchSequence() {
        val f = fixture()
        f.ready(); f.compare()
        repeat(21) {
            val frame = f.submit(delta = if (it == 0) 0 else 60)
            if (f.queue.pending > 0) f.queue.runNext()
            assertWiped(frame)
        }
        drain()
        assertEquals(3, f.inference.calls)
        assertEquals(OwnerFacePhase.MATCH, f.face.status.phase)
        assertTrue(f.face.status.sessionActive)
    }

    @Test fun onlyOneFrameCanWaitForInference() {
        val f = fixture()
        f.ready(); f.compare()
        val waiting = f.submit()
        repeat(3) { assertWiped(f.submit()) }
        assertEquals(1, f.queue.pending)
        f.queue.runNext()
        assertWiped(waiting)
        assertEquals(1, f.inference.calls)
    }

    @Test fun oldSessionFramesAreDiscardedAfterRestart() {
        val f = fixture()
        f.ready(); f.compare()
        val oldToken = f.face.sessionToken
        f.face.stop()
        f.compare()
        assertNotEquals(oldToken, f.face.sessionToken)
        val old = f.frame(oldToken)
        f.face.acceptFrame(old)
        assertWiped(old)
        assertEquals(0, f.queue.pending)
        assertEquals(0, f.inference.calls)
        f.observe()
        assertEquals(OwnerFacePhase.RECOGNIZING, f.face.status.phase)
    }

    @Test fun stoppedWorkerCannotPublishLateMatch() {
        val f = fixture()
        f.ready(); f.compare()
        repeat(2) { f.observe() }
        f.states.clear()
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        f.inference.beforeObserve = { entered.countDown(); check(release.await(5, TimeUnit.SECONDS)) }
        val frame = f.submit()
        val running = f.queue.startNext()
        try {
            assertTrue(entered.await(5, TimeUnit.SECONDS))
            f.face.stop()
            assertEquals(OwnerFacePhase.OFF, f.face.status.phase)
            assertFalse(f.face.wantsFrames)
        } finally { release.countDown(); running.finish() }
        drain()
        assertWiped(frame)
        assertFalse(f.states.any { it.phase == OwnerFacePhase.MATCH })
        assertEquals(OwnerFacePhase.OFF, f.face.status.phase)
    }

    @Test fun pendingUiMatchIsSuppressedByStop() {
        val f = fixture()
        f.ready(); f.compare()
        repeat(2) { f.observe() }
        f.states.clear()
        f.observe(deliverUi = false)
        assertEquals(OwnerFacePhase.MATCH, f.face.status.phase)
        f.face.stop()
        drain()
        assertFalse(f.states.any { it.phase == OwnerFacePhase.MATCH })
        assertEquals(OwnerFacePhase.OFF, f.states.last().phase)
    }

    @Test fun matchExpiresAndStalledCameraEndsSession() {
        val f = fixture()
        f.ready(); f.compare()
        repeat(3) { f.observe() }
        assertEquals(OwnerFacePhase.MATCH, f.face.status.phase)
        f.advance(1_600)
        assertEquals(OwnerFacePhase.NO_FACE, f.face.status.phase)
        assertTrue(f.face.wantsFrames)
        f.observe()
        assertEquals(OwnerFacePhase.RECOGNIZING, f.face.status.phase)
        assertEquals(1, f.face.status.acceptedSamples)
        f.advance(5_001)
        assertEquals(OwnerFacePhase.ERROR, f.face.status.phase)
        assertFalse(f.face.wantsFrames)
        assertEquals(0L, f.face.sessionToken)
    }

    @Test fun modelLoadingTimeoutCannotRestartFromLateWorker() {
        val f = fixture()
        f.ready()
        assertTrue(f.face.beginRecognition(true))
        f.advance(30_001)
        assertFalse(f.face.wantsFrames)
        assertEquals(OwnerFacePhase.ERROR, f.face.status.phase)
        f.queue.runNext()
        drain()
        assertEquals(0, f.factoryCalls)
        assertEquals(OwnerFacePhase.ERROR, f.face.status.phase)
    }

    @Test fun completedEnrollmentReplacesOnlyAfterEightFrames() {
        val f = fixture()
        f.ready(); f.enroll()
        repeat(7) {
            f.observe()
            assertEquals("StoredFixture", f.store.load()!!.displayName)
        }
        f.observe()
        assertEquals(OwnerFacePhase.ENROLLED, f.face.status.phase)
        assertFalse(f.face.wantsFrames)
        assertEquals("NewFixture", f.store.load()!!.displayName)
        assertEquals(8, f.face.status.acceptedSamples)
    }

    @Test fun stopDuringEncryptionCannotReplacePreviousProfile() {
        val f = fixture()
        f.ready(); f.enroll()
        repeat(7) { f.observe() }
        val original = f.directory.resolve("profile.aes").readBytes()
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        f.keyGate = { entered.countDown(); check(release.await(5, TimeUnit.SECONDS)) }
        val frame = f.submit()
        val running = f.queue.startNext()
        try {
            assertTrue(entered.await(5, TimeUnit.SECONDS))
            f.face.stop()
            assertFalse(f.face.wantsFrames)
        } finally { release.countDown(); running.finish(); f.keyGate = null }
        drain()
        assertWiped(frame)
        assertEquals(OwnerFacePhase.OFF, f.face.status.phase)
        assertArrayEquals(original, f.directory.resolve("profile.aes").readBytes())
        assertEquals(listOf("profile.aes"), f.directory.listFiles().orEmpty().map { it.name })
    }

    @Test fun deleteBeforeInitialLoadCannotReenableProfile() {
        val f = fixture()
        f.face.deleteProfile()
        f.queue.runNext()
        assertFalse(f.face.beginRecognition(true))
        assertFalse(f.face.status.hasProfile)
        f.queue.runNext()
        drain()
        assertEquals(OwnerFacePhase.DELETED, f.face.status.phase)
        assertNull(f.store.load())
        assertFalse(f.face.beginRecognition(true))
    }

    @Test fun anotherControllerDeleteDropsQueuedMatchAndOldFrames() {
        val first = fixture()
        first.ready(); first.compare()
        repeat(2) { first.observe() }
        val oldToken = first.face.sessionToken
        first.states.clear()
        first.observe(deliverUi = false)
        val second = fixture(profileName = null, shared = first)
        second.queue.runNext()
        second.face.deleteProfile()
        second.queue.runNext()
        drain()
        assertFalse(first.states.any { it.phase == OwnerFacePhase.MATCH })
        assertFalse(first.face.wantsFrames)
        val old = first.frame(oldToken)
        first.face.acceptFrame(old)
        assertWiped(old)
        first.queue.runAll()
        drain()
        assertEquals(OwnerFacePhase.NOT_ENROLLED, first.face.status.phase)
        assertEquals(3, first.inference.calls)
        assertNull(first.store.load())
    }

    @Test fun anotherControllerReplacementRevokesRunningComparison() {
        val first = fixture()
        first.ready(); first.compare()
        repeat(2) { first.observe() }
        first.states.clear()
        val second = fixture(profileName = null, shared = first)
        second.ready(); second.enroll("ReplacementFixture")
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        first.inference.beforeObserve = { entered.countDown(); check(release.await(5, TimeUnit.SECONDS)) }
        val frame = first.submit()
        val running = first.queue.startNext()
        try {
            assertTrue(entered.await(5, TimeUnit.SECONDS))
            repeat(8) { second.observe() }
            assertEquals("ReplacementFixture", second.store.load()!!.displayName)
        } finally { release.countDown(); running.finish() }
        first.queue.runAll()
        drain()
        assertWiped(frame)
        assertFalse(first.states.any { it.phase == OwnerFacePhase.MATCH })
        assertFalse(first.face.wantsFrames)
        assertEquals("ReplacementFixture", first.face.status.displayName)
    }

    @Test fun anotherControllerDeletePreventsLateEnrollmentWrite() {
        val first = fixture()
        first.ready(); first.enroll()
        repeat(7) { first.observe() }
        val second = fixture(profileName = null, shared = first)
        second.ready()
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        first.inference.beforeObserve = { entered.countDown(); check(release.await(5, TimeUnit.SECONDS)) }
        val frame = first.submit()
        val running = first.queue.startNext()
        try {
            assertTrue(entered.await(5, TimeUnit.SECONDS))
            second.face.deleteProfile()
            second.queue.runNext()
            assertNull(second.store.load())
        } finally { release.countDown(); running.finish() }
        first.queue.runAll()
        drain()
        assertWiped(frame)
        assertNull(first.store.load())
        assertFalse(first.face.wantsFrames)
        assertFalse(first.states.any { it.phase == OwnerFacePhase.ENROLLED })
    }

    @Test fun profileChangeDuringModelLoadingCannotUseOldReference() {
        val first = fixture()
        first.ready()
        val second = fixture(profileName = null, shared = first)
        second.ready()
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        first.factoryGate = { entered.countDown(); check(release.await(5, TimeUnit.SECONDS)) }
        assertTrue(first.face.beginRecognition(true))
        val running = first.queue.startNext()
        try {
            assertTrue(entered.await(5, TimeUnit.SECONDS))
            second.face.deleteProfile()
            second.queue.runNext()
        } finally { release.countDown(); running.finish() }
        first.queue.runAll()
        drain()
        assertFalse(first.face.wantsFrames)
        assertEquals(OwnerFacePhase.NOT_ENROLLED, first.face.status.phase)
        assertEquals(0, first.inference.calls)
    }

    @Test fun closeDisposesQueuedFramesAndSuppressesCallbacks() {
        val f = fixture()
        f.ready(); f.compare()
        f.states.clear()
        val frame = f.submit()
        f.face.close()
        f.queue.runAll()
        drain()
        assertWiped(frame)
        assertTrue(f.inference.closed)
        assertTrue(f.states.isEmpty())
        assertEquals(OwnerFacePhase.OFF, f.face.status.phase)
        assertFalse(f.face.beginRecognition(true))
    }

    private companion object {
        fun drain() { shadowOf(Looper.getMainLooper()).idle() }
        fun assertWiped(frame: OwnerFaceFrame) {
            assertFalse(frame.valid())
            assertTrue(frame.yuv420.all { it == 0.toByte() })
        }
    }
}
