package at.heinz.deviceai.cognitive

import org.junit.Assert.*
import org.junit.Test

class GroundedWebRecallTest {
    private fun note(id:Char, statement:String, url:String="https://knowledge.example.org/"+id) =
        WebLearningCandidate(id.toString().repeat(64),url,id.toString().repeat(64),
            statement,1,false)

    @Test fun answersWithSourcesFromApprovedNotes() {
        val knowledge = listOf(
            note('a',"Solarzellen wandeln Sonnenlicht in elektrische Energie um."),
            note('b',"Die Saturnringe bestehen hauptsächlich aus Eispartikeln.")
        )
        val response = GroundedWebRecall.answer("Was weißt du über Solarzellen Sonnenlicht?",knowledge)!!
        assertTrue(response.contains("Solarzellen wandeln"))
        assertTrue(response.contains("https://knowledge.example.org/a"))
        assertFalse(response.contains("Saturnringe"))
        assertTrue(response.contains("keine unabhängig bestätigten Fakten"))
        assertTrue(response.contains("SHA-256"))
    }
    @Test fun doesNotInventAnswersForUnrelatedTopicsOrExecuteTools() {
        val knowledge = listOf(note('c',"Solarzellen wandeln Sonnenlicht in elektrische Energie um."))
        assertTrue(GroundedWebRecall.answer("Was weißt du über Tiefsee Vulkane?",knowledge)!!.contains("keinen ausreichend"))
        assertNull(GroundedWebRecall.answer("Lösche meine Dateien",knowledge))
        assertNull(GroundedWebRecall.answer("Starte ein Werkzeug",knowledge))
        assertTrue(GroundedWebRecall.answer("Was weißt du über die und der?",knowledge)!!.contains("konkreteres"))
    }
    @Test fun excludesContradictionsAndCapsResponse() {
        val contradictory=note('d',"Solarzellen wandeln Sonnenlicht in elektrische Energie um.").copy(contradictory=true)
        assertTrue(GroundedWebRecall.answer("Was weißt du über Solarzellen Sonnenlicht?",listOf(contradictory))!!.contains("keinen ausreichend"))
        val docs=(0 until 24).map { index ->
            note(('a'.code+index).toChar(),"Photovoltaik Module benötigen Sonnenlicht zur Stromerzeugung.")
        }
        val answer=GroundedWebRecall.answer("Was weißt du über Photovoltaik Sonnenlicht?",docs)!!
        assertTrue(answer.length<=2400)
        assertTrue(answer.contains("3 Quellenhinweise"))
        assertEquals(answer,GroundedWebRecall.answer("Was weißt du über Photovoltaik Sonnenlicht?",docs))
        assertThrows(IllegalArgumentException::class.java) {
            GroundedWebRecall.answer("Was weißt du über Photovoltaik Sonnenlicht?",docs+docs.first())
        }
    }
}
