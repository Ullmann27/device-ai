package at.heinz.deviceai

import at.heinz.deviceai.cockpit.CockpitAction
import at.heinz.deviceai.cockpit.StepState
import at.heinz.deviceai.cognitive.ApprovedWebResearchSession
import at.heinz.deviceai.cognitive.ApprovedWebToolGate
import at.heinz.deviceai.cognitive.InternetAccessMode
import at.heinz.deviceai.cognitive.InternetConsentPolicy
import at.heinz.deviceai.cognitive.NewsFeed
import at.heinz.deviceai.cognitive.NewsFeedCatalog
import at.heinz.deviceai.cognitive.NewsFeedResult
import at.heinz.deviceai.cognitive.NewsSourceRef
import at.heinz.deviceai.cognitive.NewsTestFeeds
import at.heinz.deviceai.cognitive.NonModelReplyKind
import at.heinz.deviceai.cognitive.OwnerDocumentLayout
import at.heinz.deviceai.cognitive.ResearchDialogueHandoff
import at.heinz.deviceai.cognitive.ResearchDocument
import at.heinz.deviceai.cognitive.ResearchMode
import at.heinz.deviceai.cognitive.ResearchProvider
import at.heinz.deviceai.cognitive.ResearchRequest
import at.heinz.deviceai.cognitive.ResearchResult
import at.heinz.deviceai.cognitive.SourceIdentity
import at.heinz.deviceai.cognitive.WebToolRequest
import at.heinz.deviceai.cognitive.sha256Hex
import java.net.URI

/**
 * Host of [OwnerWorkTools] for JVM tests. Everything that decides or words is the productive code: the consent
 * policy and web gate, the news tool runner behind the ToolGate, parser, selector and briefing. Only the screen, the
 * network bytes (synthetic feeds by URL) and the model text are stand-ins; jobs run inline.
 */
