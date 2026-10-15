package at.heinz.deviceai.learning

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.IOException
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest

enum class LearningExperienceType {
    INTENT_LABEL, SOURCE_TEXT, SEMANTIC_PAIR, TRAINING_AUDIT,
    USER_FACT, COMMAND_MAPPING, USER_FEEDBACK, ANSWER_CORRECTION
}
enum class LearningProvenance { USER_CONFIRMED, MENTOR_WIKIPEDIA, SYNTHETIC_FIXTURE, LOCAL_TRAINING, REVIEWED_WEB_SOURCE }
enum class LearningTrustClass { OWNER_CONFIRMED, SOURCE_OBSERVATION, SYNTHETIC, SYSTEM_MEASUREMENT }
enum class LearningStatus { OBSERVED, CONFIRMED, LEARNED, REPLAY_PENDING, REJECTED, REVOKED }

data class LearningExperience(
    val id:String,
    val type:LearningExperienceType,
    val provenance:LearningProvenance,
    val trust:LearningTrustClass,
    val status:LearningStatus,
    val content:String,
    val annotation:String?=null,
    val sourceRef:String?=null,
    val contentHash:String,
    val createdAt:Long,
    val updatedAt:Long
) {
    init {
        require(id.matches(Regex("lx-[a-f0-9]{24}")))
        require(content.length in 1..2048)
        require(annotation==null || annotation.length in 1..160)
        require(sourceRef==null || sourceRef.length in 1..500)
        require(contentHash.matches(Regex("[a-f0-9]{64}")))
        require(createdAt>=0 && updatedAt>=createdAt)
        require(contentHash==LearningExperienceFactory.hash(type,provenance,trust,content,annotation,sourceRef))
        require(id=="lx-"+contentHash.take(24))
    }
}

object LearningExperienceFactory {
    fun create(
        type:LearningExperienceType,
        provenance:LearningProvenance,
        trust:LearningTrustClass,
        status:LearningStatus,
        content:String,
        annotation:String?=null,
        sourceRef:String?=null,
        now:Long=System.currentTimeMillis()
    ):LearningExperience {
        require(now>=0)
        val cleaned=content.trim()
        val label=annotation?.trim()?.takeIf(String::isNotEmpty)
        val source=sourceRef?.trim()?.takeIf(String::isNotEmpty)
        val digest=hash(type,provenance,trust,cleaned,label,source)
        return LearningExperience(
            id="lx-"+digest.take(24), type=type, provenance=provenance, trust=trust,
            status=status, content=cleaned, annotation=label, sourceRef=source,
            contentHash=digest, createdAt=now, updatedAt=now
        )
    }

    internal fun hash(
        type:LearningExperienceType,
        provenance:LearningProvenance,
        trust:LearningTrustClass,
        content:String,
        annotation:String?,
        sourceRef:String?
    ):String {
        val digest=MessageDigest.getInstance("SHA-256")
        listOf(type.name,provenance.name,trust.name,content,annotation.orEmpty(),sourceRef.orEmpty())
            .forEach { value ->
                digest.update(value.toByteArray(Charsets.UTF_8))
                digest.update(0.toByte())
            }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}

data class LearningExperienceStats(
    val total:Int,
    val confirmed:Int,
    val learned:Int,
    val replayPending:Int,
    val sourceObservations:Int,
    val intentLabels:Int,
    val audits:Int,
    val userFacts:Int,
    val commandMappings:Int,
    val revoked:Int
)

class FileLearningExperienceStore(private val directory:File) {
    private val primary=File(directory,"experiences-v2.bin")
    private val backup=File(directory,"experiences-v2.lkg")

    fun all():List<LearningExperience> = synchronized(FILE_LOCK) { loadValidated() }

    fun append(experience:LearningExperience):Boolean = synchronized(FILE_LOCK) {
        val current=loadValidated()
        val existing=current.firstOrNull { it.id==experience.id }
        if(existing!=null) {
            require(existing.contentHash==experience.contentHash) { "Conflicting learning experience identity" }
            return@synchronized false
        }
        val next=(current+experience)
            .sortedWith(compareBy<LearningExperience>{it.createdAt}.thenBy{it.id})
            .takeLast(MAX_RECORDS)
        save(next)
        true
    }

