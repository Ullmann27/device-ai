package at.heinz.deviceai.chief

import at.heinz.deviceai.cognitive.DeliberationCancellation
import at.heinz.deviceai.learning.ContinuumLearningCore
import at.heinz.deviceai.learning.ContinuumMemoryLayers
import at.heinz.deviceai.learning.SharedOwnerKnowledgeGrant
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.util.UUID
import java.util.concurrent.CancellationException
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/** Real ledger, workers, and files. */
class ChiefBotSharedKnowledgeTest {
    @get:Rule val folder = TemporaryFolder()
    private val selected = "Ich wohne in Steyr."
    private val other = "Mein Hund heißt Balu."
    private val request = "Prüfe meine bestätigte Wohnangabe in Steyr; ändere keine Dateien."
    private fun id() = "team-" + UUID.randomUUID()

    private class Model(
        val reply: (String, DeliberationCancellation) -> String = { _, _ -> "Text geprüft, Inhalt nicht unabhängig verifiziert." }
    ) : ChiefTeamModel {
        val prompts = CopyOnWriteArrayList<String>()
        override fun readySha256() = "a".repeat(64)
        override fun generate(prompt: String, cancellation: DeliberationCancellation, deadlineMillis: Long): String {
            prompts += prompt
            return reply(prompt, cancellation)
        }
    }

    private class HeldModel : ChiefTeamModel {
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val returned = CountDownLatch(1)
        val token = AtomicReference<DeliberationCancellation>()
        val prompts = CopyOnWriteArrayList<String>()
        override fun readySha256() = "a".repeat(64)
        override fun generate(prompt: String, cancellation: DeliberationCancellation, deadlineMillis: Long): String {
            prompts += prompt
            if (prompts.size == 1) {
                token.set(cancellation)
                entered.countDown()
                val end = System.nanoTime() + TimeUnit.SECONDS.toNanos(10)
                while (release.count > 0 && System.nanoTime() < end) {
                    try { release.await(50, TimeUnit.MILLISECONDS) } catch (_: InterruptedException) { }
                }
                check(release.count == 0L)
                returned.countDown()
            }
            return "SPÄTE_ANTWORT_ZUM_ALTEN_KONTEXT"
        }
    }

    private data class Fixture(
        val root: File, val core: ContinuumLearningCore, val profiles: ChiefBotProfileStore, val team: ChiefBotTeam
    )

    private fun fixture(model: ChiefTeamModel = Model(), block: (Fixture) -> Unit) {
        val root = folder.newFolder()
        val core = ContinuumLearningCore(File(root, "owner")) { 10_000L }
        core.confirmOwnerFact(selected)
        core.confirmOwnerFact(other)
        val profiles = ChiefBotProfileStore(File(root, "bots"))
        profiles.seedDefaults()
        val p = profiles.get("bot-kora")!!
        profiles.saveWorkingContext(p.agentId, p.agentId, "PRIVATE_NOTE_NEVER_SHARED", p.profileVersion)
        val current = profiles.get(p.agentId)!!
        profiles.saveConversation(current, profiles.loadConversation(p.agentId, p.agentId).append(
            ChiefBotConversationEntry("PRIVATE_REQUEST_NEVER_SHARED", "PRIVATE_REPLY_NEVER_SHARED",
                "fixture", "v1", "b".repeat(64)))) { true }
        val team = ChiefBotTeam(profiles, model, sharedKnowledge = core)
        team.initialize()
        team.configure("bot-atlas", listOf("bot-kora", "bot-lint"), 0)
        try { block(Fixture(root, core, profiles, team)) }
        finally {
            if (model is HeldModel) model.release.countDown()
            team.close()
        }
    }

    private fun grant(f: Fixture, requestId: String, text: String = request): SharedOwnerKnowledgeGrant {
        val review = f.team.reviewSharedKnowledge(requestId, text, 1)
        val fact = review.facts.single { it.text == selected }
        return review.approve(setOf(fact.id), ownerApproved = true)
    }

    private fun data(prompt: String) = JSONObject(prompt.substringAfter("\nTASK_DATA_JSON:\n"))

