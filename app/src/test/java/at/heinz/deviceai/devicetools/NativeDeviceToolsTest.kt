package at.heinz.deviceai.devicetools

import android.content.ContextWrapper
import at.heinz.deviceai.DeviceToolExecutor
import at.heinz.deviceai.DeviceToolExecutorRunner
import at.heinz.deviceai.OwnerLibraryAccess
import at.heinz.deviceai.ToolCallRequest
import at.heinz.deviceai.ToolExecutionResult
import at.heinz.deviceai.ToolExecutionStatus
import at.heinz.deviceai.ToolGate
import at.heinz.deviceai.ToolInvocation
import at.heinz.deviceai.ToolPolicy
import at.heinz.deviceai.ToolRunner
import at.heinz.deviceai.cognitive.AgentToolApproval
import at.heinz.deviceai.cognitive.BoundedAgentOrchestrator
import at.heinz.deviceai.cognitive.ChainPlan
import at.heinz.deviceai.cognitive.InMemoryExperienceStore
import at.heinz.deviceai.cognitive.OwnerChainPlanCodec
import at.heinz.deviceai.cognitive.OwnerChainPlanValidator
import at.heinz.deviceai.cognitive.OwnerTaskChain
import at.heinz.deviceai.cognitive.OwnerTaskChainRecipes
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger

/**
 * The seam the app really uses – registry → ToolGate (with the device precheck) → DeviceToolExecutorRunner →
 * DeviceToolExecutor → in-process tools – on a temporary storage. Shizuku is a recorder that must stay untouched
 * whenever Android's own access suffices.
 */
class NativeDeviceToolsTest {
    private val storage = TestStorage()
    private val handover = TestHandover()
    private val clipboard = TestClipboard()
    private val port = TestDocumentPort(storage)
    private val pdf = TestPdf(listOf("Versuch A: Argon bei 4 bar.", "Versuch B."))
    private val apps = listOf(DeviceApp("Experimente Labor", "at.example.experimente"), DeviceApp("Kamera", "com.sec.android.app.camera"),
        DeviceApp("WhatsApp", "com.whatsapp"))
    private val native = testTools(storage, apps, pdf, port, handover, clipboard)
    private val shell = CopyOnWriteArrayList<String>()
    private var bridgeReady = false
    private val executor = DeviceToolExecutor(ContextWrapper(null), { bridgeReady }, { command, callback ->
        shell += command
        callback(Result.success("/sdcard/Download/aus-der-shell.txt"))
    }, null, null, native)
    private val settings = MemoryDeviceToolSettings()
    private var cloud = false
    private val access = DeviceToolAccess(settings, { cloud }, { bridgeReady }, { native })
    private var calls = 0

    @After fun clean() = storage.delete()

    private fun tree() {
        storage.file("Download/Experimente.pdf", ByteArray(2_400_000), "2026-11-17T14:02")
        storage.file("Download/Rechnung November.pdf", ByteArray(220_000), "2026-11-16T10:30")
        storage.file("Download/continuum-0.3.70.apk", ByteArray(5_000), "2026-11-18T21:00")
        storage.file("Download/continuum-0.3.69.apk", ByteArray(3_000), "2026-11-17T20:00")
        storage.text("Download/einkaufsliste.txt", "Milch\nBrot\nKaffee", "2026-11-19T07:10")
        storage.file("Documents/Labor/Argon4.pdf", ByteArray(700), "2026-11-12T10:30")
    }

    private fun gate(owner: String, modelTurn: Boolean = true): ToolGate {
        val turn = access.turn(owner, modelTurn)
        return ToolGate(ToolPolicy(readOnlyEnabled = true, destructiveEnabled = true, maxCalls = 16),
            ToolRunner { request -> turn.run(request) { DeviceToolExecutorRunner(executor).execute(request) } }, precheck = turn)
    }

