package at.heinz.deviceai.cognitive

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.nio.channels.FileChannel
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption
import java.security.MessageDigest
import java.util.Locale

/**
 * App-private durable agent experience journal.
 *
 * Experiences are procedural evidence, never owner facts. Raw tool output is not
 * stored here; BoundedAgent already records only tool/source ids and hashes.
 */
internal class FileExperienceStore(private val directory: File) : ExperienceStore, ExperienceLookup {
    private val file = File(directory, FILE_NAME)

    override fun store(e: Experience) = guarded(write = true) {
        require(!containsSensitiveLiteral(e.requestSummary) &&
            !containsSensitiveLiteral(e.resultSummary) &&
            !e.failures.any(::containsSensitiveLiteral) &&
            !containsSensitiveLiteral(e.feedback.orEmpty())) {
            "Sensitive owner content is not persisted as agent experience."
        }
        val normalized = normalize(e)
        val records = readAll()
        val existing = records[normalized.id]
        if (existing != null) {
            require(existing == normalized) { "Conflicting experience identity." }
            return@guarded
        }
        records[normalized.id] = normalized
        while (records.size > MAX_RECORDS) {
            val victim = records.values.minWithOrNull(
                compareBy<Experience> { it.completedAt }.thenBy { it.startedAt }.thenBy { it.id }
            ) ?: break
            records.remove(victim.id)
        }
        writeAll(records)
    }

    override fun all(): List<Experience> = guarded {
        readAll().values.sortedWith(compareBy<Experience> { it.completedAt }.thenBy { it.id })
    }

    override fun similar(taskType: String, request: String, limit: Int): List<Experience> {
        require(limit >= 0)
        if (limit == 0) return emptyList()
        val query = terms(request)
        return all().asSequence()
            .filter { it.taskType.equals(taskType, ignoreCase = true) }
            .map { it to jaccard(query, terms(it.requestSummary)) }
            .filter { it.second > 0.0 || query.isEmpty() }
            .sortedWith(compareByDescending<Pair<Experience, Double>> { it.second }
                .thenByDescending { it.first.completedAt }.thenBy { it.first.id })
            .take(limit)
            .map { it.first }
            .toList()
    }

    private fun normalize(e: Experience): Experience {
        require(e.id.matches(ID))
        require(e.taskType.length in 1..MAX_TASK &&
            e.requestSummary.length in 1..MAX_SUMMARY &&
            e.planSummary.length <= MAX_SUMMARY &&
            e.resultSummary.length <= MAX_SUMMARY &&
            e.memoryIds.size <= MAX_LIST && e.modelIds.size <= MAX_LIST &&
            e.sourceIds.size <= MAX_LIST && e.toolIds.size <= MAX_LIST &&
            e.failures.size <= MAX_FAILURES && e.evaluatorScores.size <= MAX_SCORES &&
            e.feedback.orEmpty().length <= MAX_SUMMARY &&
            e.evaluation.notes.length <= MAX_SUMMARY)
        fun valid(items: List<String>) = items.none { it.isBlank() || it.length > MAX_ID_TEXT }
        require(valid(e.memoryIds) && valid(e.modelIds) && valid(e.sourceIds) && valid(e.toolIds))
        require(valid(e.failures))
        require(e.evaluatorScores.keys.all { it.matches(SCORE_KEY) })
        return e.copy(
            memoryIds = e.memoryIds.toList(),
            modelIds = e.modelIds.toList(),
            sourceIds = e.sourceIds.toList(),
            toolIds = e.toolIds.toList(),
            failures = e.failures.toList(),
            evaluatorScores = LinkedHashMap(e.evaluatorScores)
        )
    }