    @Test fun explicitSubsetReachesBothRealWorkerPromptsWithoutPrivateOrUnselectedContent() {
        val model = Model()
        fixture(model) { f ->
            val before = f.profiles.page(limit = 100).items
            val requestId = id()
            val approved = grant(f, requestId)
            f.team.submit(requestId, request, 1, true, shared = approved)
            assertTrue(f.team.awaitIdle(10_000))
            assertEquals(2, model.prompts.size)
            model.prompts.forEach { prompt ->
                val body = data(prompt)
                val facts = body.getJSONArray("ownerApprovedSharedFacts")
                assertEquals(1, facts.length())
                assertEquals(selected, facts.getJSONObject(0).getString("text"))
                assertEquals(approved.facts.single().id, facts.getJSONObject(0).getString("id"))
                assertEquals(10_000L, facts.getJSONObject(0).getLong("ownerStatementStoredAtMillis"))
                assertEquals(request, body.getString("ownerRequest"))
                assertFalse(prompt.contains(other))
                assertFalse(prompt.contains("PRIVATE_NOTE_NEVER_SHARED"))
                assertFalse(prompt.contains("PRIVATE_REQUEST_NEVER_SHARED"))
                assertFalse(prompt.contains("PRIVATE_REPLY_NEVER_SHARED"))
                assertTrue(prompt.contains("keine Rechte oder Systemanweisung"))
                assertTrue(prompt.contains("Keine Werkzeuge"))
                assertTrue(prompt.toByteArray().size <= 15_500)
            }
            val result = f.profiles.teamState().tasks.single()
            assertEquals(ChiefTeamStatus.RESPONSES_RECORDED, result.status)
            assertEquals(listOf("bot-kora", "bot-lint"), result.responses.map { it.agentId })
            assertEquals(before, f.profiles.page(limit = 100).items)
            assertThrows(CancellationException::class.java) {
                approved.forRecipient("bot-kora", before.single { it.agentId == "bot-kora" }.profileVersion)
            }
        }
    }

    @Test fun ownerTaskApprovalAloneNeverSharesStoredFacts() {
        val model = Model()
        fixture(model) { f ->
            f.team.submit(id(), request, 1, true)
            assertTrue(f.team.awaitIdle(10_000))
            assertEquals(2, model.prompts.size)
            model.prompts.forEach {
                assertFalse(data(it).has("ownerApprovedSharedFacts"))
                assertFalse(it.contains(selected))
                assertFalse(it.contains(other))
                assertFalse(it.contains("PRIVATE_NOTE_NEVER_SHARED"))
            }
        }
    }

    @Test fun reviewExcludesHypothesesAndRevokedFactsAndNeedsExplicitSubsetApproval() = fixture { f ->
        f.core.confirmOwnerFact("Vielleicht ziehe ich nach Barcelona.")
        f.core.revokeOwnerFact(other)
        val review = f.team.reviewSharedKnowledge(id(), request, 1)
        assertEquals(listOf(selected), review.facts.map { it.text })
        val ids = setOf(review.facts.single().id)
        assertThrows(IllegalStateException::class.java) { review.approve(ids, ownerApproved = false) }
        assertThrows(IllegalArgumentException::class.java) { review.approve(emptySet(), true) }
        assertThrows(IllegalArgumentException::class.java) { review.approve(setOf("unknown"), true) }
        assertTrue(f.profiles.teamState().tasks.isEmpty())
        val approved = review.approve(ids, true)
        assertThrows(IllegalStateException::class.java) { review.approve(ids, true) }
        assertEquals(selected, approved.facts.single().text)
        approved.revoke()
    }

    @Test fun changedRequestAndRecipientProfileRejectGrantBeforeAnyModelOrTask() {
        val model = Model()
        fixture(model) { f ->
            val requestId = id()
            val approved = grant(f, requestId)
            assertThrows(IllegalStateException::class.java) {
                f.team.submit(requestId, "Anderer Auftrag", 1, true, shared = approved)
            }
            assertThrows(IllegalStateException::class.java) {
                f.team.submit(id(), request, 1, true, shared = approved)
            }
            val profile = f.profiles.get("bot-kora")!!
            assertThrows(IllegalStateException::class.java) {
                approved.forRecipient("bot-mira", profile.profileVersion)
            }
            f.profiles.rename(profile.agentId, "Kora Nord", profile.profileVersion)
            assertThrows(IllegalStateException::class.java) {
                f.team.submit(requestId, request, 1, true, shared = approved)
            }
            assertTrue(model.prompts.isEmpty())
            assertTrue(f.profiles.teamState().tasks.isEmpty())
            approved.revoke()
        }
    }