internal class OwnerWorkToolsHarness(
    mode: InternetAccessMode = InternetAccessMode.ALWAYS_ON,
    var ownerApproves: Boolean = true,
    var modelLoaded: Boolean = true,
    var model: (String) -> OwnerWorkGeneration = { OwnerWorkGeneration("", "kein Modelltext hinterlegt", false) }
) : OwnerWorkToolsHost {
    val consent = InternetConsentPolicy(mode)
    private val gate = ApprovedWebToolGate(consent)
    /** Feed bytes by URL; a missing URL is an unreachable host. */
    val served: MutableMap<String, String> = NewsFeedCatalog.feeds
        .mapNotNull { feed -> NewsTestFeeds.raw[feed.id]?.let { feed.url to it } }.toMap().toMutableMap()
    val log = mutableListOf<String>()
    val prompts = mutableListOf<String>()
    val fetched = mutableListOf<String>()
    val consents = mutableListOf<Pair<WebToolRequest, String?>>()
    val sources = mutableListOf<NewsSourceRef>()
    val digests = mutableListOf<ResearchDialogueHandoff.Digest>()
    val researched = mutableListOf<WebToolRequest>()
    var cancelDuringGeneration = false

    override var sessionId = "chat-1"
    override var ownerTurn = 0L
    override val assistantName: String? = "Jarvis"

    fun lines(prefix: String): List<String> = log.filter { it.startsWith(prefix) }.map { it.removePrefix(prefix) }
    val spoken: List<String> get() = lines("SAY:") + lines("REPLY:")

    override fun internetMode(): InternetAccessMode = consent.mode
    override fun acceptOwnerTurn(request: String) { ownerTurn++; log += "USER:$request" }
    override fun reply(request: String, text: String, kind: NonModelReplyKind) { log += "REPLY:$text" }
    override fun say(text: String) { log += "SAY:$text" }
    override fun activity(line: String) { log += "ACTIVITY:$line" }

    override fun confirmWeb(call: WebToolRequest, explanation: String?, notGranted: String?,
                            completed: (ApprovedWebResearchSession?) -> Unit) {
        consents += call to explanation
        val session = when (consent.mode) {
            InternetAccessMode.ALWAYS_ON -> gate.authorizeAlwaysOn(call, ResearchMode.STANDARD)
            InternetAccessMode.ASK -> gate.prepare(call)?.let { approval ->
                // The owner's tap in the one-time dialog.
                if (ownerApproves) gate.approve(approval, ResearchMode.STANDARD) else { gate.cancel(); null }
            }
            InternetAccessMode.OFF -> null
        }
        if (session == null) say(notGranted ?: "Recherche nicht ausgeführt: Es liegt keine aktuelle Freigabe für diese Anfrage vor.")
        completed(session)
    }

    override fun startResearch(session: ApprovedWebResearchSession) { researched += session.call }

    override fun <T> runWebJob(session: ApprovedWebResearchSession, request: String, work: (OwnerWorkWebJob) -> T,
                               done: (Result<T>) -> Unit) {
        var cancelled = false
        val job = object : OwnerWorkWebJob {
            override fun readFeeds(feeds: List<NewsFeed>): List<NewsFeedResult> {
                val provider = object : ResearchProvider {
                    override fun retrieve(request: ResearchRequest): ResearchResult {
                        synchronized(fetched) { fetched += request.url }
                        val host = URI(request.url).host
                        val body = served[request.url] ?: throw java.net.UnknownHostException(host)
                        val document = ResearchDocument(request.url, null, 0, sha256Hex(body), "application/xml",
                            SourceIdentity(host, host), body)
                        return ResearchResult(listOf(document), listOf(document.citation()))
                    }
                }
                val runner = ApprovedNewsFeedToolRunner(session, feeds, provider)
                val execution = ToolGate(ToolPolicy(timeoutMillis = 30_000), runner)
                    .execute(session.call.proposal().invocation("news-" + log.size), userConfirmed = true)
                check(execution.record.status == ToolExecutionStatus.SUCCEEDED) { execution.record.decisionReason }
                return checkNotNull(runner.results)
            }
            override fun generate(prompt: String): OwnerWorkGeneration {
                prompts += prompt
                if (cancelDuringGeneration) cancelled = true
                return model(prompt)
            }
            override fun step(key: String, label: String, state: StepState, action: CockpitAction?) {
                log += "STEP:$key:$label:${state.name}" + (action?.let { ":${it.name}" } ?: "")
            }
            override fun cancelled(): Boolean = cancelled || !session.isAuthorized()
        }
        val result = runCatching { work(job).also { session.checkAuthorized() } }
        // The Activity drops the result of a stopped job or a revoked grant.
        if (cancelled || !session.isAuthorized()) log += "ACTIVITY:NACHRICHTEN · abgebrochen oder Freigabe widerrufen · Ergebnis verworfen"
        else done(result)
    }

    override fun modelReady(): Boolean = modelLoaded
    override fun promptBudgetBytes(): Int = 6_000
    override fun showSources(sources: List<NewsSourceRef>) { this.sources += sources; log += "SOURCES:${sources.size}" }
    override fun rememberResearch(digest: ResearchDialogueHandoff.Digest) { digests += digest }

    // ── Download folder, viewer and mail app as in-memory stand-ins ─────────────────────────────────────────
    class Mail(val address: String?, val subject: String, val body: String, val attachment: OwnerSavedFile?)

    /** The owner's Download folder: display name → bytes. */
    val downloads = linkedMapOf<String, ByteArray>()
    val opened = mutableListOf<OwnerSavedFile>()
    val mails = mutableListOf<Mail>()
    var saveProblem: String? = null
    var openProblem: String? = null
    var mailProblem: String? = null
    /** false = an Android version without MediaStore downloads: saved in the app folder, no shareable URI. */
    var mediaStore = true
    /** An answer of another lane (the model) that the work tools did not produce. */
    fun foreignTurn(question: String, answer: String) { ownerTurn++; log += "USER:$question"; log += "SAY:$answer" }

    override fun lastAssistantReply(): String? = spokenInOrder().lastOrNull()
    private fun spokenInOrder(): List<String> = log.filter { it.startsWith("SAY:") || it.startsWith("REPLY:") }
        .map { it.substringAfter(':') }

    override fun <T> runFileJob(request: String, work: (OwnerWorkFileJob) -> T, done: (Result<T>) -> Unit) {
        val job = object : OwnerWorkFileJob {
            override fun step(key: String, label: String, state: StepState, action: CockpitAction?) {
                log += "STEP:$key:$label:${state.name}" + (action?.let { ":${it.name}" } ?: "")
            }
        }
        done(runCatching { work(job) })
    }

    /** No Android here: the real block model and the real pagination, lines measured as 90 characters wide. */
    override fun renderPdf(blocks: List<OwnerDocumentLayout.TextBlock>): Pair<ByteArray, Int> {
        val bottoms = blocks.map { block ->
            val lines = block.text.chunked(90).size.coerceAtLeast(1)
            List(lines) { (it + 1) * block.style.size * OwnerDocumentLayout.LINE_SPACING }
        }
        val pages = OwnerDocumentLayout.paginate(blocks, bottoms)
        return ("%PDF-TEST\n" + blocks.joinToString("\n") { it.text }).toByteArray(Charsets.UTF_8) to pages.size
    }

    override fun downloadNameTaken(displayName: String): Boolean = displayName in downloads

    override fun saveDownload(displayName: String, mime: String, bytes: ByteArray): OwnerSavedFile {
        saveProblem?.let { throw java.io.IOException(it) }
        check(displayName !in downloads) { "Datei existiert bereits" }
        downloads[displayName] = bytes
        return if (mediaStore) OwnerSavedFile(displayName, "Download-Ordner", "content://media/external/downloads/${downloads.size}", mime)
            else OwnerSavedFile(displayName, "App-Ordner Android/data/at.heinz.deviceai/files/Download", null, mime)
    }

    override fun openFile(file: OwnerSavedFile): String? { opened += file; return openProblem }

    override fun composeMail(address: String?, subject: String, body: String, attachment: OwnerSavedFile?): String? {
        mailProblem?.let { return it }
        mails += Mail(address, subject, body, attachment)
        return null
    }
}
