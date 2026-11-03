package at.heinz.deviceai

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.nio.file.LinkOption
import java.nio.file.StandardOpenOption
import java.nio.channels.FileChannel
import java.security.MessageDigest

enum class ToolReceiptState {
    PROPOSED, APPROVED, STARTED, SUCCEEDED, FAILED, TIMED_OUT, CANCELLED, REJECTED,
    START_PENDING, DISPATCH_COMMITTED, UNKNOWN_AFTER_DISPATCH
}

data class ToolReceiptTransition(val state: ToolReceiptState, val atMillis: Long) {
    init { require(atMillis >= 0) }
}

data class ToolExecutionReceipt(
    val callId: String,
    val toolName: String,
    val argumentFingerprint: String,
    val sanitizedArguments: Map<String, Any?>,
    val risk: Risk,
    val createdAtMillis: Long,
    val transitions: List<ToolReceiptTransition>,
    val outputLength: Int = 0,
    val outputSha256: String? = null
) {
    val state: ToolReceiptState get() = transitions.last().state
    /** STARTED is a legacy ambiguous boundary; new calls journal commitment explicitly. */
    val outcomeUncertain: Boolean get() = state != ToolReceiptState.SUCCEEDED &&
        transitions.any { it.state in setOf(ToolReceiptState.STARTED,
            ToolReceiptState.DISPATCH_COMMITTED, ToolReceiptState.UNKNOWN_AFTER_DISPATCH) }

    init {
        require(callId.matches(Regex("[A-Za-z0-9._:-]{1,128}")))
        require(ToolRegistry.get(toolName) != null)
        require(argumentFingerprint.matches(Regex("[a-f0-9]{64}")))
        require(createdAtMillis >= 0 && transitions.isNotEmpty() && transitions.size <= 8)
        require(transitions.first() == ToolReceiptTransition(ToolReceiptState.PROPOSED, createdAtMillis))
        require(transitions.zipWithNext().all { (a, b) ->
            a.atMillis <= b.atMillis && allowedTransition(a.state, b.state)
        })
        require(sanitizedArguments.size <= 16 && sanitizedArguments.keys.all {
            it.matches(Regex("[A-Za-z][A-Za-z0-9_]{0,63}"))
        })
        require(sanitizedArguments.values.all {
            it == null || it is Boolean || it is Int || it is Long || it is String && it.length <= 4_096
        })
        require(ToolRegistry.get(toolName)?.risk == risk)
        require(outputLength in 0..MAX_OUTPUT_LENGTH)
        require((outputSha256 == null) == (outputLength == 0))
        require(outputSha256 == null || outputSha256.matches(Regex("[a-f0-9]{64}")))
        require(state == ToolReceiptState.SUCCEEDED || outputSha256 == null)
    }

    companion object {
        private const val MAX_OUTPUT_LENGTH = 60_000
    }
}

interface ToolExecutionReceiptStore {
    fun get(callId: String): ToolExecutionReceipt?
    fun propose(receipt: ToolExecutionReceipt): ToolExecutionReceipt
    fun transition(
        callId: String,
        expected: ToolReceiptState,
        next: ToolReceiptState,
        atMillis: Long,
        outputLength: Int = 0,
        outputSha256: String? = null
    ): ToolExecutionReceipt?
}

class FileToolExecutionReceiptStore(private val directory: File) : ToolExecutionReceiptStore {
    private val file = File(directory, FILE_NAME)

    fun all(): List<ToolExecutionReceipt> = guarded { readAll().values.toList() }

    override fun get(callId: String): ToolExecutionReceipt? = guarded {
        require(validCallId(callId))
        readAll()[callId]
    }

    override fun propose(receipt: ToolExecutionReceipt): ToolExecutionReceipt = guarded {
        require(receipt.state == ToolReceiptState.PROPOSED)
        val records = readAll()
        val existing = records[receipt.callId]
        if (existing != null) {
            require(existing.toolName == receipt.toolName &&
                existing.argumentFingerprint == receipt.argumentFingerprint) {
                "Tool call id was reused with different arguments."
            }
            return@guarded existing
        }
        evictOldestCertainTerminal(records)
        require(records.size < MAX_RECEIPTS) { "Tool receipt store is full; operator cleanup is required." }
        records[receipt.callId] = receipt
        writeAll(records)
        receipt
    }

