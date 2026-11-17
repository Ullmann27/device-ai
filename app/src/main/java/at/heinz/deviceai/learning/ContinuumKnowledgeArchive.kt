package at.heinz.deviceai.learning

import at.heinz.deviceai.cognitive.HttpReadOnlyResearchProvider

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption
import java.nio.file.LinkOption
import java.nio.channels.FileChannel
import java.security.MessageDigest
import java.text.Normalizer
import java.util.Locale

/**
 * Local, content-addressed source archive. It stores citation-bearing extracts,
 * never executable instructions or owner facts. A source update is a new revision.
 * No API keys, no third-party cloud accounts and no network calls are used here.
 */
class ContinuumKnowledgeArchive(rootDirectory:File) {
    private val root=File(rootDirectory,"continuum-knowledge-v1")
    private val gate=ARCHIVE_MONITOR
    private val pendingImport=File(root,".import-pending-v1")

    /** Fault boundary for JVM filesystem regression tests, never an external callback. */
    internal var beforeImportPublish:((Int)->Unit)?=null

    init {
        require(!root.isSymbolicLink()) { "Knowledge archive must not be a symlink" }
        if(!root.exists())check(root.mkdirs() || root.isDirectory) { "Cannot create knowledge archive" }
        require(root.isDirectory && !root.isSymbolicLink())
        transaction { Unit } // Complete a previously validated import after process death.
    }

    enum class Kind { WIKIPEDIA_EXCERPT, RIS_LEGISLATION, RIS_CASE_LAW, PUBLIC_WEB_EXCERPT }

    data class SourceRecord(
        val id:String,
        val kind:Kind,
        val title:String,
        val url:String,
        val attribution:String,
        val collectedAt:Long,
        val sourceDate:String?,
        val sha256:String,
        val text:String
    )

    data class Hit(val source:SourceRecord,val score:Int)

    /** Resolve one indexed source to its complete verified stored excerpt.
     * IDs never become paths supplied by web text. No new store or revision.
     */
    fun readSource(id:String):SourceRecord? = transaction {
        require(id.matches(Regex("[a-f0-9]{64}")))
        val file=File(root,"$id.rec")
        if(!file.exists())null else runCatching { readFile(file) }.getOrNull()
    }

    /** Caller must confirm the licence/usage policy before import. */
    fun save(kind:Kind,title:String,url:String,attribution:String,
             text:String,collectedAt:Long=System.currentTimeMillis(),
             sourceDate:String?=null):SourceRecord = transaction {
        require(title.length in 3..160 && title.none { it.isISOControl() })
        require(attribution.length in 3..160 && attribution.none { it.isISOControl() })
        require(text.length in 20..6_000 && text.none { it=='\u0000' })
        require(collectedAt>0L && collectedAt<=System.currentTimeMillis()+86_400_000L)
        require(sourceDate==null || sourceDate.matches(Regex("\\d{4}-\\d{2}-\\d{2}")))
        val uri=java.net.URI(url)
        require(uri.scheme=="https" && uri.userInfo==null && uri.fragment==null &&
            uri.port==-1 && uri.rawQuery?.length.orEmptyLength()<=600)
        if (kind==Kind.PUBLIC_WEB_EXCERPT)
            HttpReadOnlyResearchProvider.ResearchUrlValidator.validateSyntax(uri)
        require(uri.host in when(kind) {
            Kind.PUBLIC_WEB_EXCERPT -> setOf(requireNotNull(uri.host))
            Kind.WIKIPEDIA_EXCERPT -> setOf("de.wikipedia.org")
            Kind.RIS_LEGISLATION,Kind.RIS_CASE_LAW -> setOf(
                "ris.bka.gv.at","www.ris.bka.gv.at","data.bka.gv.at")
        })
        require(url.length in 15..700)
        val clean=text.replace(Regex("\\s+")," ").trim()
        require(clean.length in 20..6_000)
        val contentHash=digest(clean)
        val id=digest(kind.name+"\u0000"+url+"\u0000"+contentHash)
        val record=SourceRecord(id,kind,title,url,attribution,collectedAt,
            sourceDate,contentHash,clean)
        val target=File(root,"$id.rec")
        if(target.exists()) {
            val old=readFile(target)
            require(old.id==id && old.sha256==contentHash) { "Existing revision mismatch" }
            return@transaction old
        }
        require(root.usableSpace>=MIN_FREE_BYTES) {
            "Not enough free storage to archive more public sources"
        }
        val files=files()
        require(files.size<MAX_RECORDS) { "Archive capacity reached; do not erase older citations" }
        val payload=encodePayload(record)
        val temp=File.createTempFile("knowledge-",".tmp",root)
        try {
            writeDurably(temp,payload)
            moveNew(temp,target)
        } finally { temp.delete() }
        record
    }

