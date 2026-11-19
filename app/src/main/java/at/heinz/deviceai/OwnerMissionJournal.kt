package at.heinz.deviceai

import at.heinz.deviceai.cognitive.OwnerExportCommand
import at.heinz.deviceai.cognitive.OwnerMissionStep
import at.heinz.deviceai.cognitive.OwnerTaskChainRecipes
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.nio.channels.FileChannel
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption
import java.security.MessageDigest

/** Where the journal keeps its one small document. [write] is atomic and durable or throws; null removes it. */
internal interface OwnerMissionJournalStore {
    fun read(): String?
    fun write(document: String?)
}

/** Host tests and a chat without storage: the journal lives as long as this object. */
internal class MemoryOwnerMissionJournalStore : OwnerMissionJournalStore {
    var document: String? = null
    /** Tests: the next writes fail like a full or broken disk. */
    var failing = false
    var writes = 0
    override fun read(): String? = document
    override fun write(document: String?) {
        if (failing) throw IOException("journal not writable")
        writes++
        this.document = document
    }
}

/**
 * The journal in the app's private files – the same protection as the tool receipts ([FileToolExecutionReceiptStore]):
 * a directory only this app can read, no symbolic links, a bounded regular file with a checksum, written to a
 * temporary file, synced and moved into place. A file that is damaged or too large is not trusted: it reads as
 * „nothing open“, and nothing is continued from it.
 */
internal class FileOwnerMissionJournalStore(private val directory: File) : OwnerMissionJournalStore {
    private val file = File(directory, FILE_NAME)

    override fun read(): String? = synchronized(MONITOR) {
        if (unsafe() || !file.isFile || file.length() !in 1..MAX_BYTES.toLong()) return null
        runCatching {
            DataInputStream(ByteArrayInputStream(file.readBytes())).use { input ->
                if (input.readUTF() != MAGIC || input.readInt() != SCHEMA) return null
                val payload = ByteArray(input.readInt().also { require(it in 1..MAX_BYTES) })
                input.readFully(payload)
                val sum = ByteArray(32).also(input::readFully)
                if (!sum.contentEquals(MessageDigest.getInstance("SHA-256").digest(payload))) return null
                String(payload, Charsets.UTF_8)
            }
        }.getOrNull()
    }

    override fun write(document: String?): Unit = synchronized(MONITOR) {
        if (unsafe()) throw IOException("Unsafe mission journal path.")
        if (document == null) { if (file.exists() && !file.delete()) throw IOException("Cannot remove mission journal."); return }
        if (!directory.exists() && !directory.mkdirs() && !directory.isDirectory) throw IOException("Cannot create mission journal directory.")
        val payload = document.toByteArray(Charsets.UTF_8)
        require(payload.size in 1..MAX_BYTES) { "Mission journal too large." }
        val envelope = ByteArrayOutputStream().also { buffer ->
            DataOutputStream(buffer).use { out ->
                out.writeUTF(MAGIC); out.writeInt(SCHEMA); out.writeInt(payload.size); out.write(payload)
                out.write(MessageDigest.getInstance("SHA-256").digest(payload))
            }
        }.toByteArray()
        val temporary = File.createTempFile("mission-journal-", ".tmp", directory)
        try {
            FileOutputStream(temporary).use { output -> output.write(envelope); output.fd.sync() }
            try {
                Files.move(temporary.toPath(), file.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
            } catch (_: AtomicMoveNotSupportedException) {
                Files.move(temporary.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING)
            }
            runCatching { FileChannel.open(directory.toPath(), StandardOpenOption.READ).use { it.force(true) } }
        } finally { temporary.delete() }
    }

    private fun unsafe(): Boolean = Files.isSymbolicLink(directory.toPath()) || Files.isSymbolicLink(file.toPath()) ||
        (Files.exists(file.toPath(), LinkOption.NOFOLLOW_LINKS) && !Files.isRegularFile(file.toPath(), LinkOption.NOFOLLOW_LINKS))

    companion object {
        const val FILE_NAME = "owner-mission-journal.bin"
        private const val MAGIC = "CONTINUUM-MISSION-JOURNAL"
        private const val SCHEMA = 1
        private const val MAX_BYTES = 256 * 1024
        private val MONITOR = Any()
    }
}

/**
 * The persistent journal of the owner's sequence of orders (architecture review 19.11.2026, C.4; experiment E9).
 *
 * One record: the sequence that is open – or, until the next one starts, the one that ended last. It holds
 *  - the mission id, when it started (wall clock) and the owner's sentence – on this device only;
 *  - every step with its typed arguments ([OwnerMissionStepCodec]) and its state;
 *  - for every result a reference: ids, counts, file names, hashes. Never mail text, never a summary, never a
 *    message beyond the owner's own clause that is the step's argument.
 *
 * A send step is written down BEFORE it acts: [reserve] stores [State.INTENT_RESERVED] with the idempotency key
 * `sha256(missionId|stepIndex|channel|recipientId|textHash|attachmentHash)` right before the send control is pressed,
 * and refuses when this step already holds a reservation, a send or an unconfirmed press – or when the intent could
 * not be written. Afterwards the step becomes [State.SENT], or [State.UNCONFIRMED] when the press could not be
 * confirmed. A step left at INTENT_RESERVED (the app died between the press and the report) is never pressed again:
 * only the owner's explicit „ja, nochmal“ ([allowRetry]) releases it.
 *
 * Fail closed: without a writable journal nothing is reserved, so nothing is pressed.
 */
internal class OwnerMissionJournal(private val store: OwnerMissionJournalStore = MemoryOwnerMissionJournalStore()) {
    enum class State {
        PENDING, RUNNING, WAITING, DONE, FAILED, STOPPED, SKIPPED,
        /** Written right before the one press of a send step. */
        INTENT_RESERVED,
        /** Pressed once and confirmed on screen. */
        SENT,
        /** A verified draft or a hand-over: nothing was sent. */
        PREPARED,
        /** Pressed, effect not confirmed. Never pressed again without the owner. */
        UNCONFIRMED
    }

