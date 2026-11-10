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
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Actual bridge and Android Handler; only the unavailable Shizuku server is shadowed. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], shadows = [ShizukuBridgeLifecycleTest.Server::class])
class ShizukuBridgeLifecycleTest {
    @Implements(value = Shizuku::class, isInAndroidSdk = false)
    class Server {
        companion object {
            var running = true
            var granted = true
            var uid = 2000
            var permissionRequests = 0
            var permissionListener: Shizuku.OnRequestPermissionResultListener? = null
            val connections = mutableListOf<ServiceConnection>()
            val unbound = mutableListOf<ServiceConnection>()
            @Implementation @JvmStatic fun pingBinder() = running
            @Implementation @JvmStatic fun getUid() = uid
            @Implementation @JvmStatic fun getVersion() = 13
            @Implementation @JvmStatic fun checkSelfPermission() =
                if (granted) PackageManager.PERMISSION_GRANTED else PackageManager.PERMISSION_DENIED
            @Implementation @JvmStatic fun shouldShowRequestPermissionRationale() = false
            @Implementation @JvmStatic fun requestPermission(code: Int) { permissionRequests++ }
            @Implementation @JvmStatic fun addBinderReceivedListener(listener: Shizuku.OnBinderReceivedListener) {}
            @Implementation @JvmStatic fun addBinderDeadListener(listener: Shizuku.OnBinderDeadListener) {}
            @Implementation @JvmStatic fun addRequestPermissionResultListener(listener: Shizuku.OnRequestPermissionResultListener) { permissionListener = listener }
            @Implementation @JvmStatic fun removeBinderReceivedListener(listener: Shizuku.OnBinderReceivedListener) {}
            @Implementation @JvmStatic fun removeBinderDeadListener(listener: Shizuku.OnBinderDeadListener) {}
            @Implementation @JvmStatic fun removeRequestPermissionResultListener(listener: Shizuku.OnRequestPermissionResultListener) { if (permissionListener === listener) permissionListener = null }
            @Implementation @JvmStatic fun bindUserService(args: Shizuku.UserServiceArgs, connection: ServiceConnection) {
                connections += connection
            }
            @Implementation @JvmStatic fun unbindUserService(args: Shizuku.UserServiceArgs, connection: ServiceConnection, remove: Boolean) {
                unbound += connection
            }
        }
    }

    @Before fun reset() {
        Server.running = true; Server.granted = true; Server.uid = 2000; Server.permissionRequests = 0
        Server.connections.clear(); Server.unbound.clear()
        Server.permissionListener = null
    }

    private fun bridge(status: MutableList<String>) = ShizukuBridge(RuntimeEnvironment.getApplication()) { status += it }

