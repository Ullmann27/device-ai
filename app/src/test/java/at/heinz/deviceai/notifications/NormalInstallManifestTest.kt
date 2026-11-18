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
