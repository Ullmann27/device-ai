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

class FileLearningExperienceStore(private val directory:File, private val automaticRecovery:Boolean) {
    constructor(directory:File):this(directory,false)
    private val primary=File(directory,"experiences-v2.bin")
    private val backup=File(directory,"experiences-v2.lkg")
    private val lex3=Lex3LearningExperienceStore(directory)
    private val recovery=OwnerMemoryRecovery(directory)
    private data class ReplaySelection(
        val fingerprint:String,
        val type:LearningExperienceType,
        val records:List<LearningExperience>
    )
    // Derived, instance-local and accessed only under FILE_LOCK. Retain at most 128 records,
    // not a decoded ledger. Every reuse still validates the primary and every LEX3 segment.
    private var replaySelection:ReplaySelection?=null

    fun all():List<LearningExperience> = transaction() { recovery.project(loadValidated()) }

    /** One locked read of the ledger: its SHA-256 content fingerprint, plus the decoded records only when the
     * fingerprint differs from [knownFingerprint]. Content-keyed (not mtime/length), so a rewrite that keeps
     * mtime and size is still detected. Same integrity validation as [all]; LEX3 also checks every referenced segment. */
    internal fun snapshotIfChanged(knownFingerprint:String?):Pair<String, List<LearningExperience>?> =
        transaction() { snapshotUnderLease(knownFingerprint) }

    /** Caller holds the existing transaction; never acquire a nested OS file lock. */
    private fun snapshotUnderLease(knownFingerprint:String?):Pair<String,List<LearningExperience>?> {
        val (ledgerFingerprint,records)=rawSnapshotUnderLease(knownFingerprint?.substringBefore(':'))
        val pending=recovery.pending()
        val fingerprint=recovery.fingerprint(ledgerFingerprint,pending)
        if(fingerprint==knownFingerprint)return fingerprint to null
        val raw=records ?: rawSnapshotUnderLease(null).second!!
        return fingerprint to recovery.project(raw,pending)
    }

    /** Strict validation is retained by default. Recovery is chosen only by the explicit core factory. */
    private fun rawSnapshotUnderLease(knownFingerprint:String?):Pair<String,List<LearningExperience>?> {
        if(!primary.exists()) {
            if(backup.exists() || lex3.exists() || recovery.hasHistory()) {
                check(automaticRecovery) { "Learning ledger primary missing; recovery requires explicit verification" }
                recoverUnderLease(null)
                return strictSnapshot(readPrimary(),null)
            }
            return ABSENT_FINGERPRINT to (if(knownFingerprint==ABSENT_FINGERPRINT) null else emptyList())
        }
        val bytes=if(primary.length()==0L)ByteArray(0) else readPrimary()
        return try { strictSnapshot(bytes,knownFingerprint) } catch(failure:Exception) {
            if(!automaticRecovery || failure !is IOException && failure !is IllegalArgumentException)throw failure
            recoverUnderLease(bytes)
            strictSnapshot(readPrimary(),null)
        }
    }

    private fun strictSnapshot(bytes:ByteArray,knownFingerprint:String?):Pair<String,List<LearningExperience>?> {
        if(Lex3LearningExperienceStore.isManifest(bytes)) return lex3.snapshot(bytes,knownFingerprint)
        val fingerprint=java.security.MessageDigest.getInstance("SHA-256").digest(bytes)
            .joinToString("") { "%02x".format(it) }
        return fingerprint to (if(fingerprint==knownFingerprint) null else LearningExperienceCodec.decode(bytes))
    }

    fun append(experience:LearningExperience):Boolean = transaction(write=true) {
        val current=loadValidated()
        val existing=current.firstOrNull { it.id==experience.id }
        if(existing!=null) {
            require(existing.contentHash==experience.contentHash) { "Conflicting learning experience identity" }
            return@transaction false
        }
        val next=bounded(current+experience)
        if(next.none { it.id==experience.id })return@transaction false
        save(next)
        true
    }

