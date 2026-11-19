package at.heinz.deviceai.briefing

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.BatteryManager
import android.os.Build
import android.os.PowerManager
import android.provider.CalendarContract
import android.speech.SpeechRecognizer
import at.heinz.deviceai.localmodel.LocalModelState
import at.heinz.deviceai.systemintelligence.AndroidSystemDiagnosis

/** The owner's briefing settings in app-private preferences. */
internal class ContinuumBriefingPreferences(context: Context) : ContinuumBriefingStore {
    private val preferences = context.applicationContext.getSharedPreferences(NAME, Context.MODE_PRIVATE)
    override fun get(key: String): String? = preferences.getString(key, null)
    override fun put(key: String, value: String?) {
        preferences.edit().apply { if (value == null) remove(key) else putString(key, value) }.apply()
    }

    companion object { const val NAME = "continuum-briefing" }
}

/**
 * The device calendar through `CalendarContract.Instances` (READ_CALENDAR): instances of visible calendars in the
 * asked time range, without cancelled events and without invitations the owner declined. Read only – this class has
 * no insert, update or delete, and the calendar write permission is not declared. Nothing is cached, stored or
 * transmitted.
 */
internal class AndroidContinuumCalendar(private val context: Context) : ContinuumCalendarSource {
    override fun granted(): Boolean =
        context.checkSelfPermission(Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED

    override fun read(fromMillis: Long, untilMillis: Long): ContinuumCalendarRead {
        if (!granted()) return ContinuumCalendarRead.NotGranted
        return try {
            val uri = CalendarContract.Instances.CONTENT_URI.buildUpon().also {
                ContentUris.appendId(it, fromMillis)
                ContentUris.appendId(it, untilMillis)
            }.build()
            val rows = ArrayList<ContinuumCalendarEvent>()
            val cursor = context.contentResolver.query(uri, PROJECTION, CalendarContract.Instances.VISIBLE + " = 1", null,
                CalendarContract.Instances.BEGIN + " ASC") ?: return ContinuumCalendarRead.Failed("Kalender nicht verfügbar")
            cursor.use {
                while (it.moveToNext() && rows.size < MAX_ROWS) {
                    if (!it.isNull(STATUS) && it.getInt(STATUS) == CalendarContract.Instances.STATUS_CANCELED) continue
                    if (!it.isNull(SELF_STATUS) && it.getInt(SELF_STATUS) == CalendarContract.Attendees.ATTENDEE_STATUS_DECLINED) continue
                    val begin = it.getLong(BEGIN)
                    val end = if (it.isNull(END)) begin else it.getLong(END)
                    rows += ContinuumCalendarEvent(it.getString(TITLE).orEmpty().take(200), begin, maxOf(begin, end),
                        allDay = !it.isNull(ALL_DAY) && it.getInt(ALL_DAY) != 0,
                        location = it.getString(LOCATION)?.take(200)?.takeIf { place -> place.isNotBlank() })
                }
            }
            ContinuumCalendarRead.Events(rows)
        } catch (_: SecurityException) {
            ContinuumCalendarRead.NotGranted
        } catch (error: Exception) {
            ContinuumCalendarRead.Failed(error.message ?: error.javaClass.simpleName)
        }
    }

    companion object {
        const val MAX_ROWS = 200
        val PROJECTION = arrayOf(CalendarContract.Instances.TITLE, CalendarContract.Instances.BEGIN,
            CalendarContract.Instances.END, CalendarContract.Instances.ALL_DAY, CalendarContract.Instances.EVENT_LOCATION,
            CalendarContract.Instances.STATUS, CalendarContract.Instances.SELF_ATTENDEE_STATUS)
        private const val TITLE = 0
        private const val BEGIN = 1
        private const val END = 2
        private const val ALL_DAY = 3
        private const val LOCATION = 4
        private const val STATUS = 5
        private const val SELF_STATUS = 6
    }
}

/**
 * The device values of the system check, read with the same Android APIs as the existing probes
 * (DeviceBatteryProbe, DeviceNetworkProbe, DeviceStorageProbe, AndroidSystemDiagnosis) – typed instead of as text.
 * Read-only, no listener, no polling, no shell and no network request. A value Android does not report is null.
 */
internal class AndroidContinuumDevice(private val context: Context) {
    data class Battery(val percent: Int?, val charging: Boolean?)