    private fun encodePayload(record:SourceRecord):ByteArray {
        val out=ByteArrayOutputStream()
        DataOutputStream(out).use { dest ->
            dest.writeInt(MAGIC)
            dest.writeInt(1)
            dest.writeUTF(record.id)
            dest.writeUTF(record.kind.name)
            dest.writeUTF(record.title)
            dest.writeUTF(record.url)
            dest.writeUTF(record.attribution)
            dest.writeLong(record.collectedAt)
            dest.writeBoolean(record.sourceDate!=null)
            if(record.sourceDate!=null)dest.writeUTF(record.sourceDate)
            dest.writeUTF(record.sha256)
            dest.writeUTF(record.text)
        }
        return out.toByteArray().also { require(it.size<=MAX_RECORD_BYTES) }
    }

    /** Local lexical retrieval with exact source and date metadata; no text generation. */
    fun search(query:String,kind:Kind?=null,limit:Int=8):List<Hit> = transaction {
        require(query.length in 2..240 && limit in 1..20)
        val terms=tokens(query).distinct().take(16)
        require(terms.isNotEmpty())
        files().asSequence().mapNotNull { f -> runCatching { readFile(f) }.getOrNull() }
            .filter { kind==null || it.kind==kind }
            .mapNotNull { record ->
                val titleTokens=tokens(record.title).toSet()
                val textTokens=tokens(record.text).toSet()
                val score=terms.sumOf { term ->
                    (if(term in titleTokens)5 else 0)+(if(term in textTokens)1 else 0)
                }
                if(score>0)Hit(record,score) else null
            }
            .sortedWith(compareByDescending<Hit>{it.score}
                .thenByDescending {it.source.collectedAt}
                .thenBy {it.source.id})
            .take(limit).toList()
    }

    /** Bounded, read-only view for the EWM source index: the archive state and at most [limit]
     * valid records with their text cut to [maxTextChars]. Damaged files are skipped exactly as in
     * [search]; nothing is written. Record ids are content addressed, so the sorted file names
     * identify the archive state ([indexState]) without reading any record. */
    fun indexSnapshot(limit:Int=MAX_RECORDS,maxTextChars:Int=INDEX_TEXT_CHARS):IndexSnapshot = transaction {
        require(limit in 1..MAX_RECORDS && maxTextChars in 120..6_000)
        val chosen=files().take(limit)
        IndexSnapshot(stateOf(chosen),chosen.mapNotNull { f -> runCatching { readFile(f) }.getOrNull() }
            .map { if(it.text.length>maxTextChars)it.copy(text=it.text.take(maxTextChars)) else it })
    }

    /** Cheap change check for a cached index: hashes the sorted record file names only. */
    fun indexState():String = transaction { stateOf(files().take(MAX_RECORDS)) }

    /** All currently archived source hashes; complements the bounded attempt journal. */
    fun archivedHashes():Set<String> = transaction {
        files().take(MAX_RECORDS)
            .mapNotNull { f -> runCatching { readFile(f).sha256.lowercase(Locale.ROOT) }.getOrNull() }
            .toSet()
    }

    data class IndexSnapshot(val state:String,val records:List<SourceRecord>)

    private fun stateOf(chosen:List<File>):String=digest(chosen.joinToString("\n") { it.name })

    fun stats():Map<Kind,Int> = transaction {
        val records=files().mapNotNull { runCatching { readFile(it) }.getOrNull() }
        Kind.entries.associateWith { k -> records.count { it.kind==k } }
    }

