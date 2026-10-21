package at.heinz.deviceai.learning

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import android.os.PowerManager
import android.os.StatFs

/** Live Android observations, sampled per job; no battery state is cached in preferences. */
internal fun Context.continuumLearningResources(): ContinuumLearningResources {
    val battery = registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
    val level = battery?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
    val scale = battery?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
    val percent = ContinuumOfflineLearningBudget.batteryPercent(level, scale)
    val thermal = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
        getSystemService(PowerManager::class.java)?.currentThermalStatus else null
    return ContinuumLearningResources(percent, false, thermal, StatFs(filesDir.absolutePath).availableBytes)
}
