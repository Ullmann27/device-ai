package at.heinz.deviceai.cognitive

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class Cdx1EvidenceSlotBinderTest {
    private fun passage(
        text: String = "Klara zog am Dienstag nach Graz.",
        id: String = "archive_042",
        verified: Boolean = true,
        declaredDigest: String = Cdx1EvidenceSlotBinder.sha256(text)
    ) = Cdx1EvidenceSlotBinder.ArchivePassage(id, text, declaredDigest, verified)

    @Test fun exactSupportedSpanReceivesOnlyItsOwnCanonicalSource() {
        val bundle = Cdx1EvidenceSlotBinder.create(listOf(passage()))
        assertTrue(bundle.promptEvidence.contains("[Q1]"))
        val bound = Cdx1EvidenceSlotBinder.bindExactQuote("Graz [Q1]", bundle)
        assertNotNull(bound)
        assertEquals("Graz", bound!!.answerText)
        assertEquals("archive_042", bound.canonicalId)
        assertEquals("[Quelle: archive_042]", bound.citation)
    }

    @Test fun incorrectOrInventedClaimsCannotBorrowARealCitation() {
        val bundle = Cdx1EvidenceSlotBinder.create(listOf(passage()))
        assertNull(Cdx1EvidenceSlotBinder.bindExactQuote("Wien [Q1]", bundle))
        assertNull(Cdx1EvidenceSlotBinder.bindExactQuote("Grazer [Q1]", bundle))
        assertNull(Cdx1EvidenceSlotBinder.bindExactQuote("Graz [Q2]", bundle))
        assertNull(Cdx1EvidenceSlotBinder.bindExactQuote("Graz", bundle))
        assertNull(Cdx1EvidenceSlotBinder.bindExactQuote("a [Q1]", bundle))
    }

    @Test fun unverifiableDigestOrUnvalidatedSourceNeverEntersBundle() {
        val fakeDigest = passage(declaredDigest = "0".repeat(64))
        val unvalidated = passage(id = "untrusted_001", verified = false)
        val bundle = Cdx1EvidenceSlotBinder.create(listOf(fakeDigest, unvalidated))
        assertFalse(bundle.promptEvidence.contains("[Q1]"))
        assertNull(Cdx1EvidenceSlotBinder.bindExactQuote("Graz [Q1]", bundle))
    }

    @Test fun slotNumbersAndSourceIdsStayStableAfterFiltering() {
        val discarded = passage(id = "outside id", verified = false)
        val one = passage(id = "arch_001")
        val duplicate = passage(id = "arch_001")
        val two = passage(text = "Ben trägt einen blauen Schlüssel.", id = "arch_002")
        val bundle = Cdx1EvidenceSlotBinder.create(listOf(discarded, one, duplicate, two))
        assertTrue(bundle.promptEvidence.contains("[Q1]"))
        assertTrue(bundle.promptEvidence.contains("[Q2]"))
        assertFalse(bundle.promptEvidence.contains("[Q3]"))
        assertEquals("arch_002",
            Cdx1EvidenceSlotBinder.bindExactQuote("blauen Schlüssel [Q2]", bundle)!!.canonicalId)
    }

    @Test fun factualParaphraseCannotBeSilentlyCertified() {
        val bundle = Cdx1EvidenceSlotBinder.create(listOf(passage()))
        // Even a sensible inference needs a separate semantic entailment check.
        assertNull(Cdx1EvidenceSlotBinder.bindExactQuote(
            "Klaras neuer Wohnort ist die steirische Landeshauptstadt [Q1]", bundle))
    }
}
