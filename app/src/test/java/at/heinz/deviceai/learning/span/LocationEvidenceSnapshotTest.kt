package at.heinz.deviceai.learning.span

import org.junit.Assert.*
import org.junit.Test

class LocationEvidenceSnapshotTest {
    private fun evidence(text: String, id: String = "a") = EvidenceBoundNaturalLocationProbe.Evidence(
        id, 1, EvidenceBoundNaturalLocationProbe.digest(text), text)

    @Test fun snapshotDoesNotRetainCallerListAndBindsOrderRevisionAndSourceIdentity() {
        val original = mutableListOf(evidence("Anna ist in Graz."), evidence("Ben ist in Linz.", "b"))
        val frozen = LocationEvidenceSnapshot.freeze(original)
        val same = LocationEvidenceSnapshot.freeze(original.toList())
        assertEquals(frozen.sha256, same.sha256)
        assertNotEquals(frozen.sha256, LocationEvidenceSnapshot.freeze(original.reversed()).sha256)
        assertNotEquals(frozen.sha256, LocationEvidenceSnapshot.freeze(listOf(original[0].copy(revision=2), original[1])).sha256)
        original.clear()
        assertEquals(2, frozen.sources.size)
        assertTrue(runCatching { (frozen.sources as MutableList).clear() }.isFailure)
    }

    @Test fun invalidOrAmbiguousSourceIdentityCannotBecomeSnapshot() {
        val good = evidence("Anna ist in Graz.")
        for (items in listOf(emptyList(), listOf(good, good), listOf(good.copy(text="Anna ist in Wien.")),
            listOf(good.copy(revision=0)), listOf(good.copy(sourceId="../other")),
            listOf(evidence("Anna ist in Graz.\uD800")))) {
            assertTrue(runCatching { LocationEvidenceSnapshot.freeze(items) }.isFailure)
        }
    }

    @Test fun validateExactlyTheCopiedEvidenceRatherThanAnEarlierCallerView() {
        val good = evidence("Anna ist in Graz.")
        val tampered = good.copy(text="Anna ist in Wien.")
        // Deterministic stand-in for a caller changing its list between validation and copy.
        val changing = object : AbstractList<EvidenceBoundNaturalLocationProbe.Evidence>() {
            var reads = 0
            override val size = 1
            override fun get(index: Int) = if (++reads >= 4) tampered else good
        }
        val snapshot = LocationEvidenceSnapshot.freeze(changing)
        assertEquals(snapshot.sources.single().sha256,
            EvidenceBoundNaturalLocationProbe.digest(snapshot.sources.single().text))
    }

    @Test fun modelCheckpointIsFrozenAndVerifierGuardsAllResearchPredictions() {
        val features = ContinuumResidualSpanCore.Features(positionFeatures=false, entityProximity=true,
            naturalEntityBindings=true, multiTokenSpans=true)
        val bytes = ContinuumResidualSpanCore(77, features).save()
        val researcher = VerifiedLocationSpanResearch(bytes)
        bytes.fill(0)
        val source = evidence("Anna ist in Graz. Anna ist in Wien.")
        val snapshot = LocationEvidenceSnapshot.freeze(listOf(source))
        val result = researcher.answer("Wo ist Anna?", snapshot)
        assertTrue(result.decision is EvidenceBoundNaturalLocationProbe.Decision.Abstained)
        assertEquals(snapshot.sha256, result.snapshotSha256)
        assertEquals(result, researcher.answer("Wo ist Anna?", snapshot))
        assertTrue(runCatching { VerifiedLocationSpanResearch(ContinuumResidualSpanCore(77).save()) }.isFailure)
    }
}
