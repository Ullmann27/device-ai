package at.heinz.deviceai.cognitive

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ResearchTopicAuthorityLengthTest {
    @Test
    fun aLongRelevantClaimRetainsCounterResearch() {
        val detail = "with compatible accessories and careful alignment across charging surfaces " +
            "including desks bedside tables vehicle consoles travel stands office furniture " +
            "while temperature positioning protective cases adapters power supplies battery " +
            "condition installation materials moisture maintenance usage duration affect performance"
        assertCounterResearch(
            "wireless charging support",
            "The phone supports wireless charging $detail.",
            "The phone does not support wireless charging $detail."
        )
    }

    @Test
    fun aLongOriginalQuestionRetainsCounterResearch() {
        val question = "Describe wireless charging support for everyday users taking account " +
            "of compatibility alignment protective cases adapters safety heat portability " +
            "travel charging surfaces practical limitations maintenance installation materials " +
            "moisture performance battery condition power supplies additional accessories"
        val plan = DeterministicResearchPlanner().plan(question, ResearchMode.STANDARD)
        assertEquals(1, plan.questions.size)
        assertCounterResearch(question,
            "The phone supports wireless charging during normal everyday use.",
            "The phone does not support wireless charging during normal everyday use.")
    }

    @Test
    fun aSingleQuestionStillRejectsOffTopicContradictions() {
        val positive = document("positive", "Justiz Österreich",
            "The phone supports wireless charging and lasts ten hours.")
        val negative = document("negative", "Justiz Österreich",
            "The phone does not support wireless charging and lasts ten hours.")
        val queries = mutableListOf<ResearchQuery>()
        val dossier = DeepResearchEngine(
            discovery = discovery { query ->
                queries += query
                if (queries.size == 1) listOf(positive, negative) else emptyList()
            },
            provider = SimulatedResearchProvider(listOf(positive, negative).associateBy { it.url }),
            validateTarget = {}
        ).research("Justiz Österreich", ResearchMode.STANDARD)

        assertTrue(dossier.graph.relationships.any { it.relation == ClaimRelation.CONTRADICTS })
        assertFalse(queries.any { it.counterEvidence })
        assertTrue(dossier.gaps.none { it.counterEvidence })
    }

    private fun assertCounterResearch(question: String, positiveText: String, negativeText: String) {
        val positive = document("positive", "Wireless charging support", positiveText)
        val negative = document("negative", "Wireless charging support", negativeText)
        val manual = document("manual", "Wireless charging manual",
            "The manual confirms that wireless charging is not supported.")
        val counterQuestionIds = mutableListOf<String>()
        val dossier = DeepResearchEngine(
            discovery = discovery { query ->
                if (query.counterEvidence) {
                    counterQuestionIds += query.questionId
                    listOf(manual)
                } else listOf(positive, negative)
            },
            provider = SimulatedResearchProvider(listOf(positive, negative, manual).associateBy { it.url }),
            validateTarget = {}
        ).research(question, ResearchMode.STANDARD)

        assertTrue(dossier.graph.relationships.any { it.relation == ClaimRelation.CONTRADICTS })
        assertEquals(setOf("q0"), counterQuestionIds.toSet())
        assertTrue(dossier.sources.any { it.canonicalUrl == manual.url })
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
