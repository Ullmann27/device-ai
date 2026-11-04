package at.heinz.deviceai.learning

import at.heinz.deviceai.cognitive.IdentitySigner
import at.heinz.deviceai.cognitive.MemoryMutationLedger
import at.heinz.deviceai.cognitive.MemoryMutationPayload
import at.heinz.deviceai.cognitive.MemoryMutationReason
import at.heinz.deviceai.cognitive.MemoryRecord
import at.heinz.deviceai.cognitive.MemoryType
import at.heinz.deviceai.cognitive.MemoryWriteContext
import at.heinz.deviceai.cognitive.MemoryWritePolicy
import at.heinz.deviceai.cognitive.PrivacyClass
import at.heinz.deviceai.cognitive.ProofCarryingMemoryMutation
import at.heinz.deviceai.cognitive.ProvenanceType
import at.heinz.deviceai.cognitive.ValidationState
import at.heinz.deviceai.cognitive.memoryRecordHash
import java.io.File
import java.io.FileOutputStream
import java.util.Base64

enum class CorrectionAuditStatus { NOT_CONFIGURED, VERIFIED, TAMPERED, UNSIGNED_LEDGER_CHANGE }
data class CorrectionAudit(val status:CorrectionAuditStatus, val signedMutations:Int, val unsignedRecordIds:List<String>)

/**
 * M2.3 — Signed owner corrections. A SIDECAR, append-only journal of [ProofCarryingMemoryMutation]s next to the
 * LEX2 ledger (whose on-disk format is unchanged). Every owner-fact mutation (new statement, correction,
 * revocation, reconfirmation) is mapped to a [MemoryRecord], hashed with [memoryRecordHash], signed and chained.
 * [audit] proves that the current ledger state of each owner fact is exactly the last signed state.
 * Held (hypothetical/unconfirmed) statements are never signed as facts. Callers hold the core lock.
 */
class SignedOwnerCorrectionJournal(private val directory:File, private val signer:IdentitySigner) {
    private val file get() = File(directory, FILE_NAME)

    /** All mutations, verified (signature + chain). Throws [IllegalStateException] if the journal is not intact. */
    fun entries():List<ProofCarryingMemoryMutation> {
        val f = file
        if (!f.exists()) return emptyList()
        check(f.length() <= MAX_BYTES) { "correction journal exceeds bound" }
        val ledger = MemoryMutationLedger(signer)
        f.readLines(Charsets.UTF_8).filter { it.isNotEmpty() }.forEachIndexed { i, line ->
            val m = runCatching { decode(line) }.getOrElse { throw IllegalStateException("journal line ${i + 1} unreadable") }
            runCatching { ledger.append(m) }.getOrElse { throw IllegalStateException("journal line ${i + 1} not verifiable") }
        }
        check(ledger.verifyChain()) { "journal chain broken" }
        return ledger.all()
    }

    /** Signs and appends the difference between two owner-fact snapshots (id -> record). */
    internal fun recordDiff(before:Map<String, LearningExperience>, after:Map<String, LearningExperience>, atMillis:Long):Int {
        val changed = after.values.filter { a ->
            val b = before[a.id]
            b != a && !(b == null && a.status == LearningStatus.OBSERVED)       // a new held statement is no fact
        }.sortedWith(compareBy<LearningExperience> { it.updatedAt }.thenBy { it.id })
        if (changed.isEmpty()) return 0
        var previous = entries().lastOrNull()?.payload?.hash()
        val decision = POLICY.decide(MemoryWriteContext(MemoryType.SEMANTIC, ProvenanceType.USER_PROVIDED, 1.0,
            explicitlyRequested = true, containsPersonalInformation = true))
        val activeBefore = before.values.filter { it.status in ACTIVE }
        val lines = StringBuilder()
        for (a in changed) {
            val b = before[a.id]
            val (reason, evidence, contradictions) = classify(b, a, activeBefore)
            val after = toMemoryRecord(a)
            val id = "m2c-" + ContinuumTemporalMemory.sha(a.id + "|" + memoryRecordHash(after) + "|" + previous.orEmpty()).take(32)
            val m = ProofCarryingMemoryMutation.create(id, b?.let(::toMemoryRecord), after, reason, evidence,
                contradictions, previous, decision, signer, maxOf(atMillis, 0L))
            lines.append(encode(m)).append('\n')
            previous = m.payload.hash()
        }
        directory.mkdirs()
        FileOutputStream(file, true).use { out -> out.write(lines.toString().toByteArray(Charsets.UTF_8)); out.fd.sync() }
        return changed.size
    }

    /** Verifies the journal and that every owner fact in [ledger] equals its last signed state. */
    fun audit(ledger:List<LearningExperience>):CorrectionAudit {
        val entries = try { entries() } catch (e:IllegalStateException) {
            return CorrectionAudit(CorrectionAuditStatus.TAMPERED, 0, emptyList())
        }
        val last = HashMap<String, String>()
        entries.forEach { last[it.payload.recordId] = it.payload.afterHash }
        val unsigned = ownerFacts(ledger).values.filter { e ->
            val signed = last[e.id]
            if (signed == null) e.status != LearningStatus.OBSERVED else signed != memoryRecordHash(toMemoryRecord(e))
        }.map { it.id }.sorted()
        return CorrectionAudit(if (unsigned.isEmpty()) CorrectionAuditStatus.VERIFIED else CorrectionAuditStatus.UNSIGNED_LEDGER_CHANGE,
            entries.size, unsigned)
    }