    /** One read and at most one write for a batch; ids already present (any status) are skipped. */
    fun appendMissing(items:List<LearningExperience>):Int = appendMissing(items,historicalOwnerImport=false)

    /** The Android preference mirror carries history, never a new owner confirmation after recovery. */
    internal fun appendHistoricalOwnerFacts(items:List<LearningExperience>):Int =
        appendMissing(items,historicalOwnerImport=true)

    private fun appendMissing(items:List<LearningExperience>,historicalOwnerImport:Boolean):Int = transaction(write=true) {
        require(items.size<=MAX_DURABLE_RECORDS) { "Learning ledger batch exceeds bounded limit" }
        val current=loadValidated()
        // Validation, possible recovery, this decision and the append share the SAME OS lease.
        // A complete empty marker still records history: a lost revocation must not be recreated
        // as active merely because its ID no longer exists in the partially recovered ledger.
        if(historicalOwnerImport && recovery.hasHistory())return@transaction 0
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
        if(added.isEmpty())return@transaction 0
        val next=bounded(current+added.values)
        val admitted=next.count { it.id in added }
        if(admitted>0)save(next)
        admitted
    }

    /** One durable owner correction: retain superseded labels for audit, but
     * never replay contradictory versions of the same encoder input together.
     * Reconfirmation also refreshes an old content-addressed lesson's recency.
     */
    internal fun confirmIntentLabel(experience:LearningExperience) = transaction(write=true) {
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
        transaction(write=true) {
            require(now>=0)
            val current=loadValidated()
            val index=current.indexOfFirst { it.id==id }
            if(index<0)return@transaction false
            val old=current[index]
            if(old.status==status)return@transaction true
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
    ):Int = transaction(write=true) {
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
        transaction() {
            require(limit in 1..128)
            val cached=replaySelection?.takeIf { it.type==type }
            // Drop the prior selection before reading: any integrity failure leaves no reusable entry.
            replaySelection=null
            val (fingerprint,changed)=snapshotUnderLease(cached?.fingerprint)
            val records=if(changed==null) checkNotNull(cached).records else
                changed.sortedWith(compareBy<LearningExperience> { it.updatedAt }
                .thenBy { it.createdAt }.thenBy { it.id }).asReversed().asSequence()
                .filter { it.type==type && it.status in setOf(
                    LearningStatus.OBSERVED,LearningStatus.CONFIRMED,
                    LearningStatus.LEARNED,LearningStatus.REPLAY_PENDING
                ) }
                .take(128).toList().asReversed()
            replaySelection=ReplaySelection(fingerprint,type,records)
            // Never expose the cached list (including when the requested limit is its full size).
            records.takeLast(limit)
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
        val records=rawSnapshotUnderLease(null).second!!
        recovery.pending() // A missing/malformed consent marker also blocks mutations and ordinary readers.
        return records
    }

    /** Reuse the canonical formats, OS lease and bounded readers; never parse damaged records as consent. */
    private fun recoverUnderLease(damaged:ByteArray?) {
        if(damaged!=null && damaged.size>=8) {
            val header=java.nio.ByteBuffer.wrap(damaged)
            val magic=header.int;val version=header.int
            require(magic!=0x4c455834 && (magic!=0x4c455833 || version==1)) {
                "Unsupported newer learning ledger; explicit format migration required"
            }
        }
        val segmented=lex3.exists()
        if(segmented)Lex3LearningExperienceStore.requireDirectory(File(directory,"experiences-v3"))
        val survivors=if(damaged!=null && Lex3LearningExperienceStore.isManifest(damaged))
            try { lex3.verifiedSurvivors(damaged) } catch(_:IllegalArgumentException) { emptyList() }
            else emptyList()
        val records=survivors.takeIf { it.isNotEmpty() } ?: run {
            val candidates=listOf(backup,File(directory,"experiences-v3/legacy-v2.bin"))
            candidates.firstNotNullOfOrNull { file ->
                if(!Files.exists(file.toPath(),java.nio.file.LinkOption.NOFOLLOW_LINKS))return@firstNotNullOfOrNull null
                Lex3LearningExperienceStore.requireRegular(file)
                try { LearningExperienceCodec.decode(Lex3LearningExperienceStore.readBounded(file,LearningExperienceCodec.MAX_BYTES))
                    .takeIf { it.isNotEmpty() } }
                catch(_:IOException) { null } catch(_:IllegalArgumentException) { null }
            } ?: throw IOException("No verified recovery source; damaged ledger retained")
        }
        // Durable quarantine and owner-confirmation barrier MUST precede primary publication.
        recovery.prepare(records,damaged)
        if(segmented)lex3.restoreVerified(records) { OwnerMemoryRecovery.beforeWriteForTest?.invoke(primary) }
        else {
            OwnerMemoryRecovery.beforeWriteForTest?.invoke(primary)
            Lex3LearningExperienceStore.writeAtomic(primary,LearningExperienceCodec.encode(records))
            Lex3LearningExperienceStore.syncDirectory(directory)
        }
        replaySelection=null
        emitStored(recovery.project(records))
    }

    fun pendingOwnerReconfirmation():Set<String> = transaction() {
        loadValidated();recovery.pending().facts.keys.toSet()
    }

    fun ownerRecoveryReview():OwnerRecoveryReview = transaction() {
        val records=loadValidated()
        recovery.review(records,rawSnapshotUnderLease(null).first)
    }

    fun reconfirmRecoveredOwnerFacts(review:OwnerRecoveryReview,proof:OwnerReconfirmationProof?):Set<String> = transaction(write=true) {
        val records=loadValidated()
        val ids=recovery.reconfirm(records,rawSnapshotUnderLease(null).first,review,proof)
        emitStored(records.filter { it.id in ids })
        ids
    }

    /** Observations and audits cannot evict owner instructions or revocations.
     * Refuse protected overflow before writing rather than forget consent.
     */
    private fun bounded(items:List<LearningExperience>):List<LearningExperience> {
        fun protected(item:LearningExperience)=
            item.trust==LearningTrustClass.OWNER_CONFIRMED || item.status==LearningStatus.REVOKED
        val order=compareBy<LearningExperience> { it.createdAt }.thenBy { it.id }
        val preserved=items.filter(::protected)
        val capacity=if(preserved.size>MAX_RECORDS ||
            (primary.exists() && Lex3LearningExperienceStore.isManifest(readPrimary()))) MAX_DURABLE_RECORDS else MAX_RECORDS
        check(preserved.size<=capacity) { "Learning ledger capacity reached; owner records cannot be evicted" }
        return (preserved+items.filterNot(::protected).sortedWith(order)
            .takeLast(capacity-preserved.size)).sortedWith(order)
    }

    private fun intentKey(text:String)=text.lowercase(Locale.ROOT)
        .replace(Regex("[^\\p{L}\\p{N}]+")," ").trim()

    private fun save(items:List<LearningExperience>) {
        val hub=ContinuumMemoryEventHub.forApp(directory.parentFile ?: directory)
        val previous=if(hub.hasListeners())recovery.project(loadValidated()).associateBy { it.id } else emptyMap()
        saveInner(items)
        recovery.retireWithdrawn(items)
        if(hub.hasListeners()) {
            Lex3LearningExperienceStore.syncDirectory(directory)
            emitStored(recovery.project(items).filter { previous[it.id]!=it })
        }
    }

    private fun emitStored(items:List<LearningExperience>) {
        val hub=ContinuumMemoryEventHub.forApp(directory.parentFile ?: directory)
        if(hub.hasListeners())items.forEach {
            hub.emit(MemoryStored(it.id,ContinuumMemoryEventHub.statusOf(it),System.currentTimeMillis()))
        }
    }

    private fun saveInner(items:List<LearningExperience>) {
        require(items.size<=MAX_DURABLE_RECORDS && items.map { it.id }.distinct().size==items.size)
        val previous=if(primary.exists())readPrimary() else null
        if(items.size>MAX_RECORDS || (previous!=null && Lex3LearningExperienceStore.isManifest(previous))) {
            lex3.save(items,previous)
            return
        }
        // Oversized legacy payloads also need segments; do not overwrite a valid small ledger on failure.
        val encoded=try { LearningExperienceCodec.encode(items) } catch(_:IllegalArgumentException) {
            lex3.save(items,previous)
            return
        }
        if(!directory.exists() && !directory.mkdirs())throw IOException("Cannot create learning-core directory")
        if(primary.exists()) {
            runCatching { LearningExperienceCodec.decode(primary.readBytes()) }
                .onSuccess { writeAtomically(backup,primary.readBytes()) }
        }
        writeAtomically(primary,encoded)
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

    private fun readPrimary()=Lex3LearningExperienceStore.readBounded(primary,LearningExperienceCodec.MAX_BYTES)

    /** One OS lease for the complete read/modify/commit, including separate JVM instances.
     * Read-only probes of a nonexistent store do not create it. Parent directories remain app-private.
     */
    private fun <T> transaction(write:Boolean=false,block:()->T):T = synchronized(FILE_LOCK) {
        if(write)replaySelection=null
        if(!directory.exists()) {
            if(!write)return@synchronized block()
            check(directory.mkdirs() || directory.isDirectory) { "Cannot create learning-core directory" }
        }
        Lex3LearningExperienceStore.requireDirectory(directory)
        for(file in listOf(primary,backup)) {
            if(Files.exists(file.toPath(),java.nio.file.LinkOption.NOFOLLOW_LINKS))
                Lex3LearningExperienceStore.requireRegular(file)
        }
        val lock=File(directory,"experiences.lock")
        if(Files.exists(lock.toPath(),java.nio.file.LinkOption.NOFOLLOW_LINKS))
            Lex3LearningExperienceStore.requireRegular(lock)
        java.nio.channels.FileChannel.open(lock.toPath(),java.nio.file.StandardOpenOption.CREATE,
            java.nio.file.StandardOpenOption.WRITE,java.nio.file.LinkOption.NOFOLLOW_LINKS).use { channel ->
            channel.lock().use {
                val result=block()
                // A no-op retry may follow a failed post-rename fsync. Content equality alone
                // must not acknowledge a mutation until the committed directories are durable.
                if(write && primary.exists() && Lex3LearningExperienceStore.isManifest(readPrimary()))
                    lex3.forceCommittedDirectories()
                result
            }
        }
    }

    companion object {
        /** Legacy LEX2 and its ordinary observation ring retain their historical bound. */
        const val MAX_RECORDS=256
        /** First segmented milestone; consumers still materialize all records, so this is intentionally bounded. */
        const val MAX_DURABLE_RECORDS=Lex3LearningExperienceStore.MAX_RECORDS

        /** Validate a separately backed-up original LEX2 file without opening or
         * writing a ledger. This is integrity checking, never consent recovery.
         * Keep the same bounded codec as the active store; no parallel parser.
         */
        internal fun inspectSnapshot(bytes:ByteArray):List<LearningExperience> =
            LearningExperienceCodec.decode(bytes)

        private val FILE_LOCK=Any()
        internal const val ABSENT_FINGERPRINT="absent"
    }
}

internal object LearningExperienceCodec {
    private const val MAGIC=0x4c455832
    const val MAX_BYTES=512*1024

    fun encode(items:List<LearningExperience>):ByteArray {
        require(items.size<=FileLearningExperienceStore.MAX_RECORDS)
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

