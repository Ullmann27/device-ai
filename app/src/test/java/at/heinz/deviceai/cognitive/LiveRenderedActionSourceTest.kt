package at.heinz.deviceai.cognitive

import org.junit.Assert.*
import org.junit.Test

class LiveRenderedActionSourceTest {
    private val binding = LiveFrameBinding("task", 7, "example.target", "example.owner",
        LiveFrameOrigin.PROJECTION_WINDOW_CROP)
    private val bounds = LiveTargetBounds(.2f, .2f, .4f, .4f)
    private val source = ObservationStamp("task", 3, 11, "example.target", 42, "target", 1_000_000_000,
        frameSequence = 5, captureSession = 7)
    private val meta = LiveFrameMeta(7, 900_000_000, 400, 600, "example.target", 42, false,
        observationRevision = 11, sourceViewport = LiveTargetBounds(0f, 0f, 1f, .7f))
    private val shown = LiveMonitorStatus(LiveMonitorState.LIVE, null, binding, meta, 5, 100)

    private fun check(status: LiveMonitorStatus? = shown, observation: ObservationStamp = source,
                      target: LiveTargetBounds? = bounds) =
        LiveMonitorSubmission.checkActionSource(status, observation, "example.owner", target)

    private fun refuses(reason: String, block: () -> Unit) {
        try { block(); fail("Input source must be refused") }
        catch (error: IllegalStateException) { assertEquals(reason, error.message) }
    }

    @Test fun matchingSubmittedPixelsPermitTheVisibleTarget() { check() }

    @Test fun freshImageOfPreviousObservationCannotAuthorizeInput() {
        refuses("COMPUTER_RENDERED_OBSERVATION_MISMATCH") {
            check(shown.copy(meta = meta.copy(observationRevision = 10)))
        }
    }

    @Test fun newerImageCannotAuthorizeAnOlderTarget() {
        refuses("COMPUTER_RENDERED_OBSERVATION_MISMATCH") {
            check(shown.copy(meta = meta.copy(observationRevision = 12)))
        }
    }

    @Test fun unknownRevisionCannotAuthorizeInput() {
        for (revision in listOf(-1L, 0L)) {
            refuses("COMPUTER_RENDERED_OBSERVATION_MISMATCH") {
                check(shown.copy(meta = meta.copy(observationRevision = revision)), source.copy(revision = revision))
            }
        }
    }

    @Test fun sourceIdentityAndFreshnessRemainMandatory() {
        val invalid = listOf(
            shown.copy(state = LiveMonitorState.PAUSED), shown.copy(ageMillis = 1_501),
            shown.copy(ageMillis = -1), shown.copy(sequence = 0),
            shown.copy(binding = binding.copy(taskId = "other")),
            shown.copy(binding = binding.copy(ownPackage = "example.other")),
            shown.copy(meta = meta.copy(sourceWindowId = 43)),
            shown.copy(meta = meta.copy(sourcePackage = "example.other")),
            shown.copy(meta = meta.copy(captureSession = 8))
        )
        for (status in invalid) refuses("COMPUTER_RENDERED_SOURCE_MISMATCH") { check(status) }
        refuses("COMPUTER_RENDERED_SOURCE_REQUIRED") { check(null) }
        refuses("COMPUTER_RENDERED_SOURCE_MISMATCH") { check(observation = source.copy(captureSession = 0)) }
    }

    @Test fun targetsOutsideActualCropRemainUnavailable() {
        refuses("TARGET_OUTSIDE_MONITOR_VIEWPORT") {
            check(target = LiveTargetBounds(.2f, .65f, .4f, .8f))
        }
        refuses("TARGET_BOUNDS_REQUIRED") { check(target = null) }
        refuses("COMPUTER_RENDERED_VIEWPORT_REQUIRED") { check(shown.copy(meta = meta.copy(sourceViewport = null))) }
    }

    @Test fun displayableOlderFrameDoesNotBecomeAnActionReceipt() {
        val submission = LiveMonitorSubmission()
        val imported = requireNotNull(submission.imported(shown, 1_000_000_000))
        assertTrue(submission.submitted(imported, shown, 1_000_000_000))
        val newer = shown.copy(sequence = 6, meta = meta.copy(observationRevision = 12))
        assertTrue(submission.isReady(newer, 1_050_000_000))
        refuses("COMPUTER_RENDERED_OBSERVATION_MISMATCH") {
            check(submission.submittedFrame(newer, 1_050_000_000), source.copy(revision = 12))
        }
    }
}
