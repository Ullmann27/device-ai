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
    @Test fun emptyAndOversizedDraftsFailClosed() {
        assertNull(ContinuumWorkerChatRouter.parse("Worker:"))
        assertNull(ContinuumWorkerChatRouter.parse("Worker: " + "X".repeat(401)))
    }
}