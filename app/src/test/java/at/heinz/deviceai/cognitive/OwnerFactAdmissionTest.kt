package at.heinz.deviceai.cognitive

import at.heinz.deviceai.cognitive.OwnerFactAdmission.Kind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * H2 (GROK2-HANDOFF §5): "Merke dir: Wenn ich im Lotto gewinne, kaufe ich ein Haus" wurde in
 * AgentActivity.rememberOwnerFact als Owner-Confirmed-Fakt in die App-Faktenliste geschrieben
 * und mit "Gemerkt … Owner-Confirmed" quittiert. Hypothese/Plan/Frage/Unsicherheit bleibt unbestätigt.
 */
class OwnerFactAdmissionTest {
    @Test fun hypotheticalUncertainPlannedOrQuestionedStatementsAreHeld() {
        listOf(
            "Wenn ich im Lotto gewinne, kaufe ich ein Haus",
            "Vielleicht ziehe ich nächstes Jahr nach Graz",
            "Wohne ich in Wien?"
        ).forEach { assertEquals(it, Kind.HELD_UNCONFIRMED, OwnerFactAdmission.classify(it)) }
        assertTrue(OwnerFactAdmission.HELD_MESSAGE.startsWith("Nicht als Fakt gespeichert"))
    }

    @Test fun plainAssertionsStayConfirmable() {
        listOf("Ich wohne in Wien", "Mein Hund heißt Bello", "Ich trinke keinen Kaffee")
            .forEach { assertEquals(it, Kind.CONFIRMABLE, OwnerFactAdmission.classify(it)) }
    }
}
