package at.heinz.deviceai.routines

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.nio.charset.CodingErrorAction
import java.nio.file.Files
import java.nio.file.LinkOption.NOFOLLOW_LINKS
import java.nio.file.StandardCopyOption.ATOMIC_MOVE
import java.nio.file.StandardCopyOption.REPLACE_EXISTING
import java.nio.file.StandardOpenOption.CREATE
import java.nio.file.StandardOpenOption.READ
import java.nio.file.StandardOpenOption.WRITE
import java.security.MessageDigest

data class RoutineOwnerApproval(val atMillis: Long, val definitionSha256: String) {
    fun matches(routine: Routine): Boolean = atMillis >= 0 && definitionSha256 == fingerprint(routine)

    companion object {
        fun confirmed(routine: Routine, atMillis: Long): RoutineOwnerApproval =
            RoutineOwnerApproval(atMillis, fingerprint(routine)).also { require(atMillis >= 0) }

        private fun fingerprint(routine: Routine): String {
            val bytes = ByteArrayOutputStream()
            DataOutputStream(bytes).use { out ->
                out.writeUTF(routine.id)
                out.writeUTF(routine.goal)
                when (val trigger = routine.trigger) {
                    is RoutineTrigger.Every -> {
                        out.writeByte(1); out.writeLong(trigger.periodMillis); out.writeLong(trigger.firstAtMillis)
                    }
                    is RoutineTrigger.OnEvent -> { out.writeByte(2); out.writeUTF(trigger.name) }
                }
                routine.allowedTools.sorted().forEach(out::writeUTF)
            }
            return MessageDigest.getInstance("SHA-256").digest(bytes.toByteArray()).joinToString("") { "%02x".format(it) }
        }
    }
}

data class RoutineChatAppend(val conversationId: String, val offset: Long, val text: String)

data class StoredRoutineReport(
    val routineId: String,
    val runId: String,
    val digest: String,
    val message: AgentMessage?,
    val result: String,
    val atMillis: Long,
    val chatAppend: RoutineChatAppend? = null,
    val chatDelivered: Boolean = false
)

data class StoredRoutine(
    val routine: Routine,
    val approval: RoutineOwnerApproval,
    val nextAtMillis: Long,
    val lastDigest: String? = null,
    val lastResult: String? = null,
    val lastResultAtMillis: Long = 0,
    val runId: String? = null,
    val report: StoredRoutineReport? = null
)

data class RoutineStoredState(
    val revision: Long = 0,
    val entries: List<StoredRoutine> = emptyList(),
    val outbox: List<StoredRoutineReport> = emptyList()
) {
    internal fun validate() {
        require(revision >= 0 && entries.size <= MAX_ROUTINES && outbox.size <= MAX_OUTBOX)
        require(entries.map { it.routine.id }.distinct().size == entries.size)
        val validation = RoutineBook()
        entries.forEach { entry ->
            require(validation.add(entry.routine) && entry.approval.matches(entry.routine))
            require(entry.routine.allowedTools.size in 1..3)
            require(entry.routine.allowedTools.all {
                at.heinz.deviceai.ToolRegistry.get(it)?.risk == at.heinz.deviceai.Risk.READ_ONLY
            })
            require(entry.nextAtMillis >= 0 && entry.lastResultAtMillis >= 0)
            require(entry.lastDigest == null || entry.lastDigest.length <= MAX_RESULT)
            require(entry.lastResult == null || entry.lastResult.length <= MAX_RESULT)
            require(entry.runId == null || RUN_ID.matches(entry.runId))
            entry.report?.let { report ->
                require(report.runId == entry.runId && report.routineId == entry.routine.id)
                validateReport(report)
            }
        }
        outbox.forEach {
            validateReport(it)
            require(it.message != null && !it.chatDelivered)
        }
        val pending = entries.mapNotNull { it.report } + outbox
        require(pending.map { it.runId }.distinct().size == pending.size)
        require(pending.mapNotNull { it.message?.id }.distinct().size == pending.count { it.message != null })
    }

    private fun validateReport(report: StoredRoutineReport) {
        require(ROUTINE_ID.matches(report.routineId) && RUN_ID.matches(report.runId))
        require(report.digest.length <= MAX_RESULT && report.result.length <= MAX_RESULT && report.atMillis >= 0)
        report.message?.let { m ->
            require(m.id.length in 1..128 && m.from.length in 1..64 && m.to == "chief")
            require(m.body.isNotBlank() && m.body.length <= 4_000)
            require(m.id == "routine-${report.routineId}-${report.atMillis}-${report.runId.replace("-", "")}")
        }
        report.chatAppend?.let {
            require(report.message != null && it.conversationId.matches(RUN_ID) && it.offset >= 0)
            require(it.text.length in 1..32_000)
            val entry = org.json.JSONObject(it.text.trim())
            require(entry.length() == 3 && entry.get("u") == false &&
                entry.getString("t") == report.message.body && entry.getLong("at") == report.atMillis)
        }
    }

    companion object {
        const val MAX_ROUTINES = 32
        const val MAX_OUTBOX = 64
        const val MAX_RESULT = 8_000
        private val ROUTINE_ID = Regex("[a-z0-9][a-z0-9._-]{0,63}")
        internal val RUN_ID = Regex("[a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12}")
    }
}