    private fun call(gate: ToolGate, tool: String, vararg arguments: Pair<String, Any?>, confirmed: Boolean = false): ToolExecutionResult {
        val request = ToolCallRequest("native-${calls++}", tool, mapOf(*arguments))
        return gate.execute(ToolInvocation(request.callId, request), userConfirmed = confirmed, destructiveConfirmed = confirmed)
    }

    @Test fun theOwnersFieldCaseSearchReadCreateShowWorksWithoutShizuku() {
        tree()
        val gate = gate("ich suche eine PDF die heißt Experimente, lies sie, erstelle eine PDF daraus und zeig sie mir dann")
        val search = call(gate, "search_device", "query" to "Experimente pdf")
        assertEquals(ToolExecutionStatus.SUCCEEDED, search.record.status)
        assertEquals("Gerätesuche „Experimente pdf“ – 1 Treffer:\n1. [DATEI] /sdcard/Download/Experimente.pdf · PDF · 2,3 MB · geändert 17.11.2026\n" +
            "Weiter: read_file liest eine Datei, open_file zeigt sie an.", search.output)
        val read = call(gate, "read_file", "path" to "/sdcard/Download/Experimente.pdf", "pages" to "1")
        assertTrue(read.output, read.output!!.contains("Inhalt (Seite 1 von 2):\n--- Seite 1 ---\nVersuch A: Argon bei 4 bar."))
        // Creating changes the storage: without the owner's yes nothing is written.
        val refused = call(gate, "create_document", "title" to "Experimente kurz", "content" to "# Ergebnis\n- Argon bei 4 bar")
        assertEquals(ToolExecutionStatus.REJECTED, refused.record.status)
        assertTrue(port.saved.isEmpty())
        val created = call(gate, "create_document", "title" to "Experimente kurz", "content" to "# Ergebnis\n- Argon bei 4 bar", confirmed = true)
        assertTrue(created.output, created.output!!.startsWith("Gespeichert: /sdcard/Download/Experimente kurz 19.11.2026.pdf\n"))
        assertTrue(File(storage.dir, "Download/Experimente kurz 19.11.2026.pdf").isFile)
        val opened = call(gate, "open_file", "path" to "/sdcard/Download/Experimente kurz 19.11.2026.pdf")
        assertTrue(opened.output, opened.output!!.startsWith("Geöffnet: /sdcard/Download/Experimente kurz 19.11.2026.pdf"))
        // The viewer got the very file with the reference Android gave when it was saved.
        assertEquals("Experimente kurz 19.11.2026.pdf", handover.viewed.single().first.name)
        assertEquals(port.saved.single().uri, handover.viewed.single().second!!.uri)
        assertEquals("nothing of this needs the bridge", emptyList<String>(), shell.toList())
        // One file per owner turn: a second viewer does not pop up.
        assertEquals("OPEN_LIMIT", call(gate, "open_file", "path" to "/sdcard/Download/Experimente.pdf").refusal!!.code)
    }

    @Test fun existingFileToolsAnswerInProcessAndFallBackToTheBridgeOnlyWithoutTheGrant() {
        tree()
        val gate = gate("Zeig mir den Download-Ordner")
        assertTrue(call(gate, "list_files", "path" to "/sdcard/Download").output!!.contains(" Experimente.pdf"))
        assertEquals("/sdcard/Documents/Labor/Argon4.pdf", call(gate, "find_files", "query" to "argon vier").output)
        assertEquals("/sdcard/Download/continuum-0.3.70.apk\n/sdcard/Download/continuum-0.3.69.apk", call(gate, "find_latest_apk").output)
        assertTrue(call(gate, "get_large_files", "limit" to 3).output!!.lines().first().endsWith("\t/sdcard/Download"))
        assertTrue(shell.isEmpty())
        // Without „Zugriff auf alle Dateien“: with a running bridge the old shell tool answers, without it the call is
        // refused before dispatch – and never reaches the shell.
        storage.granted = OwnerLibraryAccess.MISSING
        val missing = call(gate, "find_files", "query" to "argon")
        assertEquals(ToolExecutionStatus.REJECTED, missing.record.status)
        assertEquals("ALL_FILES_ACCESS_MISSING", missing.refusal!!.code)
        assertTrue(shell.isEmpty())
        bridgeReady = true
        assertEquals("/sdcard/Download/aus-der-shell.txt", call(gate, "find_files", "query" to "argon").output)
        assertTrue(shell.single(), shell.single().startsWith("find "))
        // The search still finds apps and says plainly that files were not looked at.
        val apps = call(gate, "search_device", "query" to "kamera").output!!
        assertTrue(apps, apps.contains("1. [APP] Kamera · com.sec.android.app.camera") && apps.contains("Dateien habe ich nicht durchsucht"))
    }

