package at.heinz.deviceai.learning

import org.junit.Assert.*
import org.junit.Test

class ContinuumContrastiveViewsTest {
    private val train = listOf(
        "sonnenlicht bringt der pflanze energie",
        "motor getriebe bewegen das fahrzeug",
        "klavier gitarre und musik instrumente",
        "regen wolken feuchtigkeit wetter",
        "stern mond galaxie planeten",
        "backofen brot rezept und mehl"
    )
    private val holdout = listOf(
        "fluss wasser fließt durch ein tal",
        "arzt medizin hilft bei gesundheitsfragen",
        "computer tastatur monitor und bildschirm",
        "katze hund tiere auf dem bauernhof"
    )

    @Test fun maskedRetrievalIsDeterministic() {
        val model=TrainableSubwordEncoder(73)
        val first=ContinuumContrastiveViews.evaluate(model,train)
        assertEquals(first,ContinuumContrastiveViews.evaluate(model,train))
        assertTrue(first.reciprocalRank in (1.0/train.size)..1.0)
        assertThrows(IllegalArgumentException::class.java) {
            ContinuumContrastiveViews.evaluate(model,listOf("same text","SAME TEXT"))
        }
    }

    @Test fun candidateTrainingDoesNotOverwriteTheLastGoodSnapshot() {
        val model=TrainableSubwordEncoder(91)
        val original=model.save()
        val result=ContinuumContrastiveViews.trainCandidate(
            model,train,holdout,steps=350
        )
        assertEquals(350,result.steps)
        assertFalse(result.interrupted)
        assertEquals(700,result.candidate.updates)
        assertArrayEquals(original,model.save())
        assertFalse(original.contentEquals(result.candidate.save()))
        assertTrue(result.trainingAfter.reciprocalRank in (1.0/train.size)..1.0)
        assertTrue(result.validationAfter.reciprocalRank.isFinite())
        val second=ContinuumContrastiveViews.trainCandidate(model,train,holdout,steps=350)
        assertArrayEquals(result.candidate.save(),second.candidate.save())
    }

    @Test fun interruptedTrainingIsNotSilentlyAccepted() {
        val model=TrainableSubwordEncoder(11)
        val original=model.save()
        var checks=0
        val r=ContinuumContrastiveViews.trainCandidate(
            model,train,holdout,steps=100,cancelled={ ++checks>7 }
        )
        assertTrue(r.interrupted)
        assertTrue(r.steps<100)
        assertArrayEquals(original,model.save())
        assertThrows(IllegalArgumentException::class.java) {
            ContinuumContrastiveViews.trainCandidate(model,train,train)
        }
        assertThrows(IllegalArgumentException::class.java) {
            ContinuumContrastiveViews.trainCandidate(model,train,
                listOf(train.first().uppercase(),holdout.first()))
        }
    }
}
