package at.heinz.deviceai.learning.span

import org.junit.Assert.*
import org.junit.Test
import at.heinz.deviceai.learning.span.DualEvidenceReadContract.FetchPlan
import at.heinz.deviceai.learning.span.DualEvidenceReadContract.Mode

class DualEvidenceReadContractTest {
    private fun snapshot(text: String = "Ylva ist in Graz.", revision: Int = 1) = LocationEvidenceSnapshot.freeze(listOf(
        EvidenceBoundNaturalLocationProbe.Evidence("archive", revision, EvidenceBoundNaturalLocationProbe.digest(text), text)))

    @Test fun explicitOfflineVetoWinsAgainstEveryPositiveSettingAndApproval() {
        for (mode in DualEvidenceReadContract.Mode.entries) assertEquals(DualEvidenceReadContract.FetchPlan.OFFLINE_VETO,
            DualEvidenceReadContract.plan("Antworte ohne Internetrecherche",mode,true,true,true))
    }
    @Test fun offAskAlwaysAndNetworkPermissionRemainSeparate() {
        val p=DualEvidenceReadContract
        assertEquals(FetchPlan.LOCAL_ONLY,p.plan("Recherche",networkOnline=true,networkPermissionGranted=true))
        assertEquals(FetchPlan.ASK_OWNER,p.plan("Recherche",Mode.ASK,true,true))
        assertEquals(FetchPlan.READ_ONLY_ELIGIBLE,p.plan("Recherche",Mode.ASK,true,true,true))
        assertEquals(FetchPlan.READ_ONLY_ELIGIBLE,p.plan("Recherche",Mode.ALWAYS_ON,true,true))
        assertEquals(FetchPlan.NO_NETWORK,p.plan("Recherche",Mode.ALWAYS_ON,false,true,true))
        assertEquals(FetchPlan.PERMISSION_REQUIRED,p.plan("Recherche",Mode.ALWAYS_ON,true,false,true))
    }
    @Test fun revisionContentOrTimeChangeInvalidatesCurrentAnswerButOfflineCacheCanRemainCurrent() {
        val s=snapshot()
        val lease=DualEvidenceReadContract.Lease(s,DualEvidenceReadContract.Origin.WEB_OBSERVATION,100,200)
        assertTrue(lease.isCurrent(150,s))
        assertFalse(lease.grantsOwnerAuthority)
        assertFalse(lease.isCurrent(99,s)); assertFalse(lease.isCurrent(200,s))
        assertFalse(lease.isCurrent(150,snapshot(revision=2)))
        assertFalse(lease.isCurrent(150,snapshot("Ylva ist in Wien.")))
        assertTrue(runCatching { lease.copy(expiresAtMillis=100) }.isFailure)
    }
}
