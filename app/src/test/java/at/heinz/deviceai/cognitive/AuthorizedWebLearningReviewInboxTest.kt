package at.heinz.deviceai.cognitive

import org.junit.Assert.*
import org.junit.Test

class AuthorizedWebLearningReviewInboxTest {
    private fun dossier(): ResearchDossier {
        val url = "https://www.nasa.gov/solar"
        val hash = "a".repeat(64)
        val source = ResearchSource(url, "NASA", 1L, hash, "www.nasa.gov", null, "Public")
        val claim = Claim("fact-1", "Solarmodule wandeln Licht in Elektrizität um.",
            ProvenanceType.EXTERNAL_SOURCE, listOf(url), createdAt = 1L)
        return ResearchDossier("b".repeat(16), 1L, 1L,
            DeterministicResearchPlanner().plan("Solarenergie", ResearchMode.QUICK),
            ResearchPhase.COMPLETE, listOf(source),
            ResearchEvidenceGraph(listOf(claim), emptyList(), emptyList(), emptyMap(), emptyMap()),
            emptyList(), null, setOf(hash), false)
    }

    @Test fun reviewOnlyWhileGranted() {
        var allowed = true
        val inbox = AuthorizedWebLearningReviewInbox()
        assertTrue(inbox.publish(dossier()) { allowed })
        val candidate = inbox.candidates().single()
        assertTrue(inbox.canReview(candidate))
        allowed = false
        assertFalse(inbox.canReview(candidate))
        assertTrue(inbox.candidates().isEmpty())
    }

    @Test fun anotherSearchClearsEarlierReview() {
        val inbox = AuthorizedWebLearningReviewInbox()
        assertTrue(inbox.publish(dossier()) { true })
        val previous = inbox.candidates().single()
        assertFalse(inbox.publish(dossier()) { false })
        assertFalse(inbox.canReview(previous))
        assertTrue(inbox.candidates().isEmpty())
    }

    @Test fun cannotPublishWhenNotAuthorized() {
        val inbox = AuthorizedWebLearningReviewInbox()
        assertFalse(inbox.publish(dossier()) { false })
        assertTrue(inbox.candidates().isEmpty())
    }
}
