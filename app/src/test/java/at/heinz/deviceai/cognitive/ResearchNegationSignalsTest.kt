package at.heinz.deviceai.cognitive

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ResearchNegationSignalsTest {
    @Test fun explicitGermanNegationContrastsOtherwiseMatchingClaims() {
        assertTrue(ResearchNegationSignals.hasOppositePolarity(
            "Das Gerät unterstützt kabelloses Laden.",
            "Das Gerät unterstützt kabelloses Laden nicht."
        ))
        assertTrue(ResearchNegationSignals.hasOppositePolarity(
            "Die Funktion ist auf Android verfügbar.",
            "Die Funktion ist auf Android nicht verfügbar."
        ))
        assertTrue(ResearchNegationSignals.hasOppositePolarity(
            "Das Gerät besitzt einen Wechselspeicher.",
            "Das Gerät besitzt keinen Wechselspeicher."
        ))
    }

    @Test fun unchangedOrSamePolarityCannotInventOpposition() {
        assertFalse(ResearchNegationSignals.hasOppositePolarity(
            "Das Gerät unterstützt kabelloses Laden.",
            "Das Gerät unterstützt kabelloses Laden."
        ))
        assertFalse(ResearchNegationSignals.hasOppositePolarity(
            "Das Gerät unterstützt kabelloses Laden nicht.",
            "Das Gerät unterstützt die Funktion nicht."
        ))
        assertFalse(ResearchNegationSignals.hasOppositePolarity(
            "The device cannot be charged without a cable.",
            "The device cannot be charged without a cable."
        ))
    }

    @Test fun englishSignalsRemainAvailable() {
        assertTrue(ResearchNegationSignals.hasOppositePolarity(
            "The phone supports wireless charging.",
            "The phone does not support wireless charging."
        ))
        assertTrue(ResearchNegationSignals.hasOppositePolarity(
            "The device supports background playback.",
            "The device doesn't support background playback."
        ))
    }

    @Test fun simpleNichtNurPhraseDoesNotClaimAContradiction() {
        assertFalse(ResearchNegationSignals.hasOppositePolarity(
            "Der Bericht beschreibt Ladezeiten und Temperatur.",
            "Der Bericht beschreibt nicht nur Ladezeiten und Temperatur."
        ))
    }
}
