package at.heinz.deviceai.cognitive

enum class MemoryMutationReason {
    USER_CORRECTION,
    NEW_EVIDENCE,
    CONTRADICTION_RESOLUTION,
    CONSOLIDATION,
    SUPERSESSION,
    PRIVACY_CHANGE
}

data class MemoryMutationPayload(
    val mutationId: String,
    val recordId: String,
    val beforeHash: String?,
    val afterHash: String,
    val reason: MemoryMutationReason,
    val provenance: ProvenanceType,
    val evidenceReferences: List<String>,
    val contradictionReferences: List<String>,
    val previousMutationHash: String?,
    val createdAtMillis: Long
) {
    init {
        require(mutationId.matches(Regex("[A-Za-z0-9._:-]{1,128}")))
        require(recordId.isNotBlank() && recordId.length <= 256)
        require(beforeHash == null || beforeHash.matches(Regex("[a-f0-9]{64}")))
        require(afterHash.matches(Regex("[a-f0-9]{64}")))
        require(previousMutationHash == null || previousMutationHash.matches(Regex("[a-f0-9]{64}")))
        require(evidenceReferences.size <= 128 && evidenceReferences.none(String::isBlank))
        require(contradictionReferences.size <= 128 && contradictionReferences.none(String::isBlank))
        require(createdAtMillis >= 0)
    }

    fun canonicalBytes(): ByteArray = buildString {
        append(mutationId).append('|')
        append(recordId).append('|')
        append(beforeHash.orEmpty()).append('|')
        append(afterHash).append('|')
        append(reason.name).append('|')
        append(provenance.name).append('|')
        append(evidenceReferences.distinct().sorted().joinToString(",")).append('|')
        append(contradictionReferences.distinct().sorted().joinToString(",")).append('|')
        append(previousMutationHash.orEmpty()).append('|')
        append(createdAtMillis)
    }.toByteArray(Charsets.UTF_8)

    fun hash(): String = sha256Bytes(canonicalBytes()).toHex()
}

data class ProofCarryingMemoryMutation(
    val payload: MemoryMutationPayload,
    val signature: ByteArray
) {
    init { require(signature.isNotEmpty() && signature.size <= 4_096) }

    fun verify(signer: IdentitySigner): Boolean =
        signer.verify(payload.canonicalBytes(), signature)

    companion object {
        fun create(
            mutationId: String,
            before: MemoryRecord?,
            after: MemoryRecord,
            reason: MemoryMutationReason,
            evidenceReferences: List<String>,
            contradictionReferences: List<String>,
            previousMutationHash: String?,
            decision: MemoryWriteDecision,
            signer: IdentitySigner,
            createdAtMillis: Long
        ): ProofCarryingMemoryMutation {
            require(decision.shouldWrite) { "MemoryWritePolicy rejected durable memory mutation." }
            require(before == null || before.id == after.id) { "Memory mutation cannot change record identity." }
            require(after.privacyClass == decision.privacyClass) {
                "Mutation privacy class must match MemoryWritePolicy decision."
            }
            if (reason in setOf(
                    MemoryMutationReason.NEW_EVIDENCE,
                    MemoryMutationReason.CONTRADICTION_RESOLUTION,
                    MemoryMutationReason.SUPERSESSION
                )
            ) {
                require(evidenceReferences.isNotEmpty()) {
                    "Evidence-bearing memory mutations require provenance references."
                }
            }
            val payload = MemoryMutationPayload(
                mutationId = mutationId,
                recordId = after.id,
                beforeHash = before?.let(::memoryRecordHash),
                afterHash = memoryRecordHash(after),
                reason = reason,
                provenance = after.provenance,
                evidenceReferences = evidenceReferences.distinct().sorted(),
                contradictionReferences = contradictionReferences.distinct().sorted(),
                previousMutationHash = previousMutationHash,
                createdAtMillis = createdAtMillis
            )
            return ProofCarryingMemoryMutation(payload, signer.sign(payload.canonicalBytes()))
        }
    }
}

class MemoryMutationLedger(private val signer: IdentitySigner) {
    private val entries = mutableListOf<ProofCarryingMemoryMutation>()

    @Synchronized
    fun append(mutation: ProofCarryingMemoryMutation) {
        require(mutation.verify(signer)) { "Memory mutation signature verification failed." }
        val expectedPrevious = entries.lastOrNull()?.payload?.hash()
        require(mutation.payload.previousMutationHash == expectedPrevious) {
            "Memory mutation chain is discontinuous."
        }
        entries += mutation
    }

    @Synchronized
    fun all(): List<ProofCarryingMemoryMutation> = entries.toList()

    @Synchronized
    fun verifyChain(): Boolean {
        var previous: String? = null
        for (entry in entries) {
            if (!entry.verify(signer)) return false
            if (entry.payload.previousMutationHash != previous) return false
            previous = entry.payload.hash()
        }
        return true
    }

    @Synchronized
    fun lastHash(): String? = entries.lastOrNull()?.payload?.hash()
}

fun memoryRecordHash(record: MemoryRecord): String {
    val canonical = buildString {
        append(record.id).append('|')
        append(record.type.name).append('|')
        append(record.content).append('|')
        append(record.provenance.name).append('|')
        append(record.confidence).append('|')
        append(record.createdAt).append('|').append(record.updatedAt).append('|')
        append(record.tags.distinct().sorted().joinToString(",")).append('|')
        append(record.parentIds.distinct().sorted().joinToString(",")).append('|')
        append(record.sourceReference.orEmpty()).append('|')
        append(record.validationState.name).append('|')
        append(record.privacyClass.name).append('|')
        append(record.validationEvidenceReferences.distinct().sorted().joinToString(","))
    }
    return sha256Bytes(canonical.toByteArray(Charsets.UTF_8)).toHex()
}
