package at.heinz.deviceai.codingagents

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * The owner flow of the cloud coding agents against a scripted Managed Agents account and a scripted GitHub: every
 * guardrail, the one confirmation, the run from the real event shapes, approvals, stop, reconnect, restart, the
 * report and the secret scan. No network, no key. What the real service does is NOT_RUN here.
 */
class CodingAgentControllerTest {
    private val pool = Executors.newCachedThreadPool { task -> Thread(task, "coding-agent-test").apply { isDaemon = true } }
    private val order = "Coding-Agent: behebe in device-ai den Fehler im Parser und mach einen Pull Request"
    private val draftPull = CodingAgentGitHub.PullRequest(912, "https://github.com/Ullmann27/device-ai/pull/912", true, "open", false)

    @After fun tearDown() { pool.shutdownNow() }

    private fun await(what: String, condition: () -> Boolean) {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(15)
        while (!condition()) {
            if (System.nanoTime() > deadline) throw AssertionError("timed out waiting for: $what")
            Thread.sleep(5)
        }
    }

    private fun Rig.withPull(): Rig = apply {
        github.heads["Ullmann27/device-ai@continuum-agent/run0001"] = "f00dfeedf00dfeedf00dfeedf00dfeedf00dfeed"
        github.pull = draftPull
    }