    /**
     * Local ratings have a strict separate retention budget. A thumbs-up/down
     * must NEVER evict an older owner fact, source observation or training audit
     * when the shared Experience ledger approaches MAX_RECORDS.
     *
     * Only the oldest feedback entries may be compacted; if there is no free
     * room after that, feedback fails closed without modifying the ledger.
     */
    fun appendFeedback(experience:LearningExperience):Boolean=synchronized(FILE_LOCK) {
        require(experience.type==LearningExperienceType.USER_FEEDBACK)
        val current=loadValidated()
        if(current.any { it.id==experience.id })return@synchronized false
        val existingRatings=current.filter { it.type==LearningExperienceType.USER_FEEDBACK }
            .sortedWith(compareBy<LearningExperience> { it.createdAt }.thenBy { it.id })
        val oldIds=existingRatings.take((existingRatings.size-23).coerceAtLeast(0))
            .map { it.id }.toSet()
        val retained=current.filterNot { it.id in oldIds }
        check(retained.size<MAX_RECORDS) {
            "Experience ledger is full; user facts cannot be displaced by feedback"
        }
        save((retained+experience).sortedWith(
            compareBy<LearningExperience> { it.createdAt }.thenBy { it.id }))
        true
    }

    /**
     * Atomically replace a confirmed answer for its exact question. Corrections
     * have a 16-entry retention budget and cannot evict any other experience.
     */
    fun putAnswerCorrection(item:LearningExperience,now:Long):Boolean=synchronized(FILE_LOCK) {
        require(item.type==LearningExperienceType.ANSWER_CORRECTION &&
            item.trust==LearningTrustClass.OWNER_CONFIRMED &&
            item.provenance==LearningProvenance.USER_CONFIRMED &&
            item.status==LearningStatus.CONFIRMED &&
            item.annotation?.matches(Regex("query-sha256:[a-f0-9]{64}"))==true)
        require(now>=0)
        val current=loadValidated()
        // Different writes can share the same wall-clock millisecond. Make
        // correction timestamps strictly monotonic so retention means oldest
        // actual confirmation, not lexicographically smallest content hash.
        val latest=current.asSequence()
            .filter { it.type==LearningExperienceType.ANSWER_CORRECTION }
            .maxOfOrNull { it.updatedAt } ?: -1L
        check(latest<Long.MAX_VALUE) { "Correction timestamp exhausted" }
        val effectiveNow=maxOf(now,latest+1)
        val same=current.firstOrNull { it.id==item.id }
        if(same?.status==LearningStatus.CONFIRMED &&
            current.none { it.id!=item.id && it.type==LearningExperienceType.ANSWER_CORRECTION &&
                it.annotation==item.annotation && it.status==LearningStatus.CONFIRMED })
            return@synchronized false
        val revised=current.map { e ->
            when {
                e.type!=LearningExperienceType.ANSWER_CORRECTION ||
                    e.annotation!=item.annotation -> e
                e.id==item.id -> e.copy(status=LearningStatus.CONFIRMED,updatedAt=maxOf(effectiveNow,e.updatedAt))
                e.status==LearningStatus.CONFIRMED ->
                    e.copy(status=LearningStatus.REVOKED,updatedAt=maxOf(effectiveNow,e.updatedAt))
                else -> e
            }
        }
        val combined=if(same==null) revised+item.copy(createdAt=effectiveNow,updatedAt=effectiveNow) else revised
        val corrections=combined.filter { it.type==LearningExperienceType.ANSWER_CORRECTION }
            .sortedWith(compareBy<LearningExperience>{it.updatedAt}.thenBy{it.id})
        // Always retain the just confirmed correction even when multiple
        // writes share the same millisecond; tie-breaking may not evict it.
        val discarded=corrections.filter { it.id!=item.id }
            .take((corrections.size-16).coerceAtLeast(0))
            .map { it.id }.toSet()
        val retained=combined.filterNot { it.id in discarded }
        check(retained.size<=MAX_RECORDS) {
            "Experience ledger full; an answer correction must not evict owner facts"
        }
        save(retained.sortedWith(compareBy<LearningExperience>{it.createdAt}.thenBy{it.id}))
        true
    }

