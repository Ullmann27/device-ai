package at.heinz.deviceai.learning

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.LinkOption
import java.security.MessageDigest
import java.util.Collections
import java.util.UUID

/** A restored statement presented for a NEW owner decision, never an active fact. */
data class RecoveredOwnerFact internal constructor(val id:String, val contentHash:String, val content:String)

class OwnerRecoveryReview internal constructor(val fingerprint:String, facts:List<RecoveredOwnerFact>) {
    val facts:List<RecoveredOwnerFact> = Collections.unmodifiableList(facts.toList())
}

enum class OwnerConfirmationSource { OWNER_PILL, OWNER_YES, MODEL_TOOL, FOREIGN_CONTENT }

/** Construct only in the trusted owner-decision callback, bound to the facts actually displayed there.
 * This is not a registered model tool or a capability to execute any device action. */
data class OwnerReconfirmationProof(
    val source:OwnerConfirmationSource,
    val approvedByOwner:Boolean,
    val turnId:String,
    val atMillis:Long,
    val reviewFingerprint:String,
    val confirmedFactHashes:Map<String,String>
)

/** Consent metadata for the existing ledger, not another memory store. Every method runs under its OS lease.
 * The marker is committed and directory-synced BEFORE a recovered primary is published. It is never deleted,
 * including when the last fact is reconfirmed. Its complete checksummed empty state differs from missing data.
 */
internal class OwnerMemoryRecovery(private val directory:File) {
    val markerFile=File(directory,MARKER_NAME)
    private val evidenceRoot=File(directory,"owner-recovery")

    fun hasHistory()=Files.exists(markerFile.toPath(),LinkOption.NOFOLLOW_LINKS) ||
        Files.exists(evidenceRoot.toPath(),LinkOption.NOFOLLOW_LINKS)

    internal data class Pending(val generation:String, val facts:Map<String,String>, val fingerprint:String)

    fun pending(allowMissingDuringRepair:Boolean=false):Pending {
        if(!Files.exists(markerFile.toPath(),LinkOption.NOFOLLOW_LINKS)) {
            check(allowMissingDuringRepair || !Files.exists(evidenceRoot.toPath(),LinkOption.NOFOLLOW_LINKS)) {
                "Recovery consent marker missing; owner facts unavailable"
            }
            return Pending("",emptyMap(),"none")
        }
        val bytes=Lex3LearningExperienceStore.readBounded(markerFile,MAX_MARKER_BYTES)
        return try { decode(bytes) } catch(e:Exception) {
            throw IOException("Recovery consent marker invalid; owner facts unavailable",e)
        }
    }

    fun project(items:List<LearningExperience>, pending:Pending=pending()):List<LearningExperience> =
        items.map { e -> if(isActiveOwnerFact(e) && e.id in pending.facts)
            e.copy(status=LearningStatus.OBSERVED) else e }

    fun fingerprint(ledgerFingerprint:String, pending:Pending):String =
        if(pending.fingerprint=="none") ledgerFingerprint else "$ledgerFingerprint:${pending.fingerprint}"

    /** A durably committed revocation cannot be reactivated by clearing an obsolete pending marker. */
    fun retireWithdrawn(items:List<LearningExperience>) {
        val p=pending()
        val active=items.filter(::isActiveOwnerFact).mapTo(HashSet()) { it.id }
        val retained=p.facts.filterKeys { it in active }
        if(retained.size!=p.facts.size)writePending(retained)
    }

    fun review(items:List<LearningExperience>, ledgerFingerprint:String):OwnerRecoveryReview {
        val p=pending()
        return OwnerRecoveryReview(fingerprint(ledgerFingerprint,p),items.filter {
            isActiveOwnerFact(it) && p.facts[it.id]==it.contentHash
        }.map { RecoveredOwnerFact(it.id,it.contentHash,it.content) })
    }

