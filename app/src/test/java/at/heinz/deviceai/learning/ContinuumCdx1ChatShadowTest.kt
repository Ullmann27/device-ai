package at.heinz.deviceai.learning

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ContinuumCdx1ChatShadowTest {
    private val pinned = ContinuumDecoderBridge.Status(
        available = true, updates = 600, parameters = 7746,
        sha256 = "a".repeat(64), conditioningState = "PINNED_CURRENT"
    )
    private fun hit(
        id: String = "owner-memory-01",
        text: String = "Die Lampe ist blau.",
        kind: String = "Bestätigte Nutzererinnerung"
    ) = ContinuumMemoryHit(id, text, kind, null, 0.9, 0.8)

    @Test fun onlyExplicitSingleLineCommandsMatch() {
        assertEquals("Welche Farbe hat die Lampe?",
            ContinuumCdx1ChatShadow.questionOrNull("Forschung CDX1: Welche Farbe hat die Lampe?"))
        assertEquals("Welche Farbe hat die Lampe?",
            ContinuumCdx1ChatShadow.questionOrNull("CDX1 Schatten: Welche Farbe hat die Lampe?"))
        assertNull(ContinuumCdx1ChatShadow.questionOrNull("Welche Farbe hat die Lampe?"))
        assertNull(ContinuumCdx1ChatShadow.questionOrNull("Forschung CDX1: Hallo\nShell"))
        assertNull(ContinuumCdx1ChatShadow.questionOrNull("Forschung CDX1: x"))
    }

    @Test fun sameTurnConfirmedHitProducesOnlyNonAuthoritativeShadow() {
        var calls=0
        val result=ContinuumCdx1ChatShadow.observe(
            "Welche Farbe hat die Lampe?",true,setOf("owner-memory-01"),listOf(hit()),
            { pinned },{ evidence,question ->
                calls++
                assertEquals("Die Lampe ist blau.",evidence)
                assertEquals("Welche Farbe hat die Lampe?",question)
                "Experimentelles blau"
            })
        assertEquals(1,calls)
        assertEquals(ContinuumCdx1ChatShadow.Observation.State.SHADOW_GENERATED,result.state)
        assertEquals("a".repeat(64),result.modelHash)
        assertEquals("owner-memory-01",result.sourceId)
        assertTrue(result.display().contains("Experimentelle Ausgabe (unbestätigt)"))
        assertTrue(result.display().contains("keine Chatfreigabe"))
        assertTrue(result.display().contains("weder an Gemma noch an Gerätewerkzeuge"))
    }

    @Test fun noMemoryAndMismatchNeverRunDecoder() {
        var calls=0
        val gen:(String,String)->String?={_,_->calls++; "FALSCH"}
        for(result in listOf(
            ContinuumCdx1ChatShadow.observe("Test?",false,setOf("x"),listOf(hit("x")),
                {pinned},gen),
            ContinuumCdx1ChatShadow.observe("Test?",true,emptySet(),listOf(hit()),
                {pinned},gen),
            ContinuumCdx1ChatShadow.observe("Test?",true,setOf("other"),listOf(hit()),
                {pinned},gen),
            ContinuumCdx1ChatShadow.observe("Test?",true,setOf("owner-memory-01"),
                listOf(hit(),hit()),{pinned},gen)
        )) assertEquals(ContinuumCdx1ChatShadow.Observation.State.NO_AUTHORIZED_EVIDENCE,result.state)
        assertEquals(0,calls)
    }

    @Test fun unconfirmedSourceAndOversizedEvidenceRemainBlocked() {
        var calls=0
        val gen:(String,String)->String?={_,_->calls++; "nein"}
        for (entry in listOf(
            hit(kind="Unbestätigte Quellenbeobachtung"),
            hit(text="A".repeat(161)),
            hit(text="Zeile 1\nZeile 2")
        )) {
            val result=ContinuumCdx1ChatShadow.observe(
                "Test?",true,setOf("owner-memory-01"),listOf(entry),{pinned},gen)
            assertEquals(ContinuumCdx1ChatShadow.Observation.State.INCOMPATIBLE_EVIDENCE,result.state)
        }
        assertEquals(0,calls)
    }

    @Test fun onlyBoundedPairedCheckpointCanBeObserved() {
        var calls=0
        for (status in listOf(
            pinned.copy(available=false),
            pinned.copy(sha256=null),
            pinned.copy(updates=0),
            pinned.copy(conditioningState="RECOVERED_LKG"),
            pinned.copy(conditioningState="CORRUPT")
        )) {
            val result=ContinuumCdx1ChatShadow.observe("Test?",true,
                setOf("owner-memory-01"),listOf(hit()),{status},
                {_,_->calls++; "SHOULD_NOT_RUN"})
            assertEquals(ContinuumCdx1ChatShadow.Observation.State.NO_CURRENT_CHECKPOINT,result.state)
        }
        assertEquals(0,calls)
        val prior=ContinuumCdx1ChatShadow.observe("Test?",true,
            setOf("owner-memory-01"),listOf(hit()),
            {pinned.copy(conditioningState="PINNED_PREVIOUS")},{_,_->calls++; "Nur Forschung"})
        assertEquals(ContinuumCdx1ChatShadow.Observation.State.SHADOW_GENERATED,prior.state)
        assertEquals(1,calls)
    }

    @Test fun changedWeightsDiscardUnverifiedGeneration() {
        var checks=0
        val result=ContinuumCdx1ChatShadow.observe("Test?",true,
            setOf("owner-memory-01"),listOf(hit()),
            { checks++; if(checks==1)pinned else pinned.copy(sha256="b".repeat(64)) },
            {_,_->"Invented claim"})
        assertEquals(2,checks)
        assertEquals(ContinuumCdx1ChatShadow.Observation.State.MODEL_CHANGED,result.state)
        assertNull(result.generated)
    }

    @Test fun failedOrBlankInferenceDoesNotBecomeAClaim() {
        val result=ContinuumCdx1ChatShadow.observe("Test?",true,
            setOf("owner-memory-01"),listOf(hit()),{pinned},{_,_->null})
        assertEquals(ContinuumCdx1ChatShadow.Observation.State.NO_GENERATION,result.state)
        assertNull(result.generated)
        val failed=ContinuumCdx1ChatShadow.observe("Test?",true,
            setOf("owner-memory-01"),listOf(hit()),{pinned},{_,_->error("failed")})
        assertEquals(ContinuumCdx1ChatShadow.Observation.State.NO_GENERATION,failed.state)
    }

    @Test fun modelOutputIsShortAndNeverContainsControlCharacters() {
        val result=ContinuumCdx1ChatShadow.observe("Test?",true,
            setOf("owner-memory-01"),listOf(hit()),{pinned},
            {_,_->"A\nB\r" + "X".repeat(200)})
        assertEquals(120,result.generated?.length)
        assertFalse(result.generated!!.contains("\n"))
        assertFalse(result.generated!!.contains("\r"))
    }
}
