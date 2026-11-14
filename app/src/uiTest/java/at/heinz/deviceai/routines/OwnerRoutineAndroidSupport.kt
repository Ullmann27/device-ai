package at.heinz.deviceai.routines

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Handler
import android.os.Looper
import at.heinz.deviceai.FileToolExecutionReceiptStore
import at.heinz.deviceai.ShizukuBridge
import at.heinz.deviceai.chief.ChiefWorkerPool
import at.heinz.deviceai.cognitive.ContinuumAutonomyControl
import at.heinz.deviceai.cognitive.ContinuumAutonomyLevel
import org.junit.Assert.*
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import java.io.File
import java.util.UUID
import java.util.concurrent.ExecutorService
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

internal object OwnerRoutineAndroidSupport {
    class ObservedContext(context: Context, private val directory: File = context.filesDir) : ContextWrapper(context) {
        val batteryReads = AtomicInteger()
        override fun getApplicationContext(): Context = this
        override fun getFilesDir(): File = directory
        override fun registerReceiver(receiver: BroadcastReceiver?, filter: IntentFilter): Intent? {
            if (receiver == null && filter.hasAction(Intent.ACTION_BATTERY_CHANGED)) batteryReads.incrementAndGet()
            return super.registerReceiver(receiver, filter)
        }
    }

    fun field(target: Any, name: String): Any? = target.javaClass.getDeclaredField(name)
        .apply { isAccessible = true }.get(target)

    fun until(label: String, condition: () -> Boolean) {
        val end = System.nanoTime() + TimeUnit.SECONDS.toNanos(10)
        while (!condition() && System.nanoTime() < end) {
            shadowOf(Looper.getMainLooper()).idleFor(16, TimeUnit.MILLISECONDS)
            Thread.sleep(5)
        }
        assertTrue(label, condition())
    }

    fun battery(context: Context, percent: Int) {
        context.sendStickyBroadcast(Intent(Intent.ACTION_BATTERY_CHANGED)
            .putExtra(BatteryManager.EXTRA_LEVEL, percent).putExtra(BatteryManager.EXTRA_SCALE, 100)
            .putExtra(BatteryManager.EXTRA_STATUS, BatteryManager.BATTERY_STATUS_DISCHARGING))
    }

    fun register(runtime: OwnerRoutineRuntime, target: OwnerRoutineTarget = OwnerRoutineTarget.BATTERY,
                 event: Boolean = false): Routine {
        val routine = OwnerRoutineDraft(target, if (event) null else RoutineBook.MIN_PERIOD_MILLIS)
            .routine("r-" + UUID.randomUUID(), 0)
        assertTrue(runtime.book.addApproved(routine, RoutineOwnerApproval.confirmed(routine, 0)))
        return routine
    }

    fun receipts(context: Context) = FileToolExecutionReceiptStore(File(context.filesDir.canonicalFile, "tool-receipts")).all()

    fun idle(runtime: OwnerRoutineRuntime) {
        (field(runtime, "control") as ExecutorService).submit {}.get(10, TimeUnit.SECONDS)
        assertTrue((field(runtime, "pool") as ChiefWorkerPool).awaitIdle(10_000))
    }

    fun reset() {
        OwnerRoutineRuntime.current()?.let { runtime ->
            (field(runtime, "control") as ExecutorService).shutdownNow()
            (field(runtime, "pool") as ChiefWorkerPool).shutdown()
            assertTrue((field(runtime, "pool") as ChiefWorkerPool).awaitIdle(10_000))
            (field(runtime, "bridge") as ShizukuBridge).stop()
            (field(runtime, "handler") as Handler).removeCallbacksAndMessages(null)
        }
        OwnerRoutineRuntime::class.java.getDeclaredField("shared").apply { isAccessible = true }.set(null, null)
        (OwnerRoutineRuntime::class.java.getDeclaredField("ownerEntered").apply { isAccessible = true }
            .get(null) as AtomicBoolean).set(false)
    }

    fun context(): ObservedContext {
        val app = RuntimeEnvironment.getApplication<Application>()
        assertTrue(ContinuumAutonomyControl.setLevel(app, ContinuumAutonomyLevel.RESEARCH_AND_PROPOSALS))
        return ObservedContext(app)
    }
}
