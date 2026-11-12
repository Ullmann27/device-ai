package at.heinz.deviceai

import android.app.Activity
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import at.heinz.deviceai.avatar.AvatarDirector
import at.heinz.deviceai.avatar.ComputerWorkAvatarBridge
import at.heinz.deviceai.avatar.ComputerWorkCuePlayer
import at.heinz.deviceai.cockpit.CockpitAnimationBus
import at.heinz.deviceai.cognitive.*
import java.util.UUID
import java.util.concurrent.Executor

/** One visible, explicitly approved app task. */
internal class OwnerComputerSessionController(
    private val activity: Activity,
    private val bus: CockpitAnimationBus,
    director: AvatarDirector,
    private val worker: Executor,
    private val setFeed: (LiveMonitorFeed<AndroidLiveFrame>?) -> Unit,
    private val setBusy: (Boolean) -> Unit,
    private val reply: (String) -> Unit,
    private val visible: () -> Boolean,
    playClick: () -> Unit,
    stopClick: () -> Unit
) : AutoCloseable {
    private data class Plan(val taskId: String, val packageName: String, val intent: OwnerComputerIntent,
                            val approvedAt: Long)
    private val main = Handler(Looper.getMainLooper())
    private val bridge = ComputerWorkAvatarBridge(director,
        ComputerWorkCuePlayer(post = { main.post(it) }, playClick = playClick, stopClick = stopClick))
    private val subscription = bus.subscribe(bridge)
    @Volatile private var plan: Plan? = null
    @Volatile private var grant: OwnerComputerTaskGrant? = null
    @Volatile private var workflow: OwnerComputerWorkflow? = null
    private var pendingConsent = false
    private val consentRequests = ComputerCaptureConsent()
    private data class ConsentResult(val plan: Plan, val code: Int, val data: Intent)
    private var deferredConsent: ConsentResult? = null
    private var closed = false
    val active: Boolean get() = plan != null
    val awaitingConsent: Boolean get() = pendingConsent || deferredConsent != null

    fun begin(packageName: String, intent: OwnerComputerIntent, approvedAt: Long) {
        stop()
        if (closed || SystemClock.elapsedRealtime() - approvedAt !in 0..180_000) {
            reply("Die App-Freigabe ist abgelaufen. Bitte starte den Auftrag erneut.")
            return
        }
        val next = Plan("computer-" + UUID.randomUUID(), packageName, intent, approvedAt)
        plan = next
        setBusy(true)
        val consent = if (activity.isInMultiWindowMode) AndroidLiveComputer.createProjectionConsentIntent(activity) else null
        if (consent != null) {
            pendingConsent = true
            try {
                val request = consentRequests.begin(next.taskId)
                activity.startActivityForResult(consent, request.requestCode)
                return
            } catch (_: RuntimeException) {
                consentRequests.cancel()
                stop()
                reply("Die Android-Bildschirmfreigabe konnte nicht geöffnet werden. Auftrag gestoppt.")
                return
            }
        }
        start(next, null, Activity.RESULT_CANCELED)
    }

    fun projectionResult(requestCode: Int, resultCode: Int, data: Intent?) {
        val taskId = consentRequests.accept(requestCode) ?: return
        pendingConsent = false
        val current = plan?.takeIf { it.taskId == taskId } ?: return
        if (resultCode != Activity.RESULT_OK || data == null) {
            stop()
            reply("Bildschirmfreigabe abgebrochen. Der Computer-Auftrag wurde gestoppt.")
            return
        }
        deferredConsent = ConsentResult(current, resultCode, data)
    }

    fun resumed() {
        val deferred = deferredConsent
        if (deferred != null && visible()) {
            deferredConsent = null
            if (plan === deferred.plan) start(deferred.plan, deferred.data, deferred.code)
        }
        restoreFeed()
    }

    private fun start(current: Plan, data: Intent?, resultCode: Int) {
        if (closed || plan !== current || !visible() || SystemClock.elapsedRealtime() - current.approvedAt !in 0..180_000) {
            stop()
            return
        }
        val started = if (resultCode == Activity.RESULT_OK && data != null)
            AndroidLiveComputer.startProjectionCapture(activity, current.taskId, current.packageName,
                resultCode, data, ownerConfirmed = true)
        else AndroidLiveComputer.startWindowCapture(activity, current.taskId, current.packageName, ownerConfirmed = true)
        if (!started.started || started.sessionId == null || started.observationEpoch == null) {
            stop()
            reply("Das Live-Bild konnte nicht gestartet werden: ${started.reason}. " +
                "Öffne Continuum und die Ziel-App nebeneinander und prüfe die Android-Bildschirmfreigabe.")
            return
        }
        val permission = checkNotNull(OwnerComputerTaskGrant.fromOwnerDecision(current.taskId, current.packageName,
            started.observationEpoch, current.intent, true, SystemClock::elapsedRealtime))
        grant = permission
        val emitter = CockpitWorkEmitter(current.taskId, started.sessionId, bus)
        bridge.bind(current.taskId, started.sessionId)
        val runner = OwnerComputerWorkflow(current.taskId, current.packageName, started.observationEpoch, emitter)
        workflow = runner
        setFeed(AndroidLiveComputer.feed)
        try { worker.execute {
            var firstObservation = true
            val result = try { runner.run(current.intent, object : OwnerComputerWorkflow.Port {
                override fun checkActive() {
                    check(plan === current && grant === permission && visible()) { "COMPUTER_TASK_CHANGED_OR_HIDDEN" }
                    permission.checkActive()
                    AndroidLiveComputer.checkActive(current.taskId)
                }
                private fun checkDispatchActive() {
                    checkActive()
                    LiveComputerDispatchGuard.checkLive(AndroidLiveComputer.feed, current.taskId,
                        started.sessionId, System.nanoTime())
                }
                override fun observe(): NodeActionFrame {
                    checkActive()
                    if (firstObservation) {
                        val deadline = SystemClock.elapsedRealtime() + 5_000
                        while (true) {
                            checkActive()
                            val state = AndroidLiveComputer.feed.status(System.nanoTime())
                            if (state.state == LiveMonitorState.LIVE &&
                                state.binding?.captureSession == started.sessionId &&
                                state.binding.taskId == current.taskId) break
                            check(SystemClock.elapsedRealtime() < deadline && state.state != LiveMonitorState.ENDED) {
                                "NO_FRESH_LIVE_FRAME"
                            }
                            Thread.sleep(40)
                        }
                        firstObservation = false
                    }
                    return AndroidLiveComputer.observe(current.taskId)
                }
                override fun perform(request: NodeActionRequest, events: LiveActionEventSink): NodeActionResult {
                    checkActive()
                    permission.authorize(request, observe())
                    return AndroidLiveComputer.perform(request, events, permissionCheck = ::checkDispatchActive)
                }
                override fun click(before: NodeActionFrame, label: String, expectedLabel: String,
                                   expectedEditable: Boolean, expectedHeading: Boolean, expectedSearchField: Boolean,
                                   onDispatched: (LiveTargetBounds?, Long) -> Unit): NodeActionFrame {
                    checkActive()
                    permission.authorizeClick(before, label, expectedLabel, expectedEditable, expectedHeading, expectedSearchField)
                    return AndroidLiveComputer.click(current.taskId,
                        ClickRequest(current.packageName, before.stamp.epoch, before.stamp.revision,
                            label, expectedLabel, expectedEditable, expectedHeading, expectedSearchField), ownerConfirmed = true,
                        onDispatched = onDispatched, permissionCheck = ::checkDispatchActive)
                }
            }) } finally { permission.revoke() }
            main.post {
                if (closed || plan !== current) return@post
                workflow = null
                setBusy(false)
                reply(result.message)
            }
        } } catch (_: java.util.concurrent.RejectedExecutionException) {
            stop()
            reply("Die App-Arbeit konnte nicht gestartet werden. Es wurde keine weitere Eingabe ausgeführt.")
        }
    }

    fun restoreFeed() { if (active && !pendingConsent) setFeed(AndroidLiveComputer.feed) }

    fun stop() {
        val previous = plan
        plan = null
        pendingConsent = false
        deferredConsent = null
        consentRequests.cancel()
        grant?.revoke()
        grant = null
        workflow?.cancel()
        workflow = null
        AndroidLiveComputer.stop()
        setFeed(null)
        if (previous != null) setBusy(false)
    }

    override fun close() {
        if (closed) return
        stop()
        closed = true
        subscription.close()
        bridge.close()
    }

}
