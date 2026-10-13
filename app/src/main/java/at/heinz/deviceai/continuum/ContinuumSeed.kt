package at.heinz.deviceai.continuum

import at.heinz.deviceai.cognitive.IdentitySigner
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.security.MessageDigest

data class ContinuumInstanceId(val value: String) {
    init { require(value.matches(ID_PATTERN)) }
    companion object { private val ID_PATTERN = Regex("[a-zA-Z0-9_-]{1,64}") }
}

data class GovernanceVersion(val value: Int) {
    init { require(value == CURRENT) { "Unsupported governance version." } }
    companion object { const val CURRENT = 1 }
}

data class StateFormatVersion(val value: Int) {
    init { require(value == CURRENT) { "Unsupported state format version." } }
    companion object { const val CURRENT = 1 }
}

data class ModelAdapterAbiVersion(val value: Int) {
    init { require(value == CURRENT) { "Unsupported model adapter ABI version." } }
    companion object { const val CURRENT = 1 }
}

data class OwnerPublicIdentityReference(val value: String) {
    init {
        require(value.length <= MAX_LENGTH && REFERENCE_PATTERN.matches(value)) {
            "Owner identity must be a bounded public reference, not key material."
        }
    }

    companion object {
        const val MAX_LENGTH = 512
        private val REFERENCE_PATTERN = Regex(
            "(?:local-keystore://[A-Za-z0-9._-]{1,128}|sha256:[a-f0-9]{64}|did:[a-z0-9]+:[A-Za-z0-9._:%-]{8,256}|https://[A-Za-z0-9.-]{1,253}/[A-Za-z0-9._/-]{1,256})"
        )
    }
}

data class ContinuumSeed(
    val instanceId: ContinuumInstanceId,
    val governanceVersion: GovernanceVersion,
    val stateFormatVersion: StateFormatVersion,
    val modelAdapterAbiVersion: ModelAdapterAbiVersion,
    val ownerPublicIdentityRef: OwnerPublicIdentityReference,
    val authorizedNodeIds: Set<ContinuumNodeIdentity>,
    val currentStateRoot: ContinuumObjectId,
    val migrationHistoryRefs: List<ContinuumObjectId>,
    val schemaVersion: Int = CURRENT_SCHEMA_VERSION
) {
    init {
        require(schemaVersion == CURRENT_SCHEMA_VERSION) { "Unsupported Continuum seed schema." }
        require(authorizedNodeIds.size <= MAX_AUTHORIZED_NODES)
        require(migrationHistoryRefs.size <= MAX_MIGRATION_REFERENCES)
        require(migrationHistoryRefs.distinct().size == migrationHistoryRefs.size)
    }

    companion object {
        const val CURRENT_SCHEMA_VERSION = 1
        const val MAX_AUTHORIZED_NODES = 64
        const val MAX_MIGRATION_REFERENCES = 128
    }
}

typealias AiSeedKernel = ContinuumSeed

data class ContinuumNodeIdentity(val value: String) {
    init { require(value.matches(Regex("[a-f0-9]{16,64}"))) }
}

data class SignedContinuumSeed(
    val seed: ContinuumSeed,
    val contentHash: String,
    val signature: ByteArray
) {
    init {
        require(contentHash.matches(Regex("[a-f0-9]{64}")))
        require(signature.isNotEmpty() && signature.size <= MAX_SIGNATURE_BYTES)
    }

    fun verify(signer: IdentitySigner): Boolean {
        val payload = ContinuumSeedCodec.encode(seed)
        return contentHash == sha256Hex(payload) && signer.verify(payload, signature)
    }

    fun encode(): ByteArray = ContinuumSeedCodec.encodeEnvelope(this)

    companion object { const val MAX_SIGNATURE_BYTES = 256 }
}

object ContinuumSeedCodec {
    private const val MAGIC = "DEVICE_AI_CONTINUUM_SEED"
    private const val ENVELOPE_MAGIC = "DEVICE_AI_SIGNED_CONTINUUM_SEED"
    private const val MAX_SEED_BYTES = 16 * 1024
    private const val MAX_ENVELOPE_BYTES = 20 * 1024

    fun sign(seed: ContinuumSeed, signer: IdentitySigner): SignedContinuumSeed {
        val payload = encode(seed)
        return SignedContinuumSeed(seed, sha256Hex(payload), signer.sign(payload))
    }

