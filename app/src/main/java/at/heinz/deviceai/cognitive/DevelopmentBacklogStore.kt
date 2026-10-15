package at.heinz.deviceai.cognitive

import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.nio.charset.CodingErrorAction
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption
import java.security.MessageDigest
import java.time.Instant
import java.util.Base64
import java.util.UUID
import kotlin.math.roundToInt

/** SHA-256 detects damage; it does not authenticate a sender or authorize execution. */
object DevelopmentBacklogWire {
    const val MAX_BYTES = 2_000_000
    fun hash(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes)
        .joinToString("") { "%02x".format(it.toInt() and 255) }
    private fun quote(s: String): String = buildString {
        append('"')
        var i=0
        while (i<s.length) {
            val c=s[i++]
            when (c) {
                '"' -> append("\\\""); '\\' -> append("\\\\"); '\b' -> append("\\b")
                '\u000c' -> append("\\f"); '\n' -> append("\\n"); '\r' -> append("\\r"); '\t' -> append("\\t")
                else -> when {
                    c.code<32 -> append("\\u%04x".format(c.code))
                    c.isHighSurrogate() -> { require(i<s.length && s[i].isLowSurrogate()) { "Invalid Unicode" };append(c);append(s[i++]) }
                    c.isLowSurrogate() -> error("Invalid Unicode")
                    else -> append(c)
                }
            }
        }
        append('"')
    }
    fun canonical(value: Any?): String = when(value) {
        null, JSONObject.NULL -> "null"
        is String -> quote(value)
        is Boolean -> value.toString()
        is Int -> value.toString()
        is Long -> { require(value in -9007199254740991L..9007199254740991L);value.toString() }
        is JSONArray -> (0 until value.length()).joinToString(",","[","]") { canonical(value.get(it)) }
        is JSONObject -> value.keys().asSequence().toList().sorted().joinToString(",","{","}") {
            require(Regex("[A-Za-z][A-Za-z0-9]*").matches(it)); quote(it)+":"+canonical(value.get(it))
        }
        else -> error("Non-canonical value")
    }
    fun digest(value: Any?): String = hash(canonical(value).toByteArray(Charsets.UTF_8))
    fun encode(body: JSONObject): String {
        val bytes=canonical(body).toByteArray(Charsets.UTF_8)
        require(bytes.size<=MAX_BYTES)
        return canonical(JSONObject().put("schemaVersion",2).put("format","continuum-backlog-bridge")
            .put("payloadBase64",Base64.getEncoder().encodeToString(bytes)).put("sha256",hash(bytes)))+"\n"
    }
    fun decode(text: String): JSONObject {
        require(text.toByteArray(Charsets.UTF_8).size<=3_000_000) { "Backlog import exceeds size limit" }
        val e=JSONObject(text)
        require(e.getInt("schemaVersion")==2 && e.getString("format")=="continuum-backlog-bridge")
        val encoded=e.getString("payloadBase64")
        val bytes=Base64.getDecoder().decode(encoded)
        require(bytes.size<=MAX_BYTES && Base64.getEncoder().encodeToString(bytes)==encoded && hash(bytes)==e.getString("sha256")) { "Bridge checksum mismatch" }
        val decoded=Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString()
        val b=JSONObject(decoded)
        require(canonical(b)==decoded) { "Non-canonical bridge" }
        return b
    }
}

data class DevelopmentBacklogItem(
    val id:String, val title:String, val file:String, val kind:String, val symbol:String,
    val source:String, val status:String, val benefit:Int, val risk:Int, val effort:Int,
    val priority:Int, val owner:String?, val notes:List<String>, val evidence:List<String>,
    val createdAt:String, val updatedAt:String, val revision:Int, val baseCommit:String,
    val receiptSummary:String?
)

/** All mutations reload under a process + OS file lock. No cached stale writers.
 * Atomic replacement, retained LKG and explicit recovery; no automatic reset.
 * Only owner-selected SAF data is merged. No executable code or consent in the wire format.
 */
