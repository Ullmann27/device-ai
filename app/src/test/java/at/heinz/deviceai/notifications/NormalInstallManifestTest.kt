package at.heinz.deviceai.notifications

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Owner finding (Fold7, Code64, 18.11.2026): Google Play Protect stopped the normal installation from browser and
 * file manager ("App wurde zum Schutz deines Geräts blockiert … Zugriff auf sensible Daten"). Its published rule
 * names SMS read/receive permissions, a notification listener and an accessibility service. The notification
 * listener was the only such declaration added since the builds that installed normally, so the normal owner build
 * no longer declares it; it stays available as an explicit build option. This test keeps it from coming back
 * unnoticed. (The accessibility service is older, is needed for device control and stays.)
 */
class NormalInstallManifestTest {
    private fun source(relative: String): String =
        listOf(File(relative), File("app", relative)).first { it.isFile }.readText()

    @Test fun theNormalBuildDeclaresNoNotificationListenerAndNoSmsAccess() {
        // Comments explain the absence; only real declarations count.
        val manifest = source("src/main/AndroidManifest.xml").replace(Regex("<!--[\\s\\S]*?-->"), "")
        assertFalse(manifest.contains("BIND_NOTIFICATION_LISTENER_SERVICE"))
        assertFalse(manifest.contains("NotificationListenerService"))
        assertFalse(manifest.contains("android.permission.READ_SMS"))
        assertFalse(manifest.contains("android.permission.RECEIVE_SMS"))
    }

    /**
     * Owner send orders („Schreib Anna auf WhatsApp …“) resolve a name in the address book. READ_CONTACTS is the one
     * permission added for them and is allowed; it is not in Play Protect's blocking rule. Sending itself goes through
     * WhatsApp and Gmail, so no SMS, call, account or contact-write permission may come with it. The exact set is
     * pinned: a further permission has to be added here deliberately.
     */
    @Test fun readContactsIsTheOnlyPermissionAddedForOwnerSendOrders() {
        val manifest = source("src/main/AndroidManifest.xml").replace(Regex("<!--[\\s\\S]*?-->"), "")
        val declared = Regex("<uses-permission\\s+android:name=\"([^\"]+)\"").findAll(manifest).map { it.groupValues[1] }.toList()
        assertTrue(declared.count { it == "android.permission.READ_CONTACTS" } == 1)
        listOf("WRITE_CONTACTS", "SEND_SMS", "READ_SMS", "RECEIVE_SMS", "RECEIVE_MMS", "READ_CALL_LOG", "CALL_PHONE",
            "GET_ACCOUNTS", "READ_PHONE_STATE", "BIND_NOTIFICATION_LISTENER_SERVICE").forEach {
            assertFalse(it, declared.contains("android.permission.$it"))
        }
        assertTrue("unexpected permission set: $declared", declared.toSet() == setOf(
            "android.permission.INTERNET", "android.permission.RECORD_AUDIO", "android.permission.CAMERA",
            "android.permission.ACCESS_NETWORK_STATE", "android.permission.RECEIVE_BOOT_COMPLETED",
            "android.permission.POST_NOTIFICATIONS", "android.permission.FOREGROUND_SERVICE",
            "android.permission.FOREGROUND_SERVICE_MEDIA_PROJECTION", "android.permission.QUERY_ALL_PACKAGES",
            "android.permission.REQUEST_INSTALL_PACKAGES", "android.permission.READ_CONTACTS",
            // Added deliberately for the document desk (18.11.2026), see the test below.
            "android.permission.MANAGE_EXTERNAL_STORAGE",
            // Added deliberately for the daily briefing (19.11.2026), see readCalendarIsTheOnlyPermissionAddedForTheBriefing.
            "android.permission.READ_CALENDAR",
            "com.termux.permission.RUN_COMMAND"))
    }

