package at.heinz.deviceai

import java.util.Base64
import at.heinz.deviceai.learning.StrictJson

/** One-shot Termux transport for owner-approved, commit-bound coding tasks.
 * Typed data ONLY. This object cannot grant shell/merge/APK permissions.
 */
internal object ContinuumTypedCodingBridge {
    const val INTENT_JSON = "continuum_typed_task_json_v1"
    const val INTENT_SHA = "continuum_typed_task_sha_v1"
    const val MAX_JSON = 8192
    const val MAX_RESPONSE = 8192
    private val SHA = Regex("[a-f0-9]{64}")
    private val ID = Regex("[a-z][a-z0-9-]{2,63}")
    private val COMMIT = Regex("[a-f0-9]{40}")
    private val CCC1 = Regex("t-[a-f0-9]{16}")
    private val ERROR = Regex("[A-Z][A-Z0-9_]{1,63}")
    private val fields = setOf("schema","version","id","sourceCommit","kind","goal","allowedPaths","acceptance","chain","limits")
    private val limitFields = setOf("maxModelCalls","maxContextChars")
    enum class Operation(val wire:String,val callback:String) {
        PREPARE("prepare","TYPED_CODING_PREPARE"),
        RUN("run","TYPED_CODING_RUN");
        companion object {
            fun ofCallback(name:String):Operation? = entries.firstOrNull { it.callback==name }
        }
    }
    data class Result(val operation:Operation,val id:String,val hash:String,val status:String,
        val message:String,val prepared:Boolean=false,val patchSha256:String?=null,val ccc1TaskId:String?=null)

    fun parseAuthorization(json:String?,hash:String?):ContinuumCodingTask.Authorization? {
        if(json == null || json.length !in 2..MAX_JSON || hash==null || !SHA.matches(hash))return null
        return runCatching {
            val obj=StrictJson.parseObject(json)
            require(obj.keys==fields && obj["schema"]=="continuum-coding-task" && obj["version"]==1L)
            fun list(key:String):List<String> {
                val array=obj[key] as? List<*> ?: error("Missing list")
                require(array.all { it is String })
                @Suppress("UNCHECKED_CAST")
                return array as List<String>
            }
            val limits=obj["limits"] as? Map<*,*> ?: error("Missing limits")
            require(limits.keys==limitFields)
            val maxCalls=(limits["maxModelCalls"] as? Long)?.toInt() ?: error("Model calls")
            val maxContext=(limits["maxContextChars"] as? Long)?.toInt() ?: error("Context")
            val task=ContinuumCodingTask(
                id=obj["id"] as String,sourceCommit=obj["sourceCommit"] as String,
                kind=ContinuumCodingTask.Kind.valueOf(obj["kind"] as String),
                goal=obj["goal"] as String,allowedPaths=list("allowedPaths"),
                acceptance=list("acceptance"),
                chain=ContinuumCodingTask.Chain.entries.first { it.wire==obj["chain"] },
                limits=ContinuumCodingTask.Limits(maxCalls,maxContext)
            )
            require(task.problems().isEmpty() && task.canonicalJson()==json && task.fingerprint()==hash)
            ContinuumCodingTask.Authorization(task,hash)
        }.getOrNull()
    }

    /** All saved fields must describe the same complete reviewed snapshot. */
    fun parseBoundAuthorization(json:String?,hash:String?,id:String?,sourceCommit:String?):ContinuumCodingTask.Authorization? =
        parseAuthorization(json,hash)?.takeIf { it.task.id==id && it.task.sourceCommit==sourceCommit }

    /**
     * Human-readable, lossless review of exactly the task bound to the typed bridge.
     * This remains presentation only; a task hash cannot authenticate its author.
     */
    fun preparationReview(authorization:ContinuumCodingTask.Authorization):String? = taskReview(authorization,false)

    fun executionReview(authorization:ContinuumCodingTask.Authorization):String? = taskReview(authorization,true)

    private fun taskReview(authorization:ContinuumCodingTask.Authorization,execute:Boolean):String? = runCatching {
        val task=authorization.task
        require(task.problems().isEmpty() && task.fingerprint()==authorization.taskSha256)
        require(task.canonicalJson().length in 2..MAX_JSON)
        buildString {
            append("AUFTRAG VOLLSTÄNDIG PRÜFEN\n\n")
            append("Task-ID:\n").append(task.id).append("\n\n")
            append("Auftragsart:\n").append(task.kind.name).append("\n\n")
            append("Ziel (vollständig):\n").append(task.goal).append("\n\n")
            append("Quell-Commit (40 Zeichen):\n").append(task.sourceCommit).append("\n\n")
            append("Erlaubte Quelldateien (").append(task.allowedPaths.size).append("):\n")
            task.allowedPaths.forEachIndexed { index, sourcePath ->
                append(index+1).append(". ").append(sourcePath).append('\n')
            }
            append("\nAkzeptanzkriterien (").append(task.acceptance.size).append("):\n")
            task.acceptance.forEachIndexed { index, criterion ->
                append(index+1).append(". ").append(criterion).append('\n')
            }
            append("\nPrüfkette:\n").append(task.chain.wire).append("\n\n")
            append("Ressourcenlimits:\n")
            append("Maximale Modellaufrufe: ").append(task.limits.maxModelCalls).append('\n')
            append("Maximale Kontextzeichen: ").append(task.limits.maxContextChars).append("\n\n")
            append("Vollständiger Task-SHA-256:\n").append(authorization.taskSha256).append("\n\n")
            if(execute) {
                append("AUSFÜHRUNG: Nur diesen vollständig geprüften Auftrag mit den angegebenen Grenzen starten. ")
                append("CCC1, feste Testkette, Reparatur und unabhängige Prüfung bleiben erforderlich. ")
                append("Keinen Patch automatisch übernehmen, keine APK installieren. ")
            } else {
                append("VORBEREITUNG: Nur den an den Fingerabdruck gebundenen CCC1-Auftrag anlegen. ")
                append("Kein KI-Modell ausführen, keinen Patch übernehmen, keine APK installieren. ")
            }
            append("Ein passender SHA-256-Fingerabdruck ist keine kryptografische Besitzeridentität.")
        }
    }.getOrNull()

    private const val SCRIPT=ContinuumMobileBuildPlan.PROJECT_DIR + "/scripts/continuum-typed-coding-bridge.mjs"
    fun commandPrepare(authorization:ContinuumCodingTask.Authorization):ContinuumMobileBuildPlan.Command {
        val task=authorization.task
        require(task.problems().isEmpty()&&task.fingerprint()==authorization.taskSha256)
        val json=task.canonicalJson()
        require(json.length<=MAX_JSON)
        val encoded=Base64.getUrlEncoder().withoutPadding().encodeToString(json.toByteArray(Charsets.UTF_8))
        return ContinuumMobileBuildPlan.Command(ContinuumMobileBuildPlan.NODE_EXECUTABLE,
            listOf(SCRIPT,"prepare",encoded,authorization.taskSha256),ContinuumMobileBuildPlan.PROJECT_DIR)
    }
    fun commandRun(id:String,hash:String):ContinuumMobileBuildPlan.Command {
        require(ID.matches(id)&&SHA.matches(hash))
        return ContinuumMobileBuildPlan.Command(ContinuumMobileBuildPlan.NODE_EXECUTABLE,
            listOf(SCRIPT,"run",id,hash),ContinuumMobileBuildPlan.PROJECT_DIR)
    }
    /** Callback never trusts model text or arbitrary Termux stdout. */
    fun evaluate(operation:Operation,id:String,hash:String,exitCode:Int?,termuxError:Int?,stdout:String?,expectedSourceCommit:String):Result {
        fun invalid()=Result(operation,id,hash,"INVALID","Coding-Rückmeldung ungültig. Kein Erfolg bestätigt.")
        if(!ID.matches(id)||!SHA.matches(hash)||termuxError != -1||exitCode==null||
            stdout==null||stdout.length !in 2..MAX_RESPONSE)return invalid()
        val o=runCatching { StrictJson.parseObject(stdout) }.getOrNull()?:return invalid()
        if(o["schema"]!="continuum-typed-coding-bridge"||o["version"]!=1L||
            o["operation"]!=operation.wire||o["id"]!=id||
            o["approvedForMerge"]!=false||o["installed"]!=false||o["deviceVerified"]!=false)
            return invalid()
        val status=o["status"] as? String ?: return invalid()
        if(status=="BLOCKED"&&exitCode!=0) {
            val failure=o["failure"] as? Map<*,*> ?: return invalid()
            val code=(failure["code"] as? String)?.takeIf { ERROR.matches(it) } ?: return invalid()
            return Result(operation,id,hash,status,"Lokaler Coding-Auftrag blockiert: $code. Keine Freigabe.")
        }
        if(exitCode!=0||o["taskSha256"]!=hash)return invalid()
        val source=(o["sourceCommit"] as? String)?.takeIf { COMMIT.matches(it) }?:return invalid()
        if(!COMMIT.matches(expectedSourceCommit)||source!=expectedSourceCommit)return invalid()
        if(operation==Operation.PREPARE && status=="PREPARED_NOT_EXECUTED") {
            val ccc1TaskId=o["ccc1TaskId"] as? String ?: return invalid()
            if(!CCC1.matches(ccc1TaskId) || o["replayed"] !is Boolean)return invalid()
            return Result(operation,id,hash,status,
                "Coding-Auftrag für Commit ${source.take(12)} vorbereitet. Noch kein Modell gestartet.",
                prepared=true,ccc1TaskId=ccc1TaskId)
        }
        if(operation==Operation.RUN && status in setOf("EXECUTION_REPORTED_UNVERIFIED","PATCH_PENDING_OWNER_REVIEW")){
            val patch=(o["patchSha256"] as? String)?.takeIf { SHA.matches(it) }
            if(status=="PATCH_PENDING_OWNER_REVIEW"&&patch==null)return invalid()
            if(status=="EXECUTION_REPORTED_UNVERIFIED"&&o["patchSha256"]!=null)return invalid()
            return Result(operation,id,hash,status,
                if(patch!=null)"Termux meldet einen Patch (${patch.take(12)}) zur Besitzerprüfung. Keine automatische Übernahme oder Geräteverifikation."
                else "Coding-Lauf gemeldet; kein unabhängig bestätigter Patch aus dieser Rückmeldung.",patchSha256=patch)
        }
        return invalid()
    }
}
