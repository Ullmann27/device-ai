package at.heinz.deviceai

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ContinuumSelfInspectionQueryTest {
    @Test fun recognizesExplicitProjectWeaknessQuestions() {
        assertTrue(ContinuumSelfInspectionQuery.matches("Wo liegen Schwachstellen in unserem Code bei Continuum?"))
        assertTrue(ContinuumSelfInspectionQuery.matches("Welche Schwächen hat deine App Kontinuum?"))
        assertTrue(ContinuumSelfInspectionQuery.matches("Analysiere Codeprobleme bei Cotino."))
        assertTrue(ContinuumSelfInspectionQuery.matches("Was sind die Schwachstellen in deinem Code?"))
    }

    @Test fun refusesUnrelatedGeneralAndCommandQueries() {
        assertFalse(ContinuumSelfInspectionQuery.matches("Welche Probleme hat Windows?"))
        assertFalse(ContinuumSelfInspectionQuery.matches("Starte Continuum und lösche alles."))
        assertFalse(ContinuumSelfInspectionQuery.matches("Wie funktioniert ein Computer?"))
        assertFalse(ContinuumSelfInspectionQuery.matches("Neuron Demo"))
        assertFalse(ContinuumSelfInspectionQuery.matches("Bitte speichere meine Fehler als persönliche Erinnerungen"))
    }
}