    /**
     * Daily briefing („Was steht heute an?“, „Welche Termine habe ich heute?“, owner goal 19.11.2026): today's and
     * tomorrow morning's appointments come from the device calendar. READ_CALENDAR is the one permission added for
     * the briefing lane: a runtime permission Android asks the owner for, after a plain explanation, the first time
     * he asks for appointments. It is not in Play Protect's blocking rule (SMS, notification listener). The app can
     * only read: no calendar write, no call, no location and no SMS permission comes with it – „Ruf Anna an“ opens
     * the dialer (ACTION_DIAL) and the weather uses a place the owner names. Whether Play Protect accepts a normally
     * installed build with this declaration is NOT verified by this test – only the device can show that.
     */
    @Test fun readCalendarIsTheOnlyPermissionAddedForTheBriefing() {
        val manifest = source("src/main/AndroidManifest.xml").replace(Regex("<!--[\\s\\S]*?-->"), "")
        val declared = Regex("<uses-permission\\s+android:name=\"([^\"]+)\"").findAll(manifest).map { it.groupValues[1] }.toList()
        assertTrue(declared.count { it == "android.permission.READ_CALENDAR" } == 1)
        listOf("WRITE_CALENDAR", "CALL_PHONE", "READ_CALL_LOG", "READ_PHONE_STATE", "ACCESS_FINE_LOCATION",
            "ACCESS_COARSE_LOCATION", "ACCESS_BACKGROUND_LOCATION", "SEND_SMS", "READ_SMS", "RECEIVE_SMS",
            "SCHEDULE_EXACT_ALARM", "USE_EXACT_ALARM", "SYSTEM_ALERT_WINDOW").forEach {
            assertFalse(it, declared.contains("android.permission.$it"))
        }
        // The calendar boundary only queries; the dialer is opened, a call is never placed.
        val boundary = source("src/main/java/at/heinz/deviceai/briefing/AndroidContinuumBriefing.kt")
        assertTrue(boundary.contains("CalendarContract.Instances.CONTENT_URI"))
        assertTrue(boundary.contains("Intent(Intent.ACTION_DIAL"))
        listOf("ACTION_CALL", ".insert(", ".update(", ".delete(", "CalendarContract.Events.CONTENT_URI", "WRITE_CALENDAR").forEach {
            assertFalse(it, boundary.contains(it))
        }
    }

    /**
     * Document desk („Öffne das PDF Argon4“, owner goal 18.11.2026): finding a PDF by name in the shared storage,
     * offline and inside the app, needs Android's „Zugriff auf alle Dateien“. It is the one permission added for it:
     * a special access the owner switches on himself in Android's settings and can switch off there – it is never
     * granted by an install or a dialog of this app. No media or legacy storage permission comes with it, and the
     * permissions Play Protect's blocking rule names (SMS, notification listener) stay absent. Whether Play Protect
     * accepts a normally installed build with this declaration is NOT verified by this test – only the device can show
     * that (see docs/handover/ARGON4-ABLAUF-GERAETECHECKLISTE-20261118.md).
     */
    @Test fun allFilesAccessIsTheOnlyPermissionAddedForTheDocumentDesk() {
        val manifest = source("src/main/AndroidManifest.xml").replace(Regex("<!--[\\s\\S]*?-->"), "")
        val declared = Regex("<uses-permission\\s+android:name=\"([^\"]+)\"").findAll(manifest).map { it.groupValues[1] }.toList()
        assertTrue(declared.count { it == "android.permission.MANAGE_EXTERNAL_STORAGE" } == 1)
        listOf("READ_EXTERNAL_STORAGE", "WRITE_EXTERNAL_STORAGE", "READ_MEDIA_IMAGES", "READ_MEDIA_VIDEO", "READ_MEDIA_AUDIO",
            "READ_MEDIA_VISUAL_USER_SELECTED", "ACCESS_MEDIA_LOCATION", "MANAGE_MEDIA", "MANAGE_DOCUMENTS").forEach {
            assertFalse(it, declared.contains("android.permission.$it"))
        }
        assertFalse(manifest.contains("requestLegacyExternalStorage"))
        // The grant is asked for in Android's own settings page and checked before every walk; no Shizuku is involved.
        val library = source("src/main/java/at/heinz/deviceai/OwnerDeviceLibrary.kt")
        assertTrue(library.contains("Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION"))
        assertTrue(library.contains("Environment.isExternalStorageManager()"))
        assertFalse(library.contains("ShizukuBridge"))
    }

    @Test fun theListenerIsMergedOnlyOnExplicitRequest() {
        val overlay = source("src/notificationAccess/AndroidManifest.xml")
        assertTrue(overlay.contains("at.heinz.deviceai.notifications.ContinuumNotificationListener"))
        assertTrue(overlay.contains("android.permission.BIND_NOTIFICATION_LISTENER_SERVICE"))
        val build = source("build.gradle.kts")
        assertTrue(build.contains("providers.gradleProperty(\"continuumNotificationAccess\").orNull == \"true\""))
        assertTrue(build.contains("manifest.srcFile(\"src/notificationAccess/AndroidManifest.xml\")"))
    }

    @Test fun theSettingsSayHonestlyWhenAnnouncementsAreNotPartOfTheInstallation() {
        val activity = source("src/main/java/at/heinz/deviceai/AgentActivity.kt")
        assertTrue(activity.contains("NotificationAnnouncementSettings.isSupported(this)"))
        assertTrue(activity.contains("store.UNSUPPORTED_NOTICE"))
        assertTrue(NotificationAnnouncementSettings.UNSUPPORTED_NOTICE.contains("nicht enthalten"))
    }
}