    class Step(val args: JSONObject, var state: State, var key: String?, var result: JSONObject?, var at: Long) {
        /** true = whether this step's message went out is not known. */
        val unknown: Boolean get() = state == State.INTENT_RESERVED || state == State.UNCONFIRMED
    }

    class Record(val id: String, val request: String, val startedAt: Long, val steps: List<Step>, var ended: String?, var endedAt: Long) {
        val open: Boolean get() = ended == null
    }

    private var cached: Record? = null
    private var loaded = false
    /** true after a write failed: the director says so once instead of pretending the journal is kept. */
    @Volatile var broken = false
        private set

    private fun record(): Record? {
        if (!loaded) { loaded = true; cached = runCatching { store.read()?.let(::decode) }.getOrNull() }
        return cached
    }

    private fun save(): Boolean = try {
        store.write(cached?.let(::encode)); broken = false; true
    } catch (_: Exception) { broken = true; false }

    /** The sequence that is not ended, or null. */
    @Synchronized fun open(): Record? = record()?.takeIf { it.open }

    /** A new sequence starts; whatever the journal held is replaced. false = the journal could not be written. */
    @Synchronized fun begin(id: String, request: String, startedAt: Long, steps: List<OwnerMissionStep>): Boolean {
        loaded = true
        cached = Record(id, request.take(MAX_REQUEST), startedAt,
            steps.map { Step(OwnerMissionStepCodec.encode(it), State.PENDING, null, null, startedAt) }, null, 0L)
        return save()
    }

    /**
     * The director's view of step [index]. A reservation is never overwritten by a running or waiting state: once the
     * intent is written, the step can only become sent, unconfirmed – or, when the press did not happen, free again
     * ([pressed]). [args]: the step after a correction.
     */
    @Synchronized fun step(id: String, index: Int, state: State, result: OwnerLaneResult?, at: Long, args: OwnerMissionStep? = null) {
        val record = record()?.takeIf { it.id == id } ?: return
        val step = record.steps.getOrNull(index) ?: return
        val delivered = result as? OwnerLaneResult.Delivered
        val next = when {
            delivered?.state == OwnerLaneResult.Delivered.State.SENT -> State.SENT
            delivered != null -> State.PREPARED
            step.state == State.INTENT_RESERVED && state in setOf(State.RUNNING, State.WAITING, State.PENDING) -> State.INTENT_RESERVED
            // Pressed, and the lane could not report a confirmed send: unknown, whatever else is said about it.
            step.state == State.INTENT_RESERVED && state in setOf(State.FAILED, State.STOPPED) -> State.UNCONFIRMED
            step.state == State.UNCONFIRMED && state != State.PENDING -> State.UNCONFIRMED
            else -> state
        }
        val reference = result?.let(OwnerMissionStepCodec::reference)
        val changed = step.state != next || (reference != null && reference.toString() != step.result?.toString()) || args != null
        if (!changed) return
        step.state = next
        if (reference != null) step.result = reference else if (next == State.PENDING) step.result = null
        if (args != null) { step.args.keys().asSequence().toList().forEach(step.args::remove)
            OwnerMissionStepCodec.encode(args).let { fresh -> fresh.keys().forEach { key -> step.args.put(key, fresh.get(key)) } } }
        step.at = at
        save()
    }

