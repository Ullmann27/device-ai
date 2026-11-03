package at.heinz.deviceai.learning

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption
import java.security.MessageDigest

/** Bounded storage implementation of the existing ledger, not a second fact system.
 * Immutable content-addressed segments are committed by ONE atomic primary-manifest rename.
 * The LEX3 magic deliberately occupies the LEX2 primary path: old readers must fail closed.
 * Checksums detect damage, not malicious rollback by a writer with filesystem access.
 */
internal class Lex3LearningExperienceStore(private val directory: File) {
    private val root = File(directory, "experiences-v3")
    private val primary = File(directory, "experiences-v2.bin")
    private data class Segment(val hash: String, val bytes: Int, val records: Int)

    fun exists(): Boolean = Files.exists(root.toPath(), LinkOption.NOFOLLOW_LINKS)

    fun snapshot(manifest: ByteArray, knownFingerprint: String? = null): Pair<String, List<LearningExperience>?> {
        requireDirectory(root)
        val segments = decodeManifest(manifest)
        val fingerprint = hash(manifest)
        val result = if (fingerprint == knownFingerprint) null else ArrayList<LearningExperience>()
        // Even cache hits hash each bounded segment: same-size/mtime corruption cannot hide.
        for (segment in segments) {
            val bytes = readBounded(File(root, "${segment.hash}.seg"), segment.bytes)
            require(bytes.size == segment.bytes && hash(bytes) == segment.hash) { "LEX3 segment integrity failure" }
            if (result != null) {
                val records = LearningExperienceCodec.decode(bytes)
                require(records.size == segment.records) { "LEX3 segment count mismatch" }
                result.addAll(records)
            }
        }
        if (result != null) {
            require(result.map { it.id }.distinct().size == result.size) { "LEX3 duplicate identity" }
            require(result == result.sortedWith(RECORD_ORDER)) { "LEX3 record order invalid" }
        }
        return fingerprint to result
    }

    /** Caller holds the store's JVM + OS lock and has validated the current complete ledger.
     * [beforeCommit] is an internal fault-injection seam, never used by runtime callers.
     */
    fun save(items: List<LearningExperience>, previous: ByteArray?, beforeCommit: () -> Unit = {}, afterCommit: () -> Unit = {}) {
        require(items.size <= MAX_RECORDS && items.map { it.id }.distinct().size == items.size)
        val encoded = items.sortedWith(RECORD_ORDER).chunked(SEGMENT_RECORDS).map(LearningExperienceCodec::encode)
        require(encoded.sumOf { it.size.toLong() } <= MAX_TOTAL_BYTES) { "LEX3 byte capacity reached" }
        val descriptors = encoded.mapIndexed { index, bytes ->
            Segment(hash(bytes), bytes.size, minOf(SEGMENT_RECORDS, items.size-index*SEGMENT_RECORDS))
        }
        val manifest = encodeManifest(descriptors)
        if (!root.exists()) check(root.mkdir()) { "Cannot create LEX3 directory" }
        requireDirectory(root)
        // Keep originals byte-for-byte. Never load these as an automatic fallback.
        if (previous != null && !isManifest(previous)) {
            LearningExperienceCodec.decode(previous)
            val original = File(root,"legacy-v2.bin")
            if (original.exists()) {
                // A pre-commit crash may leave an earlier migration attempt. Preserve each original.
                if (!readBounded(original,LearningExperienceCodec.MAX_BYTES).contentEquals(previous)) {
                    writeImmutable(File(root,"legacy-${hash(previous)}.bin"), previous)
                }
            } else writeImmutable(original,previous)
        }
        val prior = if (previous != null && isManifest(previous)) decodeManifest(previous).map { it.hash }.toSet() else emptySet()
        // Clear only unreferenced segment artifacts from interrupted writes, under the same OS lock.
        cleanup(prior)
        encoded.forEachIndexed { index, bytes -> writeImmutable(File(root,"${descriptors[index].hash}.seg"),bytes) }
        syncDirectory(root)
        beforeCommit()
        writeAtomic(primary,manifest)
        afterCommit()
        // A failure here reports an uncertain commit. Retry through the store is idempotent; never roll back.
        syncDirectory(directory)
        // Cleanup is advisory after the commit point; its failure cannot change the committed result.
        runCatching { cleanup(descriptors.map { it.hash }.toSet()); syncDirectory(root) }
    }

    private fun cleanup(keep: Set<String>) {
        Files.newDirectoryStream(root.toPath(),"*.seg").use { paths ->
            for (path in paths) {
                val name = path.fileName.toString()
                if (name.matches(Regex("[a-f0-9]{64}\\.seg")) && name.removeSuffix(".seg") !in keep) {
                    requireRegular(path.toFile())
                    Files.delete(path)
                }
            }
        }
    }

