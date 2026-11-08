package at.heinz.deviceai.cognitive

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ConversationSituationModelTest {
    @Test fun explicitThreadReturnUsesTopicCuesNotRawChatHistory() {
        val m=ConversationSituationModel()
        m.nextTurn()
        m.openThread("lumo","Lumo Kart Physik",listOf("lumo kart","driften","fahrphysik"))
        m.addOpenQuestion("lumo","Warum driftet das Kart?")
        m.nextTurn()
        m.openThread("thermal","Android Thermik",listOf("android thermik","fold7","hitze"))
        m.nextTurn()
        m.pauseActive()
        val r=m.resolveReturn("Wie lösen wir jetzt das Driften von vorhin?")
        assertTrue(r is ConversationSituationModel.Resolution.Resolved)
        assertEquals("lumo",(r as ConversationSituationModel.Resolution.Resolved).thread.id)
        assertEquals("lumo",m.snapshot().currentThreadId)
    }

    @Test fun unrelatedQuestionCannotJumpToRecentPausedThreadByRecencyAlone() {
        val m=ConversationSituationModel()
        m.openThread("thermal","Android Thermik",listOf("android thermik","hitze"),atTurn=1)
        m.pauseActive()
        val r=m.resolveReturn("Wie wird morgen das Wetter?",atTurn=2)
        assertTrue(r is ConversationSituationModel.Resolution.NoMatch)
        assertNull(m.snapshot().currentThreadId)
    }

    @Test fun closeCandidatesAskInsteadOfGuessing() {
        val m=ConversationSituationModel()
        m.openThread("a","Renderer",listOf("shader","layout"),atTurn=1)
        m.openThread("b","Avatar",listOf("shader","kopf"),atTurn=2)
        m.pauseActive()
        val r=m.resolveReturn("Was war mit dem Shader?",atTurn=3)
        assertTrue(r is ConversationSituationModel.Resolution.Ambiguous)
        assertTrue(m.snapshot().unresolvedReference!!.contains("Shader"))
    }

    @Test fun explicitScopeIsEphemeralAndResetClearsEverything() {
        val m=ConversationSituationModel()
        m.setExplicitScope(project="Continuum",task="Verifier",goal="Falsche Aussagen abfangen")
        m.openThread("verifier","Verifier",listOf("claim verifier"),atTurn=1)
        val before=m.snapshot()
        assertEquals("Continuum",before.currentProject)
        assertEquals("Verifier",before.currentTask)
        m.reset()
        val after=m.snapshot()
        assertNull(after.currentProject)
        assertNull(after.currentThreadId)
        assertTrue(after.threads.isEmpty())
    }

    @Test fun resolvedOldThreadIsEvictedBeforePausedThreadAtCapacity() {
        val m=ConversationSituationModel(maxThreads=2)
        m.openThread("old","Alt",listOf("alt"),atTurn=1)
        m.resolveThread("old")
        m.openThread("keep","Wichtig",listOf("wichtig"),atTurn=2)
        m.pauseActive()
        m.openThread("new","Neu",listOf("neu"),atTurn=3)
        val ids=m.snapshot().threads.map { it.id }.toSet()
        assertTrue("old" !in ids)
        assertTrue("keep" in ids)
        assertTrue("new" in ids)
    }
}
