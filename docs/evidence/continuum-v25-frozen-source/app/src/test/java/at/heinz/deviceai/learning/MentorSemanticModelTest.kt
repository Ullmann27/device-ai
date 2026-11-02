package at.heinz.deviceai.learning

import org.junit.Assert.*
import org.junit.Test

class MentorSemanticModelTest {
    private fun source(area:String,index:Int,text:String)=MentorSource(
        area,
        "https://de.wikipedia.org/wiki/"+area,
        "%064x".format(index+1),
        text
    )

    private val sample=listOf(
        source("Biologie",0,"Pflanze nutzt Sonnenlicht speichert Energie Wachstum Organismus."),
        source("Botanik",1,"Alge nutzt Sonnenlicht speichert Energie Wachstum Organismus."),
        source("Technik",2,"Motor nutzt Treibstoff erzeugt Bewegung Fahrzeug Maschine.")
    )

    @Test fun learnsSimilarityAcrossTermsThatNeverCooccur() {
        val model=MentorSemanticModel.build(sample)
        val result=model.similar("Pflanze")
        assertTrue(result.any { it.term=="alge" && it.similarity>0.70 })
        assertFalse(sample.any { it.excerpt.lowercase().contains("pflanze") &&
            it.excerpt.lowercase().contains("alge") })
        val answer=MentorSemanticRecall.answer("Mentor Ähnlich: Pflanze",sample,model)!!
        assertTrue(answer.contains("alge"))
        assertTrue(answer.contains("VERTEILUNGSMUSTER"))
        assertTrue(answer.contains("beweist weder Bedeutung noch Wahrheit"))
    }

    @Test fun snapshotIsDeterministicSourceBoundAndRejectsTampering() {
        val model=MentorSemanticModel.build(sample)
        val encoded=model.serialize()
        assertEquals(encoded,MentorSemanticModel.build(sample).serialize())
        assertEquals(encoded,MentorSemanticModel.restore(encoded,sample).serialize())
        assertThrows(IllegalArgumentException::class.java) {
            MentorSemanticModel.restore(encoded,sample.reversed())
        }
        assertThrows(IllegalArgumentException::class.java) {
            MentorSemanticModel.restore(encoded.replace("alge","fake"),sample)
        }
    }

    @Test fun unknownOrActionTextDoesNotCreateSemanticAuthority() {
        val model=MentorSemanticModel.build(sample)
        assertTrue(model.similar("Quantencomputer").isEmpty())
        assertNull(MentorSemanticRecall.answer("Öffne die Kamera",sample,model))
        assertNull(MentorSemanticRecall.answer("Mentor Ähnlich:",sample,model))
        assertTrue(MentorSemanticRecall.answer(
            "Mentor Semantik: Quantencomputer",sample,model)!!.contains("noch kein"))
    }

    @Test fun boundedVocabularyAndSerializedState() {
        val notes=(0 until 48).map { i ->
            source("Biologie",i,"Pflanze Sonnenlicht Energie Wachstum Zelle Wasser Kohlenstoff "+
                "Sauerstoff Stoffwechsel Organismus Entwicklung Anpassung Lebensraum Beispiel $i")
        }
        val model=MentorSemanticModel.build(notes)
        assertTrue(model.vocabularySize<=128)
        assertTrue(model.serialize().length<=90000)
        assertEquals(model.serialize(),MentorSemanticModel.restore(model.serialize(),notes).serialize())
        assertThrows(IllegalArgumentException::class.java) {
            MentorSemanticModel.build(notes+notes.first())
        }
    }
}