    private fun writeImmutable(file: File, bytes: ByteArray) {
        if (Files.exists(file.toPath(),LinkOption.NOFOLLOW_LINKS)) {
            require(readBounded(file,LearningExperienceCodec.MAX_BYTES).contentEquals(bytes)) { "LEX3 immutable file conflict" }
        } else writeAtomic(file,bytes)
    }

    private fun encodeManifest(segments: List<Segment>): ByteArray {
        val payload = ByteArrayOutputStream().also { buffer ->
            DataOutputStream(buffer).use { out ->
                out.writeInt(MAGIC); out.writeInt(VERSION)
                out.writeInt(segments.sumOf { it.records }); out.writeInt(segments.size)
                segments.forEach { out.writeUTF(it.hash); out.writeInt(it.bytes); out.writeInt(it.records) }
            }
        }.toByteArray()
        return payload + digest(payload)
    }

    private fun decodeManifest(bytes: ByteArray): List<Segment> {
        require(bytes.size in 48..MAX_MANIFEST_BYTES) { "Invalid LEX3 manifest length" }
        val payload = bytes.copyOfRange(0,bytes.size-32)
        require(MessageDigest.isEqual(digest(payload),bytes.copyOfRange(payload.size,bytes.size))) { "LEX3 manifest checksum mismatch" }
        val input = DataInputStream(ByteArrayInputStream(payload))
        require(input.readInt()==MAGIC && input.readInt()==VERSION) { "Unsupported LEX3 manifest" }
        val count = input.readInt().also { require(it in 0..MAX_RECORDS) }
        val segmentCount = input.readInt().also { require(it in 0..MAX_SEGMENTS) }
        val segments = List(segmentCount) {
            Segment(input.readUTF().also { require(it.matches(Regex("[a-f0-9]{64}"))) },
                input.readInt().also { require(it in 40..LearningExperienceCodec.MAX_BYTES) },
                input.readInt().also { require(it in 1..SEGMENT_RECORDS) })
        }
        require(input.available()==0 && segments.sumOf { it.records }==count)
        require(segments.map { it.hash }.distinct().size==segments.size)
        require(segments.sumOf { it.bytes.toLong() } <= MAX_TOTAL_BYTES)
        return segments
    }

    companion object {
        const val MAX_RECORDS = 4096
        const val MAX_TOTAL_BYTES = 32L * 1024 * 1024
        const val SEGMENT_RECORDS = 32
        private const val MAX_SEGMENTS = MAX_RECORDS / SEGMENT_RECORDS
        private const val MAX_MANIFEST_BYTES = 16 * 1024
        private const val MAGIC = 0x4c455833
        private const val VERSION = 1
        val RECORD_ORDER = compareBy<LearningExperience> { it.createdAt }.thenBy { it.id }

        fun isManifest(bytes: ByteArray): Boolean = bytes.size >= 4 && ByteBuffer.wrap(bytes,0,4).int==MAGIC
        private fun digest(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes)
        private fun hash(bytes: ByteArray) = digest(bytes).joinToString("") { "%02x".format(it) }

        fun requireDirectory(file: File) {
            require(!Files.isSymbolicLink(file.toPath()) && Files.isDirectory(file.toPath(),LinkOption.NOFOLLOW_LINKS)) {
                "Unsafe learning ledger directory"
            }
        }
        fun requireRegular(file: File) {
            require(!Files.isSymbolicLink(file.toPath()) && Files.isRegularFile(file.toPath(),LinkOption.NOFOLLOW_LINKS)) {
                "Unsafe or missing learning ledger file"
            }
        }
        fun readBounded(file: File, maxBytes: Int): ByteArray {
            requireRegular(file)
            require(file.length() in 1..maxBytes.toLong()) { "Learning ledger file exceeds limit" }
            return Files.newInputStream(file.toPath(),LinkOption.NOFOLLOW_LINKS).use { input ->
                val out=ByteArrayOutputStream()
                val buffer=ByteArray(8192)
                while(true) {
                    val count=input.read(buffer)
                    if(count<0)break
                    require(out.size().toLong()+count<=maxBytes) { "Learning ledger file grew beyond limit" }
                    out.write(buffer,0,count)
                }
                out.toByteArray()
            }
        }
        private fun writeAtomic(target: File, bytes: ByteArray) {
            requireDirectory(target.parentFile!!)
            if (Files.exists(target.toPath(),LinkOption.NOFOLLOW_LINKS)) requireRegular(target)
            val temp=File.createTempFile("lex3-", ".tmp",target.parentFile)
            try {
                FileOutputStream(temp).use { it.write(bytes);it.fd.sync() }
                // No non-atomic fallback: unsupported filesystems must keep the old ledger.
                Files.move(temp.toPath(),target.toPath(),StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING)
            } finally { temp.delete() }
        }
        private fun syncDirectory(file: File) {
            FileChannel.open(file.toPath(),StandardOpenOption.READ).use { it.force(true) }
        }
    }
}
