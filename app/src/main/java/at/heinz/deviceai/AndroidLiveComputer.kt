package at.heinz.deviceai

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Context
import android.content.Intent
import android.graphics.ColorSpace
import android.hardware.HardwareBuffer
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.media.projection.MediaProjectionConfig
import android.media.projection.MediaProjectionManager
import at.heinz.deviceai.cognitive.*
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.Executors

/** Hardware-backed pixels; no encoding or persistent capture. */
internal class AndroidLiveFrame(
    val hardwareBuffer: HardwareBuffer,
    val colorSpace: ColorSpace,
    private val release: () -> Unit = { hardwareBuffer.close() }
) : AutoCloseable {
    val width get() = hardwareBuffer.width
    val height get() = hardwareBuffer.height
    private val closed = AtomicBoolean(false)
    override fun close() { if (closed.compareAndSet(false, true)) release() }
}

internal data class LiveComputerStart(
    val started: Boolean, val reason: String, val sessionId: Long? = null, val observationEpoch: Long? = null
)

/** One owner-started window session. Activity destruction must call stop(). */
internal object AndroidLiveComputer {
    private data class Session(
        val binding: LiveFrameBinding, val access: LiveObservationAccess,
        val cancelled: AtomicBoolean = AtomicBoolean(false), val journal: LiveActionJournal = LiveActionJournal(binding.taskId),
        val stopCapture: AtomicReference<(() -> Unit)?> = AtomicReference(null)
    )
    private val sequence = AtomicLong()
    private val active = AtomicReference<Session?>()
    private val handler by lazy { Handler(Looper.getMainLooper()) }
    private val screenshotExecutor by lazy { Executors.newSingleThreadExecutor { work ->
        Thread(work, "ContinuumWindowColor").apply { isDaemon = true }
    } }
    private val frames = LatestLiveFrameHolder<AndroidLiveFrame>(AndroidLiveFrame::close)
    val feed: LiveMonitorFeed<AndroidLiveFrame> get() = frames

    fun startWindowCapture(context: Context, taskId: String, targetPackage: String, ownerConfirmed: Boolean): LiveComputerStart {
        val started = beginSession(context, taskId, targetPackage, ownerConfirmed, LiveFrameOrigin.ACCESSIBILITY_WINDOW)
        if (started.started) active.get()?.let { current -> handler.post { if (Build.VERSION.SDK_INT >= 34) captureWindow(current) } }
        return started
    }

    fun createProjectionConsentIntent(context: Context): Intent? = if (Build.VERSION.SDK_INT >= 34)
        context.getSystemService(MediaProjectionManager::class.java)
            .createScreenCaptureIntent(MediaProjectionConfig.createConfigForDefaultDisplay()) else null

    fun startProjectionCapture(context: Context, taskId: String, targetPackage: String,
                               resultCode: Int, resultData: Intent, ownerConfirmed: Boolean): LiveComputerStart {
        if (resultCode != android.app.Activity.RESULT_OK) return LiveComputerStart(false, "SCREEN_CAPTURE_DENIED")
        val started = beginSession(context, taskId, targetPackage, ownerConfirmed, LiveFrameOrigin.PROJECTION_WINDOW_CROP)
        if (!started.started) return started
        return try {
            checkNotNull(context.startForegroundService(Intent(context, ContinuumLiveProjectionService::class.java)
                .putExtra(ContinuumLiveProjectionService.SESSION, started.sessionId!!)
                .putExtra(ContinuumLiveProjectionService.RESULT_CODE, resultCode)
                .putExtra(ContinuumLiveProjectionService.RESULT_DATA, resultData))) { "CAPTURE_SERVICE_START_REFUSED" }
            started
        } catch (_: Exception) {
            endProjection(started.sessionId!!, LiveMonitorReason.SOURCE_UNVERIFIED)
            LiveComputerStart(false, "SCREEN_CAPTURE_START_FAILED")
        }
    }

