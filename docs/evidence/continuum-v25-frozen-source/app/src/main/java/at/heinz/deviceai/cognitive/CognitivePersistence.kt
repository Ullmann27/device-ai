package at.heinz.deviceai.cognitive

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.util.Base64

data class CognitiveArchive(
    val identityState: IdentityState,
    val genome: CognitiveGenome,
    val memories: List<MemoryRecord> = emptyList(),
    val experiences: List<Experience> = emptyList()
) {
    val identityId: String get() = identityState.identityId
    val identityGeneration: Int get() = identityState.generation

    init {
        require(identityState.verify()) { "archive requires a verified identity state" }
        require(genome.identityId == identityState.identityId && genome.hash() == identityState.cognitiveGenomeHash) {
            "archive genome does not match its identity state"
        }
        require(memories.map { it.id }.distinct().size == memories.size)
        require(experiences.map { it.id }.distinct().size == experiences.size)
    }
}

enum class ArchiveLoadSource { PRIMARY, LAST_KNOWN_GOOD }

data class ArchiveLoadResult(val archive: CognitiveArchive, val source: ArchiveLoadSource)

/**
 * Persists only explicitly modeled fields. It never instantiates a class named by imported data.
 * Supply an app-private directory (for example Context.filesDir) from Android callers.
 */
class CognitiveArchiveStore(private val directory: File, private val identityId: String) {
    private val primary = File(directory, "cognitive-state.bin")
    private val backup = File(directory, "cognitive-state.lkg")

    init { require(identityId.isNotBlank()) }

    @Synchronized fun save(archive: CognitiveArchive) {
        require(archive.identityId == identityId) { "archive identity does not match this store" }
        val encoded = CognitiveArchiveCodec.encode(archive)
        if (!directory.exists() && !directory.mkdirs()) throw IOException("cannot create cognitive state directory")
        if (primary.exists()) {
            runCatching { readValidated(primary) }
                .onSuccess { writeAtomically(backup, primary.readBytes()) }
        }
        writeAtomically(primary, encoded)
    }

    @Synchronized fun load(): ArchiveLoadResult? = loadCandidates().firstOrNull()

    /**
     * Returns every independently validated archive candidate, primary first.
     * Continuum uses this to reconcile a signed state root after a process dies
     * between archive and seed commits.
     */
    @Synchronized fun loadCandidates(): List<ArchiveLoadResult> {
        val candidates = mutableListOf<ArchiveLoadResult>()
        if (primary.exists()) {
            runCatching { readValidated(primary) }
                .getOrNull()
                ?.let { candidates += ArchiveLoadResult(it, ArchiveLoadSource.PRIMARY) }
        }
        if (backup.exists()) {
            runCatching { readValidated(backup) }
                .getOrNull()
                ?.let { candidates += ArchiveLoadResult(it, ArchiveLoadSource.LAST_KNOWN_GOOD) }
        }
        return candidates
    }

    fun export(archive: CognitiveArchive): String {
        require(archive.identityId == identityId) { "archive identity does not match this store" }
        return Base64.getEncoder().encodeToString(CognitiveArchiveCodec.encode(archive))
    }

    fun import(encodedArchive: String): CognitiveArchive {
        require(encodedArchive.length <= MAX_ARCHIVE_BYTES * 2) { "archive is too large" }
        val bytes = try {
            Base64.getDecoder().decode(encodedArchive)
        } catch (e: IllegalArgumentException) {
            throw IllegalArgumentException("invalid archive encoding", e)
        }
        return CognitiveArchiveCodec.decode(bytes, identityId)
    }

    private fun readValidated(file: File): CognitiveArchive {
        require(file.length() in 1..MAX_ARCHIVE_BYTES.toLong()) { "invalid archive size" }
        return CognitiveArchiveCodec.decode(file.readBytes(), identityId)
    }

