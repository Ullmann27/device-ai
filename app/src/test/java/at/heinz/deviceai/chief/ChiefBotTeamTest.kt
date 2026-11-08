package at.heinz.deviceai.chief

import at.heinz.deviceai.cognitive.DeliberationCancellation
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.nio.file.Files
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/** Controlled model port, real coordinator/pool/orchestrator and on-disk store. Not LLM quality proof. */
class ChiefBotTeamTest {
    @get:Rule val folder = TemporaryFolder()
    private val sha = "a".repeat(64)
    private fun store() = ChiefBotProfileStore(folder.newFolder("bots"))
    private fun id() = "team-" + UUID.randomUUID()
    private class Model(var sha: String?, val reply: (String, DeliberationCancellation) -> String = { _, _ -> "Analyse ungeprüft." }) : ChiefTeamModel {
        val calls = AtomicInteger()
        val prompts = java.util.concurrent.CopyOnWriteArrayList<String>()
        override fun readySha256() = sha
        override fun generate(prompt: String, cancellation: DeliberationCancellation, deadlineMillis: Long): String {
            calls.incrementAndGet(); prompts += prompt
            return reply(prompt, cancellation)
        }
    }
    private fun configured(store: ChiefBotProfileStore, model: ChiefTeamModel): ChiefBotTeam =
        ChiefBotTeam(store, model).also {
            it.initialize()
            it.configure("bot-atlas", listOf("bot-kora", "bot-lint"), 0)
        }

    @Test fun twoRealOrchestratorStagesPreserveProfilesAndPersistBoundResults() {
        val s = store()
        s.seedDefaults(20)
        val p = s.get("bot-kora")!!
        s.saveWorkingContext(p.agentId, p.agentId, "PRIVATE_NOTE_NEVER_SHARED", p.profileVersion)
        val before = s.page(limit = 100).items
        val model = Model(sha)
        val team = configured(s, model)
        try {
            val task = team.submit(id(), "Prüfe diesen Kotlin-Ausdruck: value ?: 20.", 1, true)
            assertTrue(team.awaitIdle(10_000))
            val result = s.teamState().tasks.single()
            assertEquals(ChiefTeamStatus.RESPONSES_RECORDED, result.status)
            assertEquals(2, model.calls.get())
            assertEquals(listOf("bot-kora", "bot-lint"), result.responses.map { it.agentId })
            assertTrue(model.prompts[1].contains(result.responses[0].outputSha256))
            assertFalse(model.prompts.joinToString().contains("PRIVATE_NOTE_NEVER_SHARED"))
            assertEquals(before, s.page(limit = 100).items)
            assertEquals(task.id, result.id)
            assertEquals(sha, result.modelSha256)
            assertNull(team.storageError)
        } finally { team.close() }
    }

    @Test fun duplicateRequestSurvivesReopeningWithoutCallingModelAgain() {
        val s = store(); val model = Model(sha); val team = configured(s, model)
        val requestId = id()
        team.submit(requestId, "Text prüfen", 1, true)
        assertTrue(team.awaitIdle(10_000)); team.close()
        val next = ChiefBotTeam(s, model)
        try {
            next.initialize()
            val duplicate = next.submit(requestId, "Text prüfen", 1, true)
            assertEquals(ChiefTeamStatus.RESPONSES_RECORDED, duplicate.status)
            assertEquals(2, model.calls.get())
            assertThrows(IllegalStateException::class.java) { next.submit(requestId, "Anderer Text", 1, true) }
        } finally { next.close() }
    }

    @Test fun missingModelIsPersistedBlockedNotSuccess() {
        val s = store(); val model = Model(null); val team = configured(s, model)
        try {
            val task = team.submit(id(), "Text prüfen", 1, true)
            assertEquals(ChiefTeamStatus.BLOCKED, task.status)
            assertEquals("MODEL_UNAVAILABLE", task.diagnostic)
            assertTrue(task.responses.isEmpty()); assertEquals(0, model.calls.get())
        } finally { team.close() }
    }

    @Test fun missingApprovalDoesNotCreateOrExecuteTask() {
        val s = store(); val model = Model(sha); val team = configured(s, model)
        try {
            assertThrows(IllegalStateException::class.java) { team.submit(id(), "Text", 1, false) }
            assertTrue(s.teamState().tasks.isEmpty()); assertEquals(0, model.calls.get())
        } finally { team.close() }
    }

    @Test fun chiefCanBeChangedWithoutGrantingToolsOrRewritingProfiles() {
        val s = store(); val team = configured(s, Model(sha))
        try {
            val profiles = s.page(limit = 100).items
            team.configure("bot-mira", listOf("bot-kora", "bot-lint"), 1)
            assertEquals("bot-mira", s.teamState().chiefId)
            assertEquals(profiles, s.page(limit = 100).items)
            assertTrue(s.get("bot-mira")!!.permissionScope.isEmpty())
            assertThrows(IllegalStateException::class.java) { team.submit(id(), "stale", 1, true) }
        } finally { team.close() }
    }

    @Test fun sameBotCannotAnalyzeAndVerifyOrLeadItsOwnReview() {
        val s = store(); val team = configured(s, Model(sha))
        try {
            assertThrows(IllegalArgumentException::class.java) {
                team.configure("bot-atlas", listOf("bot-kora", "bot-kora"), 1)
            }
            assertThrows(IllegalArgumentException::class.java) {
                team.configure("bot-kora", listOf("bot-kora", "bot-lint"), 1)
            }
            assertThrows(IllegalArgumentException::class.java) {
                team.configure("bot-unknown", listOf("bot-kora", "bot-lint"), 1)
            }
            assertEquals(1L, s.teamState().revision)
        } finally { team.close() }
    }

