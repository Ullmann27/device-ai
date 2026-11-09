package at.heinz.deviceai.avatar

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AvatarSentenceAccentPolicyTest {
    @Test fun titlesBeforeNamesDoNotCreateFalseSentenceNods() {
        for (title in listOf("Dr", "Prof", "Mag", "Ing", "DI", "Dipl", "Mr", "Mrs", "Ms")) {
            val text = "$title. Müller antwortet."
            assertTrue(AvatarSentenceAccentPolicy.suppress(text, 0, title.length - 1, title.length + 2))
            assertFalse(AvatarSentenceAccentPolicy.suppress("$title.", 0, title.length - 1, title.length + 1))
        }
    }

    @Test fun referencesFollowedByNumbersDoNotCreateFalseSentenceNods() {
        for (reference in listOf("Abs", "Art", "Nr")) {
            val text = "$reference. 13 gilt."
            assertTrue(AvatarSentenceAccentPolicy.suppress(text, 0, reference.length - 1,
                reference.length + 2))
            assertFalse(AvatarSentenceAccentPolicy.suppress("$reference. Müller", 0, reference.length - 1,
                reference.length + 2))
        }
    }

    @Test fun aTrueBoundaryAndInvalidPositionsAreUnchanged() {
        val text = "Der Vorgang endet. Morgen geht es weiter."
        assertFalse(AvatarSentenceAccentPolicy.suppress(text, 12, 16, 19))
        assertFalse(AvatarSentenceAccentPolicy.suppress(text, -1, 2, 8))
        assertFalse(AvatarSentenceAccentPolicy.suppress(text, 0, 1, text.length))
    }

    @Test fun directorStillEmitsFinalBoundaryWithoutAFalseTitleGesture() {
        val events = mutableListOf<AvatarEvent>()
        val director = AvatarDirector(AvatarSink { events += it }) { 1_000L }
        val speech = "Dr. Müller antwortet."
        assertTrue(director.speechStarted("t1", speech))
        assertTrue(director.wordRange("t1", 0, 2))
        assertTrue(events.none { it is AvatarEvent.SentenceEnd })
        val word = speech.indexOf("antwortet")
        assertTrue(director.wordRange("t1", word, word + "antwortet".length))
        assertEquals(listOf(SpeechBoundary.STATEMENT),
            events.filterIsInstance<AvatarEvent.SentenceEnd>().map { it.boundary })
        director.speechEnded("t1", false)

        events.clear()
        val legal = "Art. 5 gilt."
        assertTrue(director.speechStarted("t2", legal))
        assertTrue(director.wordRange("t2", 0, 3))
        assertTrue(events.none { it is AvatarEvent.SentenceEnd })
        val final = legal.indexOf("gilt")
        assertTrue(director.wordRange("t2", final, final + 4))
        assertEquals(listOf(SpeechBoundary.STATEMENT),
            events.filterIsInstance<AvatarEvent.SentenceEnd>().map { it.boundary })
    }
}
