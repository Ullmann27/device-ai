package at.heinz.deviceai.localmodel

import org.junit.Assert.*
import org.junit.Test

class LocalConversationBoundaryTest {
    private class Conversation(val messages: MutableList<String> = mutableListOf(), var closed: Boolean = false)

    @Test fun newPromptDropsNativeHistoryButToolContinuationKeepsCurrentTurn() {
        val old=Conversation(mutableListOf("private prior-session text"))
        val fresh=selectLocalConversation(old,true,{Conversation()},{it.closed=true})
        assertTrue(old.closed);assertNotSame(old,fresh);assertTrue(fresh.messages.isEmpty())
        fresh.messages.add("current tool proposal")
        val continued=selectLocalConversation(fresh,false,{error("No replacement during tool response")},{error("Must retain turn")})
        assertSame(fresh,continued);assertEquals(listOf("current tool proposal"),continued.messages)
        val next=selectLocalConversation(continued,true,{Conversation()},{it.closed=true})
        assertTrue(continued.closed);assertTrue(next.messages.isEmpty())
    }

    @Test fun absentContinuationAndFailedReleaseCannotUseStaleConversation() {
        try { selectLocalConversation<Conversation>(null,false,{Conversation()},{}) ; fail("Missing continuation accepted") }
        catch (_:IllegalStateException) {}
        var created=false
        try { selectLocalConversation(Conversation(),true,{created=true;Conversation()},{error("release failed")});fail("Release failure ignored") }
        catch (_:IllegalStateException) {}
        assertFalse(created)
    }
}
