package at.heinz.deviceai.cognitive

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException
import java.security.MessageDigest

@JvmInline
value class ContinuumInstanceId(val value: String) {
    init { require(value.matches(Regex("[A-Za-z0-9._:-]{1,128}"))) { "Invalid continuum instance id." } }
    override fun toString(): String = value
}

@JvmInline
value class GovernanceVersion(val value: Int) {
    init { require(value in 1..10_000) }
}

@JvmInline
value class StateFormatVersion(val value: Int) {
    init { require(value in 1..10_000) }
}

@JvmInline
value class ModelAdapterAbiVersion(val value: Int) {
    init { require(value in 1..10_000) }
}

data class ContinuumSeed(
    val instanceId: ContinuumInstanceId,
    val governanceVersion: GovernanceVersion,
    val stateFormatVersion: StateFormatVersion,
    val memorySchemaVersion: Int,
    val skillSchemaVersion: Int,
    val modelAdapterAbiVersion: ModelAdapterAbiVersion,
    val ownerIdentityReference: String,
    val authorizedNodeIds: List<ComputeNodeId>,
    val currentStateRoot: ContinuumObjectId,
    val migrationHistoryReferences: List<String> = emptyList(),
    val createdAtMillis: Long,
    val schemaVersion: Int = CURRENT_SCHEMA_VERSION
) {
    init {
        require(memorySchemaVersion in 1..10_000 && skillSchemaVersion in 1..10_000)
        require(ownerIdentityReference.matches(Regex("[A-Za-z0-9._:@/-]{1,256}")))
        require(authorizedNodeIds.size <= MAX_AUTHORIZED_NODES)
        require(authorizedNodeIds.distinct().size == authorizedNodeIds.size)
        require(migrationHistoryReferences.size <= MAX_MIGRATIONS)
        require(migrationHistoryReferences.all { it.matches(Regex("[A-Za-z0-9._:/-]{1,256}")) })
        require(createdAtMillis >= 0)
        require(schemaVersion in 1..10_000)
    }

    fun contentHash(): String = sha256Bytes(ContinuumSeedCodec.encodePayload(this)).toHex()

    companion object {
        const val CURRENT_SCHEMA_VERSION = 1
        const val SUPPORTED_STATE_FORMAT_VERSION = 1
        const val SUPPORTED_MODEL_ADAPTER_ABI = 1
        private const val MAX_AUTHORIZED_NODES = 64
        private const val MAX_MIGRATIONS = 256
    }
}

data class SignedContinuumSeed(
    val payload: ByteArray,
    val signature: ByteArray
) {
    init {
        require(payload.isNotEmpty() && payload.size <= MAX_PAYLOAD_BYTES)
        require(signature.isNotEmpty() && signature.size <= MAX_SIGNATURE_BYTES)
    }

    companion object {
        const val MAX_PAYLOAD_BYTES = 64 * 1024
        const val MAX_SIGNATURE_BYTES = 4 * 1024
    }
}

object ContinuumSeedCodec {
    private const val MAGIC = "DEVICE_AI_CONTINUUM_SEED"
    private const val ENVELOPE_SCHEMA = 1
    private const val MAX_STRING_BYTES = 4 * 1024

    fun seal(seed: ContinuumSeed, signer: IdentitySigner): SignedContinuumSeed {
        requireSupported(seed)
        val payload = encodePayload(seed)
        return SignedContinuumSeed(payload, signer.sign(payload))
    }

    fun open(envelope: SignedContinuumSeed, signer: IdentitySigner): ContinuumSeed {
        require(signer.verify(envelope.payload, envelope.signature)) {
            "Continuum seed signature verification failed."
        }
        return decodePayload(envelope.payload).also(::requireSupported)
    }

    fun encodeEnvelope(envelope: SignedContinuumSeed): ByteArray {
        val bytes = ByteArrayOutputStream().also { buffer ->
            DataOutputStream(buffer).use { out ->
                out.writeUTF(MAGIC)
                out.writeInt(ENVELOPE_SCHEMA)
                out.writeInt(envelope.payload.size)
                out.write(envelope.payload)
                out.writeInt(envelope.signature.size)
                out.write(envelope.signature)
                out.write(sha256Bytes(envelope.payload + envelope.signature))
            }
        }.toByteArray()
        require(bytes.size <= SignedContinuumSeed.MAX_PAYLOAD_BYTES + SignedContinuumSeed.MAX_SIGNATURE_BYTES + 256)
        return bytes
    }