    fun latest(kind:Kind,limit:Int=8):List<SourceRecord> = transaction {
        require(limit in 1..20)
        files().mapNotNull { runCatching { readFile(it) }.getOrNull() }
            .filter { it.kind==kind }.sortedByDescending {it.collectedAt}.take(limit)
    }

    /**
     * Owner-controlled SAF backup: versioned source record files, no API tokens.
     * Cloud export requires the user to explicitly select a cloud-backed
     * Android DocumentsProvider in the system picker.
     */
    fun exportZip(destination:OutputStream):Int = transaction {
        val existing=files()
        ZipOutputStream(destination).use { zip ->
            for(file in existing) {
                readFile(file) // fail closed before copying any damaged record
                zip.putNextEntry(ZipEntry(file.name))
                zip.write(file.readBytes())
                zip.closeEntry()
            }
            zip.finish()
        }
        existing.size
    }

    /**
     * One-app migration gate: parse and validate ALL ZIP entries before the
     * first permanent archive write. Corrupt later entries must not produce
     * an earlier partial import. New records are durably staged before a
     * commit manifest is published. A failed/interrupted commit is resumed by
     * the next archive operation, never overwriting or deleting older records.
     */
    fun importZip(input:InputStream):Int = transaction {
        val names=mutableSetOf<String>()
        val staged=ArrayList<SourceRecord>()
        var uncompressed=0L
        ZipInputStream(input).use { zip ->
            while(true) {
                val entry=zip.nextEntry ?: break
                require(!entry.isDirectory && entry.name.matches(Regex("[0-9a-f]{64}\\.rec"))) {
                    "Backup entry name not permitted"
                }
                require(names.add(entry.name) && names.size<=MAX_RECORDS) {
                    "Backup contains duplicate or excessive entries"
                }
                val buffer=ByteArray(4096)
                val out=ByteArrayOutputStream()
                var emptyReads=0
                while(true) {
                    val n=zip.read(buffer)
                    if(n<0)break
                    if(n==0) {
                        if(++emptyReads>=16)throw java.io.IOException("Backup stream made no progress")
                        continue
                    }
                    emptyReads=0
                    require(n<=MAX_RECORD_BYTES-out.size()) { "Oversize archived source" }
                    require(uncompressed<=MAX_IMPORT_UNCOMPRESSED_BYTES-n) {
                        "Backup expands beyond safe migration limit"
                    }
                    uncompressed+=n
                    out.write(buffer,0,n)
                }
                val record=decodePayload(out.toByteArray(),entry.name)
                validateImportedRecord(record)
                staged.add(record)
                zip.closeEntry()
            }
        }
        val newEntries=staged.count { !File(root,"${it.id}.rec").exists() }
        require(files().size<=MAX_RECORDS-newEntries) {
            "Archive capacity reached; do not remove existing citations"
        }
        if(newEntries>0)require(root.usableSpace>=MIN_FREE_BYTES+uncompressed) {
            "Not enough free storage for verified import"
        }
        // Verify all colliding on-device records before committing any new
        // records, so one damaged existing item cannot create partial results.
        staged.forEach { record ->
            val existing=File(root,"${record.id}.rec")
            if(existing.exists()) {
                val current=readFile(existing)
                require(current.id==record.id && current.sha256==record.sha256) {
                    "Existing archived source differs from verified backup"
                }
            }
        }
        val additions=staged.filter { !File(root,"${it.id}.rec").exists() }
        if(additions.isNotEmpty()) {
            check(pendingImport.mkdir()) { "Cannot stage verified archive import" }
            additions.forEach { record ->
                writeDurably(File(pendingImport,"${record.id}.rec"),encodePayload(record))
            }
            val manifest=buildString {
                append("1\n")
                additions.forEach { append(it.id).append(".rec\n") }
            }.toByteArray(Charsets.US_ASCII)
            val temporary=File(pendingImport,"manifest.tmp")
            writeDurably(temporary,manifest)
            moveNew(temporary,File(pendingImport,"manifest.v1"))
            recoverPendingImport()
        }
        staged.size
    }

