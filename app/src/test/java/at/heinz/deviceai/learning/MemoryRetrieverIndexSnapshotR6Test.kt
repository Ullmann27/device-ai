package at.heinz.deviceai.learning

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * R6: Frozen before production changes. An index binds the document identities,
 * text and confirmation flags that produced its postings/vectors. Caller mutation
 * must never relabel a scored result. Explicit reindex is the only replacement.
 */
class MemoryRetrieverIndexSnapshotR6Test {
    private fun variants(): List<ContinuumMemoryRetriever> = listOf(
        Bm25GermanRetriever(),
        CharNgramHashRetriever(),
    )
    private val original = MemoryDoc("original", "Bernsteinquarz in der Gartenlaube", 123L, true)
    private val replacement = MemoryDoc("replacement", "Unbelegte Fremdaussage", 456L, false)

    @Test fun replacingCallerListEntryCannotRelabelAnExistingScoreOrConfirmation() {
        for (retriever in variants()) {
            val input = mutableListOf(original)
            retriever.index(input)
            assertEquals(original, retriever.search("Bernsteinquarz", 500L, 1).single().doc)
            input[0] = replacement
            val hit = retriever.search("Bernsteinquarz", 500L, 1).single()
            assertEquals("indexed identity must remain original", original, hit.doc)
            assertTrue("retriever must never change original confirmation", hit.confirmed)
        }
    }

    @Test fun clearingCallerListAfterIndexDoesNotEraseOrCrashTheIndex() {
        for (retriever in variants()) {
            val input = mutableListOf(original)
            retriever.index(input)
            input.clear()
            assertEquals(original, retriever.search("Bernsteinquarz", 500L, 1).single().doc)
        }
    }

    @Test fun onlyExplicitReindexReplacesTheSnapshotAndPreservesUnconfirmedStatus() {
        for (retriever in variants()) {
            val input = mutableListOf(original)
            retriever.index(input)
            input[0] = replacement
            assertEquals(original, retriever.search("Bernsteinquarz", 500L, 1).single().doc)
            retriever.index(input)
            assertTrue(retriever.search("Bernsteinquarz", 500L, 1).isEmpty())
            val hit = retriever.search("Fremdaussage", 500L, 1).single()
            assertEquals(replacement, hit.doc)
            assertFalse(hit.confirmed)
        }
    }

    @Test fun scoredPartsUseTheIndexedDocumentAndSameConfirmationAsSearch() {
        val retriever = CharNgramHashRetriever()
        val input = mutableListOf(original)
        retriever.index(input)
        input[0] = replacement
        val parts = retriever.scoredParts("Bernsteinquarz", 500L, 1).single()
        assertEquals(original, parts.hit.doc)
        assertTrue(parts.hit.confirmed)
        assertEquals(parts.hit, retriever.search("Bernsteinquarz", 500L, 1).single())
    }
}
