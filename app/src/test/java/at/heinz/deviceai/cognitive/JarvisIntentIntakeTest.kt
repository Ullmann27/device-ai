package at.heinz.deviceai.cognitive

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files

class JarvisIntentIntakeTest {
    private val now = 1_700_000_000_000L

    private fun withBacklog(block: (DeviceDevelopmentBacklog) -> Unit) {
        val dir = Files.createTempDirectory("jarvis-intent-intake").toFile()
        try {
            block(DeviceDevelopmentBacklog(dir) { now })
        } finally {
            dir.deleteRecursively()
        }
    }

    private fun voice(
        id: String = "voice-0001",
        text: String = "Repariere die Kameravorschau"
    ) = JarvisIntentEvent(
        sourceEventId = id,
        origin = JarvisIntentOrigin.OWNER_VOICE,
        kind = JarvisIntentKind.DEVELOPMENT,
        summary = text
    )

    @Test fun defaultDeniesOwnerAndExternalRequestsWithoutQueuing() = withBacklog { backlog ->
        val intake = JarvisIntentIntake(backlog)
        assertEquals(
            JarvisIntentIntakeResult.Rejected("INTENT_NOT_AUTHORIZED"),
            intake.propose(voice())
        )
        assertEquals(
            JarvisIntentIntakeResult.Rejected("INTENT_NOT_AUTHORIZED"),
            intake.propose(voice().copy(origin = JarvisIntentOrigin.EXTERNAL_NOTIFICATION))
        )
        assertTrue(backlog.views().isEmpty())
    }

    @Test fun authorizedOwnerVoiceCreatesOnlyProposedTaskAndDeduplicates() =
        withBacklog { backlog ->
            val intake = JarvisIntentIntake(backlog, JarvisOwnerIntentVerifier { true })
            val first = intake.propose(voice())
            assertTrue(first is JarvisIntentIntakeResult.Proposed)
            first as JarvisIntentIntakeResult.Proposed
            assertFalse(first.alreadyProposed)
            assertTrue(first.taskId.startsWith("jarvis-"))
            val only = backlog.views().single()
            assertEquals(first.taskId, only.id)
            assertEquals("PROPOSED", only.state)
            assertEquals("IMPLEMENT", only.kind)
            assertEquals("android", only.source)
            assertEquals("Repariere die Kameravorschau", only.title)

            val again = intake.propose(voice())
            assertEquals(
                JarvisIntentIntakeResult.Proposed(first.taskId, alreadyProposed = true),
                again
            )
            assertEquals(1, backlog.views().size)
            assertEquals("PROPOSED", backlog.views().single().state)
        }

    @Test fun conflictingReplayIsRejectedWithoutChangingFirstProposal() =
        withBacklog { backlog ->
            val intake = JarvisIntentIntake(backlog, JarvisOwnerIntentVerifier { true })
            val first = intake.propose(voice())
            assertTrue(first is JarvisIntentIntakeResult.Proposed)
            assertEquals(
                JarvisIntentIntakeResult.Rejected("EVENT_ID_COLLISION"),
                intake.propose(voice(text = "Loesche den gesamten Quellcode"))
            )
            assertEquals("Repariere die Kameravorschau", backlog.views().single().title)
            assertEquals("PROPOSED", backlog.views().single().state)
            assertEquals(1, backlog.views().size)
        }

    @Test fun deviceDiagnosticsNeedExplicitOptInAndNeverAuthorizeCodeWork() =
        withBacklog { backlog ->
            val diagnostic = JarvisIntentEvent(
                "battery-event-1",
                JarvisIntentOrigin.DEVICE_SIGNAL,
                JarvisIntentKind.DIAGNOSTIC,
                "Batteriebelastung pruefen"
            )
            assertEquals(
                JarvisIntentIntakeResult.Rejected("INTENT_NOT_AUTHORIZED"),
                JarvisIntentIntake(backlog).propose(diagnostic)
            )
            val enabled = JarvisIntentIntake(
                backlog,
                passiveDeviceDiagnosticsEnabled = { true }
            )
            val proposal = enabled.propose(diagnostic)
            assertTrue(proposal is JarvisIntentIntakeResult.Proposed)
            assertEquals("PROPOSED", backlog.views().single().state)
            assertEquals("INVESTIGATE", backlog.views().single().kind)
            assertEquals(
                JarvisIntentIntakeResult.Rejected("INTENT_NOT_AUTHORIZED"),
                enabled.propose(diagnostic.copy(
                    sourceEventId = "battery-event-2",
                    kind = JarvisIntentKind.DEVELOPMENT
                ))
            )
            assertEquals(1, backlog.views().size)
        }

    @Test fun malformedIdsAndEventTextsCannotReachBacklog() = withBacklog { backlog ->
        val enabled = JarvisIntentIntake(backlog, JarvisOwnerIntentVerifier { true })
        for (id in listOf("", "../escape", "a".repeat(129))) {
            assertEquals(
                JarvisIntentIntakeResult.Rejected("INVALID_EVENT_ID"),
                enabled.propose(voice(id = id))
            )
        }
        for (summary in listOf("", " \n ", "A\nB", "x".repeat(201),
            "bad\uD800surrogate")) {
            assertEquals(
                JarvisIntentIntakeResult.Rejected("INVALID_EVENT_SUMMARY"),
                enabled.propose(voice(text = summary))
            )
        }
        assertTrue(backlog.views().isEmpty())
    }

    @Test fun manualAcceptanceRemainsSeparateAndNeverOccursDuringIntake() =
        withBacklog { backlog ->
            val enabled = JarvisIntentIntake(backlog, JarvisOwnerIntentVerifier { true })
            val proposal = enabled.propose(voice()) as JarvisIntentIntakeResult.Proposed
            assertEquals("PROPOSED", backlog.views().single().state)
            // A trusted UI action, not the intake or model, must invoke the
            // existing backlog decision method to move the proposal forward.
            assertTrue(backlog.decide(proposal.taskId, "accept", null).ok)
            assertEquals("READY", backlog.views().single().state)
            assertEquals(
                JarvisIntentIntakeResult.Proposed(proposal.taskId, alreadyProposed = true),
                enabled.propose(voice())
            )
            assertEquals("READY", backlog.views().single().state)
        }
}
