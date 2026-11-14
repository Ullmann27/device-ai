package at.heinz.deviceai.cognitive

import org.junit.Assert.*
import org.junit.Test

class LocalDialoguePromptHistoryTest {
    private fun turn(q: String, r: String) = LocalChatTranscriptStore.ContextTurn(q, r)

    @Test fun shortCompleteUnitsSurviveWithoutSummarization() {
        val turns = listOf(turn("Unser Plan hat zwei Schritte.", "Erstens prüfen. Zweitens berichten."),
            turn("Nur den ersten Schritt.", "Ich beschreibe zuerst die Prüfung."))
        val rendered = LocalDialoguePromptHistory.render(turns, 2_000, true, false)
        turns.forEach { assertTrue(rendered.contains(it.request)); assertTrue(rendered.contains(it.response)) }
        assertFalse(rendered.contains("Endabschnitt"))
    }

    @Test fun continuationCarriesTheActualAnswerEndInsteadOfItsOpening() {
        val response = "ALTER ANFANG. " + "Lange Erläuterung. ".repeat(250) +
            "Letzter tatsächlicher Abschnitt: Nun folgt die Prüfung des zweiten Bauteils."
        val rendered = LocalDialoguePromptHistory.render(listOf(turn("Erkläre den Plan.", response)),
            1_200, true, true)
        assertTrue(rendered.contains("Letzter tatsächlicher Abschnitt"))
        assertFalse(rendered.contains("ALTER ANFANG"))
        assertTrue(rendered.contains("Endabschnitt"))
        assertTrue(rendered.toByteArray(Charsets.UTF_8).size <= 1_200)
    }

    @Test fun fullOwnerNegationHasPriorityOverModelText() {
        val owner = "Emma ist in Berg. " + "Zusatz. ".repeat(150) + "Emma ist nicht mehr in Berg."
        val rendered = LocalDialoguePromptHistory.render(listOf(turn(owner, "Antwort. ".repeat(1_000))),
            2_000, true, false)
        assertTrue(rendered.contains(owner))
        assertTrue(rendered.contains("nicht mehr in Berg"))
        assertTrue(rendered.toByteArray(Charsets.UTF_8).size <= 2_000)
    }

    @Test fun optionalSelectionIsAContiguousNewestSuffix() {
        val turns = listOf(turn("ALT: Emma ist in Berg.", "Alt."),
            turn("MITTE: " + "x".repeat(900), "Mitte."),
            turn("NEU: Emma ist nicht mehr in Berg.", "Ungeklärt."))
        val rendered = LocalDialoguePromptHistory.render(turns, 700, false, false)
        assertTrue(rendered.contains(turns.last().request))
        assertFalse(rendered.contains("ALT:"))
        assertFalse(rendered.contains("MITTE:"))
        assertTrue(rendered.contains("Ältere Gesprächsteile fehlen"))
        assertEquals("", LocalDialoguePromptHistory.render(turns, 700, true, false))
    }

    @Test fun anUnfittableNewestCorrectionCannotExposeAnOlderClaim() {
        val turns = listOf(turn("Emma ist in Berg.", "Alt."),
            turn("Neue vollständige Korrektur: " + "x".repeat(900) + " nicht Berg.", "Neu."))
        assertEquals("", LocalDialoguePromptHistory.render(turns, 700, false, false))
    }

    @Test fun incompleteOwnerEvidenceBlocksAllOldClaims() {
        val turns = listOf(turn("Emma ist in Berg.", "Alt."),
            turn("", "Unklar.").copy(requestComplete = false))
        val rendered = LocalDialoguePromptHistory.render(turns, 2_000, false, false)
        assertTrue(rendered.contains("UNVOLLSTÄNDIG"))
        assertFalse(rendered.contains("Emma ist in Berg"))
        assertFalse(LocalDialoguePromptHistory.hasContinuation(turns))
    }

    @Test fun partialOutputHasAnEndButLegacyClippedPrefixDoesNot() {
        val partial = turn("Erkläre den Plan.", "Schritt eins, dann beginnt").copy(responseComplete = false)
        val rendered = LocalDialoguePromptHistory.render(listOf(partial), 1_000, true, true)
        assertTrue(rendered.contains(partial.response))
        assertTrue(rendered.contains("unvollständig"))
        assertEquals("", LocalDialoguePromptHistory.render(listOf(partial.copy(responseHasEnd = false)),
            1_000, true, true))
        val unanswered = turn("Eine andere Aufgabe.", "Keine Antwort.")
            .copy(modelResponse = false, responseComplete = false, responseHasEnd = false)
        assertFalse(LocalDialoguePromptHistory.hasContinuation(listOf(partial, unanswered)))
    }

