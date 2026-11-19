package at.heinz.deviceai.briefing

import android.Manifest
import android.content.ContentProvider
import android.content.ContentValues
import android.content.Intent
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.BatteryManager
import android.provider.CalendarContract
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * The Android boundaries of the briefing lane on Robolectric: the calendar reader against a stand-in for the
 * calendar provider (query shape, permission, row mapping), the dialer intent and the typed device reads. A real
 * calendar provider, a real dialer and a real battery are NOT part of this run.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AndroidContinuumBriefingUiTest {
    /** CalendarContract.Instances stand-in: records every query and answers with [rows]. Writing is an error. */
    class CalendarProvider : ContentProvider() {
        companion object {
            class Query(val uri: Uri, val projection: List<String>?, val selection: String?, val sortOrder: String?)
            val queries = mutableListOf<Query>()
            var rows: List<Array<Any?>> = emptyList()
            var failure: RuntimeException? = null
            var noCursor = false
        }
        override fun onCreate() = true
        override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?,
                           sortOrder: String?): Cursor? {
            queries += Query(uri, projection?.toList(), selection, sortOrder)
            failure?.let { throw it }
            if (noCursor) return null
            return MatrixCursor(projection).apply { rows.forEach { addRow(it) } }
        }
        override fun getType(uri: Uri): String? = null
        override fun insert(uri: Uri, values: ContentValues?): Uri? = error("the briefing lane never writes to the calendar")
        override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = error("never deletes")
        override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int =
            error("never updates")
    }

    private val app get() = RuntimeEnvironment.getApplication()
    private val from = 1_795_000_000_000L
    private val until = from + 36 * 3_600_000L

    private fun row(title: String?, begin: Long, end: Long?, allDay: Int? = 0, location: String? = null, status: Int? = null,
                    self: Int? = null): Array<Any?> = arrayOf(title, begin, end, allDay, location, status, self)

    @Before fun provider() {
        CalendarProvider.queries.clear()
        CalendarProvider.rows = emptyList()
        CalendarProvider.failure = null
        CalendarProvider.noCursor = false
        Robolectric.setupContentProvider(CalendarProvider::class.java, CalendarContract.AUTHORITY)
    }

    @Test fun withoutThePermissionNothingIsQueried() {
        shadowOf(app).denyPermissions(Manifest.permission.READ_CALENDAR)
        val calendar = AndroidContinuumCalendar(app)
        assertFalse(calendar.granted())
        assertEquals(ContinuumCalendarRead.NotGranted, calendar.read(from, until))
        assertTrue(CalendarProvider.queries.isEmpty())
    }

    @Test fun theInstancesOfTheTimeRangeAreQueriedReadOnlyAndMappedRowByRow() {
        shadowOf(app).grantPermissions(Manifest.permission.READ_CALENDAR)
        CalendarProvider.rows = listOf(
            row("Zahnarzt", from + 9 * 3_600_000L, from + 10 * 3_600_000L, location = "Praxis Dr. Beispiel"),
            row("Geburtstag Anna", from, from + 24 * 3_600_000L, allDay = 1),
            row("Abgesagt", from + 11 * 3_600_000L, from + 12 * 3_600_000L, status = CalendarContract.Instances.STATUS_CANCELED),
            row("Abgelehnt", from + 13 * 3_600_000L, from + 14 * 3_600_000L, self = CalendarContract.Attendees.ATTENDEE_STATUS_DECLINED),
            row("Zugesagt", from + 15 * 3_600_000L, from + 16 * 3_600_000L, status = CalendarContract.Instances.STATUS_CONFIRMED,
                self = CalendarContract.Attendees.ATTENDEE_STATUS_ACCEPTED),
            row(null, from + 17 * 3_600_000L, null, allDay = null, location = "  "))
        val calendar = AndroidContinuumCalendar(app)
        assertTrue(calendar.granted())
        val read = calendar.read(from, until) as ContinuumCalendarRead.Events
        assertEquals(listOf(
            ContinuumCalendarEvent("Zahnarzt", from + 9 * 3_600_000L, from + 10 * 3_600_000L, false, "Praxis Dr. Beispiel"),
            ContinuumCalendarEvent("Geburtstag Anna", from, from + 24 * 3_600_000L, true, null),
            ContinuumCalendarEvent("Zugesagt", from + 15 * 3_600_000L, from + 16 * 3_600_000L, false, null),
            ContinuumCalendarEvent("", from + 17 * 3_600_000L, from + 17 * 3_600_000L, false, null)), read.events)
        val query = CalendarProvider.queries.single()
        assertEquals("content://com.android.calendar/instances/when/$from/$until", query.uri.toString())
        assertEquals(listOf("title", "begin", "end", "allDay", "eventLocation", "eventStatus", "selfAttendeeStatus"), query.projection)
        assertEquals("only calendars the owner shows", "visible = 1", query.selection)
        assertEquals("begin ASC", query.sortOrder)
    }

    @Test fun aProviderThatRefusesOrFailsIsReportedNotGuessed() {
        shadowOf(app).grantPermissions(Manifest.permission.READ_CALENDAR)
        val calendar = AndroidContinuumCalendar(app)
        CalendarProvider.failure = SecurityException("Permission Denial")
        assertEquals(ContinuumCalendarRead.NotGranted, calendar.read(from, until))
        CalendarProvider.failure = IllegalStateException("Kalenderdatenbank gesperrt")
        assertEquals(ContinuumCalendarRead.Failed("Kalenderdatenbank gesperrt"), calendar.read(from, until))
        CalendarProvider.failure = null
        CalendarProvider.noCursor = true
        assertEquals(ContinuumCalendarRead.Failed("Kalender nicht verfügbar"), calendar.read(from, until))
        CalendarProvider.noCursor = false
        assertEquals(ContinuumCalendarRead.Events(emptyList()), calendar.read(from, until))
    }

    @Test fun theDialerIsOpenedWithTheNumberAndNoCallIsPlaced() {
        val device = AndroidContinuumDevice(app)
        assertNull(device.dial("+43 000 555 0103"))
        val started = shadowOf(app).nextStartedActivity
        assertEquals(Intent.ACTION_DIAL, started.action)
        assertEquals("tel", started.data!!.scheme)
        assertEquals("+430005550103", started.data!!.schemeSpecificPart)
        assertTrue("from the application context the dialer needs its own task", started.flags and Intent.FLAG_ACTIVITY_NEW_TASK != 0)
        assertEquals("die Nummer ist unvollständig", device.dial("12"))
        assertNull("nothing was started for an incomplete number", shadowOf(app).nextStartedActivity)
        assertEquals(Intent.ACTION_DIAL, AndroidContinuumDevice.dialIntent("0664123").action)
    }

    @Test fun batteryAndPermissionsAreReadTypedAndAMissingValueIsNull() {
        val device = AndroidContinuumDevice(app)
        app.sendStickyBroadcast(Intent(Intent.ACTION_BATTERY_CHANGED).putExtra(BatteryManager.EXTRA_LEVEL, 41)
            .putExtra(BatteryManager.EXTRA_SCALE, 50).putExtra(BatteryManager.EXTRA_STATUS, BatteryManager.BATTERY_STATUS_DISCHARGING))
        assertEquals(AndroidContinuumDevice.Battery(82, false), device.battery())
        app.sendStickyBroadcast(Intent(Intent.ACTION_BATTERY_CHANGED).putExtra(BatteryManager.EXTRA_LEVEL, 100)
            .putExtra(BatteryManager.EXTRA_SCALE, 100).putExtra(BatteryManager.EXTRA_STATUS, BatteryManager.BATTERY_STATUS_FULL)
            .putExtra(BatteryManager.EXTRA_PLUGGED, BatteryManager.BATTERY_PLUGGED_USB))
        assertEquals("full on the charger counts as charging", AndroidContinuumDevice.Battery(100, true), device.battery())
        app.sendStickyBroadcast(Intent(Intent.ACTION_BATTERY_CHANGED).putExtra(BatteryManager.EXTRA_LEVEL, 7)
            .putExtra(BatteryManager.EXTRA_SCALE, 0))
        assertEquals("an invalid scale is no percentage", AndroidContinuumDevice.Battery(null, null), device.battery())
        shadowOf(app).denyPermissions(Manifest.permission.RECORD_AUDIO)
        assertEquals(false, device.microphoneGranted())
        shadowOf(app).grantPermissions(Manifest.permission.RECORD_AUDIO)
        assertEquals(true, device.microphoneGranted())
        val storage = device.storage()
        assertTrue(storage.toString(), storage == null || storage.first in 0..storage.second)
        // The settings store keeps and removes plain values.
        val store = ContinuumBriefingPreferences(app)
        val settings = ContinuumBriefingSettings(store)
        settings.place = " Linz "
        settings.offerFromHour = 7
        assertEquals("Linz", ContinuumBriefingSettings(ContinuumBriefingPreferences(app)).place)
        assertEquals(7, ContinuumBriefingSettings(ContinuumBriefingPreferences(app)).offerFromHour)
        settings.place = null
        assertNull(ContinuumBriefingSettings(ContinuumBriefingPreferences(app)).place)
    }
}
