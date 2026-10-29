package at.heinz.deviceai.intelligence.ewm1

import at.heinz.deviceai.learning.ContinuumKnowledgeArchive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Synthetic records only; no archive content enters the repository. */
class Ewm1SourceIndexTest {
    private var n = 0
    private fun rec(title: String, text: String, sha: String = "s${n}") = ContinuumKnowledgeArchive.SourceRecord(
        id = "%064x".format(++n), kind = ContinuumKnowledgeArchive.Kind.WIKIPEDIA_EXCERPT, title = title,
        url = "https://de.wikipedia.org/wiki/T$n", attribution = "Wikipedia-Mitwirkende · CC BY-SA 4.0",
        collectedAt = 1_760_000_000_000L, sourceDate = null, sha256 = sha, text = text)

    private val long = "x".repeat(380)

    @Test fun questionFramesYieldTheTopic() {
        assertEquals("Zirkonia", Ewm1SourceIndex.topicOf("Was ist Zirkonia?"))
        assertEquals("Zirkonia", Ewm1SourceIndex.topicOf("Kannst du mir etwas über Zirkonia sagen?"))
        assertEquals("Zirkonia", Ewm1SourceIndex.topicOf("Erkläre mir bitte das Zirkonia."))
        assertEquals(null, Ewm1SourceIndex.topicOf("Mach das Licht an"))
    }

    @Test fun primaryTitleWinsAndQualifiedNamesasksBack() {
        val idx = Ewm1SourceIndex(listOf(rec("Zirkonia", "Zirkonia ist ein Mineral."), rec("Zirkonia (Band)", "Zirkonia ist eine Band.")))
        assertEquals("Zirkonia", idx.retrieve("Was ist Zirkonia?").evidence.single().record.title)
        val two = Ewm1SourceIndex(listOf(rec("Kappa (Film)", "Ein Film von 1990."), rec("Kappa (Roman)", "Ein Roman von 1980.")))
        assertEquals("CLARIFY", two.retrieve("Was ist Kappa?").decision)
    }

    @Test fun redirectTwinsAreOneSourceAndDisambiguationNeverAnswers() {
        val idx = Ewm1SourceIndex(listOf(rec("ASCII", "ASCII ist ein Zeichensatz.", "same"),
            rec("American Standard Code", "ASCII ist ein Zeichensatz.", "same"), rec("Ortel", "Ortel steht für: einen Ort, eine Firma.")))
        val ascii = idx.retrieve("Was ist ASCII?")
        assertEquals("QUERY", ascii.decision)
        assertEquals(1, ascii.evidence.single().twins.size)
        assertEquals("CLARIFY", idx.retrieve("Was ist Ortel?").decision)
    }

    @Test fun anUnknownTopicOrAMereSharedWordAbstains() {
        val idx = Ewm1SourceIndex(listOf(rec("Balkansprachbund", "Der Balkansprachbund ist ein Sprachbund.")))
        assertEquals("ABSTAIN", idx.retrieve("Was ist Quantenchromodynamik?").decision)
        assertEquals("ABSTAIN", idx.retrieve("Was ist ein Sprachbund?").decision)
    }

    @Test fun thePackCarriesProvenanceLicenceTrustAndTruncation() {
        val idx = Ewm1SourceIndex(listOf(rec("Zirkonia", "Zirkonia ist $long")))
        val pack = idx.retrieve("Was ist Zirkonia?")
        assertEquals("SUPPORTED_TRUNCATED", pack.reason)
        val json = pack.render()
        assertTrue(json.contains("SOURCE_OBSERVATION_UNVERIFIED") && json.contains("CC BY-SA 4.0") && json.contains("\"truncated\":true"))
    }
}