    /** The state the journal holds for step [index]; null when [id] is not its sequence. */
    @Synchronized fun state(id: String, index: Int): State? = record()?.takeIf { it.id == id }?.steps?.getOrNull(index)?.state

    /**
     * Right before the one press of the send step [index]. true = the intent is on disk and the press may happen.
     * [recipientId] (a number or an address) and [text] only go into the hash.
     */
    @Synchronized fun reserve(id: String, index: Int, channel: String, recipientId: String, text: String, attachment: String?, at: Long): Boolean {
        val record = record()?.takeIf { it.id == id && it.open } ?: return false
        val step = record.steps.getOrNull(index) ?: return false
        if (step.state == State.INTENT_RESERVED || step.state == State.SENT || step.state == State.UNCONFIRMED) return false
        val key = key(id, index, channel, recipientId, text, attachment)
        // The same message of the same step was reserved before and never released by the owner.
        if (record.steps.any { it.key == key && it !== step }) return false
        val before = step.state
        val beforeKey = step.key
        step.state = State.INTENT_RESERVED
        step.key = key
        step.at = at
        if (save()) return true
        step.state = before
        step.key = beforeKey
        return false
    }

    /** The press returned. [clicked] false = the control was not clicked: nothing went out, the step is free again. */
    @Synchronized fun pressed(id: String, index: Int, clicked: Boolean, at: Long) {
        val step = record()?.takeIf { it.id == id }?.steps?.getOrNull(index) ?: return
        if (clicked || step.state != State.INTENT_RESERVED) return
        step.state = State.RUNNING
        step.key = null
        step.at = at
        save()
    }

    /** The owner's explicit „ja, nochmal“ for a step whose send is unknown: it may be reserved and pressed once more. */
    @Synchronized fun allowRetry(id: String, index: Int, at: Long): Boolean {
        val step = record()?.takeIf { it.id == id }?.steps?.getOrNull(index) ?: return false
        if (!step.unknown) return false
        step.state = State.PENDING
        step.key = null
        step.result = null
        step.at = at
        return save()
    }

    /** A sequence that had ended goes on (the owner corrected it in the very next turn). */
    @Synchronized fun reopen(id: String) {
        val record = record()?.takeIf { it.id == id && !it.open } ?: return
        record.ended = null
        save()
    }

    @Synchronized fun end(id: String, how: String, at: Long) {
        val record = record()?.takeIf { it.id == id && it.open } ?: return
        record.ended = how
        record.endedAt = at
        save()
    }

    /** Removes everything (the owner's „vergiss die Auftragsfolge“, or a record that can no longer be read as steps). */
    @Synchronized fun clear() { loaded = true; cached = null; save() }

    private fun encode(record: Record): String = JSONObject().put("v", VERSION).put("id", record.id).put("request", record.request)
        .put("startedAt", record.startedAt).put("ended", record.ended ?: JSONObject.NULL).put("endedAt", record.endedAt)
        .put("steps", JSONArray().apply {
            record.steps.forEach { step ->
                put(JSONObject().put("args", step.args).put("state", step.state.name).put("key", step.key ?: JSONObject.NULL)
                    .put("result", step.result ?: JSONObject.NULL).put("at", step.at))
            }
        }).toString()

