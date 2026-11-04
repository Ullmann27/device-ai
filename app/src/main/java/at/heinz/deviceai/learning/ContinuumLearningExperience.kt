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
import java.util.Locale

enum class LearningExperienceType {
    INTENT_LABEL, SOURCE_TEXT, SEMANTIC_PAIR, TRAINING_AUDIT,
    USER_FACT, COMMAND_MAPPING, USER_FEEDBACK
}
enum class LearningProvenance { USER_CONFIRMED, MENTOR_WIKIPEDIA, SYNTHETIC_FIXTURE, LOCAL_TRAINING, REVIEWED_WEB_SOURCE, PUBLIC_WEB_SOURCE }
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

    /** One locked read of the ledger: its SHA-256 content fingerprint, plus the decoded records only when the
     * fingerprint differs from [knownFingerprint]. Content-keyed (not mtime/length), so a rewrite that keeps
     * mtime and size is still detected. Same validation as [all]; the on-disk format is untouched. */
    internal fun snapshotIfChanged(knownFingerprint:String?):Pair<String, List<LearningExperience>?> =
        synchronized(FILE_LOCK) {
            if(!primary.exists()) {
                check(!backup.exists()) { "Learning ledger primary missing; older backup requires explicit verification" }
                return@synchronized ABSENT_FINGERPRINT to (if(knownFingerprint==ABSENT_FINGERPRINT) null else emptyList())
            }
            val bytes=primary.readBytes()
            val fingerprint=java.security.MessageDigest.getInstance("SHA-256").digest(bytes)
                .joinToString("") { "%02x".format(it) }
            fingerprint to (if(fingerprint==knownFingerprint) null else loadState().items)
        }

    fun append(experience:LearningExperience):Boolean = synchronized(FILE_LOCK) {
        val current=loadValidated()
        val existing=current.firstOrNull { it.id==experience.id }
        if(existing!=null) {
            require(existing.contentHash==experience.contentHash) { "Conflicting learning experience identity" }
            return@synchronized false
        }
        val next=bounded(current+experience)
        if(next.none { it.id==experience.id })return@synchronized false
        save(next)
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
        val next=bounded(current+added.values)
        val admitted=next.count { it.id in added }
        if(admitted>0)save(next)
        admitted
    }

    /** One durable owner correction: retain superseded labels for audit, but
     * never replay contradictory versions of the same encoder input together.
     * Reconfirmation also refreshes an old content-addressed lesson's recency.
     */
    internal fun confirmIntentLabel(experience:LearningExperience) = synchronized(FILE_LOCK) {
        require(experience.type==LearningExperienceType.INTENT_LABEL &&
            experience.provenance==LearningProvenance.USER_CONFIRMED &&
            experience.trust==LearningTrustClass.OWNER_CONFIRMED &&
            experience.status==LearningStatus.CONFIRMED && experience.annotation in setOf("0","1"))
        val key=intentKey(experience.content).also { require(it.isNotEmpty()) }
        val current=loadValidated()
        val next=current.map { old ->
            when {
                old.id==experience.id -> old.copy(status=LearningStatus.CONFIRMED,
                    updatedAt=maxOf(old.updatedAt,experience.updatedAt))
                old.type==LearningExperienceType.INTENT_LABEL &&
                    old.provenance==LearningProvenance.USER_CONFIRMED &&
                    old.trust==LearningTrustClass.OWNER_CONFIRMED && intentKey(old.content)==key ->
                    old.copy(status=LearningStatus.REVOKED,
                        updatedAt=maxOf(old.updatedAt,experience.updatedAt))
                else -> old
            }
        }.let { items -> if(items.any { it.id==experience.id })items else items+experience }
        save(bounded(next))
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
            loadValidated().sortedWith(compareBy<LearningExperience> { it.updatedAt }
                .thenBy { it.createdAt }.thenBy { it.id }).asReversed().asSequence()
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
        if(!primary.exists()) {
            check(!backup.exists()) { "Learning ledger primary missing; older backup requires explicit verification" }
            return emptyList()
        }
        // The previous generation can predate an owner revocation/correction.
        // Preserve it for diagnosis, but never restore stale consent silently.
        return loadState().items
    }

    /** LEX4: cached replay state, validated by file length + tail chain hash; torn tail truncated in place. */
    private fun loadState():LearningExperienceCodec.LogState {
        val key=primary.absolutePath
        val bytes=primary.readBytes()
        val running=MessageDigest.getInstance("SHA-256").apply { update(bytes) }
        val fingerprint=(running.clone() as MessageDigest).digest()
        LOG_CACHE[key]?.let { c -> if(MessageDigest.isEqual(c.fileHash,fingerprint))return c }
        if(!LearningExperienceCodec.isLog(bytes))
            return LearningExperienceCodec.LogState(LearningExperienceCodec.decode(bytes),ByteArray(32),0,bytes.size.toLong(),legacy=true)
        val state=LearningExperienceCodec.decodeLog(bytes,allowTornTail=true)
        if(state.validLength<bytes.size) java.io.RandomAccessFile(primary,"rw").use { it.setLength(state.validLength);it.fd.sync() }
        if(state.validLength==bytes.size.toLong()) { state.fileHash=fingerprint;state.running=running;LOG_CACHE[key]=state }
        return state
    }

    /** Observations and audits cannot evict owner instructions or revocations.
     * Refuse protected overflow before writing rather than forget consent.
     */
    private fun bounded(items:List<LearningExperience>):List<LearningExperience> {
        fun protected(item:LearningExperience)=
            item.trust==LearningTrustClass.OWNER_CONFIRMED || item.status==LearningStatus.REVOKED
        val order=compareBy<LearningExperience> { it.createdAt }.thenBy { it.id }
        val preserved=items.filter(::protected)
        check(preserved.size<=MAX_RECORDS) { "Learning ledger capacity reached; owner records cannot be evicted" }
        return (preserved+items.filterNot(::protected).sortedWith(order)
            .takeLast(MAX_RECORDS-preserved.size)).sortedWith(order)
    }

    private fun intentKey(text:String)=text.lowercase(Locale.ROOT)
        .replace(Regex("[^\\p{L}\\p{N}]+")," ").trim()

    private fun save(items:List<LearningExperience>) {
        require(items.size<=MAX_RECORDS && items.map { it.id }.distinct().size==items.size)
        if(!directory.exists() && !directory.mkdirs())throw IOException("Cannot create learning-core directory")
        val state=if(primary.exists()) runCatching { loadState() }.getOrNull() else null
        if(state!=null && !state.legacy) {
            val previous=state.items.associateBy { it.id }
            val nextIds=items.mapTo(HashSet()) { it.id }
            val dels=state.items.filter { it.id !in nextIds }.map { it.id }
            val puts=items.filter { previous[it.id]!=it }
            val simulated=LinkedHashMap(previous).apply { dels.forEach(::remove);puts.forEach { put(it.id,it) } }
            val withinBudget=state.records+dels.size+puts.size<=maxOf(64,2*items.size)
            if(simulated.values.toList()==items && withinBudget) {
                if(dels.isEmpty() && puts.isEmpty())return
                val (bytes,hash)=LearningExperienceCodec.encodeOps(state.chainHash,dels,puts)
                java.io.FileOutputStream(primary,true).use { it.write(bytes);it.fd.sync() }
                // Next read still re-hashes the whole file; the cache only skips re-decoding when content matches.
                val running=state.running?.let { (it.clone() as MessageDigest).apply { update(bytes) } }
                if(running==null) LOG_CACHE.remove(primary.absolutePath) else
                    LOG_CACHE[primary.absolutePath]=LearningExperienceCodec.LogState(items,hash,
                        state.records+dels.size+puts.size,state.validLength+bytes.size).also {
                        it.fileHash=(running.clone() as MessageDigest).digest();it.running=running }
                return
            }
        }
        compact(items)
    }

    /** Full rewrite (migration from LEX2/LEX3 or churn compaction); previous readable generation -> LKG backup. */
    private fun compact(items:List<LearningExperience>) {
        LOG_CACHE.remove(primary.absolutePath)
        if(primary.exists()) {
            runCatching { LearningExperienceCodec.decode(primary.readBytes()) }
                .onSuccess { writeAtomically(backup,primary.readBytes()) }
        }
        writeAtomically(primary,LearningExperienceCodec.encodeLog(items))
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
        /** LEX3 capacity (was 256 in LEX2). LEX2 files (≤256) are still read and migrated on the next write. */
        const val MAX_RECORDS=4096
        const val LEGACY_LEX2_MAX_RECORDS=256

        /** Validate a separately backed-up original LEX2 file without opening or
         * writing a ledger. This is integrity checking, never consent recovery.
         * Keep the same bounded codec as the active store; no parallel parser.
         */
        internal fun inspectSnapshot(bytes:ByteArray):List<LearningExperience> =
            LearningExperienceCodec.decode(bytes)

        private val FILE_LOCK=Any()
        private val LOG_CACHE=HashMap<String,LearningExperienceCodec.LogState>()
        internal const val ABSENT_FINGERPRINT="absent"
    }
}

