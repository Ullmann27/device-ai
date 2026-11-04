package at.heinz.deviceai.chief

import java.nio.file.Files
import org.junit.Assert.*
import org.junit.Test

class ChiefBotTaskIntakeTest {
    private fun withStore(body: (ChiefBotProfileStore) -> Unit) {
        val root = Files.createTempDirectory("bot-task-intake").toFile()
        try { body(ChiefBotProfileStore(root.resolve("bots")).also { it.seedDefaults(20) }) }
        finally { root.deleteRecursively() }
    }

    @Test fun completeOwnerTaskAndStableBotIdentitySurvivePreparation() = withStore { store ->
        val profile = requireNotNull(store.get("bot-kora"))
        val text = "Prüfe den aktuellen Status.\nÖffne keine App und verwende kein Internet."
        val request = ChiefBotTaskRequest(profile.agentId, profile.profileVersion, text, ChiefBotTaskMode.LOCAL_TOOLS)
        val task = ChiefBotTaskIntake(store).prepare(request, "session-one")
        assertEquals(text, task.request.text)
        assertEquals(profile, task.profile)
        assertTrue(task.claim("session-one"))
        assertFalse("A double tap must not claim the same work twice", task.claim("session-one"))
        assertTrue(task.isCurrent("session-one"))
    }

    @Test fun renamedOrEditedProfileInvalidatesAVisibleDraft() = withStore { store ->
        val profile = requireNotNull(store.get("bot-kora"))
        val request = ChiefBotTaskRequest(profile.agentId, profile.profileVersion, "Erkläre den Fehler.", ChiefBotTaskMode.PRIVATE_REPLY)
        store.rename(profile.agentId, "Kora Neu", profile.profileVersion)
        assertEquals("BOT_TASK_PROFILE_CHANGED", runCatching {
            ChiefBotTaskIntake(store).prepare(request, "session-one")
        }.exceptionOrNull()?.message)
    }

    @Test fun preparedTaskCannotCrossASessionOrRevocation() = withStore { store ->
        val profile = requireNotNull(store.get("bot-atlas"))
        val request = ChiefBotTaskRequest(profile.agentId, profile.profileVersion, "Plane drei Schritte.", ChiefBotTaskMode.LOCAL_TOOLS)
        val task = ChiefBotTaskIntake(store).prepare(request, "session-one")
        assertFalse(task.claim("session-two"))
        assertFalse(task.isCurrent("session-two"))
        assertTrue(task.claim("session-one"))
        task.cancel()
        assertFalse(task.isCurrent("session-one"))
        assertFalse(task.claim("session-one"))
        assertEquals("BOT_TASK_CANCELLED", runCatching { task.reserveModelCall() }.exceptionOrNull()?.message)
    }

    @Test fun freshProfileRevisionAndMissingBotFailClosed() = withStore { store ->
        val profile = requireNotNull(store.get("bot-atlas"))
        val task = ChiefBotTaskIntake(store).prepare(ChiefBotTaskRequest(profile.agentId,
            profile.profileVersion, "Erkläre den Status.", ChiefBotTaskMode.LOCAL_TOOLS), "session-one")
        assertTrue(task.claim("session-one"))
        store.updateDescription(profile.agentId, profile.role, "Sehr kurz und direkt.", profile.profileVersion)
        assertFalse(task.profileCurrent())
        assertEquals("BOT_TASK_PROFILE_MISSING", runCatching {
            ChiefBotTaskIntake(store).prepare(ChiefBotTaskRequest("bot-missing", 1,
                "Erkläre den Status.", ChiefBotTaskMode.PRIVATE_REPLY), "session-one")
        }.exceptionOrNull()?.message)
    }

    @Test fun utf8AndLateNegationAreNeverSilentlyShortened() {
        val suffix = " Ohne Internet und Löschen."
        val request = "a".repeat(4_000 - suffix.toByteArray(Charsets.UTF_8).size) + suffix
        val accepted = ChiefBotTaskRequest("bot-kora", 1, request, ChiefBotTaskMode.PRIVATE_REPLY)
        assertEquals(request, accepted.text)
        assertThrows(IllegalArgumentException::class.java) {
            ChiefBotTaskRequest("bot-kora", 1, "😀".repeat(1_001), ChiefBotTaskMode.PRIVATE_REPLY)
        }
        assertThrows(IllegalArgumentException::class.java) {
            ChiefBotTaskRequest("bot-kora", 1, "unvollständig\uD800", ChiefBotTaskMode.PRIVATE_REPLY)
        }
        assertThrows(IllegalArgumentException::class.java) {
            ChiefBotTaskRequest("bot-kora", 1, "a".repeat(601), ChiefBotTaskMode.CODING_DRAFT)
        }
    }

    @Test fun ownerTaskModelCallsAreFiniteAndDeadlineIsMonotonic() = withStore { store ->
        var now = 10_000L
        val profile = requireNotNull(store.get("bot-atlas"))
        val request = ChiefBotTaskRequest(profile.agentId, profile.profileVersion, "Prüfe den Status.", ChiefBotTaskMode.LOCAL_TOOLS)
        val intake = ChiefBotTaskIntake(store) { now }
        val task = intake.prepare(request, "session-one")
        assertTrue(task.claim("session-one"))
        repeat(ChiefBotTaskTicket.MAX_OWNER_MODEL_CALLS) { task.reserveModelCall() }
        assertEquals("BOT_TASK_MODEL_BUDGET_EXHAUSTED", runCatching { task.reserveModelCall() }.exceptionOrNull()?.message)
        val expiring = intake.prepare(request, "session-one")
        assertTrue(expiring.claim("session-one"))
        now += java.util.concurrent.TimeUnit.MILLISECONDS.toNanos(profile.resourceBudget.deadlineMillis)
        assertEquals(0L, expiring.remainingMillis())
        assertFalse(expiring.isCurrent("session-one"))
        assertEquals("BOT_TASK_DEADLINE_EXCEEDED", runCatching { expiring.reserveModelCall() }.exceptionOrNull()?.message)
    }
}