    private fun beginSession(context: Context, taskId: String, targetPackage: String, ownerConfirmed: Boolean,
                             origin: LiveFrameOrigin): LiveComputerStart {
        if (!ownerConfirmed) return LiveComputerStart(false, "OWNER_APPROVAL_REQUIRED")
        if (Build.VERSION.SDK_INT < 34) return LiveComputerStart(false, "WINDOW_CAPTURE_REQUIRES_ANDROID_14")
        val access = ScreenObservationBus.liveAccess() ?: return LiveComputerStart(false, "ACCESSIBILITY_OBSERVATION_NOT_GRANTED")
        if (access.service.serviceInfo.capabilities and AccessibilityServiceInfo.CAPABILITY_CAN_TAKE_SCREENSHOT == 0)
            return LiveComputerStart(false, "WINDOW_CAPTURE_PERMISSION_NOT_GRANTED")
        val binding = runCatching { LiveFrameBinding(taskId, sequence.incrementAndGet(), targetPackage,
            context.applicationContext.packageName, origin,
            minFrameIntervalMillis = if (origin == LiveFrameOrigin.PROJECTION_WINDOW_CROP) 33 else 250) }
            .getOrElse { return LiveComputerStart(false, "INVALID_CAPTURE_BINDING") }
        stop()
        val session = Session(binding, access)
        active.set(session)
        frames.bind(binding)
        return LiveComputerStart(true, "WAITING_FOR_WINDOW", binding.captureSession, access.epoch)
    }

    fun stop(reason: LiveMonitorReason = LiveMonitorReason.CAPTURE_STOPPED) {
        val previous = active.getAndSet(null)
        previous?.cancelled?.set(true)
        frames.end(reason)
        ScreenObservationBus.wake()
        previous?.stopCapture?.getAndSet(null)?.let { runCatching(it) }
    }

    internal fun projectionAccess(sessionId: Long): Pair<LiveFrameBinding, LiveObservationAccess>? {
        val current = active.get() ?: return null
        if (current.binding.captureSession != sessionId || current.cancelled.get() ||
            current.binding.origin != LiveFrameOrigin.PROJECTION_WINDOW_CROP) return null
        if (runCatching { ScreenObservationBus.liveRevision(current.access) }.isFailure) return null
        return current.binding to current.access
    }

    internal fun installProjectionStop(sessionId: Long, stop: () -> Unit): Boolean {
        val current = active.get() ?: return false
        if (current.binding.captureSession != sessionId || current.cancelled.get()) return false
        current.stopCapture.set(stop)
        if (active.get() !== current || current.cancelled.get()) { stop(); return false }
        return true
    }

    internal fun endProjection(sessionId: Long, reason: LiveMonitorReason) {
        if (active.get()?.binding?.captureSession == sessionId) stop(reason)
    }
    internal fun pauseProjection(sessionId: Long, reason: LiveMonitorReason) = frames.pause(reason, sessionId)
    internal fun wantsProjectionFrame(sessionId: Long) = frames.wantsFrame(sessionId)
    internal fun offerProjection(meta: LiveFrameMeta, frame: AndroidLiveFrame) = frames.offer(meta, frame)

    internal fun observationRevoked(service: ContinuumScreenObservationService) {
        if (active.get()?.access?.service === service) stop(LiveMonitorReason.PERMISSION_REVOKED)
    }

    fun checkActive(taskId: String) { session(taskId) }

    private fun session(taskId: String): Session {
        val current = active.get() ?: error("LIVE_COMPUTER_STOPPED")
        check(current.binding.taskId == taskId && !current.cancelled.get()) { "LIVE_COMPUTER_SESSION_CHANGED" }
        ScreenObservationBus.liveRevision(current.access)
        return current
    }

    private fun port(current: Session, access: LiveObservationAccess,
                     permissionCheck: () -> Unit = { error("ACTION_GRANT_REQUIRED") }) = AndroidLiveNodePort(access,
        current.binding.taskId, current.binding.targetPackage, { feed.status(System.nanoTime()).sequence }, {
            check(session(current.binding.taskId) === current) { "LIVE_COMPUTER_SESSION_CHANGED" }
        }, permissionCheck, captureSession = current.binding.captureSession)

