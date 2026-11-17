package at.heinz.deviceai.learning

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Additional R9 holdouts; executed against both the unchanged R8 baseline and R9. */
class MemoryNegationNearNeighborR9Test {
    @Test fun bm25DoesNotReplaceNichtWithNische() {
        val r = Bm25GermanRetriever()
        r.index(listOf(MemoryDoc("positive-niche", "Eine Nische", 1_000L, true)))
        assertTrue(r.search("nicht", 2_000L, 3).isEmpty())
    }

    @Test fun bm25DoesNotReplaceKeinWithKeim() {
        val r = Bm25GermanRetriever()
        r.index(listOf(MemoryDoc("positive-germ", "Ein Keim", 1_000L, true)))
        assertTrue(r.search("kein", 2_000L, 3).isEmpty())
    }

    @Test fun ngramDoesNotTreatKeimAsKein() {
        val r = CharNgramHashRetriever(dim = 1 shl 14)
        r.index(listOf(MemoryDoc("positive-germ", "Ein Keim", 1_000L, true)))
        assertTrue(r.search("kein", 2_000L, 3).isEmpty())
    }

    @Test fun exactPositiveNearNeighborStillRetrieves() {
        for (r in listOf(Bm25GermanRetriever(), CharNgramHashRetriever(dim = 1 shl 14))) {
            val doc = MemoryDoc("positive-niche", "Eine Nische", 1_000L, true)
            r.index(listOf(doc))
            val hit = r.search("Nische", 2_000L, 1).single()
            assertEquals(doc, hit.doc)
            assertTrue(hit.confirmed)
        }
    }
}