    fun encode(seed: ContinuumSeed): ByteArray {
        val bytes = ByteArrayOutputStream()
        DataOutputStream(bytes).use { out ->
            out.writeUTF(MAGIC)
            out.writeInt(seed.schemaVersion)
            out.writeString(seed.instanceId.value, 64)
            out.writeInt(seed.governanceVersion.value)
            out.writeInt(seed.stateFormatVersion.value)
            out.writeInt(seed.modelAdapterAbiVersion.value)
            out.writeString(seed.ownerPublicIdentityRef.value, OwnerPublicIdentityReference.MAX_LENGTH)
            out.writeInt(seed.authorizedNodeIds.size)
            seed.authorizedNodeIds.sortedBy { it.value }.forEach { out.writeString(it.value, 64) }
            out.writeString(seed.currentStateRoot.value, 64)
            out.writeInt(seed.migrationHistoryRefs.size)
            seed.migrationHistoryRefs.forEach { out.writeString(it.value, 64) }
        }
        return bytes.toByteArray().also { require(it.size <= MAX_SEED_BYTES) }
    }

    fun decode(bytes: ByteArray): ContinuumSeed {
        require(bytes.size in 1..MAX_SEED_BYTES) { "Continuum seed exceeds its size limit." }
        val input = DataInputStream(ByteArrayInputStream(bytes))
        require(input.readUTF() == MAGIC) { "Invalid Continuum seed header." }
        val schema = input.readInt()
        require(schema == ContinuumSeed.CURRENT_SCHEMA_VERSION) { "Unsupported Continuum seed schema." }
        val seed = ContinuumSeed(
            instanceId = ContinuumInstanceId(input.readString(64)),
            governanceVersion = GovernanceVersion(input.readInt()),
            stateFormatVersion = StateFormatVersion(input.readInt()),
            modelAdapterAbiVersion = ModelAdapterAbiVersion(input.readInt()),
            ownerPublicIdentityRef = OwnerPublicIdentityReference(input.readString(OwnerPublicIdentityReference.MAX_LENGTH)),
            authorizedNodeIds = List(input.readCount(ContinuumSeed.MAX_AUTHORIZED_NODES)) {
                ContinuumNodeIdentity(input.readString(64))
            }.toSet(),
            currentStateRoot = ContinuumObjectId(input.readString(64)),
            migrationHistoryRefs = List(input.readCount(ContinuumSeed.MAX_MIGRATION_REFERENCES)) {
                ContinuumObjectId(input.readString(64))
            },
            schemaVersion = schema
        )
        require(input.available() == 0) { "Unexpected trailing Continuum seed bytes." }
        require(encode(seed).contentEquals(bytes)) { "Continuum seed encoding is not canonical." }
        return seed
    }

    fun encodeEnvelope(envelope: SignedContinuumSeed): ByteArray {
        val payload = encode(envelope.seed)
        require(envelope.contentHash == sha256Hex(payload)) { "Seed content hash mismatch." }
        val bytes = ByteArrayOutputStream()
        DataOutputStream(bytes).use { out ->
            out.writeUTF(ENVELOPE_MAGIC)
            out.writeInt(1)
            out.writeInt(payload.size)
            out.write(payload)
            out.writeString(envelope.contentHash, 64)
            out.writeInt(envelope.signature.size)
            out.write(envelope.signature)
        }
        return bytes.toByteArray().also { require(it.size <= MAX_ENVELOPE_BYTES) }
    }

    fun decodeEnvelope(bytes: ByteArray, signer: IdentitySigner): SignedContinuumSeed {
        require(bytes.size in 1..MAX_ENVELOPE_BYTES) { "Signed seed exceeds its size limit." }
        val input = DataInputStream(ByteArrayInputStream(bytes))
        require(input.readUTF() == ENVELOPE_MAGIC && input.readInt() == 1) {
            "Unsupported signed seed envelope."
        }
        val payloadSize = input.readInt()
        require(payloadSize in 1..MAX_SEED_BYTES && payloadSize <= input.available()) {
            "Invalid Continuum seed payload size."
        }
        val payload = ByteArray(payloadSize)
        input.readFully(payload)
        val seed = decode(payload)
        val hash = input.readString(64)
        val signatureSize = input.readCount(SignedContinuumSeed.MAX_SIGNATURE_BYTES)
        require(signatureSize > 0 && signatureSize == input.available())
        val signature = ByteArray(signatureSize)
        input.readFully(signature)
        val envelope = SignedContinuumSeed(seed, hash, signature)
        require(envelope.verify(signer)) { "Continuum seed signature or integrity check failed." }
        return envelope
    }

    private fun DataOutputStream.writeString(value: String, maxLength: Int) {
        require(value.length <= maxLength)
        val bytes = value.toByteArray(Charsets.UTF_8)
        require(bytes.size <= maxLength * 4)
        writeInt(bytes.size)
        write(bytes)
    }

    private fun DataInputStream.readString(maxLength: Int): String {
        val size = readInt()
        require(size in 0..(maxLength * 4) && size <= available())
        val bytes = ByteArray(size)
        readFully(bytes)
        return bytes.toString(Charsets.UTF_8).also { require(it.length <= maxLength) }
    }

    private fun DataInputStream.readCount(max: Int): Int =
        readInt().also { require(it in 0..max) }
}

private fun sha256Hex(bytes: ByteArray): String =
    MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