    private fun decode(document: String): Record? {
        val json = JSONObject(document)
        if (json.optInt("v") != VERSION) return null
        val steps = json.getJSONArray("steps")
        if (steps.length() !in 1..MAX_STEPS) return null
        return Record(json.getString("id"), json.getString("request"), json.getLong("startedAt"), (0 until steps.length()).map { index ->
            val step = steps.getJSONObject(index)
            Step(step.getJSONObject("args"), State.valueOf(step.getString("state")), step.optString("key").takeIf { !step.isNull("key") && it.isNotEmpty() },
                step.optJSONObject("result"), step.optLong("at"))
        }, json.optString("ended").takeIf { !json.isNull("ended") && it.isNotEmpty() }, json.optLong("endedAt"))
    }

    companion object {
        private const val VERSION = 1
        private const val MAX_STEPS = 12
        private const val MAX_REQUEST = 700

        fun sha256(text: String): String = MessageDigest.getInstance("SHA-256").digest(text.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }

        /** `sha256(missionId|stepIndex|channel|recipientId|textHash|attachmentHash)`. */
        fun key(id: String, index: Int, channel: String, recipientId: String, text: String, attachment: String?): String =
            sha256(listOf(id, index.toString(), channel, recipientId, sha256(text), attachment?.let(::sha256) ?: "-").joinToString("|"))
    }
}

/**
 * A step of a sequence as the journal keeps it – kind and typed arguments – and a result as a reference. The owner's
 * own clause ([OwnerMissionStep.order]) is part of the arguments; nothing a lane read or generated is.
 */
internal object OwnerMissionStepCodec {
    fun encode(step: OwnerMissionStep): JSONObject {
        val json = JSONObject().put("order", step.order).put("source", step.source ?: JSONObject.NULL)
        fun text(key: String, value: String?) = json.put(key, value ?: JSONObject.NULL)
        when (step) {
            is OwnerMissionStep.OpenPdf -> { json.put("kind", "OPEN_PDF").put("query", step.query).put("open", step.open); text("folder", step.folderHint) }
            is OwnerMissionStep.PageSave -> { json.put("kind", "PAGE_SAVE").put("page", step.page); text("format", step.format?.name); text("name", step.name) }
            is OwnerMissionStep.PageSummary -> json.put("kind", "PAGE_SUMMARY").put("page", step.page)
            is OwnerMissionStep.News -> { json.put("kind", "NEWS"); text("topic", step.topic) }
            is OwnerMissionStep.Export -> { json.put("kind", "EXPORT").put("format", step.format.name); text("name", step.name) }
            is OwnerMissionStep.Mail -> { json.put("kind", "MAIL"); text("address", step.address); text("recipient", step.recipientName) }
            is OwnerMissionStep.FindFile -> json.put("kind", "FIND_FILE")
            is OwnerMissionStep.OpenFound -> json.put("kind", "OPEN_FOUND")
            is OwnerMissionStep.OpenSaved -> json.put("kind", "OPEN_SAVED")
            is OwnerMissionStep.Storage -> json.put("kind", "STORAGE").put("mode", step.mode)
            is OwnerMissionStep.SendChat -> { json.put("kind", "SEND_CHAT").put("fromOpening", step.channelFromOpening)
                .put("draftOnly", step.draftOnly).put("corrected", step.corrected); text("recipient", step.recipient) }
            is OwnerMissionStep.OpenApp -> json.put("kind", "OPEN_APP").put("app", step.app).put("appName", step.appName)
            is OwnerMissionStep.MailSearch -> { json.put("kind", "MAIL_SEARCH"); search(json, step) }
            is OwnerMissionStep.MailRead -> { json.put("kind", "MAIL_READ"); step.own?.let { json.put("own", search(JSONObject().put("order", it.order), it)) } }
            is OwnerMissionStep.MailSummary -> { json.put("kind", "MAIL_SUMMARY"); step.own?.let { json.put("own", search(JSONObject().put("order", it.order), it)) } }
            is OwnerMissionStep.Calendar -> json.put("kind", "CALENDAR").put("what", step.what).put("span", step.span).put("ask", step.ask)
            is OwnerMissionStep.Research -> json.put("kind", "RESEARCH").put("topic", step.topic)
            else -> json.put("kind", "UNKNOWN")
        }
        return json
    }

    private fun search(json: JSONObject, step: OwnerMissionStep.MailSearch): JSONObject = json.put("from", step.from ?: JSONObject.NULL)
        .put("about", step.about ?: JSONObject.NULL).put("day", step.day ?: JSONObject.NULL).put("unread", step.unreadOnly)
        .put("newest", step.newest).put("fromSaid", step.fromSaid ?: JSONObject.NULL)