interface RoutineStateStore {
    fun load(): RoutineStoredState
    fun replace(expectedRevision: Long, state: RoutineStoredState)
}

class FileRoutineStateStore(private val directory: File) : RoutineStateStore {
    private val file = File(directory, "owner-routines-v1.bin")

    override fun load(): RoutineStoredState = guarded { read() }

    override fun replace(expectedRevision: Long, state: RoutineStoredState) = guarded {
        check(read().revision == expectedRevision) { "Routine state changed; reload before continuing." }
        require(expectedRevision < Long.MAX_VALUE && state.revision == expectedRevision + 1)
        state.validate()
        val payload = encode(state)
        require(payload.size <= MAX_BYTES)
        val temp = File.createTempFile("routine-", ".tmp", directory)
        try {
            FileOutputStream(temp).use { output ->
                output.write(MAGIC)
                output.write(MessageDigest.getInstance("SHA-256").digest(payload))
                output.write(payload)
                output.fd.sync()
            }
            Files.move(temp.toPath(), file.toPath(), ATOMIC_MOVE, REPLACE_EXISTING)
            FileChannel.open(directory.toPath(), READ).use { it.force(true) }
        } finally { temp.delete() }
    }

    private fun <T> guarded(block: () -> T): T = synchronized(LOCK) {
        val path = directory.absoluteFile.toPath().normalize()
        require(path == directory.canonicalFile.toPath()) { "Unsafe routine directory." }
        Files.createDirectories(path)
        require(Files.isDirectory(path, NOFOLLOW_LINKS))
        val lock = path.resolve(".routine-lock")
        for (candidate in listOf(lock, file.toPath())) {
            require(!Files.isSymbolicLink(candidate))
            if (Files.exists(candidate, NOFOLLOW_LINKS)) require(Files.isRegularFile(candidate, NOFOLLOW_LINKS))
        }
        FileChannel.open(lock, CREATE, WRITE, NOFOLLOW_LINKS).use { channel ->
            val held = channel.tryLock() ?: throw IOException("Routine store busy.")
            held.use { block() }
        }
    }

    private fun read(): RoutineStoredState {
        if (!file.exists()) return RoutineStoredState()
        require(file.length() in (MAGIC.size + 32L)..(MAX_BYTES + MAGIC.size + 32L))
        val bytes = file.readBytes()
        require(bytes.copyOfRange(0, MAGIC.size).contentEquals(MAGIC)) { "Routine store version." }
        val payload = bytes.copyOfRange(MAGIC.size + 32, bytes.size)
        require(MessageDigest.isEqual(bytes.copyOfRange(MAGIC.size, MAGIC.size + 32),
            MessageDigest.getInstance("SHA-256").digest(payload))) { "Routine store checksum." }
        return decode(payload).also { it.validate() }
    }

