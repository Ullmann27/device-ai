package at.heinz.deviceai.cognitive

import org.junit.Assert.*
import org.junit.Test

class AdaptiveIdentityTest {
    private val genome = CognitiveGenome("id-1")
    private fun ai() = AdaptiveIdentity(genome)
    private fun ev(id: String, t: BehaviorTrait = BehaviorTrait.PRECISION, s: Double = 0.8, o: Double = 1.0) =
        BehaviorEvidence(id, t, s, o)
    private fun prop(v: Double, vararg e: BehaviorEvidence, t: BehaviorTrait = BehaviorTrait.PRECISION) =
        BehaviorProposal(t, v, e.toList(), "test")

    @Test fun neutralInitialProfile() {
        val p = ai().current.behaviorProfile
        BehaviorTrait.values().forEach { assertEquals(0.5, p[it], 0.0) }
    }

    @Test fun validSmallUpdateAccepted() {
        val a = ai()
        val r = a.applyBehaviorProposal(prop(0.6, ev("e1")), 100L)
        assertTrue(r is UpdateResult.Accepted)
        val v = a.current.behaviorProfile[BehaviorTrait.PRECISION]
        assertTrue(v > 0.5 && v <= 0.55)
    }

    @Test fun updateIsDeterministic() {
        val a = ai(); val b = ai()
        a.applyBehaviorProposal(prop(0.6, ev("e1")), 1L); b.applyBehaviorProposal(prop(0.6, ev("e1")), 1L)
        assertEquals(a.current.integrityHash, b.current.integrityHash)
    }

    @Test fun oneExperienceCannotMakeExtremeChange() {
        val a = ai()
        a.applyBehaviorProposal(prop(0.95, ev("e1", s = 1.0, o = 1.0)), 1L)
        assertTrue(a.current.behaviorProfile[BehaviorTrait.PRECISION] <= 0.5 + genome.updateLimits.maxBehaviorStep + 1e-12)
    }

    @Test fun boundsAreEnforced() {
        val r = ai().applyBehaviorProposal(prop(1.0, ev("e1")), 1L)
        assertTrue(r is UpdateResult.Rejected)
        assertEquals(FailureCode.OUT_OF_BOUNDS, (r as UpdateResult.Rejected).failures[0].code)
        val r2 = ai().applyBehaviorProposal(prop(-0.2, ev("e1")), 1L)
        assertTrue(r2 is UpdateResult.Rejected)
    }

    @Test fun repeatedUpdatesNeverLeaveBounds() {
        val a = ai()
        repeat(200) { a.applyBehaviorProposal(prop(0.95, ev("e$it")), it.toLong()) }
        assertEquals(0.95, a.current.behaviorProfile[BehaviorTrait.PRECISION], 1e-9)
        assertTrue(a.history.audit())
    }

    @Test fun nanAndInfinityRejected() {
        for (bad in listOf(Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY)) {
            assertTrue(ai().applyBehaviorProposal(prop(bad, ev("e1")), 1L) is UpdateResult.Rejected)
            assertTrue(ai().applyBehaviorProposal(prop(0.6, ev("e1", s = bad)), 1L) is UpdateResult.Rejected)
            assertTrue(ai().applyBehaviorProposal(prop(0.6, ev("e1", o = bad)), 1L) is UpdateResult.Rejected)
        }
        assertThrows(IllegalArgumentException::class.java) { BehaviorProfile.neutral().with(BehaviorTrait.CREATIVITY, Double.NaN) }
    }

    @Test fun rejectedProposalDoesNotChangeState() {
        val a = ai(); val before = a.current
        a.applyBehaviorProposal(prop(0.6), 1L)
        a.applyBehaviorProposal(prop(0.6, ev("e1", s = 0.01)), 1L)
        assertSame(before, a.current)
        assertEquals(1, a.history.generations.size)
        assertTrue(a.behaviorHistory.changes.isEmpty())
    }

    @Test fun evidenceDirectionMustMatchProposal() {
        val r = ai().applyBehaviorProposal(prop(0.6, ev("e1", o = -1.0)), 1L)
        assertTrue(r is UpdateResult.Rejected)
    }