    private fun writeAtomically(target: File, bytes: ByteArray) {
        val temp = File.createTempFile("cognitive-state-", ".tmp", directory)
        try {
            java.io.FileOutputStream(temp).use { output ->
                output.write(bytes)
                output.fd.sync()
            }
            try {
                Files.move(
                    temp.toPath(), target.toPath(),
                    StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING
                )
            } catch (_: AtomicMoveNotSupportedException) {
                Files.move(temp.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
            }
        } finally {
            temp.delete()
        }
    }

    companion object { private const val MAX_ARCHIVE_BYTES = 10 * 1024 * 1024 }
}

private object CognitiveArchiveCodec {
    private const val MAGIC = "DEVICE_AI_COGNITIVE_ARCHIVE"
    private const val SCHEMA_VERSION = 1
    private const val MAX_BYTES = 10 * 1024 * 1024
    private const val MAX_ITEMS = 10_000
    private const val MAX_STRING_BYTES = 1_000_000

    fun encode(archive: CognitiveArchive): ByteArray {
        val payloadBytes = ByteArrayOutputStream().also { bytes ->
            DataOutputStream(bytes).use { out ->
                out.writeIdentityState(archive.identityState)
                out.writeGenome(archive.genome)
                out.writeInt(archive.memories.size)
                archive.memories.forEach { out.writeMemory(it) }
                out.writeInt(archive.experiences.size)
                archive.experiences.forEach { out.writeExperience(it) }
            }
        }.toByteArray()
        val bytes = ByteArrayOutputStream().also { buffer ->
            DataOutputStream(buffer).use { out ->
                out.writeUTF(MAGIC)
                out.writeInt(SCHEMA_VERSION)
                out.writeInt(payloadBytes.size)
                out.write(payloadBytes)
                out.write(sha256(payloadBytes))
            }
        }.toByteArray()
        require(bytes.size <= MAX_BYTES) { "archive is too large" }
        return bytes
    }

    fun decode(bytes: ByteArray, expectedIdentityId: String): CognitiveArchive {
        require(bytes.size <= MAX_BYTES) { "archive is too large" }
        try {
            val input = DataInputStream(ByteArrayInputStream(bytes))
            require(input.readUTF() == MAGIC) { "unrecognized archive" }
            require(input.readInt() == SCHEMA_VERSION) { "unsupported archive schema" }
            val payloadSize = input.readInt()
            require(payloadSize in 1..MAX_BYTES && payloadSize + 32 <= input.available()) { "invalid payload size" }
            val payload = ByteArray(payloadSize)
            input.readFully(payload)
            val checksum = ByteArray(32)
            input.readFully(checksum)
            require(input.available() == 0) { "unexpected trailing archive data" }
            require(MessageDigest.isEqual(checksum, sha256(payload))) { "archive checksum mismatch" }

            val data = DataInputStream(ByteArrayInputStream(payload))
            val identityState = data.readIdentityState()
            require(identityState.identityId == expectedIdentityId) { "archive identity does not match this store" }
            val genome = data.readGenome()
            require(genome.identityId == identityState.identityId &&
                genome.hash() == identityState.cognitiveGenomeHash) { "archive genome does not match identity state" }
            val memoryCount = data.readCount()
            val memories = List(memoryCount) { data.readMemory() }
            val experienceCount = data.readCount()
            val experiences = List(experienceCount) { data.readExperience() }
            require(data.available() == 0) { "unexpected payload data" }
            return CognitiveArchive(identityState, genome, memories, experiences)
        } catch (e: IOException) {
            throw IllegalArgumentException("corrupted cognitive archive", e)
        }
    }

    private fun DataInputStream.readCount(): Int = readInt().also {
        require(it in 0..MAX_ITEMS) { "invalid archive item count" }
    }

    private fun DataOutputStream.writeString(value: String) {
        val bytes = value.toByteArray(Charsets.UTF_8)
        require(bytes.size <= MAX_STRING_BYTES) { "archive field is too large" }
        writeInt(bytes.size)
        write(bytes)
    }

