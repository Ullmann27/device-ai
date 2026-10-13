package at.heinz.deviceai.learning

import org.junit.Assert.*
import org.junit.Test

class MentorConceptNetworkTest {
    private fun source(area:String,hex:Char,text:String)=MentorSource(
        area,"https://de.wikipedia.org/wiki/"+area,hex.toString().repeat(64),text
    )
    private val sample=listOf(
        source("Biologie",'a',"Fotosynthese wandelt Licht in chemische Energie der Pflanze um."),
        source("Physik",'b',"Energie kommt in der Physik in verschiedenen Formen vor."),
        source("Astronomie",'c',"Sternenlicht bringt Energie zur Erde und beleuchtet Pflanzen.")
    )

    @Test fun buildsNewLocalAssociationsWithDistinctSourceEvidence() {
        val net=MentorConceptNetwork.build(sample)
        assertTrue(net.edgeCount>0)
        assertTrue(net.vocabularySize>3)
        assertTrue(net.related("Fotosynthese").any { it.term=="wandelt" })
        val result=MentorAssociativeRecall.answer(
            "Mentor Verbindungen: Fotosynthese",sample,net)!!
        assertTrue(result.contains("https://de.wikipedia.org/wiki/Biologie"))
        assertTrue(result.contains("Unbestätigte Internetbeobachtungen"))
        assertFalse(result.contains("https://de.wikipedia.org/wiki/Physik"))
    }

    @Test fun sourceBoundSnapshotRoundTripsAndRejectsStaleOrForgedEntries() {
        val graph=MentorConceptNetwork.build(sample)
        val encoded=graph.serialize()
        assertEquals(encoded,MentorConceptNetwork.restore(encoded,sample).serialize())
        assertThrows(IllegalArgumentException::class.java) {
            MentorConceptNetwork.restore(encoded,sample.reversed())
        }
        val changed=encoded.replace("wandelt","erfunden")
        assertThrows(IllegalArgumentException::class.java) {
            MentorConceptNetwork.restore(changed,sample)
        }
        assertThrows(IllegalArgumentException::class.java) {
            MentorConceptNetwork.restore(encoded+"falsch\n",sample)
        }
    }

    @Test fun emptyUnknownAndToolRequestsNeverInventRelations() {
        val net=MentorConceptNetwork.build(sample)
        assertTrue(net.related("Quantencomputer").isEmpty())
        assertNull(MentorAssociativeRecall.answer("Starte deine Kamera",sample,net))
        assertNull(MentorAssociativeRecall.answer("Mentor Verbindungen:",sample,net))
        assertTrue(MentorAssociativeRecall.answer(
            "Mentor Verbindungen: Quantencomputer",sample,net)!!.contains("keine"))
    }

    @Test fun limitsSizeAndPreservesSourceIndexes() {
        val all=(0 until 48).map { i ->
            source("Biologie",('a'+i%6),"Die Fotosynthese wandelt Sonnenlicht in Energie für Pflanzen um.")
                .copy(sha="%064x".format(i+1))
        }
        val graph=MentorConceptNetwork.build(all)
        assertTrue(graph.edgeCount<=1440)
        assertTrue(graph.serialize().length<=64000)
        assertEquals(graph.edgeCount,MentorConceptNetwork.restore(graph.serialize(),all).edgeCount)
        assertThrows(IllegalArgumentException::class.java){
            MentorConceptNetwork.build(all+all.first())
        }
    }
}
