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

    private val binderReceived = Shizuku.OnBinderReceivedListener {
        onStatus("Shizuku-Dienst gefunden")
        ensurePermissionAndBind()
    }

    private val binderDead = Shizuku.OnBinderDeadListener {
        checkedSession.incrementAndGet()
        shell = null
        onStatus("Shizuku nicht verbunden")
    }

    private val permissionResult = Shizuku.OnRequestPermissionResultListener { requestCode, grantResult ->
        if (requestCode == REQUEST_CODE) {
            if (grantResult == PackageManager.PERMISSION_GRANTED) {
                bindShellService()
            } else {
                onStatus("Shizuku-Zugriff nicht erlaubt")
            }
        }
    }

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            checkedSession.incrementAndGet()
            shell = IDeviceShell.Stub.asInterface(service)
            onStatus("Bereit · Shell UID über Shizuku")
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            checkedSession.incrementAndGet()
            shell = null
            onStatus("Shell-Dienst getrennt")
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

    fun start() {
        if (!listenersRegistered) {
            Shizuku.addBinderReceivedListener(binderReceived)
            Shizuku.addBinderDeadListener(binderDead)
            Shizuku.addRequestPermissionResultListener(permissionResult)
            listenersRegistered = true
        }
        refresh()
    }

    fun refresh() {
        if (isReady()) {
            onStatus("Bereit · Shell UID über Shizuku")
            return
        }

        if (isShizukuRunning()) {
            ensurePermissionAndBind()
        } else {
            onStatus("Shizuku ist noch nicht gestartet")
        }
    }

    fun stop() {
        checkedSession.incrementAndGet()
        if (listenersRegistered) {
            runCatching { Shizuku.removeBinderReceivedListener(binderReceived) }
            runCatching { Shizuku.removeBinderDeadListener(binderDead) }
            runCatching { Shizuku.removeRequestPermissionResultListener(permissionResult) }
            listenersRegistered = false
        }
        runCatching { Shizuku.unbindUserService(userServiceArgs, connection, false) }
        // a stopped bridge runs no further commands (a command already started may finish)
        shell = null
    }

    fun requestAccess() {
        ensurePermissionAndBind(forceRequest = true)
    }

    fun isShizukuRunning(): Boolean =
        runCatching { Shizuku.pingBinder() }.getOrDefault(false)

    fun isReady(): Boolean =
        shell?.asBinder()?.pingBinder() == true

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

    private fun ensurePermissionAndBind(forceRequest: Boolean = false) {
        try {
            if (!Shizuku.pingBinder()) {
                onStatus("Shizuku ist noch nicht gestartet")
                return
            }

            when {
                Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED -> bindShellService()
                forceRequest || !Shizuku.shouldShowRequestPermissionRationale() -> {
                    onStatus("Warte auf Shizuku-Freigabe …")
                    Shizuku.requestPermission(REQUEST_CODE)
                }
                else -> onStatus("Shizuku-Berechtigung wurde abgelehnt")
            }
        } catch (t: Throwable) {
            onStatus("Shizuku-Fehler: ${t.message ?: t.javaClass.simpleName}")
        }
    }

    private fun bindShellService() {
        if (isReady()) {
            onStatus("Bereit · Shell UID über Shizuku")
            return
        }

        onStatus("Verbinde Shell-Dienst …")
        runCatching { Shizuku.bindUserService(userServiceArgs, connection) }
            .onFailure {
                onStatus("Verbindung fehlgeschlagen: ${it.message ?: it.javaClass.simpleName}")
            }
    }
}