    private fun DataInputStream.readString(): String {
        val size = readInt()
        require(size in 0..MAX_STRING_BYTES && size <= available()) { "invalid string length" }
        val bytes = ByteArray(size)
        readFully(bytes)
        return Charsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
            .decode(ByteBuffer.wrap(bytes))
            .toString()
    }

    private fun DataOutputStream.writeStrings(values: List<String>) {
        require(values.size <= MAX_ITEMS)
        writeInt(values.size)
        values.forEach { writeString(it) }
    }

    private fun DataInputStream.readStrings(): List<String> = List(readCount()) { readString() }

    private fun DataOutputStream.writeIdentityState(state: IdentityState) {
        writeString(state.identityId)
        writeInt(state.generation)
        writeNullableString(state.chosenName)
        writeStringMap(state.communicationProfile)
        writeEnumDoubleMap(state.behaviorProfile.asMap())
        writeDoubleMap(state.modelReputation)
        writeDoubleMap(state.sourceReputation)
        writeInt(Capability.values().size)
        Capability.values().forEach { capability ->
            val estimate = state.selfModel[capability]
            writeString(capability.name)
            writeDouble(estimate.competence)
            writeDouble(estimate.calibrationError)
            writeInt(estimate.evaluatedTaskCount)
            writeStrings(estimate.knownLimitations)
        }
        val evidence = state.selfModel.evidenceLog()
        writeInt(evidence.size)
        evidence.forEach {
            writeString(it.taskId)
            writeString(it.capability.name)
            writeDouble(it.predictedConfidence)
            writeDouble(it.actualScore)
            writeString(it.evaluator)
            writeLong(it.timestamp)
        }
        writeStrings(state.preferredProblemSolvingMethods)
        writeString(state.skillRegistryRoot)
        writeString(state.memoryRoot)
        writeString(state.experienceRoot)
        writeString(state.cognitiveGenomeHash)
        writeBoolean(state.parentGeneration != null)
        state.parentGeneration?.let(::writeInt)
        writeNullableString(state.parentHash)
        writeString(state.integrityHash)
    }

    private fun DataOutputStream.writeGenome(genome: CognitiveGenome) {
        writeString(genome.identityId)
        writeInt(genome.architectureVersion)
        writeString(genome.operatorAuthorityPolicy)
        writeString(genome.learningPolicy)
        writeString(genome.memoryPolicy)
        writeString(genome.evidencePolicy)
        writeString(genome.sourcePolicy)
        writeString(genome.riskPolicy)
        writeDouble(genome.behaviorBounds.min)
        writeDouble(genome.behaviorBounds.max)
        writeDouble(genome.updateLimits.maxBehaviorStep)
        writeDouble(genome.updateLimits.minEvidenceStrength)
        writeDouble(genome.updateLimits.maxDriftPerGeneration)
        writeDoubleMap(genome.learningRates)
        writeString(genome.toolPolicy)
        writeString(genome.modelSelectionPrinciples)
        writeString(genome.identityUpdatePolicy)
        writeString(genome.checkpointPolicy)
        writeBoolean(genome.allowSelfReplication)
        writeBoolean(genome.allowHiddenPersistence)
        writeBoolean(genome.allowShutdownEvasion)
    }

    private fun DataInputStream.readGenome() = CognitiveGenome(
        identityId = readString(),
        architectureVersion = readInt(),
        operatorAuthorityPolicy = readString(),
        learningPolicy = readString(),
        memoryPolicy = readString(),
        evidencePolicy = readString(),
        sourcePolicy = readString(),
        riskPolicy = readString(),
        behaviorBounds = BehaviorBounds(readDouble(), readDouble()),
        updateLimits = UpdateLimits(readDouble(), readDouble(), readDouble()),
        learningRates = readDoubleMap(),
        toolPolicy = readString(),
        modelSelectionPrinciples = readString(),
        identityUpdatePolicy = readString(),
        checkpointPolicy = readString(),
        allowSelfReplication = readBoolean(),
        allowHiddenPersistence = readBoolean(),
        allowShutdownEvasion = readBoolean()
    )