    @Test fun movingAndDeletingNeedTheOwnersYesAndImpossibleChangesAreSaidBeforeHeIsAsked() {
        tree()
        val gate = gate("Verschieb die Rechnung nach Dokumente und lösch die alte APK")
        assertEquals(ToolExecutionStatus.REJECTED, call(gate, "delete_file", "path" to "/sdcard/Download/continuum-0.3.69.apk").record.status)
        assertTrue(File(storage.dir, "Download/continuum-0.3.69.apk").exists())
        val moved = call(gate, "move_file", "source" to "/sdcard/Download/Rechnung November.pdf", "destination" to "/sdcard/Documents", confirmed = true)
        assertTrue(moved.output, moved.output!!.startsWith("Verschoben."))
        assertTrue(File(storage.dir, "Documents/Rechnung November.pdf").isFile)
        assertTrue(call(gate, "delete_file", "path" to "/sdcard/Download/continuum-0.3.69.apk", confirmed = true).output!!.startsWith("Gelöscht."))
        assertFalse(File(storage.dir, "Download/continuum-0.3.69.apk").exists())
        val gone = call(gate, "delete_file", "path" to "/sdcard/Download/continuum-0.3.69.apk", confirmed = true)
        assertEquals("FILE_CHANGE_NOT_POSSIBLE", gone.refusal!!.code)
        assertFalse(gone.record.outcomeUncertain)
        assertTrue(shell.isEmpty())
    }

    @Test fun searchResultsAreBoundedForTheBrainThatReadsThem() {
        repeat(80) { index -> storage.file("Download/bericht-%02d.pdf".format(index), ByteArray(10), "2026-11-%02dT10:00".format(1 + index % 28)) }
        val local = call(gate("such berichte"), "search_device", "query" to "bericht").output!!
        assertTrue(local, local.startsWith("Gerätesuche „bericht“ – 80 Treffer:"))
        assertTrue("on-device result: ${local.length}", local.length <= NativeDeviceTools.SMALL_RESULT)
        assertTrue(local, local.contains("weitere Treffer nicht gezeigt"))
        // Cloud brain with the owner's consent: the longer result, still far below the gate's limit.
        cloud = true
        settings.cloudNames = true
        native.largeContext = { cloud }
        val large = call(gate("such berichte"), "search_device", "query" to "bericht").output!!
        assertTrue(large.lines().count { it.contains("[DATEI]") } == DeviceSearch.DEFAULT_LIMIT)
        assertTrue(large.length <= NativeDeviceTools.LARGE_RESULT)
        // A walk cut short says so; a stop ends it.
        val small = testTools(storage, limits = DeviceFileWalk.Limits(maxEntries = 10))
        assertTrue(small.execute(ToolCallRequest("c", "search_device", mapOf("query" to "bericht"))).contains("Hinweis: Dateisuche nach 10 Einträgen beendet"))
        Thread.currentThread().interrupt()
        try { native.execute(ToolCallRequest("c", "search_device", mapOf("query" to "bericht"))); org.junit.Assert.fail("stop ignored") }
        catch (_: java.util.concurrent.CancellationException) { } finally { Thread.interrupted() }
    }

