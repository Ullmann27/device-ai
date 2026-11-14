package at.heinz.deviceai.cognitive

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ResearchTopicAuthorityTest {
    @Test
    fun counterEvidenceTemplatesCannotAdmitOffTopicSources() {
        val unrelated = document("funeral", "Find a funeral home",
            "Funeral services include ceremonies and burial arrangements.")
        val relevant = document("justice", "Justiz Österreich – Gegenposition",
            "Die Justiz in Österreich umfasst Gerichte und Staatsanwaltschaften.")
        val saved = mutableListOf<ResearchDossier>()
        val discovery = discovery { query ->
            if (query.counterEvidence) listOf(unrelated, relevant) else emptyList()
        }
        val dossier = DeepResearchEngine(
            discovery = discovery,
            provider = SimulatedResearchProvider(listOf(unrelated, relevant).associateBy { it.url }),
            dossierRepository = ResearchDossierRepository { saved += it },
            validateTarget = {}
        ).research("Justiz Österreich", ResearchMode.DEEP)

        assertEquals(listOf(relevant.url), dossier.sources.map { it.canonicalUrl })
        assertTrue(saved.isNotEmpty())
        assertTrue(saved.all { record -> record.sources.none { it.canonicalUrl == unrelated.url } })
        assertTrue(dossier.graph.claims.none { unrelated.url in it.sourceReferences })
    }

    @Test
    fun followUpClaimTextCannotReplaceOriginalTopic() {
        val positive = document("positive", "Wireless charging support",
            "The phone supports wireless charging and provides funeral insurance.")
        val negative = document("negative", "Wireless charging support",
            "The phone does not support wireless charging and provides funeral insurance.")
        val unrelated = document("insurance", "Funeral insurance plans",
            "Funeral insurance covers burial arrangements for families.")
        val sources = listOf(positive, negative, unrelated)
        var counterFollowUpSeen = false
        val discovery = discovery { query ->
            if (query.text.startsWith("Find counter-evidence for:")) {
                counterFollowUpSeen = true
                listOf(unrelated)
            } else listOf(positive, negative)
        }
        val saved = mutableListOf<ResearchDossier>()
        val dossier = DeepResearchEngine(
            discovery = discovery,
            provider = SimulatedResearchProvider(sources.associateBy { it.url }),
            dossierRepository = ResearchDossierRepository { saved += it },
            validateTarget = {}
        ).research("wireless charging support", ResearchMode.STANDARD)

        assertTrue(counterFollowUpSeen)
        assertTrue(dossier.graph.relationships.any { it.relation == ClaimRelation.CONTRADICTS })
        assertEquals(setOf(positive.url, negative.url), dossier.sources.map { it.canonicalUrl }.toSet())
        assertTrue(saved.all { record -> record.sources.none { it.canonicalUrl == unrelated.url } })
        assertTrue(dossier.graph.claims.none { unrelated.url in it.sourceReferences })
    }

    @Test
    fun titleGateUsesOriginatingSubquestion() {
        val wrongTopic = document("solar", "Solarzellen und Wirkungsgrad",
            "Solarzellen wandeln Sonnenlicht in elektrischen Strom um.")
        val plan = DeterministicResearchPlanner().plan("Justiz Österreich sowie Solarzellen", ResearchMode.QUICK)
        assertEquals(2, plan.questions.size)
        val first = plan.questions.first()
        val dossier = DeepResearchEngine(
            planner = ResearchPlanner { _, _ -> plan.copy(paths = listOf(
                ResearchSearchPath("first", ResearchQuery("Solarzellen", first.id))
            )) },
            discovery = discovery { listOf(wrongTopic) },
            provider = SimulatedResearchProvider(mapOf(wrongTopic.url to wrongTopic)),
            validateTarget = {}
        ).research(plan.question, ResearchMode.QUICK)

        assertTrue(dossier.sources.isEmpty())
        assertFalse(dossier.graph.claims.any { wrongTopic.url in it.sourceReferences })
    }

    @Test
    fun contradictionFollowUpKeepsItsMatchingSubquestion() {
        val justice = document("justice", "Justiz Österreich",
            "Die Justiz in Österreich umfasst Gerichte und Staatsanwaltschaften.")
        val positive = document("positive", "Wireless charging support",
            "The phone supports wireless charging and lasts ten hours.")
        val negative = document("negative", "Wireless charging support",
            "The phone does not support wireless charging and lasts ten hours.")
        val followUp = document("manual", "Wireless charging – manual",
            "The manual confirms that wireless charging is not supported.")
        val counterQuestionIds = mutableListOf<String>()
        val discovery = discovery { query ->
            when {
                query.counterEvidence -> {
                    counterQuestionIds += query.questionId
                    listOf(followUp)
                }
                query.questionId == "q0" -> listOf(justice)
                else -> listOf(positive, negative)
            }
        }
        val dossier = DeepResearchEngine(
            discovery = discovery,
            provider = SimulatedResearchProvider(
                listOf(justice, positive, negative, followUp).associateBy { it.url }
            ),
            validateTarget = {}
        ).research("Justiz Österreich sowie wireless charging support", ResearchMode.STANDARD)

        assertTrue(counterQuestionIds.isNotEmpty())
        assertTrue(counterQuestionIds.all { it == "q1" })
        assertEquals(setOf(justice.url, positive.url, negative.url, followUp.url),
            dossier.sources.map { it.canonicalUrl }.toSet())
    }

    @Test
    fun unrelatedContradictionsCannotInventCounterQueries() {
        val positive = document("positive", "Justiz Österreich",
            "The phone supports wireless charging and lasts ten hours.")
        val negative = document("negative", "Justiz Österreich",
            "The phone does not support wireless charging and lasts ten hours.")
        val observed = mutableListOf<ResearchQuery>()
        val discovery = discovery { query ->
            observed += query
            if (observed.size == 1) listOf(positive, negative) else emptyList()
        }
        val dossier = DeepResearchEngine(
            discovery = discovery,
            provider = SimulatedResearchProvider(listOf(positive, negative).associateBy { it.url }),
            validateTarget = {}
        ).research("Justiz Österreich sowie Solarzellen", ResearchMode.STANDARD)

        assertTrue(dossier.graph.relationships.any { it.relation == ClaimRelation.CONTRADICTS })
        assertTrue(dossier.rounds.any { it.number == 2 })
        assertFalse(observed.any { it.counterEvidence })
        assertEquals(setOf("q0", "q1"), observed.map { it.questionId }.toSet())
    }

    private fun discovery(select: (ResearchQuery) -> List<ResearchDocument>) =
        object : ResearchDiscoveryProvider {
            override val isGeneralSearchAvailable = true
            override fun discover(query: ResearchQuery) = select(query).map {
                ResearchSourceCandidate(it.url, "Search result", "general-web-search")
            }
        }

    private fun document(host: String, title: String, text: String) = ResearchDocument(
        "https://$host.example.org/source", title, 100L, contentHash(text), "text/plain",
        SourceIdentity("$host.example.org", "$host.example.org"), text
    )
}