    /** One read and at most one write for a batch; ids already present (any status) are skipped. */
    fun appendMissing(items:List<LearningExperience>):Int = synchronized(FILE_LOCK) {
        val current=loadValidated()
        val known=current.associateBy { it.id }
        val added=LinkedHashMap<String,LearningExperience>()
        for(item in items) {
            val existing=known[item.id] ?: added[item.id]
            if(existing!=null) {
                require(existing.contentHash==item.contentHash) { "Conflicting learning experience identity" }
                continue
            }
            added[item.id]=item
        }
        if(added.isEmpty())return@synchronized 0
        save((current+added.values)
            .sortedWith(compareBy<LearningExperience>{it.createdAt}.thenBy{it.id})
            .takeLast(MAX_RECORDS))
        added.size
    }

    fun updateStatus(id:String,status:LearningStatus,now:Long=System.currentTimeMillis()):Boolean =
        synchronized(FILE_LOCK) {
            require(now>=0)
            val current=loadValidated()
            val index=current.indexOfFirst { it.id==id }
            if(index<0)return@synchronized false
            val old=current[index]
            if(old.status==status)return@synchronized true
            val next=current.toMutableList()
            next[index]=old.copy(status=status,updatedAt=maxOf(now,old.updatedAt))
            save(next)
            true
        }

    fun updateMatching(
        type:LearningExperienceType,
        content:String?=null,
        status:LearningStatus,
        now:Long=System.currentTimeMillis()
    ):Int = synchronized(FILE_LOCK) {
        require(now>=0)
        val current=loadValidated()
        var changed=0
        val next=current.map { item ->
            if(item.type==type && (content==null || item.content==content) && item.status!=status) {
                changed++
                item.copy(status=status,updatedAt=maxOf(now,item.updatedAt))
            } else item
        }
        if(changed>0)save(next)
        changed
    }

    fun replay(type:LearningExperienceType,limit:Int=64):List<LearningExperience> =
        synchronized(FILE_LOCK) {
            require(limit in 1..128)
            loadValidated().asReversed().asSequence()
                .filter { it.type==type && it.status in setOf(
                    LearningStatus.OBSERVED,LearningStatus.CONFIRMED,
                    LearningStatus.LEARNED,LearningStatus.REPLAY_PENDING
                ) }
                .take(limit).toList().asReversed()
        }

    fun stats():LearningExperienceStats {
        val items=all()
        return LearningExperienceStats(
            total=items.size,
            confirmed=items.count { it.status==LearningStatus.CONFIRMED },
            learned=items.count { it.status==LearningStatus.LEARNED },
            replayPending=items.count { it.status==LearningStatus.REPLAY_PENDING },
            sourceObservations=items.count { it.type==LearningExperienceType.SOURCE_TEXT },
            intentLabels=items.count { it.type==LearningExperienceType.INTENT_LABEL },
            audits=items.count { it.type==LearningExperienceType.TRAINING_AUDIT },
            userFacts=items.count { it.type==LearningExperienceType.USER_FACT },
            commandMappings=items.count { it.type==LearningExperienceType.COMMAND_MAPPING },
            revoked=items.count { it.status==LearningStatus.REVOKED }
        )
    }

    private fun loadValidated():List<LearningExperience> {
        if(!primary.exists())return emptyList()
        return runCatching { LearningExperienceCodec.decode(primary.readBytes()) }
            .getOrElse {
                if(backup.exists())LearningExperienceCodec.decode(backup.readBytes()) else throw it
            }
    }

    private fun save(items:List<LearningExperience>) {
        require(items.size<=MAX_RECORDS && items.map { it.id }.distinct().size==items.size)
        if(!directory.exists() && !directory.mkdirs())throw IOException("Cannot create learning-core directory")
        if(primary.exists()) {
            runCatching { LearningExperienceCodec.decode(primary.readBytes()) }
                .onSuccess { writeAtomically(backup,primary.readBytes()) }
        }
        writeAtomically(primary,LearningExperienceCodec.encode(items))
    }

