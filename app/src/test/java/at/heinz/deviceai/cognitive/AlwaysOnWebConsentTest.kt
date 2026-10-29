package at.heinz.deviceai.cognitive

import at.heinz.deviceai.ApprovedWebResearchToolRunner
import at.heinz.deviceai.FileToolExecutionReceiptStore
import at.heinz.deviceai.ToolExecutionReceipt
import at.heinz.deviceai.ToolExecutionReceiptStore
import at.heinz.deviceai.ToolExecutionStatus
import at.heinz.deviceai.ToolGate
import at.heinz.deviceai.ToolPolicy
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class AlwaysOnWebConsentTest {
    @Test fun alwaysOnIssuesExactOneShotSessionsWithoutAnAskTicket() {
        val consent = InternetConsentPolicy(InternetAccessMode.ALWAYS_ON)
        val gate = ApprovedWebToolGate(consent)
        val call = WebToolRequest.Search("solar energy")
        assertNull(consent.requestTicket(call.question))
        assertFalse(consent.approve(call.question))
        assertNull(gate.prepare(call))
        val session = gate.authorizeAlwaysOn(call, ResearchMode.QUICK)!!
        var executions = 0
        assertThrows(IllegalStateException::class.java) {
            session.execute(WebToolRequest.Search("another question")) { executions++ }
        }
        assertEquals("evidence", session.execute(call) { executions++; "evidence" })
        assertThrows(IllegalStateException::class.java) { session.execute(call) { executions++ } }
        assertEquals(1, executions)
    }

    @Test fun askAndOffNeverIssueAutomaticSessions() {
        for (mode in listOf(InternetAccessMode.OFF, InternetAccessMode.ASK)) {
            val consent = InternetConsentPolicy(mode)
            val gate = ApprovedWebToolGate(consent)
            assertNull(mode.name, gate.authorizeAlwaysOn(WebToolRequest.Search("topic"), ResearchMode.QUICK))
            if (mode == InternetAccessMode.ASK) {
                val call = WebToolRequest.Search("topic")
                val approval = gate.prepare(call)!!
                val session = gate.approve(approval, ResearchMode.QUICK)!!
                assertEquals("approved", session.execute(call) { "approved" })
            }
        }
    }

    @Test fun alwaysOnPreservesQueryAndPublicHttpsValidation() {
        val gate = ApprovedWebToolGate(InternetConsentPolicy(InternetAccessMode.ALWAYS_ON))
        for (call in listOf(WebToolRequest.Search(" "), WebToolRequest.Search("x".repeat(513)),
            WebToolRequest.Read("http://www.nasa.gov/"), WebToolRequest.Read("https://127.0.0.1/"),
            WebToolRequest.Read("https://host.internal/path"), WebToolRequest.Read("https://user@www.nasa.gov/"),
            WebToolRequest.Read("https://www.nasa.gov:8443/"))) {
            assertNull(call.toString(), gate.authorizeAlwaysOn(call, ResearchMode.QUICK))
        }
        val call = WebToolRequest.Read("https://www.nasa.gov/solar")
        val session = gate.authorizeAlwaysOn(call, ResearchMode.QUICK)!!
        val provider = session.provider(object : ResearchProvider {
            override fun retrieve(request: ResearchRequest) = ResearchResult(emptyList(), emptyList())
        })
        session.execute(call) {
            assertThrows(IllegalArgumentException::class.java) {
                provider.retrieve(ResearchRequest("topic", "https://www.nasa.gov/other"))
            }
            provider.retrieve(ResearchRequest("topic", call.url))
        }
    }

    @Test fun everyModeChangeRevokesExistingAndPendingSessions() {
        val consent = InternetConsentPolicy()
        val gate = ApprovedWebToolGate(consent)
        val call = WebToolRequest.Search("topic")
        val pending = gate.prepare(call)!!
        consent.setMode(InternetAccessMode.ALWAYS_ON)
        assertNull(gate.approve(pending, ResearchMode.QUICK))
        for (nextMode in InternetAccessMode.entries) {
            consent.setMode(InternetAccessMode.ALWAYS_ON)
            val session = gate.authorizeAlwaysOn(call, ResearchMode.QUICK)!!
            consent.setMode(nextMode)
            consent.setMode(InternetAccessMode.ALWAYS_ON)
            assertFalse(session.isAuthorized())
            assertThrows(IllegalStateException::class.java) { session.execute(call) { error("revoked") } }
        }
    }

    @Test fun completedEvidenceRemainsUsableOnlyUntilConsentIsRevoked() {
        var time = 0L
        val consent = InternetConsentPolicy(InternetAccessMode.ALWAYS_ON, nowMillis = { time })
        val gate = ApprovedWebToolGate(consent)
        val call = WebToolRequest.Search("topic")
        val session = gate.authorizeAlwaysOn(call, ResearchMode.QUICK)!!
        assertEquals("partial evidence", session.execute(call) {
            time = ResearchBudgetPolicy.forMode(ResearchMode.QUICK).totalDeadlineMillis + 1
            assertThrows(IllegalStateException::class.java) { session.checkActive() }
            "partial evidence"
        })
        session.checkAuthorized()
        assertTrue(session.isAuthorized())
        gate.cancel()
        assertFalse(session.isAuthorized())
        assertThrows(IllegalStateException::class.java) { session.checkAuthorized() }
    }

    @Test fun laterRequestsRetainEarlierEvidenceButExplicitRevocationInvalidatesAll() {
        for (mode in listOf(InternetAccessMode.ASK, InternetAccessMode.ALWAYS_ON)) {
            for (revoke in listOf<(InternetConsentPolicy, ApprovedWebToolGate) -> Unit>(
                { consent, _ -> consent.setMode(InternetAccessMode.OFF) },
                { consent, _ -> consent.setMode(InternetAccessMode.ASK) },
                { _, gate -> gate.cancel() })) {
                val consent = InternetConsentPolicy(mode)
                val gate = ApprovedWebToolGate(consent)
                val sessions = listOf("first source", "second source").map { question ->
                    val call = WebToolRequest.Search(question)
                    val session = if (mode == InternetAccessMode.ALWAYS_ON)
                        gate.authorizeAlwaysOn(call, ResearchMode.QUICK)!!
                    else gate.approve(gate.prepare(call)!!, ResearchMode.QUICK)!!
                    session.execute(call) { "evidence for $question" }
                    session
                }
                assertTrue("Second $mode request must retain the first source", sessions.all { it.isAuthorized() })
                revoke(consent, gate)
                assertTrue("Explicit revocation invalidates all $mode results", sessions.none { it.isAuthorized() })
            }
        }
    }

    @Test fun switchingOffWhileReadIsInFlightDiscardsItsResult() {
        val consent = InternetConsentPolicy(InternetAccessMode.ALWAYS_ON)
        val gate = ApprovedWebToolGate(consent)
        val call = WebToolRequest.Read("https://www.nasa.gov/solar")
        val session = gate.authorizeAlwaysOn(call, ResearchMode.QUICK)!!
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val provider = session.provider(object : ResearchProvider {
            override fun retrieve(request: ResearchRequest): ResearchResult {
                entered.countDown()
                check(release.await(5, TimeUnit.SECONDS))
                return ResearchResult(emptyList(), emptyList())
            }
        })
        val executor = Executors.newSingleThreadExecutor()
        try {
            val result = executor.submit<Boolean> {
                runCatching { session.execute(call) { provider.retrieve(ResearchRequest("topic", call.url)) } }.isSuccess
            }
            assertTrue(entered.await(5, TimeUnit.SECONDS))
            consent.setMode(InternetAccessMode.OFF)
            release.countDown()
            assertFalse("Revoked source output must be discarded", result.get(5, TimeUnit.SECONDS))
        } finally {
            release.countDown()
            executor.shutdownNow()
        }
    }

    @Test fun alwaysOnStillUsesTheCommonToolGateAndDoesNotPermitReplay() {
        val consent = InternetConsentPolicy(InternetAccessMode.ALWAYS_ON)
        val gate = ApprovedWebToolGate(consent)
        val call = WebToolRequest.Search("solar energy")
        val session = gate.authorizeAlwaysOn(call, ResearchMode.QUICK)!!
        val runner = runner(session)
        val tools = ToolGate(ToolPolicy(timeoutMillis = 20_000), runner)
        assertEquals(ToolExecutionStatus.REJECTED,
            tools.execute(call.proposal().invocation("without-consent")).record.status)
        val result = tools.execute(call.proposal().invocation("persistent-owner-consent"), userConfirmed = true)
        assertEquals(result.record.decisionReason, ToolExecutionStatus.SUCCEEDED, result.record.status)
        assertTrue(result.output!!.contains("https://www.nasa.gov/solar"))
        assertNotNull(runner.dossier)
        assertEquals(ToolExecutionStatus.FAILED,
            ToolGate(ToolPolicy(timeoutMillis = 20_000), runner)
                .execute(call.proposal().invocation("replay"), userConfirmed = true).record.status)
        consent.setMode(InternetAccessMode.OFF)
        assertNull("Revoked dossier must not be published or learned", runner.dossier)
    }

    @Test fun cancellationDuringSynthesisNeverPublishesDossier() {
        val consent = InternetConsentPolicy(InternetAccessMode.ALWAYS_ON)
        val gate = ApprovedWebToolGate(consent)
        val call = WebToolRequest.Search("solar energy")
        val session = gate.authorizeAlwaysOn(call, ResearchMode.QUICK)!!
        val runner = runner(session, ResearchSynthesizer {
            consent.setMode(InternetAccessMode.OFF)
            ResearchSynthesisOutput("Revoked evidence must not escape")
        })
        val result = ToolGate(ToolPolicy(timeoutMillis = 20_000), runner)
            .execute(call.proposal().invocation("revoked-synthesis"), userConfirmed = true)
        assertEquals(ToolExecutionStatus.FAILED, result.record.status)
        assertNull(result.output)
        assertNull(runner.dossier)
    }

    @Test fun modelChatResearchRequiresDurableSuccessReceiptBeforeReviewPublication() {
        val directory = java.nio.file.Files.createTempDirectory("continuum-web-review-").toFile()
        try {
            val consent = InternetConsentPolicy(InternetAccessMode.ALWAYS_ON)
            val call = WebToolRequest.Search("solar energy")
            val session = ApprovedWebToolGate(consent).authorizeAlwaysOn(call, ResearchMode.QUICK)!!
            val runner = runner(session)
            val receipts = FileToolExecutionReceiptStore(java.io.File(directory, "receipts"))
            var stored = 0
            val review = ResearchDossierRepository { stored++ }

            assertEquals(0, runner.publishForReview(receipts, review))
            val execution = ToolGate(ToolPolicy(timeoutMillis = 20_000), runner, receiptStore = receipts)
                .execute(call.proposal().invocation("web-publish-confirmed"), userConfirmed = true)
            assertEquals(execution.record.decisionReason,
                ToolExecutionStatus.SUCCEEDED, execution.record.status)

            val tampered = object : ToolExecutionReceiptStore by receipts {
                override fun get(callId: String): ToolExecutionReceipt? =
                    receipts.get(callId)?.copy(outputSha256 = "0".repeat(64))
            }
            assertEquals("Unmatched output must not be published", 0,
                runner.publishForReview(tampered, review))
            assertEquals(0, stored)
            assertTrue("Real source candidates must reach the existing review-only inbox",
                runner.publishForReview(receipts, review) > 0)
            assertEquals(1, stored)
            consent.setMode(InternetAccessMode.OFF)
            assertEquals("Consent revocation must reject subsequent publication", 0,
                runner.publishForReview(receipts, review))
            assertEquals(1, stored)
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test fun rejectedWebToolCannotPublishResearchToLearningInbox() {
        val directory = java.nio.file.Files.createTempDirectory("continuum-web-rejected-").toFile()
        try {
            val consent = InternetConsentPolicy(InternetAccessMode.ALWAYS_ON)
            val call = WebToolRequest.Search("solar energy")
            val session = ApprovedWebToolGate(consent).authorizeAlwaysOn(call, ResearchMode.QUICK)!!
            val runner = runner(session)
            val receipts = FileToolExecutionReceiptStore(java.io.File(directory, "receipts"))
            var stored = 0
            val denied = ToolGate(ToolPolicy(timeoutMillis = 20_000), runner, receiptStore = receipts)
                .execute(call.proposal().invocation("web-publish-denied"), userConfirmed = false)
            assertEquals(ToolExecutionStatus.REJECTED, denied.record.status)
            assertEquals(0, runner.publishForReview(receipts, ResearchDossierRepository { stored++ }))
            assertEquals(0, stored)
        } finally {
            directory.deleteRecursively()
        }
    }

    private fun runner(session: ApprovedWebResearchSession,
        synthesizer: ResearchSynthesizer = DeterministicResearchSynthesizer()): ApprovedWebResearchToolRunner {
        val discovery = object : ResearchDiscoveryProvider {
            override val isGeneralSearchAvailable = true
            override fun discover(query: ResearchQuery) =
                listOf(ResearchSourceCandidate("https://www.nasa.gov/solar", "Solar energy"))
        }
        val provider = object : ResearchProvider {
            override fun retrieve(request: ResearchRequest): ResearchResult {
                val text = "Solar panels convert sunlight into electricity. Solar energy is renewable."
                val document = ResearchDocument(request.url, "Solar energy", 1, sha256Hex(text), "text/plain",
                    SourceIdentity("www.nasa.gov", "www.nasa.gov"), text)
                return ResearchResult(listOf(document), listOf(document.citation()))
            }
        }
        return ApprovedWebResearchToolRunner(session, discovery, provider, engineFactory = { d, p ->
            DeepResearchEngine(discovery = d, provider = p, synthesizer = synthesizer,
                validateTarget = { HttpReadOnlyResearchProvider.ResearchUrlValidator.validateSyntax(it) })
        })
    }
}