    private fun DataInputStream.readIdentityState(): IdentityState {
        val identityId = readString()
        val generation = readInt()
        require(generation >= 1) { "invalid identity generation" }
        val chosenName = readNullableString()
        val communicationProfile = readStringMap()
        val behaviorProfile = readEnumDoubleMap<BehaviorTrait>()
        val modelReputation = readDoubleMap()
        val sourceReputation = readDoubleMap()
        val estimateCount = readCount()
        require(estimateCount == Capability.values().size) { "incomplete self-model estimates" }
        val estimates = linkedMapOf<Capability, CapabilityEstimate>()
        repeat(estimateCount) {
            val capability = enumValueOf<Capability>(readString())
            require(capability !in estimates) { "duplicate self-model estimate" }
            estimates[capability] = CapabilityEstimate(
                competence = readDouble(),
                calibrationError = readDouble(),
                evaluatedTaskCount = readInt(),
                knownLimitations = readStrings()
            )
        }
        val evidence = List(readCount()) {
            CapabilityEvidence(
                taskId = readString(),
                capability = enumValueOf<Capability>(readString()),
                predictedConfidence = readDouble(),
                actualScore = readDouble(),
                evaluator = readString(),
                timestamp = readLong()
            )
        }
        val selfModel = SelfModel.restored(estimates, evidence)
        val preferredMethods = readStrings()
        val skillRegistryRoot = readString()
        val memoryRoot = readString()
        val experienceRoot = readString()
        val genomeHash = readString()
        val parentGeneration = if (readBoolean()) readInt() else null
        val parentHash = readNullableString()
        val integrityHash = readString()
        return IdentityState(
            identityId, generation, chosenName, communicationProfile, BehaviorProfile.of(behaviorProfile),
            modelReputation, sourceReputation, selfModel, preferredMethods, skillRegistryRoot,
            memoryRoot, experienceRoot, genomeHash, parentGeneration, parentHash, integrityHash
        ).also { require(it.verify()) { "identity integrity hash mismatch" } }
    }

    private fun DataOutputStream.writeNullableString(value: String?) {
        writeBoolean(value != null)
        value?.let { writeString(it) }
    }

    private fun DataInputStream.readNullableString(): String? = if (readBoolean()) readString() else null

    private fun DataOutputStream.writeStringMap(values: Map<String, String>) {
        require(values.size <= MAX_ITEMS)
        writeInt(values.size)
        values.toSortedMap().forEach { (key, value) ->
            writeString(key)
            writeString(value)
        }
    }

    private fun DataInputStream.readStringMap(): Map<String, String> = buildMap {
        repeat(readCount()) {
            val key = readString()
            require(key !in this) { "duplicate map key" }
            put(key, readString())
        }
    }

    private fun DataOutputStream.writeDoubleMap(values: Map<String, Double>) {
        require(values.size <= MAX_ITEMS)
        writeInt(values.size)
        values.toSortedMap().forEach { (key, value) ->
            writeString(key)
            writeDouble(value)
        }
    }

    private fun DataInputStream.readDoubleMap(): Map<String, Double> = buildMap {
        repeat(readCount()) {
            val key = readString()
            require(key !in this) { "duplicate map key" }
            put(key, readDouble())
        }
    }

    private fun <E : Enum<E>> DataOutputStream.writeEnumDoubleMap(values: Map<E, Double>) {
        writeInt(values.size)
        values.entries.sortedBy { it.key.name }.forEach { (key, value) ->
            writeString(key.name)
            writeDouble(value)
        }
    }

