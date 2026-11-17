package at.heinz.deviceai.learning

import org.junit.Assert.*
import org.junit.Test

class ContinuumPublicStudyPolicyTest {
    @Test fun topicsAreCuratedBoundedAndDeterministicallyRotating() {
        assertEquals(48,ContinuumPublicStudyPolicy.topics.size)
        assertEquals(48,ContinuumPublicStudyPolicy.topics.toSet().size)
        assertTrue(ContinuumPublicStudyPolicy.topics.size<=ContinuumPublicStudyPolicy.MAX_TOPICS)
        assertEquals(ContinuumPublicStudyPolicy.topicForCycle(0),
            ContinuumPublicStudyPolicy.topicForCycle(48))
        assertEquals(ContinuumPublicStudyPolicy.topicForCycle(1),
            ContinuumPublicStudyPolicy.topicForCycle(49))
    }
    @Test fun onlyCuratedEncyclopediaEndpointsCanBeConstructed() {
        val topic="Künstliche Intelligenz"
        assertEquals("de.wikipedia.org",
            java.net.URI(ContinuumPublicStudyPolicy.encyclopediaApiUrl(topic)).host)
        val article=ContinuumPublicStudyPolicy.publicArticleUrl(topic)
        assertTrue(article.startsWith("https://de.wikipedia.org/wiki/"))
        assertThrows(IllegalArgumentException::class.java) {
            ContinuumPublicStudyPolicy.encyclopediaApiUrl("https://127.0.0.1/")
        }
    }
    @Test fun excerptNormalizationAndDigestAreStable() {
        val excerpt=ContinuumPublicStudyPolicy.normalizeExcerpt(
            "  Öffentliche  Quelle.\nEin fachlicher Lerntext ohne Geräteanweisung.  ")
        assertEquals("Öffentliche Quelle. Ein fachlicher Lerntext ohne Geräteanweisung.",excerpt)
        assertTrue(ContinuumPublicStudyPolicy.digest(excerpt).matches(Regex("[a-f0-9]{64}")))
        assertThrows(IllegalArgumentException::class.java) {
            ContinuumPublicStudyPolicy.normalizeExcerpt("kurz")
        }
    }
}