    fun observe(taskId: String): NodeActionFrame {
        val current = session(taskId)
        return ScreenObservationBus.withLiveOperation(current.binding.targetPackage) { access ->
            check(access == current.access) { "OBSERVATION_PERMISSION_CHANGED" }
            port(current, access).current()
        }
    }

    fun perform(request: NodeActionRequest, events: LiveActionEventSink = LiveActionEventSink {},
                permissionCheck: () -> Unit = { error("ACTION_GRANT_REQUIRED") }): NodeActionResult {
        val current = session(request.taskId)
        check(request.packageName == current.binding.targetPackage) { "ACTION_SESSION_MISMATCH" }
        return ScreenObservationBus.withLiveOperation(request.packageName) { access ->
            check(access == current.access) { "OBSERVATION_PERMISSION_CHANGED" }
            VerifiedNodeAction(current.journal, events).execute(request, port(current, access, permissionCheck))
        }
    }

    fun click(taskId: String, request: ClickRequest, ownerConfirmed: Boolean,
              onDispatched: (LiveTargetBounds?, Long) -> Unit = { _, _ -> },
              permissionCheck: () -> Unit = { error("ACTION_GRANT_REQUIRED") }): NodeActionFrame {
        val current = session(taskId)
        check(request.packageName == current.binding.targetPackage) { "ACTION_SESSION_MISMATCH" }
        return ScreenObservationBus.withLiveOperation(request.packageName) { access ->
            check(access == current.access) { "OBSERVATION_PERMISSION_CHANGED" }
            val port = port(current, access, permissionCheck)
            val before = port.current()
            val fingerprint = liveSha256("click|${request.packageName}|${request.label}|${request.expectedLabel}|${request.expectedEditable}|${request.expectedHeading}|${request.expectedSearchField}|${request.expectedComposeField}")
            check(current.journal.admit(fingerprint, before.stamp) == LiveActionJournal.Admission.ALLOWED) { "CLICK_ALREADY_ATTEMPTED" }
            var dispatched = false
            var after: NodeActionFrame? = null
            var verdict = EffectVerdict.NOT_DISPATCHED
            try {
                VerifiedNodeClick.execute(request, ownerConfirmed, port.clickPort(request) { bounds, frame ->
                    dispatched = true
                    verdict = EffectVerdict.UNCLEAR_NO_NEW_OBSERVATION
                    onDispatched(bounds, frame)
                })
                after = port.current()
                verdict = ActionEffectVerifier.verify(before.stamp, true, after!!.stamp,
                    after!!.nodes.count { !it.password && request.matchesExpected(it) } == 1)
                check(verdict.confirmed) { "CLICK_EFFECT_NOT_VERIFIED" }
                after!!
            } catch (error: Exception) {
                if (dispatched) verdict = EffectVerdict.UNCLEAR_NO_NEW_OBSERVATION
                else if (port.inputAttempted && port.inputAccepted != false) verdict = EffectVerdict.UNCLEAR_DISPATCH
                throw error
            } finally { current.journal.finish(fingerprint, before.stamp, verdict, after?.stamp) }
        }
    }

