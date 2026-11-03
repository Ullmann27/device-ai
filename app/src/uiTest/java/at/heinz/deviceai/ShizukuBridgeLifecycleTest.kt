package at.heinz.deviceai

import android.content.ComponentName
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.Looper
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements
import rikka.shizuku.Shizuku
import java.time.Duration

/** Actual bridge and Android Handler; only the unavailable Shizuku server is shadowed. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], shadows = [ShizukuBridgeLifecycleTest.Server::class])
class ShizukuBridgeLifecycleTest {
    @Implements(value = Shizuku::class, isInAndroidSdk = false)
    class Server {
        companion object {
            var running = true
            var granted = true
            var permissionRequests = 0
            val connections = mutableListOf<ServiceConnection>()
            val unbound = mutableListOf<ServiceConnection>()
            @Implementation @JvmStatic fun pingBinder() = running
            @Implementation @JvmStatic fun checkSelfPermission() =
                if (granted) PackageManager.PERMISSION_GRANTED else PackageManager.PERMISSION_DENIED
            @Implementation @JvmStatic fun shouldShowRequestPermissionRationale() = false
            @Implementation @JvmStatic fun requestPermission(code: Int) { permissionRequests++ }
            @Implementation @JvmStatic fun addBinderReceivedListener(listener: Shizuku.OnBinderReceivedListener) {}
            @Implementation @JvmStatic fun addBinderDeadListener(listener: Shizuku.OnBinderDeadListener) {}
            @Implementation @JvmStatic fun addRequestPermissionResultListener(listener: Shizuku.OnRequestPermissionResultListener) {}
            @Implementation @JvmStatic fun removeBinderReceivedListener(listener: Shizuku.OnBinderReceivedListener) {}
            @Implementation @JvmStatic fun removeBinderDeadListener(listener: Shizuku.OnBinderDeadListener) {}
            @Implementation @JvmStatic fun removeRequestPermissionResultListener(listener: Shizuku.OnRequestPermissionResultListener) {}
            @Implementation @JvmStatic fun bindUserService(args: Shizuku.UserServiceArgs, connection: ServiceConnection) {
                connections += connection
            }
            @Implementation @JvmStatic fun unbindUserService(args: Shizuku.UserServiceArgs, connection: ServiceConnection, remove: Boolean) {
                unbound += connection
            }
        }
    }

    @Before fun reset() {
        Server.running = true; Server.granted = true; Server.permissionRequests = 0
        Server.connections.clear(); Server.unbound.clear()
    }

    private fun bridge(status: MutableList<String>) = ShizukuBridge(RuntimeEnvironment.getApplication()) { status += it }
    private fun connect(connection: ServiceConnection) {
        connection.onServiceConnected(ComponentName("test", "test.Shell"), object : IDeviceShell.Stub() {
            override fun execute(command: String): String = error("No shell execution in lifecycle tests")
            override fun destroy() {}
        })
    }

    @Test fun repeatedRefreshAndTapKeepOnePendingBinding() {
        val status = mutableListOf<String>(); val bridge = bridge(status)
        bridge.start(); bridge.refresh(); bridge.requestAccess()
        assertEquals(1, Server.connections.size)
        connect(Server.connections.single())
        assertTrue(bridge.isReady())
        bridge.stop()
    }

    @Test fun timedOutBindingCanRetryAndLateCallbackCannotReplaceNewSession() {
        val status = mutableListOf<String>(); val bridge = bridge(status)
        bridge.start(); val old = Server.connections.single()
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(16))
        assertFalse(bridge.isReady())
        assertTrue("timeout must be actionable: $status", status.last().contains("Zeit"))
        assertTrue(Server.unbound.contains(old))
        bridge.requestAccess(); assertEquals(2, Server.connections.size)
        connect(old); assertFalse("stale callback must not attach", bridge.isReady())
        connect(Server.connections.last()); assertTrue(bridge.isReady())
        bridge.stop()
    }

    @Test fun callbackAfterStopCannotResurrectTheShell() {
        val status = mutableListOf<String>(); val bridge = bridge(status)
        bridge.start(); val pending = Server.connections.single(); bridge.stop()
        connect(pending)
        assertFalse(bridge.isReady())
    }

    @Test fun nullBindingIsVisibleAndAllowsRetry() {
        val status = mutableListOf<String>(); val bridge = bridge(status)
        bridge.start(); Server.connections.single().onNullBinding(ComponentName("test", "test.Shell"))
        assertTrue(status.last().contains("fehlgeschlagen"))
        bridge.requestAccess(); assertEquals(2, Server.connections.size)
        bridge.stop()
    }


    @Test fun destroyedBindingDoesNotLetLateShellCallbackResurrectAccess() {
        val status = mutableListOf<String>()
        val bridge = bridge(status)
        bridge.start()
        val first = Server.connections.single()
        first.onBindingDied(ComponentName("test", "test.Shell"))
        assertFalse(bridge.isReady())
        assertTrue(Server.unbound.contains(first))
        assertTrue(status.last().contains("erneut verbinden"))

        bridge.requestAccess()
        assertEquals(2, Server.connections.size)
        connect(first)
        assertFalse("dead binding callback cannot resurrect a shell", bridge.isReady())
        connect(Server.connections.last())
        assertTrue("current session can connect after a dead binding", bridge.isReady())
        bridge.stop()
    }

    @Test fun refreshKeepsAConnectingBindingAndShowsSpecificStatus() {
        val statuses = mutableListOf<String>()
        val bridge = bridge(statuses)
        bridge.start()
        repeat(6) { bridge.refresh(); bridge.requestAccess() }
        assertEquals("repeated owner taps must not spawn additional services",
            1, Server.connections.size)
        assertTrue(bridge.statusDetail().contains("Verbinde"))
        connect(Server.connections.single())
        assertTrue(bridge.statusDetail().startsWith("Bereit"))
        assertTrue(bridge.isReady())
        bridge.stop()
    }

    @Test fun pendingPermissionDoesNotOpenRepeatedDialogs() {
        Server.granted = false
        val status = mutableListOf<String>(); val bridge = bridge(status)
        bridge.start(); bridge.refresh(); bridge.requestAccess()
        assertEquals(1, Server.permissionRequests)
        assertTrue(Server.connections.isEmpty())
        bridge.stop()
    }


    @Test fun permissionRequestTimeoutDoesNotLockOutFutureOwnerRetry() {
        Server.granted = false
        val status = mutableListOf<String>()
        val bridge = bridge(status)
        bridge.start()
        assertEquals(1, Server.permissionRequests)
        bridge.refresh(); bridge.requestAccess()
        assertEquals("only one active permission request", 1, Server.permissionRequests)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(21))
        assertTrue(bridge.statusDetail().contains("Zeitüberschreitung"))
        bridge.requestAccess()
        assertEquals("owner may retry after missing callback", 2, Server.permissionRequests)
        bridge.stop()
    }

    @Test fun permissionGrantedWhileCallbackMissingCanBindOnRefresh() {
        Server.granted = false
        val bridge = bridge(mutableListOf())
        bridge.start()
        assertEquals(1, Server.permissionRequests)
        Server.granted = true
        bridge.refresh()
        assertEquals(1, Server.connections.size)
        connect(Server.connections.single())
        assertTrue(bridge.isReady())
        bridge.stop()
    }

    @Test fun serverDisappearingWithoutDeadCallbackNeverRemainsReady() {
        val bridge = bridge(mutableListOf())
        bridge.start()
        connect(Server.connections.single())
        assertTrue(bridge.isReady())
        Server.running = false
        assertFalse("server absence invalidates shell readiness", bridge.isReady())
        bridge.refresh()
        assertTrue(bridge.statusDetail().contains("nicht gestartet"))
        assertEquals(1, Server.unbound.size)
        Server.running = true
        bridge.requestAccess()
        assertEquals(2, Server.connections.size)
        connect(Server.connections.last())
        assertTrue(bridge.isReady())
        bridge.stop()
    }

    @Test fun stoppedServerReportsMissingWithoutRequestingPermissionOrBinding() {
        Server.running = false
        val status = mutableListOf<String>(); val bridge = bridge(status)
        bridge.start(); bridge.requestAccess()
        assertTrue(status.last().contains("nicht gestartet"))
        assertTrue(Server.connections.isEmpty()); assertEquals(0, Server.permissionRequests)
        bridge.stop()
    }
}
