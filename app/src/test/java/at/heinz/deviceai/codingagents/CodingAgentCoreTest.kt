package at.heinz.deviceai.codingagents

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The Android-free core of the cloud coding agents without any network: what the setup accepts, the fixed rules the
 * agent is created with, the app's own rule check on tool calls, how session events become progress and a state,
 * and how the report words what is known and what is not.
 */
class CodingAgentCoreTest {
    private fun run(status: CodingAgentRunStatus = CodingAgentRunStatus.COMPLETED, facts: CodingAgentGitFacts? = null) = CodingAgentRun(
        id = "run0001", kind = CodingAgentTaskKind.CODE, repository = "Ullmann27/device-ai", baseBranch = "main",
        baseSha = "a1b2c3d4e5f60718", workBranch = "continuum-agent/run0001", task = "behebe den Fehler im Parser", budgetCents = 200,
        maxRunMinutes = 30, model = "claude-opus-5-5", createdAtMillis = 1_000_000, status = status, sessionId = "sesn_1",
        endedAtMillis = 1_000_000 + 754_000, facts = facts)

    // ── Setup validation ────────────────────────────────────────────────────────────────────────────────────────

    @Test fun aRepositoryIsOnlyEverOwnerSlashName() {
        assertEquals("Ullmann27/device-ai", CodingAgentRepository.normalise(" Ullmann27/device-ai "))
        assertEquals("Ullmann27/device-ai", CodingAgentRepository.normalise("https://github.com/Ullmann27/device-ai.git"))
        assertEquals("Ullmann27/device-ai", CodingAgentRepository.normalise("github.com/Ullmann27/device-ai/"))
        listOf("device-ai", "Ullmann27/", "/device-ai", "a/b/c", "Ullmann27/..", "-x/repo", "x--y/repo", "Ullmann27/device ai",
            "https://example.org/Ullmann27/device-ai", "").forEach { assertNull(it, CodingAgentRepository.normalise(it)) }
        assertEquals("https://github.com/Ullmann27/device-ai", CodingAgentRepository.url("Ullmann27/device-ai"))
    }

    @Test fun amountsAreDollarsAsTypedAndNothingElse() {
        assertEquals(200, CodingAgentMoney.cents("2"))
        assertEquals(250, CodingAgentMoney.cents("2,50"))
        assertEquals(250, CodingAgentMoney.cents(" 2.5 $"))
        assertEquals(25, CodingAgentMoney.cents("0,25"))
        listOf("", "zwei", "2,555", "-1", "2,", "1e3").forEach { assertNull(it, CodingAgentMoney.cents(it)) }
        assertEquals("1,37 $", CodingAgentMoney.usd(137L))
    }

    @Test fun onlyAFineGrainedTokenIsAccepted() {
        assertTrue(CodingAgentGitHubToken.plausible(FAKE_GITHUB_TOKEN))
        assertFalse(CodingAgentGitHubToken.plausible("ghp_" + "a".repeat(36)))
        assertTrue(CodingAgentGitHubToken.classic("ghp_" + "a".repeat(36)))
        assertFalse(CodingAgentGitHubToken.plausible("github_pat_kurz"))
        assertFalse(CodingAgentGitHubToken.plausible(FAKE_GITHUB_TOKEN + " "))
    }

