package at.heinz.deviceai.cognitive

import org.junit.Assert.assertEquals
import org.junit.Test

class EntrenchmentAwareRetentionPolicyTest {
    private fun metrics(
        protectedCore:Boolean=false, explicit:Boolean=false, dependents:Int=0, access:Int=0,
        days:Float=0f, superseded:Boolean=false, contradiction:Boolean=false
    )=EntrenchmentAwareRetentionPolicy.Metrics(
        protectedCore,explicit,dependents,access,days,superseded,contradiction)

    @Test fun protectedAndStructurallyRequiredMemoryNeverBecomesPurgeCandidate() {
        assertEquals(EntrenchmentAwareRetentionPolicy.Stage.HOT_ACTIVE,
            EntrenchmentAwareRetentionPolicy.stage(metrics(protectedCore=true,days=1000f)))
        assertEquals(EntrenchmentAwareRetentionPolicy.Stage.HOT_ACTIVE,
            EntrenchmentAwareRetentionPolicy.stage(metrics(dependents=1,days=1000f)))
        assertEquals(EntrenchmentAwareRetentionPolicy.Stage.HOT_ACTIVE,
            EntrenchmentAwareRetentionPolicy.stage(metrics(contradiction=true,days=1000f)))
    }

    @Test fun revokedHistoryArchivesInsteadOfBeingForgotten() {
        assertEquals(EntrenchmentAwareRetentionPolicy.Stage.WARM_INDEXED,
            EntrenchmentAwareRetentionPolicy.stage(metrics(days=30f,superseded=true)))
        assertEquals(EntrenchmentAwareRetentionPolicy.Stage.COLD_ARCHIVE,
            EntrenchmentAwareRetentionPolicy.stage(metrics(days=200f,superseded=true)))
    }

    @Test fun onlyOldOrphanedLowUseItemBecomesPurgeEligible() {
        assertEquals(EntrenchmentAwareRetentionPolicy.Stage.PURGE_ELIGIBLE,
            EntrenchmentAwareRetentionPolicy.stage(metrics(days=365f)))
        assertEquals(EntrenchmentAwareRetentionPolicy.Stage.HOT_ACTIVE,
            EntrenchmentAwareRetentionPolicy.stage(metrics(access=50,days=365f)))
    }
}
