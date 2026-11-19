package at.heinz.deviceai

import at.heinz.deviceai.cockpit.CockpitAction
import at.heinz.deviceai.cockpit.StepState
import at.heinz.deviceai.cognitive.OwnerDeliverable
import at.heinz.deviceai.cognitive.OwnerDocumentDialogue
import at.heinz.deviceai.cognitive.OwnerDocumentLayout
import at.heinz.deviceai.cognitive.OwnerExportCommand
import at.heinz.deviceai.cognitive.OwnerLibraryFile
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.concurrent.CancellationException

/**
 * Host of [OwnerDocumentDesk] for JVM tests. Everything that decides or words is the productive code – parsers,
 * matcher, the desk's state machine, the page prompt, the Word writer. Stand-ins are only the phone: the storage
 * listing, the PDF reader, the model text, the Download folder and Android's settings and picker. Jobs run inline;
 * the hooks [duringScan], [duringGeneration] and [beforeSave] let a test act while a job is running.
 */
internal class OwnerDocumentDeskHarness(
    /** The work tools' host of the same chat: one turn counter and one Download folder for both lanes. */
    private val shared: OwnerWorkToolsHarness? = null
) : OwnerDocumentDeskHost {
    /** A PDF on the fake storage: its pages' text ("" = a page without text layer). */
    class Pdf(val pages: List<String>, val textState: OwnerDocumentDialogue.TextState = OwnerDocumentDialogue.TextState.EXTRACTED)

    val log = mutableListOf<String>()
    override var sessionId = "chat-1"
    private var ownTurn = 0L
    override var ownerTurn: Long
        get() = shared?.ownerTurn ?: ownTurn
        set(value) { if (shared != null) shared.ownerTurn = value else ownTurn = value }
    override val assistantName: String? = "Jarvis"

    var libraryAccess = OwnerLibraryAccess.GRANTED
    val library = mutableListOf<OwnerLibraryFile>()
    val pdfs = HashMap<String, Pdf>()
    var folders = listOf("Download", "Documents", "DCIM")
    var scanStopped: String? = null
    var scans = 0
    var settingsProblem: String? = null
    var settingsOpened = 0
    val pickers = mutableListOf<String>()
    override var pendingSearch: OwnerDeskPendingSearch? = null

    val hitsShown = mutableListOf<List<String>>()
    val shown = mutableListOf<OwnerDeskPdf>()
    val discarded = mutableListOf<OwnerDeskPdf>()
    private var open: OwnerDeskDocument? = null

    var modelLoaded = true
    var cloud = false
    var budget = 6_000
    var model: (String) -> OwnerWorkGeneration = { OwnerWorkGeneration("", "kein Modelltext hinterlegt", false) }
    val prompts = mutableListOf<String>()

    val downloads: LinkedHashMap<String, ByteArray> = shared?.downloads ?: linkedMapOf()
    var saveProblem: String? = null
    var mediaStore = true
    var readBackWorks = true
    val adopted = mutableListOf<Pair<OwnerDeliverable, OwnerSavedFile>>()
    var onAdopt: ((OwnerDeliverable, OwnerSavedFile) -> Unit)? = null
    /** What the work tools do with „Ich meinte als PDF“ after one of their own saves. */
    var exportAgainTakes: ((String, OwnerExportCommand.Format) -> Boolean)? = null
    var busyElsewhere = false

    var duringScan: (() -> Unit)? = null
    var duringGeneration: (() -> Unit)? = null
    var beforeSave: (() -> Unit)? = null
    private var jobActive = false
    private var cancelRequested = false
    var cancels = 0

    fun lines(prefix: String): List<String> = log.filter { it.startsWith(prefix) }.map { it.removePrefix(prefix) }
    val spoken: List<String> get() = log.filter { it.startsWith("SAY:") || it.startsWith("REPLY:") }.map { it.substringAfter(':') }
    val steps: List<String> get() = lines("STEP:")

    /** Puts a PDF on the fake storage. */
    fun put(name: String, folder: String = "Download", pages: List<String> = listOf("Erste Seite."), size: Long = 1_258_291,
            modified: String = "2026-11-17", textState: OwnerDocumentDialogue.TextState = OwnerDocumentDialogue.TextState.EXTRACTED): OwnerLibraryFile {
        val file = OwnerLibraryFile(name, folder, "/storage/emulated/0/" + (if (folder.isEmpty()) "" else "$folder/") + name, size,
            LocalDate.parse(modified).atTime(10, 0).atZone(ZONE).toInstant().toEpochMilli())
        library += file
        pdfs[file.path] = Pdf(pages, textState)
        return file
    }

    /** A PDF opened earlier in this chat by another path (Android's picker). */
    fun opened(name: String, pages: List<String>) {
        pdfs["picked:$name"] = Pdf(pages)
        open = OwnerDeskDocument(name, pages.size, "picked:$name") { page -> pages.getOrNull(page - 1) }
    }

    /** Another lane answered a turn in between. */
    fun foreignTurn() { ownerTurn++ }

    override fun acceptOwnerTurn(request: String) { ownerTurn++; log += "USER:$request" }
    override fun reply(request: String, text: String) { log += "REPLY:$text" }
    override fun say(text: String) { log += "SAY:$text" }
    override fun activity(line: String) { log += "ACTIVITY:$line" }
    override fun idle(): Boolean = !jobActive && !busyElsewhere
    override fun access(): OwnerLibraryAccess = libraryAccess
    override fun openAccessSettings(): String? = settingsProblem ?: null.also { settingsOpened++ }
    override fun openPicker(request: String) { pickers += request }

    override fun <T> runJob(request: String, work: (OwnerDeskJob) -> T, done: (Result<T>, OwnerDeskJobEnd) -> Unit) {
        check(!jobActive) { "Two jobs on the single lane" }
        jobActive = true
        cancelRequested = false
        val startedIn = sessionId
        val job = object : OwnerDeskJob {
            override fun step(key: String, label: String, state: StepState, action: CockpitAction?) {
                log += "STEP:$key:$label:${state.name}" + (action?.let { ":${it.name}" } ?: "")
            }
            override fun cancelled(): Boolean = cancelRequested
            override fun scan(progress: (Int) -> Unit): OwnerLibraryScan {
                scans++
                duringScan?.also { duringScan = null }?.invoke()
                if (cancelRequested) throw CancellationException("Suche gestoppt")
                if (library.size >= 25) progress(25)
                progress(library.size)
                return OwnerLibraryScan(library.toList(), folders, scanStopped)
            }
            override fun readPdf(file: OwnerLibraryFile): OwnerDeskPdf {
                val pdf = pdfs[file.path] ?: throw java.io.FileNotFoundException("${file.name} ist nicht mehr da")
                return OwnerDeskPdf(file.name, "file://" + file.path, pdf.pages.size, pdf.textState, pdf.pages.sumOf { it.length }, pdf)
            }
            override fun readPage(document: OwnerDeskDocument, page: Int): OwnerDeskPage {
                val pdf = pdfs[document.uri.orEmpty().removePrefix("file://")] ?: return OwnerDeskPage.Failed("Datei nicht mehr lesbar")
                return when {
                    pdf.textState == OwnerDocumentDialogue.TextState.NEEDS_ANDROID_15 -> OwnerDeskPage.NeedsAndroid15
                    page !in 1..pdf.pages.size -> OwnerDeskPage.Missing(pdf.pages.size)
                    pdf.pages[page - 1].isBlank() -> OwnerDeskPage.NoText
                    else -> OwnerDeskPage.Text(pdf.pages[page - 1])
                }
            }
            override fun generate(prompt: String, progress: (Int) -> Unit): OwnerWorkGeneration {
                prompts += prompt
                duringGeneration?.also { duringGeneration = null }?.invoke()
                // The Activity's generation ends with a CancellationException as soon as STOP was pressed.
                if (cancelRequested) throw CancellationException("Local generation cancelled")
                return model(prompt).also { progress(it.text.length) }
            }
        }
        val result = runCatching { work(job) }
        jobActive = false
        done(result, when {
            sessionId != startedIn -> OwnerDeskJobEnd.SESSION_CHANGED
            cancelRequested -> OwnerDeskJobEnd.STOPPED
            else -> OwnerDeskJobEnd.FINISHED
        })
    }

    /** STOP as the Activity delivers it: the running job is asked to end; its result still comes back. */
    override fun cancelJob() { if (jobActive) { cancelRequested = true; cancels++ } }

    override fun showHits(query: String, files: List<OwnerLibraryFile>) { hitsShown += files.map { it.name } }

    override fun showPdf(request: String, pdf: OwnerDeskPdf) {
        shown += pdf
        val pages = (pdf.payload as Pdf).pages
        open = OwnerDeskDocument(pdf.name, pdf.pageCount, pdf.uri) { page -> pages.getOrNull(page - 1) }
        log += "SHOW:${pdf.name}"
    }

    override fun discardPdf(pdf: OwnerDeskPdf) { discarded += pdf }
    override fun openDocument(): OwnerDeskDocument? = open
    override fun modelReady(): Boolean = modelLoaded
    override fun cloudBrain(): Boolean = cloud
    override fun promptBudgetBytes(): Int = budget

    override fun renderPdf(blocks: List<OwnerDocumentLayout.TextBlock>): Pair<ByteArray, Int> =
        ("%PDF-TEST\n" + blocks.joinToString("\n") { it.text }).toByteArray(Charsets.UTF_8) to 1

    override fun downloadNameTaken(displayName: String): Boolean = displayName in downloads

    override fun saveDownload(displayName: String, mime: String, bytes: ByteArray): OwnerSavedFile {
        beforeSave?.also { beforeSave = null }?.invoke()
        saveProblem?.let { throw java.io.IOException(it) }
        check(displayName !in downloads) { "Datei existiert bereits" }
        downloads[displayName] = bytes
        return if (mediaStore) OwnerSavedFile(displayName, "Download-Ordner", "content://media/external/downloads/${downloads.size}", mime)
            else OwnerSavedFile(displayName, "App-Ordner Android/data/at.heinz.deviceai/files/Download", null, mime)
    }

    override fun readBack(file: OwnerSavedFile): ByteArray? = if (readBackWorks) downloads[file.displayName] else null
    override fun location(file: OwnerSavedFile): String? = if (file.uri != null) "Interner Speicher › Download" else null
    override fun adopt(result: OwnerDeliverable, file: OwnerSavedFile) { adopted += result to file; onAdopt?.invoke(result, file) }

    /** true = a sequence of owner orders hands its steps to the desk right now. */
    var sequence = false
    override fun inSequence(): Boolean = sequence
    /** Results the desk showed without saving them; [presentTaken]: the host made them what the chat is about. */
    val presented = mutableListOf<OwnerDeliverable>()
    var presentTaken = false
    override fun present(result: OwnerDeliverable): Boolean { presented += result; return presentTaken }
    /** How the desk said its orders ended (typed), in order. */
    val outcomes = mutableListOf<OwnerLaneOutcome>()
    override fun outcome(outcome: OwnerLaneOutcome) { outcomes += outcome }
    override fun exportAgain(request: String, format: OwnerExportCommand.Format): Boolean =
        exportAgainTakes?.invoke(request, format) ?: false

    companion object {
        val ZONE: ZoneId = ZoneId.of("Europe/Vienna")
        val NOW: Instant = Instant.parse("2026-11-18T09:30:00Z")

        fun desk(host: OwnerDocumentDeskHarness) = OwnerDocumentDesk(host, now = { NOW }, zone = { ZONE })

        /** Four pages of a synthetic document; every page names itself so a prompt shows which one it carries. */
        val ARGON = listOf(
            "Argon4 – Überblick. Seite eins beschreibt den Aufbau des lokalen Gedächtnisses und seine Grenzen.",
            "Seite zwei erklärt den Abruf: Erinnerungen werden nach Zeit und Bedeutung geordnet. Der Abruf läuft vollständig " +
                "auf dem Gerät und braucht kein Netz. Ein Eintrag behält, wann er gesagt und wann er gültig wurde.",
            "Seite drei behandelt Korrekturen: Eine spätere Aussage ersetzt die frühere nicht, sie wird daneben gelegt und " +
                "markiert. Widersprüche werden dem Besitzer gezeigt, nicht still aufgelöst.",
            "")
        const val SUMMARY_2 = "Die zweite Seite beschreibt, wie Erinnerungen abgerufen werden. Sie werden nach Zeit und " +
            "Bedeutung geordnet. Der Abruf läuft vollständig auf dem Gerät und braucht kein Netz. Jeder Eintrag behält zwei Zeitpunkte."
        const val SUMMARY_3 = "Die dritte Seite behandelt Korrekturen. Eine spätere Aussage ersetzt die frühere nicht, " +
            "sondern wird daneben gelegt und markiert. Widersprüche werden dem Besitzer gezeigt."
    }
}