    private fun text(json: JSONObject, key: String): String? = if (json.isNull(key)) null else json.optString(key).takeIf { it.isNotEmpty() }

    private fun searchOf(json: JSONObject): OwnerMissionStep.MailSearch = OwnerMissionStep.MailSearch(json.getString("order"), text(json, "from"),
        text(json, "about"), text(json, "day"), json.optBoolean("unread"), json.optBoolean("newest"), text(json, "fromSaid"))

    /** The step [json] describes, or null when it cannot be read back exactly – then nothing is continued. */
    fun decode(json: JSONObject, assistantName: String?): OwnerMissionStep? = runCatching {
        val order = json.getString("order")
        val source = if (json.isNull("source")) null else json.getInt("source")
        when (json.getString("kind")) {
            "OPEN_PDF" -> OwnerMissionStep.OpenPdf(order, json.getString("query"), json.getBoolean("open"), text(json, "folder"), source)
            "PAGE_SAVE" -> OwnerMissionStep.PageSave(order, json.getInt("page"), text(json, "format")?.let(OwnerExportCommand.Format::valueOf),
                text(json, "name"), source)
            "PAGE_SUMMARY" -> OwnerMissionStep.PageSummary(order, json.getInt("page"), source)
            "NEWS" -> OwnerMissionStep.News(order, text(json, "topic"), source)
            "EXPORT" -> OwnerMissionStep.Export(order, OwnerExportCommand.Format.valueOf(json.getString("format")), text(json, "name"), source)
            "MAIL" -> OwnerMissionStep.Mail(order, text(json, "address"), text(json, "recipient"), source)
            // The chain's plan is code, not data: it is recognised again from the owner's own clause.
            "FIND_FILE" -> OwnerTaskChainRecipes.recognise(order, assistantName)?.let { OwnerMissionStep.FindFile(order, it, source) }
            "OPEN_FOUND" -> OwnerMissionStep.OpenFound(order, source)
            "OPEN_SAVED" -> OwnerMissionStep.OpenSaved(order, source)
            "STORAGE" -> OwnerMissionStep.Storage(order, json.getString("mode"), source)
            "SEND_CHAT" -> OwnerMissionStep.SendChat(order, text(json, "recipient"), json.optBoolean("fromOpening"), json.optBoolean("draftOnly"),
                json.optBoolean("corrected"), source)
            "OPEN_APP" -> OwnerMissionStep.OpenApp(order, json.getString("app"), json.getString("appName"), source)
            "MAIL_SEARCH" -> searchOf(json).copy(source = source)
            "MAIL_READ" -> OwnerMissionStep.MailRead(order, source, json.optJSONObject("own")?.let(::searchOf))
            "MAIL_SUMMARY" -> OwnerMissionStep.MailSummary(order, source, json.optJSONObject("own")?.let(::searchOf))
            "CALENDAR" -> OwnerMissionStep.Calendar(order, json.getString("what"), json.getString("span"), json.getString("ask"), source)
            "RESEARCH" -> OwnerMissionStep.Research(order, json.getString("topic"), source)
            else -> null
        }
    }.getOrNull()

    private fun hash(text: String): String = OwnerMissionJournal.sha256(text)