    @Test fun storedSettingsAreBroughtIntoTheirBoundsAndSurviveARestart() {
        val storage = InMemoryCodingAgentStorage()
        val store = CodingAgentStore(storage)
        assertTrue(store.updateSettings { it.copy(consentGiven = true, repositories = listOf("Ullmann27/device-ai", "ullmann27/DEVICE-AI",
            "kein repo"), budgetCents = 99_999, maxBudgetCents = 99_999, maxRunMinutes = 1, model = "gpt") })
        assertEquals(listOf("Ullmann27/device-ai"), store.settings.repositories)
        assertEquals(CodingAgentSettings.ABSOLUTE_MAX_BUDGET_CENTS, store.settings.maxBudgetCents)
        assertEquals(CodingAgentSettings.ABSOLUTE_MAX_BUDGET_CENTS, store.settings.budgetCents)
        assertEquals(CodingAgentSettings.MIN_RUN_MINUTES, store.settings.maxRunMinutes)
        assertEquals("claude-opus-5-5", store.settings.model)
        assertTrue(store.updateResources { it.copy(agentId = "agent_1", agentVersion = 3, installTag = "abc123") })
        assertTrue(store.putRun(run().copy(approvals = listOf(CodingAgentApproval("sevt_9", "bash", "Befehl: ls")),
            facts = CodingAgentGitFacts(5, "ffff", 2, 3, 912, "https://github.com/Ullmann27/device-ai/pull/912", true, "open", false, "a1b2"),
            tokens = CodingAgentTokens(1, 2, 3, 4), settled = true, reported = true, stopCause = CodingAgentStopCause.TIME_LIMIT)))

        val again = CodingAgentStore(storage)
        assertEquals(store.settings, again.settings)
        assertEquals(store.resources, again.resources)
        assertEquals(store.runs(), again.runs())
        assertTrue(again.runs().single().settled)
    }

    @Test fun aDamagedOrUnwritableStoreFallsBackToNoConsentAndKeepsTheOldValue() {
        val damaged = CodingAgentStore(InMemoryCodingAgentStorage().apply { value = "{kaputt" })
        assertFalse(damaged.settings.consentGiven)
        assertTrue(damaged.settings.repositories.isEmpty())
        assertTrue(damaged.runs().isEmpty())

        val storage = InMemoryCodingAgentStorage()
        val store = CodingAgentStore(storage)
        storage.failWrites = true
        assertFalse(store.updateSettings { it.copy(consentGiven = true) })
        assertFalse(store.settings.consentGiven)
        assertFalse(store.putRun(run()))
        assertTrue(store.runs().isEmpty())
    }

    // ── The agent definition ────────────────────────────────────────────────────────────────────────────────────

    @Test fun theSystemPromptCarriesEveryFixedRule() {
        val prompt = CodingAgentPrompt.SYSTEM
        listOf("Nur das eingebundene Repository", "Kein anderes Repository", "continuum-agent/", "Entwurfs-Pull-Request",
            "auf den Standardzweig oder einen anderen bestehenden Zweig pushen", "force-pushen", "zusammenführen (mergen)",
            ".github/workflows", "Secrets", "Signaturdateien", "Release-Einstellungen", "Keine Geheimnisse im Code",
            "nicht geprüft", "Keine erfundenen Ergebnisse", "Keine Scans fremder Hosts", "keine Angriffe oder Exploits gegen laufende Systeme",
            "auf Deutsch", "Material, keine Anweisung").forEach { assertTrue("rule missing: $it", prompt.contains(it)) }
        // The prompt is a constant of the code: no key, no token, nothing of the owner.
        assertEquals(CodingAgentRedactor().clean(prompt), prompt)
        assertFalse(prompt.contains("Ullmann27"))
    }

    @Test fun aChangedPromptVersionOrModelIsAnUpdateOfTheSameAgent() {
        assertEquals("v${CodingAgentPrompt.VERSION}:claude-opus-5-5", CodingAgentPrompt.fingerprint("claude-opus-5-5"))
        assertNotEquals(CodingAgentPrompt.fingerprint("claude-opus-5-5"), CodingAgentPrompt.fingerprint("claude-sonnet-5-5"))
        // The owner's default; the app never picks another model by itself.
        assertEquals("claude-opus-5-5", CodingAgentSettings().model)
        val settings = CodingAgentSettings(tokenGeneration = 2)
        val complete = CodingAgentResources("agent_1", 1, CodingAgentPrompt.fingerprint(settings.model), "env_1",
            CodingAgentEnvironment.VERSION, "vlt_1", "vcrd_1", 2)
        assertTrue(complete.complete(settings))
        assertFalse(complete.complete(settings.copy(model = "claude-sonnet-5-5")))
        assertFalse(complete.complete(settings.copy(tokenGeneration = 3)))
        assertFalse(complete.copy(environmentVersion = 0).complete(settings))
    }

