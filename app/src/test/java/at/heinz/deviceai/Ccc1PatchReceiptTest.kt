package at.heinz.deviceai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * M2: CCC1 RED→GREEN-Patch-Quittung als prüfbares Datenobjekt.
 * Fixture = reale M1-Quittung aus PR #369 (LocalFollowUpTaskFrameTest).
 */
class Ccc1PatchReceiptTest {
    private val target = listOf(
        "at.heinz.deviceai.cognitive.LocalFollowUpTaskFrameTest.t2CorrectionTaskCarriesTheEarlierBlueSevenAsTaskData",
        "at.heinz.deviceai.cognitive.LocalFollowUpTaskFrameTest.t3HistoricalQuestionKeepsChronologicalOrderOfAllOwnerTurns",
        "at.heinz.deviceai.cognitive.LocalFollowUpTaskFrameTest.frameMarksOwnerTurnsAsEphemeralTaskDataNotMemoryOrPermission")
    private val base = "85602f73e92d6ed005b6b918036f0bfcc0b4f41c"
    private val red = "988ff152dcf9a9315e6e17c948c961bcae88655e"
    private val green = "cf4620e0c8ea572d4e8cd149be11ca9bdac714eb"
    // sha256 of `git diff --binary --full-index red green` in Ullmann27/device-ai (was "a"*64 in M2).
    private val patch = "f9c032aa70be9b30562f25213ce6d922b180c4cf618651468ebadd8b45100b9b"
    private val cmd = "./gradlew :app:testDebugUnitTest --tests at.heinz.deviceai.cognitive.LocalFollowUpTaskFrameTest"

    private fun receipt() = Ccc1PatchReceipt(
        repo = "Ullmann27/device-ai", baseSha = base, redSha = red, greenSha = green,
        patchSha256 = patch, targetTests = target,
        red = Ccc1TestRun(red, cmd, executed = 7, failed = 3, skipped = 0, failingTests = target,
            passedTargets = emptyList(), exitCode = 1),
        green = Ccc1TestRun(green, "./gradlew :app:testDebugUnitTest", executed = 1752, failed = 0, skipped = 5,
            failingTests = emptyList(), passedTargets = target, exitCode = 0))

    private fun verdict(r: Ccc1PatchReceipt) = r.evaluate().verdict

    @Test fun realM1ReceiptIsOnlySelfConsistentWithoutAttestation() {
        val e = receipt().evaluate()
        assertEquals(e.reasons.toString(), Ccc1PatchReceipt.Verdict.SELF_CONSISTENT_UNATTESTED, e.verdict)
    }

