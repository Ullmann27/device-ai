package at.heinz.deviceai.learning

import java.io.File
import java.io.DataInputStream
import java.io.ByteArrayInputStream
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test

/** Runs the same synthetic training independently and checks Node SWB1 floats. */
class ContinuumContrastiveParityTest {
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

    @Test fun actualNodeKotlinNodeTrainingParity() {
        val folder=System.getenv("CONTINUUM_CONTRASTIVE_PARITY_DIR")
        assumeTrue("Node training vectors are supplied in CI",!folder.isNullOrBlank())
        val dir=File(folder!!)
        val nodeBytes=File(dir,"node-candidate.swb1").readBytes()
        val node=TrainableSubwordEncoder.restore(nodeBytes)
        val trained=ContinuumContrastiveViews.trainCandidate(
            TrainableSubwordEncoder(91L),train,holdout,
            steps=350,seed=43L,drop=0.65,rate=0.10
        )
        assertFalse(trained.interrupted)
        assertEquals(350,trained.steps)
        assertEquals(700,trained.candidate.updates)
        assertEquals(node.updates,trained.candidate.updates)
        val kotlinBytes=trained.candidate.save()
        compareWeights(nodeBytes,kotlinBytes)

        val expected=File(dir,"node-metrics.tsv").readLines().associate { line ->
            val p=line.split("=",limit=2)
            require(p.size==2)
            p[0] to p[1]
        }
        fun check(key:String,value:Number) {
            val target=expected[key]?.toDoubleOrNull()?:error("Missing Node metric $key")
            assertEquals(key,target,value.toDouble(),1e-8)
        }
        check("steps",trained.steps)
        assertEquals("false",expected["interrupted"])
        fun checkRetrieval(label:String,value:ContrastiveRetrieval) {
            check("$label.top1",value.top1)
            check("$label.total",value.total)
            check("$label.reciprocalRank",value.reciprocalRank)
        }
        checkRetrieval("trainingBefore",trained.trainingBefore)
        checkRetrieval("trainingAfter",trained.trainingAfter)
        checkRetrieval("validationBefore",trained.validationBefore)
        checkRetrieval("validationAfter",trained.validationAfter)

        File(dir,"kotlin-candidate.swb1").writeBytes(kotlinBytes)
        File(dir,"kotlin-metrics.tsv").writeText(buildString {
            appendLine("steps="+trained.steps)
            appendLine("interrupted="+trained.interrupted)
            for((label,value) in listOf(
                "trainingBefore" to trained.trainingBefore,
                "trainingAfter" to trained.trainingAfter,
                "validationBefore" to trained.validationBefore,
                "validationAfter" to trained.validationAfter
            )) {
                appendLine("$label.top1="+value.top1)
                appendLine("$label.total="+value.total)
                appendLine("$label.reciprocalRank="+value.reciprocalRank)
            }
        })
    }

    /**
     * Independent SWB2 Node -> Kotlin -> Node validation.
     * This is a research-only path; the production SWB1 decoder is untouched.
     * The Node prepare step writes node-swb2.swb2; the Node verify step
     * subsequently checks the actual Kotlin-trained checkpoint.
     */
    @Test fun actualNodeKotlinNodeSwb2TrainingParityAfter350Rounds() {
        val folder=System.getenv("CONTINUUM_CONTRASTIVE_PARITY_DIR")
        assumeTrue("Node training vectors are supplied in CI",!folder.isNullOrBlank())
        val dir=File(folder!!)
        val nodeBytes=File(dir,"node-swb2.swb2").readBytes()
        val node=Swb2ExperimentalEncoder.restore(nodeBytes)
        val trained=Swb2ExperimentalEncoder(73L)
        repeat(350) { i ->
            trained.updateIntent(
                SubwordIntentExample(
                    if(i%2==0) "hallo freund" else "wie funktioniert strom",
                    i%2
                ), 0.08
            )
            trained.updateSemantic(
                SubwordSemanticExample(
                    "solarmodule liefern energie",
                    if(i%2==0) "photovoltaik erzeugt strom" else "getriebe motor rad",
                    i%2==0
                ), 0.07
            )
        }
        assertEquals(700,trained.updates)
        assertEquals(node.updates,trained.updates)
        val kotlinBytes=trained.save()
        assertEquals(32960,kotlinBytes.size)
        compareWeights(
            nodeBytes,kotlinBytes,
            expectedMagic=0x53574232,expectedSeed=73L,expectedUpdates=700
        )
        // An independent Node process verifies the full resulting checkpoint.
        File(dir,"kotlin-swb2.swb2").writeBytes(kotlinBytes)
    }

    private fun compareWeights(
        nodeBytes:ByteArray,kotlinBytes:ByteArray,
        expectedMagic:Int=0x53574231,expectedSeed:Long=91L,expectedUpdates:Int=700
    ) {
        val node=DataInputStream(ByteArrayInputStream(nodeBytes))
        val kotlin=DataInputStream(ByteArrayInputStream(kotlinBytes))
        for(input in listOf(node,kotlin)) {
            assertEquals(expectedMagic,input.readInt())
            assertEquals(expectedSeed,input.readLong())
            assertEquals(expectedUpdates,input.readInt())
        }
        for(n in listOf(4096,17)) {
            assertEquals(n,node.readInt())
            assertEquals(n,kotlin.readInt())
            repeat(n) { i ->
                assertEquals("Trained weight $i",node.readDouble(),kotlin.readDouble(),1e-8)
            }
        }
    }
}