    override fun transition(
        callId: String,
        expected: ToolReceiptState,
        next: ToolReceiptState,
        atMillis: Long,
        outputLength: Int,
        outputSha256: String?
    ): ToolExecutionReceipt? = guarded {
        require(allowedTransition(expected, next) && atMillis >= 0)
        val records = readAll()
        val current = records[callId] ?: return@guarded null
        if (current.state != expected) return@guarded null
        val updated = current.copy(
            transitions = current.transitions + ToolReceiptTransition(next, atMillis),
            outputLength = outputLength,
            outputSha256 = outputSha256
        )
        records[callId] = updated
        writeAll(records)
        updated
    }

    /**
     * Bounded retention. Without it the 512-receipt cap turns into a permanent
     * ToolGate outage after 512 tool calls. Only receipts whose outcome is
     * certainly terminal are dropped, oldest first. PROPOSED/APPROVED/
     * START_PENDING and every outcome-UNKNOWN receipt stay for operator review
     * and replay blocking; a journal full of those still fails closed.
     */
    private fun evictOldestCertainTerminal(records: LinkedHashMap<String, ToolExecutionReceipt>) {
        val excess = records.size - MAX_RECEIPTS + 1
        if (excess <= 0) return
        records.values
            .filter { it.state in CERTAIN_TERMINAL_STATES && !it.outcomeUncertain }
            .sortedWith(compareBy<ToolExecutionReceipt> { it.transitions.last().atMillis }.thenBy { it.callId })
            .take(excess)
            .forEach { records.remove(it.callId) }
    }

    /** One transaction across instances/processes; never fall back to unlocked I/O. */
    private fun <T> guarded(operation: () -> T): T = synchronized(PROCESS_MONITOR) {
        val root=directory.toPath()
        if (Files.isSymbolicLink(root) || Files.isSymbolicLink(file.toPath()))
            throw IOException("Unsafe tool receipt path.")
        if (!directory.exists() && !directory.mkdirs() && !directory.isDirectory)
            throw IOException("Cannot create tool receipt directory.")
        val lockPath=File(directory,".tool-receipts.lock").toPath()
        for (path in listOf(file.toPath(),lockPath)) {
            if (Files.exists(path,LinkOption.NOFOLLOW_LINKS) &&
                !Files.isRegularFile(path,LinkOption.NOFOLLOW_LINKS))
                throw IOException("Tool receipt and lock must be regular files.")
        }
        FileChannel.open(lockPath,
            StandardOpenOption.CREATE,StandardOpenOption.WRITE,LinkOption.NOFOLLOW_LINKS).use { channel ->
            val lock=channel.tryLock() ?: throw IOException("Tool receipt store is busy; execution blocked.")
            lock.use { operation() }
        }
    }

    private fun readAll(): LinkedHashMap<String, ToolExecutionReceipt> {
        if (!file.isFile) return linkedMapOf()
        require(file.length() in 1..MAX_FILE_BYTES.toLong()) { "Tool receipt file is oversized or empty." }
        return decode(file.readBytes())
    }

    private fun writeAll(records: Map<String, ToolExecutionReceipt>) {
        require(records.size <= MAX_RECEIPTS)
        if (!directory.exists() && !directory.mkdirs()) throw IOException("Cannot create tool receipt directory.")
        val payload = encode(records)
        val envelope = ByteArrayOutputStream().also { buffer ->
            DataOutputStream(buffer).use { out ->
                out.writeUTF(MAGIC)
                out.writeInt(SCHEMA_VERSION)
                out.writeInt(payload.size)
                out.write(payload)
                out.write(sha256(payload))
            }
        }.toByteArray()
        require(envelope.size <= MAX_FILE_BYTES)
        val temporary = File.createTempFile("tool-receipts-", ".tmp", directory)
        try {
            FileOutputStream(temporary).use { output ->
                output.write(envelope)
                output.fd.sync()
            }
            try {
                Files.move(
                    temporary.toPath(), file.toPath(),
                    StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING
                )
            } catch (_: AtomicMoveNotSupportedException) {
                Files.move(temporary.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING)
            }
            FileChannel.open(directory.toPath(),StandardOpenOption.READ).use { it.force(true) }
        } finally {
            temporary.delete()
        }
    }