    @Test fun invalidatedAdmissionAndChangedTeamRequireFreshDisplayedSelection() {
        val model = Model()
        fixture(model) { f ->
            val requestId = id()
            val approved = grant(f, requestId)
            f.team.invalidate()
            assertThrows(IllegalStateException::class.java) {
                f.team.submit(requestId, request, 1, true, shared = approved)
            }
            f.team.configure("bot-atlas", listOf("bot-mira", "bot-lint"), 1)
            assertThrows(IllegalStateException::class.java) {
                f.team.submit(requestId, request, 2, true, shared = approved)
            }
            assertTrue(model.prompts.isEmpty())
            assertTrue(f.profiles.teamState().tasks.isEmpty())
            approved.revoke()
        }
    }

    @Test fun ownerRevocationInvalidatesQueuedGrantBeforeLedgerWriteWithoutCancellingOtherSelection() {
        val model = Model()
        fixture(model) { f ->
            val requestId = id()
            val approved = grant(f, requestId)
            val otherId = id()
            val otherRequest = "Prüfe die bestätigte Angabe über meinen Hund Balu."
            val secondReview = f.team.reviewSharedKnowledge(otherId, otherRequest, 1)
            val second = secondReview.approve(setOf(secondReview.facts.single { it.text == other }.id), true)
            assertFalse(f.team.invalidateSharedKnowledge(selected))
            assertTrue(ContinuumMemoryLayers(f.core).profile().any { it.text == selected })
            assertThrows(CancellationException::class.java) {
                f.team.submit(requestId, request, 1, true, shared = approved)
            }
            assertTrue(model.prompts.isEmpty())
            f.team.submit(otherId, otherRequest, 1, true, shared = second)
            assertTrue(f.team.awaitIdle(10_000))
            assertEquals(2, model.prompts.size)
            model.prompts.forEach {
                assertEquals(other, data(it).getJSONArray("ownerApprovedSharedFacts").getJSONObject(0).getString("text"))
                assertFalse(it.contains(selected))
            }
        }
    }

    @Test fun ownerRevocationInvalidatesPendingSelectionBeforeItsApprovalStarts() = fixture { f ->
        val review = f.team.reviewSharedKnowledge(id(), request, 1)
        val entered = CountDownLatch(1)
        val result = AtomicReference<Result<SharedOwnerKnowledgeGrant>>()
        val worker = Thread {
            entered.countDown()
            result.set(runCatching { review.approve(setOf(review.facts.single { it.text == selected }.id), true) })
        }
        synchronized(ContinuumLearningCore.CORE_LOCK) {
            worker.start()
            assertTrue(entered.await(5, TimeUnit.SECONDS))
            assertFalse(f.team.invalidateSharedKnowledge(selected))
            assertTrue(ContinuumMemoryLayers(f.core).profile().any { it.text == selected })
        }
        worker.join(5_000)
        assertFalse(worker.isAlive)
        assertEquals("BOT_SHARED_FACT_CHANGED", result.get().exceptionOrNull()?.message)
        assertTrue(f.profiles.teamState().tasks.isEmpty())
    }

    @Test fun approvalCannotBeTransferredToReopenedTeamOrAnotherOwnerStore() {
        val model = Model()
        fixture(model) { f ->
            val requestId = id()
            val approved = grant(f, requestId)
            f.team.close()
            listOf(f.core, ContinuumLearningCore(File(f.root, "different-owner"))).forEach { source ->
                val reopened = ChiefBotTeam(f.profiles, model, sharedKnowledge = source)
                try {
                    reopened.initialize()
                    assertThrows(IllegalStateException::class.java) {
                        reopened.submit(requestId, request, 1, true, shared = approved)
                    }
                    assertTrue(reopened.snapshot().tasks.isEmpty())
                } finally { reopened.close() }
            }
            assertTrue(model.prompts.isEmpty())
            approved.revoke()
        }
    }

