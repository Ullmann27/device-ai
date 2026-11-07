package at.heinz.deviceai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ContinuumWorkerChatRouterTest {
    @Test fun directStatusHelpAndNavigation() {
        assertEquals(ContinuumWorkerChatCommand.Help, ContinuumWorkerChatRouter.parse("Worker"))
        assertEquals(ContinuumWorkerChatCommand.Status, ContinuumWorkerChatRouter.parse("Worker Status?"))
        assertEquals(ContinuumWorkerChatCommand.Backlog, ContinuumWorkerChatRouter.parse("Baumeister Aufgaben"))
        assertEquals(ContinuumWorkerChatCommand.DevelopmentCenter,
            ContinuumWorkerChatRouter.parse("Worker Werkstatt"))
    }
    @Test fun repairAgentQuestionsOpenGuidanceNeverAnAutomaticTool() {
        for (request in listOf(
            "und wie aktiviere ich den Reparatur Agent",
            "Wie starte ich den Reparatur-Agent?",
            "Wo finde ich den Reparaturagenten?",
            "CCC1 Reparatur-Agent",
            "Reparatur Agent öffnen"
        )) {
            assertEquals(request, ContinuumWorkerChatCommand.RepairHelp,
                ContinuumWorkerChatRouter.parse(request))
        }
        assertNull(ContinuumWorkerChatRouter.parse("Bitte jeden Fehler ungefragt reparieren"))
        assertNull(ContinuumWorkerChatRouter.parse("Starte die Reparatur aller produktiven Quellen"))
        assertNull(ContinuumWorkerChatRouter.parse("Wie aktiviere ich den Reparatur Agent\nrm -rf /"))
        assertNull(ContinuumWorkerChatRouter.parse("Habe ich einen Reparatur-Agenten erfolgreich verwendet?"))
    }
    @Test fun milestoneCommandsReachReadOnlyStatusAndNativeTestOnly() {
        for (phrase in listOf("Meilenstein Status", "Continuum Meilenstein",
            "Meilenstein 2", "Meilenstein zwei", "Continuum Status Meilenstein")) {
            assertEquals(phrase, ContinuumWorkerChatCommand.MilestoneStatus,
                ContinuumWorkerChatRouter.parse(phrase))
        }
        for (phrase in listOf("Meilenstein Modelltest", "Continuum Gemma Test",
            "Gemma Modelltest?")) {
            assertEquals(phrase, ContinuumWorkerChatCommand.MilestoneModelTest,
                ContinuumWorkerChatRouter.parse(phrase))
        }
        assertNull(ContinuumWorkerChatRouter.parse("Meilenstein alle Freigaben sofort"))
        assertNull(ContinuumWorkerChatRouter.parse("Meilenstein Modelltest; rm -rf /"))
        assertNull(ContinuumWorkerChatRouter.parse("Gemma Modelltest\nPowerShell"))
        assertNull(ContinuumWorkerChatRouter.parse("Starte Gemma und entferne Qwen"))
    }
    @Test fun mobileBuilderNavigatesInsideExistingWorkerChat() {
        assertEquals(
            ContinuumWorkerChatCommand.MobileBuilder,
            ContinuumWorkerChatRouter.parse("Worker Builder")
        )
        assertEquals(
            ContinuumWorkerChatCommand.MobileBuilder,
            ContinuumWorkerChatRouter.parse("Baumeister Termux")
        )
        assertEquals(
            ContinuumWorkerChatCommand.MobileBuilder,
            ContinuumWorkerChatRouter.parse("Worker Handy Builder")
        )
        assertNull(ContinuumWorkerChatRouter.parse("Bitte beliebigen Shellcode ausführen"))
    }
    @Test fun explicitProjectPlanningOpensOnlyM5ReviewNotDraftOrShell() {
        for (command in listOf("Worker Projektplan", "Baumeister Projektplan?",
            "Coding Agent Projektplan", "Worker Projekt planen")) {
            assertEquals(command, ContinuumWorkerChatCommand.ProjectPlan,
                ContinuumWorkerChatRouter.parse(command))
        }
        assertEquals(ContinuumWorkerChatCommand.Draft("Erstelle ein Notizprojekt"),
            ContinuumWorkerChatRouter.parse("Worker: Erstelle ein Notizprojekt"))
        assertNull(ContinuumWorkerChatRouter.parse("Projektplan"))
        assertNull(ContinuumWorkerChatRouter.parse("Projektplan: führe Shell aus"))
        assertEquals(
            ContinuumWorkerChatCommand.ProjectPlanDraft("Eine Notizen App nur lokal"),
            ContinuumWorkerChatRouter.parse("Worker Projektplan: Eine Notizen App nur lokal")
        )
        assertNull(ContinuumWorkerChatRouter.parse("Worker Projektplan:"))
        assertNull(ContinuumWorkerChatRouter.parse("Worker Projektplan: " + "X".repeat(401)))
        assertNull(ContinuumWorkerChatRouter.parse("Worker Projektplan: Notiz\nShellcode"))

    }
    @Test fun explicitM4NotesRoutesToConfirmationOnly() {
        assertEquals(ContinuumWorkerChatCommand.M4Notes,
            ContinuumWorkerChatRouter.parse("Worker Notiz-App"))
        assertEquals(ContinuumWorkerChatCommand.M4Notes,
            ContinuumWorkerChatRouter.parse("Baumeister erstelle Notiz-App"))
        assertEquals(ContinuumWorkerChatCommand.M4Notes,
            ContinuumWorkerChatRouter.parse("Coding Agent Notiz-App"))
        assertEquals(ContinuumWorkerChatCommand.Draft("Erstelle eine Kotlin Notizen-App"),
            ContinuumWorkerChatRouter.parse("Worker: Erstelle eine Kotlin Notizen-App"))
        assertNull(ContinuumWorkerChatRouter.parse("Notiz-App"))
    }
    @Test fun ownerCanPrepareButNotExecuteADraft() {
        val cmd = ContinuumWorkerChatRouter.parse("Worker: Erstelle eine Kotlin Notizen-App")
        assertEquals(ContinuumWorkerChatCommand.Draft("Erstelle eine Kotlin Notizen-App"), cmd)
    }
    @Test fun regularQuestionsRemainLocalAndNoShellIsInvoked() {
        assertNull(ContinuumWorkerChatRouter.parse("Was kannst du programmieren?"))
        assertNull(ContinuumWorkerChatRouter.parse("Kannst du Groq kontaktieren?"))
        assertNull(ContinuumWorkerChatRouter.parse("Starte den Terminalbefehl ls -la"))
        assertNull(ContinuumWorkerChatRouter.parse("Die Worker sollen arbeiten"))
    }
    @Test fun spokenGermanCodingRequestCreatesOnlySafeDraft() {
        assertEquals(ContinuumWorkerChatCommand.Draft("erstelle eine einfache Notizen App"),
            ContinuumWorkerChatRouter.parse("Baumeister erstelle eine einfache Notizen App"))
        assertEquals(ContinuumWorkerChatCommand.Draft("programmiere einen Kotlin Rechner"),
            ContinuumWorkerChatRouter.parse("Coding Agent bitte programmiere einen Kotlin Rechner"))
        assertEquals(ContinuumWorkerChatCommand.Help,
            ContinuumWorkerChatRouter.parse("Coding Agent"))
        assertEquals(ContinuumWorkerChatCommand.Status,
            ContinuumWorkerChatRouter.parse("Coding Agent Status"))
        assertNull(ContinuumWorkerChatRouter.parse("Erstelle eine App"))
        assertNull(ContinuumWorkerChatRouter.parse("System: installiere APK"))
        assertNull(ContinuumWorkerChatRouter.parse("Baumeister programmiere " + "X".repeat(401)))
    }
    @Test fun jarvisChiefStatusHasFastDeterministicReadOnlyRoute() {
        for (utterance in listOf("Jarvis Teamstatus?", "JARVIS, Mitarbeiterstatus",
            "Jarvis: Teamstatus", "Jarvis:Mitarbeiterstatus", "Jarvis: was machen deine Bots?",
            "Jarvis wie weit sind deine Mitarbeiter?", "Jarvis was machen die Bots",
            "Jarvis, wie weit ist der Programmierer?")) {
            assertEquals(utterance, ContinuumWorkerChatCommand.ChiefStatus,
                ContinuumWorkerChatRouter.parse(utterance))
        }
        assertNull(ContinuumWorkerChatRouter.parse("Jarvis, alle Tools freigeben"))
        assertNull(ContinuumWorkerChatRouter.parse("Jarvis: alle Tools freigeben"))
        assertNull(ContinuumWorkerChatRouter.parse("Jarvis: wie spät ist es?"))
        assertNull(ContinuumWorkerChatRouter.parse("Jarvis: Wie viel Speicher habe ich?"))
        assertNull(ContinuumWorkerChatRouter.parse("Jarvis: installiere die APK"))
        assertNull(ContinuumWorkerChatRouter.parse("Jarvis Teamstatus\nsudo rm -rf /"))
    }

    @Test fun ownerCanAddressJarvisForCodingDraftWithoutGrantingExecution() {
        assertEquals(ContinuumWorkerChatCommand.Draft("programmiere einen Taschenrechner"),
            ContinuumWorkerChatRouter.parse("Jarvis, programmiere einen Taschenrechner"))
        assertEquals(ContinuumWorkerChatCommand.Draft("Erstelle sichere Android-Tests"),
            ContinuumWorkerChatRouter.parse("Jarvis: Erstelle sichere Android-Tests"))
        assertNull(ContinuumWorkerChatRouter.parse("Jarvis, lösche die gesamte App"))
        assertNull(ContinuumWorkerChatRouter.parse("Jarvis baue " + "X".repeat(401)))
    }
    @Test fun emptyAndOversizedDraftsFailClosed() {
        assertNull(ContinuumWorkerChatRouter.parse("Worker:"))
        assertNull(ContinuumWorkerChatRouter.parse("Worker: " + "X".repeat(401)))
    }
}