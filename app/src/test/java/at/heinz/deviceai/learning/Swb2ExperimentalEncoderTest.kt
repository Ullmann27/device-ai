package at.heinz.deviceai.learning

import org.junit.Assert.*
import org.junit.Test
import org.junit.Assume.assumeTrue
import java.io.File

class Swb2ExperimentalEncoderTest {
    @Test fun weightedFeaturesAreNormalizedAndVersioned() {
        val features=Swb2ExperimentalEncoder.weightedBuckets("Solarmodule liefern elektrische Energie")
        assertTrue(features.isNotEmpty())
        assertTrue(features.size<=128)
        assertEquals(1.0,features.sumOf { it.weight },1e-12)
        val model=Swb2ExperimentalEncoder(73)
        val before=model.similarity("Solarmodule","Photovoltaikzellen")
        val bytes=model.save()
        assertEquals(32960,bytes.size)
        assertEquals("SWB2",""+bytes.take(4).map { it.toInt().toChar() }.joinToString(""))
        assertEquals(before,Swb2ExperimentalEncoder.restore(bytes).similarity("Solarmodule","Photovoltaikzellen"),1e-12)
        assertThrows(Exception::class.java) { TrainableSubwordEncoder.restore(bytes) }
        bytes[500]=(bytes[500].toInt() xor 1).toByte()
        assertThrows(Exception::class.java) { Swb2ExperimentalEncoder.restore(bytes) }
    }

    @Test fun crossRuntimeSwb2TrainingParity() {
        val folder=System.getenv("CONTINUUM_CONTRASTIVE_PARITY_DIR")
        assumeTrue("Node SWB2 vectors are supplied in CI",!folder.isNullOrBlank())
        val dir=File(folder!!)
        val model=Swb2ExperimentalEncoder(73)
        repeat(16) { i ->
            model.updateIntent(SubwordIntentExample(if(i%2==0)"hallo freund" else "wie funktioniert strom",i%2),0.08)
            model.updateSemantic(SubwordSemanticExample(
                "solarmodule liefern energie",if(i%2==0)"photovoltaik erzeugt strom" else "getriebe motor rad",i%2==0),0.07)
        }
        val node=Swb2ExperimentalEncoder.restore(File(dir,"node-swb2.swb2").readBytes())
        assertEquals(32,model.updates)
        assertEquals(node.updates,model.updates)
        for(i in model.embeddings.indices) assertEquals("embedding $i",node.embeddings[i],model.embeddings[i],1e-8)
        for(i in model.head.indices) assertEquals("head $i",node.head[i],model.head[i],1e-8)
        File(dir,"kotlin-swb2.swb2").writeBytes(model.save())
    }
}
