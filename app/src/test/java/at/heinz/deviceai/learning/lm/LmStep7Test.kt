package at.heinz.deviceai.learning.lm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.ln

class LmStep7Test {
    @Test fun instructionPairsAreFixedBalancedAndContrastive() {
        val pairs = LmStep7.instructionPairs()
        assertEquals(200, pairs.size)
        assertEquals(LmStep7.itemsSha256(pairs), LmStep7.itemsSha256(LmStep7.instructionPairs()))
        assertEquals(mapOf("position" to 40, "case" to 40, "compare" to 40, "yesno" to 40, "neighbour" to 40),
            pairs.groupingBy { it.a.family }.eachCount())
        for (p in pairs) {
            assertEquals(p.a.candidates, p.b.candidates)
            assertNotEquals(p.a.prompt, p.b.prompt)
            assertNotEquals("the two instructions of a pair need different answers", p.a.gold, p.b.gold)
            assertTrue(p.a.gold in p.a.candidates.indices && p.b.gold in p.b.candidates.indices)
            assertEquals(p.a.candidates.size, p.a.candidates.toSet().size)
            assertTrue(p.a.prompt.endsWith(" Antwort:") && p.b.prompt.endsWith(" Antwort:"))
        }
    }

    @Test fun pairCountingSeparatesInstructionUseFromBlindness() {
        val pairs = LmStep7.instructionPairs()
        val gold = pairs.flatMap { listOf(it.a, it.b) }.associate { it.prompt to it.candidates[it.gold] }
        val other = pairs.flatMap { listOf(it.a.prompt to it.b.candidates[it.b.gold], it.b.prompt to it.a.candidates[it.a.gold]) }.toMap()
        /** bits of an answer: 1 for the wanted one, 9 otherwise; a bare prompt costs 0. */
        fun model(wanted: Map<String, String>): (String) -> Double = { text ->
            val prompt = text.substringBefore(" Antwort:") + " Antwort:"
            if (text == prompt) 0.0 else if (text == "$prompt ${wanted.getValue(prompt)}.") 1.0 else 9.0
        }
        val follows = LmStep7.instructionResult(model(gold), pairs)
        assertEquals(200, follows.both); assertEquals(0, follows.swapped); assertEquals(400, follows.correct)
        val inverts = LmStep7.instructionResult(model(other), pairs)
        assertEquals(0, inverts.both); assertEquals(200, inverts.swapped)
        val blind = LmStep7.instructionResult({ text -> text.substringAfter("Antwort:").length.toDouble() }, pairs)
        assertEquals("a model that ignores the instruction answers both alike", 0, blind.both)
        assertEquals(0, blind.swapped)
        assertTrue(blind.correct > 0)
    }

    @Test fun statisticsMatchExactValues() {
        assertEquals(1.0 / 32, LmStep7.signTestP(5, 5), 1e-12)
        assertEquals(6.0 / 32, LmStep7.signTestP(4, 5), 1e-12)
        assertEquals(1.0, LmStep7.signTestP(0, 7), 1e-12)
        assertEquals(1.0, LmStep7.signTestP(0, 0), 1e-12)
        assertEquals(2.0 / 1024, LmStep7.mcnemarP(0, 10), 1e-12)
        assertEquals(1.0, LmStep7.mcnemarP(5, 5), 1e-12)
        val (lo, hi) = LmStep7.wilson(50, 100)
        assertTrue(lo < .5 && hi > .5 && abs((lo + hi) / 2 - .5) < 1e-9)
    }

    @Test fun overlapFilterDropsCopiedAndRepeatedSegmentsOnly() {
        val corpus = listOf("Der Coding-Agent prüft jede Quittung im isolierten Worktree, bevor er etwas bestätigt.")
        val copied = "Neu: " + corpus[0].substring(0, 55)
        val fresh = "Ein ganz anderer Satz über das Hologramm und seine Lichtbögen auf dem Fold."
        val r = LmStep7.filter(listOf(fresh, copied, fresh, "kurz"), corpus)
        assertEquals(listOf(fresh, "kurz"), r.segments)
        assertEquals(1, r.droppedOverlap)
        assertEquals(1, r.droppedDuplicate)
    }

    @Test fun nextByteProbabilitiesAreADistributionThatMatchesSegmentBits() {
        val kn = KneserNeyBytes(4, listOf("der Hund läuft schnell", "der Hund bellt laut", "die Katze läuft"))
        val text = "der Hund läuft"
        val b = text.toByteArray(Charsets.UTF_8)
        var bits = 0.0
        for (i in b.indices) {
            val p = kn.nextByteProbabilities(b.copyOf(i))
            assertEquals(1.0, p.sum(), 1e-9)
            bits -= ln(p[b[i].toInt() and 255]) / ln(2.0)
        }
        assertEquals(kn.bits(text), bits, 1e-9)
    }

    @Test fun greedyWordPredictionNeedsTheWholeWordAndItsEnd() {
        val kn = KneserNeyBytes(7, List(20) { "der Hund läuft schnell über die Wiese" })
        assertTrue(LmStep7.knPredictsWord(kn, LmStep7.Position("der Hund läuft schnell über", "die")))
        assertFalse("the model goes on with the word", LmStep7.knPredictsWord(kn, LmStep7.Position("der Hund läuft schnell über", "di")))
        assertFalse(LmStep7.knPredictsWord(kn, LmStep7.Position("der Hund läuft schnell über", "das")))

        val tok = LmBpeTokenizer.train(List(20) { "der kern lernt schnell. der kern lernt schnell." }, 300)
        val seq = intArrayOf(tok.bos) + tok.encode("der kern lernt schnell. der kern lernt schnell.") + tok.eos
        val model = LmModel(LmParams.init(LmConfig(LmArch.MINGRU, tok.vocabSize, 16, 1, 2, 64, 32), 1))
        LmTrainer(model, 1e-2f, 0f, 5, 200, threads = 1).use { t -> repeat(200) { t.step(listOf(seq)) } }
        assertTrue(LmStep7.lmPredictsWord(model, tok, LmStep7.Position("der kern lernt", "schnell")))
        assertFalse(LmStep7.lmPredictsWord(model, tok, LmStep7.Position("der kern lernt", "langsam")))
        assertFalse(LmStep7.lmPredictsWord(model, tok, LmStep7.Position("der kern lernt", "schnel")))
    }

    @Test fun positionsStartAfterOneSpaceWithContextAndPromptsEndBeforeASpace() {
        val pos = LmStep7.positions(listOf("Ein kurzer Satz mit Wörtern, und noch  zwei Leerzeichen hier."), 100)
        assertEquals(setOf("Wörtern", "und", "noch", "Leerzeichen", "hier"), pos.map { it.word }.toSet())
        for (p in pos) assertFalse(p.prefix.endsWith(" "))
        val prompts = LmStep7.prompts(listOf("Der Reasoner fragt nach, wenn die Beispiele eine Funktion nicht eindeutig festlegen."), 5)
        assertEquals(1, prompts.size)
        assertTrue(prompts[0].prompt.length in 20..60 && !prompts[0].prompt.endsWith(" "))
        assertTrue(prompts[0].reference.startsWith(" "))
        assertEquals("Ab", LmStep7.clipBytes("Abö", 3))
    }
}