    private fun sha(s: String) = java.security.MessageDigest.getInstance("SHA-256")
        .digest(s.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
    private fun attestation(r: Ccc1PatchReceipt, verdict: String = "VERIFIED", receiptSha: String = sha(r.toJson()),
                            patchSha: String = r.patchSha256, redFailed: Long = 3, greenFailed: Long = 0, reasons: String = "") =
        "{\"schema\":\"continuum-ccc1-redgreen-attestation\",\"version\":1,\"receiptSha256\":\"$receiptSha\"," +
            "\"verdict\":\"$verdict\",\"reasons\":[$reasons],\"command\":\"node --test\",\"patchSha256\":\"$patchSha\"," +
            "\"red\":{\"xmlSha256\":\"${"c".repeat(64)}\",\"exitCode\":1,\"executed\":7,\"failed\":$redFailed}," +
            "\"green\":{\"xmlSha256\":\"${"d".repeat(64)}\",\"exitCode\":0,\"executed\":1752,\"failed\":$greenFailed}}"

    // M3 / Review #370 Punkt 2: GREEN nur mit Attest des Host-Verifiers, gebunden an genau diese Quittung.
    @Test fun greenNeedsAHostAttestationBoundToExactlyThisReceipt() {
        val r = receipt()
        assertEquals(Ccc1PatchReceipt.Verdict.GREEN_ATTESTED_PENDING_REVIEW, r.evaluateAttested(attestation(r)).verdict)
        val other = r.copy(targetTests = target.take(2), red = r.red!!.copy(failingTests = target.take(2)),
            green = r.green!!.copy(passedTargets = target.take(2)))
        // attestation of a different receipt, a rejected run, a patch mismatch or counts that differ: never GREEN
        listOf(
            r.evaluateAttested(attestation(other)),
            r.evaluateAttested(attestation(r, verdict = "REJECTED", reasons = "\"PATCH_HASH_MISMATCH\"")),
            r.evaluateAttested(attestation(r, patchSha = "e".repeat(64))),
            r.evaluateAttested(attestation(r, redFailed = 0)),
            r.evaluateAttested(attestation(r, greenFailed = 1)),
            r.evaluateAttested("{}"),
            r.evaluateAttested(attestation(r).replace("redgreen-attestation", "other"))
        ).forEach { assertTrue(it.toString(), it.verdict != Ccc1PatchReceipt.Verdict.GREEN_ATTESTED_PENDING_REVIEW) }
        // An invalid receipt never becomes green through any attestation.
        val broken = r.copy(green = r.green!!.copy(failed = 1))
        assertEquals(Ccc1PatchReceipt.Verdict.INVALID, broken.evaluateAttested(attestation(broken)).verdict)
    }

    @Test fun redMustReallyFailOnTheTargetTests() {
        val r = receipt()
        assertEquals(Ccc1PatchReceipt.Verdict.INVALID, verdict(r.copy(red = r.red!!.copy(failed = 0, failingTests = emptyList(), exitCode = 0))))
        assertEquals(Ccc1PatchReceipt.Verdict.INVALID, verdict(r.copy(red = r.red!!.copy(failingTests = listOf("other.Test.x")))))
        assertEquals(Ccc1PatchReceipt.Verdict.INVALID, verdict(r.copy(red = r.red!!.copy(sha = green))))
    }

    @Test fun greenMustPassEveryTargetWithZeroFailuresOnItsOwnSha() {
        val r = receipt()
        assertEquals(Ccc1PatchReceipt.Verdict.INVALID, verdict(r.copy(green = r.green!!.copy(failed = 1, exitCode = 1))))
        assertEquals(Ccc1PatchReceipt.Verdict.INVALID, verdict(r.copy(green = r.green!!.copy(passedTargets = target.drop(1)))))
        assertEquals(Ccc1PatchReceipt.Verdict.INVALID, verdict(r.copy(green = r.green!!.copy(sha = red))))
        assertEquals(Ccc1PatchReceipt.Verdict.RED_ONLY, verdict(r.copy(green = null)))
    }

    @Test fun timeoutCancelOrMissingExitIsUnknownNeverGreen() {
        val r = receipt()
        assertEquals(Ccc1PatchReceipt.Verdict.UNKNOWN, verdict(r.copy(green = r.green!!.copy(timedOut = true))))
        assertEquals(Ccc1PatchReceipt.Verdict.UNKNOWN, verdict(r.copy(green = r.green!!.copy(cancelled = true))))
        assertEquals(Ccc1PatchReceipt.Verdict.UNKNOWN, verdict(r.copy(red = r.red!!.copy(exitCode = null))))
    }

    @Test fun mergeInstallOrDeviceClaimsAndMalformedIdsAreInvalid() {
        val r = receipt()
        assertEquals(Ccc1PatchReceipt.Verdict.INVALID, verdict(r.copy(approvedForMerge = true)))
        assertEquals(Ccc1PatchReceipt.Verdict.INVALID, verdict(r.copy(installed = true)))
        assertEquals(Ccc1PatchReceipt.Verdict.INVALID, verdict(r.copy(deviceVerified = true)))
        assertEquals(Ccc1PatchReceipt.Verdict.INVALID, verdict(r.copy(baseSha = "main")))
        assertEquals(Ccc1PatchReceipt.Verdict.INVALID, verdict(r.copy(patchSha256 = "xyz")))
        assertEquals(Ccc1PatchReceipt.Verdict.INVALID, verdict(r.copy(greenSha = base, green = r.green!!.copy(sha = base))))
        assertEquals(Ccc1PatchReceipt.Verdict.INVALID, verdict(r.copy(targetTests = emptyList())))
    }

    @Test fun canonicalJsonRoundTripsAndRejectsForgedOrUnknownSchema() {
        val r = receipt()
        val json = r.toJson()
        assertTrue(json, json.contains("\"schema\":\"continuum-ccc1-patch-receipt\""))
        assertEquals(r, Ccc1PatchReceipt.parse(json))
        assertEquals(json, Ccc1PatchReceipt.parse(json)!!.toJson())
        assertEquals(null, Ccc1PatchReceipt.parse(json.replace("continuum-ccc1-patch-receipt", "x")))
        assertEquals(null, Ccc1PatchReceipt.parse("{\"schema\":\"continuum-ccc1-patch-receipt\"}"))
        assertEquals(null, Ccc1PatchReceipt.parse("not json"))
    }
}
