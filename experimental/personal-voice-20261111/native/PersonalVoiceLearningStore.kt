package at.heinz.deviceai.research.voice

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.StandardCopyOption
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Two independent, default-off choices; this pilot never retains raw audio. */
data class VoiceLearningConsent(val textTraining: Boolean = false, val audioRetention: Boolean = false)

/** A local protected draft. Recognition is observation; two confirmation flags are independent. */
class PersonalVoiceExample(
    val sessionId: String,
    val recognizedTranscript: String,
    val correctedTranscript: String?,
    val transcriptConfirmed: Boolean,
    val intendedInstruction: String,
    val meaningConfirmed: Boolean,
    ownerContext: List<String>,
    previousTaskReferences: List<String>,
    val desiredOutcome: String,
    forbiddenInterpretations: List<String>,
    val sourceReference: String,
    val ownerOnly: Boolean,
    val sensitiveContentReviewed: Boolean,
    val sensitiveContentApproved: Boolean
) {
    val ownerContext = ownerContext.toList()
    val previousTaskReferences = previousTaskReferences.toList()
    val forbiddenInterpretations = forbiddenInterpretations.toList()
    val digest: String get() = VoiceDigests.fields(listOf(sessionId, recognizedTranscript,
        correctedTranscript.orEmpty(), transcriptConfirmed.toString(), intendedInstruction,
        meaningConfirmed.toString(), VoiceDigests.fields(ownerContext), VoiceDigests.fields(previousTaskReferences),
        desiredOutcome, VoiceDigests.fields(forbiddenInterpretations), sourceReference, ownerOnly.toString(),
        sensitiveContentReviewed.toString(), sensitiveContentApproved.toString()))
}

/** Content-bound approval is issued only by an explicit review action, not model output. */
class VoiceTrainingApproval(val exampleDigest: String, val approvedByOwner: Boolean)
enum class VoiceStageResult { STORED, DUPLICATE, CONSENT_REQUIRED, REVIEW_REQUIRED, INVALID, CAPACITY_REACHED }

/**
 * Review staging for existing learning components, never a replacement fact ledger.
 * AES-256-GCM (injected key), owner-only directory, bounded files, authenticated IDs,
 * no export API and no training scheduler. Android supplies a dedicated Keystore key
 * and a no-backup app-private directory through AndroidPersonalVoiceStore.
 */
class PersonalVoiceLearningStore(private val directory: File, private val key: SecretKey) {
    private var consent = VoiceLearningConsent()

    @Synchronized fun setConsent(value: VoiceLearningConsent) { consent = value }
    @Synchronized fun consent(): VoiceLearningConsent = consent.copy()

    @Synchronized fun stage(example: PersonalVoiceExample, approval: VoiceTrainingApproval): VoiceStageResult {
        if (!consent.textTraining) return VoiceStageResult.CONSENT_REQUIRED
        if (!valid(example)) return VoiceStageResult.INVALID
        if (!example.meaningConfirmed || !example.ownerOnly || !example.sensitiveContentReviewed ||
            !example.sensitiveContentApproved || !approval.approvedByOwner || approval.exampleDigest != example.digest)
            return VoiceStageResult.REVIEW_REQUIRED
        val id = example.digest
        ensureDirectory()
        val target = file(id)
        check(!Files.isSymbolicLink(target.toPath())) { "VOICE_STORE_INVALID_FILE" }
        if (target.exists()) {
            // Do not hide corruption by treating an unauthenticated file as a duplicate.
            check(inspect(id).digest == id) { "VOICE_STORE_INTEGRITY" }
            return VoiceStageResult.DUPLICATE
        }
        if (ids().size >= MAX_RECORDS) return VoiceStageResult.CAPACITY_REACHED
        val encoded = encode(example)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key)
        cipher.updateAAD(id.toByteArray(Charsets.US_ASCII))
        val encrypted = cipher.doFinal(encoded)
        val envelope = ByteArrayOutputStream()
        DataOutputStream(envelope).use { out ->
            out.writeInt(MAGIC); out.writeInt(VERSION); out.writeInt(cipher.iv.size)
            out.write(cipher.iv); out.writeInt(encrypted.size); out.write(encrypted)
        }
        val temporary = Files.createTempFile(directory.toPath(), ".voice-", ".pending").toFile()
        try {
            FileOutputStream(temporary).use { out -> out.write(envelope.toByteArray()); out.fd.sync() }
            check(temporary.setReadable(false, false) && temporary.setWritable(false, false) &&
                temporary.setReadable(true, true) && temporary.setWritable(true, true)) { "VOICE_STORE_PERMISSIONS" }
            Files.move(temporary.toPath(), target.toPath(), StandardCopyOption.ATOMIC_MOVE)
        } finally { temporary.delete() }
        return VoiceStageResult.STORED
    }

    /** A UI may show these local entries, edit one, obtain fresh approval, and replace it. */
    @Synchronized fun ids(): List<String> {
        if (!directory.exists()) return emptyList()
        ensureDirectory()
        return directory.listFiles().orEmpty().filter { it.name.matches(FILE_ID) }
            .map { it.name.removeSuffix(".pv1") }.sorted().also {
                check(it.size <= MAX_RECORDS) { "VOICE_STORE_CAPACITY" }
            }
    }

    @Synchronized fun inspect(id: String): PersonalVoiceExample {
        val target = file(id)
        ensureDirectory()
        check(Files.isRegularFile(target.toPath(), LinkOption.NOFOLLOW_LINKS) && target.length() in 1..MAX_FILE_BYTES)
            { "VOICE_STORE_INVALID_FILE" }
        try {
            val input = DataInputStream(ByteArrayInputStream(target.readBytes()))
            check(input.readInt() == MAGIC && input.readInt() == VERSION)
            val ivSize = input.readInt(); check(ivSize == 12)
            val iv = ByteArray(ivSize).also(input::readFully)
            val size = input.readInt(); check(size in 16..MAX_FILE_BYTES && size == input.available())
            val encrypted = ByteArray(size).also(input::readFully)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, iv))
            cipher.updateAAD(id.toByteArray(Charsets.US_ASCII))
            return decode(cipher.doFinal(encrypted)).also { check(valid(it) && it.digest == id) }
        } catch (_: Exception) { throw IllegalStateException("VOICE_STORE_INTEGRITY") }
    }

    /** Explicit UI deletion only; deleting data does not unlearn an already trained model. */
    @Synchronized fun delete(id: String): Boolean {
        ensureDirectory()
        return Files.deleteIfExists(file(id).toPath())
    }

    @Synchronized fun replace(id: String, revised: PersonalVoiceExample, approval: VoiceTrainingApproval): VoiceStageResult {
        inspect(id)
        val status = stage(revised, approval)
        if ((status == VoiceStageResult.STORED || status == VoiceStageResult.DUPLICATE) && revised.digest != id) delete(id)
        return status
    }

    /** A snapshot is for a later controlled LOCAL training batch; never an automatic update. */
    @Synchronized fun reviewedBatch(minimumExamples: Int = 32): List<PersonalVoiceExample> {
        require(minimumExamples in 2..MAX_RECORDS)
        if (!consent.textTraining) return emptyList()
        val examples = ids().map(::inspect)
        if (examples.size < minimumExamples) return emptyList()
        return examples.filter { it.meaningConfirmed && it.ownerOnly && it.sensitiveContentReviewed && it.sensitiveContentApproved }
    }

    private fun file(id: String): File {
        require(id.matches(DIGEST)) { "VOICE_STORE_INVALID_ID" }
        return File(directory, "$id.pv1")
    }
    private fun ensureDirectory() {
        check(!Files.isSymbolicLink(directory.toPath())) { "VOICE_STORE_DIRECTORY" }
        if (!directory.exists()) check(directory.mkdirs()) { "VOICE_STORE_DIRECTORY" }
        check(directory.isDirectory && directory.canonicalFile == directory.absoluteFile) { "VOICE_STORE_DIRECTORY" }
        check(directory.setReadable(false, false) && directory.setWritable(false, false) &&
            directory.setExecutable(false, false) && directory.setReadable(true, true) &&
            directory.setWritable(true, true) && directory.setExecutable(true, true)) { "VOICE_STORE_PERMISSIONS" }
    }
    private fun valid(e: PersonalVoiceExample): Boolean {
        val values = listOf(e.sessionId, e.recognizedTranscript, e.intendedInstruction, e.desiredOutcome, e.sourceReference) +
            listOfNotNull(e.correctedTranscript) + e.ownerContext + e.previousTaskReferences + e.forbiddenInterpretations
        return e.sessionId.length in 1..160 && values.all { it.isNotBlank() && it.length <= 8192 && '\u0000' !in it } &&
            e.ownerContext.size <= 4 && e.previousTaskReferences.size <= 16 && e.forbiddenInterpretations.size <= 16 &&
            (e.correctedTranscript == null) == !e.transcriptConfirmed &&
            values.sumOf { it.toByteArray(Charsets.UTF_8).size } <= 48_000
    }
    private fun encode(e: PersonalVoiceExample): ByteArray {
        val buffer = ByteArrayOutputStream()
        DataOutputStream(buffer).use { out ->
            fun list(values: List<String>) { out.writeInt(values.size); values.forEach(out::writeUTF) }
            out.writeUTF(e.sessionId); out.writeUTF(e.recognizedTranscript)
            out.writeBoolean(e.correctedTranscript != null); e.correctedTranscript?.let(out::writeUTF)
            out.writeBoolean(e.transcriptConfirmed); out.writeUTF(e.intendedInstruction); out.writeBoolean(e.meaningConfirmed)
            list(e.ownerContext); list(e.previousTaskReferences); out.writeUTF(e.desiredOutcome)
            list(e.forbiddenInterpretations); out.writeUTF(e.sourceReference); out.writeBoolean(e.ownerOnly)
            out.writeBoolean(e.sensitiveContentReviewed); out.writeBoolean(e.sensitiveContentApproved)
        }
        return buffer.toByteArray()
    }
    private fun decode(bytes: ByteArray): PersonalVoiceExample {
        val input = DataInputStream(ByteArrayInputStream(bytes))
        fun list(max: Int): List<String> {
            val n = input.readInt(); require(n in 0..max)
            return List(n) { input.readUTF() }
        }
        val e = PersonalVoiceExample(input.readUTF(), input.readUTF(), if (input.readBoolean()) input.readUTF() else null,
            input.readBoolean(), input.readUTF(), input.readBoolean(), list(4), list(16), input.readUTF(), list(16),
            input.readUTF(), input.readBoolean(), input.readBoolean(), input.readBoolean())
        check(input.available() == 0)
        return e
    }
    companion object {
        const val MAX_RECORDS = 256
        private const val MAGIC = 0x50564c31
        private const val VERSION = 1
        private const val MAX_FILE_BYTES = 65536L
        private val DIGEST = Regex("[a-f0-9]{64}")
        private val FILE_ID = Regex("[a-f0-9]{64}\\.pv1")
    }
}