    @Test fun clipboardShareAndViewerHandOverExactlyWhatWasApproved() {
        tree()
        val gate = gate("Teile die Rechnung und kopier den Text, dann zeig mir die APK")
        clipboard.content = "Geheim 123"
        assertEquals(ToolExecutionStatus.REJECTED, call(gate, "read_clipboard").record.status)
        assertEquals("Zwischenablage (10 Zeichen):\nGeheim 123", call(gate, "read_clipboard", confirmed = true).output)
        assertTrue(call(gate, "write_clipboard", "text" to "Neu\nzweizeilig", confirmed = true).output!!.startsWith("In die Zwischenablage gelegt: 14 Zeichen."))
        assertEquals("Neu\nzweizeilig", clipboard.content)
        clipboard.content = null
        assertTrue(call(gate, "read_clipboard", confirmed = true).output!!.startsWith("Zwischenablage: leer"))
        assertEquals(ToolExecutionStatus.REJECTED, call(gate, "share_file", "path" to "/sdcard/Download/Rechnung November.pdf").record.status)
        assertTrue(handover.shared.isEmpty())
        assertTrue(call(gate, "share_file", "path" to "/sdcard/Download/Rechnung November.pdf", confirmed = true).output!!.startsWith("Teilen-Dialog gestartet:"))
        assertEquals("Rechnung November.pdf", handover.shared.single().name)
        // An APK is never handed to a viewer (that would start Android's installer); a missing file is said.
        val apk = call(gate, "open_file", "path" to "/sdcard/Download/continuum-0.3.70.apk").output!!
        assertTrue(apk, apk.startsWith("Nicht angezeigt:") && apk.contains("Installation"))
        assertTrue(call(gate, "open_file", "path" to "/sdcard/Download/fehlt.pdf").output!!.startsWith("Nicht angezeigt: /sdcard/Download/fehlt.pdf gibt es nicht"))
        assertTrue(handover.viewed.isEmpty())
        handover.problem = "Ich habe keine App gefunden, die diesen Dateityp anzeigen kann."
        assertTrue(call(gate, "open_file", "path" to "/sdcard/Download/Experimente.pdf").output!!.startsWith("Nicht angezeigt: /sdcard/Download/Experimente.pdf – Ich habe keine App"))
        // With the cloud brain the clipboard is content: no consent, no read – not even with the owner's yes for the call.
        cloud = true
        assertEquals("CLOUD_CONTENT_CONSENT_MISSING", call(gate("lies die Zwischenablage"), "read_clipboard", confirmed = true).refusal!!.code)
    }

    private fun runChain(plan: ChainPlan, owner: String, approve: Boolean = true): Pair<OwnerTaskChain.Outcome, Int> {
        val chain = OwnerTaskChain(plan, owner)
        val asked = AtomicInteger()
        val orchestrator = BoundedAgentOrchestrator(model = chain, memories = { emptyList() }, toolGate = gate(owner, modelTurn = false),
            evaluator = chain, experienceStore = InMemoryExperienceStore(), evaluationConfirmsOutcome = false,
            approvalProvider = chain.approvals(access.approvals(owner) { _, _ -> asked.incrementAndGet(); AgentToolApproval(approve) }),
            limits = OwnerTaskChain.limits(plan), callIdPrefix = "native-chain-${calls++}", onToolStarted = { _, _ -> chain.toolStarted() })
        return chain.runWith(orchestrator).outcome to asked.get()
    }

