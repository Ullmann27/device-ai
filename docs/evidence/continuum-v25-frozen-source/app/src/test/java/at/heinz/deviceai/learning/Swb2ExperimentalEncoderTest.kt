package at.heinz.deviceai.learning

import org.junit.Assert.*
import org.junit.Test

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

    // The full 350-round / 700-update Node -> Kotlin -> Node protocol lives
    // in ContinuumContrastiveParityTest; do not overwrite its shared artifact
    // with a separate 16-round checkpoint.
}
