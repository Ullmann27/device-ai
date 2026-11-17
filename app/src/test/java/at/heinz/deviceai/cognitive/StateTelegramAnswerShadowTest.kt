package at.heinz.deviceai.cognitive

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StateTelegramAnswerShadowTest {
    @Test fun currentValuePassesAndSupersededValueContradicts() {
        val telegram = """
            [STATE_TELEGRAM_V1@3000]
            S=ich|P=wohnort|V=Graz|NOT=Wien|SRC=lx-2
        """.trimIndent()

        val good=StateTelegramAnswerShadow.assess("Du wohnst in Graz.",telegram)
        assertEquals(StateTelegramAnswerShadow.Status.PASS_OBSERVED,good.status)
        assertEquals(1,good.passed)

        val bad=StateTelegramAnswerShadow.assess("Du wohnst in Wien.",telegram)
        assertEquals(StateTelegramAnswerShadow.Status.CONTRADICTION_OBSERVED,bad.status)
        assertEquals(1,bad.contradictions)
        assertTrue(bad.reasons.any { it.contains("FORBIDDEN_SUPERSEDED_VALUE") })
    }

    @Test fun unresolvedLedgerConflictNeverBecomesPass() {
        val telegram = """
            [STATE_TELEGRAM_V1@3000]
            S=ich|P=wohnort|CONFLICT=Graz,Wien|SRC=lx-1,lx-2
        """.trimIndent()
        val result=StateTelegramAnswerShadow.assess("Du wohnst in Graz.",telegram)
        assertEquals(StateTelegramAnswerShadow.Status.CONFLICT_IN_EVIDENCE,result.status)
        assertEquals(1,result.conflicts)
        assertEquals(0,result.passed)
    }

    @Test fun answerThatDoesNotTouchClaimIsInsufficientNotWrong() {
        val telegram = """
            [STATE_TELEGRAM_V1@3000]
            S=ich|P=wohnort|V=Graz|SRC=lx-2
        """.trimIndent()
        val result=StateTelegramAnswerShadow.assess(
            "Dafür müsste ich zuerst den Projektstand prüfen.",telegram)
        assertEquals(StateTelegramAnswerShadow.Status.INSUFFICIENT_COVERAGE,result.status)
        assertEquals(0,result.contradictions)
    }

    @Test fun missingStateIsNoState() {
        assertEquals(
            StateTelegramAnswerShadow.Status.NO_STATE,
            StateTelegramAnswerShadow.assess("Antwort",null).status
        )
    }
}