    private fun Rig.orderAndConfirm(sentence: String = order): Rig = apply {
        assertTrue(controller.take(sentence, "Jarvis"))
        // With a real worker thread the question arrives a moment later (GitHub is asked first).
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10)
        while (controller.pendingConfirmation() == null && host.said.none { it.startsWith(CodingAgentTexts.NOT_STARTED) } &&
            System.nanoTime() < deadline) Thread.sleep(2)
        assertTrue("no confirmation was asked: ${host.said}", controller.take("Ja", "Jarvis"))
    }

    /** The run has ended and the owner has been told (the report is the last thing said before the run is ended). */
    private fun awaitReport(rig: Rig, reports: Int = 1) = await("report") { rig.host.ended.size >= reports }

    // ── Guardrails: nothing leaves the device for a run unless every one of them holds ──────────────────────────

    private fun refused(code: String, text: String? = null, arrange: Rig.() -> Unit) {
        val rig = Rig().ready()
        rig.arrange()
        assertTrue(rig.controller.take(order, "Jarvis"))
        assertEquals(text ?: CodingAgentFailure.message(code), rig.host.said.last())
        assertNull(rig.controller.pendingConfirmation())
        assertTrue("sent to Anthropic: ${rig.cloud.calls}", rig.cloud.calls.isEmpty())
        assertTrue("sent to GitHub: ${rig.github.calls}", rig.github.calls.isEmpty())
        assertTrue(rig.store.runs().isEmpty())
    }

    @Test fun noRunWithoutTheAnthropicKey() = refused(CodingAgentFailure.NO_API_KEY) { account.key = null }
    @Test fun noRunWithoutTheClaudeConsent() = refused(CodingAgentFailure.NO_CLAUDE_CONSENT) { account.consent = false }
    @Test fun noRunWithoutTheCodingAgentConsent() = refused(CodingAgentFailure.NO_AGENT_CONSENT) { controller.revokeConsent() }
    @Test fun noRunWithoutAGitHubToken() = refused(CodingAgentFailure.NO_GITHUB_TOKEN) { controller.removeToken { } }
    @Test fun noRunWithoutAnAllowedRepository() = refused(CodingAgentFailure.NO_REPOSITORY) { controller.removeRepository("Ullmann27/device-ai") }

    @Test fun noRunForARepositoryThatIsNotOnTheAllowList() {
        val rig = Rig().ready()
        // By sentence: the named repository is not replaced by the allowed one.
        assertTrue(rig.controller.take("Coding-Agent: behebe im Repo wetter-app den Fehler", "Jarvis"))
        assertEquals(CodingAgentTexts.unknownRepository("wetter-app", listOf("Ullmann27/device-ai")), rig.host.said.last())
        // Through the entry the dialog uses: refused by the controller itself, whatever a caller passes.
        rig.controller.order(CodingAgentTaskKind.CODE, CodingAgentRepoChoice.Named("Fremder/geheim"), "ändere die README")
        assertEquals(CodingAgentFailure.message(CodingAgentFailure.REPOSITORY_NOT_ALLOWED), rig.host.said.last())
        assertNotNull(rig.controller.refusal("Fremder/geheim", 200))
        assertTrue(rig.cloud.calls.isEmpty() && rig.github.calls.isEmpty() && rig.store.runs().isEmpty())
    }

    @Test fun noRunWithoutTheExplicitStartConfirmation() {
        val rig = Rig().ready()
        assertTrue(rig.controller.take(order, "Jarvis"))
        // The question is asked with what GitHub reported; nothing is at Anthropic yet.
        assertEquals(listOf("repository:Ullmann27/device-ai", "head:Ullmann27/device-ai@main"), rig.github.calls)
        assertTrue(rig.host.said.last().startsWith("Coding-Agent – bitte bestätigen"))
        assertTrue(rig.cloud.calls.isEmpty())
        // „ok“ is not a yes: the order is dropped and the word keeps its own route.
        assertFalse(rig.controller.take("ok", "Jarvis"))
        assertNull(rig.controller.pendingConfirmation())
        assertFalse(rig.controller.take("Ja", "Jarvis"))
        // „Nein“ is taken and says that nothing was sent.
        rig.controller.take(order, "Jarvis")
        assertTrue(rig.controller.take("Nein", "Jarvis"))
        assertEquals(CodingAgentTexts.DECLINED, rig.host.said.last())
        // A confirmation that is older than five minutes is no confirmation.
        rig.controller.take(order, "Jarvis")
        rig.now += CodingAgentController.CONFIRMATION_VALID_MILLIS + 1
        assertFalse(rig.controller.take("Ja", "Jarvis"))
        // A tap on „Ja“ without an open question starts nothing either.
        rig.controller.confirm()
        assertEquals(CodingAgentFailure.message(CodingAgentFailure.NOT_CONFIRMED), rig.host.said.last())
        assertTrue(rig.cloud.calls.isEmpty() && rig.store.runs().isEmpty())
    }

    @Test fun noRunWithABudgetOutsideTheOwnersLimits() {
        val rig = Rig().ready()
        assertEquals(CodingAgentTexts.budgetOutOfRange(900, rig.controller.settings), rig.controller.setBudget("9"))
        assertEquals(CodingAgentTexts.AMOUNT_INVALID, rig.controller.setBudget("viel"))
        assertEquals(CodingAgentTexts.MAX_BUDGET_RANGE, rig.controller.setMaxBudget("500"))
        assertEquals(200, rig.controller.settings.budgetCents)
        rig.controller.order(CodingAgentTaskKind.CODE, CodingAgentRepoChoice.Named("Ullmann27/device-ai"), "x y", budgetCents = 501)
        assertEquals(CodingAgentTexts.budgetOutOfRange(501, rig.controller.settings), rig.host.said.last())
        rig.controller.order(CodingAgentTaskKind.CODE, CodingAgentRepoChoice.Named("Ullmann27/device-ai"), "x y", budgetCents = 10)
        assertEquals(CodingAgentTexts.budgetOutOfRange(10, rig.controller.settings), rig.host.said.last())
        assertTrue(rig.cloud.calls.isEmpty() && rig.github.calls.isEmpty())
        // Lowering the owner's maximum pulls the budget of a run down with it.
        assertNull(rig.controller.setMaxBudget("1,50"))
        assertEquals(150, rig.controller.settings.budgetCents)
    }

    @Test fun noRunThatWouldExceedTheMonthlyClaudeLimit() {
        val rig = Rig().ready()
        rig.account.limitCents = 2_500
        rig.account.spentCents = 2_301
        assertTrue(rig.controller.take(order, "Jarvis"))
        assertEquals(CodingAgentTexts.monthlyLimit(200, 2_301, 0, 2_500), rig.host.said.last())
        assertTrue(rig.host.said.last().contains("würde dein Monatslimit für Claude überschreiten: bisher geschätzt 23,01 $"))
        assertTrue(rig.cloud.calls.isEmpty() && rig.github.calls.isEmpty())
        // Exactly up to the limit is allowed.
        rig.account.spentCents = 2_300
        assertTrue(rig.controller.take(order, "Jarvis"))
        assertNotNull(rig.controller.pendingConfirmation())
        // The limit is checked again at the moment of the „Ja“.
        rig.account.spentCents = 2_400
        assertTrue(rig.controller.take("Ja", "Jarvis"))
        assertEquals(CodingAgentTexts.monthlyLimit(200, 2_400, 0, 2_500), rig.host.said.last())
        assertTrue(rig.cloud.calls.isEmpty())
    }

    @Test fun aRejectedTokenOrAReadOnlyRepositoryStopsBeforeAnythingReachesAnthropic() {
        listOf(CodingAgentFailure.GITHUB_TOKEN_REJECTED, CodingAgentFailure.GITHUB_NOT_FOUND, CodingAgentFailure.GITHUB_OFFLINE).forEach { code ->
            val rig = Rig().ready()
            rig.github.failure = code
            assertTrue(rig.controller.take(order, "Jarvis"))
            assertEquals(CodingAgentTexts.NOT_STARTED + " " + CodingAgentFailure.message(code), rig.host.said.last())
            assertNull(rig.controller.pendingConfirmation())
            assertTrue(rig.cloud.calls.isEmpty())
        }
        val archived = Rig().ready()
        archived.github.archived = true
        archived.controller.take(order, "Jarvis")
        assertEquals(CodingAgentTexts.NOT_STARTED + " " + CodingAgentFailure.message(CodingAgentFailure.GITHUB_READ_ONLY), archived.host.said.last())
        assertTrue(archived.cloud.calls.isEmpty())
    }

    @Test fun onlyOneAgentRunsAtATimeAndAQuestionIsAskedWhenTheRepositoryIsUnclear() {
        val rig = Rig(pool).ready("Ullmann27/device-ai", "Ullmann27/continuum-web")
        rig.github.heads["Ullmann27/continuum-web@main"] = "b".repeat(40)
        assertTrue(rig.controller.take("Coding-Agent: behebe den Fehler in der Sprachausgabe", "Jarvis"))
        assertEquals(CodingAgentTexts.askRepository(listOf("Ullmann27/device-ai", "Ullmann27/continuum-web")), rig.host.said.last())
        assertTrue(rig.github.calls.isEmpty())
        assertTrue(rig.controller.take("2", "Jarvis"))
        await("confirmation") { rig.controller.pendingConfirmation() != null }
        assertTrue(rig.controller.pendingConfirmation()!!.contains("Repository: Ullmann27/continuum-web"))
        assertTrue(rig.controller.take("Ja", "Jarvis"))
        await("running") { rig.store.runs().firstOrNull()?.status == CodingAgentRunStatus.RUNNING }
        assertTrue(rig.controller.take(order, "Jarvis"))
        assertEquals(CodingAgentFailure.message(CodingAgentFailure.RUN_ACTIVE), rig.host.said.last())
        assertEquals(1, rig.cloud.sessionRequests.size)
        rig.cloud.emit(Events.running(), Events.idle("end_turn"))
        awaitReport(rig)
    }

    // ── A complete run ──────────────────────────────────────────────────────────────────────────────────────────

    @Test fun aConfirmedOrderRunsAsOneSessionAndEndsInATruthfulReport() {
        val rig = Rig().ready().withPull()
        rig.cloud.onOutcome = Rig.happyScript(rig.cloud)
        rig.orderAndConfirm()

        // Created once, in this order; the stream is open before the outcome is sent (stream-first ordering).
        assertEquals(listOf("saveAgent:create:claude-opus-5-5", "saveEnvironment:create:continuum-coding-agents-run000", "createVault",
            "saveGitHubCredential:create", "createSession", "openStream", "listEvents", "defineOutcome", "session"), rig.cloud.calls)
        val request = rig.cloud.sessionRequests.single()
        assertEquals("Ullmann27/device-ai", request.repository)
        assertEquals("main", request.baseBranch)
        assertEquals(200, request.budgetCents)
        assertEquals("agent_fake_01", request.agent.id)
        assertEquals("env_fake_01", request.environmentId)
        assertEquals("vlt_fake_01", request.vaultId)
        assertEquals(FAKE_GITHUB_TOKEN, request.githubToken.reveal())
        assertEquals("SessionRequest(Ullmann27/device-ai@main, budget=200, token=redacted)", request.toString())
        val (description, rubric, iterations) = rig.cloud.outcomes.single()
        assertTrue(description.contains("„behebe in device-ai den Fehler im Parser und mach einen Pull Request“"))
        assertTrue(description.contains("Arbeitszweig (neu anlegen): continuum-agent/run0001"))
        assertTrue(rubric.contains("ein Entwurf (draft)"))
        assertEquals(CodingAgentPrompt.OUTCOME_ITERATIONS, iterations)

        val run = rig.run
        assertEquals(CodingAgentRunStatus.COMPLETED, run.status)
        assertEquals("sesn_fake_01", run.sessionId)
        assertEquals(137L, run.costCents)
        assertEquals("satisfied", run.outcomeResult)
        assertEquals(listOf("/workspace/device-ai/app/src/main/java/Parser.kt"), run.changedFiles)
        assertTrue(run.settled && run.reported)
        // The session's tokens went into the one monthly counter, once.
        assertEquals(listOf("claude-opus-5-5" to CodingAgentTokens(12_000, 3_400, 90_000, 8_000)), rig.account.recorded.toList())

        val report = rig.host.said.last()
        assertTrue(report, report.startsWith("Coding-Agent fertig: Der Entwurfs-Pull-Request liegt vor."))
        assertTrue(report, report.contains("Pull Request #912: Entwurf, Zustand open · https://github.com/Ullmann27/device-ai/pull/912"))
        assertTrue(report, report.contains("main: unverändert seit dem Start"))
        assertTrue(report, report.contains("Kosten: 1,37 $ laut Anthropic (Listenpreis) von höchstens 2,00 $"))
        assertTrue(report, report.contains("Bericht des Agenten (seine eigenen Angaben, von der App nicht geprüft):"))
        assertTrue(report, report.contains("NICHT GEPRÜFT: Gerätetest"))
        assertEquals(listOf("true|true"), rig.host.ended.toList())
        assertTrue(rig.host.steps.any { it == "agent-setup|Bei Anthropic vorbereitet|DONE" })
        assertTrue(rig.host.steps.any { it == "agent-report|Entwurfs-PR #912 liegt vor|DONE" })
        assertTrue(rig.host.said.any { it.startsWith("Coding-Agent gestartet: Ullmann27/device-ai, neuer Zweig continuum-agent/run0001, Budget höchstens 2,00 $") })
    }

    @Test fun agentEnvironmentAndVaultAreCreatedOnceAndOnlyUpdatedLater() {
        val rig = Rig().ready()
        rig.cloud.onOutcome = { rig.cloud.emit(Events.running(), Events.idle("end_turn")) }
        rig.orderAndConfirm()
        rig.cloud.calls.clear()
        rig.cloud.history.clear()
        // A second run: nothing is created, nothing is updated.
        rig.orderAndConfirm()
        assertEquals(listOf("createSession", "openStream", "listEvents", "defineOutcome", "session"), rig.cloud.calls)
        assertEquals("agent_fake_01", rig.cloud.sessionRequests.last().agent.id)

        // The owner changes the model and the token: the same agent and the same credential are updated in place.
        rig.cloud.calls.clear()
        rig.cloud.history.clear()
        assertNull(rig.controller.setModel("claude-sonnet-5-5"))
        assertEquals(CodingAgentTexts.TOKEN_STORED, rig.controller.saveToken(FAKE_GITHUB_TOKEN + "9"))
        rig.github.expectedToken = FAKE_GITHUB_TOKEN + "9"
        rig.orderAndConfirm()
        assertEquals(listOf("saveAgent:update:claude-sonnet-5-5", "saveGitHubCredential:update", "createSession"), rig.cloud.calls.take(3))
        assertEquals(FAKE_GITHUB_TOKEN + "9", rig.cloud.credentials.last())
        assertEquals("claude-sonnet-5-5", rig.run.model)
    }

    @Test fun theSetupButtonCreatesWhatIsMissingAndForgetsWhatWasRemovedInTheAccount() {
        val rig = Rig().ready()
        var told = ""
        rig.controller.provision { told = it }
        assertEquals(CodingAgentTexts.provisioned(rig.controller.resources), told)
        assertTrue(rig.controller.resources.complete(rig.controller.settings))
        assertEquals(4, rig.cloud.calls.size)
        rig.controller.provision { told = it }
        assertEquals("nothing is created or updated twice", 4, rig.cloud.calls.size)

        // The agent was deleted in the Console: the update says so, and the next setup creates a new one.
        assertNull(rig.controller.setModel("claude-sonnet-5-5"))
        rig.cloud.failNext("saveAgent", CodingAgentFailure.RESOURCE_GONE)
        rig.controller.provision { told = it }
        assertEquals(CodingAgentFailure.message(CodingAgentFailure.RESOURCE_GONE), told)
        assertNull(rig.controller.resources.agentId)
        rig.controller.provision { told = it }
        assertEquals("saveAgent:create:claude-sonnet-5-5", rig.cloud.calls.last())

        // Typed failures are owner texts; nothing is retried.
        val failing = Rig().ready()
        listOf(CodingAgentFailure.INVALID_API_KEY, CodingAgentFailure.PERMISSION_DENIED, CodingAgentFailure.BETA_UNAVAILABLE,
            CodingAgentFailure.RATE_LIMITED, CodingAgentFailure.OVERLOADED, CodingAgentFailure.OFFLINE).forEach { code ->
            failing.cloud.calls.clear()
            failing.cloud.failNext("saveAgent", code)
            failing.controller.provision { told = it }
            assertEquals(CodingAgentFailure.message(code), told)
            assertEquals(listOf("saveAgent:create:claude-opus-5-5"), failing.cloud.calls)
        }
        failing.cloud.failNext("createVault", CodingAgentFailure.VAULT)
        failing.controller.provision { told = it }
        assertEquals(CodingAgentFailure.message(CodingAgentFailure.VAULT), told)
        assertNull(failing.controller.resources.vaultId)
        assertNotNull(failing.controller.resources.agentId)
    }

    @Test fun aSecurityReviewIsItsOwnKindOfOrderForAnAllowedRepositoryOnly() {
        val rig = Rig().ready()
        rig.cloud.onOutcome = { rig.cloud.emit(Events.running(), Events.idle("end_turn")) }
        assertTrue(rig.controller.take("Coding-Agent: Sicherheitsprüfung für device-ai", "Jarvis"))
        val question = rig.host.said.last()
        assertTrue(question, question.contains("Auftragsart: Sicherheitsprüfung"))
        assertTrue(question, question.contains("Geprüft wird nur der Code dieses Repositories – kein fremder Host, kein laufendes System."))
        assertTrue(rig.controller.take("Ja", "Jarvis"))
        assertEquals(CodingAgentTaskKind.SECURITY, rig.run.kind)
        assertTrue(rig.cloud.outcomes.single().first.contains("docs/security/SICHERHEITSPRUEFUNG-"))
        assertTrue(rig.cloud.outcomes.single().second.contains("kein fremder Host gescannt"))
        // A host is never a target: with one allowed repository the review is proposed for that repository only.
        val other = Rig().ready()
        assertTrue(other.controller.take("Coding-Agent: Sicherheitsprüfung von example.org", "Jarvis"))
        assertTrue(other.controller.pendingConfirmation()!!.contains("Repository: Ullmann27/device-ai (du hast keines genannt"))
    }

    // ── Failures of a run: what exists is said, nothing is repeated ─────────────────────────────────────────────

    @Test fun aFailedStartIsReportedOnceAndNeverRepeatedByItself() {
        mapOf(CodingAgentFailure.RATE_LIMITED to "nimmt gerade keine weitere Anfrage an", CodingAgentFailure.NO_CREDIT to "API-Guthaben",
            CodingAgentFailure.BUDGET_REJECTED to "Budget", CodingAgentFailure.OVERLOADED to "überlastet").forEach { (code, word) ->
            val rig = Rig().ready()
            rig.cloud.failNext("createSession", code)
            rig.orderAndConfirm()
            assertEquals(CodingAgentRunStatus.NOT_STARTED, rig.run.status)
            assertEquals(code, rig.run.failure)
            assertNull(rig.run.sessionId)
            assertEquals(1, rig.cloud.calls.count { it == "createSession" })
            assertTrue(rig.host.said.last(), rig.host.said.last().startsWith("Coding-Agent nicht gestartet: ") && rig.host.said.last().contains(word))
            // Nothing exists at Anthropic for it: no budget stays reserved.
            assertEquals(0L, rig.controller.reservedCents())
            assertTrue(rig.account.recorded.isEmpty())
        }
    }

    @Test fun anOrderThatDidNotReachTheSessionIsNotSentASecondTime() {
        val rig = Rig().ready()
        rig.cloud.failNext("defineOutcome", CodingAgentFailure.TIMEOUT)
        rig.orderAndConfirm()
        assertEquals(CodingAgentRunStatus.FAILED, rig.run.status)
        assertEquals(CodingAgentFailure.TIMEOUT, rig.run.failure)
        assertEquals(1, rig.cloud.calls.count { it == "defineOutcome" })
        assertEquals(1, rig.cloud.streamsOpened.get())
        // The session exists and the report says so.
        assertEquals("sesn_fake_01", rig.run.sessionId)
        assertTrue(rig.host.said.last().contains("Der Verlauf dieser Sitzung liegt bei Anthropic, bis du ihn löschst."))
    }

    @Test fun aSessionThatGivesUpOrHitsItsBudgetEndsWithThatStatement() {
        val auth = Rig().ready()
        auth.cloud.onOutcome = { auth.cloud.emit(Events.running(), Events.error("repository_authentication_error", "exhausted"), Events.idle("retries_exhausted")) }
        auth.orderAndConfirm()
        assertEquals(CodingAgentRunStatus.FAILED, auth.run.status)
        assertEquals(CodingAgentFailure.SESSION_REPOSITORY_AUTH, auth.run.failure)
        assertTrue(auth.host.said.last().startsWith("Coding-Agent fehlgeschlagen: GitHub hat das Token für dieses Repository abgelehnt."))
        assertEquals(listOf("false|false"), auth.host.ended.toList())

        val budget = Rig().ready()
        budget.github.heads["Ullmann27/device-ai@continuum-agent/run0001"] = "c".repeat(40)
        budget.cloud.state = CodingAgentCloud.SessionState("idle", CodingAgentEvent.Usage(null, 203, CodingAgentTokens(40_000, 9_000, 0, 0), 800), null, null)
        budget.cloud.onOutcome = { budget.cloud.emit(Events.running(), Events.bash("./gradlew test"), Events.usage(203), Events.idle("budget_reached")) }
        budget.orderAndConfirm()
        assertEquals(CodingAgentRunStatus.BUDGET_REACHED, budget.run.status)
        val report = budget.host.said.last()
        assertTrue(report, report.startsWith("Coding-Agent angehalten: Das Budget von 2,00 $ ist erreicht."))
        assertTrue(report, report.contains("Kosten: 2,03 $ laut Anthropic"))
        assertTrue(report, report.contains("Pull Request: keiner gefunden. Du kannst ihn selbst anlegen: https://github.com/Ullmann27/device-ai/compare/main...continuum-agent/run0001?expand=1"))

        val refusal = Rig().ready()
        refusal.cloud.onOutcome = { refusal.cloud.emit(Events.running(), Events.idle("refusal")) }
        refusal.orderAndConfirm()
        assertEquals(CodingAgentFailure.SESSION_REFUSAL, refusal.run.failure)
        val unknown = Rig().ready()
        unknown.cloud.onOutcome = { unknown.cloud.emit(Events.idle("etwas_neues")) }
        unknown.orderAndConfirm()
        assertEquals(CodingAgentFailure.SESSION_UNKNOWN_STOP, unknown.run.failure)
    }

    @Test fun whenGitHubCannotBeAskedAfterTheRunTheReportSaysUnverified() {
        val rig = Rig().ready()
        rig.cloud.onOutcome = { rig.github.failure = CodingAgentFailure.GITHUB_OFFLINE; Rig.happyScript(rig.cloud)() }
        rig.orderAndConfirm()
        val report = rig.host.said.last()
        assertTrue(report, report.startsWith("Coding-Agent fertig. Ob ein Pull Request existiert, konnte ich nicht prüfen."))
        // The agent's own „PULL REQUEST: …“ line is shown, but as the agent's claim – not as a fact of the app.
        assertTrue(report, report.indexOf("Bericht des Agenten (seine eigenen Angaben") < report.indexOf("PULL REQUEST: https://"))
        assertEquals(listOf("true|false"), rig.host.ended.toList())
    }

    // ── Approvals ───────────────────────────────────────────────────────────────────────────────────────────────

    @Test fun aPausedCallGoesToTheOwnerAndHisAnswerGoesBackAsAToolConfirmation() {
        val rig = Rig(pool).ready().withPull()
        rig.cloud.onOutcome = { rig.cloud.emit(Events.running(), Events.bash("./gradlew publishToMavenLocal", "ask", "sevt_ask1"), Events.idle("requires_action", "sevt_ask1")) }
        rig.cloud.onConfirm = { _, allow -> if (allow) rig.cloud.emit(Events.running(), Events.message("fertig"), Events.idle("end_turn")) }
        rig.orderAndConfirm()
        await("approval asked") { rig.store.runs().firstOrNull()?.status == CodingAgentRunStatus.WAITING_APPROVAL }
        assertEquals(listOf(CodingAgentApproval("sevt_ask1", "bash", "Befehl: ./gradlew publishToMavenLocal")), rig.run.approvals)
        assertTrue(rig.host.said.last(), rig.host.said.last().contains("wartet auf deine Freigabe: Befehl: ./gradlew publishToMavenLocal"))
        assertTrue(rig.host.steps.any { it == "agent-approval|Freigabe nötig|WAITING" })
        assertTrue(rig.controller.controls("Erlauben"))
        assertTrue(rig.controller.statusLine().contains("wartet auf deine Freigabe"))

        assertTrue(rig.controller.take("Erlauben", "Jarvis"))
        awaitReport(rig)
        // tool_use_id is the id of the agent.tool_use event (client patterns, § 4).
        assertEquals(listOf(Triple("sevt_ask1", true, null)), rig.cloud.confirmations.toList())
        assertEquals(CodingAgentRunStatus.COMPLETED, rig.run.status)
        assertTrue(rig.run.approvals.isEmpty())
    }

    @Test fun aDeniedCallTellsTheAgentWhyAndACallAgainstTheFixedRulesIsDeniedWithoutAsking() {
        val rig = Rig(pool).ready()
        rig.cloud.onOutcome = {
            rig.cloud.emit(Events.running(), Events.bash("git push origin main", "ask", "sevt_rule"), Events.idle("requires_action", "sevt_rule"))
        }
        rig.cloud.onConfirm = { id, _ ->
            if (id == "sevt_rule") rig.cloud.emit(Events.running(), Events.bash("./release.sh", "ask", "sevt_owner"), Events.idle("requires_action", "sevt_owner"))
            else rig.cloud.emit(Events.running(), Events.idle("end_turn"))
        }
        rig.orderAndConfirm()
        await("owner asked") { rig.store.runs().firstOrNull()?.approvals?.any { it.eventId == "sevt_owner" } == true }
        // The push to the default branch never became a question.
        assertEquals(Triple("sevt_rule", false, CodingAgentTexts.denyMessage("Push nur auf den Arbeitszweig continuum-agent/run0001")),
            rig.cloud.confirmations.first())
        assertFalse(rig.host.said.any { it.contains("git push origin main") && it.contains("wartet auf deine Freigabe") })
        rig.controller.decide(rig.run.id, "sevt_owner", allow = false)
        awaitReport(rig)
        assertEquals(Triple("sevt_owner", false, CodingAgentTexts.OWNER_DENY_MESSAGE), rig.cloud.confirmations.last())
        assertTrue(rig.host.said.last(), rig.host.said.last().contains("Auffälligkeiten:\n• Abgelehnt, ohne dich zu fragen (Regel: Push nur auf den Arbeitszweig"))
    }

    @Test fun anAnswerThatDidNotArriveLeavesTheQuestionOpen() {
        val rig = Rig(pool).ready()
        rig.cloud.onOutcome = { rig.cloud.emit(Events.running(), Events.bash("./x.sh", "ask", "sevt_q"), Events.idle("requires_action", "sevt_q")) }
        rig.orderAndConfirm()
        await("asked") { rig.store.runs().firstOrNull()?.approvals?.isNotEmpty() == true }
        rig.cloud.failNext("confirmTool", CodingAgentFailure.OFFLINE)
        rig.controller.decide(rig.run.id, "sevt_q", allow = true)
        await("told") { rig.host.said.any { it == CodingAgentTexts.decisionNotSent(CodingAgentFailure.message(CodingAgentFailure.OFFLINE)) } }
        assertEquals(listOf("sevt_q"), rig.run.approvals.map { it.eventId })
        assertEquals(CodingAgentRunStatus.WAITING_APPROVAL, rig.run.status)
        rig.cloud.onConfirm = { _, _ -> rig.cloud.emit(Events.running(), Events.idle("end_turn")) }
        rig.controller.decide(rig.run.id, "sevt_q", allow = true)
        awaitReport(rig)
    }

    // ── Stop ────────────────────────────────────────────────────────────────────────────────────────────────────

    @Test fun stoppIsTheDocumentedInterruptAndTheReportSaysWhatExists() {
        val rig = Rig(pool).ready()
        rig.github.heads["Ullmann27/device-ai@continuum-agent/run0001"] = "d".repeat(40)
        rig.cloud.onOutcome = { rig.cloud.emit(Events.running(), Events.bash("git push origin continuum-agent/run0001")) }
        // An interrupted turn ends with stop_reason end_turn; only the client knows it sent the interrupt (events, § Interrupt).
        rig.cloud.onInterrupt = { rig.cloud.emit(Events.interrupt(), Events.evaluationEnd("interrupted", ""), Events.idle("end_turn")) }
        rig.cloud.state = CodingAgentCloud.SessionState("idle", CodingAgentEvent.Usage(null, 41, CodingAgentTokens(5_000, 900, 0, 0), 95), "interrupted", null)
        rig.orderAndConfirm()
        await("running") { rig.store.runs().firstOrNull()?.progress?.isNotEmpty() == true }
        assertTrue(rig.controller.controls("Stopp den Coding-Agenten"))
        assertTrue(rig.controller.take("Stopp den Coding-Agenten", "Jarvis"))
        awaitReport(rig)
        assertEquals(1, rig.cloud.interrupts.get())
        assertEquals(CodingAgentRunStatus.STOPPED, rig.run.status)
        assertEquals(CodingAgentStopCause.OWNER, rig.run.stopCause)
        val report = rig.host.said.last()
        assertTrue(report, report.startsWith("Coding-Agent gestoppt."))
        assertTrue(report, report.contains("Zweig continuum-agent/run0001: existiert"))
        assertTrue(report, report.contains("Pull Request: keiner gefunden."))
        assertTrue(report, report.contains("Ergebnisprüfung bei Anthropic (nicht von dieser App): unterbrochen"))
    }

    @Test fun theBareStopWordOnlyMeansTheAgentWhenNothingElseIsRunning() {
        val rig = Rig(pool).ready()
        assertFalse("no run: today's route", rig.controller.take("Stopp", "Jarvis"))
        rig.cloud.onOutcome = { rig.cloud.emit(Events.running()) }
        rig.cloud.onInterrupt = { rig.cloud.emit(Events.idle("end_turn")) }
        rig.orderAndConfirm()
        await("running") { rig.store.runs().firstOrNull()?.status == CodingAgentRunStatus.RUNNING && rig.cloud.calls.contains("defineOutcome") }
        rig.host.idleNow = false
        assertFalse("other work in front: today's route", rig.controller.take("Stopp", "Jarvis"))
        assertEquals(0, rig.cloud.interrupts.get())
        rig.host.idleNow = true
        assertTrue(rig.controller.take("Stopp", "Jarvis"))
        awaitReport(rig)
        assertEquals(CodingAgentRunStatus.STOPPED, rig.run.status)
    }

    @Test fun aStopThatDidNotArriveIsNotCalledAStop() {
        val rig = Rig(pool).ready()
        rig.cloud.onOutcome = { rig.cloud.emit(Events.running()) }
        rig.orderAndConfirm()
        await("running") { rig.cloud.calls.contains("defineOutcome") }
        rig.cloud.failNext("interrupt", CodingAgentFailure.OFFLINE)
        rig.controller.stop()
        await("told") { rig.host.said.any { it == CodingAgentTexts.stopNotSent(CodingAgentFailure.message(CodingAgentFailure.OFFLINE)) } }
        assertTrue(rig.run.active)
        assertNull(rig.run.stopCause)
        rig.cloud.emit(Events.idle("end_turn"))
        awaitReport(rig)
        assertEquals("ended by itself, not by a stop", CodingAgentRunStatus.COMPLETED, rig.run.status)
    }

    @Test fun theRunTimeLimitInterruptsAndPendingQuestionsAreDeniedFirst() {
        val rig = Rig(pool).ready()
        rig.cloud.onOutcome = { rig.cloud.emit(Events.running(), Events.bash("./lange.sh", "ask", "sevt_wait"), Events.idle("requires_action", "sevt_wait")) }
        rig.cloud.onInterrupt = { rig.cloud.emit(Events.idle("end_turn")) }
        rig.orderAndConfirm()
        await("asked") { rig.store.runs().firstOrNull()?.approvals?.isNotEmpty() == true }
        val (millis, limit) = rig.timers.single()
        assertEquals(30 * 60_000L, millis)
        limit()
        awaitReport(rig)
        assertEquals(Triple("sevt_wait", false, CodingAgentTexts.STOP_DENY_MESSAGE), rig.cloud.confirmations.single())
        assertTrue(rig.cloud.calls.indexOf("confirmTool:sevt_wait") < rig.cloud.calls.indexOf("interrupt"))
        assertEquals(CodingAgentStopCause.TIME_LIMIT, rig.run.stopCause)
        assertTrue(rig.host.said.last().startsWith("Coding-Agent gestoppt: Die Laufzeit von 30 Minuten war erreicht."))
    }

    // ── Reconnect and restart ───────────────────────────────────────────────────────────────────────────────────

    @Test fun aBrokenStreamIsReopenedWithTheHistoryFirstAndNothingCountsTwice() {
        val rig = Rig().ready().withPull()
        rig.cloud.onOutcome = { rig.cloud.emit(Events.running(), Events.bash("./gradlew test")); rig.cloud.breakStream() }
        // While the app was away the session went on; the second connection finds that in the history (lossless reconnect).
        rig.cloud.onStream = { number -> if (number == 2) { rig.cloud.emit(Events.message("GEPRÜFT: grün")); rig.cloud.history += Events.idle("end_turn") } }
        rig.orderAndConfirm()
        assertEquals(2, rig.cloud.streamsOpened.get())
        assertEquals(2, rig.cloud.calls.count { it == "listEvents" })
        assertEquals("the order is sent once", 1, rig.cloud.calls.count { it == "defineOutcome" })
        assertEquals(listOf(CodingAgentController.RECONNECT_PAUSE_MILLIS), rig.pauses.toList())
        assertEquals(CodingAgentRunStatus.COMPLETED, rig.run.status)
        assertEquals(listOf("Befehl: ./gradlew test", "Agent: GEPRÜFT: grün"), rig.run.progress)
    }

    @Test fun aConnectionThatStaysBrokenIsSaidAsUnknownAndAktualisierenReadsItAgain() {
        val rig = Rig().ready().withPull()
        rig.cloud.onOutcome = { rig.cloud.emit(Events.running()); rig.cloud.breakStream() }
        rig.cloud.onStream = { number -> if (number in 2..3) rig.cloud.breakStream() }
        rig.orderAndConfirm()
        assertEquals(1 + CodingAgentController.MAX_RECONNECTS, rig.cloud.streamsOpened.get())
        assertEquals(CodingAgentRunStatus.CONNECTION_LOST, rig.run.status)
        assertTrue(rig.run.active)
        assertTrue(rig.host.said.last(), rig.host.said.last().startsWith(CodingAgentFailure.message(CodingAgentFailure.STREAM_LOST)))
        assertTrue(rig.host.said.last().contains("Die Sitzung kann bei Anthropic weiterarbeiten; ihr Budget bleibt die Obergrenze."))
        // No report was made up, and the budget stays reserved while the end is unknown.
        assertFalse(rig.host.said.any { it.startsWith("Coding-Agent fertig") })
        assertEquals(200L, rig.controller.reservedCents())
        // The app comes to the front again: one quiet re-read. It fails again, which is neither announced a second
        // time nor repeated within the next minute – no polling.
        val saidBefore = rig.host.said.size
        rig.cloud.onStream = { rig.cloud.breakStream() }
        rig.controller.attach(rig.host)
        assertEquals(2 * (1 + CodingAgentController.MAX_RECONNECTS), rig.cloud.streamsOpened.get())
        assertEquals(CodingAgentRunStatus.CONNECTION_LOST, rig.run.status)
        assertEquals(saidBefore, rig.host.said.size)
        rig.controller.attach(rig.host)
        rig.now += CodingAgentController.QUIET_RECONNECT_GAP_MILLIS - 1
        rig.controller.attach(rig.host)
        assertEquals(6, rig.cloud.streamsOpened.get())

        rig.cloud.onStream = { rig.cloud.history += listOf(Events.message("PULL REQUEST: angelegt"), Events.idle("end_turn")) }
        rig.controller.refresh(rig.run.id)
        assertEquals(7, rig.cloud.streamsOpened.get())
        assertEquals(CodingAgentRunStatus.COMPLETED, rig.run.status)
        assertEquals(0L, rig.controller.reservedCents())
        assertTrue(rig.host.said.last().startsWith("Coding-Agent fertig: Der Entwurfs-Pull-Request liegt vor."))
    }

    @Test fun anInvalidKeyDuringARunIsNotAnsweredWithReconnects() {
        val rig = Rig().ready()
        rig.cloud.onOutcome = { rig.cloud.emit(Events.running()); rig.cloud.failNext("openStream", CodingAgentFailure.INVALID_API_KEY); rig.cloud.breakStream() }
        rig.orderAndConfirm()
        assertEquals(2, rig.cloud.calls.count { it == "openStream" })
        assertEquals(CodingAgentRunStatus.CONNECTION_LOST, rig.run.status)
        assertTrue(rig.host.said.last(), rig.host.said.last().contains("Grund: Anthropic hat den gespeicherten API-Schlüssel abgelehnt"))
    }

    @Test fun afterARestartTheStoredSessionIsReadOnceFromItsHistoryAndTheOrderIsNotSentAgain() {
        val first = Rig(pool).ready()
        first.cloud.onOutcome = { first.cloud.emit(Events.running(), Events.bash("./gradlew test")) }
        first.orderAndConfirm()
        await("running") { first.store.runs().firstOrNull()?.progress?.isNotEmpty() == true }
        first.controller.apply { }   // the process dies here: nothing is told to the old controller

        // A new process: same stored state, same secrets, the session finished meanwhile.
        val second = Rig(storage = first.storage, secrets = first.secrets)
        second.github.heads["Ullmann27/device-ai@continuum-agent/run0001"] = "e".repeat(40)
        second.github.pull = draftPull
        second.cloud.history += first.cloud.history
        second.cloud.history += listOf(Events.message("PULL REQUEST: https://github.com/Ullmann27/device-ai/pull/912"), Events.idle("end_turn"))
        assertEquals(CodingAgentRunStatus.RUNNING, second.run.status)
        second.controller.attach(second.host)
        assertEquals(listOf("openStream", "listEvents", "session"), second.cloud.calls)
        assertEquals(CodingAgentRunStatus.COMPLETED, second.run.status)
        assertEquals(listOf("Befehl: ./gradlew test", "Agent: PULL REQUEST: https://github.com/Ullmann27/device-ai/pull/912"), second.run.progress)
        assertTrue(second.host.said.last().startsWith("Coding-Agent fertig: Der Entwurfs-Pull-Request liegt vor."))
        // Attaching again (the app comes to the front a second time) does nothing more.
        second.controller.attach(second.host)
        assertEquals(3, second.cloud.calls.size)
        assertEquals(1, second.host.said.count { it.startsWith("Coding-Agent fertig") })
    }

    @Test fun whatAnEarlierProcessLeftUnfinishedIsSettledTruthfully() {
        // The app died before the session existed: nothing was sent, and that is what the list says.
        val early = Rig()
        early.ready()
        early.store.putRun(CodingAgentRun("runX", CodingAgentTaskKind.CODE, "Ullmann27/device-ai", "main", null, "continuum-agent/runX", "a b", 200, 30,
            "claude-opus-5-5", early.now))
        early.controller.attach(early.host)
        assertEquals(CodingAgentRunStatus.NOT_STARTED, early.run.status)
        assertEquals(CodingAgentFailure.APP_RESTARTED_BEFORE_START, early.run.failure)
        assertTrue(early.cloud.calls.isEmpty())

        // A session exists but its history holds no order: the app does not send one on its own after a restart.
        val orphan = Rig()
        orphan.ready()
        orphan.store.putRun(CodingAgentRun("runY", CodingAgentTaskKind.CODE, "Ullmann27/device-ai", "main", null, "continuum-agent/runY", "a b", 200, 30,
            "claude-opus-5-5", orphan.now, status = CodingAgentRunStatus.RUNNING, sessionId = "sesn_orphan"))
        orphan.controller.attach(orphan.host)
        assertEquals(CodingAgentRunStatus.FAILED, orphan.run.status)
        assertEquals(CodingAgentFailure.SESSION_WITHOUT_ORDER, orphan.run.failure)
        assertFalse(orphan.cloud.calls.contains("defineOutcome"))

        // Without the key nothing is asked; the state is called unknown.
        val keyless = Rig()
        keyless.ready()
        keyless.account.key = null
        keyless.store.putRun(CodingAgentRun("runZ", CodingAgentTaskKind.CODE, "Ullmann27/device-ai", "main", null, "continuum-agent/runZ", "a b", 200, 30,
            "claude-opus-5-5", keyless.now, status = CodingAgentRunStatus.RUNNING, sessionId = "sesn_z"))
        keyless.controller.attach(keyless.host)
        assertEquals(CodingAgentRunStatus.CONNECTION_LOST, keyless.run.status)
        assertTrue(keyless.cloud.calls.isEmpty())

        // A run that ended while no screen was there is reported when one is.
        val silent = Rig()
        silent.controller.giveConsent()
        silent.controller.saveToken(FAKE_GITHUB_TOKEN)
        silent.controller.addRepository("Ullmann27/device-ai")
        silent.store.putRun(CodingAgentRun("runW", CodingAgentTaskKind.CODE, "Ullmann27/device-ai", "main", null, "continuum-agent/runW", "a b", 200, 30,
            "claude-opus-5-5", silent.now, status = CodingAgentRunStatus.STOPPED, sessionId = "sesn_w", settled = true))
        silent.controller.attach(silent.host)
        assertEquals(listOf("Coding-Agent gestoppt."), silent.host.said.map { it.lineSequence().first() })
        assertTrue(silent.run.reported)
    }

    // ── Cost, history, token ────────────────────────────────────────────────────────────────────────────────────

    @Test fun aSessionWhoseCostIsUnknownKeepsItsWholeBudgetReserved() {
        val rig = Rig().ready()
        rig.account.limitCents = 500
        rig.cloud.onOutcome = { rig.cloud.emit(Events.running(), Events.idle("end_turn")) }
        rig.cloud.failNext("session", CodingAgentFailure.OFFLINE)
        rig.orderAndConfirm()
        assertEquals(CodingAgentRunStatus.COMPLETED, rig.run.status)
        assertFalse(rig.run.settled)
        assertNull(rig.run.costCents)
        assertTrue(rig.host.said.last().contains("Kosten: von Anthropic noch nicht gemeldet (Obergrenze 2,00 $)"))
        assertEquals(200L, rig.controller.reservedCents())
        rig.account.spentCents = 150
        assertTrue(rig.controller.take(order, "Jarvis"))
        assertEquals(CodingAgentTexts.monthlyLimit(200, 150, 200, 500), rig.host.said.last())
        assertTrue(rig.host.said.last().contains("für einen laufenden Agenten reserviert 2,00 $"))
        // „Aktualisieren“ reads the final figure once; it is recorded once.
        rig.controller.refresh(rig.run.id)
        assertTrue(rig.run.settled)
        assertEquals(137L, rig.run.costCents)
        assertEquals(1, rig.account.recorded.size)
        rig.controller.refresh(rig.run.id)
        assertEquals(1, rig.account.recorded.size)
        assertEquals(0L, rig.controller.reservedCents())
    }

    @Test fun theOwnerDeletesASessionsHistoryAtAnthropicFromTheApp() {
        val rig = Rig().ready()
        rig.cloud.onOutcome = { rig.cloud.emit(Events.running(), Events.idle("end_turn")) }
        rig.orderAndConfirm()
        val id = rig.run.id
        assertFalse("still at Anthropic: stays in the list", rig.controller.removeRun(id))
        var told = ""
        rig.cloud.failNext("deleteSession", CodingAgentFailure.CONFLICT)
        rig.controller.deleteHistory(id) { told = it }
        assertEquals(CodingAgentTexts.historyNotDeleted(CodingAgentFailure.message(CodingAgentFailure.CONFLICT)), told)
        assertFalse(rig.run.historyDeleted)
        rig.controller.deleteHistory(id) { told = it }
        assertEquals(CodingAgentTexts.HISTORY_DELETED, told)
        assertEquals(listOf("sesn_fake_01"), rig.cloud.deletedSessions.toList())
        assertTrue(rig.run.historyDeleted)
        assertTrue(rig.controller.reportText(id)!!.endsWith("Der Verlauf dieser Sitzung ist bei Anthropic gelöscht."))
        rig.controller.deleteHistory(id) { told = it }
        assertEquals(CodingAgentTexts.HISTORY_ALREADY_DELETED, told)
        assertTrue(rig.controller.removeRun(id))
        assertTrue(rig.store.runs().isEmpty())
    }

    @Test fun aRunningSessionsHistoryIsNotDeleted() {
        val rig = Rig(pool).ready()
        rig.cloud.onOutcome = { rig.cloud.emit(Events.running()) }
        rig.orderAndConfirm()
        await("running") { rig.cloud.calls.contains("defineOutcome") }
        var told = ""
        rig.controller.deleteHistory(rig.run.id) { told = it }
        assertEquals(CodingAgentTexts.HISTORY_WHILE_RUNNING, told)
        rig.controller.removeToken { told = it }
        assertEquals(CodingAgentTexts.TOKEN_REMOVE_WHILE_RUNNING, told)
        assertTrue(rig.controller.tokenPresent())
        assertTrue(rig.cloud.deletedSessions.isEmpty())
        rig.cloud.emit(Events.idle("end_turn"))
        awaitReport(rig)
    }

    @Test fun theTokenIsStoredEncryptedOnlyAndRemovingItAlsoDeletesTheVaultCredential() {
        val rig = Rig()
        assertEquals(CodingAgentTexts.TOKEN_CLASSIC, rig.controller.saveToken("ghp_" + "a".repeat(36)))
        assertEquals(CodingAgentTexts.TOKEN_IMPLAUSIBLE, rig.controller.saveToken("mein passwort"))
        assertFalse(rig.controller.tokenPresent())
        assertEquals(CodingAgentTexts.TOKEN_STORED, rig.controller.saveToken("  $FAKE_GITHUB_TOKEN\n"))
        // Saving sends nothing, and the plain token is in no stored value.
        assertTrue(rig.cloud.calls.isEmpty() && rig.github.calls.isEmpty())
        assertFalse(rig.secrets.values.values.any { it.contains(FAKE_GITHUB_TOKEN) })
        assertFalse(rig.storage.value.orEmpty().contains(FAKE_GITHUB_TOKEN))
        assertTrue(rig.controller.statusText().contains("GitHub-Token: Token gespeichert (verschlüsselt, wird nie angezeigt)"))

        rig.ready()
        var told = ""
        rig.controller.provision { told = it }
        assertEquals(listOf(FAKE_GITHUB_TOKEN), rig.cloud.credentials.toList())
        // Anthropic is unreachable: the device copy is gone, and the app says that the vault entry is not.
        rig.cloud.failNext("deleteGitHubCredential", CodingAgentFailure.OFFLINE)
        rig.controller.removeToken { told = it }
        assertFalse(rig.controller.tokenPresent())
        assertTrue(rig.secrets.values.isEmpty())
        assertEquals(CodingAgentTexts.TOKEN_REMOVED_LOCAL + " " + CodingAgentTexts.tokenVaultKept(CodingAgentFailure.message(CodingAgentFailure.OFFLINE)), told)
        assertEquals("vcrd_fake_01", rig.controller.resources.credentialId)
        rig.controller.removeToken { told = it }
        assertEquals(CodingAgentTexts.TOKEN_REMOVED_LOCAL + " " + CodingAgentTexts.TOKEN_VAULT_REMOVED, told)
        assertEquals("deleteGitHubCredential:vcrd_fake_01", rig.cloud.calls.last())
        assertNull(rig.controller.resources.credentialId)
        assertEquals("vlt_fake_01", rig.controller.resources.vaultId)
    }

    // ── Spoken control and the secret scan ──────────────────────────────────────────────────────────────────────

    @Test fun theAreaAndTheStatusAreReachableBySentenceAndOtherSentencesKeepTheirRoute() {
        val rig = Rig().ready()
        assertTrue(rig.controller.take("Öffne die Coding-Agenten", "Jarvis"))
        assertEquals(1, rig.host.panels.get())
        assertTrue(rig.controller.take("Was macht der Coding-Agent gerade?", "Jarvis"))
        assertEquals(CodingAgentTexts.NOTHING_RUNNING, rig.host.said.last())
        assertTrue(rig.controller.take("Stopp den Coding-Agenten", "Jarvis"))
        assertEquals(CodingAgentTexts.NOTHING_RUNNING, rig.host.said.last())
        listOf("Öffne die Agenten", "Was ist ein Coding-Agent?", "Erlauben", "Ja", "Öffne das PDF Argon4", "Lass den Agenten in Ruhe").forEach {
            assertFalse(it, rig.controller.take(it, "Jarvis"))
            assertFalse(it, rig.controller.controls(it))
        }
        assertTrue(rig.cloud.calls.isEmpty())
    }

    @Test fun noSecretEverAppearsInAnythingShownLoggedOrStored() {
        val rig = Rig().ready().withPull()
        // The agent itself prints both secrets, in a message, in a command and in what the grader explains.
        rig.cloud.onOutcome = {
            rig.cloud.emit(Events.running(), Events.bash("curl -H 'Authorization: Bearer $FAKE_GITHUB_TOKEN' https://api.github.com/user"),
                Events.bash("echo $FAKE_API_KEY > key.txt && git push origin main"),
                Events.tool("edit", mapOf("file_path" to "/workspace/device-ai/$FAKE_GITHUB_TOKEN.txt")),
                Events.evaluationEnd("needs_revision", "Im Diff steht $FAKE_GITHUB_TOKEN"),
                Events.message("Mein Token ist $FAKE_GITHUB_TOKEN und der Schlüssel $FAKE_API_KEY"), Events.idle("end_turn"))
        }
        rig.cloud.state = CodingAgentCloud.SessionState("idle", CodingAgentEvent.Usage(null, 50, CodingAgentTokens(1, 1, 0, 0), 5),
            "needs_revision", "Erklärung mit $FAKE_API_KEY")
        rig.orderAndConfirm()
        rig.controller.take("Was macht der Coding-Agent gerade?", "Jarvis")
        var told = ""
        rig.controller.deleteHistory(rig.run.id) { told = it }
        rig.controller.removeToken { told += it }
        // And a failing flow with typed errors.
        val failing = Rig().ready()
        failing.cloud.failNext("saveAgent", CodingAgentFailure.INVALID_API_KEY)
        failing.orderAndConfirm()

        listOf(rig, failing).forEach { one ->
            val everything = one.emitted() + told
            assertTrue("something must have been emitted", everything.length > 500)
            assertFalse("API key leaked", everything.contains(FAKE_API_KEY))
            assertFalse("GitHub token leaked", everything.contains(FAKE_GITHUB_TOKEN))
            assertFalse(everything.contains(FAKE_GITHUB_TOKEN.take(24)))
        }
        assertTrue(rig.emitted().contains(CodingAgentRedactor.REMOVED))
        // The executed push to the default branch is in the report as an anomaly.
        assertTrue(rig.run.ruleNotes.any { it.contains("Push nur auf den Arbeitszweig") })
    }
}
