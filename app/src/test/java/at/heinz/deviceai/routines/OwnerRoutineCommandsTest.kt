package at.heinz.deviceai.routines

import org.junit.Assert.*
import org.junit.Test

class OwnerRoutineCommandsTest {
    @Test fun concreteIntervalAndConditionRemainBoundToTheApprovedGoal() {
        val parsed = OwnerRoutineCommands.parse("Routine alle 15 Minuten: Akku < 20 %") as OwnerRoutineCommand.Create
        val routine = parsed.draft.routine("battery", 100)
        assertEquals(OwnerRoutineTarget.LOW_BATTERY, parsed.draft.target)
        assertEquals(RoutineTrigger.Every(900_000, 100), routine.trigger)
        assertEquals(setOf("get_battery_info"), routine.allowedTools)
        assertEquals(OwnerRoutineTarget.LOW_BATTERY, OwnerRoutineTarget.from(routine))
        assertNull(OwnerRoutineTarget.from(routine.copy(allowedTools = setOf("list_files"))))
    }

    @Test fun unsupportedExtraWorkAndConditionsNeverBecomeSimpleBatteryReads() {
        for (goal in listOf("Prüfe Akku und lösche Downloads", "Prüfe Akku nur wenn ich schlafe",
            "Prüfe Akku über 40 %", "Prüfe Akku und sende WhatsApp", "Prüfe nicht den Akku",
            "Aktualisiere alle Apps", "Lies private Botnotizen")) {
            assertTrue(goal, OwnerRoutineCommands.parse("Routine alle 15 Minuten: $goal") is OwnerRoutineCommand.Unsupported)
        }
    }

    @Test fun onlyTheExplicitAppStartEventAndLegalIntervalsAreAdmitted() {
        val request = OwnerRoutineCommands.parse("Routine beim Appstart: Prüfe Speicher") as OwnerRoutineCommand.Create
        assertEquals(RoutineTrigger.OnEvent(OwnerRoutineDraft.APP_START), request.draft.routine("storage", 20).trigger)
        assertTrue(OwnerRoutineCommands.parse("Routine alle 14 Minuten: Prüfe Akku") is OwnerRoutineCommand.Unsupported)
        assertTrue(OwnerRoutineCommands.parse("Routine beim Entsperren: Prüfe Akku") is OwnerRoutineCommand.Unsupported)
        assertNull(OwnerRoutineCommands.parse("Was ist eine Routine?"))
    }

    @Test fun actualReadOnlyPlanScopesAreFreshAndLimited() {
        val target = OwnerRoutineTarget.STORAGE_DOWNLOADS
        assertEquals(listOf("get_storage_info", "list_files"), target.plan().plannedToolNames)
        assertNotSame(target.plan(), target.plan())
        assertTrue(target.requiresShizuku)
        assertEquals(setOf("list_installed_apps"), OwnerRoutineTarget.APPS.allowedTools)
        assertFalse(OwnerRoutineTarget.BATTERY.requiresShizuku)
    }

    @Test fun lowBatteryConditionRejectsUnverifiedOrNonmatchingMeasurements() {
        val target = OwnerRoutineTarget.LOW_BATTERY
        fun result(percent: Int) = "Akku prüfen:\nAktueller Akkustand: $percent % (Android-Batteriedienst).\nLadezustand: entlädt sich."
        assertFalse(target.reports(result(50)))
        assertFalse(target.reports(result(25)))
        assertFalse(target.reports(result(20)))
        assertTrue(target.reports(result(19)))
        assertTrue(target.reports(result(18)))
        assertThrows(IllegalArgumentException::class.java) { target.reports("Akku vielleicht 19 %") }
        assertThrows(IllegalArgumentException::class.java) { target.reports(result(101)) }
        assertThrows(IllegalArgumentException::class.java) { target.reports(result(19) + "\n" + result(18)) }
    }

    @Test fun storageTimeDoesNotInventChangeButOriginalResultRemainsAvailable() {
        val target = OwnerRoutineTarget.STORAGE
        val original = "Speicher prüfen:\nSpeicherbeobachtung v1\nFür die App nutzbar: 200 Byte\nZeitpunkt: 10"
        assertEquals(target.digest(original), target.digest(original.replace("Zeitpunkt: 10", "Zeitpunkt: 20")))
        assertNotEquals(target.digest(original), target.digest(original.replace("200 Byte", "199 Byte")))
        assertTrue(original.endsWith("Zeitpunkt: 10"))
        assertThrows(IllegalArgumentException::class.java) { target.digest("Keine belegte Speichermessung") }
    }
}