    /** What [result] is, without what it says: ids, counts, file names, hashes. */
    fun reference(result: OwnerLaneResult): JSONObject = when (result) {
        is OwnerLaneResult.DocumentOpened -> JSONObject().put("type", "DOCUMENT_OPENED").put("name", result.name).put("pages", result.pages)
        is OwnerLaneResult.FilesFound -> JSONObject().put("type", "FILES_FOUND").put("count", result.names.size)
        is OwnerLaneResult.Briefing -> JSONObject().put("type", "BRIEFING").put("stories", result.stories).put("hash", hash(result.deliverable.plainText))
        is OwnerLaneResult.FileSaved -> file(JSONObject().put("type", "FILE_SAVED"), result.file).put("hash", hash(result.of.plainText))
        is OwnerLaneResult.FileKept -> file(JSONObject().put("type", "FILE_SAVED"), result.file)
        is OwnerLaneResult.PageSummarised -> JSONObject().put("type", "PAGE_SUMMARISED").put("document", result.document).put("page", result.page)
            .put("hash", hash(result.of.plainText))
        is OwnerLaneResult.Agenda -> JSONObject().put("type", "AGENDA").put("span", result.span).put("events", result.events)
            .put("findings", result.findings).put("free", result.freeBlocks)
        is OwnerLaneResult.FileFound -> JSONObject().put("type", "FILE_FOUND").put("name", result.name).put("folder", result.folderHint ?: JSONObject.NULL)
        is OwnerLaneResult.FileOpened -> file(JSONObject().put("type", "FILE_OPENED"), result.file)
        is OwnerLaneResult.Storage -> JSONObject().put("type", "STORAGE").put("mode", result.mode).put("freeBefore", result.freeBefore ?: JSONObject.NULL)
            .put("freeAfter", result.freeAfter ?: JSONObject.NULL).put("removedFiles", result.removedFiles).put("removedBytes", result.removedBytes)
            .put("offered", result.offered)
        is OwnerLaneResult.AppOpened -> JSONObject().put("type", "APP_OPENED").put("app", result.appName).put("package", result.packageName)
        is OwnerLaneResult.MailsFound -> JSONObject().put("type", "MAILS_FOUND").put("query", hash(result.query))
            .put("ids", JSONArray().apply { result.ids.forEach { put(JSONArray().put(it.first).put(it.second)) } })
        is OwnerLaneResult.MailRead -> JSONObject().put("type", "MAIL_READ").put("id", result.id.first).put("thread", result.id.second).put("chars", result.chars)
        is OwnerLaneResult.MailSummarised -> JSONObject().put("type", "MAIL_SUMMARISED").put("mails", result.mails).put("cloud", result.cloud)
            .put("hash", hash(result.of.plainText))
        is OwnerLaneResult.Researched -> JSONObject().put("type", "RESEARCHED").put("sources", result.sources).put("hash", hash(result.of.plainText))
        is OwnerLaneResult.Delivered -> JSONObject().put("type", "DELIVERED").put("channel", result.channel).put("state", result.state.name)
            .put("to", hash(result.to)).put("attachment", result.attachment?.displayName ?: JSONObject.NULL)
    }

    private fun file(json: JSONObject, file: OwnerSavedFile): JSONObject = json.put("name", file.displayName).put("folder", file.folder)
        .put("uri", file.uri ?: JSONObject.NULL).put("mime", file.mime)

    /**
     * The part of a result that can stand again after a restart, built from its reference: what was found, opened,
     * measured or delivered. A text result (a briefing, a summary, a dossier) is not in the journal and comes back as
     * null – the step that produced it is done again when a later step needs it.
     */
    fun restore(reference: JSONObject?): OwnerLaneResult? = runCatching {
        reference ?: return null
        when (reference.optString("type")) {
            "FILE_FOUND" -> OwnerLaneResult.FileFound(reference.getString("name"), text(reference, "folder"))
            "FILE_SAVED" -> OwnerLaneResult.FileKept(savedFile(reference))
            "FILE_OPENED" -> OwnerLaneResult.FileOpened(savedFile(reference))
            "AGENDA" -> OwnerLaneResult.Agenda(reference.getString("span"), reference.getInt("events"), reference.getInt("findings"), reference.getInt("free"))
            "STORAGE" -> OwnerLaneResult.Storage(reference.getString("mode"), if (reference.isNull("freeBefore")) null else reference.getLong("freeBefore"),
                if (reference.isNull("freeAfter")) null else reference.getLong("freeAfter"), reference.getInt("removedFiles"),
                reference.getLong("removedBytes"), reference.getInt("offered"))
            "APP_OPENED" -> OwnerLaneResult.AppOpened(reference.getString("app"), reference.getString("package"))
            "MAILS_FOUND" -> reference.getJSONArray("ids").let { ids ->
                OwnerLaneResult.MailsFound("", (0 until ids.length()).map { ids.getJSONArray(it).let { pair -> pair.getString(0) to pair.getString(1) } }) }
            "MAIL_READ" -> OwnerLaneResult.MailRead(reference.getString("id") to reference.getString("thread"), reference.getInt("chars"))
            else -> null
        }
    }.getOrNull()

    private fun savedFile(reference: JSONObject): OwnerSavedFile =
        OwnerSavedFile(reference.getString("name"), reference.getString("folder"), text(reference, "uri"), reference.getString("mime"))
}