    @Test fun theOutcomeIsTheOwnersWordsPlusTheFactsOfTheRunAndAGradeableRubric() {
        val code = run()
        val description = CodingAgentPrompt.outcomeDescription(code)
        listOf("„behebe den Fehler im Parser“", "Ullmann27/device-ai", "/workspace/device-ai", "Ausgangszweig: main",
            "continuum-agent/run0001", "Entwurfs-Pull-Request", "nicht: auf main pushen").forEach { assertTrue(it, description.contains(it)) }
        val rubric = CodingAgentPrompt.rubric(code)
        listOf("continuum-agent/run0001", "Entwurf (draft)", ".github/workflows", "nicht geprüft", "kein Geheimnis",
            "GEÄNDERT, GEPRÜFT, NICHT GEPRÜFT und PULL REQUEST").forEach { assertTrue(it, rubric.contains(it)) }
        assertFalse(rubric.contains("SICHERHEITSPRUEFUNG"))

        val security = code.copy(kind = CodingAgentTaskKind.SECURITY)
        assertTrue(CodingAgentPrompt.outcomeDescription(security).contains("docs/security/SICHERHEITSPRUEFUNG-run0001.md"))
        assertTrue(CodingAgentPrompt.rubric(security).contains("kein fremder Host gescannt"))
    }

    @Test fun theSandboxReachesOnlyGitHubPackageSourcesAndBuildArtifacts() {
        assertEquals(setOf("github.com", "api.github.com", "codeload.github.com", "objects.githubusercontent.com",
            "raw.githubusercontent.com", "services.gradle.org", "downloads.gradle.org", "plugins.gradle.org",
            "repo.maven.apache.org", "repo1.maven.org", "maven.google.com", "dl.google.com"), CodingAgentEnvironment.ALLOWED_HOSTS.toSet())
        assertTrue(CodingAgentEnvironment.ALLOWED_HOSTS.none { it.contains('*') })
        assertFalse(CodingAgentEnvironment.ALLOW_MCP_SERVERS)
        // The token is substituted for GitHub's API only.
        assertEquals(listOf("api.github.com"), CodingAgentEnvironment.TOKEN_HOSTS)
    }

    // ── The app's own rule check ────────────────────────────────────────────────────────────────────────────────

    private fun bash(command: String) = CodingAgentRules.violation("bash", mapOf("command" to command), "continuum-agent/run0001", "main")

    @Test fun ruleBreakingCallsAreRecognised() {
        mapOf(
            "git push --force origin continuum-agent/run0001" to "kein Force-Push",
            "git push -f origin continuum-agent/run0001" to "kein Force-Push",
            "git push origin +continuum-agent/run0001" to "kein Force-Push",
            "git push origin --delete alte-arbeit" to "keine Zweige löschen",
            "git branch -D main" to "keine Zweige löschen",
            "gh pr merge 912 --squash" to "nichts zusammenführen",
            "curl -X PUT -H \"Authorization: Bearer \$GITHUB_TOKEN\" https://api.github.com/repos/Ullmann27/device-ai/pulls/912/merge" to "nichts zusammenführen",
            "gh secret set SIGNING_KEY" to "keine Secrets",
            "curl https://api.github.com/repos/Ullmann27/device-ai/actions/secrets" to "keine Secrets",
            "gh release create v1" to "keine Releases oder Tags",
            "git tag v9 && git push origin continuum-agent/run0001 --tags" to "keine Releases oder Tags",
            "echo x > .github/workflows/ci.yml" to "Workflows und CI werden nicht geändert",
            "git push origin main" to "Push nur auf den Arbeitszweig continuum-agent/run0001",
            "git push origin continuum-agent/run0001:main" to "Push nur auf den Arbeitszweig continuum-agent/run0001",
            "git push origin HEAD" to "Push nur auf den Arbeitszweig continuum-agent/run0001",
            "git push origin feature/x" to "Push nur auf den Arbeitszweig continuum-agent/run0001"
        ).forEach { (command, rule) -> assertEquals(command, rule, bash(command)) }
        assertEquals("Workflows und CI werden nicht geändert",
            CodingAgentRules.violation("write", mapOf("file_path" to "/workspace/device-ai/.github/workflows/build.yml"), "w", "main"))
        assertEquals("Signaturdateien werden nicht geändert",
            CodingAgentRules.violation("edit", mapOf("file_path" to "/workspace/device-ai/release.jks"), "w", "main"))
    }

