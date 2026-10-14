package at.heinz.deviceai.learning

import org.junit.Assert.*
import org.junit.Test

class TrainableSubwordEncoderTest {
    @Test fun intentTrainingChangesSharedLearnedRepresentation() {
        val model=TrainableSubwordEncoder(73)
        val train=listOf(
            SubwordIntentExample("hallo mein freund",0),
            SubwordIntentExample("servus zusammen",0),
            SubwordIntentExample("kannst du mir helfen",1),
            SubwordIntentExample("warum leuchten sterne",1)
        )
        val before=model.evaluateIntent(train)
        val result=model.trainIntent(train,steps=800,maxMillis=4000)
        val after=model.evaluateIntent(train)
        assertFalse(result.stoppedEarly)
        assertTrue(after.loss<before.loss)
        assertTrue(after.correct>=before.correct)
        assertEquals(TrainableSubwordEncoder.BUCKETS*TrainableSubwordEncoder.DIMENSIONS+
            TrainableSubwordEncoder.DIMENSIONS+1,model.parameterCount)
    }

    @Test fun semanticTrainingPullsRelatedTextTogetherAndSeparatesNegativePair() {
        val model=TrainableSubwordEncoder(91)
        val positive=SubwordSemanticExample(
            "pflanze sonnenlicht energie",
            "pflanzen nutzen lichtenergie",true
        )
        val negative=SubwordSemanticExample(
            "pflanze sonnenlicht energie",
            "motor getriebe fahrzeug",false
        )
        val positiveBefore=model.similarity(positive.left,positive.right)
        val negativeBefore=model.similarity(negative.left,negative.right)
        val result=model.trainSemantic(listOf(positive,negative),steps=1000,maxMillis=4000)
        assertFalse(result.stoppedEarly)
        assertTrue(model.similarity(positive.left,positive.right)>positiveBefore)
        assertTrue(model.similarity(negative.left,negative.right)<negativeBefore)
    }

    @Test fun snapshotIsDeterministicAndTamperEvident() {
        val model=TrainableSubwordEncoder(101)
        model.trainIntent(listOf(
            SubwordIntentExample("guten morgen",0),
            SubwordIntentExample("wo liegt wien",1)
        ),steps=120,maxMillis=4000)
        val saved=model.save()
        val restored=TrainableSubwordEncoder.restore(saved)
        assertEquals(model.intentScore("wo liegt graz"),restored.intentScore("wo liegt graz"),1e-12)
        assertEquals(model.similarity("licht energie","energie licht"),
            restored.similarity("licht energie","energie licht"),1e-12)
        val bad=saved.copyOf()
        bad[bad.size/3]=(bad[bad.size/3].toInt() xor 1).toByte()
        assertThrows(Exception::class.java) { TrainableSubwordEncoder.restore(bad) }
    }

    @Test fun characterSubwordsShareFeaturesAcrossUnseenVariants() {
        val a=TrainableSubwordEncoder.subwordBuckets("fragen")
        val b=TrainableSubwordEncoder.subwordBuckets("frage")
        assertTrue(a.toSet().intersect(b.toSet()).isNotEmpty())
        assertTrue(a.size<=128 && b.size<=128)
    }
}
