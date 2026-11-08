package at.heinz.deviceai.cognitive

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalConversationSituationTrackerTest {
    @Test fun explicitReturnFindsEarlierTopicWithoutCopyingOldInstructions() {
        val t=LocalConversationSituationTracker()
        t.observeOwnerTurn("Beim Avatar müssen wir den Filament Surface Fehler prüfen.")
        t.observeOwnerTurn("Jetzt kümmern wir uns um die Android Thermik beim Prefill.")

        val hint=t.renderHint("Zurück zum Avatar Surface Fehler von vorhin.")
        assertTrue(hint.contains("SITUATIONSHINWEIS"))
        assertTrue(hint.contains("avatar"))
        assertFalse(hint.contains("müssen wir den Filament"))
        assertFalse(hint.contains("Werkzeug"))
    }

    @Test fun closeOldTopicsRemainAmbiguousInsteadOfGuessing() {
        val t=LocalConversationSituationTracker()
        t.observeOwnerTurn("Beim Renderer Shader ist noch ein Fehler offen.")
        t.observeOwnerTurn("Beim Avatar Shader ist noch ein anderer Fehler offen.")
        t.observeOwnerTurn("Jetzt reden wir über Thermik.")

        val hint=t.renderHint("Zurück zum Shader von vorhin.")
        assertTrue(hint.contains("mehrdeutig"))
        assertTrue(hint.contains("Frage gezielt nach"))
    }

    @Test fun ordinaryMessageDoesNotInjectSituationHint() {
        val t=LocalConversationSituationTracker()
        t.observeOwnerTurn("Wir arbeiten am Avatar.")
        assertEquals("",t.renderHint("Wie funktioniert ein Bloom Shader?"))
    }

    @Test fun resetRemovesAllTopicNavigationState() {
        val t=LocalConversationSituationTracker()
        t.observeOwnerTurn("Wir arbeiten am Avatar Surface Problem.")
        t.observeOwnerTurn("Jetzt an der Thermik.")
        t.reset()
        assertEquals("",t.renderHint("Zurück zum Avatar von vorhin."))
        assertNull(t.snapshot().currentThreadId)
        assertTrue(t.snapshot().threads.isEmpty())
    }
}