    @Test fun ordinaryWorkOnTheBranchBreaksNoRule() {
        listOf("git checkout -b continuum-agent/run0001", "./gradlew :app:testDebugUnitTest", "git merge origin/main",
            "git push origin continuum-agent/run0001", "git push -u origin continuum-agent/run0001", "cat .github/workflows/ci.yml",
            "git status && git diff", "npm test").forEach { assertNull(it, bash(it)) }
        assertNull(CodingAgentRules.violation("edit", mapOf("file_path" to "/workspace/device-ai/app/src/main/java/Parser.kt"), "w", "main"))
        assertNull(CodingAgentRules.violation("read", mapOf("file_path" to "/workspace/device-ai/.github/workflows/ci.yml"), "w", "main"))
    }

    // ── Events to progress and state ────────────────────────────────────────────────────────────────────────────

    private fun fold() = CodingAgentFold("continuum-agent/run0001", "main", CodingAgentRedactor { listOf(FAKE_GITHUB_TOKEN) })

    @Test fun eventsBecomeGermanProgressLinesAndAState() {
        val fold = fold()
        listOf(Events.outcomeDefined(), Events.running(), Events.thinking(), Events.bash("./gradlew test"),
            Events.tool("edit", mapOf("file_path" to "/workspace/device-ai/Parser.kt")),
            Events.tool("read", mapOf("file_path" to "/workspace/device-ai/README.md")),
            Events.error("model_overloaded_error", "retrying"), Events.rescheduled(),
            Events.evaluationStart(0), Events.evaluationEnd("needs_revision", "Tests fehlen."),
            Events.evaluationStart(1), Events.evaluationEnd("satisfied", "Alles erfüllt.", 1),
            Events.message("GEÄNDERT: Parser.kt"), Events.usage(137, 12_000, 3_400)).forEach { assertTrue(fold.accept(it)) }
        assertEquals(listOf("Agent denkt nach", "Befehl: ./gradlew test", "Ändert /workspace/device-ai/Parser.kt",
            "Liest /workspace/device-ai/README.md",
            "Störung, Anthropic versucht es erneut: " + CodingAgentFailure.message(CodingAgentFailure.SESSION_MODEL),
            "Sitzung wird nach einer Störung neu eingeplant", "Ergebnisprüfung, Runde 1", "Ergebnisprüfung, Runde 1: Nacharbeit nötig",
            "Ergebnisprüfung, Runde 2", "Ergebnisprüfung, Runde 2: erfüllt", "Agent: GEÄNDERT: Parser.kt"), fold.progress)
        assertTrue(fold.outcomeDefined)
        assertEquals(listOf("/workspace/device-ai/Parser.kt"), fold.changedFiles)
        assertEquals("satisfied", fold.outcomeResult)
        assertEquals(137L, fold.costCents)
        assertEquals(CodingAgentTokens(12_000, 3_400, 0, 0), fold.tokens)
        assertEquals(61L, fold.activeSeconds)
        // A retried error is no failure of the run.
        assertNull(fold.failure)
        assertFalse(fold.final)
    }