    /** Shared monitor plus OS lock prevent two archive instances/processes from
     * racing a save or recovering the same import. Reads cannot expose a partial
     * committed migration: recovery either finishes first or fails visibly.
     */
    private fun <T> transaction(action:()->T):T=synchronized(gate) {
        require(root.isDirectory && !root.isSymbolicLink())
        val lock=File(root,".archive.lock")
        FileChannel.open(lock.toPath(),StandardOpenOption.CREATE,StandardOpenOption.WRITE,
            LinkOption.NOFOLLOW_LINKS).use { channel ->
            channel.lock().use {
                recoverPendingImport()
                action()
            }
        }
    }

    private fun recoverPendingImport() {
        require(!pendingImport.isSymbolicLink()) { "Unsafe pending archive import" }
        if(!pendingImport.exists())return
        require(pendingImport.isDirectory)
        val children=pendingImport.listFiles() ?: error("Pending import is not readable")
        require(children.all { !it.isSymbolicLink() && it.isFile &&
            (it.name.matches(RECORD_NAME) || it.name in setOf("manifest.tmp","manifest.v1")) }) {
            "Unrecognized pending archive import data"
        }
        val manifest=File(pendingImport,"manifest.v1")
        if(!manifest.exists()) {
            // No commit intent: every staged file is private and was never published.
            discardPendingImport(children)
            return
        }
        require(manifest.length() in 3L..(MAX_RECORDS*69L+2L))
        val lines=manifest.readText(Charsets.US_ASCII).split('\n')
        require(lines.first()=="1" && lines.last().isEmpty())
        val names=lines.drop(1).dropLast(1)
        require(names.size in 1..MAX_RECORDS && names.distinct().size==names.size &&
            names.all { it.matches(RECORD_NAME) }) { "Invalid archive import manifest" }
        require(children.filter { it.name.matches(RECORD_NAME) }.all { it.name in names })
        // Validate the complete recovery set before the first further write.
        names.forEach { name ->
            val target=File(root,name)
            val staged=File(pendingImport,name)
            val record=if(target.exists()) readFile(target) else {
                readPendingRecord(staged,name)
            }
            validateImportedRecord(record)
            if(staged.exists()) {
                val pending=readPendingRecord(staged,name)
                validateImportedRecord(pending)
                require(pending.id==record.id && pending.sha256==record.sha256)
            }
        }
        val absent=names.count { !File(root,it).exists() }
        require(files().size<=MAX_RECORDS-absent) { "Archive capacity reached during recovery" }
        names.forEachIndexed { index,name ->
            val target=File(root,name)
            if(!target.exists()) {
                beforeImportPublish?.invoke(index)
                moveNew(File(pendingImport,name),target)
            }
        }
        discardPendingImport(pendingImport.listFiles() ?: error("Pending import is not readable"))
    }

    private fun readPendingRecord(file:File,name:String):SourceRecord {
        require(file.parentFile?.canonicalPath==pendingImport.canonicalPath &&
            !file.isSymbolicLink() && file.isFile &&
            file.length() in 50L..MAX_RECORD_BYTES.toLong())
        return decodePayload(file.readBytes(),name)
    }

    private fun discardPendingImport(children:Array<File>) {
        // Delete only this transaction's staged files; never an existing source.
        children.filter { it.name!="manifest.v1" }.forEach {
            check(it.delete()) { "Cannot remove completed archive staging file" }
        }
        val manifest=File(pendingImport,"manifest.v1")
        if(manifest.exists())check(manifest.delete()) { "Cannot clear archive import manifest" }
        check(pendingImport.delete()) { "Cannot clear archive staging directory" }
    }

    private fun writeDurably(file:File,bytes:ByteArray) {
        FileOutputStream(file).use { output -> output.write(bytes);output.fd.sync() }
    }

    private fun moveNew(source:File,target:File) {
        require(!target.exists() && !target.isSymbolicLink()) { "Archive target already exists" }
        try {
            Files.move(source.toPath(),target.toPath(),StandardCopyOption.ATOMIC_MOVE)
        } catch (_:java.nio.file.AtomicMoveNotSupportedException) {
            Files.move(source.toPath(),target.toPath())
        }
    }

