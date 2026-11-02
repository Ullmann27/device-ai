package at.heinz.deviceai.learning

import org.junit.Assert.*
import org.junit.Test

class NeuralGeneralizationComparisonTest {
    @Test fun allSyntheticCorporaHaveBalancedLabelsAndNoIdenticalTrainingSentences() {
        val train = NeuralDialogue.DEMO_TRAINING + NeuralGeneralizationComparison.DIVERSE_TRAINING
        val heldouts = NeuralDialogue.DEMO_HOLDOUT +
            NeuralDialogue.UNFAMILIAR_HOLDOUT +
            NeuralTransferExperiment.CHALLENGE +
            NeuralGeneralizationComparison.FRESH_CONTROL
        val normalizedTrain = train.map { it.first.lowercase() }.toSet()
        assertEquals(train.size, normalizedTrain.size)
        assertTrue(heldouts.none { it.first.lowercase() in normalizedTrain })
        assertEquals(12,NeuralGeneralizationComparison.DIVERSE_TRAINING.count { it.second==0 })
        assertEquals(12,NeuralGeneralizationComparison.DIVERSE_TRAINING.count { it.second==1 })
        assertEquals(10,NeuralGeneralizationComparison.FRESH_CONTROL.count { it.second==0 })
        assertEquals(10,NeuralGeneralizationComparison.FRESH_CONTROL.count { it.second==1 })
    }

    @Test fun charBigramsAreBoundedReproducibleAndNeverEncodeLabels() {
        val lab=NeuralGeneralizationComparison
        val a=lab.features("Willkommen Zuhause",NeuralGeneralizationComparison.Encoder.CHAR_BIGRAM)
        val b=lab.features("Wieviel kostet es?",NeuralGeneralizationComparison.Encoder.CHAR_BIGRAM)
        assertEquals(32,a.size)
        assertTrue(a.all { it==0.0 || it==1.0 })
        assertEquals(a,lab.features("Willkommen Zuhause",NeuralGeneralizationComparison.Encoder.CHAR_BIGRAM))
        assertNotEquals(a,b)
        assertThrows(IllegalArgumentException::class.java) {
            lab.features("",NeuralGeneralizationComparison.Encoder.CHAR_BIGRAM)
        }
        assertThrows(IllegalArgumentException::class.java) {
            lab.features("a".repeat(321),NeuralGeneralizationComparison.Encoder.CHAR_BIGRAM)
        }
    }

    @Test fun factorialResearchRunsFourIndependentModelsAndNeverChangesUserWeights() {
        val original=NeuralDialogue()
        original.teach("Servus mein Freund",0,true)
        val frozen=original.save()
        val results=NeuralGeneralizationComparison.run()
        assertEquals(4,results.size)
        assertEquals(listOf(16,40,16,40),results.map { it.examples })
        assertTrue(results.all { it.steps==3000 && !it.stoppedEarly })
        assertTrue(results.all { it.familiar.total==8 && it.unfamiliar.total==12 &&
            it.counterexamples.total==10 && it.fresh.total==20 })
        assertTrue(results.all { it.fresh.loss.isFinite() })
        assertEquals(0,results[0].unfamiliarOverlapTokens)
        assertTrue(results[1].unfamiliarOverlapTokens>=0)
        assertArrayEquals(frozen,original.save())
        assertEquals(results.map { it.fresh },NeuralGeneralizationComparison.run().map { it.fresh })
    }

    @Test fun cancellationNeverPromotesIncompleteExperiment() {
        val calls=mutableListOf<Int>()
        val results=NeuralGeneralizationComparison.run(
            cancelled = { calls.size>=2 },
            onProgress = { calls.add(it.step) }
        )
        assertTrue(results.size<=1)
        assertTrue(results.all { it.stoppedEarly })
    }
}