    private fun <T> guarded(write: Boolean = false, block: () -> T): T = synchronized(PROCESS_LOCK) {
        val root = directory.toPath()
        if (Files.exists(root, LinkOption.NOFOLLOW_LINKS) && Files.isSymbolicLink(root))
            throw IOException("Unsafe experience directory.")
        if (!directory.exists()) {
            if (!write) return@synchronized block()
            check(directory.mkdirs() || directory.isDirectory) { "Cannot create experience directory." }
        }
        require(Files.isDirectory(root, LinkOption.NOFOLLOW_LINKS))
        for (candidate in listOf(file.toPath(), File(directory, LOCK_NAME).toPath())) {
            if (Files.exists(candidate, LinkOption.NOFOLLOW_LINKS) &&
                !Files.isRegularFile(candidate, LinkOption.NOFOLLOW_LINKS))
                throw IOException("Experience files must be regular files.")
        }
        val lockPath = File(directory, LOCK_NAME).toPath()
        FileChannel.open(lockPath, StandardOpenOption.CREATE, StandardOpenOption.WRITE,
            LinkOption.NOFOLLOW_LINKS).use { channel ->
            val lock = channel.tryLock() ?: throw IOException("Experience store is busy.")
            lock.use { block() }
        }
    }

    private fun readAll(): LinkedHashMap<String, Experience> {
        if (!file.exists()) return linkedMapOf()
        require(file.length() in 1..MAX_FILE_BYTES.toLong()) { "Experience file size invalid." }
        return decode(file.readBytes())
    }

