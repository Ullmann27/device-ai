package at.heinz.deviceai.learning

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption
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
    private val gate=Any()

    init {
        require(!root.isSymbolicLink()) { "Knowledge archive must not be a symlink" }
        if(!root.exists())check(root.mkdirs()) { "Cannot create knowledge archive" }
        require(root.isDirectory && !root.isSymbolicLink())
    }

    enum class Kind { WIKIPEDIA_EXCERPT, RIS_LEGISLATION, RIS_CASE_LAW }

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

    /** Caller must confirm the licence/usage policy before import. */
    fun save(kind:Kind,title:String,url:String,attribution:String,
             text:String,collectedAt:Long=System.currentTimeMillis(),
             sourceDate:String?=null):SourceRecord = synchronized(gate) {
        require(title.length in 3..160 && title.none { it.isISOControl() })
        require(attribution.length in 3..160 && attribution.none { it.isISOControl() })
        require(text.length in 20..6_000 && text.none { it=='\u0000' })
        require(collectedAt>0L && collectedAt<=System.currentTimeMillis()+86_400_000L)
        require(sourceDate==null || sourceDate.matches(Regex("\\d{4}-\\d{2}-\\d{2}")))
        val uri=java.net.URI(url)
        require(uri.scheme=="https" && uri.userInfo==null && uri.fragment==null &&
            uri.port==-1 && uri.rawQuery?.length.orEmptyLength()<=600)
        require(uri.host in when(kind) {
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
            return@synchronized old
        }
        val files=files()
        require(files.size<MAX_RECORDS) { "Archive capacity reached; do not erase older citations" }
        val out=ByteArrayOutputStream()
        DataOutputStream(out).use { dest ->
            dest.writeInt(MAGIC)
            dest.writeInt(1)
            dest.writeUTF(id)
            dest.writeUTF(kind.name)
            dest.writeUTF(title)
            dest.writeUTF(url)
            dest.writeUTF(attribution)
            dest.writeLong(collectedAt)
            dest.writeBoolean(sourceDate!=null)
            if(sourceDate!=null)dest.writeUTF(sourceDate)
            dest.writeUTF(contentHash)
            dest.writeUTF(clean)
        }
        val payload=out.toByteArray()
        require(payload.size<=MAX_RECORD_BYTES)
        val temp=File.createTempFile("knowledge-",".tmp",root)
        try {
            temp.writeBytes(payload)
            try {
                Files.move(temp.toPath(),target.toPath(),
                    StandardCopyOption.ATOMIC_MOVE)
            } catch (_:java.nio.file.AtomicMoveNotSupportedException) {
                Files.move(temp.toPath(),target.toPath())
            }
        } finally { temp.delete() }
        record
    }

    /** Local lexical retrieval with exact source and date metadata; no text generation. */
    fun search(query:String,kind:Kind?=null,limit:Int=8):List<Hit> = synchronized(gate) {
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

    fun stats():Map<Kind,Int> = synchronized(gate) {
        val records=files().mapNotNull { runCatching { readFile(it) }.getOrNull() }
        Kind.entries.associateWith { k -> records.count { it.kind==k } }
    }

    fun latest(kind:Kind,limit:Int=8):List<SourceRecord> = synchronized(gate) {
        require(limit in 1..20)
        files().mapNotNull { runCatching { readFile(it) }.getOrNull() }
            .filter { it.kind==kind }.sortedByDescending {it.collectedAt}.take(limit)
    }

    /**
     * Owner-controlled SAF backup: versioned source record files, no API tokens.
     * Cloud export requires the user to explicitly select a cloud-backed
     * Android DocumentsProvider in the system picker.
     */
    fun exportZip(destination:OutputStream):Int = synchronized(gate) {
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
     * Import accepts only validated content-addressed records, never filenames
     * from a ZIP path as destination paths. A partial import is reported as an
     * exception; successfully imported prior records remain intact.
     */
    fun importZip(input:InputStream):Int = synchronized(gate) {
        val names=mutableSetOf<String>()
        var count=0
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
                while(true) {
                    val n=zip.read(buffer)
                    if(n<0)break
                    if(n==0)continue
                    require(out.size()+n<=MAX_RECORD_BYTES) { "Oversize archived source" }
                    out.write(buffer,0,n)
                }
                val record=decodePayload(out.toByteArray(),entry.name)
                save(record.kind,record.title,record.url,record.attribution,
                    record.text,record.collectedAt,record.sourceDate)
                count++
                zip.closeEntry()
            }
        }
        count
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
        const val MAX_RECORDS=640
        const val MAX_RECORD_BYTES=40_000
        private const val MAGIC=0x434B4131
    }
}
