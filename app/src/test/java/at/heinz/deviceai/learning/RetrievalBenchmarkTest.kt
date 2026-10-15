package at.heinz.deviceai.learning

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Base64

/**
 * Android side of the frozen retrieval benchmark (continuum/benchmark-v1.json).
 * The Kotlin ranking must reproduce the GitHub measurement in
 * docs/retrieval-benchmark.json ("adopted"), so the number reported for the
 * phone is the number measured for the phone. Small authored benchmark,
 * not an external certification.
 */
class RetrievalBenchmarkTest {
    private val bench=StrictJson.parseObject(
        javaClass.getResourceAsStream("/continuum/benchmark-v1.json")!!.bufferedReader(Charsets.UTF_8).readText())
    @Suppress("UNCHECKED_CAST")
    private val retrieval=bench["retrieval"] as Map<String,Any?>
    @Suppress("UNCHECKED_CAST")
    private val passages=retrieval["passages"] as List<String>
    private val weights=Base64.getDecoder().decode(
        javaClass.getResourceAsStream("/continuum-neural-swb1.base64")!!.bufferedReader().readText().trim())
    private val memories=passages.map { text ->
        LearningExperienceFactory.create(LearningExperienceType.USER_FACT,LearningProvenance.USER_CONFIRMED,
            LearningTrustClass.OWNER_CONFIRMED,LearningStatus.CONFIRMED,text,now=1)
    }

    @Suppress("UNCHECKED_CAST")
    private fun queries(split:String)=(retrieval[split] as List<Map<String,Any?>>).map { it["query"] as String to (it["passage"] as Long).toInt() }

    private fun metrics(split:String):Pair<Double,Double> {
        var top1=0.0;var mrr=0.0
        val qs=queries(split)
        for((query,target) in qs) {
            val hits=ContinuumMemoryDialogue.rank(weights,query,memories)
            val rank=hits.indexOfFirst { it.id==memories[target].id }
            if(rank==0)top1++
            if(rank>=0)mrr+=1.0/(rank+1)
        }
        return top1/qs.size to mrr/qs.size
    }

    @Test fun kotlinRankingReproducesTheGithubMeasurement() {
        // docs/retrieval-benchmark.json, method "adopted" (top-3 as shown in chat)
        val (devTop1,devMrr)=metrics("dev")
        val (testTop1,testMrr)=metrics("test")
        assertEquals(0.625,devTop1,0.0005);assertEquals(0.656,devMrr,0.0005)
        assertEquals(0.594,testTop1,0.0005);assertEquals(0.651,testMrr,0.0005)
    }

    @Test fun unknownTopicsAreMostlyDeclinedInsteadOfAnsweredWithAWrongMemory() {
        val qs=queries("dev")+queries("test")
        var abstained=0
        for((query,target) in qs) {
            val withoutTarget=memories.filterIndexed { i,_ -> i!=target }
            if(ContinuumMemoryDialogue.rank(weights,query,withoutTarget).isEmpty())abstained++
        }
        // Measured 0.516 with the IDF computed over all passages; removing the target
        // changes IDF slightly, so assert the property, not the exact digits.
        assertTrue("abstention collapsed: $abstained/${qs.size}",abstained.toDouble()/qs.size>=0.45)
    }

    @Test fun benchmarkSplitsDoNotLeakIntoCandidates() {
        val all=queries("dev")+queries("test")
        assertEquals(64,all.size)
        assertTrue(all.none { (q,_) -> passages.any { it.equals(q,ignoreCase=true) } })
        assertEquals(all.size,all.map { it.first }.toSet().size)
    }
}