    @Test fun lateDenialReplacesTheExpiredPermissionTimeoutDiagnosis() {
        Server.granted = false
        val statuses = mutableListOf<String>(); val bridge = bridge(statuses)
        bridge.start()
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(21))
        assertTrue(statuses.last().contains("Zeit"))
        Server.permissionListener!!.onRequestPermissionResult(ShizukuBridge.REQUEST_CODE, PackageManager.PERMISSION_DENIED)
        assertTrue("the actual decision replaces the timeout", statuses.last().contains("abgelehnt"))
        assertTrue(Server.connections.isEmpty())
        bridge.stop()
    }


    @Test fun permissionGrantedAfterWatchdogTimeoutStillConnectsWhenActuallyGranted() {
        Server.granted = false
        val statuses = mutableListOf<String>(); val bridge = bridge(statuses)
        bridge.start()
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(21))
        assertTrue(statuses.last().contains("Zeit"))
        Server.granted = true
        Server.permissionListener!!.onRequestPermissionResult(ShizukuBridge.REQUEST_CODE, PackageManager.PERMISSION_GRANTED)
        assertEquals("late verified permission must trigger binding", 1, Server.connections.size)
        connect(Server.connections.single()); assertTrue(bridge.isReady())
        bridge.stop()
    }

    @Test fun permissionCallbackCannotClaimARealPermissionThatIsNotGranted() {
        Server.granted = false
        val statuses = mutableListOf<String>(); val bridge = bridge(statuses)
        bridge.start()
        Server.permissionListener!!.onRequestPermissionResult(ShizukuBridge.REQUEST_CODE, PackageManager.PERMISSION_GRANTED)
        assertTrue("callback alone cannot authorize shell access", Server.connections.isEmpty())
        bridge.stop()
    }
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

    @Test fun revokedPermissionImmediatelyRemovesReadiness() {
        val bridge = bridge(mutableListOf())
        bridge.start(); connect(Server.connections.single())
        assertTrue(bridge.isReady())
        Server.granted = false
        assertFalse(bridge.isReady())
        bridge.stop()
    }

    @Test fun revokedPermissionCannotDispatchCheckedCommand() {
        val bridge = bridge(mutableListOf())
        bridge.start(); connect(Server.connections.single())
        Server.granted = false
        val done = CountDownLatch(1)
        var failure: Throwable? = null
        bridge.executeChecked("echo forbidden") { failure = it.exceptionOrNull(); done.countDown() }
        assertTrue(done.await(5, TimeUnit.SECONDS))
        assertNotNull(failure)
        assertFalse(failure!!.message.orEmpty().contains("No shell execution"))
        bridge.stop()
    }

    @Test fun rootServerDoesNotEnableRegularCommands() {
        val bridge = bridge(mutableListOf())
        bridge.start(); connect(Server.connections.single())
        Server.uid = 0
        bridge.refresh()
        assertTrue(bridge.statusDetail().contains("Root UID 0"))
        val done = CountDownLatch(1)
        var failure: Throwable? = null
        bridge.executeChecked("echo forbidden") { failure = it.exceptionOrNull(); done.countDown() }
        assertTrue(done.await(5, TimeUnit.SECONDS))
        assertEquals("ROOT_EXPERT_DISABLED", failure?.message)
        bridge.stop()
    }

    @Test fun fixedProbeVerifiesRealUserServiceIdentity() {
        val bridge = bridge(mutableListOf())
        bridge.start()
        Server.uid = 0
        Server.connections.single().onServiceConnected(ComponentName("test", "test.Shell"), object : IDeviceShell.Stub() {
            override fun execute(command: String): String {
                assertEquals(ShizukuCapabilityProbe.COMMAND, ShellExecutionWire.decodeRequest(command).command)
                return ShellExecutionWire.encodeResult(ShellExecutionResult(ShellOutcome.COMPLETED, 0,
                    "CONTINUUM_SHIZUKU_CAPABILITIES_V1\n0\n28\nDUMPSYS=1\nPM=1\nCMD=1\n"))
            }
            override fun destroy() {}
        })
        val done = CountDownLatch(1)
        var result: Result<ShizukuCapabilities>? = null
        bridge.probeCapabilities { result = it; done.countDown() }
        assertTrue(done.await(5, TimeUnit.SECONDS))
        assertEquals(ShizukuIdentity.ROOT, result!!.getOrThrow().identity)
        assertFalse(result!!.getOrThrow().rootExpertEnabled)
        bridge.stop()
    }

    @Test fun permissionRevokedDuringShellExecutionSuppressesSuccess() {
        val bridge = bridge(mutableListOf())
        bridge.start()
        Server.connections.single().onServiceConnected(ComponentName("test", "test.Shell"), object : IDeviceShell.Stub() {
            override fun execute(command: String): String {
                Server.granted = false
                return ShellExecutionWire.encodeResult(ShellExecutionResult(ShellOutcome.COMPLETED, 0, "done"))
            }
            override fun destroy() {}
        })
        val done = CountDownLatch(1)
        var result: Result<String>? = null
        bridge.executeChecked("echo test") { result = it; done.countDown() }
        assertTrue(done.await(5, TimeUnit.SECONDS))
        assertTrue(result!!.isFailure)
        bridge.stop()
    }
}