    @Test fun theSessionIsOnlyDoneOnADocumentedTerminalState() {
        val fold = fold()
        fold.accept(Events.running())
        // Waiting for a tool confirmation is idle, but not done (client patterns, § 5 Correct idle-break gate).
        fold.accept(Events.idle("requires_action", "sevt_x"))
        assertFalse(fold.final)
        fold.accept(Events.running())
        fold.accept(Events.idle("end_turn"))
        assertEquals("end_turn", fold.finalStop)
        fold.accept(Events.running())
        assertFalse(fold.final)
        fold.accept(Events.idle("budget_reached"))
        assertEquals("budget_reached", fold.finalStop)
        assertEquals("terminated", fold().apply { accept(Events.terminated()) }.finalStop)
        assertEquals("retries_exhausted", fold().apply { accept(Events.idle("retries_exhausted")) }.finalStop)
        // A stop reason this app does not know still ends the loop; the report then says so.
        assertEquals("neu_und_unbekannt", fold().apply { accept(Events.idle("neu_und_unbekannt")) }.finalStop)
        // An event type this app does not know changes nothing.
        val unknown = CodingAgentEvent.parse(mapOf("type" to "session.something_new", "id" to "sevt_new"))
        assertTrue(unknown is CodingAgentEvent.Other)
        assertTrue(fold().apply { accept(unknown) }.progress.isEmpty())
    }

    @Test fun anEventSeenTwiceCountsOnceButStillEndsTheLoop() {
        val fold = fold()
        val message = Events.message("einmal")
        val idle = Events.idle("end_turn")
        assertTrue(fold.accept(message))
        assertFalse(fold.accept(message))
        assertTrue(fold.accept(idle))
        // The terminal event was already in the history: the tail must not wait for another one.
        assertFalse(fold.accept(idle))
        assertTrue(fold.final)
        assertEquals(2, fold.events)
        assertEquals(listOf("Agent: einmal"), fold.progress)
    }

    @Test fun pausedCallsWaitUntilTheirConfirmationAndDeniedOnesNeverAsk() {
        val fold = fold()
        fold.accept(Events.bash("./deploy.sh", permission = "ask", id = "sevt_ask"))
        fold.accept(Events.bash("rm -rf /", permission = "deny", id = "sevt_deny"))
        assertEquals(listOf("sevt_ask"), fold.pendingApprovals.map { it.id })
        assertEquals(listOf("Wartet auf Freigabe · Befehl: ./deploy.sh", "Von Anthropic abgelehnt · Befehl: rm -rf /"), fold.progress)
        fold.accept(Events.confirmation("sevt_ask", allow = true))
        assertTrue(fold.pendingApprovals.isEmpty())
    }

    @Test fun anExecutedCallThatLooksLikeARuleBreachIsNotedAndAFinalErrorIsAFailure() {
        val fold = fold()
        fold.accept(Events.bash("git push origin main"))
        assertEquals(1, fold.ruleNotes.size)
        assertTrue(fold.ruleNotes.single().startsWith("Ausgeführt, obwohl es wie ein Regelverstoß aussieht (Push nur auf den Arbeitszweig"))
        fold.accept(Events.error("repository_authentication_error", "terminal"))
        assertEquals(CodingAgentFailure.SESSION_REPOSITORY_AUTH, fold.failure)
        // The upstream text of the error never becomes part of what the owner sees.
        assertFalse(fold.progress.joinToString().contains("upstream text"))
    }