    private fun writeAtomically(target:File,bytes:ByteArray) {
        val temp=File.createTempFile("experience-", ".tmp",directory)
        try {
            java.io.FileOutputStream(temp).use { output -> output.write(bytes);output.fd.sync() }
            try {
                Files.move(temp.toPath(),target.toPath(),StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING)
            } catch (_:AtomicMoveNotSupportedException) {
                Files.move(temp.toPath(),target.toPath(),StandardCopyOption.REPLACE_EXISTING)
            }
        } finally { temp.delete() }
    }

    companion object {
        const val MAX_RECORDS=256
        private val FILE_LOCK=Any()
    }
}

private object LearningExperienceCodec {
    private const val MAGIC=0x4c455832
    private const val MAX_BYTES=512*1024

    fun encode(items:List<LearningExperience>):ByteArray {
        val payload=ByteArrayOutputStream().also { buffer ->
            DataOutputStream(buffer).use { out ->
                out.writeInt(MAGIC);out.writeInt(items.size)
                items.forEach { e ->
                    out.writeUTF(e.id)
                    out.writeUTF(e.type.name);out.writeUTF(e.provenance.name)
                    out.writeUTF(e.trust.name);out.writeUTF(e.status.name)
                    writeString(out,e.content)
                    writeNullable(out,e.annotation);writeNullable(out,e.sourceRef)
                    out.writeUTF(e.contentHash);out.writeLong(e.createdAt);out.writeLong(e.updatedAt)
                }
            }
        }.toByteArray()
        val checksum=MessageDigest.getInstance("SHA-256").digest(payload)
        val bytes=payload+checksum
        require(bytes.size<=MAX_BYTES)
        return bytes
    }

    fun decode(bytes:ByteArray):List<LearningExperience> {
        require(bytes.size in 40..MAX_BYTES)
        val payload=bytes.copyOfRange(0,bytes.size-32)
        val checksum=bytes.copyOfRange(bytes.size-32,bytes.size)
        require(MessageDigest.isEqual(checksum,MessageDigest.getInstance("SHA-256").digest(payload))) {
            "Learning experience checksum mismatch"
        }
        try {
            val input=DataInputStream(ByteArrayInputStream(payload))
            require(input.readInt()==MAGIC)
            val count=input.readInt().also { require(it in 0..FileLearningExperienceStore.MAX_RECORDS) }
            val result=List(count) {
                LearningExperience(
                    id=input.readUTF(),
                    type=LearningExperienceType.valueOf(input.readUTF()),
                    provenance=LearningProvenance.valueOf(input.readUTF()),
                    trust=LearningTrustClass.valueOf(input.readUTF()),
                    status=LearningStatus.valueOf(input.readUTF()),
                    content=readString(input),
                    annotation=readNullable(input),
                    sourceRef=readNullable(input),
                    contentHash=input.readUTF(),
                    createdAt=input.readLong(),
                    updatedAt=input.readLong()
                )
            }
            require(input.available()==0 && result.map { it.id }.distinct().size==result.size)
            return result
        } catch(e:IOException) {
            throw IllegalArgumentException("Invalid learning experience archive",e)
        }
    }

    private fun writeString(out:DataOutputStream,value:String) {
        val bytes=value.toByteArray(Charsets.UTF_8)
        require(bytes.size<=8192)
        out.writeInt(bytes.size);out.write(bytes)
    }
    private fun readString(input:DataInputStream):String {
        val size=input.readInt().also { require(it in 0..8192 && it<=input.available()) }
        val bytes=ByteArray(size);input.readFully(bytes)
        return bytes.toString(Charsets.UTF_8)
    }
    private fun writeNullable(out:DataOutputStream,value:String?) {
        out.writeBoolean(value!=null);if(value!=null)writeString(out,value)
    }
    private fun readNullable(input:DataInputStream):String?=if(input.readBoolean())readString(input) else null
}

