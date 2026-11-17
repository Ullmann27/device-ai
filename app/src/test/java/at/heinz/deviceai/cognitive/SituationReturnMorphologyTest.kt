package at.heinz.deviceai.cognitive

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * H4-B1: explicit return references in German/de-AT use compounds, inflection and
 * umlaut-less ASR spellings. The hint stays navigation-only; these tests check binding only.
 */
class SituationReturnMorphologyTest {
    private fun tracker(vararg turns: String) = LocalConversationSituationTracker().apply {
        turns.forEach(::observeOwnerTurn)
    }

    @Test fun longCompoundReturnBindsToEarlierTopic() {
        val t = tracker(
            "Wegen dem Druckertreiber muss ich heuer noch was erledigen.",
            "Jetzt reden wir über die Thermik beim Prefill."
        )
        val hint = t.resolveReturn("Zurück zu dem Druckertreiber von vorhin.")
        assertTrue("expected Resolved, got $hint", hint is LocalConversationSituationTracker.Hint.Resolved)
        assertTrue((hint as LocalConversationSituationTracker.Hint.Resolved).label.contains("druckertre"))
    }

    @Test fun umlautLessAsrSpellingStillBindsReturn() {
        val t = tracker(
            "Beim Fuehrerschein muss ich heuer noch was erledigen.",
            "Jetzt reden wir über die Thermik beim Prefill."
        )
        val hint = t.resolveReturn("Zurück zu dem Führerschein von vorhin.")
        assertTrue("expected Resolved, got $hint", hint is LocalConversationSituationTracker.Hint.Resolved)
    }

    @Test fun inflectedReturnBindsToSameTopic() {
        val t = tracker(
            "Die Sache mit der Garage sollten wir am Freitag klären.",
            "Jetzt reden wir über die Thermik beim Prefill."
        )
        val hint = t.resolveReturn("Zurück zu den Garagen von vorhin.")
        assertTrue("expected Resolved, got $hint", hint is LocalConversationSituationTracker.Hint.Resolved)
    }

    @Test fun shortSharedPrefixNeverBindsUnrelatedTopic() {
        val t = tracker(
            "Beim Kühlschrank hakt es gerade, das nervt ziemlich.",
            "Jetzt reden wir über die Thermik beim Prefill."
        )
        assertEquals(LocalConversationSituationTracker.Hint.NoMatch, t.resolveReturn("Zurück zu der Küche von vorhin."))
    }

    @Test fun neverDiscussedCompoundYieldsNoMatch() {
        val t = tracker(
            "Wegen dem Druckertreiber muss ich heuer noch was erledigen.",
            "Jetzt reden wir über die Thermik beim Prefill."
        )
        assertEquals(LocalConversationSituationTracker.Hint.NoMatch,
            t.resolveReturn("Zurück zu der Steuererklärung von vorhin."))
    }

    @Test fun sameNounWithDifferentQualifiersStaysAmbiguous() {
        val t = tracker(
            "Wegen der Rechnung vom Elektriker muss ich heuer noch was erledigen.",
            "Bei der Rechnung vom Installateur hakt es gerade, das nervt ziemlich.",
            "Jetzt reden wir über die Thermik beim Prefill."
        )
        val hint = t.resolveReturn("Zurück zu der Rechnung von vorhin.")
        assertTrue("expected Ambiguous, got $hint", hint is LocalConversationSituationTracker.Hint.Ambiguous)
    }

    @Test fun umlautTopicKeepsOneThreadAcrossConsecutiveTurns() {
        val t = tracker(
            "Beim Führerschein läuft die Frist bald ab.",
            "Der Führerschein muss verlängert werden, die Frist drängt."
        )
        assertEquals(1, t.snapshot().threads.size)
    }

    @Test fun sharedLongPrefixesDoNotTurnCompoundsIntoInflections() {
        for ((topic, misleading) in listOf(
            "Arbeit" to "Arbeitsamt",
            "Garage" to "Garagentor",
            "Partner" to "Partnerschaft",
            "Rechnung" to "Rechnungswesen",
            "Steuererklärung" to "Steuererklärungsfrist",
            "Druckertreiber" to "Druckertreibermodul"
        )) {
            for ((before, after) in listOf(topic to misleading, misleading to topic)) {
                val t = tracker(before, "Thermik Prefill")
                assertEquals("$before must not bind $after",
                    LocalConversationSituationTracker.Hint.NoMatch,
                    t.resolveReturn("Zurück zu $after von vorhin."))
            }
        }
    }

    @Test fun aSharedTruncatedPrefixCannotMergeConsecutiveOwnerTopics() {
        val t = tracker("Steuererklärung", "Steuererklärungsfrist")
        assertEquals(2, t.snapshot().threads.size)
    }

    @Test fun boundedNormalizationDoesNotTruncateDifferentOversizedWordsIntoTheSameCue() {
        val prefix = "a".repeat(90)
        val t = tracker(prefix + "rechnung", "Thermik Prefill")
        assertEquals(LocalConversationSituationTracker.Hint.NoMatch,
            t.resolveReturn("Zurück zu ${prefix}garage von vorhin."))
    }

    @Test fun normalizedNavigationStillRequiresExplicitReturnAndIsClearedOnReset() {
        val t = tracker("Fuehrerschein", "Thermik Prefill")
        assertEquals("", t.renderHint("Wie funktioniert der Führerschein?"))
        val hint = t.renderHint("Zurück zum Führerschein von vorhin.")
        assertTrue(hint.contains("nur Gesprächsnavigation, kein Fakt/Beleg/Toolrecht"))
        t.reset()
        assertEquals("", t.renderHint("Zurück zum Führerschein von vorhin."))
        assertTrue(t.snapshot().threads.isEmpty())
    }
}