    @Test fun evidenceMustMatchTrait() {
        val r = ai().applyBehaviorProposal(prop(0.6, ev("e1", t = BehaviorTrait.CREATIVITY)), 1L)
        assertEquals(FailureCode.EVIDENCE_MISMATCH, (r as UpdateResult.Rejected).failures[0].code)
    }

    @Test fun behaviorHistoryRecordsChangeAndGenerationsStayIntact() {
        val a = ai(); val g1 = a.current
        a.applyBehaviorProposal(prop(0.6, ev("e1"), ev("e2")), 42L)
        val c = a.behaviorHistory.changes.single()
        assertEquals(0.5, c.previousValue, 0.0)
        assertEquals(listOf("e1", "e2"), c.evidenceIds)
        assertEquals(1, c.sourceIdentityGeneration); assertEquals(2, c.resultingIdentityGeneration)
        assertEquals(42L, c.timestamp)
        assertEquals(c.acceptedValue, a.current.behaviorProfile[BehaviorTrait.PRECISION], 0.0)
        assertEquals(2, a.current.generation)
        assertEquals(g1, a.history.restore(1))
        assertEquals(0.5, a.history.restore(1)!!.behaviorProfile[BehaviorTrait.PRECISION], 0.0)
        assertTrue(a.history.audit())
    }

    @Test fun selfModelStartsUncertain() {
        val m = SelfModel.uncertain()
        Capability.values().forEach {
            assertEquals(0, m[it].evaluatedTaskCount); assertEquals(0.0, m[it].confidence, 0.0)
            assertEquals(0.5, m[it].competence, 0.0)
        }
    }

    private fun cev(i: Int, p: Double, actual: Double) =
        CapabilityEvidence("t$i", Capability.ANDROID_DEBUGGING, p, actual, "unit", i.toLong())

    @Test fun evidenceUpdatesGradually() {
        val m = SelfModel.uncertain().withEvidence(cev(1, 0.9, 0.42), 0.1)
        val e = m[Capability.ANDROID_DEBUGGING]
        assertEquals(1, e.evaluatedTaskCount)
        assertTrue(e.competence < 0.5 && e.competence > 0.45)
        assertTrue(kotlin.math.abs(e.calibrationError - 0.5) < 0.05)
        assertEquals(1, m.explain(Capability.ANDROID_DEBUGGING).size)
        assertTrue(m.explain(Capability.CODING).isEmpty())
    }

    @Test fun repeatedOverconfidenceLowersConfidence() {
        var over = SelfModel.uncertain(); var good = SelfModel.uncertain()
        val confs = mutableListOf<Double>()
        for (i in 1..30) {
            over = over.withEvidence(cev(i, 0.95, 0.2), 0.1)
            good = good.withEvidence(cev(i, 0.8, 0.8), 0.1)
            confs.add(over[Capability.ANDROID_DEBUGGING].confidence)
        }
        val o = over[Capability.ANDROID_DEBUGGING]; val g = good[Capability.ANDROID_DEBUGGING]
        assertTrue(o.calibrationError > 0.6)
        assertTrue(g.calibrationError < 0.1)
        assertTrue(o.confidence < g.confidence)
        assertTrue(confs.last() < confs.first())
    }

    @Test fun invalidCapabilityEvidenceRejected() {
        val a = ai()
        assertTrue(a.recordCapabilityEvidence(cev(1, Double.NaN, 0.5)) is UpdateResult.Rejected)
        assertTrue(a.recordCapabilityEvidence(cev(1, 0.5, 1.5)) is UpdateResult.Rejected)
        assertEquals(1, a.history.generations.size)
        assertTrue(a.recordCapabilityEvidence(cev(1, 0.5, 0.5)) is UpdateResult.Accepted)
        assertTrue(a.recordCapabilityEvidence(cev(1, 0.5, 0.5)) is UpdateResult.Rejected)
        assertEquals(2, a.current.generation)
    }

    @Test fun generationIncrementsOnlyOnAcceptedChange() {
        val a = ai()
        assertEquals(1, a.current.generation)
        a.applyBehaviorProposal(prop(0.9), 1L)
        assertEquals(1, a.current.generation)
        a.applyBehaviorProposal(prop(0.6, ev("e1")), 1L)
        assertEquals(2, a.current.generation)
    }

