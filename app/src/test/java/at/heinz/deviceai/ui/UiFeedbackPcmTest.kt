package at.heinz.deviceai.ui

import java.io.ByteArrayInputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.security.MessageDigest
import org.junit.Assert.*
import org.junit.Test

class UiFeedbackPcmTest {
    private fun bytes(vararg samples:Int)=ByteArray(samples.size*2) { index ->
        (samples[index/2] shr ((index%2)*8)).toByte()
    }

    @Test fun littleEndianSamplesSurvivePartialReadsAndTheStreamCloses() {
        var closed=false
        val input=object:ByteArrayInputStream(bytes(0,1400,-1400,0)) {
            override fun read(target:ByteArray,offset:Int,length:Int)=super.read(target,offset,minOf(length,1))
            override fun close() { closed=true;super.close() }
        }
        assertArrayEquals(shortArrayOf(0,1400,-1400,0),UiFeedbackPcm.load(4) { input })
        assertTrue(closed)
    }

    @Test fun invalidMissingAndTruncatedAssetsPermitSyntheticFallback() {
        for(payload in listOf(byteArrayOf(),byteArrayOf(0),bytes(0,0,0),bytes(1,100,0),
            bytes(0,100,1),bytes(0,1501,0),bytes(0,-1501,0),bytes(0,-32768,0))) {
            assertNull(UiFeedbackPcm.load(7200) { ByteArrayInputStream(payload) })
        }
        assertNull(UiFeedbackPcm.load(2880) { throw IOException("Missing synthetic asset") })
    }

    @Test fun oversizedAssetStopsReadingAtOneBytePastTheCueBudget() {
        var consumed=0
        var closed=false
        val input=object:InputStream() {
            override fun read():Int { consumed++;return 0 }
            override fun close() { closed=true }
        }
        assertNull(UiFeedbackPcm.load(2880) { input })
        assertEquals(5761,consumed)
        assertTrue(closed)
    }

    @Test fun stalledOrFailingReadsNeverSpinAndAlwaysClose() {
        for(stalled in listOf(true,false)) {
            var reads=0
            var closed=false
            val input=object:InputStream() {
                override fun read():Int=throw IOException("Unused")
                override fun read(target:ByteArray,offset:Int,length:Int):Int {
                    reads++
                    if(stalled)return 0
                    throw IOException("Synthetic read failure")
                }
                override fun close() { closed=true }
            }
            assertNull(UiFeedbackPcm.load(7200) { input })
            assertEquals(1,reads)
            assertTrue(closed)
        }
    }

    @Test fun impossibleBudgetsNeverOpenAssets() {
        for(limit in listOf(-1,0,7201,Int.MAX_VALUE)) {
            assertNull(UiFeedbackPcm.load(limit) { error("Must not open") })
        }
    }

    @Test fun bundledElevenLabsCuesPassTheRealDecoderWithPinnedContent() {
        val assets=listOfNotNull(System.getProperty("continuum.ui.assets")?.let(::File),
            File("src/main/assets"),File("app/src/main/assets"))
            .firstOrNull { File(it,"audio/ui/provenance.json").isFile }
        assertNotNull("Bundled cue provenance is missing",assets)
        val expected=listOf(
            Triple(ContinuumUiFeedback.Cue.BUTTON,2560,
                "8d6dd464327d9a701b2411b03f629b76c451c012e448fc43238e41b1ab965d4e"),
            Triple(ContinuumUiFeedback.Cue.TASK_STARTED,6720,
                "067ac28e4d29867197c066816f89f8b65ddce73c384d3634ceedfcd41f6860da"))
        for((cue,count,sha256) in expected) {
            val file=File(assets!!,cue.assetPath)
            assertEquals(cue.name,count*2L,file.length())
            val actual=MessageDigest.getInstance("SHA-256").digest(file.readBytes())
                .joinToString("") { "%02x".format(it.toInt() and 255) }
            assertEquals(cue.name,sha256,actual)
            val pcm=UiFeedbackPcm.load(cue.maxSamples) { file.inputStream() }
            assertNotNull("Bundled cue must not require synthetic fallback: ${cue.name}",pcm)
            assertEquals(count,pcm!!.size)
            assertEquals(1200,pcm.maxOf { kotlin.math.abs(it.toInt()) })
        }
    }
}
