package at.heinz.deviceai

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager

/**
 * Android's current sticky battery status. No shell, Shizuku, raw dumpsys,
 * network request or private files are needed.
 */
internal object DeviceBatteryProbe {
    fun read(context: Context): String {
        val status = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            ?: error("Android-Batteriestatus nicht verfügbar. Nicht geprüft.")
        val level = status.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = status.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
        val charging = status.getIntExtra(BatteryManager.EXTRA_STATUS, BatteryManager.BATTERY_STATUS_UNKNOWN)
        val temperature = status.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, Int.MIN_VALUE)
            .takeUnless { it == Int.MIN_VALUE }
        return describe(level, scale, charging, temperature)
    }

    internal fun describe(level: Int, scale: Int, status: Int, temperatureTenths: Int?): String {
        require(scale > 0 && level in 0..scale) {
            "Android-Batteriestand ungültig oder nicht verfügbar. Nicht geprüft."
        }
        val percent = (level.toLong() * 100 / scale).toInt()
        val chargingState = when (status) {
            BatteryManager.BATTERY_STATUS_CHARGING -> "wird geladen"
            BatteryManager.BATTERY_STATUS_DISCHARGING -> "entlädt sich"
            BatteryManager.BATTERY_STATUS_FULL -> "voll geladen"
            BatteryManager.BATTERY_STATUS_NOT_CHARGING -> "lädt derzeit nicht"
            else -> "nicht eindeutig gemeldet"
        }
        return buildString {
            append("Aktueller Akkustand: ").append(percent)
                .append(" % (Android-Batteriedienst).\n")
            append("Ladezustand: ").append(chargingState).append('.')
            if (temperatureTenths != null) {
                val value = temperatureTenths.toLong()
                val magnitude = kotlin.math.abs(value)
                append("\nBatterietemperatur: ")
                if (value < 0) append('-')
                append(magnitude / 10).append(',').append(magnitude % 10)
                    .append(" °C (Akku, nicht Prozessor).")
            }
        }
    }
}