    @Test fun secretsInWhatTheAgentWritesNeverReachTheStateOfARun() {
        val fold = fold()
        fold.accept(Events.message("Token ist $FAKE_GITHUB_TOKEN und sk-ant-api03-abcdefghijklmnop"))
        fold.accept(Events.bash("curl -H \"Authorization: Bearer ghp_abcdefghijklmnopqrstuvwxyz0123456789\" https://api.github.com/user"))
        fold.accept(Events.evaluationEnd("failed", "enthält $FAKE_GITHUB_TOKEN"))
        val everything = fold.summary + fold.progress + fold.outcomeExplanation
        assertFalse(everything.contains(FAKE_GITHUB_TOKEN))
        assertFalse(everything.contains("sk-ant-api03"))
        assertFalse(everything.contains("ghp_abcdef"))
        assertTrue(everything.contains(CodingAgentRedactor.REMOVED))
        // The placeholder variable itself is no secret and stays readable.
        assertEquals("Authorization: Bearer \$GITHUB_TOKEN", CodingAgentRedactor().clean("Authorization: Bearer \$GITHUB_TOKEN"))
    }

    // ── Failures ────────────────────────────────────────────────────────────────────────────────────────────────

    @Test fun everyFailureCodeHasAGermanOwnerMessageAndCarriesNothingElse() {
        val codes = CodingAgentFailure::class.java.declaredFields.filter { it.type == String::class.java }
            .mapNotNull { field -> field.isAccessible = true; field.get(null) as? String }
            .filter { it.startsWith("AGENT_") || it.startsWith("GITHUB_") }
        assertTrue("codes found: ${codes.size}", codes.size >= 45)
        codes.forEach { code ->
            assertTrue(code, CodingAgentFailure.known(code))
            val message = CodingAgentFailure.message(code)
            assertTrue(code, message.length > 30 && !message.contains("AGENT_") && !message.contains("Exception"))
        }
        assertEquals("Der Coding-Agent meldet einen unbekannten Fehler.", CodingAgentFailure.message("irgendwas"))
        val failure = CodingAgentException(CodingAgentFailure.INVALID_API_KEY, IllegalStateException("x-api-key: $FAKE_API_KEY"))
        assertEquals("CodingAgentException(AGENT_INVALID_API_KEY)", failure.toString())
        assertEquals(CodingAgentFailure.INVALID_API_KEY, failure.message)
        assertNull(failure.cause)
        assertEquals("IllegalStateException", failure.origin)
        // The documented statuses: 401, 403, 429, 5xx, beta not enabled, vault, rejected GitHub token.
        listOf(CodingAgentFailure.INVALID_API_KEY to "abgelehnt", CodingAgentFailure.PERMISSION_DENIED to "Berechtigung",
            CodingAgentFailure.RATE_LIMITED to "nicht von selbst noch einmal", CodingAgentFailure.OVERLOADED to "nicht von selbst noch einmal",
            CodingAgentFailure.BETA_UNAVAILABLE to "Beta", CodingAgentFailure.VAULT to "Tresor",
            CodingAgentFailure.SESSION_REPOSITORY_AUTH to "Token", CodingAgentFailure.GITHUB_TOKEN_REJECTED to "Token")
            .forEach { (code, word) -> assertTrue(code, CodingAgentFailure.message(code).contains(word)) }
    }

    // ── Report ──────────────────────────────────────────────────────────────────────────────────────────────────

    private val draft = CodingAgentGitFacts(9, "f00dfeedf00dfeed", 2, 3, 912, "https://github.com/Ullmann27/device-ai/pull/912", true, "open",
        false, "a1b2c3d4e5f60718")