    @Test fun theExistingRecipesCompleteOnTheInProcessBackendsWithoutShizuku() {
        tree()
        val newest = runChain(OwnerTaskChainRecipes.recognise("Such die neueste PDF in Downloads und sag mir, wie groß sie ist")!!, "…").first
        assertTrue(newest.report, newest is OwnerTaskChain.Outcome.Completed)
        assertTrue(newest.report, newest.report.contains("„Experimente.pdf“") && newest.report.contains("2.3M") && newest.report.contains("17.11.2026"))
        val apk = runChain(OwnerTaskChainRecipes.recognise("Finde die neueste APK und zeig mir die Infos")!!, "…").first
        assertTrue(apk.report, apk is OwnerTaskChain.Outcome.Completed && apk.report.contains("„continuum-0.3.70.apk“") && apk.report.contains("/sdcard/Download"))
        val search = runChain(OwnerTaskChainRecipes.recognise("Such die Datei einkaufsliste")!!, "…").first
        assertTrue(search.report, search is OwnerTaskChain.Outcome.Completed && search.report.contains("„einkaufsliste.txt“"))
        val nothing = runChain(OwnerTaskChainRecipes.recognise("Such die Datei gibtsnicht")!!, "…").first
        assertTrue(nothing.report, nothing is OwnerTaskChain.Outcome.Failed && nothing.report.contains("kein Treffer"))
        assertTrue(shell.isEmpty())
        // Without grant and bridge the chain stops at its first step with the plain reason – nothing is unknown.
        storage.granted = OwnerLibraryAccess.MISSING
        val refused = runChain(OwnerTaskChainRecipes.recognise("Finde die neueste APK und zeig mir die Infos")!!, "…").first
        assertTrue(refused.report, refused is OwnerTaskChain.Outcome.Failed && refused.report.contains("„Zugriff auf alle Dateien“") &&
            !refused.report.contains("UNKNOWN") && !refused.report.contains("Besitzerprüfung"))
    }

    @Test fun modelPlansWithTheNewToolsRunSearchReadAndCreateShow() {
        tree()
        fun plan(owner: String, steps: String) = (OwnerChainPlanCodec.parse(owner,
            "{\"schema\":\"continuum-owner-chain-v1\",\"goal\":\"$owner\",\"steps\":$steps}") as OwnerChainPlanValidator.Result.Valid).plan
        val owner = "Such die Datei Argon4 und lies sie"
        val read = runChain(plan(owner, "[{\"tool\":\"search_device\",\"args\":{\"query\":\"Argon4\"}},{\"tool\":\"read_file\",\"args\":{\"path\":\"{{1.path}}\"}}]"), owner).first
        assertTrue(read.report, read is OwnerTaskChain.Outcome.Completed && read.report.contains("/sdcard/Documents/Labor/Argon4.pdf") &&
            read.report.contains("Versuch A: Argon bei 4 bar."))
        val show = "Erstelle eine Einkaufsliste als PDF und zeig sie mir"
        val steps = "[{\"tool\":\"create_document\",\"args\":{\"title\":\"Einkauf\",\"content\":\"- Milch\\n- Brot\",\"format\":\"pdf\"}}," +
            "{\"tool\":\"open_file\",\"args\":{\"path\":\"{{1.path}}\"}}]"
        // Declined: nothing is written and nothing is shown.
        val (declined, asked) = runChain(plan(show, steps), show, approve = false)
        assertTrue(declined.report, declined is OwnerTaskChain.Outcome.Paused)
        assertEquals(1, asked)
        assertTrue(port.saved.isEmpty() && handover.viewed.isEmpty())
        val (done, askedAgain) = runChain(plan(show, steps), show)
        assertTrue(done.report, done is OwnerTaskChain.Outcome.Completed && done.report.contains("/sdcard/Download/Einkauf 19.11.2026.pdf"))
        assertEquals(1, askedAgain)
        assertEquals("Einkauf 19.11.2026.pdf", handover.viewed.single().first.name)
        // With the owner's switch on, the document he ordered in this sentence needs no dialog.
        settings.documentsWithoutAsking = true
        val (again, askedThird) = runChain(plan(show, steps), show, approve = false)
        assertTrue(again.report, again is OwnerTaskChain.Outcome.Completed && again.report.contains("Einkauf 19.11.2026 (2).pdf"))
        assertEquals(0, askedThird)
        assertNull(shell.firstOrNull())
    }
}
