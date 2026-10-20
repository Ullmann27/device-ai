package at.heinz.deviceai.learning

import org.junit.Assert.*
import org.junit.Test

class ContinuumEvidenceSpanProbeTest {
    private fun expects(evidence:String,question:String,answer:String) {
        val result=ContinuumEvidenceSpanProbe.answer(evidence,question)
        assertTrue(result.toString(),result is ContinuumEvidenceSpanProbe.Result.Found)
        val span=(result as ContinuumEvidenceSpanProbe.Result.Found).span
        assertEquals(answer,span.text)
        assertEquals(answer,evidence.substring(span.start,span.endExclusive))
        assertTrue(span.start>=0)
        assertTrue(span.endExclusive<=evidence.length)
    }

    @Test fun novelRobotIdentifiersCopyOnlyAttestedSpans() {
        expects("Roboter Z77 steht in Graz.","Wo steht Z77?","Graz")
        expects("In Graz steht Roboter Z77.","In welcher Stadt steht Z77?","Graz")
        expects("Der Roboter X15 hat die Farbe blau.","Welche Farbe hat X15?","blau")
        expects("Der Roboter W80 hält einen Hammer.","Welches Werkzeug hält W80?","Hammer")
        expects("Roboter R01 nutzt Solarstrom als Energiequelle.",
            "Welche Energie nutzt R01?","Solarstrom")
    }

    @Test fun unknownOrAmbiguousEvidenceNeverPretendsToAnswer() {
        val unsupported=listOf(
            "Roboter Z77 steht in Graz." to "Wo steht Z78?",
            "Roboter Z77 steht in Graz. Roboter A12 steht in Wien." to "Wo steht Z77?",
            "Roboter Z77 steht in Graz." to "Wie alt ist Z77?",
            "Roboter Z77 steht in Graz." to "Wo steht Z77 und A12?",
            "Wien ist die Hauptstadt Österreichs." to "Wo liegt Wien?",
            "Roboter Z77 steht in Graz.\nRoboter A12 steht in Wien." to "Wo steht Z77?",
            "Roboter Z77 ist seit gestern in Graz." to "Wo steht Z77?"
        )
        unsupported.forEach { (e,q) ->
            assertTrue("$e | $q",ContinuumEvidenceSpanProbe.answer(e,q)
                is ContinuumEvidenceSpanProbe.Result.Abstained)
        }
    }

    @Test fun probeDoesNotUseNeuralModelsOrTrainAnyWeights() {
        val e="Der Roboter X15 hat die Farbe violett."
        repeat(10) { expects(e,"Welche Farbe hat X15?","violett") }
    }
}