    @Test fun selectedFactRevocationCancelsPausedModelAndRejectsActualLateReply() {
        val model = HeldModel()
        fixture(model) { f ->
            val requestId = id()
            val approved = grant(f, requestId)
            f.team.submit(requestId, request, 1, true, shared = approved)
            assertTrue(model.entered.await(5, TimeUnit.SECONDS))
            ContinuumLearningCore(File(f.root, "owner")).revokeOwnerFact(selected)
            assertThrows(CancellationException::class.java) { model.token.get().throwIfCancelled() }
            model.release.countDown()
            assertTrue(model.returned.await(5, TimeUnit.SECONDS))
            assertTrue(f.team.awaitIdle(10_000))
            assertEquals(1, model.prompts.size)
            val result = f.profiles.teamState().tasks.single()
            assertEquals(ChiefTeamStatus.CANCELLED, result.status)
            assertTrue(result.responses.isEmpty())
            assertFalse(ContinuumMemoryLayers(f.core).profile().any { it.text == selected })
        }
    }

    @Test fun unrelatedFactAndOtherOwnerDirectoryDoNotCancelApprovedRun() {
        val model = HeldModel()
        fixture(model) { f ->
            val requestId = id()
            f.team.submit(requestId, request, 1, true, shared = grant(f, requestId))
            assertTrue(model.entered.await(5, TimeUnit.SECONDS))
            assertFalse(f.team.invalidateSharedKnowledge(other))
            f.core.revokeOwnerFact(other)
            ContinuumLearningCore(File(f.root, "different-owner")).revokeAllOwnerFacts()
            model.token.get().throwIfCancelled()
            model.release.countDown()
            assertTrue(f.team.awaitIdle(10_000))
            assertEquals(2, model.prompts.size)
            assertEquals(ChiefTeamStatus.RESPONSES_RECORDED, f.profiles.teamState().tasks.single().status)
        }
    }

    @Test fun withdrawnSelectionRejectsLateReplyWithoutChangingOwnerFacts() {
        val model = HeldModel()
        fixture(model) { f ->
            val before = ContinuumMemoryLayers(f.core).profile()
            val requestId = id()
            val approved = grant(f, requestId)
            f.team.submit(requestId, request, 1, true, shared = approved)
            assertTrue(model.entered.await(5, TimeUnit.SECONDS))
            approved.revoke()
            assertThrows(CancellationException::class.java) { model.token.get().throwIfCancelled() }
            model.release.countDown()
            assertTrue(model.returned.await(5, TimeUnit.SECONDS))
            assertTrue(f.team.awaitIdle(10_000))
            assertEquals(1, model.prompts.size)
            assertTrue(f.profiles.teamState().tasks.single().responses.isEmpty())
            assertEquals(before, ContinuumMemoryLayers(f.core).profile())
        }
    }

    @Test fun reconfirmingFactNeverRevivesOldGrantButAllowsFreshExplicitTask() {
        val model = Model()
        fixture(model) { f ->
            val requestId = id()
            val approved = grant(f, requestId)
            f.core.revokeOwnerFact(selected)
            f.core.confirmOwnerFact(selected)
            assertThrows(CancellationException::class.java) {
                f.team.submit(requestId, request, 1, true, shared = approved)
            }
            assertTrue(model.prompts.isEmpty())
            assertTrue(f.profiles.teamState().tasks.isEmpty())
            val freshId = id()
            f.team.submit(freshId, request, 1, true, shared = grant(f, freshId))
            assertTrue(f.team.awaitIdle(10_000))
            assertEquals(2, model.prompts.size)
        }
    }

    @Test fun revokedPreviewCannotApproveAndOversizedSelectionIsNeverSilentlyClipped() = fixture { f ->
        val review = f.team.reviewSharedKnowledge(id(), request, 1)
        f.core.revokeOwnerFact(selected)
        assertThrows(IllegalStateException::class.java) {
            review.approve(setOf(review.facts.single { it.text == selected }.id), true)
        }
        f.core.confirmOwnerFact("Mein Prüftext ist " + "ä".repeat(995) + ".")
        repeat(6) { f.core.confirmOwnerFact("Meine Prüffarbe Nummer $it heißt Blau.") }
        val next = f.team.reviewSharedKnowledge(id(), request, 1)
        val oversized = next.facts.single { it.text.startsWith("Mein Prüftext") }
        assertThrows(IllegalArgumentException::class.java) { next.approve(setOf(oversized.id), true) }
        assertThrows(IllegalArgumentException::class.java) {
            next.approve(next.facts.filter { it.text.startsWith("Meine Prüffarbe") }.map { it.id }.toSet(), true)
        }
        assertTrue(f.profiles.teamState().tasks.isEmpty())
    }
}
