package at.heinz.deviceai

import android.content.ComponentName
import android.content.Context
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import rikka.shizuku.Shizuku

class ShizukuBridge(
    private val context: Context,
    private val onStatus: (String) -> Unit
) {
    companion object {
        const val REQUEST_CODE = 4107
    }

    private val mainHandler = Handler(Looper.getMainLooper())
    private var shell: IDeviceShell? = null

    private val binderReceived = Shizuku.OnBinderReceivedListener {
        onStatus("Shizuku-Dienst gefunden")
        ensurePermissionAndBind()
    }

    private val binderDead = Shizuku.OnBinderDeadListener {
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
            shell = IDeviceShell.Stub.asInterface(service)
            onStatus("Bereit · Shell UID über Shizuku")
        }

        override fun onServiceDisconnected(name: ComponentName?) {
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
        Shizuku.addBinderReceivedListener(binderReceived)
        Shizuku.addBinderDeadListener(binderDead)
        Shizuku.addRequestPermissionResultListener(permissionResult)

        if (runCatching { Shizuku.pingBinder() }.getOrDefault(false)) {
            ensurePermissionAndBind()
        } else {
            onStatus("Shizuku ist noch nicht gestartet")
        }
    }

    fun stop() {
        runCatching { Shizuku.removeBinderReceivedListener(binderReceived) }
        runCatching { Shizuku.removeBinderDeadListener(binderDead) }
        runCatching { Shizuku.removeRequestPermissionResultListener(permissionResult) }
        runCatching { Shizuku.unbindUserService(userServiceArgs, connection, false) }
    }

    fun requestAccess() {
        ensurePermissionAndBind(forceRequest = true)
    }

    fun isReady(): Boolean = shell?.asBinder()?.pingBinder() == true

    fun execute(command: String, callback: (String) -> Unit) {
        val service = shell
        if (service == null) {
            callback("Noch nicht verbunden. Starte Shizuku und tippe auf „Shizuku verbinden“.")
            return
        }

        Thread {
            val result = runCatching { service.execute(command) }
                .getOrElse { "Fehler: ${it.message ?: it.javaClass.simpleName}" }
            mainHandler.post { callback(result) }
        }.start()
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
        if (isReady()) return
        onStatus("Verbinde Shell-Dienst …")
        runCatching { Shizuku.bindUserService(userServiceArgs, connection) }
            .onFailure { onStatus("Verbindung fehlgeschlagen: ${it.message ?: it.javaClass.simpleName}") }
    }
}