internal object LearningExperienceCodec {
    private const val MAGIC_LEX2=0x4c455832
    /** LEX3 = LEX2 record layout + explicit format version int after the magic. */
    private const val MAGIC=0x4c455833
    const val FORMAT_VERSION=3
    private const val MAX_BYTES=48*1024*1024

    fun encode(items:List<LearningExperience>):ByteArray {
        val payload=ByteArrayOutputStream().also { buffer ->
            DataOutputStream(buffer).use { out ->
                out.writeInt(MAGIC);out.writeInt(FORMAT_VERSION);out.writeInt(items.size)
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

    // ---- LEX4: append-only log. Header = magic + version; record = [len][payload][sha256(prevHash||payload)].
    private const val MAGIC_LEX4=0x4c455834
    private const val MAX_RECORD=64*1024
    private const val PUT:Byte=1
    private const val DEL:Byte=2
    class LogState(val items:List<LearningExperience>, val chainHash:ByteArray, val records:Int,
                   val validLength:Long, val legacy:Boolean=false) { var fileHash:ByteArray=ByteArray(0);var running:MessageDigest?=null }

    fun isLog(bytes:ByteArray)=bytes.size>=8 && java.nio.ByteBuffer.wrap(bytes,0,4).int==MAGIC_LEX4
    private fun header()=java.nio.ByteBuffer.allocate(8).putInt(MAGIC_LEX4).putInt(4).array()
    private fun seed()=MessageDigest.getInstance("SHA-256").digest(header())
    private fun link(prev:ByteArray,payload:ByteArray)=MessageDigest.getInstance("SHA-256").run { update(prev);update(payload);digest() }

    fun encodeLog(items:List<LearningExperience>):ByteArray {
        val (body,_)=encodeOps(seed(),emptyList(),items)
        return (header()+body).also { require(it.size<=MAX_BYTES) }
    }

    fun encodeOps(prev:ByteArray,dels:List<String>,puts:List<LearningExperience>):Pair<ByteArray,ByteArray> {
        var hash=prev
        val out=ByteArrayOutputStream()
        val data=DataOutputStream(out)
        fun emit(payload:ByteArray) {
            require(payload.size in 1..MAX_RECORD)
            hash=link(hash,payload);data.writeInt(payload.size);data.write(payload);data.write(hash)
        }
        dels.forEach { id -> emit(ByteArrayOutputStream().also { b -> DataOutputStream(b).use { it.writeByte(DEL.toInt());it.writeUTF(id) } }.toByteArray()) }
        puts.forEach { e -> emit(ByteArrayOutputStream().also { b -> DataOutputStream(b).use { it.writeByte(PUT.toInt());writeRecord(it,e) } }.toByteArray()) }
        data.flush()
        return out.toByteArray() to hash
    }

    fun decodeLog(bytes:ByteArray,allowTornTail:Boolean):LogState {
        require(isLog(bytes) && bytes.size<=MAX_BYTES) { "Invalid LEX4 ledger" }
        require(java.nio.ByteBuffer.wrap(bytes,4,4).int==4) { "Unsupported ledger format version" }
        val state=LinkedHashMap<String,LearningExperience>()
        var hash=seed();var pos=8;var records=0
        while(pos<bytes.size) {
            val remaining=bytes.size-pos
            val len=if(remaining>=4) java.nio.ByteBuffer.wrap(bytes,pos,4).int else -1
            if(len!=-1) require(len in 1..MAX_RECORD) { "Corrupt LEX4 record length" }
            if(len==-1 || remaining<4+len+32) {
                require(allowTornTail) { "Truncated LEX4 ledger" }
                break
            }
            val payload=bytes.copyOfRange(pos+4,pos+4+len)
            val next=link(hash,payload)
            require(MessageDigest.isEqual(next,bytes.copyOfRange(pos+4+len,pos+4+len+32))) { "LEX4 record checksum mismatch" }
            try {
                val input=DataInputStream(ByteArrayInputStream(payload))
                when(input.readByte()) {
                    PUT -> readRecord(input).let { state[it.id]=it }
                    DEL -> require(state.remove(input.readUTF())!=null)
                    else -> throw IllegalArgumentException("Unknown LEX4 op")
                }
                require(input.available()==0)
            } catch(e:IOException) { throw IllegalArgumentException("Invalid LEX4 record",e) }
            require(state.size<=FileLearningExperienceStore.MAX_RECORDS)
            hash=next;pos+=4+len+32;records++
        }
        return LogState(state.values.toList(),hash,records,pos.toLong())
    }

    private fun writeRecord(out:DataOutputStream,e:LearningExperience) {
        out.writeUTF(e.id)
        out.writeUTF(e.type.name);out.writeUTF(e.provenance.name)
        out.writeUTF(e.trust.name);out.writeUTF(e.status.name)
        writeString(out,e.content)
        writeNullable(out,e.annotation);writeNullable(out,e.sourceRef)
        out.writeUTF(e.contentHash);out.writeLong(e.createdAt);out.writeLong(e.updatedAt)
    }
    private fun readRecord(input:DataInputStream)=LearningExperience(
        id=input.readUTF(), type=LearningExperienceType.valueOf(input.readUTF()),
        provenance=LearningProvenance.valueOf(input.readUTF()), trust=LearningTrustClass.valueOf(input.readUTF()),
        status=LearningStatus.valueOf(input.readUTF()), content=readString(input),
        annotation=readNullable(input), sourceRef=readNullable(input), contentHash=input.readUTF(),
        createdAt=input.readLong(), updatedAt=input.readLong())

    /** Decodes LEX2, LEX3 (snapshot) or LEX4 (strict: no torn tail accepted). */
    fun decode(bytes:ByteArray):List<LearningExperience> {
        if(isLog(bytes))return decodeLog(bytes,allowTornTail=false).items
        require(bytes.size in 40..MAX_BYTES)
        val payload=bytes.copyOfRange(0,bytes.size-32)
        val checksum=bytes.copyOfRange(bytes.size-32,bytes.size)
        require(MessageDigest.isEqual(checksum,MessageDigest.getInstance("SHA-256").digest(payload))) {
            "Learning experience checksum mismatch"
        }
        try {
            val input=DataInputStream(ByteArrayInputStream(payload))
            val limit=when(input.readInt()) {
                MAGIC_LEX2 -> FileLearningExperienceStore.LEGACY_LEX2_MAX_RECORDS
                MAGIC -> { require(input.readInt()==FORMAT_VERSION) { "Unsupported ledger format version" }
                    FileLearningExperienceStore.MAX_RECORDS }
                else -> throw IllegalArgumentException("Unknown learning ledger magic")
            }
            val count=input.readInt().also { require(it in 0..limit) }
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

