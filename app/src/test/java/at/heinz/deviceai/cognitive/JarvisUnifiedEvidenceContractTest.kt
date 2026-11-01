package at.heinz.deviceai.cognitive

import org.junit.Assert.*
import org.junit.Test

class JarvisUnifiedEvidenceContractTest {
    private fun evidence(origin: JarvisUnifiedEvidenceContract.Origin,
                         source: String, text: String, expires: Long,
                         privacy: JarvisUnifiedEvidenceContract.Privacy =
                             JarvisUnifiedEvidenceContract.Privacy.PUBLIC) =
        JarvisUnifiedEvidenceContract.Evidence(origin, privacy, source, 1, 10, expires,
            text, JarvisUnifiedEvidenceContract.hash(text))

    @Test fun offlineAnswersCannotReceiveLiveWebEvidenceAutomatically() {
        val local = evidence(JarvisUnifiedEvidenceContract.Origin.REVIEWED_ARCHIVE,
            "archive:5", "Anna ist in Graz.", 100)
        val web = evidence(JarvisUnifiedEvidenceContract.Origin.PUBLIC_WEB,
            "https://www.ris.bka.gv.at/eli/jgs/1811/946/P1295/NOR12019037",
            "Das Gesetz wird hier nur als synthetische Testaussage zitiert.", 100)
        assertEquals(listOf(local),
            JarvisUnifiedEvidenceContract.useForAnswer(listOf(local,web),50,true))
        assertEquals(2, JarvisUnifiedEvidenceContract.useForAnswer(listOf(local,web),50,false).size)
        assertTrue(JarvisUnifiedEvidenceContract.useForAnswer(listOf(local),150,true).isEmpty())
        assertFalse(local.grantsOwnerAuthority)
    }

    @Test fun privateLocalDataCanNeverBecomePublicWebEvidence() {
        val text = "Nur auf dem Gerät."
        assertTrue(runCatching {
            JarvisUnifiedEvidenceContract.Evidence(
                JarvisUnifiedEvidenceContract.Origin.PUBLIC_WEB,
                JarvisUnifiedEvidenceContract.Privacy.DEVICE_LOCAL_ONLY,
                "https://www.ris.bka.gv.at/NormDokument.wxe",1,10,100,text,
                JarvisUnifiedEvidenceContract.hash(text))
        }.isFailure)
        assertTrue(runCatching {
            JarvisUnifiedEvidenceContract.Evidence(
                JarvisUnifiedEvidenceContract.Origin.PUBLIC_WEB,
                JarvisUnifiedEvidenceContract.Privacy.PUBLIC,
                "https://www.ris.bka.gv.at.evil.invalid/a",1,10,100,text,
                "0".repeat(64))
        }.isFailure)
    }
}