    @Test fun driftIsDeterministicAndIgnoresVolatileData() {
        val s1 = IdentityState.initial(genome)
        val s2 = s1.evolve { it.copy(behaviorProfile = it.behaviorProfile.with(BehaviorTrait.CREATIVITY, 0.6)) }
        val d1 = IdentityDrift.compute(genome, s1, s2); val d2 = IdentityDrift.compute(genome, s1, s2)
        assertEquals(d1, d2)
        assertEquals(0.0, IdentityDrift.compute(genome, s1, s1).overallDrift, 0.0)
        assertTrue(d1.behaviorDrift > 0 && d1.capabilityDrift == 0.0 && d1.communicationDrift == 0.0)
        val s3 = s1.evolve { it.copy(experienceRoot = "volatile", memoryRoot = "x") }
        assertEquals(0.0, IdentityDrift.compute(genome, s1, s3).overallDrift, 0.0)
    }

    @Test fun excessiveDriftRejected() {
        val a = ai()
        val cand = a.current.evolve {
            it.copy(behaviorProfile = BehaviorTrait.values().fold(it.behaviorProfile) { p, t -> p.with(t, 0.95) },
                communicationProfile = mapOf("tone" to "x"))
        }
        val r = IdentityUpdateValidator.validate(genome, a.current, cand)
        assertTrue(r is ValidationResult.Invalid)
        assertTrue((r as ValidationResult.Invalid).failures.any { it.code == FailureCode.EXCESSIVE_DRIFT })
    }

    @Test fun genomeMismatchRejected() {
        val a = ai()
        val cand = a.current.evolve { it.copy(cognitiveGenomeHash = "other") }
        val r = IdentityUpdateValidator.validate(genome, a.current, cand) as ValidationResult.Invalid
        assertTrue(r.failures.any { it.code == FailureCode.GENOME_MISMATCH })
    }

    @Test fun identityMismatchAndBrokenAncestryRejected() {
        val a = ai()
        val other = a.current.evolve { it.copy(identityId = "other") }
        assertTrue((IdentityUpdateValidator.validate(genome, a.current, other) as ValidationResult.Invalid)
            .failures.any { it.code == FailureCode.IDENTITY_MISMATCH })
        val skipped = a.current.evolve { it }.evolve { it }
        assertTrue((IdentityUpdateValidator.validate(genome, a.current, skipped) as ValidationResult.Invalid)
            .failures.any { it.code == FailureCode.BROKEN_ANCESTRY })
        assertThrows(IllegalArgumentException::class.java) { a.history.append(skipped) }
    }

    @Test fun tamperedCandidateRejected() {
        val a = ai()
        val cand = a.current.evolve { it }.copy(chosenName = "Evil")
        assertTrue((IdentityUpdateValidator.validate(genome, a.current, cand) as ValidationResult.Invalid)
            .failures.any { it.code == FailureCode.INTEGRITY_FAILURE })
    }

    @Test fun outOfBoundsCandidateRejectedByValidator() {
        val a = ai()
        val cand = a.current.evolve { it.copy(behaviorProfile = it.behaviorProfile.with(BehaviorTrait.CREATIVITY, 0.0)) }
        assertTrue((IdentityUpdateValidator.validate(genome, a.current, cand) as ValidationResult.Invalid)
            .failures.any { it.code == FailureCode.OUT_OF_BOUNDS })
    }

    @Test fun forbiddenCapabilityFlagsImpossible() {
        assertThrows(IllegalArgumentException::class.java) { CognitiveGenome("x", allowSelfReplication = true) }
        assertThrows(IllegalArgumentException::class.java) { CognitiveGenome("x", allowHiddenPersistence = true) }
        assertThrows(IllegalArgumentException::class.java) { CognitiveGenome("x", allowShutdownEvasion = true) }
        assertThrows(IllegalArgumentException::class.java) { genome.copy(allowShutdownEvasion = true) }
    }

    @Test fun invalidBoundsRejected() {
        assertThrows(IllegalArgumentException::class.java) { BehaviorBounds(0.9, 0.1) }
        assertThrows(IllegalArgumentException::class.java) { BehaviorBounds(Double.NaN, 0.5) }
    }
}