    fun reconfirm(items:List<LearningExperience>, ledgerFingerprint:String,
                  review:OwnerRecoveryReview, proof:OwnerReconfirmationProof?):Set<String> {
        if(proof==null || !proof.approvedByOwner ||
            proof.source !in setOf(OwnerConfirmationSource.OWNER_PILL,OwnerConfirmationSource.OWNER_YES) ||
            !proof.turnId.matches(Regex("[A-Za-z0-9_.:-]{1,64}")) || proof.atMillis<0)
            throw SecurityException("Owner reconfirmation requires an explicit current owner decision")
        val p=pending()
        val currentFingerprint=fingerprint(ledgerFingerprint,p)
        if(review.fingerprint!=currentFingerprint || proof.reviewFingerprint!=currentFingerprint)
            throw SecurityException("Owner recovery review changed; display a new review")
        val selected=proof.confirmedFactHashes.toMap()
        if(selected.isEmpty() || selected.size>FileLearningExperienceStore.MAX_DURABLE_RECORDS)
            throw SecurityException("Select the concrete owner facts shown in the review")
        val displayed=review.facts.associateBy { it.id }
        val current=items.associateBy { it.id }
        if(selected.any { (id,hash) ->
                val e=current[id]
                e==null || !isActiveOwnerFact(e) || e.contentHash!=hash || p.facts[id]!=hash ||
                    displayed[id]?.contentHash!=hash || displayed[id]?.content!=e.content
            }) throw SecurityException("Owner confirmation does not match the displayed restored facts")
        writePending(p.facts-selected.keys)
        return selected.keys.toSet()
    }

    /** All restored active owner facts are held, regardless of what can be parsed from damaged bytes.
     * In particular, an unreadable/obliterated revocation ID is never evidence of renewed consent. */
    fun prepare(items:List<LearningExperience>, damagedPrimary:ByteArray?) {
        val old=pending(allowMissingDuringRepair=true)
        val retained=items.mapTo(HashSet()) { it.id }
        val marks=old.facts.filterKeys { it in retained }.toMutableMap()
        items.filter(::isActiveOwnerFact).forEach { marks[it.id]=it.contentHash }
        preserveEvidence(damagedPrimary)
        writePending(marks)
    }

    private fun writePending(facts:Map<String,String>) {
        val generation=UUID.randomUUID().toString()
        val payload=ByteArrayOutputStream().also { buffer -> DataOutputStream(buffer).use { out ->
            out.writeInt(MAGIC);out.writeInt(VERSION);out.writeUTF(generation);out.writeInt(facts.size)
            facts.toSortedMap().forEach { (id,hash) -> out.writeUTF(id);out.writeUTF(hash) }
        } }.toByteArray()
        require(facts.size<=FileLearningExperienceStore.MAX_DURABLE_RECORDS)
        val bytes=payload+digest(payload)
        require(bytes.size<=MAX_MARKER_BYTES)
        beforeWriteForTest?.invoke(markerFile)
        Lex3LearningExperienceStore.writeAtomic(markerFile,bytes)
        Lex3LearningExperienceStore.syncDirectory(directory)
    }

    private fun decode(bytes:ByteArray):Pending {
        require(bytes.size in 48..MAX_MARKER_BYTES)
        val payload=bytes.copyOfRange(0,bytes.size-32)
        require(MessageDigest.isEqual(digest(payload),bytes.copyOfRange(payload.size,bytes.size)))
        val input=DataInputStream(ByteArrayInputStream(payload))
        require(input.readInt()==MAGIC && input.readInt()==VERSION)
        val generation=input.readUTF().also { require(UUID.fromString(it).toString()==it) }
        val count=input.readInt().also { require(it in 0..FileLearningExperienceStore.MAX_DURABLE_RECORDS) }
        val facts=LinkedHashMap<String,String>()
        repeat(count) {
            val id=input.readUTF();val hash=input.readUTF()
            require(id.matches(Regex("lx-[a-f0-9]{24}")) && hash.matches(Regex("[a-f0-9]{64}")))
            require(id=="lx-"+hash.take(24) && facts.put(id,hash)==null)
        }
        require(input.available()==0)
        return Pending(generation,facts,hex(digest(bytes)))
    }