    fun decodeEnvelope(bytes: ByteArray): SignedContinuumSeed = try {
        require(bytes.size in 1..(SignedContinuumSeed.MAX_PAYLOAD_BYTES + SignedContinuumSeed.MAX_SIGNATURE_BYTES + 256))
        val input = DataInputStream(ByteArrayInputStream(bytes))
        require(input.readUTF() == MAGIC) { "Unrecognized Continuum seed envelope." }
        require(input.readInt() == ENVELOPE_SCHEMA) { "Unsupported Continuum seed envelope schema." }
        val payloadSize = input.readInt()
        require(payloadSize in 1..SignedContinuumSeed.MAX_PAYLOAD_BYTES && payloadSize <= input.available())
        val payload = ByteArray(payloadSize)
        input.readFully(payload)
        val signatureSize = input.readInt()
        require(signatureSize in 1..SignedContinuumSeed.MAX_SIGNATURE_BYTES && signatureSize <= input.available() - 32)
        val signature = ByteArray(signatureSize)
        input.readFully(signature)
        val checksum = ByteArray(32)
        input.readFully(checksum)
        require(input.available() == 0) { "Unexpected trailing Continuum seed data." }
        require(MessageDigest.isEqual(checksum, sha256Bytes(payload + signature))) {
            "Continuum seed envelope integrity check failed."
        }
        SignedContinuumSeed(payload, signature)
    } catch (e: IOException) {
        throw IllegalArgumentException("Corrupted Continuum seed envelope.", e)
    }

    internal fun encodePayload(seed: ContinuumSeed): ByteArray =
        ByteArrayOutputStream().also { buffer ->
            DataOutputStream(buffer).use { out ->
                out.writeInt(seed.schemaVersion)
                out.writeBoundedString(seed.instanceId.value)
                out.writeInt(seed.governanceVersion.value)
                out.writeInt(seed.stateFormatVersion.value)
                out.writeInt(seed.memorySchemaVersion)
                out.writeInt(seed.skillSchemaVersion)
                out.writeInt(seed.modelAdapterAbiVersion.value)
                out.writeBoundedString(seed.ownerIdentityReference)
                val nodes = seed.authorizedNodeIds.map { it.value }.sorted()
                out.writeInt(nodes.size)
                nodes.forEach { out.writeBoundedString(it) }
                out.writeBoundedString(seed.currentStateRoot.value)
                val migrations = seed.migrationHistoryReferences.sorted()
                out.writeInt(migrations.size)
                migrations.forEach { out.writeBoundedString(it) }
                out.writeLong(seed.createdAtMillis)
            }
        }.toByteArray().also {
            require(it.size <= SignedContinuumSeed.MAX_PAYLOAD_BYTES) { "Continuum seed payload is too large." }
        }

    internal fun decodePayload(bytes: ByteArray): ContinuumSeed = try {
        require(bytes.size in 1..SignedContinuumSeed.MAX_PAYLOAD_BYTES)
        val input = DataInputStream(ByteArrayInputStream(bytes))
        val schema = input.readInt()
        val instanceId = ContinuumInstanceId(input.readBoundedString())
        val governance = GovernanceVersion(input.readInt())
        val stateFormat = StateFormatVersion(input.readInt())
        val memorySchema = input.readInt()
        val skillSchema = input.readInt()
        val abi = ModelAdapterAbiVersion(input.readInt())
        val owner = input.readBoundedString()
        val nodeCount = input.readInt()
        require(nodeCount in 0..64)
        val nodes = List(nodeCount) { ComputeNodeId(input.readBoundedString()) }
        val stateRoot = ContinuumObjectId(input.readBoundedString())
        val migrationCount = input.readInt()
        require(migrationCount in 0..256)
        val migrations = List(migrationCount) { input.readBoundedString() }
        val created = input.readLong()
        require(input.available() == 0) { "Unexpected trailing Continuum seed payload." }
        ContinuumSeed(
            instanceId, governance, stateFormat, memorySchema, skillSchema, abi,
            owner, nodes, stateRoot, migrations, created, schema
        )
    } catch (e: IOException) {
        throw IllegalArgumentException("Corrupted Continuum seed payload.", e)
    }

    private fun requireSupported(seed: ContinuumSeed) {
        require(seed.schemaVersion == ContinuumSeed.CURRENT_SCHEMA_VERSION) { "Unsupported Continuum seed schema." }
        require(seed.stateFormatVersion.value <= ContinuumSeed.SUPPORTED_STATE_FORMAT_VERSION) {
            "Unsupported future Continuum state format."
        }
        require(seed.modelAdapterAbiVersion.value <= ContinuumSeed.SUPPORTED_MODEL_ADAPTER_ABI) {
            "Unsupported future model adapter ABI."
        }
    }

    private fun DataOutputStream.writeBoundedString(value: String) {
        val bytes = value.toByteArray(Charsets.UTF_8)
        require(bytes.size <= MAX_STRING_BYTES)
        writeInt(bytes.size)
        write(bytes)
    }

    private fun DataInputStream.readBoundedString(): String {
        val size = readInt()
        require(size in 0..MAX_STRING_BYTES && size <= available())
        val bytes = ByteArray(size)
        readFully(bytes)
        return bytes.toString(Charsets.UTF_8)
    }
}

internal fun sha256Bytes(bytes: ByteArray): ByteArray =
    MessageDigest.getInstance("SHA-256").digest(bytes)

internal fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }
