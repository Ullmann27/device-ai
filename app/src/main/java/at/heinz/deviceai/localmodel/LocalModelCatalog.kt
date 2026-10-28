package at.heinz.deviceai.localmodel

import com.google.ai.edge.litertlm.BackendType
import com.google.ai.edge.litertlm.ModelInfo
import com.google.ai.edge.litertlm.Modality
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.io.IOException
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest

enum class LocalModelBackend { CPU, GPU, NPU }

data class LocalModelCapabilities(
    val text: Boolean,
    val vision: Boolean,
    val audio: Boolean,
    val video: Boolean,
    val functionCalling: Boolean,
    val maxContextTokens: Int,
    val minimumRuntimeVersion: String?,
    val supportedBackends: Set<LocalModelBackend>
) {
    init {
        require(maxContextTokens >= 0)
        require(minimumRuntimeVersion == null || minimumRuntimeVersion.length <= 64)
        require(supportedBackends.isNotEmpty())
    }
}

data class ImportedLocalModel(
    val id: String,
    val version: String,
    val sha256: String,
    val byteCount: Long,
    val capabilities: LocalModelCapabilities
) {
    init {
        require(isSafeIdentifier(id) && isSafeIdentifier(version))
        require(sha256.matches(Regex("[a-f0-9]{64}")) && byteCount in 1..MAX_MODEL_BYTES)
        require(capabilities.text && LocalModelBackend.CPU in capabilities.supportedBackends)
    }
}

fun interface LocalModelInspector {
    fun inspect(modelFile: File): LocalModelCapabilities
}

class NativeLiteRtModelInspector : LocalModelInspector {
    override fun inspect(modelFile: File): LocalModelCapabilities =
        ModelInfo.from(modelFile.absolutePath).use { info ->
            require(info is ModelInfo.Llm) { "Only LiteRT-LM language models are supported." }
            val modalities = info.inputModalities()
            LocalModelCapabilities(
                text = modalities.text,
                vision = modalities.vision,
                audio = modalities.audio,
                video = modalities.video,
                functionCalling = info.supportsFunctionCalling(),
                maxContextTokens = info.maxContextTokens(),
                minimumRuntimeVersion = info.minRuntimeVersion(),
                supportedBackends = info.supportedBackends(Modality.TEXT).mapNotNull {
                    when (it) {
                        BackendType.CPU -> LocalModelBackend.CPU
                        BackendType.GPU -> LocalModelBackend.GPU
                        BackendType.NPU -> LocalModelBackend.NPU
                    }
                }.toSet()
            )
        }.also {
            require(it.text && LocalModelBackend.CPU in it.supportedBackends) {
                "Model must support text input on the CPU backend."
            }
        }
}