    /** Keep the original manifest/snapshot and every bounded segment before a recovery publication.
     * Evidence is immutable and outside the normal segment cleanup directory. Its content identity also
     * makes a retry after failed I/O idempotent instead of copying the same damaged data indefinitely. */
    private fun preserveEvidence(primary:ByteArray?) {
        data class SegmentEvidence(val file:File,val hash:String)
        val segments=ArrayList<SegmentEvidence>()
        val identity=MessageDigest.getInstance("SHA-256")
        identity.update(if(primary==null)0.toByte() else 1.toByte())
        primary?.let { identity.update(digest(it)) }
        val segmentRoot=File(directory,"experiences-v3")
        if(Files.exists(segmentRoot.toPath(),LinkOption.NOFOLLOW_LINKS)) {
            Lex3LearningExperienceStore.requireDirectory(segmentRoot)
            var total=0L
            Files.newDirectoryStream(segmentRoot.toPath(),"*.seg").use { paths ->
                val files=ArrayList<File>()
                for(path in paths) {
                    require(files.size<2*FileLearningExperienceStore.MAX_DURABLE_RECORDS/Lex3LearningExperienceStore.SEGMENT_RECORDS)
                    files+=path.toFile()
                }
                files.sortBy { it.name }
                for(file in files) {
                    require(file.name.matches(Regex("[a-f0-9]{64}\\.seg")))
                    val bytes=readEvidence(file)
                    total+=bytes.size
                    require(total<=2*Lex3LearningExperienceStore.MAX_TOTAL_BYTES)
                    val hash=hex(digest(bytes))
                    segments+=SegmentEvidence(file,hash)
                    identity.update(file.name.toByteArray(Charsets.US_ASCII));identity.update(digest(bytes))
                }
            }
        }
        if(Files.exists(evidenceRoot.toPath(),LinkOption.NOFOLLOW_LINKS))
            Lex3LearningExperienceStore.requireDirectory(evidenceRoot)
        else Files.createDirectory(evidenceRoot.toPath())
        Lex3LearningExperienceStore.syncDirectory(directory)
        val target=File(evidenceRoot,hex(identity.digest()))
        if(Files.exists(target.toPath(),LinkOption.NOFOLLOW_LINKS))Lex3LearningExperienceStore.requireDirectory(target)
        else Files.createDirectory(target.toPath())
        Lex3LearningExperienceStore.syncDirectory(evidenceRoot)
        if(primary!=null)writeEvidence(File(target,"primary.bin"),primary)
        for(segment in segments) {
            val bytes=readEvidence(segment.file)
            check(hex(digest(bytes))==segment.hash) { "Recovery evidence changed during the ledger lease" }
            writeEvidence(File(target,segment.file.name),bytes)
        }
        writeEvidence(File(target,"evidence.txt"),
            "format=1\nprimary=${if(primary==null)"missing" else "present"}\nsegments=${segments.size}\n".toByteArray(Charsets.UTF_8))
        Lex3LearningExperienceStore.syncDirectory(target)
        Lex3LearningExperienceStore.syncDirectory(evidenceRoot)
    }

    private fun readEvidence(file:File):ByteArray {
        Lex3LearningExperienceStore.requireRegular(file)
        return if(file.length()==0L)ByteArray(0) else Lex3LearningExperienceStore.readBounded(file,LearningExperienceCodec.MAX_BYTES)
    }

    private fun writeEvidence(file:File,bytes:ByteArray) {
        if(Files.exists(file.toPath(),LinkOption.NOFOLLOW_LINKS))
            check(readEvidence(file).contentEquals(bytes)) { "Recovery evidence is immutable" }
        else Lex3LearningExperienceStore.writeAtomic(file,bytes)
    }

    companion object {
        const val MARKER_NAME="owner-reconfirmation-v1.bin"
        private const val MAGIC=0x4f524331
        private const val VERSION=1
        private const val MAX_MARKER_BYTES=512*1024
        private val ACTIVE=setOf(LearningStatus.CONFIRMED,LearningStatus.LEARNED,LearningStatus.REPLAY_PENDING)
        fun isActiveOwnerFact(e:LearningExperience)=e.type==LearningExperienceType.USER_FACT &&
            e.trust==LearningTrustClass.OWNER_CONFIRMED && e.status in ACTIVE
        private fun digest(bytes:ByteArray)=MessageDigest.getInstance("SHA-256").digest(bytes)
        private fun hex(bytes:ByteArray)=bytes.joinToString("") { "%02x".format(it) }
        /** Fault injection only; production leaves this null. */
        @Volatile internal var beforeWriteForTest:((File)->Unit)?=null
    }
}
