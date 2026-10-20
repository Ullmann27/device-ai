package at.heinz.deviceai.learning

import org.junit.Assert.*
import org.junit.Test

class ContinuumTopicFrontierTest {
    @Test fun fixedSeedsAndNovelTopicsInterleaveWithoutUnboundedCrawling() {
        val discovered=listOf("Robotik in der Medizin", "Neuronale Architektur")
        assertEquals(ContinuumPublicStudyPolicy.topicForCycle(0),
            ContinuumTopicFrontier.select(0,discovered))
        assertEquals(discovered[0],ContinuumTopicFrontier.select(1,discovered))
        assertEquals(discovered[0],ContinuumTopicFrontier.select(3,discovered))
        assertEquals(ContinuumPublicStudyPolicy.topicForCycle(1),
            ContinuumTopicFrontier.select(4,discovered))
        assertEquals(48,ContinuumPublicStudyPolicy.topics.size)
    }

    @Test fun maliciousUrlApiOptionsAndNamespaceTitlesCannotBecomeDiscoveryTargets() {
        val invalid=listOf(
            "https://localhost/", "http://bad.example", "Special:UserLogin",
            "Wikipedia:Administratoren", "Talk:Robotik", "Datei:Foto",
            "../config", "Hello&action=delete", "Daten?format=json",
            "Robotik\nAccept: application/x-dangerous",
            "Robotik #fragment", "", "AB"
        )
        for(value in invalid) {
            assertFalse("Unsafe source title accepted: "+value,
                ContinuumPublicStudyPolicy.validArticleTitle(value))
            assertThrows(IllegalArgumentException::class.java) {
                ContinuumPublicStudyPolicy.encyclopediaApiUrl(value)
            }
        }
        assertTrue(ContinuumPublicStudyPolicy.validArticleTitle("Maschinelles Lernen"))
        assertTrue(ContinuumPublicStudyPolicy.validArticleTitle("Robotik (Technik)"))
        assertTrue(ContinuumPublicStudyPolicy.encyclopediaLinksApiUrl("Robotik")
            .startsWith("https://de.wikipedia.org/w/api.php?"))
    }

    @Test fun frontierDeduplicatesAndNeverRevisitsAlreadySeenTitles() {
        val pending=listOf("Robotik in der Medizin","Robotik in der Medizin")
        val visited=listOf("Softwaretest")
        val incoming=listOf("Neuronale Architektur","Softwaretest","Forschung über Robotik",
            "https://bad.example","Special:Inhalt","Neuronale Architektur")
        val queue=ContinuumTopicFrontier.enqueue(pending,visited,incoming)
        assertEquals(listOf("Robotik in der Medizin","Neuronale Architektur",
            "Forschung über Robotik"),queue)
        val (remaining,history)=ContinuumTopicFrontier.afterAttempt(
            queue,visited,"Robotik in der Medizin",true)
        assertEquals(2,remaining.size)
        assertTrue("Robotik in der Medizin" in history)
        assertEquals(remaining,ContinuumTopicFrontier.enqueue(remaining,history,
            listOf("Robotik in der Medizin")))
    }

    @Test fun frontierBoundsVisitedAndPendingAndAllowsLaterRestoration() {
        val pending=(1..300).map { "Forschungsthema Nummer $it" }
        val queue=ContinuumTopicFrontier.enqueue(emptyList(),emptyList(),pending)
        assertEquals(ContinuumTopicFrontier.MAX_PENDING,queue.size)
        var visited=emptyList<String>()
        var awaiting=queue
        for(i in 0..480) {
            val title="Neues Thema $i"
            val (next,seen)=ContinuumTopicFrontier.afterAttempt(
                awaiting,visited,title,true)
            awaiting=next
            visited=seen
        }
        assertEquals(ContinuumTopicFrontier.MAX_VISITED,visited.size)
        assertEquals("Neues Thema 480",visited.last())
        assertThrows(IllegalArgumentException::class.java) {
            ContinuumTopicFrontier.select(-1,queue)
        }
    }
}
