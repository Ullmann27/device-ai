package at.heinz.deviceai.sending

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Untrusted content stays data. A grant changes only through an owner tap created in the settings dialog, and the
 * send lane is reachable only from the owner's own turn (and from the mail order the work tools parsed from that
 * turn) – there is no tool, so no model output, web page, file, notification or screen text can start a send.
 */
class OwnerSendBoundaryTest {
    private val main = listOf(File("src/main/java/at/heinz/deviceai"), File("app/src/main/java/at/heinz/deviceai")).first { it.isDirectory }
    private val sources = main.walkTopDown().filter { it.isFile && it.extension == "kt" }.toList()
    private fun using(pattern: Regex) = sources.filter { pattern.containsMatchIn(it.readText()) }.map { it.name }.sorted()
    private fun source(name: String) = sources.single { it.name == name }.readText()

    @Test fun onlyTheSettingsDialogCanChangeAGrant() {
        assertEquals(listOf("OwnerSendSettingsDialog.kt"), using(Regex("OwnerSendOwnerTap\\(\\)")))
        assertEquals(listOf("OwnerSendSettingsDialog.kt"), using(Regex("grants\\.change\\(")))
        // In the dialog a grant is switched on only inside the confirmation's positive button.
        val dialog = source("OwnerSendSettingsDialog.kt")
        val on = dialog.indexOf("grants.change(channel, true, OwnerSendOwnerTap())")
        assertTrue(on > dialog.indexOf(".setPositiveButton(\"Ja, selbst senden\")") && dialog.indexOf(".setPositiveButton(\"Ja, selbst senden\")") > 0)
        assertEquals(1, Regex("grants\\.change\\(channel, true").findAll(dialog).count())
    }

    @Test fun theSendLaneIsEnteredOnlyFromTheOwnersOwnTurn() {
        assertEquals(listOf("AgentActivity.kt"), using(Regex("(?<!class )OwnerSendDirector\\(")))
        assertEquals(listOf("AgentActivity.kt"), using(Regex("ownerSend\\.(?:handle|mail)\\(")))
        val activity = source("AgentActivity.kt")
        // The owner-turn intake, the same owner turn as the answer to this lane's open question (changed 19.11.2026:
        // open questions are served before the whole-sentence lanes), the settings button for the diagnosis.
        assertEquals(3, Regex("ownerSend\\.handle\\(").findAll(activity).count())
        assertTrue(activity.contains("ownerSend.handle(request)) return"))
        assertTrue(activity.contains("awaiting = { ownerSend.awaitingAnswer }) { ownerSend.handle(it) }"))
        assertTrue(activity.contains("ownerSend.handle(\"Sende-Diagnose\")"))
        assertEquals(1, Regex("ownerSend\\.mail\\(").findAll(activity).count())
        // The only caller of the mail hand-over is the work tools' own mail order.
        assertEquals(listOf("AgentActivity.kt", "OwnerWorkTools.kt"), using(Regex("sendMailYourself\\(")))
        assertEquals(1, Regex("host\\.sendMailYourself\\(").findAll(source("OwnerWorkTools.kt")).count())
    }

    @Test fun noToolAndNoModelLaneCanSendAMessage() {
        val registry = source("ToolRegistry.kt")
        assertFalse(Regex("ToolSpec\\(\"(?:send|whatsapp|mail)[a-z_]*\"").containsMatchIn(registry))
        // Nothing outside the send package and the Activity wiring presses the send lane's device.
        assertEquals(listOf("AgentActivity.kt", "AndroidOwnerSend.kt"), using(Regex("AndroidOwnerSendDevice\\(")))
        assertEquals(listOf("OwnerSendDirector.kt"), using(Regex("(?<!class )OwnerSendFlow\\(")))
        // The old computer lane keeps refusing every send-like control.
        val guard = source("LiveComputerActions.kt")
        assertTrue(guard.contains("val SEND_TOOLS: Set<String> = emptySet()"))
        assertTrue(source("OwnerComputerTaskGrant.kt").contains("!SendActionGuard.isSendLike(target.label, target.viewId)"))
    }

    @Test fun theAccessibilityDescriptionTellsTheOwnerTheTruthAboutSending() {
        val strings = listOf(File("src/main/res/values/continuum_screen_strings.xml"),
            File("app/src/main/res/values/continuum_screen_strings.xml")).first { it.isFile }.readText()
        assertTrue(strings.contains("Selbst senden") && strings.contains("Dauerfreigabe"))
        assertTrue(strings.contains("Ohne diese Freigabe kein automatischer Versand"))
    }
}
