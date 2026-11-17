package at.heinz.deviceai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExpertModeConsentTest {
    private fun plan(vararg steps: CommandStep, title: String = "Plan") =
        CommandPlan(title, steps.joinToString { it.label }, steps.toList())

    @Test
    fun confirmationTextShowsTheExactShellCommandNotOnlyTheLabel() {
        val hidden = "screencap -p /sdcard/Download/device-ai.png"
        val message = ExpertModeConsent.confirmMessage(
            plan(CommandStep("Screenshot speichern", hidden, Risk.CONFIRM))
        )
        assertTrue(message, message.contains(hidden))
        assertTrue(message, message.contains("Screenshot speichern"))
        assertTrue(message, message.startsWith("Geplante Aktionen:"))
        assertTrue(message, message.contains("Ausführen?"))
    }

    @Test
    fun destructiveDialogsShowTheExactCommandAndReuseAgentActivityWording() {
        val command = "pm uninstall com.example.app"
        val first = ExpertModeConsent.destructiveFirstMessage(
            plan(CommandStep("App deinstallieren: Beispiel", command, Risk.DESTRUCTIVE))
        )
        val strong = ExpertModeConsent.destructiveStrongMessage(
            plan(CommandStep("App deinstallieren: Beispiel", command, Risk.DESTRUCTIVE))
        )
        assertTrue(first, first.contains(command))
        assertTrue(strong, strong.contains(command))
        assertTrue(strong, strong.startsWith(ExpertModeConsent.STRONG_PREFIX))
        assertTrue(strong, strong.contains(first))
        assertEquals("Kritische Aktion bestätigen", ExpertModeConsent.DESTRUCTIVE_TITLE)
        assertEquals("Weiter prüfen", ExpertModeConsent.DESTRUCTIVE_CONTINUE)
        assertEquals("Starke Bestätigung erforderlich", ExpertModeConsent.STRONG_TITLE)
        assertEquals("Destruktiv ausführen", ExpertModeConsent.STRONG_CONFIRM)
        assertEquals("Diese Aktion kann Daten verändern oder löschen.", ExpertModeConsent.STRONG_PREFIX)
    }

    @Test
    fun planRiskFollowsTheHighestStepIncludingToolRegistryBindings() {
        val screenshot = CommandStep("Screenshot speichern", "screencap -p out.png", ToolRegistry.risk("take_screenshot"))
        val launch = CommandStep("App starten: Demo", "monkey -p com.example 1", ToolRegistry.risk("launch_app"))
        val uninstall = CommandStep("App deinstallieren: Demo", "pm uninstall com.example", ToolRegistry.risk("uninstall_app"))
        val shell = CommandStep("Shell-Befehl ausführen", "rm -f /sdcard/Download/x", ToolRegistry.risk("shell"))
        assertEquals(Risk.CONFIRM, screenshot.risk)
        assertEquals(Risk.CONFIRM, launch.risk)
        assertEquals(Risk.CONFIRM, ToolRegistry.risk("take_screenshot"))
        assertEquals(Risk.CONFIRM, ToolRegistry.risk("launch_app"))
        assertEquals(Risk.DESTRUCTIVE, uninstall.risk)
        assertEquals(Risk.DESTRUCTIVE, shell.risk)
        assertEquals(Risk.CONFIRM, plan(screenshot, launch).risk)
        assertEquals(Risk.DESTRUCTIVE, plan(screenshot, uninstall).risk)
        assertFalse(ExpertModeConsent.confirmMessage(plan(screenshot)).contains("• Screenshot speichern\n\n"))
    }
}
