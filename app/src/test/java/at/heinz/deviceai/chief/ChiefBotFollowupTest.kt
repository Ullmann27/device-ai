package at.heinz.deviceai.chief

import at.heinz.deviceai.cognitive.AgentFollowupEnvelope
import at.heinz.deviceai.cognitive.DeliberationCancellation
import at.heinz.deviceai.learning.ContinuumLearningCore
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.util.UUID
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/** Real workers, files, controlled model. */
class ChiefBotFollowupTest {
    @get:Rule val folder = TemporaryFolder()
    private val nextGoal = "Prüfe den zweiten Satz: Ein Quadrat hat vier Seiten."
    private val sourceGoal = "Prüfe den ersten Satz: Ein Dreieck hat drei Seiten."

    private fun envelope(goal: String = nextGoal, capability: String = "text.analyze", tools: List<String> = emptyList()) =
        JSONObject().put("schema", AgentFollowupEnvelope.SCHEMA).put("answer", "Erster Satz ungeprüft bewertet.")
            .put("next", JSONObject().put("goal", goal).put("capability", capability)
                .put("tools", JSONArray(tools)).put("reason", "Zweiter Satz ist noch offen.")).toString()

    private class Model(val sourceReply: String) : ChiefTeamModel {
        @Volatile var sha: String? = "a".repeat(64)
        val prompts = CopyOnWriteArrayList<String>()
        val deadlines = CopyOnWriteArrayList<Long>()
        var heldCall = 0
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val returned = CountDownLatch(1)
        val token = AtomicReference<DeliberationCancellation>()
        override fun readySha256() = sha
        override fun generate(prompt: String, cancellation: DeliberationCancellation, deadlineMillis: Long): String {
            prompts += prompt; deadlines += deadlineMillis
            val call = prompts.size
            if (call == heldCall) {
                token.set(cancellation); entered.countDown()
                val end = System.nanoTime() + TimeUnit.SECONDS.toNanos(10)
                while (release.count > 0 && System.nanoTime() < end) {
                    try { release.await(25, TimeUnit.MILLISECONDS) } catch (_: InterruptedException) { }
                }
                check(release.count == 0L)
                returned.countDown()
            }
            return if (call == 2) sourceReply else "Kontrollierte, ungeprüfte Textantwort $call."
        }
    }

    private data class Fixture(val store: ChiefBotProfileStore, val core: ContinuumLearningCore,
                               val team: ChiefBotTeam, val model: Model, val session: AtomicReference<String>)
    private fun fixture(reply: String = envelope(), block: (Fixture) -> Unit) {
        val root = folder.newFolder()
        val store = ChiefBotProfileStore(File(root, "bots"))
        val core = ContinuumLearningCore(File(root, "owner"))
        core.confirmOwnerFact("Ich wohne in Steyr.")
        val model = Model(reply)
        val session = AtomicReference("owner-session-a")
        val team = ChiefBotTeam(store, model, sharedKnowledge = core, ownerSession = session::get)
        team.initialize(); team.configure("bot-atlas", listOf("bot-kora", "bot-lint"), 0)
        try { block(Fixture(store, core, team, model, session)) }
        finally { model.release.countDown(); team.close() }
    }

    private fun source(f: Fixture): String {
        val id = "team-" + UUID.randomUUID()
        f.team.submit(id, sourceGoal, 1, true)
        assertTrue(f.team.awaitIdle(10_000))
        assertEquals(ChiefTeamStatus.RESPONSES_RECORDED, f.store.teamState().tasks.single().status)
        return id
    }

    private fun review(f: Fixture, sourceId: String): ChiefFollowupReview {
        val decision = f.team.prepareFollowup(sourceId, "REVIEW")
        assertEquals(ChiefFollowupDecisionCode.OWNER_APPROVAL_REQUIRED, decision.code)
        return requireNotNull(decision.review)
    }

    private fun submit(f: Fixture, review: ChiefFollowupReview, approved: Boolean = true) =
        f.team.submit(review.requestId, review.proposal.goal, review.teamRevision, approved, followup = review)