    @Test fun changedChiefRejectsLateResponseAndNeverDispatchesReviewer() {
        lateResult { team, _ -> team.configure("bot-mira", listOf("bot-kora", "bot-lint"), 1) }
    }

    @Test fun stopRejectsLateResponseAndNeverDispatchesReviewer() {
        lateResult { team, _ -> team.stop(team.snapshot().tasks.single().id) }
    }

    private fun lateResult(mutate: (ChiefBotTeam, ChiefBotProfileStore) -> Unit) {
        val entered = CountDownLatch(1); val release = CountDownLatch(1)
        val model = Model(sha) { _, _ ->
            entered.countDown()
            try { release.await(5, TimeUnit.SECONDS) } catch (_: InterruptedException) { }
            "Späte Antwort"
        }
        val s = store(); val team = configured(s, model)
        try {
            team.submit(id(), "Text prüfen", 1, true)
            assertTrue(entered.await(5, TimeUnit.SECONDS))
            mutate(team, s); release.countDown()
            assertTrue(team.awaitIdle(10_000))
            assertEquals(ChiefTeamStatus.CANCELLED, s.teamState().tasks.single().status)
            assertTrue(s.teamState().tasks.single().responses.isEmpty())
            assertEquals(1, model.calls.get())
        } finally { release.countDown(); team.close() }
    }

    @Test fun changedModelCannotCommitResponse() {
        val s = store(); lateinit var model: Model
        model = Model(sha) { _, _ -> model.sha = "b".repeat(64); "Antwort" }
        val team = configured(s, model)
        try {
            team.submit(id(), "Text prüfen", 1, true); assertTrue(team.awaitIdle(10_000))
            assertEquals(ChiefTeamStatus.FAILED, s.teamState().tasks.single().status)
            assertTrue(s.teamState().tasks.single().responses.isEmpty())
        } finally { team.close() }
    }

    @Test fun editedProfileCannotCommitResponse() {
        val s = store()
        val model = Model(sha) { _, _ ->
            val p = s.get("bot-kora")!!
            s.rename(p.agentId, "Neuer Name", p.profileVersion)
            "Antwort"
        }
        val team = configured(s, model)
        try {
            team.submit(id(), "Text prüfen", 1, true); assertTrue(team.awaitIdle(10_000))
            assertEquals(ChiefTeamStatus.FAILED, s.teamState().tasks.single().status)
            assertTrue(s.teamState().tasks.single().responses.isEmpty())
        } finally { team.close() }
    }

    @Test fun persistedInterruptedTaskRequiresFreshOwnerRequestNotReplay() {
        val s = store(); val model = Model(null); val team = configured(s, model)
        val task = team.submit(id(), "Text prüfen", 1, true)
        team.close()
        s.updateTeam { it.copy(tasks = listOf(task.copy(status = ChiefTeamStatus.ANALYZING, diagnostic = ""))) }
        val restarted = ChiefBotTeam(s, Model(sha))
        try {
            restarted.initialize()
            assertEquals(ChiefTeamStatus.INTERRUPTED, restarted.snapshot().tasks.single().status)
            assertEquals(ChiefTeamStatus.INTERRUPTED, restarted.submit(task.id, task.request, 1, true).status)
            assertFalse(restarted.active())
        } finally { restarted.close() }
    }

    @Test fun invalidHandoffsAndSelfReportedHashesAreRejected() {
        val s = store(); val team = configured(s, Model(null))
        try {
            val task = team.submit(id(), "Text prüfen", 1, true)
            assertThrows(IllegalArgumentException::class.java) {
                ChiefBotTextHandoff.prompt(task, s.get("bot-lint")!!, 0)
            }
            assertThrows(IllegalArgumentException::class.java) {
                ChiefBotTextHandoff.prompt(task, s.get("bot-lint")!!, 1)
            }
            assertThrows(IllegalArgumentException::class.java) {
                ChiefTeamResponse("bot-kora", "ANALYZE", sha, "Tests bestanden", sha, 10)
            }
        } finally { team.close() }
    }

    @Test fun corruptedTeamFileFailsClosedAndDoesNotReplaceProfiles() {
        val root = folder.newFolder("corrupt"); val s = ChiefBotProfileStore(root)
        val team = configured(s, Model(null)); team.close()
        val profiles = s.page(limit = 100).items
        val file = java.io.File(root, "bot-team-v1.bin")
        val bytes = file.readBytes(); bytes[bytes.lastIndex] = (bytes.last() + 1).toByte(); file.writeBytes(bytes)
        assertThrows(IllegalArgumentException::class.java) { s.teamState() }
        assertArrayEquals(bytes, file.readBytes())
        assertEquals(profiles, s.page(limit = 100).items)
    }

    @Test fun teamFileSymlinkCannotReadOrOverwriteForeignData() {
        val root = folder.newFolder("linked"); val s = ChiefBotProfileStore(root); s.seedDefaults(20)
        val target = folder.newFile("foreign"); target.writeText("unchanged")
        Files.createSymbolicLink(java.io.File(root, "bot-team-v1.bin").toPath(), target.toPath())
        assertThrows(IllegalArgumentException::class.java) { s.teamState() }
        assertThrows(IllegalArgumentException::class.java) { s.updateTeam { it } }
        assertEquals("unchanged", target.readText())
    }

    @Test fun codecRoundTripsAndRejectsTrailingBytes() {
        val state = ChiefBotTeamState(1, "bot-atlas", listOf("bot-kora", "bot-lint"))
        val encoded = ChiefBotTeamCodec.encode(state)
        assertEquals(state, ChiefBotTeamCodec.decode(encoded))
        assertThrows(IllegalArgumentException::class.java) { ChiefBotTeamCodec.decode(encoded + byteArrayOf(1)) }
    }
}
