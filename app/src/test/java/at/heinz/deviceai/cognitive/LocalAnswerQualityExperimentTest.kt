package at.heinz.deviceai.cognitive

import org.junit.Assert.*
import org.junit.Test

/** Code55 field case #792: arithmetic correct, dialect failed. No runtime rewrite. */
class LocalAnswerQualityExperimentTest {
    private val expression = "5 × 3 + 5 − 2 : 4 + 210 − 456"

    @Test fun theOwnersActualAnswerHasCorrectMathButWrongDialect() {
        val reply = "Servus! Zuerst: 5 × 3 = 15; 2 : 4 = 0,5. " +
            "15 + 5 - 0,5 + 210 - 456. Das Ergebnis ist also **-226,5**. " +
            "Gibt's sonst noch was, bei dem i dir helfen kann?" + " Erklärung.".repeat(80)
        val report = LocalAnswerQualityExperiment.inspect(expression, reply)
        assertEquals(LocalAnswerQualityExperiment.Result.PASS, report.arithmetic)
        assertEquals("-226,5", report.expected)
        assertEquals("-226,5", report.claimed)
        assertEquals(LocalAnswerQualityExperiment.Result.FAIL, report.standardGerman)
        assertTrue(report.brevityHint)
    }

    @Test fun explicitlyIncorrectFinalValueIsDetected() {
        val result = LocalAnswerQualityExperiment.inspect(
            expression, "Das Ergebnis ist -225,5.")
        assertEquals(LocalAnswerQualityExperiment.Result.FAIL, result.arithmetic)
        assertEquals(LocalAnswerQualityExperiment.Result.PASS, result.standardGerman)
    }

    @Test fun noExplicitFinalClaimIsNotLabeledAsIncorrect() {
        val result = LocalAnswerQualityExperiment.inspect(expression,
            "Zuerst 5 × 3 = 15, danach geteilt.")
        assertEquals(LocalAnswerQualityExperiment.Result.UNVERIFIED, result.arithmetic)
    }

    @Test fun operatorPrecedenceAndParenthesesAreExact() {
        val x = LocalAnswerQualityExperiment.inspect("(5 + 3) × 4 − 2 : 4",
            "Ergebnis: 31,5")
        assertEquals(LocalAnswerQualityExperiment.Result.PASS, x.arithmetic)
        val y = LocalAnswerQualityExperiment.inspect("-5+2", "Ergebnis: -3")
        assertEquals(LocalAnswerQualityExperiment.Result.PASS, y.arithmetic)
    }

    @Test fun repeatingDecimalsAndDivisionByZeroAreUnverified() {
        assertEquals(LocalAnswerQualityExperiment.Result.UNVERIFIED,
            LocalAnswerQualityExperiment.inspect("1 : 3", "Ergebnis: 0,333").arithmetic)
        assertEquals(LocalAnswerQualityExperiment.Result.UNVERIFIED,
            LocalAnswerQualityExperiment.inspect("5 : 0", "Ergebnis: 0").arithmetic)
    }

    @Test fun unsupportedRequestsAreUnverifiedNotBlocked() {
        for (s in listOf("Bitte öffne die Kamera.", "Was bedeutet das?", "2 hoch 30")) {
            assertEquals(s, LocalAnswerQualityExperiment.Result.UNVERIFIED,
                LocalAnswerQualityExperiment.inspect(s, "Ergebnis: 5").arithmetic)
        }
    }

    @Test fun spokenQuotationIsNotCountedAsTheAssistantsOwnDialect() {
        val result = LocalAnswerQualityExperiment.inspect(
            "Was sagt das Zitat?", "Er sagte „i dir“. Die Form stammt aus dem Zitat.")
        assertEquals(LocalAnswerQualityExperiment.Result.PASS, result.standardGerman)
        val dialect = LocalAnswerQualityExperiment.inspect(
            "Schreib im Dialekt.", "Gibt's noch was?")
        assertEquals(LocalAnswerQualityExperiment.Result.UNVERIFIED, dialect.standardGerman)
    }

    @Test fun briefMathResponseIsPreferredButNotRequired() {
        val concise = LocalAnswerQualityExperiment.inspect(expression, "Ergebnis: -226,5")
        assertFalse(concise.brevityHint)
        val detailed = LocalAnswerQualityExperiment.inspect(
            "Erkläre Schritt für Schritt: 5 × 3 + 5 − 2 : 4 + 210 − 456",
            "Ergebnis: -226,5. " + "Der Rechenweg ist beschrieben. ".repeat(30))
        assertFalse(detailed.brevityHint)
    }
}