    fun battery(): Battery {
        val status = runCatching { context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED)) }.getOrNull()
            ?: return Battery(null, null)
        val level = status.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = status.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
        val percent = if (scale > 0 && level in 0..scale) (level.toLong() * 100 / scale).toInt() else null
        val state = status.getIntExtra(BatteryManager.EXTRA_STATUS, BatteryManager.BATTERY_STATUS_UNKNOWN)
        // A full battery on the charger does not discharge: it counts as charging here.
        val charging = if (state == BatteryManager.BATTERY_STATUS_FULL &&
            status.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) != 0) true else AndroidSystemDiagnosis.chargingStatus(state)
        return Battery(percent, charging)
    }

    /** PowerManager's thermal status (0..6), or null where the platform reports none. Never a temperature. */
    fun thermalStatus(): Int? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return null
        return runCatching { (context.getSystemService(Context.POWER_SERVICE) as? PowerManager)?.currentThermalStatus }
            .getOrNull()?.takeIf { it in 0..6 }
    }

    /** Free and total bytes of the app's own volume. */
    fun storage(): Pair<Long, Long>? = runCatching {
        val directory = context.filesDir
        val total = directory.totalSpace
        val free = directory.usableSpace
        if (total > 0 && free in 0..total) free to total else null
    }.getOrNull()

    /** Android's own signal that the active network was validated; not a proof that a particular service answers. */
    fun online(): Boolean? = runCatching {
        val manager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = manager.activeNetwork ?: return false
        manager.getNetworkCapabilities(network)?.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) == true
    }.getOrNull()

    fun speechRecognition(): Boolean? = if (Build.VERSION.SDK_INT < 31) false
        else runCatching { SpeechRecognizer.isOnDeviceRecognitionAvailable(context) }.getOrNull()

    fun microphoneGranted(): Boolean? = runCatching {
        context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
    }.getOrNull()

    /**
     * Opens the phone's dialer with [number] filled in (ACTION_DIAL). The owner presses call himself: this app has no
     * CALL_PHONE permission and never places a call. null = started, otherwise the reason.
     */
    fun dial(number: String): String? {
        val cleaned = number.filter { it.isDigit() || it == '+' }
        if (cleaned.count(Char::isDigit) < 3) return "die Nummer ist unvollständig"
        return try {
            context.startActivity(dialIntent(cleaned).apply {
                if (context !is android.app.Activity) addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            })
            null
        } catch (_: ActivityNotFoundException) {
            "auf diesem Gerät gibt es keine Telefon-App"
        } catch (error: Exception) {
            error.message ?: error.javaClass.simpleName
        }
    }

    companion object {
        const val CALENDAR_PERMISSION_REQUEST = 2271

        fun dialIntent(number: String): Intent = Intent(Intent.ACTION_DIAL, Uri.fromParts("tel", number, null))

        /** The on-device runtime's state as the system check reads it; null = the runtime does not exist yet. */
        fun localBrain(state: LocalModelState?, modelId: String?): ContinuumSystemFacts.Brain.Local =
            ContinuumSystemFacts.Brain.Local(when (state) {
                LocalModelState.EMPTY -> ContinuumSystemFacts.Brain.State.NOT_LOADED
                LocalModelState.LOADING -> ContinuumSystemFacts.Brain.State.LOADING
                LocalModelState.READY -> ContinuumSystemFacts.Brain.State.READY
                LocalModelState.GENERATING -> ContinuumSystemFacts.Brain.State.GENERATING
                LocalModelState.UNLOADING -> ContinuumSystemFacts.Brain.State.UNLOADING
                LocalModelState.FAILED -> ContinuumSystemFacts.Brain.State.FAILED
                null -> ContinuumSystemFacts.Brain.State.UNKNOWN
            }, modelId)
    }
}
