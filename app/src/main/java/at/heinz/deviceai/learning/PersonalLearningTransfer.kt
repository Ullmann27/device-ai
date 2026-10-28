package at.heinz.deviceai.learning

import java.io.InputStream
import java.io.InterruptedIOException
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.nio.ByteBuffer
import java.nio.CharBuffer
import java.nio.charset.CodingErrorAction
import java.security.MessageDigest

/** Portable learning records only: no weights, model files, settings, permissions or keys.
 * The checksum detects corruption; the claimed source package and trust labels are NOT
 * authenticated. Every proposed personal fact still requires explicit owner review.
 */
data class PersonalLearningBackup(
    val claimedSourcePackage: String,
    val records: List<LearningExperience>,
    val sha256: String
)

/** Read-only proposal. No method in this module writes a target ledger or grants consent.
 * Imported revocations dominate active records; a target revocation can never be
 * silently undone by an older or merely newer-looking backup.
 */
data class PersonalLearningMergePlan(
    val backup: PersonalLearningBackup,
    val additionsForReview: List<LearningExperience>,
    val revocationsForReview: List<String>,
    val blockedReactivations: List<String>,
    val duplicateCount: Int,
    val reviewOnlyCount: Int,
    val identityConflicts: List<String>,
    val capacityExceeded: Boolean
) {
    val requiresPerFactOwnerConfirmation: Boolean get() = true
    val canApplyAutomatically: Boolean get() = false
}

object PersonalLearningTransfer {
    const val SCHEMA_VERSION = 1
    const val MAX_BYTES = 1024 * 1024
    private const val MAGIC = 0x434c5054 // CLPT
    private const val DIGEST_BYTES = 32
    private const val MAX_FIELD_BYTES = 8192

    fun readBackup(input: InputStream): ByteArray {
        val out = ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        var emptyReads = 0
        while (true) {
            if (Thread.currentThread().isInterrupted) throw InterruptedIOException("Transfer interrupted")
            val count = input.read(buffer)
            if (count < 0) break
            if (count == 0) { require(++emptyReads <= 16) { "Transfer source made no progress" }; continue }
            emptyReads = 0
            require(out.size().toLong() + count <= MAX_BYTES) { "Transfer source exceeds limit" }
            out.write(buffer, 0, count)
        }
        return out.toByteArray()
    }

    fun export(sourcePackage: String, store: FileLearningExperienceStore): ByteArray =
        encode(sourcePackage, store.all())

    fun encode(sourcePackage: String, records: List<LearningExperience>): ByteArray {
        requirePackage(sourcePackage)
        require(records.size <= FileLearningExperienceStore.MAX_RECORDS)
        require(records.map { it.id }.distinct().size == records.size) { "Duplicate learning identity" }
        val payload = ByteArrayOutputStream().also { buffer ->
            DataOutputStream(buffer).use { out ->
                out.writeInt(MAGIC)
                out.writeInt(SCHEMA_VERSION)
                writeString(out, sourcePackage)
                out.writeInt(records.size)
                records.sortedBy { it.id }.forEach { item ->
                    writeString(out, item.id)
                    writeString(out, item.type.name)
                    writeString(out, item.provenance.name)
                    writeString(out, item.trust.name)
                    writeString(out, item.status.name)
                    writeString(out, item.content)
                    writeNullable(out, item.annotation)
                    writeNullable(out, item.sourceRef)
                    writeString(out, item.contentHash)
                    out.writeLong(item.createdAt)
                    out.writeLong(item.updatedAt)
                }
            }
        }.toByteArray()
        require(payload.size + DIGEST_BYTES <= MAX_BYTES) { "Personal learning backup exceeds limit" }
        return payload + digest(payload)
    }

    fun inspect(bytes: ByteArray): PersonalLearningBackup {
        require(bytes.size in (DIGEST_BYTES + 12)..MAX_BYTES) { "Invalid personal learning backup" }
        val payload = bytes.copyOfRange(0, bytes.size - DIGEST_BYTES)
        require(MessageDigest.isEqual(digest(payload), bytes.copyOfRange(payload.size, bytes.size))) {
            "Personal learning checksum mismatch"
        }
        try {
            val input = DataInputStream(ByteArrayInputStream(payload))
            require(input.readInt() == MAGIC && input.readInt() == SCHEMA_VERSION)
            val source = readString(input).also(::requirePackage)
            val count = input.readInt()
            require(count in 0..FileLearningExperienceStore.MAX_RECORDS)
            val records = List(count) {
                LearningExperience(
                    id = readString(input),
                    type = LearningExperienceType.valueOf(readString(input)),
                    provenance = LearningProvenance.valueOf(readString(input)),
                    trust = LearningTrustClass.valueOf(readString(input)),
                    status = LearningStatus.valueOf(readString(input)),
                    content = readString(input),
                    annotation = readNullable(input),
                    sourceRef = readNullable(input),
                    contentHash = readString(input),
                    createdAt = input.readLong(), updatedAt = input.readLong()
                )
            }
            require(input.available() == 0)
            require(records.map { it.id }.distinct().size == records.size)
            return PersonalLearningBackup(source, records.toList(), digest(bytes).hex())
        } catch (_: Exception) {
            // Never echo personal content or hostile field values in diagnostic logs.
            throw IllegalArgumentException("Invalid personal learning schema or record")
        }
    }