    @Test fun completedProposalNeedsFreshApprovalAndRunsExactlyTwoNewWorkers() = fixture { f ->
        val sourceId = source(f)
        assertEquals(2, f.model.prompts.size)
        val review = review(f, sourceId)
        assertEquals(nextGoal, review.proposal.goal)
        assertThrows(IllegalStateException::class.java) { submit(f, review, approved = false) }
        assertEquals(1, f.store.teamState().tasks.size)
        assertEquals(2, f.model.prompts.size)
        submit(f, review)
        assertTrue(f.team.awaitIdle(10_000))
        assertEquals(4, f.model.prompts.size)
        val tasks = f.store.teamState().tasks
        assertEquals(listOf(sourceId, review.requestId), tasks.map { it.id })
        assertTrue(tasks.all { it.status == ChiefTeamStatus.RESPONSES_RECORDED && it.responses.size == 2 })
        assertEquals(nextGoal, tasks.last().request)
        assertTrue(f.model.deadlines.all { it in 1..120_000 })
        assertTrue(f.model.prompts[2].contains("Keine Werkzeuge"))
        assertFalse(f.model.prompts[2].contains(sourceGoal))
        assertFalse(f.team.active())
    }

    @Test fun followupDoesNotInheritThePreviousSharedFactsOrGrant() = fixture { f ->
        val id = "team-" + UUID.randomUUID()
        val request = "Prüfe meine bestätigte Wohnangabe in Steyr."
        val selected = f.team.reviewSharedKnowledge(id, request, 1)
        val grant = selected.approve(setOf(selected.facts.single().id), ownerApproved = true)
        f.team.submit(id, request, 1, true, shared = grant)
        assertTrue(f.team.awaitIdle(10_000))
        assertTrue(f.model.prompts.take(2).all { it.contains("Ich wohne in Steyr.") })
        val review = review(f, id)
        assertThrows(IllegalStateException::class.java) {
            f.team.submit(review.requestId, nextGoal, 1, true, shared = grant, followup = review)
        }
        submit(f, review); assertTrue(f.team.awaitIdle(10_000))
        assertTrue(f.model.prompts.drop(2).all {
            !it.contains("ownerApprovedSharedFacts") && !it.contains("Ich wohne in Steyr.")
        })
    }

    @Test fun chiefRejectsUnknownCapabilityRiskyToolsAndDuplicateGoal() {
        listOf(envelope(capability = "device.delete") to ChiefFollowupDecisionCode.UNSUPPORTED_CAPABILITY,
            envelope(tools = listOf("delete_file")) to ChiefFollowupDecisionCode.TOOLS_NOT_AUTHORIZED,
            envelope(goal = "  " + sourceGoal.uppercase() + "  ") to ChiefFollowupDecisionCode.DUPLICATE_GOAL).forEach { (reply, code) ->
            fixture(reply) { f ->
                val id = source(f)
                val decision = f.team.prepareFollowup(id, "REVIEW")
                assertEquals(code, decision.code); assertNull(decision.review)
                assertEquals(2, f.model.prompts.size); assertEquals(1, f.store.teamState().tasks.size)
            }
        }
    }

    @Test fun currentModelAndPersistedBudgetAreCheckedBeforeNewAdmission() = fixture { f ->
        val id = source(f)
        val review = review(f, id)
        f.model.sha = null
        assertEquals(ChiefFollowupDecisionCode.MODEL_UNAVAILABLE, f.team.prepareFollowup(id, "REVIEW").code)
        assertThrows(IllegalStateException::class.java) { submit(f, review) }
        f.model.sha = "a".repeat(64)
        val profiles = f.store.page(limit = 100).items.associateBy { it.agentId }.toMutableMap()
        val analyst = profiles.getValue("bot-kora")
        profiles[analyst.agentId] = analyst.copy(resourceBudget = analyst.resourceBudget.copy(maxModelCalls = 0))
        ChiefBotProfileStore::class.java.getDeclaredMethod("write", Map::class.java)
            .apply { isAccessible = true }.invoke(f.store, profiles)
        assertEquals(ChiefFollowupDecisionCode.MODEL_BUDGET_UNAVAILABLE, f.team.prepareFollowup(id, "REVIEW").code)
        assertThrows(IllegalStateException::class.java) { submit(f, review) }
        assertEquals(2, f.model.prompts.size); assertEquals(1, f.store.teamState().tasks.size)
    }

