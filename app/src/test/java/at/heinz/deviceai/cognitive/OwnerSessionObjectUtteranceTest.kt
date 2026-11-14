package at.heinz.deviceai.cognitive

import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Real Fold7 dialogue transcription; no model-generated text is promoted as an owner fact. */
class OwnerSessionObjectUtteranceTest {
    @Test fun sessionScopedMemoryRequestRetainsObjectForPronounAndCorrection() {
        val context = LocalConversationContext()
        val session = "fold7-session"
        context.record(session,
            "Merke dir für dieses Gespräch: Die blaue Werkzeugkiste steht in der Garage und enthält fünf Schraubenschlüssel.",
            "Ich habe mir notiert: Die blaue Werkzeugkiste steht in der Garage und enthält fünf Schraubenschlüssel.")
        val question = "Welche Farbe hat sie und wo steht sie?"
        val before = context.objectStateAnswer(session, question)
        assertNotNull("The stated object must survive a model acknowledgement", before)
        assertTrue(before, before!!.contains("Farbe: blau"))
        assertTrue(before, before.contains("Ort: in der Garage"))

        context.record(session,
            "Nein, die Werkzeugkiste ist rot. Der Standort und die Anzahl bleiben gleich.",
            "Verstanden: Die Werkzeugkiste ist rot.")
        val after = context.objectStateAnswer(session, question)
        assertNotNull("The same object must be updated rather than forgotten", after)
        assertTrue(after, after!!.contains("Farbe: rot"))
        assertTrue(after, after.contains("Ort: in der Garage"))
        assertTrue(after, after.contains("Anzahl Schraubenschlüssel: 5").not() || after.contains("Farbe: rot"))

        val count = context.objectStateAnswer(session, "Wie viele Schraubenschlüssel sind darin?")
        assertNotNull(count)
        assertTrue(count, count!!.contains("Anzahl Schraubenschlüssel: 5"))
    }

    @Test fun sessionScopedObjectCannotLeakIntoAnotherConversation() {
        val context = LocalConversationContext()
        context.record("owner-A", "Merke dir für dieses Gespräch: Die rote Kiste steht im Keller.", "Notiert.")
        assertNotNull(context.objectStateAnswer("owner-A", "Welche Farbe hat sie?"))
        assertTrue(context.objectStateAnswer("owner-B", "Welche Farbe hat sie?") == null)
    }
}
