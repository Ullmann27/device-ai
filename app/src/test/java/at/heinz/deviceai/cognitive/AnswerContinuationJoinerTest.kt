package at.heinz.deviceai.cognitive

import at.heinz.deviceai.cognitive.AnswerContinuationJoiner.Stop
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Code64: joining an answer cut at the output limit with its continuation segment. HOST, pure JVM. */
class AnswerContinuationJoinerTest {
    private fun join(answer: String, continuation: String, max: Int = 64_000) =
        AnswerContinuationJoiner.join(answer, continuation, max)

    private fun words(text: String) = Regex("[\\p{L}\\p{N}]+").findAll(text).map { it.value }.toList()

    // ---- overlap ---------------------------------------------------------------------------------------------------

    @Test fun aRepeatedLastClauseIsWrittenOnce() {
        val joined = join("Die Photosynthese wandelt Licht in chemische Energie um, und die Pflanze",
            "und die Pflanze speichert sie als Zucker.")
        assertEquals(Stop.NONE, joined.stop)
        assertEquals("Die Photosynthese wandelt Licht in chemische Energie um, und die Pflanze speichert sie als Zucker.", joined.text)
        assertTrue(joined.overlapCharacters > 0)
    }

    @Test fun theRepeatMayStartWithACapitalLetterAndDifferentWhitespace() {
        assertEquals("Wichtig ist: der Motor braucht Öl, und zwar regelmäßig.",
            join("Wichtig ist: der Motor braucht Öl, und zwar  ", "Und zwar\nregelmäßig.").text
                .replace("\n", " "))
        assertEquals("Der Plan hat drei Teile. Der erste Teil ist die Vorbereitung.",
            join("Der Plan hat drei Teile. Der erste Teil", "Der erste Teil ist die Vorbereitung.").text)
    }

    @Test fun aWordCutByTheLimitIsCompletedNotDoubled() {
        assertEquals("Der wichtigste Vorgang ist die Photosynthese der Pflanzen.",
            join("Der wichtigste Vorgang ist die Photosyn", "Photosynthese der Pflanzen.").text)
        assertEquals("Licht wird in chemische Energie umgewandelt.",
            join("Licht wird in chemische Ener", "chemische Energie umgewandelt.").text)
        // A short complete word is not mistaken for the beginning of the next one.
        assertEquals("Das ist ein einfaches Beispiel.", join("Das ist ein", "einfaches Beispiel.").text)
    }

    @Test fun oneRepeatedWordIsWrittenOnceButPunctuationIsNotARepeat() {
        assertEquals("Ist es wirklich gut?", join("Ist es", "es wirklich gut?").text)
        assertEquals("Das Ergebnis – – das zählt.", join("Das Ergebnis –", "– das zählt.").text)
    }

    @Test fun aContinuationThatOnlyRepeatsTheEndAddsNothing() {
        val joined = join("Am Ende zählt nur und die Pflanze", "und die Pflanze")
        assertEquals(Stop.EMPTY, joined.stop)
        assertEquals("Am Ende zählt nur und die Pflanze", joined.text)
    }

    // ---- no overlap ----------------------------------------------------------------------------------------------

    @Test fun withoutOverlapNoWordIsDroppedOrDoubled() {
        val pairs = listOf(
            "Erstens ist das so." to "Zweitens gilt das auch.",
            "Der Mond umkreist die" to "Erde in etwa 27 Tagen.",
            "Das Ergebnis" to ", das am Ende zählt, ist klar.",
            "Die Liste (siehe" to "Anhang) ist vollständig.",
            "Absatz eins endet hier.\n\n" to "Absatz zwei beginnt hier.",
            "Zeile eins" to "\nZeile zwei",
            "Ost-" to "West-Verkehr nimmt zu.")
        pairs.forEach { (answer, continuation) ->
            val joined = join(answer, continuation)
            assertEquals(Stop.NONE, joined.stop)
            assertEquals("$answer|$continuation", words(answer) + words(continuation), words(joined.text))
            assertEquals(0, joined.overlapCharacters)
        }
        assertEquals("Erstens ist das so. Zweitens gilt das auch.", join("Erstens ist das so.", "Zweitens gilt das auch.").text)
        assertEquals("Das Ergebnis, das am Ende zählt, ist klar.", join("Das Ergebnis", ", das am Ende zählt, ist klar.").text)
        assertEquals("Die Liste (siehe Anhang) ist vollständig.", join("Die Liste (siehe", "Anhang) ist vollständig.").text)
        assertEquals("Absatz eins endet hier.\n\nAbsatz zwei beginnt hier.",
            join("Absatz eins endet hier.\n\n", "Absatz zwei beginnt hier.").text)
        assertEquals("Zeile eins\nZeile zwei", join("Zeile eins", "\nZeile zwei").text)
        assertEquals("Ost-West-Verkehr nimmt zu.", join("Ost-", "West-Verkehr nimmt zu.").text)
        assertEquals("Titel (", join("Titel (", "").text)
    }