    private fun writeAll(records: Map<String, Experience>) {
        require(records.size <= MAX_RECORDS)
        val payload = encode(records.values.sortedWith(
            compareBy<Experience> { it.completedAt }.thenBy { it.id }))
        val envelope = ByteArrayOutputStream().also { bytes ->
            DataOutputStream(bytes).use { out ->
                out.writeUTF(MAGIC)
                out.writeInt(VERSION)
                out.writeInt(payload.size)
                out.write(payload)
                out.write(sha256(payload))
            }
        }.toByteArray()
        require(envelope.size <= MAX_FILE_BYTES)
        val tmp = File.createTempFile("agent-experience-", ".tmp", directory)
        try {
            FileOutputStream(tmp).use { output ->
                output.write(envelope)
                output.fd.sync()
            }
            try {
                Files.move(tmp.toPath(), file.toPath(),
                    StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
            } catch (_: java.nio.file.AtomicMoveNotSupportedException) {
                Files.move(tmp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING)
            }
            FileChannel.open(directory.toPath(), StandardOpenOption.READ).use { it.force(true) }
        } finally {
            tmp.delete()
        }
    }

    private fun encode(records: List<Experience>): ByteArray =
        ByteArrayOutputStream().also { bytes ->
            DataOutputStream(bytes).use { out ->
                out.writeInt(records.size)
                records.forEach { e ->
                    out.writeUTF(e.id)
                    out.writeUTF(e.taskType)
                    out.writeUTF(e.requestSummary)
                    writeStrings(out, e.memoryIds)
                    writeStrings(out, e.modelIds)
                    writeStrings(out, e.sourceIds)
                    writeStrings(out, e.toolIds)
                    out.writeUTF(e.planSummary)
                    out.writeUTF(e.resultSummary)
                    writeStrings(out, e.failures)
                    out.writeInt(e.retries)
                    out.writeInt(e.evaluatorScores.size)
                    e.evaluatorScores.toSortedMap().forEach { (key, value) ->
                        out.writeUTF(key); out.writeDouble(value)
                    }
                    out.writeBoolean(e.feedback != null)
                    e.feedback?.let(out::writeUTF)
                    out.writeDouble(e.confidenceBefore)
                    out.writeDouble(e.confidenceAfter)
                    out.writeUTF(e.outcome.name)
                    out.writeLong(e.startedAt)
                    out.writeLong(e.completedAt)
                    out.writeBoolean(e.evaluation.success)
                    out.writeDouble(e.evaluation.score)
                    out.writeUTF(e.evaluation.notes)
                }
            }
        }.toByteArray().also { require(it.size <= MAX_PAYLOAD_BYTES) }

    private fun decode(bytes: ByteArray): LinkedHashMap<String, Experience> = try {
        val input = DataInputStream(ByteArrayInputStream(bytes))
        require(input.readUTF() == MAGIC)
        require(input.readInt() == VERSION)
        val size = input.readInt()
        require(size in 1..MAX_PAYLOAD_BYTES && size <= input.available() - HASH_BYTES)
        val payload = ByteArray(size)
        input.readFully(payload)
        val checksum = ByteArray(HASH_BYTES)
        input.readFully(checksum)
        require(input.available() == 0 && MessageDigest.isEqual(checksum, sha256(payload))) {
            "Experience integrity check failed."
        }
        val data = DataInputStream(ByteArrayInputStream(payload))
        val count = data.readInt()
        require(count in 0..MAX_RECORDS)
        val records = linkedMapOf<String, Experience>()
        repeat(count) {
            val id = data.readUTF()
            val taskType = data.readUTF()
            val request = data.readUTF()
            val memoryIds = readStrings(data, MAX_LIST)
            val modelIds = readStrings(data, MAX_LIST)
            val sourceIds = readStrings(data, MAX_LIST)
            val toolIds = readStrings(data, MAX_LIST)
            val plan = data.readUTF()
            val result = data.readUTF()
            val failures = readStrings(data, MAX_FAILURES)
            val retries = data.readInt()
            val scoreCount = data.readInt()
            require(scoreCount in 0..MAX_SCORES)
            val scores = linkedMapOf<String, Double>()
            repeat(scoreCount) {
                val key = data.readUTF()
                require(key.matches(SCORE_KEY) && key !in scores)
                scores[key] = data.readDouble()
            }
            val feedback = if (data.readBoolean()) data.readUTF() else null
            val before = data.readDouble()
            val after = data.readDouble()
            val outcome = ExperienceOutcome.valueOf(data.readUTF())
            val started = data.readLong()
            val completed = data.readLong()
            val evaluation = Evaluation(data.readBoolean(), data.readDouble(), data.readUTF())
            val e = normalize(Experience(
                id, taskType, request, memoryIds, modelIds, sourceIds, toolIds,
                plan, result, failures, retries, scores, feedback, before, after,
                outcome, started, completed, evaluation
            ))
            require(records.put(id, e) == null) { "Duplicate experience id." }
        }
        require(data.available() == 0)
        records
    } catch (e: IOException) {
        throw IllegalArgumentException("Corrupted experience file.", e)
    }

    private fun writeStrings(out: DataOutputStream, values: List<String>) {
        out.writeInt(values.size)
        values.forEach(out::writeUTF)
    }

    private fun readStrings(input: DataInputStream, max: Int): List<String> {
        val count = input.readInt()
        require(count in 0..max)
        return List(count) { input.readUTF() }
    }

    private fun containsSensitiveLiteral(text: String): Boolean =
        text.isNotBlank() && SENSITIVE.containsMatchIn(text)

    private fun terms(text: String): Set<String> =
        text.lowercase(Locale.ROOT).split(Regex("[^\\p{L}\\p{N}]+"))
            .filter(String::isNotBlank).toSet()

    private fun jaccard(a: Set<String>, b: Set<String>): Double =
        if (a.isEmpty() && b.isEmpty()) 1.0
        else a.intersect(b).size.toDouble() / a.union(b).size

    companion object {
        private val PROCESS_LOCK = Any()
        private const val FILE_NAME = "agent-experience-v1.bin"
        private const val LOCK_NAME = ".agent-experience.lock"
        private const val MAGIC = "CONTINUUM_AGENT_EXPERIENCE"
        private const val VERSION = 1
        private const val MAX_RECORDS = 256
        private const val MAX_LIST = 32
        private const val MAX_FAILURES = 16
        private const val MAX_SCORES = 16
        private const val MAX_TASK = 96
        private const val MAX_SUMMARY = 512
        private const val MAX_ID_TEXT = 512
        private const val MAX_PAYLOAD_BYTES = 1024 * 1024
        private const val MAX_FILE_BYTES = MAX_PAYLOAD_BYTES + 128
        private const val HASH_BYTES = 32
        private val ID = Regex("[A-Za-z0-9._:-]{1,160}")
        private val SCORE_KEY = Regex("[A-Za-z0-9._:-]{1,96}")
        private val SENSITIVE = Regex(
            "(?iu)(?<![\\p{L}])(?:passwort|password|kennwort|pin|puk|iban|bic|" +
                "kreditkarte\\p{L}*|api[ _-]?key|secret|token|tan|zugangscode|geheim\\p{L}*)(?![\\p{L}])"
        )

        private fun sha256(bytes: ByteArray): ByteArray =
            MessageDigest.getInstance("SHA-256").digest(bytes)
    }
}