    private fun encode(state: RoutineStoredState): ByteArray = ByteArrayOutputStream().also { bytes ->
        DataOutputStream(bytes).use { out ->
            out.writeLong(state.revision)
            out.writeInt(state.entries.size)
            state.entries.forEach { e ->
                out.string(e.routine.id); out.string(e.routine.goal)
                when (val t = e.routine.trigger) {
                    is RoutineTrigger.Every -> { out.writeByte(1); out.writeLong(t.periodMillis); out.writeLong(t.firstAtMillis) }
                    is RoutineTrigger.OnEvent -> { out.writeByte(2); out.string(t.name) }
                }
                out.writeInt(e.routine.allowedTools.size)
                e.routine.allowedTools.sorted().forEach { out.string(it) }
                out.writeLong(e.approval.atMillis); out.string(e.approval.definitionSha256)
                out.writeLong(e.nextAtMillis); out.optional(e.lastDigest); out.optional(e.lastResult)
                out.writeLong(e.lastResultAtMillis); out.optional(e.runId)
                out.writeBoolean(e.report != null); e.report?.let { out.report(it) }
            }
            out.writeInt(state.outbox.size); state.outbox.forEach { out.report(it) }
        }
    }.toByteArray()

    private fun decode(bytes: ByteArray): RoutineStoredState = DataInputStream(ByteArrayInputStream(bytes)).use { input ->
        val revision = input.readLong()
        val entries = List(input.count(RoutineStoredState.MAX_ROUTINES)) {
            val id = input.string(); val goal = input.string()
            val trigger = when (input.readByte().toInt()) {
                1 -> RoutineTrigger.Every(input.readLong(), input.readLong())
                2 -> RoutineTrigger.OnEvent(input.string())
                else -> error("Unknown routine trigger.")
            }
            val tools = List(input.count(3)) { input.string() }
            require(tools.distinct().size == tools.size)
            val approval = RoutineOwnerApproval(input.readLong(), input.string())
            StoredRoutine(Routine(id, goal, trigger, tools.toSet()), approval,
                input.readLong(), input.optional(), input.optional(), input.readLong(), input.optional(),
                if (input.readBoolean()) input.report() else null)
        }
        val outbox = List(input.count(RoutineStoredState.MAX_OUTBOX)) { input.report() }
        require(input.read() == -1) { "Trailing routine data." }
        RoutineStoredState(revision, entries, outbox)
    }

    private fun DataOutputStream.report(r: StoredRoutineReport) {
        string(r.routineId); string(r.runId); string(r.digest); string(r.result); writeLong(r.atMillis)
        writeBoolean(r.message != null)
        r.message?.let { m -> string(m.id); string(m.from); string(m.to); writeInt(m.priority.ordinal); string(m.body) }
        writeBoolean(r.chatAppend != null)
        r.chatAppend?.let { string(it.conversationId); writeLong(it.offset); string(it.text) }
        writeBoolean(r.chatDelivered)
    }

    private fun DataInputStream.report(): StoredRoutineReport {
        val id = string(); val run = string(); val digest = string(); val result = string(); val at = readLong()
        val message = if (readBoolean()) {
            val messageId = string(); val from = string(); val to = string()
            val priority = readInt().also { require(it in AgentPriority.entries.indices) }
            AgentMessage(messageId, from, to, AgentPriority.entries[priority], string())
        } else null
        val append = if (readBoolean()) RoutineChatAppend(string(), readLong(), string()) else null
        return StoredRoutineReport(id, run, digest, message, result, at, append, readBoolean())
    }

    private fun DataOutputStream.string(value: String) {
        val bytes = value.toByteArray(Charsets.UTF_8)
        require(bytes.size <= MAX_STRING_BYTES)
        writeInt(bytes.size); write(bytes)
    }
    private fun DataOutputStream.optional(value: String?) { writeBoolean(value != null); value?.let { string(it) } }
    private fun DataInputStream.optional(): String? = if (readBoolean()) string() else null
    private fun DataInputStream.count(max: Int): Int = readInt().also { require(it in 0..max) }
    private fun DataInputStream.string(): String {
        val bytes = ByteArray(count(MAX_STRING_BYTES)); readFully(bytes)
        return Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString()
    }

    companion object {
        private val LOCK = Any()
        private val MAGIC = "CONTINUUM-ROUTINES-1\n".toByteArray(Charsets.US_ASCII)
        private const val MAX_BYTES = 8 * 1024 * 1024
        private const val MAX_STRING_BYTES = 128 * 1024
    }
}