class LocalModelCatalog(
    private val directory: File,
    private val inspector: LocalModelInspector,
    private val maxModelBytes: Long = DEFAULT_MAX_MODEL_BYTES
) {
    init { require(maxModelBytes in 1..MAX_ALLOWED_MODEL_BYTES) }

    @Synchronized
    fun import(
        id: String,
        version: String,
        input: InputStream,
        expectedSha256: String,
        onProgress: ((LocalModelImportSnapshot) -> Unit)? = null
    ): ImportedLocalModel {
        require(isSafeIdentifier(id) && isSafeIdentifier(version))
        require(expectedSha256.matches(Regex("[a-f0-9]{64}")))
        if (!directory.exists() && !directory.mkdirs()) throw IOException("Cannot create local model directory.")
        val modelFile = modelFile(id, version)
        val metadataFile = metadataFile(id, version)
        require(!modelFile.exists() && !metadataFile.exists()) { "Model version is already imported." }
        require(directory.listFiles { file -> file.isFile && file.name.endsWith(METADATA_SUFFIX) }
            .orEmpty().size < MAX_CATALOG_ENTRIES) { "Local model catalog is full." }
        val temporary = File.createTempFile("model-import-", ".litertlm", directory)
        val progress = LocalModelImportProgress()
        var total = 0L
        fun report(phase: LocalModelImportPhase) {
            val snapshot = progress.record(phase, total)
            // An optional diagnostic observer must never break a valid model import.
            runCatching { onProgress?.invoke(snapshot) }
        }
        try {
            report(LocalModelImportPhase.STREAM_OPENED)
            val digest = MessageDigest.getInstance("SHA-256")
            report(LocalModelImportPhase.COPYING)
            var nextProgressBytes = 8L * 1024 * 1024
            FileOutputStream(temporary).use { output ->
                input.use { source ->
                    val buffer = ByteArray(COPY_BUFFER_SIZE)
                    var emptyReads = 0
                    while (true) {
                        if (Thread.currentThread().isInterrupted) {
                            throw java.io.InterruptedIOException("Model import cancelled between reads.")
                        }
                        val count = source.read(buffer)
                        if (Thread.currentThread().isInterrupted) {
                            throw java.io.InterruptedIOException("Model import cancelled after read.")
                        }
                        if (count < 0) break
                        if (count == 0) {
                            emptyReads++
                            if (emptyReads >= 16) {
                                throw IOException("Model input made no progress after 16 reads.")
                            }
                            continue
                        }
                        emptyReads = 0
                        require(total <= maxModelBytes - count) { "Model exceeds the configured import limit." }
                        total += count
                        digest.update(buffer, 0, count)
                        output.write(buffer, 0, count)
                        if (total >= nextProgressBytes) {
                            report(LocalModelImportPhase.COPYING)
                            nextProgressBytes = total + 8L * 1024 * 1024
                        }
                    }
                }
                output.fd.sync()
            }
            report(LocalModelImportPhase.COPY_COMPLETED)
            if (Thread.currentThread().isInterrupted) {
                throw java.io.InterruptedIOException("Model import cancelled after copy.")
            }
            require(total > 0) { "Model file is empty." }
            val hash = digest.digest().toHex()
            require(MessageDigest.isEqual(hash.toByteArray(), expectedSha256.toByteArray())) {
                "Model SHA-256 does not match the expected digest. " +
                    "Expected: $expectedSha256; actual: $hash; bytes: $total."
            }
            report(LocalModelImportPhase.HASH_VERIFIED)
            val capabilities = inspector.inspect(temporary)
            if (Thread.currentThread().isInterrupted) {
                throw java.io.InterruptedIOException("Model import cancelled during inspection.")
            }
            require(capabilities.text && LocalModelBackend.CPU in capabilities.supportedBackends) {
                "Only text models with CPU support can be imported."
            }
            report(LocalModelImportPhase.INSPECTED)
            val record = ImportedLocalModel(id, version, hash, total, capabilities)
            moveAtomically(temporary, modelFile)
            try {
                writeMetadata(metadataFile, record)
            } catch (e: Exception) {
                modelFile.delete()
                throw e
            }
            report(LocalModelImportPhase.COMMITTED)
            return record
        } catch (error: Exception) {
            runCatching { report(LocalModelImportPhase.FAILED) }
            throw error
        } finally {
            temporary.delete()
        }
    }

    @Synchronized
    fun all(): List<ImportedLocalModel> =
        directory.listFiles { file -> file.isFile && file.name.endsWith(METADATA_SUFFIX) }
            .orEmpty()
            .take(MAX_CATALOG_ENTRIES)
            .mapNotNull { file ->
                runCatching { readMetadata(file) }.getOrNull()?.takeIf { record ->
                    metadataFile(record.id, record.version).name == file.name &&
                        modelFile(record.id, record.version).let { it.isFile && it.length() == record.byteCount }
                }
            }
            .sortedWith(compareBy({ it.id }, { it.version }))

    @Synchronized
    fun get(id: String, version: String, verifyBytes: Boolean = true): ImportedLocalModel? {
        require(isSafeIdentifier(id) && isSafeIdentifier(version))
        val record = runCatching { readMetadata(metadataFile(id, version)) }.getOrNull() ?: return null
        if (record.id != id || record.version != version) return null
        val file = modelFile(id, version)
        if (!file.isFile || file.length() != record.byteCount) return null
        if (verifyBytes && sha256(file) != record.sha256) return null
        return record
    }

    @Synchronized
    fun modelFile(record: ImportedLocalModel): File {
        require(get(record.id, record.version) == record) { "Imported model failed catalog verification." }
        return modelFile(record.id, record.version)
    }

    @Synchronized
    fun delete(id: String, version: String): Boolean {
        require(isSafeIdentifier(id) && isSafeIdentifier(version))
        val model = modelFile(id, version)
        val metadata = metadataFile(id, version)
        if (!metadata.exists()) return false
        model.delete()
        return metadata.delete()
    }

    private fun modelFile(id: String, version: String) =
        File(directory, "${keyPrefix(id, version)}.litertlm")
    private fun metadataFile(id: String, version: String) =
        File(directory, "${keyPrefix(id, version)}.meta")

    private fun keyPrefix(id: String, version: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest("$id\u0000$version".toByteArray(Charsets.UTF_8)).toHex()

    private fun writeMetadata(file: File, record: ImportedLocalModel) {
        val bytes = ByteArrayOutputStream().also { buffer ->
            DataOutputStream(buffer).use { out ->
                out.writeUTF(METADATA_MAGIC)
                out.writeInt(METADATA_SCHEMA)
                out.writeUTF(record.id)
                out.writeUTF(record.version)
                out.writeUTF(record.sha256)
                out.writeLong(record.byteCount)
                val caps = record.capabilities
                out.writeBoolean(caps.text)
                out.writeBoolean(caps.vision)
                out.writeBoolean(caps.audio)
                out.writeBoolean(caps.video)
                out.writeBoolean(caps.functionCalling)
                out.writeInt(caps.maxContextTokens)
                out.writeBoolean(caps.minimumRuntimeVersion != null)
                caps.minimumRuntimeVersion?.let(out::writeUTF)
                out.writeInt(caps.supportedBackends.size)
                caps.supportedBackends.sortedBy { it.name }.forEach { out.writeUTF(it.name) }
            }
        }.toByteArray()
        require(bytes.size <= MAX_METADATA_BYTES)
        val temporary = File.createTempFile("model-meta-", ".tmp", directory)
        try {
            FileOutputStream(temporary).use { output ->
                output.write(bytes)
                output.fd.sync()
            }
            moveAtomically(temporary, file)
        } finally {
            temporary.delete()
        }
    }

    private fun readMetadata(file: File): ImportedLocalModel {
        require(file.isFile && file.length() in 1..MAX_METADATA_BYTES.toLong())
        val input = DataInputStream(ByteArrayInputStream(file.readBytes()))
        require(input.readUTF() == METADATA_MAGIC)
        require(input.readInt() == METADATA_SCHEMA) { "Unsupported local model metadata schema." }
        val id = input.readUTF()
        val version = input.readUTF()
        val hash = input.readUTF()
        val bytes = input.readLong()
        val text = input.readBoolean()
        val vision = input.readBoolean()
        val audio = input.readBoolean()
        val video = input.readBoolean()
        val functionCalling = input.readBoolean()
        val contextTokens = input.readInt()
        val runtimeVersion = if (input.readBoolean()) input.readUTF() else null
        val count = input.readInt()
        require(count in 1..3)
        val backends = List(count) { LocalModelBackend.valueOf(input.readUTF()) }.toSet()
        require(input.available() == 0)
        return ImportedLocalModel(
            id, version, hash, bytes,
            LocalModelCapabilities(text, vision, audio, video, functionCalling, contextTokens, runtimeVersion, backends)
        )
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        FileInputStream(file).use { input ->
            val buffer = ByteArray(COPY_BUFFER_SIZE)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        return digest.digest().toHex()
    }

    private fun moveAtomically(source: File, target: File) {
        try {
            Files.move(source.toPath(), target.toPath(), StandardCopyOption.ATOMIC_MOVE)
        } catch (_: AtomicMoveNotSupportedException) {
            Files.move(source.toPath(), target.toPath())
        }
    }

    companion object {
        const val DEFAULT_MAX_MODEL_BYTES = 20L * 1024 * 1024 * 1024
        private const val MAX_ALLOWED_MODEL_BYTES = 64L * 1024 * 1024 * 1024
        private const val COPY_BUFFER_SIZE = 64 * 1024
        private const val METADATA_MAGIC = "DEVICE_AI_LOCAL_MODEL"
        private const val METADATA_SCHEMA = 1
        private const val METADATA_SUFFIX = ".meta"
        private const val MAX_METADATA_BYTES = 4 * 1024
        private const val MAX_CATALOG_ENTRIES = 256
    }
}

private const val MAX_MODEL_BYTES = 64L * 1024 * 1024 * 1024

internal fun isSafeIdentifier(value: String) =
    value.length in 1..64 && value.matches(Regex("[A-Za-z0-9][A-Za-z0-9._-]*"))

private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }
