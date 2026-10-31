package at.heinz.deviceai.learning

import at.heinz.deviceai.cognitive.ResearchDiscoveryProvider
import at.heinz.deviceai.cognitive.ResearchDocument
import at.heinz.deviceai.cognitive.ResearchProvider
import at.heinz.deviceai.cognitive.ResearchQuery
import at.heinz.deviceai.cognitive.ResearchResult
import at.heinz.deviceai.cognitive.ResearchSourceCandidate
import at.heinz.deviceai.cognitive.SourceIdentity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.IOException
import java.security.MessageDigest

class ContinuumOpenWebStudyFetchTest {
    private fun sha256(text: String): String =
        MessageDigest.getInstance("SHA-256").digest(text.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it.toInt() and 0xff) }

    private fun doc(url: String, content: String): ResearchResult {
        val d = ResearchDocument(
            url = url,
            title = "Test",
            retrievedAt = 1_760_000_000_000L,
            contentHash = sha256(content),
            mimeType = "text/html",
            source = SourceIdentity("example.org", "example.org"),
            content = content
        )
        return ResearchResult(listOf(d), listOf(d.citation()))
    }

    @Test
    fun skipsAlreadyCompletedFirstCandidateAndAcquiresNovelSecondSource() {
        val firstText = "Photovoltaik wandelt Lichtenergie mittels Solarzellen direkt in elektrische Energie um."
        val secondText = "Moderne Tandem-Solarzellen erreichen durch gestapelte Halbleiterschichten höhere Wirkungsgrade."
        val firstExcerpt = ContinuumPublicStudyPolicy.normalizeExcerpt(firstText)
        val firstSha = ContinuumPublicStudyPolicy.digest(firstExcerpt)

        var discoveryCalls = 0
        val discovery = object : ResearchDiscoveryProvider {
            override val isGeneralSearchAvailable = true
            override fun discover(query: ResearchQuery): List<ResearchSourceCandidate> {
                discoveryCalls++
                return listOf(
                    ResearchSourceCandidate(
                        "https://solar.example.org/grundlagen",
                        "Photovoltaik Grundlagen - Fraunhofer ISE",
                        "general-web-search"
                    ),
                    ResearchSourceCandidate(
                        "https://forschung.example.org/tandemzellen",
                        "Tandem Solarzellen in der Forschung | Helmholtz Zentrum",
                        "general-web-search"
                    )
                )
            }
        }
        val reader = object : ResearchProvider {
            override fun retrieve(req: ResearchRequest): ResearchResult = when (req.url) {
                "https://solar.example.org/grundlagen" -> doc(req.url, firstText)
                "https://forschung.example.org/tandemzellen" -> doc(req.url, secondText)
                else -> error("unexpected URL "+req.url)
            }
        }

        val acquisition = ContinuumOpenWebStudyFetch.acquire(
            topic = "Photovoltaik",
            cancelled = { false },
            completedShas = setOf(firstSha),
            discoveryFactory = { discovery },
            readerFactory = { reader }
        )

        assertEquals(1, discoveryCalls)
        assertEquals("https://forschung.example.org/tandemzellen", acquisition.source.url)
        assertFalse(acquisition.source.sha == firstSha)
        assertTrue(acquisition.discoveredTitles.contains("Photovoltaik Grundlagen"))
        assertTrue(acquisition.discoveredTitles.contains("Tandem Solarzellen in der Forschung"))
    }

    @Test
    fun filtersOverlongUrlsAndExtractsValidSubtitlesFromHyphenatedSearchTitles() {
        val overlongUrl = "https://portal.example.org/artikel/" + "a".repeat(420)
        val validUrl = "https://uni.example.org/informatik/maschinelles-lernen"
        val validText = "Maschinelles Lernen untersucht Algorithmen, die aus Daten Muster erkennen und verallgemeinern."

        val discovery = object : ResearchDiscoveryProvider {
            override val isGeneralSearchAvailable = true
            override fun discover(query: ResearchQuery): List<ResearchSourceCandidate> = listOf(
                ResearchSourceCandidate(overlongUrl, "Überlanger Pfad - Portal", "general-web-search"),
                ResearchSourceCandidate(
                    validUrl,
                    "Statistische Lerntheorie - Universität Musterstadt | Fakultät für Informatik und Data Science mit sehr langem Namen",
                    "general-web-search"
                )
            )
        }
        val requestedUrls = mutableListOf<String>()
        val reader = object : ResearchProvider {
            override fun retrieve(req: ResearchRequest): ResearchResult {
                requestedUrls += req.url
                return doc(req.url, validText)
            }
        }

        val acquisition = ContinuumOpenWebStudyFetch.acquire(
            topic = "Maschinelles Lernen",
            cancelled = { false },
            completedShas = emptySet(),
            discoveryFactory = { discovery },
            readerFactory = { reader }
        )

        assertEquals(listOf(validUrl), requestedUrls)
        assertEquals(validUrl, acquisition.source.url)
        assertTrue(acquisition.discoveredTitles.contains("Statistische Lerntheorie"))
        assertTrue(acquisition.discoveredTitles.all(ContinuumPublicStudyPolicy::validArticleTitle))
        val colonSplit = ContinuumOpenWebStudyFetch.extractCandidateTitles(
            "Künstliche Neuronen: Biologische Vorbilder und Modelle [2026]",
            "Künstliches neuronales Netz"
        )
        assertTrue(colonSplit.contains("Künstliche Neuronen"))
        assertTrue(colonSplit.contains("Biologische Vorbilder und Modelle"))
        val slashSplit = ContinuumOpenWebStudyFetch.extractCandidateTitles(
            "Photovoltaik / Solarzellen - Überblick",
            "Photovoltaik"
        )
        assertTrue(slashSplit.contains("Solarzellen"))
    }

    @Test
    fun earlierTransientFailureIsNotMaskedByLaterValidationFailure() {
        val discovery = object : ResearchDiscoveryProvider {
            override val isGeneralSearchAvailable = true
            override fun discover(query: ResearchQuery): List<ResearchSourceCandidate> = listOf(
                ResearchSourceCandidate("https://uni.example.org/eins", "Erster Artikel"),
                ResearchSourceCandidate("https://uni.example.org/zwei", "Zweiter Artikel")
            )
        }
        val reader = object : ResearchProvider {
            override fun retrieve(req: ResearchRequest): ResearchResult =
                if (req.url.endsWith("/eins")) {
                    throw IllegalArgumentException("Research request failed with HTTP 503.")
                } else {
                    doc(req.url, "Zu kurz")
                }
        }

        val error = try {
            ContinuumOpenWebStudyFetch.acquire(
                topic = "Physik",
                cancelled = { false },
                discoveryFactory = { discovery },
                readerFactory = { reader }
            )
            fail("Expected IOException from transient failure")
            null
        } catch (e: IOException) {
            e
        }!!

        val phase = ContinuumPublicStudyCycleResult.failurePhase("FETCH_SOURCE", error, false)
        assertEquals("FAILED_FETCH_NETWORK", phase)
        assertFalse(ContinuumPublicStudyCycleResult.markTopicCompleted(phase))
    }

    @Test
    fun emptySearchResultsThrowSourceUnavailableAndAdvanceTopicFrontier() {
        val discovery = object : ResearchDiscoveryProvider {
            override val isGeneralSearchAvailable = true
            override fun discover(query: ResearchQuery): List<ResearchSourceCandidate> = emptyList()
        }
        val reader = object : ResearchProvider {
            override fun retrieve(request: ResearchRequest): ResearchResult =
                error("reader must not be called")
        }

        val error = try {
            ContinuumOpenWebStudyFetch.acquire(
                topic = "Spezialthema",
                cancelled = { false },
                discoveryFactory = { discovery },
                readerFactory = { reader }
            )
            fail("Expected WikimediaSourceUnavailable")
            null
        } catch (e: WikimediaSourceUnavailable) {
            e
        }!!

        val phase = ContinuumPublicStudyCycleResult.failurePhase("FETCH_SOURCE", error, false)
        assertEquals("SOURCE_UNAVAILABLE_NO_PUBLIC_WEB_RESULTS", phase)
        assertEquals("NO_CHANGE", ContinuumPublicStudyCycleResult.outcome(phase))
        assertTrue(ContinuumPublicStudyCycleResult.markTopicCompleted(phase))

        val (remaining, visited) = ContinuumTopicFrontier.afterAttempt(
            listOf("Spezialthema", "Informatik"),
            emptyList(),
            "Spezialthema",
            ContinuumPublicStudyCycleResult.markTopicCompleted(phase)
        )
        assertEquals(listOf("Informatik"), remaining)
        assertTrue("Spezialthema" in visited)
    }

    @Test
    fun cancellationDuringFailedReaderDoesNotRetireTheTopic() {
        var stopped = false
        val discovery = object : ResearchDiscoveryProvider {
            override val isGeneralSearchAvailable = true
            override fun discover(query: ResearchQuery) = listOf(
                ResearchSourceCandidate("https://uni.example.org/physik", "Physik – Forschung")
            )
        }
        val reader = object : ResearchProvider {
            override fun retrieve(request: ResearchRequest): ResearchResult {
                stopped = true
                throw IOException("request stopped")
            }
        }
        val error = try {
            ContinuumOpenWebStudyFetch.acquire(
                topic = "Physik",
                cancelled = { stopped },
                discoveryFactory = { discovery },
                readerFactory = { reader }
            )
            fail("Expected cancellation")
            null
        } catch (e: IllegalStateException) {
            e
        }!!
        assertTrue(error.message.orEmpty().contains("WEB_STUDY_CANCELLED"))
    }

    @Test
    fun transportFailureRemainsNetworkFailureAndDoesNotMarkTopicCompleted() {
        val discovery = object : ResearchDiscoveryProvider {
            override val isGeneralSearchAvailable = true
            override fun discover(query: ResearchQuery): List<ResearchSourceCandidate> = listOf(
                ResearchSourceCandidate("https://uni.example.org/physik", "Quantenoptik - Institut")
            )
        }
        val reader = object : ResearchProvider {
            override fun retrieve(request: ResearchRequest): ResearchResult =
                throw IOException("DNS offline")
        }

        val error = try {
            ContinuumOpenWebStudyFetch.acquire(
                topic = "Physik",
                cancelled = { false },
                discoveryFactory = { discovery },
                readerFactory = { reader }
            )
            fail("Expected IOException")
            null
        } catch (e: IOException) {
            e
        }!!

        val phase = ContinuumPublicStudyCycleResult.failurePhase("FETCH_SOURCE", error, false)
        assertEquals("FAILED_FETCH_NETWORK", phase)
        assertEquals("FAILED", ContinuumPublicStudyCycleResult.outcome(phase))
        assertFalse(ContinuumPublicStudyCycleResult.markTopicCompleted(phase))
    }
}