    @Test fun unicodeBudgetAndAnswerEndStayIntact() {
        val response = "🧠 東京. ".repeat(500) + "Das ist das tatsächliche Ende."
        val rendered = LocalDialoguePromptHistory.render(listOf(turn("Erkläre auf Deutsch.", response)),
            900, true, true)
        assertTrue(rendered.endsWith("nachfragen.\n"))
        assertTrue(rendered.contains("Das ist das tatsächliche Ende."))
        assertFalse(rendered.contains('\uFFFD'))
        assertTrue(rendered.toByteArray(Charsets.UTF_8).size <= 900)
    }

    @Test fun largeLocalReportCannotDisplaceOwnerCorrection() {
        val report = "Alte Messwerte: " + "Temperatur 31 Grad. ".repeat(500) + "Keine aktuelle Messung."
        val turns = listOf(turn("Analysiere mein Handy.", report).copy(modelResponse = false),
            turn("Korrektur: Die aktuelle Temperatur ist nicht bekannt.", "Keine aktuelle Prüfung."))
        val rendered = LocalDialoguePromptHistory.render(turns, 1_200, true, false)
        assertTrue(rendered.contains(turns.first().request))
        assertTrue(rendered.contains(turns.last().request))
        assertTrue(rendered.contains("Auskunft fehlt; keine Messwerte daraus ableiten"))
        assertFalse(rendered.contains("Temperatur 31 Grad"))
        assertTrue(rendered.toByteArray(Charsets.UTF_8).size <= 1_200)
        assertTrue(LocalDialoguePromptHistory.render(turns, 16_000, true, false).contains(report))
    }

    @Test fun continuationKeepsRealAnswerEndBeforeLaterOptionalLocalReport() {
        val response = "Einleitung. " + "Langer Abschnitt. ".repeat(500) + "Hier setzt die nächste Erklärung an."
        val turns = listOf(turn("Erkläre den Ablauf.", response),
            turn("Prüfe den Speicher.", "Diagnosedaten. ".repeat(300)).copy(modelResponse = false))
        val reserve = requireNotNull(LocalDialoguePromptHistory.requiredBytes(turns, true))
        val rendered = LocalDialoguePromptHistory.render(turns, reserve, true, true)
        assertTrue(rendered.contains("Hier setzt die nächste Erklärung an."))
        assertTrue(rendered.contains("Endabschnitt"))
        assertTrue(rendered.contains("Auskunft fehlt"))
        assertTrue(rendered.toByteArray(Charsets.UTF_8).size <= reserve)
    }

    @Test fun requiredByteCountReservesAllUnicodeOwnersAndPrefix() {
        val turns = listOf(turn("Die Kiste ist in 東京, nicht in Graz.", "Lange Antwort. ".repeat(300)),
            turn("Korrektur: Sie steht in Wien.", "Neue Antwort. ".repeat(300)))
        val prefix = "Korrigierter Fokus: Kiste.\n"
        val reserve = requireNotNull(LocalDialoguePromptHistory.requiredBytes(turns, false, prefix))
        val rendered = LocalDialoguePromptHistory.render(turns, reserve, true, false, prefix)
        assertEquals(reserve, rendered.toByteArray(Charsets.UTF_8).size)
        assertTrue(rendered.startsWith(prefix))
        turns.forEach { assertTrue(rendered.contains(it.request)) }
        assertNull(LocalDialoguePromptHistory.requiredBytes(
            turns + turn("", "Fehlt.").copy(requestComplete = false), false))
    }

    @Test fun shortOwnerDeduplicationNeverEnlargesTheHistory() {
        val turns = listOf(turn("Rot.", "Antwort."))
        val original = requireNotNull(LocalDialoguePromptHistory.requiredBytes(turns, false))
        val deduplicated = requireNotNull(LocalDialoguePromptHistory.requiredBytes(turns, false,
            ownerTurnsInTask = setOf("Rot.")))
        assertTrue(deduplicated <= original)
        assertTrue(LocalDialoguePromptHistory.render(turns, original, true, false,
            ownerTurnsInTask = setOf("Rot.")).contains("Rot."))
    }

    @Test fun quotedOmissionLabelIsStillAnActualAnswer() {
        val response = "Der Text lautet: (Antwort nicht im Kontext). Danach folgt der nächste Absatz."
        val rendered = LocalDialoguePromptHistory.render(listOf(turn("Beschreibe den Text.", response)),
            1_200, true, true)
        assertTrue(rendered.contains(response))
    }
}
