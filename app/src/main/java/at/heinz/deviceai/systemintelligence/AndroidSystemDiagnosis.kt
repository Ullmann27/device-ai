package at.heinz.deviceai.systemintelligence

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import android.os.Debug
import android.os.PowerManager
import android.os.Process
import android.os.StatFs
import android.os.SystemClock

/** One bounded local observation, no workers. */
class AndroidSystemDiagnosis(private val context: Context) {
    fun capture(cancelled: () -> Boolean = { false }): SystemDiagnosis {
        fun active() { check(!cancelled() && !Thread.currentThread().isInterrupted) { "DIAGNOSIS_CANCELLED" } }
        active()
        val missing = linkedSetOf<String>()
        fun <T> sample(name: String, read: () -> T): T? = try { active(); read() }
            catch (e: InterruptedException) { Thread.currentThread().interrupt(); throw e }
            catch (_: Exception) { active(); missing.add(name); null }
        val disk = sample("Datenträger") { StatFs(context.filesDir.absolutePath).let {
            require(it.totalBytes > 0 && it.availableBytes in 0..it.totalBytes)
            it.totalBytes to it.availableBytes
        } }
        val memory = sample("RAM") {
            val manager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            ActivityManager.MemoryInfo().also { manager.getMemoryInfo(it) }.also {
                require(it.totalMem > 0 && it.availMem in 0..it.totalMem)
            }
        }
        val pss = sample("Continuum-PSS") {
            Debug.MemoryInfo().also(Debug::getMemoryInfo).totalPss.toLong().also { require(it > 0) } * 1024L
        }
        val battery = sample("Akku") {
            requireNotNull(context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED)))
        }
        val level = battery?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = battery?.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
        val percent = if (level != null && scale != null && scale > 0 && level in 0..scale)
            (level.toLong() * 100 / scale).toInt() else null
        if (percent == null) missing.add("Akkustand")
        val temperature = battery?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, Int.MIN_VALUE)
            ?.takeIf { it in -400..1000 }?.div(10.0)
        if (temperature == null) missing.add("Akkutemperatur")
        val charging = chargingStatus(battery?.getIntExtra(BatteryManager.EXTRA_STATUS, BatteryManager.BATTERY_STATUS_UNKNOWN))
        val thermal = if (Build.VERSION.SDK_INT >= 29) sample("Thermalstatus") {
            (context.getSystemService(Context.POWER_SERVICE) as PowerManager).currentThermalStatus.also { require(it in 0..6) }
        } else null
        val cpuStart = Process.getElapsedCpuTime()
        val wallStart = SystemClock.elapsedRealtime()
        Thread.sleep(250L)
        active()
        val cpuDelta = Process.getElapsedCpuTime() - cpuStart
        val wallDelta = SystemClock.elapsedRealtime() - wallStart
        val cpu = if (wallDelta >= 200 && cpuDelta >= 0) cpuDelta * 100.0 / wallDelta else null
        if (cpu == null) missing.add("App-CPU")
        return SystemDiagnosis(System.currentTimeMillis(), Build.VERSION.SDK_INT, disk?.first, disk?.second,
            memory?.totalMem, memory?.availMem, memory?.lowMemory, pss, percent, temperature, charging,
            thermal, cpu, wallDelta.coerceAtLeast(0), missing)
    }

    companion object {
        internal fun chargingStatus(status: Int?): Boolean? = when (status) {
            BatteryManager.BATTERY_STATUS_CHARGING -> true
            BatteryManager.BATTERY_STATUS_FULL, BatteryManager.BATTERY_STATUS_DISCHARGING,
            BatteryManager.BATTERY_STATUS_NOT_CHARGING -> false
            else -> null
        }
    }
}