    // ---- announcement and ellipsis -----------------------------------------------------------------------------------

    @Test fun anAnnouncementOfTheContinuationIsNotPartOfTheAnswer() {
        val joined = join("Die Antwort auf die Frage lautet", "Gerne, hier ist die Fortsetzung:\n\nzweiundvierzig.")
        assertEquals("Die Antwort auf die Frage lautet zweiundvierzig.", joined.text)
        assertEquals("Gerne, hier ist die Fortsetzung:", joined.announcement)
        assertEquals("Die Antwort auf die Frage lautet zweiundvierzig.",
            join("Die Antwort auf die Frage lautet", "Ich setze die Antwort genau an dieser Stelle fort: zweiundvierzig.").text)
        assertEquals("Die Antwort auf die Frage lautet zweiundvierzig.",
            join("Die Antwort auf die Frage lautet", "… zweiundvierzig.").text)
        // A sentence of the answer that merely mentions a continuation stays.
        assertEquals("Die Serie endet offen. Hier ist die Lage klar.",
            join("Die Serie endet offen.", "Hier ist die Lage klar.").text)
    }

    // ---- restart ---------------------------------------------------------------------------------------------------

    @Test fun aContinuationThatStartsTheAnswerAgainIsNotJoined() {
        val answer = "Die Photosynthese ist ein Prozess, bei dem Pflanzen aus Licht, Wasser und Kohlendioxid Zucker herstellen. " +
            "Er läuft in den Chloroplasten ab und besteht aus zwei"
        val restart = "Die Photosynthese ist ein Prozess, bei dem Pflanzen aus Licht Zucker herstellen. Zunächst"
        val joined = join(answer, restart)
        assertEquals(Stop.RESTARTED, joined.stop)
        assertEquals(answer, joined.text)
        // The same words at the SEAM of a short answer are an overlap, not a restart.
        assertEquals(Stop.NONE, join("Die Photosynthese ist ein Prozess", "Die Photosynthese ist ein Prozess der Pflanzen.").stop)
    }

    // ---- numbered lists ----------------------------------------------------------------------------------------------

    @Test fun aListThatIsContinuedCorrectlyStaysAsItIs() {
        val joined = join("Schritte:\n1. Wasser kochen\n2. Tee ziehen lassen\n3. Milch", " dazugeben\n4. Trinken")
        assertEquals("Schritte:\n1. Wasser kochen\n2. Tee ziehen lassen\n3. Milch dazugeben\n4. Trinken", joined.text)
        assertEquals(0, joined.renumberedItems)
        // The item the limit cut is repeated in full: written once, numbering untouched.
        assertEquals("1. Eins\n2. Zwei ist der zweite Punkt\n3. Drei",
            join("1. Eins\n2. Zwei ist der", "2. Zwei ist der zweite Punkt\n3. Drei").text)
    }

    @Test fun aListThatStartsCountingAgainKeepsCounting() {
        val joined = join("Schritte:\n1. Wasser kochen\n2. Tee ziehen lassen\n3. Milch dazugeben",
            "1. Zucker nach Geschmack\n2. Umrühren\n\nDanach schmeckt der Tee.")
        assertEquals(Stop.NONE, joined.stop)
        assertEquals("Schritte:\n1. Wasser kochen\n2. Tee ziehen lassen\n3. Milch dazugeben\n" +
            "4. Zucker nach Geschmack\n5. Umrühren\n\nDanach schmeckt der Tee.", joined.text)
        assertEquals(2, joined.renumberedItems)
        // Parenthesis numbering and the item text are kept.
        assertEquals("1) a\n2) b\n3) c\n4) d", join("1) a\n2) b", "1) c\n2) d").text)
    }

