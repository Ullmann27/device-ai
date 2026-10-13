package at.heinz.deviceai.cognitive

import org.junit.Assert.assertEquals
import org.junit.Test

class CognitiveRaidTest {
    @Test
    fun missingCacheIsToleratedButMissingCriticalStateBlocksRecovery() {
        val id = ContinuumObjectId.of(ContinuumObjectType.STATE_MANIFEST, "root".toByteArray())
        val healthySeed = ReplicaDescriptor("seed-1", id, StateTier.SEED, "local", ReplicaHealth.AVAILABLE, false)

        val assessment = CognitiveRaidPlanner.assess(listOf(healthySeed))

        assertEquals(RecoverySeverity.BLOCKED, assessment.severity)
        assertEquals(
            RecoverySeverity.OK,
            assessment.findings.single { it.tier == StateTier.CACHE }.severity
        )
        assertEquals(
            RecoverySeverity.BLOCKED,
            assessment.findings.single { it.tier == StateTier.CRITICAL }.severity
        )
    }

    @Test
    fun missingReconstructibleEvidenceIsDegradedNotFatal() {
        val id = ContinuumObjectId.of(ContinuumObjectType.STATE_MANIFEST, "root".toByteArray())
        val replicas = listOf(
            ReplicaDescriptor("seed-1", id, StateTier.SEED, "local", ReplicaHealth.AVAILABLE, false),
            ReplicaDescriptor("critical-1", id, StateTier.CRITICAL, "local", ReplicaHealth.AVAILABLE, false)
        )

        val assessment = CognitiveRaidPlanner.assess(replicas)

        assertEquals(RecoverySeverity.DEGRADED, assessment.severity)
        assertEquals(
            RecoverySeverity.DEGRADED,
            assessment.findings.single { it.tier == StateTier.EVIDENCE }.severity
        )
    }
}