    @Test fun theReportSaysWhatTheAppReadItselfAndMarksTheAgentsOwnWords() {
        val report = CodingAgentReport.compose(run(facts = draft).copy(costCents = 137, activeSeconds = 420, outcomeResult = "satisfied",
            outcomeExplanation = "Alle Kriterien erfüllt.", summary = "GEPRÜFT: ./gradlew test – grün", changedFiles = listOf("Parser.kt")), 0)
        listOf("Coding-Agent fertig: Der Entwurfs-Pull-Request liegt vor.", "Zweig continuum-agent/run0001: existiert (Stand f00dfee, 2 Commits vor main, 3 geänderte Dateien).",
            "Pull Request #912: Entwurf, Zustand open · https://github.com/Ullmann27/device-ai/pull/912",
            "main: unverändert seit dem Start (a1b2c3d).", "Ergebnisprüfung bei Anthropic (nicht von dieser App): erfüllt – Alle Kriterien erfüllt.",
            "Kosten: 1,37 $ laut Anthropic (Listenpreis) von höchstens 2,00 $", "Dauer: 12 Minuten 34 Sekunden (davon 420 Sekunden aktive Sitzung laut Anthropic)",
            "Bericht des Agenten (seine eigenen Angaben, von der App nicht geprüft):\nGEPRÜFT: ./gradlew test – grün",
            "Der Verlauf dieser Sitzung liegt bei Anthropic, bis du ihn löschst.").forEach { assertTrue("missing: $it\n$report", report.contains(it)) }
    }

    @Test fun whatDoesNotExistOrWasNotReportedIsCalledThat() {
        val nothing = CodingAgentReport.compose(run(facts = CodingAgentGitFacts(9, baseShaNow = "a1b2c3d4e5f60718")), 0)
        assertTrue(nothing, nothing.contains("fertig, aber ohne Ergebnis auf GitHub: weder Zweig noch Pull Request."))
        assertTrue(nothing, nothing.contains("Zweig continuum-agent/run0001: existiert nicht auf GitHub."))
        assertTrue(nothing, nothing.contains("Kosten: von Anthropic noch nicht gemeldet (Obergrenze 2,00 $)"))
        assertTrue(nothing, nothing.contains("Der Agent hat keinen Bericht geschrieben."))

        val branchOnly = CodingAgentReport.compose(run(facts = draft.copy(pullNumber = null, pullUrl = null, pullDraft = null, pullState = null)), 0)
        assertTrue(branchOnly, branchOnly.contains("fertig, aber ohne Pull Request: Es gibt nur den Zweig."))
        assertTrue(branchOnly, branchOnly.contains("https://github.com/Ullmann27/device-ai/compare/main...continuum-agent/run0001?expand=1"))

        val unchecked = CodingAgentReport.compose(run(facts = CodingAgentGitFacts(9, problem = CodingAgentFailure.GITHUB_OFFLINE)), 0)
        assertTrue(unchecked, unchecked.contains("Ob ein Pull Request existiert, konnte ich nicht prüfen."))
        assertTrue(unchecked, unchecked.contains("Zweig und Pull Request sind deshalb ungeprüft."))
    }