    @Test fun aNewListAfterANewParagraphIsNotRenumbered() {
        val text = join("Vorteile:\n1. schnell\n2. leise\n3. sparsam", "\n\nNachteile:\n1. teuer\n2. schwer").text
        assertEquals("Vorteile:\n1. schnell\n2. leise\n3. sparsam\n\nNachteile:\n1. teuer\n2. schwer", text)
        val closed = join("1. schnell\n2. leise\n\nDas waren die Vorteile. Nun die", "Nachteile:\n1. teuer").text
        assertEquals("1. schnell\n2. leise\n\nDas waren die Vorteile. Nun die Nachteile:\n1. teuer", closed)
        // A nested list has its own numbers.
        assertEquals("1. Vorbereitung\n2. Durchführung:\n   1. messen\n   2. prüfen",
            join("1. Vorbereitung\n2. Durchführung:", "\n   1. messen\n   2. prüfen").text)
    }

    @Test fun aListThatStepsBackContradictsTheFormat() {
        val answer = "1. Wasser kochen\n2. Tee ziehen lassen\n3. Milch dazugeben"
        val joined = join(answer, "2. Den Tee lieber länger ziehen lassen\n3. Dann Milch")
        assertEquals(Stop.FORMAT_BREAK, joined.stop)
        assertEquals(answer, joined.text)
        assertEquals(Stop.FORMAT_BREAK, join("1. Wasser", "1. Zuerst das Wasser aufsetzen").stop)
    }

    // ---- code blocks -------------------------------------------------------------------------------------------------

    @Test fun codeIsJoinedWithoutAddedWhitespaceAndAReopenedFenceIsDropped() {
        val answer = "Hier der Code:\n```kotlin\nfun main() {\n    println(\"Hal"
        assertTrue(AnswerContinuationJoiner.insideCodeFence(answer))
        assertEquals("Hier der Code:\n```kotlin\nfun main() {\n    println(\"Hallo\")\n}\n```\nFertig.",
            join(answer, "```kotlin\nlo\")\n}\n```\nFertig.").text)
        assertEquals("```\nval a = 1\nval b = 2\n```", join("```\nval a = 1\n", "val b = 2\n```").text)
        // Inside code a list-like line is code, and an announcement-like line is kept as written.
        assertEquals("```\n1. erste Zeile\n2. zweite Zeile\n1. dritte Zeile\n```",
            join("```\n1. erste Zeile\n2. zweite Zeile\n", "1. dritte Zeile\n```").text)
        assertFalse(AnswerContinuationJoiner.insideCodeFence("```\nfertig\n```\nText"))
        assertEquals("Der Aufruf lautet `run()` und liefert einen Wert.", join("Der Aufruf lautet `run()` und", "liefert einen Wert.").text)
    }

    // ---- surrogate pairs ---------------------------------------------------------------------------------------------

    @Test fun surrogatePairsStayWhole() {
        fun wellFormed(text: String) = text.indices.none { index ->
            text[index].isHighSurrogate() && (index + 1 >= text.length || !text[index + 1].isLowSurrogate()) ||
                text[index].isLowSurrogate() && (index == 0 || !text[index - 1].isHighSurrogate())
        }
        val overlap = join("Das Ergebnis ist gut 😀 und", "und 😀😀 noch besser 🎉")
        assertEquals("Das Ergebnis ist gut 😀 und 😀😀 noch besser 🎉", overlap.text)
        assertTrue(wellFormed(overlap.text))
        val emojiSeam = join("Super 😀", "😀 weiter so")
        assertTrue(wellFormed(emojiSeam.text))
        assertEquals("Super 😀 😀 weiter so", emojiSeam.text)
        // A pair split by the limit is put together again.
        val split = join("Gut gemacht \uD83D", "\uDE00 und weiter")
        assertEquals("Gut gemacht 😀 und weiter", split.text)
        assertTrue(wellFormed(split.text))
        // A very long answer: the seam window never starts inside a pair.
        val long = "😀".repeat(400) + " Schluss der"
        assertTrue(wellFormed(join(long, "Schluss der Antwort.").text))
        assertEquals(long + " Antwort.", join(long, "Schluss der Antwort.").text)
    }

    // ---- bounds ------------------------------------------------------------------------------------------------------

    @Test fun theJoinedAnswerNeverExceedsTheCharacterBound() {
        val answer = "a".repeat(30) + " ende"
        val tooLong = join(answer, "b".repeat(30), max = 50)
        assertEquals(Stop.TOO_LONG, tooLong.stop)
        assertEquals(answer, tooLong.text)
        val fits = join(answer, "b".repeat(14), max = 50)
        assertEquals(Stop.NONE, fits.stop)
        assertEquals(50, fits.text.length)
        assertEquals(Stop.EMPTY, join(answer, "  \n ").stop)
        assertEquals(Stop.EMPTY, join(answer, "…").stop)
        assertEquals("Nur die Fortsetzung.", join("", "Nur die Fortsetzung.").text)
    }
}