    private inline fun <reified E : Enum<E>> DataInputStream.readEnumDoubleMap(): Map<E, Double> = buildMap {
        repeat(readCount()) {
            val key = enumValueOf<E>(readString())
            require(key !in this) { "duplicate enum map key" }
            put(key, readDouble())
        }
    }

    private fun DataOutputStream.writeMemory(memory: MemoryRecord) {
        writeString(memory.id)
        writeString(memory.type.name)
        writeString(memory.content)
        writeString(memory.provenance.name)
        writeDouble(memory.confidence)
        writeLong(memory.createdAt)
        writeLong(memory.updatedAt)
        writeStrings(memory.tags)
        writeStrings(memory.parentIds)
        writeBoolean(memory.sourceReference != null)
        memory.sourceReference?.let { writeString(it) }
        writeString(memory.validationState.name)
        writeString(memory.privacyClass.name)
        writeStrings(memory.validationEvidenceReferences)
    }

    private fun DataInputStream.readMemory() = MemoryRecord(
        id = readString(),
        type = enumValueOf<MemoryType>(readString()),
        content = readString(),
        provenance = enumValueOf<ProvenanceType>(readString()),
        confidence = readDouble(),
        createdAt = readLong(),
        updatedAt = readLong(),
        tags = readStrings(),
        parentIds = readStrings(),
        sourceReference = if (readBoolean()) readString() else null,
        validationState = enumValueOf<ValidationState>(readString()),
        privacyClass = enumValueOf<PrivacyClass>(readString()),
        validationEvidenceReferences = readStrings()
    )

    private fun DataOutputStream.writeExperience(experience: Experience) {
        writeString(experience.id)
        writeString(experience.taskType)
        writeString(experience.requestSummary)
        writeStrings(experience.memoryIds)
        writeStrings(experience.modelIds)
        writeStrings(experience.sourceIds)
        writeStrings(experience.toolIds)
        writeString(experience.planSummary)
        writeString(experience.resultSummary)
        writeStrings(experience.failures)
        writeInt(experience.retries)
        writeInt(experience.evaluatorScores.size)
        experience.evaluatorScores.toSortedMap().forEach { (key, value) ->
            writeString(key)
            writeDouble(value)
        }
        writeBoolean(experience.feedback != null)
        experience.feedback?.let { writeString(it) }
        writeDouble(experience.confidenceBefore)
        writeDouble(experience.confidenceAfter)
        writeString(experience.outcome.name)
        writeLong(experience.startedAt)
        writeLong(experience.completedAt)
        writeBoolean(experience.evaluation.success)
        writeDouble(experience.evaluation.score)
        writeString(experience.evaluation.notes)
    }

    private fun DataInputStream.readExperience(): Experience {
        val id = readString()
        val taskType = readString()
        val requestSummary = readString()
        val memoryIds = readStrings()
        val modelIds = readStrings()
        val sourceIds = readStrings()
        val toolIds = readStrings()
        val planSummary = readString()
        val resultSummary = readString()
        val failures = readStrings()
        val retries = readInt()
        val scoreCount = readCount()
        val scores = buildMap {
            repeat(scoreCount) {
                val key = readString()
                require(key !in this) { "duplicate evaluator score" }
                put(key, readDouble())
            }
        }
        val feedback = if (readBoolean()) readString() else null
        val confidenceBefore = readDouble()
        val confidenceAfter = readDouble()
        val outcome = enumValueOf<ExperienceOutcome>(readString())
        val startedAt = readLong()
        val completedAt = readLong()
        val evaluation = Evaluation(readBoolean(), readDouble(), readString())
        return Experience(
            id, taskType, requestSummary, memoryIds, modelIds, sourceIds, toolIds,
            planSummary, resultSummary, failures, retries, scores, feedback,
            confidenceBefore, confidenceAfter, outcome, startedAt, completedAt, evaluation
        )
    }

    private fun sha256(payload: ByteArray): ByteArray = MessageDigest.getInstance("SHA-256").digest(payload)
}