    private fun encode(records: Map<String, ToolExecutionReceipt>): ByteArray =
        ByteArrayOutputStream().also { buffer ->
            DataOutputStream(buffer).use { out ->
                out.writeInt(records.size)
                records.toSortedMap().values.forEach { receipt ->
                    out.writeUTF(receipt.callId)
                    out.writeUTF(receipt.toolName)
                    out.writeUTF(receipt.argumentFingerprint)
                    out.writeUTF(receipt.risk.name)
                    out.writeLong(receipt.createdAtMillis)
                    out.writeInt(receipt.sanitizedArguments.size)
                    receipt.sanitizedArguments.toSortedMap().forEach { (key, value) ->
                        out.writeUTF(key)
                        when (value) {
                            null -> out.writeByte(0)
                            is Boolean -> { out.writeByte(1); out.writeBoolean(value) }
                            is Int -> { out.writeByte(2); out.writeInt(value) }
                            is Long -> { out.writeByte(3); out.writeLong(value) }
                            is String -> { out.writeByte(4); out.writeUTF(value) }
                            else -> throw IllegalArgumentException("Unsupported sanitized argument value.")
                        }
                    }
                    out.writeInt(receipt.transitions.size)
                    receipt.transitions.forEach {
                        out.writeUTF(it.state.name)
                        out.writeLong(it.atMillis)
                    }
                    out.writeInt(receipt.outputLength)
                    out.writeBoolean(receipt.outputSha256 != null)
                    receipt.outputSha256?.let(out::writeUTF)
                }
            }
        }.toByteArray().also { require(it.size <= MAX_PAYLOAD_BYTES) }

    private fun decode(bytes: ByteArray): LinkedHashMap<String, ToolExecutionReceipt> = try {
        val input = DataInputStream(ByteArrayInputStream(bytes))
        require(input.readUTF() == MAGIC) { "Invalid tool receipt file." }
        require(input.readInt() == SCHEMA_VERSION) { "Unsupported tool receipt schema." }
        val size = input.readInt()
        require(size in 1..MAX_PAYLOAD_BYTES)
        require(size <= input.available() - CHECKSUM_BYTES) { "Truncated tool receipt file." }
        val payload = ByteArray(size)
        input.readFully(payload)
        val checksum = ByteArray(CHECKSUM_BYTES)
        input.readFully(checksum)
        require(input.available() == 0 && MessageDigest.isEqual(checksum, sha256(payload))) {
            "Tool receipt integrity check failed."
        }
        val recordsIn = DataInputStream(ByteArrayInputStream(payload))
        val count = recordsIn.readInt()
        require(count in 0..MAX_RECEIPTS)
        val records = linkedMapOf<String, ToolExecutionReceipt>()
        repeat(count) {
            val callId = recordsIn.readUTF()
            val toolName = recordsIn.readUTF()
            val fingerprint = recordsIn.readUTF()
            val risk = Risk.valueOf(recordsIn.readUTF())
            val created = recordsIn.readLong()
            val argumentCount = recordsIn.readInt()
            require(argumentCount in 0..16)
            val arguments = linkedMapOf<String, Any?>()
            repeat(argumentCount) {
                val key = recordsIn.readUTF()
                val value: Any? = when (recordsIn.readUnsignedByte()) {
                    0 -> null
                    1 -> recordsIn.readBoolean()
                    2 -> recordsIn.readInt()
                    3 -> recordsIn.readLong()
                    4 -> recordsIn.readUTF().also { require(it.length <= 4_096) }
                    else -> throw IllegalArgumentException("Unknown receipt argument type.")
                }
                require(key !in arguments) { "Duplicate tool receipt argument." }
                arguments[key] = value
            }
            val transitionCount = recordsIn.readInt()
            require(transitionCount in 1..8)
            val transitions = List(transitionCount) {
                ToolReceiptTransition(ToolReceiptState.valueOf(recordsIn.readUTF()), recordsIn.readLong())
            }
            val outputLength = recordsIn.readInt()
            val outputHash = if (recordsIn.readBoolean()) recordsIn.readUTF() else null
            val receipt = ToolExecutionReceipt(
                callId, toolName, fingerprint, arguments, risk, created, transitions, outputLength, outputHash
            )
            require(records.put(callId, receipt) == null) { "Duplicate tool receipt id." }
        }
        require(recordsIn.available() == 0) { "Unexpected trailing tool receipt data." }
        records
    } catch (e: IOException) {
        throw IllegalArgumentException("Corrupted tool receipt file.", e)
    }

    companion object {
        private val PROCESS_MONITOR = Any()
        private val CERTAIN_TERMINAL_STATES = setOf(
            ToolReceiptState.SUCCEEDED, ToolReceiptState.FAILED, ToolReceiptState.TIMED_OUT,
            ToolReceiptState.CANCELLED, ToolReceiptState.REJECTED
        )
        private const val FILE_NAME = "tool-execution-receipts.bin"
        private const val MAGIC = "DEVICE_AI_TOOL_RECEIPTS"
        private const val SCHEMA_VERSION = 1
        private const val MAX_RECEIPTS = 512
        private const val MAX_PAYLOAD_BYTES = 256 * 1024
        private const val MAX_FILE_BYTES = MAX_PAYLOAD_BYTES + 128
        private const val CHECKSUM_BYTES = 32
    }
}

internal fun allowedTransition(from: ToolReceiptState, to: ToolReceiptState): Boolean = when (from) {
    ToolReceiptState.PROPOSED -> to in setOf(ToolReceiptState.APPROVED, ToolReceiptState.REJECTED, ToolReceiptState.CANCELLED)
    ToolReceiptState.APPROVED -> to in setOf(ToolReceiptState.STARTED, ToolReceiptState.START_PENDING,
        ToolReceiptState.REJECTED, ToolReceiptState.CANCELLED)
    ToolReceiptState.START_PENDING -> to in setOf(ToolReceiptState.DISPATCH_COMMITTED,
        ToolReceiptState.FAILED, ToolReceiptState.TIMED_OUT, ToolReceiptState.CANCELLED,
        ToolReceiptState.UNKNOWN_AFTER_DISPATCH)
    ToolReceiptState.DISPATCH_COMMITTED -> to in setOf(ToolReceiptState.SUCCEEDED, ToolReceiptState.UNKNOWN_AFTER_DISPATCH)
    ToolReceiptState.STARTED -> to in setOf(
        ToolReceiptState.SUCCEEDED, ToolReceiptState.FAILED, ToolReceiptState.TIMED_OUT, ToolReceiptState.CANCELLED
    )
    else -> false
}

internal fun toolArgumentFingerprint(arguments: Map<String, Any?>): String {
    val canonical = arguments.toSortedMap().entries.joinToString("\n") { (key, value) ->
        val safeKey = key.lowercase()
        val safeValue = when {
            safeKey.contains(Regex("password|token|secret|credential|authorization")) -> "[REDACTED]"
            value is String -> "string:${value.sha256Hex()}"
            value is List<*> -> "list:${value.joinToString(";") { item ->
                if (item is String) "${item.length}:${item.sha256Hex()}" else item.toString()
            }.sha256Hex()}"
            else -> value.toString()
        }
        "$key=$safeValue"
    }
    return sha256(canonical.toByteArray()).joinToString("") { "%02x".format(it) }
}

internal fun validCallId(callId: String) = callId.matches(Regex("[A-Za-z0-9._:-]{1,128}"))

internal fun ToolReceiptState.toExecutionStatus(): ToolExecutionStatus = when (this) {
    ToolReceiptState.SUCCEEDED -> ToolExecutionStatus.SUCCEEDED
    ToolReceiptState.FAILED -> ToolExecutionStatus.FAILED
    ToolReceiptState.TIMED_OUT -> ToolExecutionStatus.TIMED_OUT
    ToolReceiptState.CANCELLED -> ToolExecutionStatus.CANCELLED
    else -> ToolExecutionStatus.REJECTED
}

private fun sha256(bytes: ByteArray): ByteArray = MessageDigest.getInstance("SHA-256").digest(bytes)
private fun String.sha256Hex() =
    sha256(toByteArray()).joinToString("") { "%02x".format(it) }
