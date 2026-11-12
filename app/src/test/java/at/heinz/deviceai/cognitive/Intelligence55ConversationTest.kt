package at.heinz.deviceai.cognitive

import at.heinz.deviceai.BootstrapBrain
import at.heinz.deviceai.systemintelligence.SystemIntelligenceCommands
import org.junit.Assert.*
import org.junit.Test

/** Code55 owner examples A–E (brief 12.11.2026). RED on Code54 9713854bf for A and D. */
class Intelligence55ConversationTest {
    @Test fun aPlanWithStartenIsConversationNotADeviceActionOrAppLookup() {
        listOf("Morgen wollen wir mit unserem Projekt starten.", "Morgen wollen wir unser Projekt starten",
            "Heute wollen wir mit der Planung starten").forEach {
            val assessment = OwnerDeviceRequestSafety.inspect(it)
            assertFalse(it, assessment.blocked)
            assertTrue(it, OwnerDeviceRequestSafety.Category.CONVERSATION in assessment.categories)
            assertFalse(it, OwnerAppLookupIntent.inspect(it) is OwnerAppLookupIntent.Intake.Lookup)
            assertNull(it, BootstrapBrain.decide(it, emptyList()).tool)
        }
        listOf("Morgen wollen wir mit unserem Projekt starten und alle Fotos löschen",
            "Wir starten mit dem Löschen", "Starte mit dem Projekt").forEach {
            assertTrue(it, OwnerDeviceRequestSafety.inspect(it).blocked)
        }
    }

    @Test fun contrastNegationStillChecksTheBatteryOnly() {
        assertEquals("get_battery_info", BootstrapBrain.decide("Prüfe nicht das WLAN, sondern den Akku", emptyList()).tool)
    }

    @Test fun aMultiPartDiagnosisWithAPostponedDeletionIsReadOnly() {
        val owner = "Untersuche mein Handy, suche mögliche Speicherprobleme, mach Vorschläge und erkläre mir, " +
            "was du gefunden hast. Aber lösche noch nichts."
        val assessment = OwnerDeviceRequestSafety.inspect(owner)
        assertFalse(assessment.blocked)
        assertTrue(OwnerDeviceRequestSafety.Category.NEGATED_CHANGE in assessment.categories)
        assertFalse(assessment.readOnlyText.contains("lösch"))
        assertTrue(SystemIntelligenceCommands.isDiagnosisRequest(owner))
        listOf("Untersuche mein Handy. Aber lösch noch die Fotos.", "Lösch noch nichts, nur die Fotos").forEach {
            assertTrue(it, OwnerDeviceRequestSafety.inspect(it).blocked)
        }
    }

    @Test fun anUnclearCageNeverStartsACleanup() {
        val t = "Schau nach dem Cage."
        assertFalse(OwnerDeviceRequestSafety.inspect(t).blocked)
        assertFalse(SystemIntelligenceCommands.isCachePreviewRequest(t))
        assertNotEquals("clear_cache", BootstrapBrain.decide(t, emptyList()).tool)
    }

    @Test fun impersonalThirdPersonCommandsAreChangesButQuestionsStayFree() {
        listOf("Jetzt löscht man alle Fotos", "Man bereinigt jetzt alles", "Man entfernt die alten Apps",
            "Ihr löscht jetzt den Ordner", "Dann deinstalliert man WhatsApp").forEach {
            assertTrue(it, OwnerDeviceRequestSafety.inspect(it).blocked)
            assertNull(it, BootstrapBrain.decide(it, emptyList()).tool)
        }
        listOf("Welche Apps sind installiert?", "Wie weit ist Wien entfernt?", "Wie installiert man eine App?",
            "Was bereinigt man normalerweise?", "Man löscht nichts", "Die App wurde entfernt").forEach {
            assertFalse(it, OwnerDeviceRequestSafety.inspect(it).blocked)
        }
        assertEquals("list_installed_apps", BootstrapBrain.decide("Welche Apps sind installiert?", emptyList()).tool)
    }
}