class DevelopmentBacklogStore(private val file:File) {
    private val statuses=setOf("OPEN","CLAIMED","BLOCKED","DONE","REJECTED")
    private val wire=DevelopmentBacklogWire
    val items:List<DevelopmentBacklogItem> get()=locked { tasks(load()).map(::item)
        .sortedWith(compareByDescending<DevelopmentBacklogItem> { it.priority }.thenBy { it.id }) }
    val generation:Long get()=locked { load().getLong("generation") }
    private fun empty()=JSONObject().put("schemaVersion",2).put("generation",0).put("tasks",JSONArray())
    private fun tasks(b:JSONObject):List<JSONObject> = b.getJSONArray("tasks").let { a -> (0 until a.length()).map { a.getJSONObject(it) } }
    private fun string(t:JSONObject,k:String,n:Int):String {
        val value=t.get(k);require(value is String && value.length<=n) { "Invalid $k" };return value
    }
    private fun integer(t:JSONObject,k:String):Long {
        val v=t.get(k);require(v is Int || v is Long) { "Invalid integer $k" };return (v as Number).toLong()
    }
    private fun strings(t:JSONObject,k:String):List<String> = t.getJSONArray(k).let { a ->
        require(a.length()<=12);(0 until a.length()).map { val s=a.get(it);require(s is String && s.length<=240);s }
    }
    private fun goal(t:JSONObject)=JSONObject().apply { for(k in listOf("kind","file","symbol","baseCommit","criteria"))put(k,t.get(k)) }
    private fun validate(b:JSONObject):JSONObject {
        require(integer(b,"schemaVersion")==2L && integer(b,"generation") in 0..9007199254740990L)
        val rows=tasks(b);require(rows.size<=500 && rows.map { it.getString("id") }.distinct().size==rows.size)
        for(t in rows) {
            require(Regex("task-[a-f0-9]{20}").matches(t.getString("id")))
            val status=t.getString("status");require(status in statuses)
            require(t.getString("source") in setOf("PLANNER","MANUAL","AGENT"))
            require(integer(t,"revision") in 1..Int.MAX_VALUE.toLong())
            require(t.isNull("parentHash") || Regex("[a-f0-9]{64}").matches(t.getString("parentHash")))
            for(k in listOf("benefit","risk","effort","priority"))require(integer(t,k) in 0..100)
            require(string(t,"title",160).isNotBlank())
            for(k in listOf("file","kind","symbol","createdAt","updatedAt","baseCommit"))string(t,k,240)
            val base=t.getString("baseCommit");require(base.isEmpty() || Regex("[a-f0-9]{40}").matches(base))
            if(!t.isNull("owner"))string(t,"owner",80)
            if(status=="CLAIMED")require(!t.isNull("owner") && t.getString("owner").isNotBlank())
            strings(t,"notes");strings(t,"evidence")
            val c=t.getJSONObject("criteria");val type=c.getString("type")
            require(type in setOf("MANUAL_REVIEW","REPAIR_DIAGNOSTIC"))
            string(c,"fingerprint",64);string(c,"chain",20)
            if(type=="REPAIR_DIAGNOSTIC")require(base.isNotEmpty() && t.getString("file").isNotEmpty() &&
                Regex("[a-f0-9]{16}").matches(c.getString("fingerprint")) && c.getString("chain") in setOf("node","android","jvm"))
            if(!t.isNull("receipt")) {
                val r=t.getJSONObject("receipt")
                require(r.getString("taskId")==t.getString("id") && r.getString("goalHash")==wire.digest(goal(t)) &&
                    r.getString("baseCommit")==base && r.getString("outcome")=="VERIFIED" &&
                    integer(r,"taskRevision") in 1 until integer(t,"revision") && integer(r,"tests")>0 &&
                    Regex("[a-f0-9]{64}").matches(r.getString("patchSha256")) &&
                    r.getString("diagnostic")==c.getString("fingerprint")) { "Invalid goal receipt" }
                string(r,"strategy",100)
            }
            require(status!="DONE" || !t.isNull("receipt")) { "DONE requires verification receipt" }
        }
        require(wire.canonical(b).toByteArray(Charsets.UTF_8).size<=wire.MAX_BYTES)
        return b
    }
    private fun item(t:JSONObject)=DevelopmentBacklogItem(t.getString("id"),t.getString("title"),t.getString("file"),
        t.getString("kind"),t.getString("symbol"),t.getString("source"),t.getString("status"),t.getInt("benefit"),
        t.getInt("risk"),t.getInt("effort"),t.getInt("priority"),if(t.isNull("owner"))null else t.getString("owner"),
        strings(t,"notes"),strings(t,"evidence"),t.getString("createdAt"),t.getString("updatedAt"),t.getInt("revision"),
        t.getString("baseCommit"),t.optJSONObject("receipt")?.let { "Externer Prüfbeleg: ${it.getInt("tests")} Tests · ${it.getString("strategy")} · Patch ${it.getString("patchSha256")}" })
    private fun read(f:File):JSONObject { require(f.length()<=3_000_000);return validate(wire.decode(f.readText(Charsets.UTF_8))) }
    private fun load():JSONObject {
        if(file.exists())return read(file)
        require(!File(file.path+".bak").exists()) { "Missing primary; explicit recovery required" }
        return empty()
    }
    private fun <T> locked(block:()->T):T = synchronized(LOCK) {
        file.parentFile?.mkdirs()
        FileChannel.open(File(file.path+".lck").toPath(),StandardOpenOption.CREATE,StandardOpenOption.WRITE).use { ch ->
            val lock=ch.tryLock() ?: error("Backlog locked")
            try { block() } finally { lock.release() }
        }
    }
    private fun atomic(target:File,data:ByteArray) {
        val temp=File.createTempFile("backlog-",".tmp",file.absoluteFile.parentFile)
        try {
            FileOutputStream(temp).use { it.write(data);it.fd.sync() }
            Files.move(temp.toPath(),target.toPath(),StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING)
            FileChannel.open(target.absoluteFile.parentFile.toPath(),StandardOpenOption.READ).use { it.force(true) }
        } finally { temp.delete() }
    }
    private fun save(b:JSONObject) {
        validate(b)
        if(file.exists())atomic(File(file.path+".bak"),file.readBytes())
        atomic(file,wire.encode(b).toByteArray(Charsets.UTF_8))
    }
    private fun mutate(fn:(JSONObject)->JSONObject):JSONObject = locked {
        val current=load();val next=fn(JSONObject(wire.canonical(current)))
        next.put("generation",current.getLong("generation")+1);save(next);next
    }
    private fun find(b:JSONObject,id:String)=tasks(b).firstOrNull { it.getString("id")==id } ?: error("Unknown task")
    private fun edit(t:JSONObject,fn:(JSONObject)->Unit) {
        val parent=wire.digest(t);val rev=t.getInt("revision")+1
        fn(t);t.put("parentHash",parent).put("revision",rev).put("updatedAt",Instant.now().toString())
    }
    fun exportBridge():String=locked { wire.encode(load()) }
    fun importBridge(text:String,resolution:String?=null,expectedGeneration:Long?=null) {
        require(resolution==null || resolution in setOf("local","incoming"))
        val incoming=validate(wire.decode(text))
        mutate { b ->
            require(expectedGeneration==null || b.getLong("generation")==expectedGeneration) { "Generation conflict" }
            val rows=tasks(b).associateBy { it.getString("id") }.toMutableMap()
            for(t in tasks(incoming)) {
                val id=t.getString("id");val old=rows[id]
                when {
                    old==null -> rows[id]=t
                    wire.digest(old)==wire.digest(t) -> Unit
                    t.getLong("revision")==old.getLong("revision")+1 && t.optString("parentHash")==wire.digest(old) -> rows[id]=t
                    resolution=="local" -> Unit
                    resolution=="incoming" -> {
                        t.put("parentHash",wire.digest(old)).put("revision",maxOf(t.getInt("revision"),old.getInt("revision"))+1)
                            .put("updatedAt",Instant.now().toString());rows[id]=t
                    }
                    else -> throw IllegalArgumentException("Backlog import conflict: $id; explicitly choose local or incoming")
                }
            }
            b.put("tasks",JSONArray(rows.values.toList()))
        }
    }
    fun create(title:String,benefit:Int=70,risk:Int=20,effort:Int=30):DevelopmentBacklogItem {
        require(title.isNotBlank() && title.length<=160 && listOf(benefit,risk,effort).all { it in 0..100 })
        val now=Instant.now().toString();val id="task-"+UUID.randomUUID().toString().replace("-","").take(20)
        val t=JSONObject().put("id",id).put("revision",1).put("parentHash",JSONObject.NULL).put("title",title)
            .put("file","").put("kind","MANUAL").put("symbol","").put("source","MANUAL").put("status","OPEN")
            .put("benefit",benefit).put("risk",risk).put("effort",effort).put("priority",priority(benefit,risk,effort))
            .put("owner",JSONObject.NULL).put("notes",JSONArray()).put("evidence",JSONArray())
            .put("createdAt",now).put("updatedAt",now).put("baseCommit","").put("receipt",JSONObject.NULL)
            .put("criteria",JSONObject().put("type","MANUAL_REVIEW").put("fingerprint","").put("chain",""))
        mutate { it.getJSONArray("tasks").put(t);it };return item(t)
    }
    private fun priority(b:Int,r:Int,e:Int)=(b*.65-r*.20-e*.15).roundToInt().coerceIn(0,100)
    fun prioritize(id:String,benefit:Int,risk:Int,effort:Int) {
        require(listOf(benefit,risk,effort).all { it in 0..100 })
        mutate { b -> edit(find(b,id)) { it.put("benefit",benefit).put("risk",risk).put("effort",effort).put("priority",priority(benefit,risk,effort)) };b }
    }
    fun update(id:String,status:String,note:String="",expectedRevision:Int?=null) {
        mutate { b -> val t=find(b,id)
            require(expectedRevision==null || t.getInt("revision")==expectedRevision) { "Task changed during review" }
            transition(t,status,note);b }
    }
    private fun transition(t:JSONObject,status:String,note:String) {
        val allowed=mapOf("OPEN" to setOf("CLAIMED","BLOCKED","REJECTED"),"CLAIMED" to setOf("OPEN","BLOCKED","REJECTED"),
            "BLOCKED" to setOf("OPEN","REJECTED"),"DONE" to setOf("OPEN"),"REJECTED" to setOf("OPEN"))
        require(status in allowed.getValue(t.getString("status"))) { "Invalid task transition; DONE requires verified Node receipt" }
        edit(t) { it.put("status",status).put("owner",if(status=="CLAIMED")"android-user" else JSONObject.NULL)
            .put("receipt",JSONObject.NULL).put("notes",JSONArray((strings(it,"notes")+note.take(240)).filter { s->s.isNotBlank() }.takeLast(12))) }
    }
    fun recover() = locked {
        val state=try { load().also { if(file.exists())atomic(File(file.path+".bak"),file.readBytes()) } }
        catch(e:Exception) {
            val backup=File(file.path+".bak");if(!backup.exists())throw e
            read(backup).also { if(file.exists())atomic(File(file.path+".corrupt-"+UUID.randomUUID()),file.readBytes()) }
        }
        for(t in tasks(state))if(t.getString("status")=="CLAIMED")transition(t,"BLOCKED","Recovered interrupted claim; review required")
        state.put("generation",state.getLong("generation")+1);validate(state)
        atomic(file,wire.encode(state).toByteArray(Charsets.UTF_8))
    }
    companion object { private val LOCK=Any() }
}