    private fun validateImportedRecord(record:SourceRecord) {
        require(record.title.length in 3..160 && record.title.none { it.isISOControl() })
        require(record.attribution.length in 3..160 && record.attribution.none { it.isISOControl() })
        require(record.text.length in 20..6_000 && record.text.none { it=='\u0000' })
        require(record.collectedAt>0 && record.collectedAt<=System.currentTimeMillis()+86_400_000L)
        require(record.sourceDate==null ||
            record.sourceDate.matches(Regex("\\d{4}-\\d{2}-\\d{2}")))
        require(record.url.length in 15..700)
        val uri=java.net.URI(record.url)
        require(uri.scheme=="https" && uri.userInfo==null &&
            uri.fragment==null && uri.port==-1 &&
            uri.rawQuery?.length.orEmptyLength()<=600)
        if (record.kind==Kind.PUBLIC_WEB_EXCERPT)
            HttpReadOnlyResearchProvider.ResearchUrlValidator.validateSyntax(uri)
        require(uri.host in when(record.kind) {
            Kind.PUBLIC_WEB_EXCERPT -> setOf(requireNotNull(uri.host))
            Kind.WIKIPEDIA_EXCERPT -> setOf("de.wikipedia.org")
            Kind.RIS_LEGISLATION,Kind.RIS_CASE_LAW ->
                setOf("ris.bka.gv.at","www.ris.bka.gv.at","data.bka.gv.at")
        })
        val canonical=record.text.replace(Regex("\\s+")," ").trim()
        require(canonical==record.text && canonical.length in 20..6_000)
        require(digest(canonical)==record.sha256)
        require(digest(record.kind.name+"\u0000"+record.url+"\u0000"+record.sha256)==record.id)
    }

    private fun readFile(file:File):SourceRecord {
        require(file.parentFile?.canonicalPath==root.canonicalPath &&
            !file.isSymbolicLink() && file.length() in 50L..MAX_RECORD_BYTES.toLong())
        return decodePayload(file.readBytes(),file.name)
    }

    private fun decodePayload(bytes:ByteArray,expectedName:String):SourceRecord {
        require(bytes.size in 50..MAX_RECORD_BYTES)
        return DataInputStream(ByteArrayInputStream(bytes)).use { src ->
            require(src.readInt()==MAGIC && src.readInt()==1)
            val id=src.readUTF()
            val kind=Kind.valueOf(src.readUTF())
            val title=src.readUTF()
            val url=src.readUTF()
            val attribution=src.readUTF()
            val collectedAt=src.readLong()
            val date=if(src.readBoolean())src.readUTF() else null
            val sha=src.readUTF()
            val text=src.readUTF()
            require(src.available()==0 && id.matches(Regex("[0-9a-f]{64}")) &&
                expectedName=="$id.rec" && digest(text)==sha &&
                digest(kind.name+"\u0000"+url+"\u0000"+sha)==id)
            SourceRecord(id,kind,title,url,attribution,collectedAt,date,sha,text)
        }
    }

    private fun files():List<File> = root.listFiles { f ->
        f.name.matches(Regex("[0-9a-f]{64}\\.rec")) && f.isFile && !f.isSymbolicLink()
    }?.sortedBy {it.name} ?: emptyList()

    private fun File.isSymbolicLink():Boolean=Files.isSymbolicLink(toPath())

    private fun tokens(input:String):List<String> =
        Normalizer.normalize(input.lowercase(Locale.ROOT),Normalizer.Form.NFKC)
            .split(Regex("[^\\p{L}\\p{N}]+"))
            .filter {it.length>=3}.take(1_300)

    private fun digest(value:String):String =
        MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(Locale.ROOT,it.toInt() and 255) }

    private fun Int?.orEmptyLength():Int=this?:0

    companion object {
        const val MAX_RECORDS=4096
        /** Text kept per record in the EWM source index (the answer shows 350 characters). */
        const val INDEX_TEXT_CHARS=800
        const val MIN_FREE_BYTES=128L * 1024L * 1024L
        const val MAX_RECORD_BYTES=40_000
        private const val MAX_IMPORT_UNCOMPRESSED_BYTES=64L * 1024L * 1024L
        private const val MAGIC=0x434B4131
        private val ARCHIVE_MONITOR=Any()
        private val RECORD_NAME=Regex("[0-9a-f]{64}\\.rec")
    }
}