    @androidx.annotation.RequiresApi(34)
    private fun captureWindow(current: Session) {
        if (active.get() !== current || current.cancelled.get()) return
        val before = try {
            val revision = ScreenObservationBus.liveRevision(current.access)
            val window = current.access.service.liveWindowSelection(current.binding.targetPackage).target
            check(ScreenObservationBus.liveRevision(current.access) == revision) { "CAPTURE_SOURCE_CHANGED" }
            LiveWindowCapture(current.binding, current.access.epoch, revision, window, SystemClock.uptimeMillis())
        } catch (_: Exception) {
            frames.pause(LiveMonitorReason.SOURCE_HIDDEN, current.binding.captureSession)
            schedule(current)
            return
        }
        try {
            current.access.service.takeScreenshotOfWindow(before.window.id, current.access.service.mainExecutor,
                object : AccessibilityService.TakeScreenshotCallback {
                    override fun onSuccess(result: AccessibilityService.ScreenshotResult) {
                        val frame = AndroidLiveFrame(result.hardwareBuffer, result.colorSpace)
                        var handedOff = false
                        var copying = false
                        try {
                            windowCaptureAge(current, before, result.timestamp)
                            if (frame.colorSpace.isSrgb) {
                                handedOff = true
                                publishWindowFrame(current, before, frame, result.timestamp)
                            } else {
                                screenshotExecutor.execute {
                                    AndroidGpuFrameCopier.copy(frame, screenshotExecutor) { converted ->
                                        val posted = handler.post {
                                            if (converted != null) publishWindowFrame(current, before, converted, result.timestamp)
                                            else frames.pause(LiveMonitorReason.SOURCE_UNVERIFIED, current.binding.captureSession)
                                            schedule(current)
                                        }
                                        if (!posted) converted?.close()
                                    }
                                }
                                handedOff = true
                                copying = true
                            }
                        } catch (_: Exception) {
                            frames.pause(LiveMonitorReason.SOURCE_UNVERIFIED, current.binding.captureSession)
                        } finally {
                            if (!handedOff) frame.close()
                            if (!copying) schedule(current)
                        }
                    }
                    override fun onFailure(errorCode: Int) {
                        if (active.get() !== current || current.cancelled.get()) return
                        when (errorCode) {
                            AccessibilityService.ERROR_TAKE_SCREENSHOT_NO_ACCESSIBILITY_ACCESS -> stop(LiveMonitorReason.PERMISSION_REVOKED)
                            AccessibilityService.ERROR_TAKE_SCREENSHOT_SECURE_WINDOW -> frames.pause(LiveMonitorReason.SECURE_CONTENT, current.binding.captureSession)
                            AccessibilityService.ERROR_TAKE_SCREENSHOT_INTERVAL_TIME_SHORT -> frames.pause(LiveMonitorReason.FRAME_STALE, current.binding.captureSession)
                            else -> frames.pause(LiveMonitorReason.SOURCE_HIDDEN, current.binding.captureSession)
                        }
                        schedule(current)
                    }
                })
        } catch (_: Exception) {
            frames.pause(LiveMonitorReason.SOURCE_UNVERIFIED, current.binding.captureSession)
            schedule(current)
        }
    }

    private fun windowCaptureAge(current: Session, before: LiveWindowCapture, capturedAt: Long): Long {
        check(active.get() === current && !current.cancelled.get()) { "CAPTURE_SESSION_CHANGED" }
        val revision = ScreenObservationBus.liveRevision(current.access)
        val after = current.access.service.liveWindowSelection(current.binding.targetPackage).target
        check(ScreenObservationBus.liveRevision(current.access) == revision) { "CAPTURE_SOURCE_CHANGED" }
        return before.ageIfCurrent(current.binding, current.access.epoch, revision, after, capturedAt,
            SystemClock.uptimeMillis()) ?: error("CAPTURE_SOURCE_OR_TIME_CHANGED")
    }

    private fun publishWindowFrame(current: Session, before: LiveWindowCapture, frame: AndroidLiveFrame, capturedAt: Long) {
        var handedOff = false
        try {
            val age = windowCaptureAge(current, before, capturedAt)
            check(frame.colorSpace.isSrgb) { "CAPTURE_COLOR_UNVERIFIED" }
            val meta = LiveFrameMeta(current.binding.captureSession, capturedAt * 1_000_000, frame.width, frame.height,
                current.binding.targetPackage, before.window.id, ownWindowVisible = false,
                observationRevision = before.revision, sourceViewport = LiveTargetBounds(0f, 0f, 1f, 1f),
                captureAgeMillis = age)
            handedOff = true
            frames.offer(meta, frame)
        } catch (_: Exception) {
            frames.pause(LiveMonitorReason.SOURCE_UNVERIFIED, current.binding.captureSession)
        } finally { if (!handedOff) frame.close() }
    }

    private fun schedule(current: Session) {
        if (active.get() === current && !current.cancelled.get()) handler.postDelayed({
            if (Build.VERSION.SDK_INT >= 34) captureWindow(current)
        }, 350)
    }
}
