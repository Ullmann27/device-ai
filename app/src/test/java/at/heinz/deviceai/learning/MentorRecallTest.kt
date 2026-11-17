package at.heinz.deviceai.learning
import org.junit.Assert.*
import org.junit.Test
class MentorRecallTest {
    @Test fun citesRealSourcesWithoutPretendingCertainty() {
        val notes=listOf(
            MentorSource("Biologie","https://de.wikipedia.org/wiki/Biologie","a".repeat(64),
                "Fotosynthese nutzt Licht in Pflanzen für chemische Reaktionen."),
            MentorSource("Kunst","https://de.wikipedia.org/wiki/Kunst","b".repeat(64),
                "Kunstwerke können eine besondere Ausdrucksweise besitzen.")
        )
        val answer=MentorRecall.answer("Mentor Wissen: Fotosynthese Pflanzen",notes)!!
        assertTrue(answer.contains("NICHT UNABHÄNGIG BESTÄTIGT"))
        assertTrue(answer.contains("https://de.wikipedia.org/wiki/Biologie"))
        assertFalse(answer.contains("Kunstwerke"))
        assertTrue(MentorRecall.answer("Mentor Wissen: Astronomie Sterne",notes)!!.contains("keine passenden"))
    }
    @Test fun neverTreatsGeneralRequestsAsMentorRecall() {
        assertNull(MentorRecall.answer("Starte den Wissensforscher",emptyList()))
        assertNull(MentorRecall.answer("Mentor Wissen: ",emptyList()))
    }
}