    @Test fun queuedDraftRejectsStopSessionProfileAndEditedGoal() {
        listOf<(Fixture, ChiefFollowupReview) -> Unit>(
            { f, _ -> f.team.invalidate() },
            { f, _ -> f.session.set("owner-session-b") },
            { f, _ -> f.store.rename("bot-kora", "Kora neu", f.store.get("bot-kora")!!.profileVersion) },
            { _, r -> r.revoke() }
        ).forEach { invalidate -> fixture { f ->
            val review = review(f, source(f))
            invalidate(f, review)
            assertThrows(IllegalStateException::class.java) { submit(f, review) }
            assertEquals(2, f.model.prompts.size); assertEquals(1, f.store.teamState().tasks.size)
        } }
        fixture { f ->
            val review = review(f, source(f))
            assertThrows(IllegalStateException::class.java) {
                f.team.submit(review.requestId, "Anderes Ziel", 1, true, followup = review)
            }
            assertEquals(2, f.model.prompts.size)
        }
    }

    @Test fun deterministicFollowupIdentityPreventsReplayAfterReopen() = fixture { f ->
        val sourceId = source(f)
        val review = review(f, sourceId)
        submit(f, review); assertTrue(f.team.awaitIdle(10_000))
        assertEquals(ChiefFollowupDecisionCode.ALREADY_SUBMITTED, f.team.prepareFollowup(sourceId, "REVIEW").code)
        f.team.close()
        val next = ChiefBotTeam(f.store, f.model, ownerSession = f.session::get)
        try {
            next.initialize()
            assertEquals(ChiefFollowupDecisionCode.ALREADY_SUBMITTED, next.prepareFollowup(sourceId, "REVIEW").code)
            assertEquals(ChiefTeamStatus.RESPONSES_RECORDED,
                next.submit(review.requestId, nextGoal, 1, true).status)
            assertEquals(4, f.model.prompts.size)
            assertEquals(2, f.store.teamState().tasks.size)
        } finally { next.close() }
    }

    @Test fun deletedSourceAndChangedTeamCannotAuthorizeOldDraft() = fixture { f ->
        val id = source(f)
        val review = review(f, id)
        f.team.clearHistory(1)
        assertEquals(ChiefFollowupDecisionCode.SOURCE_CHANGED, f.team.prepareFollowup(id, "REVIEW").code)
        assertThrows(IllegalStateException::class.java) { submit(f, review) }
        assertEquals(2, f.model.prompts.size); assertTrue(f.store.teamState().tasks.isEmpty())
    }

    @Test fun stopDuringFollowupRejectsLateModelResultAndFurtherReview() = fixture { f ->
        val review = review(f, source(f))
        f.model.heldCall = 3
        submit(f, review)
        assertTrue(f.model.entered.await(5, TimeUnit.SECONDS))
        f.team.stop(review.requestId)
        assertThrows(java.util.concurrent.CancellationException::class.java) { f.model.token.get().throwIfCancelled() }
        f.model.release.countDown()
        assertTrue(f.model.returned.await(5, TimeUnit.SECONDS))
        assertTrue(f.team.awaitIdle(10_000))
        val result = f.store.teamState().tasks.last()
        assertEquals(ChiefTeamStatus.CANCELLED, result.status)
        assertTrue(result.responses.isEmpty())
        assertEquals(3, f.model.prompts.size)
    }

    @Test fun ownerSessionChangeRejectsAlreadyRunningLateResult() = fixture { f ->
        val review = review(f, source(f))
        f.model.heldCall = 3
        submit(f, review)
        assertTrue(f.model.entered.await(5, TimeUnit.SECONDS))
        f.session.set("owner-session-b")
        f.model.release.countDown()
        assertTrue(f.model.returned.await(5, TimeUnit.SECONDS))
        assertTrue(f.team.awaitIdle(10_000))
        val result = f.store.teamState().tasks.last()
        assertEquals(ChiefTeamStatus.FAILED, result.status)
        assertTrue(result.responses.isEmpty())
        assertEquals(3, f.model.prompts.size)
    }
}
