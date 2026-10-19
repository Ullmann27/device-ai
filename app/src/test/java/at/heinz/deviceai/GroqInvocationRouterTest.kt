package at.heinz.deviceai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GroqInvocationRouterTest {
    @Test fun explicitMentorPrefixRoutesOnlyQuestion() {
        assertEquals("Was ist CRE1?", GroqInvocationRouter.extractQuestion("mentor: Was ist CRE1?"))
        assertEquals("Erkläre Device AI", GroqInvocationRouter.extractQuestion("MENTOR: Erkläre Device AI"))
    }

    @Test fun explicitGroqGreetingsRouteButNeverSendAutomatically() {
        assertEquals(
            "kannst du mir die App erklären?",
            GroqInvocationRouter.extractQuestion("Hallo Groq, kannst du mir die App erklären?")
        )
        assertEquals("Hallo!", GroqInvocationRouter.extractQuestion("Groq: Hallo!"))
        assertEquals("Welche Aufgabe hast du?", GroqInvocationRouter.extractQuestion("Hey Groq! Welche Aufgabe hast du?"))
    }

    @Test fun normalLocalChatMustStayLocal() {
        assertNull(GroqInvocationRouter.extractQuestion("Wie wird das Wetter heute?"))
        assertNull(GroqInvocationRouter.extractQuestion("Ich habe von Groq gelesen"))
        assertNull(GroqInvocationRouter.extractQuestion("Groq ist ein externer Mentor"))
        assertNull(GroqInvocationRouter.extractQuestion("Kannst du Groq erklären?"))
        assertNull(GroqInvocationRouter.extractQuestion("Die Antwort muss lokal bleiben"))
    }

    @Test fun emptyExplicitQuestionNeverInventsContent() {
        assertEquals("", GroqInvocationRouter.extractQuestion("mentor:"))
        assertEquals("", GroqInvocationRouter.extractQuestion("Hallo Groq"))
    }
}
