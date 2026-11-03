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
    private val cmd = "./gradlew :app:testDebugUnitTest --tests at.heinz.deviceai.cognitive.LocalFollowUpTaskFrameTest"

    private fun receipt() = Ccc1PatchReceipt(
        repo = "Ullmann27/device-ai", baseSha = base, redSha = red, greenSha = green,
        patchSha256 = "a".repeat(64), targetTests = target,
        red = Ccc1TestRun(red, cmd, executed = 7, failed = 3, skipped = 0, failingTests = target,
            passedTargets = emptyList(), exitCode = 1),
        green = Ccc1TestRun(green, "./gradlew :app:testDebugUnitTest", executed = 1752, failed = 0, skipped = 5,
            failingTests = emptyList(), passedTargets = target, exitCode = 0))

    private fun verdict(r: Ccc1PatchReceipt) = r.evaluate().verdict

    @Test fun realM1ReceiptIsGreenPendingReviewNeverMergeReady() {
        val e = receipt().evaluate()
        assertEquals(e.reasons.toString(), Ccc1PatchReceipt.Verdict.GREEN_VERIFIED_PENDING_REVIEW, e.verdict)
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
