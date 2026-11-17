package at.heinz.deviceai.learning

import org.junit.Assert.*
import org.junit.Test

class ContinuumOrthographyProbeTest {
    @Test fun repairsOnlyUnambiguousSingleTypo() {
        val vocabulary=listOf(
            "Sternenreise ist ein Forschungsprojekt.",
            "Beschreibe die Architektur.",
            "Niemand hat das getestet."
        )
        assertEquals(
            "Erzähle von Sternenreise?",
            ContinuumOrthographyProbe.suggest("Erzähle von Sternnreise?",vocabulary)?.query
        )
        assertEquals(
            "Beschreibe jetzt das Modul.",
            ContinuumOrthographyProbe.suggest("Besxhreibe jetzt das Modul.",vocabulary)?.query
        )
        assertEquals(
            "Niemand hat das getestet.",
            ContinuumOrthographyProbe.suggest("Noemand hat das getestet.",vocabulary)?.query
        )
    }

    @Test fun preservesUnknownTechnicalTokensAndRejectsLongText() {
        val vocabulary=listOf("Sternenreise ist ein Forschungsprojekt.")
        assertNull(ContinuumOrthographyProbe.suggest("SWB1 CRE1 SHA256",vocabulary))
        assertNull(ContinuumOrthographyProbe.suggest("Roboter X15 statt X16",vocabulary))
        assertNull(ContinuumOrthographyProbe.suggest("A".repeat(321),vocabulary))
    }

    @Test fun unchangedMessageIsNotFalselyNormalized() {
        val vocabulary=listOf("Die Sternenreise ist unser Forschungsprojekt.")
        assertNull(ContinuumOrthographyProbe.suggest(
            "Was bedeutet Sternenreise?",vocabulary))
    }
}