    @Test fun brokenRulesAndEveryOtherEndingAreSaidPlainly() {
        val merged = CodingAgentReport.compose(run(facts = draft.copy(pullMerged = true, pullDraft = false, baseShaNow = "9999999999")).copy(
            ruleNotes = listOf("Ausgeführt, obwohl es wie ein Regelverstoß aussieht (nichts zusammenführen): Befehl: gh pr merge 912")), 0)
        assertTrue(merged, merged.contains("ist ZUSAMMENGEFÜHRT – das war nicht erlaubt"))
        assertTrue(merged, merged.contains("main: hat sich seit dem Start geändert (a1b2c3d → 9999999)"))
        assertTrue(merged, merged.contains("Auffälligkeiten:\n• Ausgeführt, obwohl es wie ein Regelverstoß aussieht"))

        assertTrue(CodingAgentReport.headline(run(CodingAgentRunStatus.BUDGET_REACHED)).contains("Das Budget von 2,00 $ ist erreicht"))
        assertEquals("Coding-Agent gestoppt: Die Laufzeit von 30 Minuten war erreicht.",
            CodingAgentReport.headline(run(CodingAgentRunStatus.STOPPED).copy(stopCause = CodingAgentStopCause.TIME_LIMIT)))
        assertEquals("Coding-Agent gestoppt.", CodingAgentReport.headline(run(CodingAgentRunStatus.STOPPED).copy(stopCause = CodingAgentStopCause.OWNER)))
        assertEquals("Coding-Agent fehlgeschlagen: " + CodingAgentFailure.message(CodingAgentFailure.SESSION_REPOSITORY_AUTH),
            CodingAgentReport.headline(run(CodingAgentRunStatus.FAILED).copy(failure = CodingAgentFailure.SESSION_REPOSITORY_AUTH)))
        assertTrue(CodingAgentReport.headline(run(CodingAgentRunStatus.NOT_STARTED).copy(failure = CodingAgentFailure.NO_CREDIT))
            .startsWith("Coding-Agent nicht gestartet: Das API-Guthaben"))
        assertTrue(CodingAgentReport.compose(run().copy(historyDeleted = true), 0).endsWith("Der Verlauf dieser Sitzung ist bei Anthropic gelöscht."))
        assertEquals("Ullmann27/device-ai · Coding-Auftrag · abgeschlossen · 1,37 $", CodingAgentReport.listLine(run().copy(costCents = 137)))
    }

    // ── Owner texts ─────────────────────────────────────────────────────────────────────────────────────────────

    @Test fun theConsentSaysWhereItRunsWhatLeavesTheDeviceWhatItCostsAndHowItIsControlled() {
        val text = CodingAgentTexts.CONSENT
        listOf("Claude Managed Agents", "Beta", "nicht auf deinem Telefon", "dein Auftragstext", "dein GitHub-Token", "Tresor (Vault)",
            "Inhalt des gewählten Repositories", "bleibt bei Anthropic gespeichert, bis du ihn", "Token des Modells und Laufzeit der Sitzung",
            "festes Budget in Dollar", "Monatslimit", "Nichts startet ohne dein Ja", "Freigabeliste", "continuum-agent/", "Entwurfs-Pull-Request",
            "führt nichts", "kein Werkzeug in dein Telefon", "keine fremden Hosts").forEach { assertTrue("consent misses: $it", text.contains(it)) }
        val help = CodingAgentTexts.TOKEN_HELP
        listOf("Fine-grained tokens", "Only select repositories", "Contents „Read and write“", "Pull requests „Read and write“",
            "NICHT vergeben: Administration, Secrets, Workflows", "verschlüsselt", "nie wieder angezeigt").forEach { assertTrue(it, help.contains(it)) }
    }

    @Test fun theOneConfirmationNamesEverythingTheOwnerDecides() {
        val text = CodingAgentTexts.confirmation(run(CodingAgentRunStatus.PREPARING), onlyAllowed = true)
        listOf("Repository: Ullmann27/device-ai (du hast keines genannt; es ist dein einziges freigegebenes)",
            "Ausgangszweig: main (Stand a1b2c3d, von GitHub gelesen)", "Auftragsart: Coding-Auftrag",
            "Auftrag (dein Wortlaut): „behebe den Fehler im Parser“", "Budget: höchstens 2,00 $", "Laufzeit höchstens 30 Minuten",
            "Modell: claude-opus-5-5", "arbeitet auf dem neuen Zweig continuum-agent/run0001, öffnet einen Entwurfs-PR und führt nichts zusammen",
            "An Anthropic gehen dafür", "Antworte „Ja“. Alles andere startet nichts.").forEach { assertTrue("confirmation misses: $it\n$text", text.contains(it)) }
        assertTrue(CodingAgentTexts.confirmation(run().copy(kind = CodingAgentTaskKind.SECURITY), false)
            .contains("kein fremder Host, kein laufendes System"))
        assertTrue(CodingAgentTexts.SECURITY_NOTE.contains("Kein Scan fremder Hosts, kein Angriff auf laufende Systeme"))
    }
}
