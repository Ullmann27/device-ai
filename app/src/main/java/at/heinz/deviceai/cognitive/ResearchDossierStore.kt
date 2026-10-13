package at.heinz.deviceai.cognitive

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest

class FileResearchDossierStore(private val directory: File) :
    ResearchDossierRepository, ResearchCheckpointStore {
    private val dossierFile = File(directory, "research-dossier.bin")
    private val checkpointFile = File(directory, "research-checkpoint.bin")

    @Synchronized
    override fun save(dossier: ResearchDossier) {
        writeAtomically(dossierFile) { out -> out.writeDossier(dossier) }
    }

    @Synchronized
    fun loadDossier(): ResearchDossier? = if (!dossierFile.exists()) null else readEnvelope(dossierFile) {
        it.readDossier().also { dossier -> require(it.available() == 0) }
    }

    @Synchronized
    override fun loadLatest(): ResearchDossier? = loadDossier()

    @Synchronized
    override fun save(checkpoint: ResearchRunCheckpoint) {
        writeAtomically(checkpointFile) { out ->
            out.writeString(checkpoint.dossierId)
            out.writeInt(checkpoint.phase.ordinal)
            out.writeStrings(checkpoint.completedSourceHashes.toList())
            out.writeStrings(checkpoint.completedUrls.toList())
            out.writeLong(checkpoint.updatedAt)
            out.writeInt(checkpoint.totalBytes)
        }
    }

    @Synchronized
    override fun load(): ResearchRunCheckpoint? = if (!checkpointFile.exists()) null else readEnvelope(checkpointFile) {
        val checkpoint = ResearchRunCheckpoint(
            it.readString(64),
            ResearchPhase.values().getOrNull(it.readInt()) ?: error("Unknown research phase."),
            it.readStrings(24, 64).toSet(),
            it.readStrings(24, 4_096).toSet(),
            it.readLong(),
            it.readInt()
        )
        require(it.available() == 0)
        checkpoint
    }

    @Synchronized
    fun clearCheckpoint() {
        checkpointFile.delete()
    }

    private fun writeAtomically(file: File, encode: (DataOutputStream) -> Unit) {
        directory.mkdirs()
        val payloadBuffer = ByteArrayOutputStream()
        DataOutputStream(payloadBuffer).use(encode)
        val payload = payloadBuffer.toByteArray()
        require(payload.size in 1..MAX_BYTES) { "Research dossier exceeds its storage limit." }
        val temp = File.createTempFile("research-", ".tmp", directory)
        try {
            temp.outputStream().use { raw ->
                DataOutputStream(raw).use { out ->
                    out.writeUTF(MAGIC)
                    out.writeInt(SCHEMA_VERSION)
                    out.writeInt(payload.size)
                    out.write(payload)
                    out.write(sha256(payload))
                    out.flush()
                }
            }
            Files.move(temp.toPath(), file.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        } finally {
            temp.delete()
        }
    }

    private fun <T> readEnvelope(file: File, decode: (DataInputStream) -> T): T {
        require(file.length() in 1..(MAX_BYTES + 64).toLong()) { "Invalid research record size." }
        val bytes = file.readBytes()
        val envelope = DataInputStream(ByteArrayInputStream(bytes))
        require(envelope.readUTF() == MAGIC) { "Unrecognized research record." }
        require(envelope.readInt() == SCHEMA_VERSION) { "Unsupported research schema." }
        val size = envelope.readInt()
        require(size in 1..MAX_BYTES && size + CHECKSUM_BYTES == envelope.available()) { "Invalid research payload size." }
        val payload = ByteArray(size)
        envelope.readFully(payload)
        val checksum = ByteArray(CHECKSUM_BYTES)
        envelope.readFully(checksum)
        require(envelope.available() == 0 && MessageDigest.isEqual(checksum, sha256(payload))) {
            "Research record integrity check failed."
        }
        return decode(DataInputStream(ByteArrayInputStream(payload)))
    }

    private fun DataOutputStream.writeDossier(value: ResearchDossier) {
        writeString(value.id); writeLong(value.createdAt); writeLong(value.updatedAt)
        writePlan(value.plan); writeInt(value.phase.ordinal)
        writeInt(value.sources.size)
        value.sources.forEach { source ->
            writeString(source.canonicalUrl); writeNullableString(source.title); writeLong(source.retrievedAt)
            writeString(source.contentHash); writeString(source.domain); writeNullableString(source.duplicateOf)
            writeString(source.snippet); writeBoolean(source.trust.evaluated); writeDouble(source.trust.score)
            writeString(source.trust.explanation); writeDouble(source.trust.authority)
            writeDouble(source.trust.relevance); writeDouble(source.trust.freshness)
            writeDouble(source.trust.corroboration); writeDouble(source.trust.duplicatePenalty)
            writeNullableString(source.versionApplicability); writeNullableString(source.dateApplicability)
        }
        writeInt(value.graph.claims.size); value.graph.claims.forEach { writeClaim(it) }
        writeInt(value.graph.evidence.size); value.graph.evidence.forEach { link ->
            writeString(link.claimId); writeString(link.sourceUrl); writeInt(link.relation.ordinal)
            writeDouble(link.supportScore); writeString(link.explanation)
        }
        writeInt(value.graph.relationships.size); value.graph.relationships.forEach { edge ->
            writeString(edge.fromClaimId); writeString(edge.toClaimId); writeInt(edge.relation.ordinal)
        }
        writeDoubleMap(value.graph.coverage)
        writeInt(value.graph.independentSourceCounts.size)
        value.graph.independentSourceCounts.toSortedMap().forEach { (key, count) -> writeString(key); writeInt(count) }
        writeInt(value.gaps.size); value.gaps.forEach {
            writeString(it.questionId); writeString(it.reason); writeString(it.suggestedQuery)
            writeBoolean(it.counterEvidence)
        }
        writeNullableString(value.synthesis?.text)
        writeStrings(value.completedSourceHashes.toList())
        writeBoolean(value.generalSearchAvailable); writeNullableString(value.partialReason)
        writeInt(value.rounds.size); value.rounds.forEach { round ->
            writeInt(round.number); writeInt(round.phase.ordinal); writeStrings(round.queryIds)
            writeStrings(round.sourceUrls); writeLong(round.startedAt)
            writeBoolean(round.completedAt != null); round.completedAt?.let { writeLong(it) }
        }
        writeInt(value.privacyClass.ordinal)
    }

    private fun DataInputStream.readDossier(): ResearchDossier {
        val id = readString(64); val created = readLong(); val updated = readLong()
        val plan = readPlan()
        val phase = ResearchPhase.values().getOrNull(readInt()) ?: error("Unknown research phase.")
        val sources = List(readCount(24)) {
            val url = readString(4_096)
            val title = readNullableString(256)
            val retrievedAt = readLong()
            val contentHash = readString(64)
            val domain = readString(512)
            val duplicateOf = readNullableString(4_096)
            val snippet = readString(2_048)
            val trust = SourceTrustSnapshot(
                readBoolean(), readDouble(), readString(256), readDouble(), readDouble(),
                readDouble(), readDouble(), readDouble()
            )
            val version = readNullableString(128)
            val date = readNullableString(128)
            ResearchSource(url, title, retrievedAt, contentHash, domain, duplicateOf, snippet, trust, version, date)
        }
        val claims = List(readCount(256)) { readClaim() }
        val evidence = List(readCount(2_048)) {
            ClaimEvidenceLink(
                readString(128), readString(4_096),
                EvidenceRelation.values().getOrNull(readInt()) ?: error("Unknown evidence relation."),
                readDouble(), readString(256)
            )
        }
        val edges = List(readCount(2_048)) {
            ResearchClaimEdge(
                readString(128), readString(128),
                ClaimRelation.values().getOrNull(readInt()) ?: error("Unknown claim relation.")
            )
        }
        val coverage = readDoubleMap(256)
        val independent = List(readCount(256)) { readString(128) to readInt() }.toMap()
        val gaps = List(readCount(32)) {
            ResearchGap(readString(64), readString(256), readString(512), readBoolean())
        }
        val synthesis = readNullableString(8_000)?.let(::ResearchSynthesisOutput)
        val hashes = readStrings(24, 64).toSet()
        val generalSearchAvailable = readBoolean()
        val partialReason = readNullableString(512)
        val rounds = List(readCount(3)) {
            val number = readInt()
            val roundPhase = ResearchPhase.values().getOrNull(readInt()) ?: error("Unknown research round phase.")
            val queries = readStrings(12, 48)
            val urls = readStrings(24, 4_096)
            val started = readLong()
            val completed = if (readBoolean()) readLong() else null
            ResearchRound(number, roundPhase, queries, urls, started, completed)
        }
        val privacyClass = PrivacyClass.values().getOrNull(readInt())
            ?: error("Unknown research dossier privacy classification.")
        return ResearchDossier(
            id, created, updated, plan, phase, sources,
            ResearchEvidenceGraph(claims, evidence, edges, coverage, independent),
            gaps, synthesis, hashes, generalSearchAvailable, partialReason, rounds, privacyClass
        )
    }

    private fun DataOutputStream.writePlan(plan: ResearchPlan) {
        writeInt(plan.mode.ordinal); writeString(plan.question)
        writeInt(plan.questions.size); plan.questions.forEach {
            writeString(it.id); writeString(it.text); writeInt(it.importance)
        }
        writeInt(plan.paths.size); plan.paths.forEach {
            writeString(it.id); writeString(it.query.text); writeString(it.query.questionId)
            writeBoolean(it.query.counterEvidence)
        }
        val b = plan.budget
        writeInt(b.maxDiscoveryQueries); writeInt(b.maxResearchRounds); writeInt(b.maxSourcesFetched)
        writeInt(b.maxBytesPerSource); writeInt(b.maxTotalBytes); writeInt(b.maxClaims)
        writeInt(b.maxRedirects); writeInt(b.requestTimeoutMillis); writeLong(b.totalDeadlineMillis)
        writeInt(b.maxCounterEvidenceAttempts); writeInt(b.maxFollowUpQueries); writeInt(b.maxSourcesPerDomain)
    }

    private fun DataInputStream.readPlan(): ResearchPlan {
        val mode = ResearchMode.values().getOrNull(readInt()) ?: error("Unknown research mode.")
        val question = readString(2_048)
        val questions = List(readCount(32)) { ResearchQuestion(readString(48), readString(512), readInt()) }
        val paths = List(readCount(12)) {
            ResearchSearchPath(readString(48), ResearchQuery(readString(512), readString(48), readBoolean()))
        }
        val budget = ResearchBudgetPolicy(
            readInt(), readInt(), readInt(), readInt(), readInt(), readInt(), readInt(), readInt(),
            readLong(), readInt(), readInt(), readInt()
        )
        return ResearchPlan(mode, question, questions, paths, budget)
    }

    private fun DataOutputStream.writeClaim(claim: Claim) {
        writeString(claim.id); writeString(claim.text); writeInt(claim.provenance.ordinal)
        writeStrings(claim.sourceReferences); writeInt(claim.validationState.ordinal); writeDouble(claim.confidence)
        writeLong(claim.createdAt); writeStrings(claim.validationEvidenceReferences)
    }

    private fun DataInputStream.readClaim() = Claim(
        readString(128), readString(2_048),
        ProvenanceType.values().getOrNull(readInt()) ?: error("Unknown claim provenance."),
        readStrings(24, 4_096),
        ValidationState.values().getOrNull(readInt()) ?: error("Unknown validation state."),
        readDouble(), readLong(), readStrings(24, 4_096)
    )

    private fun DataOutputStream.writeDoubleMap(map: Map<String, Double>) {
        writeInt(map.size); map.toSortedMap().forEach { (key, value) -> writeString(key); writeDouble(value) }
    }

    private fun DataInputStream.readDoubleMap(max: Int): Map<String, Double> =
        List(readCount(max)) { readString(128) to readDouble() }.toMap().also {
            require(it.values.all { value -> value.isFinite() && value in 0.0..1.0 })
        }

    private fun DataOutputStream.writeStrings(values: List<String>) {
        writeInt(values.size); values.forEach { writeString(it) }
    }

    private fun DataInputStream.readStrings(max: Int, maxLength: Int): List<String> =
        List(readCount(max)) { readString(maxLength) }

    private fun DataOutputStream.writeString(value: String) {
        require(value.length <= 8_000)
        val bytes = value.toByteArray(Charsets.UTF_8)
        require(bytes.size <= MAX_STRING_BYTES)
        writeInt(bytes.size); write(bytes)
    }

    private fun DataInputStream.readString(maxLength: Int): String {
        val size = readInt()
        require(size in 0..MAX_STRING_BYTES && size <= available())
        val bytes = ByteArray(size); readFully(bytes)
        return String(bytes, Charsets.UTF_8).also { require(it.length <= maxLength) }
    }

    private fun DataOutputStream.writeNullableString(value: String?) {
        writeBoolean(value != null); if (value != null) writeString(value)
    }

    private fun DataInputStream.readNullableString(maxLength: Int = 8_000): String? =
        if (readBoolean()) readString(maxLength) else null

    private fun DataInputStream.readCount(max: Int): Int = readInt().also { require(it in 0..max) }

    companion object {
        private const val MAGIC = "DEVICE_AI_RESEARCH"
        private const val SCHEMA_VERSION = 3
        private const val MAX_BYTES = 1_000_000
        private const val MAX_STRING_BYTES = 32_000
        private const val CHECKSUM_BYTES = 32

        private fun sha256(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes)
    }
}
