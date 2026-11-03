package at.heinz.deviceai

import android.content.ComponentName
import android.content.Context
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import rikka.shizuku.Shizuku
import java.util.concurrent.atomic.AtomicLong

class ShizukuBridge(
    private val context: Context,
    private val onStatus: (String) -> Unit
) {
    companion object {
        const val REQUEST_CODE = 4107
    }

    private val mainHandler = Handler(Looper.getMainLooper())
    // written on the main thread, read on tool worker threads
    @Volatile private var shell: IDeviceShell? = null
    // Stop/rebind invalidates a checked result even if Android reuses the same
    // Binder proxy object later. The dispatcher owns one in-flight slot.
    private val checkedSession = AtomicLong(0)
    private val checkedCalls = CheckedShellCallDispatcher()
    private var listenersRegistered = false

    private val bindDeadlineMs = 15_000L
    private val permissionDeadlineMs = 20_000L
    @Volatile private var started = false
    private var permissionPending = false
    private var permissionTimeout: Runnable? = null
    private var activeConnection: ServiceConnection? = null
    private var bindTimeout: Runnable? = null
    @Volatile private var lastConnectionStatus = "Shizuku-Verbindung noch nicht geprüft"

    fun statusDetail(): String = lastConnectionStatus

    private fun reportStatus(value: String) {
        lastConnectionStatus = value
        onStatus(value)
    }

    private fun clearPermissionPending() {
        permissionPending = false
        permissionTimeout?.let(mainHandler::removeCallbacks)
        permissionTimeout = null
    }

    private fun cancelBindTimeout() {
        bindTimeout?.let(mainHandler::removeCallbacks)
        bindTimeout = null
    }

    /** The old connection is invalidated before unbinding. Late callbacks
     * are never allowed to resurrect an old shell or grant any tool access. */
    @Synchronized private fun resetBinding() {
        cancelBindTimeout()
        val old = activeConnection
        activeConnection = null
        shell = null
        checkedSession.incrementAndGet()
        if (old != null) {
            runCatching { Shizuku.unbindUserService(userServiceArgs, old, false) }
        }
    }

    @Synchronized private fun connectResult(
        attempted: ServiceConnection,
        service: IBinder?
    ) {
        if (!started || activeConnection !== attempted) return
        val candidate = runCatching { IDeviceShell.Stub.asInterface(service) }.getOrNull()
        if (candidate == null || !runCatching {
                candidate.asBinder()?.pingBinder() == true
            }.getOrDefault(false)) {
            resetBinding()
            reportStatus("Shizuku-Shell fehlgeschlagen: ungültige Dienstverbindung")
            return
        }
        cancelBindTimeout()
        checkedSession.incrementAndGet()
        shell = candidate
        reportStatus("Bereit · Shell UID über Shizuku")
    }

    @Synchronized private fun bindFailed(
        attempted: ServiceConnection, reason: String
    ) {
        if (!started || activeConnection !== attempted) return
        resetBinding()
        reportStatus(reason)
    }


    private val binderReceived = Shizuku.OnBinderReceivedListener {
        if (started) {
            reportStatus("Shizuku-Dienst gefunden")
            ensurePermissionAndBind()
        }
    }

    private val binderDead = Shizuku.OnBinderDeadListener {
        synchronized(this) {
            if (started) {
                resetBinding()
                clearPermissionPending()
                reportStatus("Shizuku-Dienst beendet · bitte in Shizuku neu starten")
            }
        }
    }

    private val permissionResult = Shizuku.OnRequestPermissionResultListener { requestCode, grantResult ->
        synchronized(this) {
            if (requestCode == REQUEST_CODE && started && permissionPending) {
                clearPermissionPending()
                if (grantResult == PackageManager.PERMISSION_GRANTED) {
                    bindShellService()
                } else {
                    reportStatus("Shizuku-Zugriff abgelehnt · Berechtigung in Shizuku prüfen")
                }
            }
        }
    }

    private val userServiceArgs by lazy {
        Shizuku.UserServiceArgs(ComponentName(context.packageName, ShellUserService::class.java.name))
            .daemon(false)
            .processNameSuffix("shell")
            .debuggable(BuildConfig.DEBUG)
            .version(BuildConfig.VERSION_CODE)
            .tag("device-ai-shell")
    }

    @Synchronized fun start() {
        if (started) {
            refresh()
            return
        }
        started = true
        if (!listenersRegistered) {
            Shizuku.addBinderReceivedListener(binderReceived)
            Shizuku.addBinderDeadListener(binderDead)
            Shizuku.addRequestPermissionResultListener(permissionResult)
            listenersRegistered = true
        }
        refresh()
    }

    @Synchronized fun refresh() {
        if (!started) return
        if (isReady()) {
            reportStatus("Bereit · Shell UID über Shizuku")
        } else if (!isShizukuRunning()) {
            resetBinding()
            clearPermissionPending()
            reportStatus("Shizuku ist noch nicht gestartet · Shizuku-App öffnen")
        } else {
            ensurePermissionAndBind()
        }
    }

    @Synchronized fun stop() {
        started = false
        clearPermissionPending()
        resetBinding()
        if (listenersRegistered) {
            runCatching { Shizuku.removeBinderReceivedListener(binderReceived) }
            runCatching { Shizuku.removeBinderDeadListener(binderDead) }
            runCatching { Shizuku.removeRequestPermissionResultListener(permissionResult) }
            listenersRegistered = false
        }
        // A stopped bridge issues no further tool calls; a dispatched
        // external shell side effect can still complete independently.
        lastConnectionStatus = "Shizuku-Verbindung beendet"
    }

    @Synchronized fun requestAccess() {
        if (!started) {
            reportStatus("Shizuku-Brücke noch nicht gestartet")
            return
        }
        ensurePermissionAndBind(forceRequest = true)
    }

    fun isShizukuRunning(): Boolean =
        runCatching { Shizuku.pingBinder() }.getOrDefault(false)

    fun isReady(): Boolean = runCatching {
        started && isShizukuRunning() && shell?.asBinder()?.pingBinder() == true
    }.getOrDefault(false)

    fun execute(command: String, callback: (String) -> Unit) {
        val service = shell
        if (service == null) {
            callback("Noch nicht verbunden. Starte Shizuku und verbinde Device AI.")
            return
        }

        Thread {
            val result = runCatching {
                val raw = service.execute(command)
                if (raw.startsWith("DEVICE_AI_SHELL_RESULT_")) ShellExecutionWire.decodeResult(raw).display()
                else raw // legacy human console display only; tools never use this fallback
            }.getOrElse { "Fehler: ${it.message ?: it.javaClass.simpleName}" }
            mainHandler.post { callback(result) }
        }.start()
    }

    /** Checked tools do not depend on the UI thread to finish. A stale service,
     * unknown result version or failed exit never becomes a successful string.
     */
    fun executeChecked(command: String, callback: (Result<String>) -> Unit) {
        val request = runCatching { ShellExecutionRequest(command, 15_000) }
        if (request.isFailure) { callback(Result.failure(request.exceptionOrNull()!!)); return }
        val service = shell
        if (service == null) { callback(Result.failure(IllegalStateException("Shizuku not ready."))); return }
        val sessionAtStart = checkedSession.get()
        checkedCalls.execute({
            check(checkedSession.get() == sessionAtStart && shell === service && isReady()) {
                "Shizuku service changed or stopped."
            }
            val raw = service.execute(ShellExecutionWire.encodeRequest(request.getOrThrow()))
            check(checkedSession.get() == sessionAtStart && shell === service) {
                "Shizuku service changed or stopped during execution."
            }
            ShellExecutionWire.decodeResult(raw).checkedOutput()
        }, callback)
    }

    @Synchronized private fun ensurePermissionAndBind(forceRequest: Boolean = false) {
        if (!started) return
        try {
            if (!Shizuku.pingBinder()) {
                resetBinding()
                clearPermissionPending()
                reportStatus("Shizuku ist noch nicht gestartet · Shizuku-App öffnen")
                return
            }
            when {
                isReady() -> reportStatus("Bereit · Shell UID über Shizuku")
                Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED -> {
                    clearPermissionPending()
                    bindShellService()
                }
                permissionPending -> reportStatus("Warte auf Shizuku-Freigabe …")
                forceRequest || !Shizuku.shouldShowRequestPermissionRationale() -> {
                    permissionPending = true
                    val timeout = Runnable {
                        synchronized(this) {
                            if (started && permissionPending) {
                                clearPermissionPending()
                                reportStatus("Zeitüberschreitung bei Shizuku-Freigabe · erneut verbinden")
                            }
                        }
                    }
                    permissionTimeout = timeout
                    mainHandler.postDelayed(timeout, permissionDeadlineMs)
                    reportStatus("Warte auf Shizuku-Freigabe …")
                    try {
                        Shizuku.requestPermission(REQUEST_CODE)
                    } catch (t: Throwable) {
                        clearPermissionPending()
                        reportStatus("Shizuku-Freigabe fehlgeschlagen: " +
                            (t.message ?: t.javaClass.simpleName))
                    }
                }
                else -> reportStatus("Shizuku-Berechtigung wurde abgelehnt · in Shizuku freigeben")
            }
        } catch (t: Throwable) {
            reportStatus("Shizuku-Fehler: " + (t.message ?: t.javaClass.simpleName))
        }
    }

    @Synchronized private fun bindShellService() {
        if (!started) return
        if (isReady()) {
            reportStatus("Bereit · Shell UID über Shizuku")
            return
        }
        if (activeConnection != null) {
            if (shell == null) {
                reportStatus("Verbinde Shell-Dienst …")
                return
            }
            // A previously connected shell may die without a service-disconnect
            // callback. Revoke its session and retry through the normal gate.
            resetBinding()
        }

        val attempted = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
                connectResult(this, service)
            }

            override fun onServiceDisconnected(name: ComponentName?) {
                bindFailed(this, "Shizuku-Shell getrennt · erneut verbinden")
            }

            override fun onNullBinding(name: ComponentName?) {
                bindFailed(this, "Shizuku-Shell fehlgeschlagen: Dienst ohne Binder")
            }

            override fun onBindingDied(name: ComponentName?) {
                bindFailed(this, "Shizuku-Shell beendet · erneut verbinden")
            }
        }
        activeConnection = attempted
        val timeout = Runnable {
            synchronized(this) {
                if (started && activeConnection === attempted && !isReady()) {
                    resetBinding()
                    reportStatus("Zeitüberschreitung beim Shizuku-Shell-Binden · erneut verbinden")
                }
            }
        }
        bindTimeout = timeout
        mainHandler.postDelayed(timeout, bindDeadlineMs)
        reportStatus("Verbinde Shell-Dienst …")
        runCatching { Shizuku.bindUserService(userServiceArgs, attempted) }
            .onFailure {
                bindFailed(attempted, "Shizuku-Verbindung fehlgeschlagen: " +
                    (it.message ?: it.javaClass.simpleName))
            }
    }

}
