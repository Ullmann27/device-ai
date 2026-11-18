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
            "com.termux.permission.RUN_COMMAND"))
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