    fun preview(bytes: ByteArray, target: FileLearningExperienceStore): PersonalLearningMergePlan =
        preview(bytes, target.all())

    fun preview(bytes: ByteArray, targetRecords: List<LearningExperience>): PersonalLearningMergePlan {
        require(targetRecords.size <= FileLearningExperienceStore.MAX_RECORDS)
        require(targetRecords.map { it.id }.distinct().size == targetRecords.size)
        val backup = inspect(bytes)
        val target = targetRecords.associateBy { it.id }
        val additions = mutableListOf<LearningExperience>()
        val revocations = mutableListOf<String>()
        val blocked = mutableListOf<String>()
        val conflicts = mutableListOf<String>()
        var duplicates = 0
        var reviewOnly = 0
        backup.records.forEach { item ->
            val existing = target[item.id]
            if (existing != null && existing.contentHash != item.contentHash) {
                conflicts += item.id
            } else if (!isOwnerFact(item)) {
                // Training lessons, research observations, commands and audits cannot
                // acquire factual authority or execution rights through migration.
                reviewOnly++
            } else if (existing == null) {
                additions += item // Includes missing revocation tombstones.
            } else if (existing.status == LearningStatus.REVOKED && item.status != LearningStatus.REVOKED) {
                blocked += item.id
            } else if (item.status == LearningStatus.REVOKED && existing.status != LearningStatus.REVOKED) {
                revocations += item.id
            } else {
                duplicates++
            }
        }
        return PersonalLearningMergePlan(
            backup, additions.toList(), revocations.toList(), blocked.toList(), duplicates,
            reviewOnly, conflicts.toList(), targetRecords.size + additions.size > FileLearningExperienceStore.MAX_RECORDS
        )
    }

    private fun isOwnerFact(item: LearningExperience): Boolean =
        item.type == LearningExperienceType.USER_FACT &&
            item.provenance == LearningProvenance.USER_CONFIRMED &&
            item.trust == LearningTrustClass.OWNER_CONFIRMED &&
            item.annotation == null && item.sourceRef == null &&
            item.status in setOf(LearningStatus.CONFIRMED, LearningStatus.LEARNED,
                LearningStatus.REPLAY_PENDING, LearningStatus.REVOKED)

    private fun requirePackage(value: String) {
        require(value.length <= 256 && value.matches(Regex("[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z][A-Za-z0-9_]*)+"))) {
            "Invalid source package identifier"
        }
    }

    private fun writeString(out: DataOutputStream, value: String) {
        val bytes = try {
            val encoded = Charsets.UTF_8.newEncoder().onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT).encode(CharBuffer.wrap(value))
            ByteArray(encoded.remaining()).also { encoded.get(it) }
        } catch (_: Exception) {
            throw IllegalArgumentException("Invalid Unicode learning field")
        }
        require(bytes.size <= MAX_FIELD_BYTES)
        out.writeInt(bytes.size)
        out.write(bytes)
    }
    private fun readString(input: DataInputStream): String {
        val length = input.readInt()
        require(length in 0..MAX_FIELD_BYTES && length <= input.available())
        val bytes = ByteArray(length).also(input::readFully)
        return Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString()
    }
    private fun writeNullable(out: DataOutputStream, value: String?) {
        out.writeBoolean(value != null)
        if (value != null) writeString(out, value)
    }
    private fun readNullable(input: DataInputStream): String? = when (input.readUnsignedByte()) {
        0 -> null
        1 -> readString(input)
        else -> throw IllegalArgumentException("Invalid nullable field")
    }
    private fun digest(bytes: ByteArray): ByteArray = MessageDigest.getInstance("SHA-256").digest(bytes)
    private fun ByteArray.hex(): String = joinToString("") { "%02x".format(it) }
}