    private fun classify(b:LearningExperience?, a:LearningExperience, activeBefore:List<LearningExperience>):
        Triple<MemoryMutationReason, List<String>, List<String>> = when {
        a.status == LearningStatus.REVOKED || a.status == LearningStatus.REJECTED ->
            Triple(MemoryMutationReason.USER_CORRECTION, listOf("owner-revocation:${a.id}"), emptyList())
        b == null && isCorrection(a.content) ->
            Triple(MemoryMutationReason.USER_CORRECTION, listOf("owner-statement:${a.id}"), superseded(a, activeBefore))
        b == null -> Triple(MemoryMutationReason.NEW_EVIDENCE, listOf("owner-statement:${a.id}"), emptyList())
        b.status !in ACTIVE && a.status in ACTIVE ->
            Triple(MemoryMutationReason.NEW_EVIDENCE, listOf("owner-reconfirmation:${a.id}"), emptyList())
        else -> Triple(MemoryMutationReason.CONSOLIDATION, listOf("ledger-transition:${a.id}"), emptyList())
    }

    private fun superseded(a:LearningExperience, activeBefore:List<LearningExperience>):List<String> {
        fun key(e:LearningExperience) = ClaimGrammar.extract(e.content, e.id, MemoryTier.LONG_TERM, e.createdAt,
            OwnerStatementModality.classify(e.content), ContinuumTemporalMemory.VIENNA)?.let { it.subject to it.predicate }
        val k = key(a) ?: return emptyList()
        return activeBefore.filter { it.id != a.id && key(it) == k }.map { it.id }.sorted().take(128)
    }

    private fun encode(m:ProofCarryingMemoryMutation):String {
        val p = m.payload
        fun b(s:String) = Base64.getUrlEncoder().withoutPadding().encodeToString(s.toByteArray(Charsets.UTF_8))
        return listOf("v1", p.mutationId, b(p.recordId), p.beforeHash ?: "-", p.afterHash, p.reason.name, p.provenance.name,
            b(p.evidenceReferences.joinToString("\n")), b(p.contradictionReferences.joinToString("\n")),
            p.previousMutationHash ?: "-", p.createdAtMillis.toString(),
            Base64.getUrlEncoder().withoutPadding().encodeToString(m.signature)).joinToString("\t")
    }

    private fun decode(line:String):ProofCarryingMemoryMutation {
        val f = line.split('\t')
        require(f.size == 12 && f[0] == "v1")
        fun s(x:String) = String(Base64.getUrlDecoder().decode(x), Charsets.UTF_8)
        fun list(x:String) = s(x).split('\n').filter { it.isNotEmpty() }
        val payload = MemoryMutationPayload(f[1], s(f[2]), f[3].takeUnless { it == "-" }, f[4],
            MemoryMutationReason.valueOf(f[5]), ProvenanceType.valueOf(f[6]), list(f[7]), list(f[8]),
            f[9].takeUnless { it == "-" }, f[10].toLong())
        return ProofCarryingMemoryMutation(payload, Base64.getUrlDecoder().decode(f[11]))
    }

    companion object {
        const val FILE_NAME = "owner-corrections-pcm-v1.log"
        private const val MAX_BYTES = 8L * 1024 * 1024
        private val POLICY = MemoryWritePolicy()
        private val ACTIVE = setOf(LearningStatus.CONFIRMED, LearningStatus.LEARNED, LearningStatus.REPLAY_PENDING)
        private val CORRECTION = Regex("(?iu)^\\s*(?:korrektur|berichtigung)\\s*:|\\bsondern\\b|\\bstattdessen\\b")
        internal fun isCorrection(text:String) = CORRECTION.containsMatchIn(text)

        /** Owner-fact snapshot used for diffs and audits (USER_FACT + OWNER_CONFIRMED). */
        internal fun ownerFacts(all:List<LearningExperience>):Map<String, LearningExperience> =
            all.filter { it.type == LearningExperienceType.USER_FACT && it.trust == LearningTrustClass.OWNER_CONFIRMED }
                .associateBy { it.id }

        /** Deterministic LEX2 → MemoryEcology mapping; any status/time/content change changes the hash. */
        fun toMemoryRecord(e:LearningExperience):MemoryRecord = MemoryRecord(
            id = e.id, type = MemoryType.SEMANTIC, content = e.content, provenance = ProvenanceType.USER_PROVIDED,
            confidence = 1.0, createdAt = e.createdAt, updatedAt = e.updatedAt,
            tags = listOfNotNull("status:${e.status.name}", "trust:${e.trust.name}", e.annotation?.let { "annotation:$it" }),
            sourceReference = e.sourceRef,
            validationState = if (e.status == LearningStatus.REVOKED || e.status == LearningStatus.REJECTED)
                ValidationState.CONTRADICTED else ValidationState.UNVERIFIED,
            privacyClass = PrivacyClass.PRIVATE,
            validationEvidenceReferences = listOf("lex2-sha256:${e.contentHash}"))
    }
}
