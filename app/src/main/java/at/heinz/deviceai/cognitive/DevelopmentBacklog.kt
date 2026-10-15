package at.heinz.deviceai.cognitive

import at.heinz.deviceai.learning.StrictJson
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.CodingErrorAction
import java.security.MessageDigest
import java.security.SecureRandom

class BacklogException(val code: String, val detail: String) : Exception("$code: $detail")

/**
 * Persistent development backlog shared with the Node agent and CLI.
 *
 * Same document format and state rules as `.github/continuum/development-backlog.mjs`;
 * both implementations run the shared conformance vectors in
 * `app/src/test/resources/continuum/backlog/`. Documents are generic JSON trees
 * (Map/List/String/Long/Boolean/null), so fields this version does not know
 * survive every rewrite. Canonical JSON: sorted keys, no whitespace, integers
 * only, UTF-8; "integrity" is the SHA-256 of the canonical document without it.
 * The hash detects corruption; it is not a signature.
 *
 * State changes only through [applyOp]. A task is DONE only with test evidence
 * that names the task, the submitted artifact and its submission token, with
 * every check passed and tests really executed; this is re-checked on every load.
 */
object DevelopmentBacklog {
    const val SCHEMA = "continuum-development-backlog"
    const val SCHEMA_VERSION = 2L
    val STATES = listOf("PROPOSED", "READY", "IN_PROGRESS", "VERIFYING", "BLOCKED", "DONE", "FAILED", "ABORTED", "REJECTED")
    val OPS = listOf("propose", "accept", "reject", "claim", "claimNext", "renew", "submit", "complete", "fail",
        "expire", "abort", "retry", "setPriority")
    val ARTIFACT_TYPES = listOf("PATCH", "PROPOSAL", "REPORT")
    val TEST_PROFILES = listOf("node-continuum", "android-unit", "jvm-harness")

    const val MAX_TASKS = 500
    const val MAX_HISTORY = 40
    const val MAX_EVIDENCE = 8
    const val MAX_DEPENDS_ON = 16
    const val MAX_PROFILES = 8
    const val MAX_TITLE = 200
    const val MAX_TEXT = 400
    const val MAX_SUBJECT_KEYS = 12
    const val MAX_SUBJECT_VALUE = 300
    const val MAX_FILE_BYTES = 4_000_000
    const val MAX_ATTEMPTS = 20L
    const val MAX_ATTEMPT_COUNT = 1000L
    const val MAX_PRIORITY = 1000L
    const val LEASE_MIN = 60_000L
    const val LEASE_MAX = 86_400_000L
    const val MAX_COUNT = 10_000_000L
    const val MAX_SAFE = 9_007_199_254_740_991L
    const val MAX_RECENT_OPS = 256

    private val TERMINAL = setOf("DONE", "ABORTED", "REJECTED")
    private val DEPENDENCY_FAILED = setOf("FAILED", "ABORTED", "REJECTED")
    private val HISTORY_OPS = (OPS + listOf("reconcile", "migrate")).toSet()

    private val ID_RE = Regex("[a-z][a-z0-9-]{2,63}")
    private val ACTOR_RE = Regex("[A-Za-z0-9][A-Za-z0-9._@:-]{0,63}")
    private val KIND_RE = Regex("[A-Z][A-Z0-9_]{1,31}")
    private val SOURCE_RE = Regex("[a-z][a-z0-9-]{1,31}")
    private val PROFILE_RE = Regex("[a-z][a-z0-9-]{1,47}")
    private val CODE_RE = Regex("[A-Z][A-Z0-9_]{1,47}")
    private val STRATEGY_RE = Regex("[A-Z][A-Z0-9_:]{1,79}")
    private val SUBJECT_KEY_RE = Regex("[a-zA-Z][a-zA-Z0-9]{0,31}")
    private val TOKEN_RE = Regex("[0-9a-f]{32}")
    private val SHA_RE = Regex("[0-9a-f]{64}")
    private val HEX16_RE = Regex("[0-9a-f]{16}")
    private val COMMIT_RE = Regex("[0-9a-f]{40}")
    private val OP_ID_RE = Regex("[A-Za-z0-9][A-Za-z0-9._:-]{7,79}")

    private fun fail(code: String, message: String): Nothing = throw BacklogException(code, message)
    private fun check(ok: Boolean, code: String, message: String) { if (!ok) fail(code, message) }

    // ------------------------------------------------------------------
    // Canonical JSON and strict parsing
    // ------------------------------------------------------------------
    private fun hasControl(s: String) = s.any { it.code < 0x20 || it.code == 0x7f }

    private fun hasLoneSurrogate(s: String): Boolean {
        var k = 0
        while (k < s.length) {
            val c = s[k]
            if (Character.isHighSurrogate(c)) {
                if (k + 1 >= s.length || !Character.isLowSurrogate(s[k + 1])) return true
                k += 2
            } else {
                if (Character.isLowSurrogate(c)) return true
                k++
            }
        }
        return false
    }

    /** Truncate to at most [n] UTF-16 units without splitting a surrogate pair. */
    fun clip(value: String, n: Int): String {
        val r = value.take(n)
        return if (r.isNotEmpty() && Character.isHighSurrogate(r.last())) r.dropLast(1) else r
    }

    /** Single-line text: control characters become spaces, then clip. */
    fun singleLine(value: String, n: Int): String =
        clip(buildString(value.length) { value.forEach { append(if (it.code < 0x20 || it.code == 0x7f) ' ' else it) } }, n)

    private fun quote(s: String, out: StringBuilder) {
        if (hasLoneSurrogate(s)) fail("INVALID_FIELD", "lone surrogate in string")
        out.append('"')
        for (c in s) {
            when {
                c == '"' -> out.append("\\\"")
                c == '\\' -> out.append("\\\\")
                c == '\b' -> out.append("\\b")
                c == '\u000c' -> out.append("\\f")
                c == '\n' -> out.append("\\n")
                c == '\r' -> out.append("\\r")
                c == '\t' -> out.append("\\t")
                c.code < 0x20 -> out.append("\\u00").append(HEX[c.code shr 4]).append(HEX[c.code and 15])
                else -> out.append(c)
            }
        }
        out.append('"')
    }

    private const val HEX = "0123456789abcdef"

    private fun write(value: Any?, out: StringBuilder, depth: Int) {
        if (depth > 64) fail("INVALID_FIELD", "nesting too deep")
        when (value) {
            null -> out.append("null")
            true -> out.append("true")
            false -> out.append("false")
            is Long -> { check(value in -MAX_SAFE..MAX_SAFE, "INVALID_FIELD", "only integers within ±2^53-1 are allowed"); out.append(value) }
            is Int -> out.append(value.toLong())
            is String -> quote(value, out)
            is List<*> -> {
                out.append('[')
                value.forEachIndexed { k, v -> if (k > 0) out.append(','); write(v, out, depth + 1) }
                out.append(']')
            }
            is Map<*, *> -> {
                out.append('{')
                val keys = value.keys.map { it as? String ?: fail("INVALID_FIELD", "object keys must be strings") }.sorted()
                keys.forEachIndexed { k, key ->
                    if (k > 0) out.append(',')
                    quote(key, out)
                    out.append(':')
                    write(value[key], out, depth + 1)
                }
                out.append('}')
            }
            else -> fail("INVALID_FIELD", "only integers within ±2^53-1 are allowed")
        }
    }

    fun canonical(value: Any?): String = StringBuilder().also { write(value, it, 0) }.toString()

    fun sha256Of(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    /** RFC 8259 with duplicate keys, non-integers, lone surrogates and trailing data rejected. */
    fun parseStrict(text: String): Any? {
        val value = try {
            StrictJson.parse(text, MAX_FILE_BYTES)
        } catch (e: IllegalArgumentException) {
            fail("CORRUPT", e.message ?: "invalid JSON")
        }
        fun walk(v: Any?) {
            when (v) {
                is Double -> fail("CORRUPT", "only integers are allowed")
                is Long -> check(v in -MAX_SAFE..MAX_SAFE, "CORRUPT", "integer outside ±2^53-1")
                is List<*> -> v.forEach(::walk)
                is Map<*, *> -> v.values.forEach(::walk)
            }
        }
        walk(value)
        return value
    }

    @Suppress("UNCHECKED_CAST")
    fun deepCopy(value: Any?): Any? = when (value) {
        is Map<*, *> -> LinkedHashMap<String, Any?>().also { m -> value.forEach { (k, v) -> m[k as String] = deepCopy(v) } }
        is List<*> -> ArrayList<Any?>(value.size).also { l -> value.forEach { l.add(deepCopy(it)) } }
        is Int -> value.toLong()
        else -> value
    }

    // ------------------------------------------------------------------
    // Validation
    // ------------------------------------------------------------------
    private fun isInt(v: Any?, min: Long, max: Long) = v is Long && v in min..max
    private fun isText(v: Any?, max: Int, allowEmpty: Boolean = false) =
        v is String && v.length <= max && (allowEmpty || v.isNotEmpty()) && !hasControl(v)
    private fun matches(v: Any?, re: Regex) = v is String && re.matches(v)

    @Suppress("UNCHECKED_CAST")
    private fun obj(v: Any?): Map<String, Any?>? = v as? Map<String, Any?>

    private fun validSubject(s: Any?): Boolean {
        if (s == null) return true
        val m = obj(s) ?: return false
        if (m.size > MAX_SUBJECT_KEYS) return false
        return m.all { (k, v) ->
            SUBJECT_KEY_RE.matches(k) && (v is Boolean || isInt(v, -MAX_SAFE, MAX_SAFE) || isText(v, MAX_SUBJECT_VALUE, true))
        }
    }

    fun validateEvidence(raw: Any?, code: String = "INVALID_FIELD"): Map<String, Any?> {
        val e = obj(raw) ?: fail(code, "evidence must be an object")
        check(e["type"] == "TEST_RUN", code, "evidence type must be TEST_RUN")
        check(matches(e["taskId"], ID_RE), code, "evidence taskId invalid")
        check(matches(e["submissionToken"], TOKEN_RE), code, "evidence submissionToken invalid")
        check(matches(e["artifactSha256"], SHA_RE), code, "evidence artifactSha256 invalid")
        check(matches(e["chain"], SOURCE_RE), code, "evidence chain invalid")
        check(matches(e["commit"], COMMIT_RE), code, "evidence commit invalid")
        check(isInt(e["at"], 0, MAX_SAFE), code, "evidence at invalid")
        check(matches(e["by"], ACTOR_RE), code, "evidence by invalid")
        val profiles = e["profiles"] as? List<*>
        check(profiles != null && profiles.size in 1..MAX_PROFILES, code, "evidence needs 1..8 profiles")
        for (raw2 in profiles!!) {
            val p = obj(raw2)
            check(p != null && matches(p["profile"], PROFILE_RE) && p["passed"] is Boolean &&
                isInt(p["executed"], 0, MAX_COUNT) && isInt(p["failed"], 0, MAX_COUNT) && isInt(p["skipped"], 0, MAX_COUNT),
                code, "evidence profile invalid")
        }
        // a missing "repair" is as invalid as in the Node implementation (undefined !== null)
        check(e.containsKey("repair"), code, "evidence repair reference invalid")
        if (e["repair"] != null) {
            val r = obj(e["repair"])
            check(r != null && matches(r["diagnostic"], HEX16_RE) && matches(r["candidate"], HEX16_RE) &&
                matches(r["strategy"], STRATEGY_RE) && matches(r["patchSha256"], SHA_RE), code, "evidence repair reference invalid")
        }
        return e
    }

    /** Why [e] does not prove [task] done (empty = sufficient). */
    fun evidenceProblems(task: Map<String, Any?>, e: Map<String, Any?>): List<String> {
        val problems = ArrayList<String>()
        if (e["taskId"] != task["id"]) problems += "evidence belongs to another task"
        val s = obj(task["submission"])
        if (s == null) {
            problems += "task has no submission"
        } else {
            if (e["submissionToken"] != s["token"]) problems += "evidence does not match the submission"
            if (e["artifactSha256"] != obj(s["artifact"])!!["sha256"]) problems += "evidence tested a different artifact"
            if ((e["at"] as Long) < (s["at"] as Long)) problems += "evidence predates the submission"
        }
        val profiles = (e["profiles"] as List<*>).map { obj(it)!! }
        if (profiles.any { it["passed"] != true || (it["failed"] as Long) > 0 }) problems += "a check failed"
        if (profiles.any { it["profile"] in TEST_PROFILES && (it["executed"] as Long) <= 0 }) problems += "test profile without executed tests"
        if (profiles.sumOf { it["executed"] as Long } <= 0) problems += "no executed tests"
        return problems
    }

    private fun validateTask(raw: Any?) {
        val c = "CORRUPT"
        val t = obj(raw) ?: fail(c, "task must be an object")
        check(matches(t["id"], ID_RE), c, "task id invalid")
        val where = " (task ${t["id"]})"
        check(isText(t["title"], MAX_TITLE), c, "title invalid$where")
        check(matches(t["kind"], KIND_RE), c, "kind invalid$where")
        check(matches(t["source"], SOURCE_RE), c, "source invalid$where")
        check(t["state"] in STATES, c, "state invalid$where")
        check(isInt(t["priority"], 0, MAX_PRIORITY), c, "priority invalid$where")
        val deps = t["dependsOn"] as? List<*>
        check(deps != null && deps.size <= MAX_DEPENDS_ON, c, "dependsOn invalid$where")
        deps!!.forEachIndexed { k, d ->
            check(matches(d, ID_RE) && d != t["id"] && (k == 0 || (deps[k - 1] as String) < (d as String)), c,
                "dependsOn must be sorted, unique task ids$where")
        }
        check(isInt(t["attempts"], 0, MAX_ATTEMPT_COUNT) && isInt(t["maxAttempts"], 1, MAX_ATTEMPTS), c, "attempt budget invalid$where")
        check(t.containsKey("claim") && t.containsKey("submission") && t.containsKey("error") && t.containsKey("subject"), c,
            "task fields missing$where")
        if (t["claim"] != null) {
            val k = obj(t["claim"])
            check(k != null && matches(k["owner"], ACTOR_RE) && matches(k["token"], TOKEN_RE) && isInt(k["at"], 0, MAX_SAFE) &&
                isInt(k["leaseUntil"], 0, MAX_SAFE) && (k["leaseUntil"] as Long) > (k["at"] as Long), c, "claim invalid$where")
        }
        check((t["state"] == "IN_PROGRESS") == (t["claim"] != null), c, "claim must exist exactly while IN_PROGRESS$where")
        if (t["submission"] != null) {
            val s = obj(t["submission"])
            val a = obj(s?.get("artifact"))
            check(s != null && matches(s["token"], TOKEN_RE) && matches(s["by"], ACTOR_RE) && isInt(s["at"], 0, MAX_SAFE) &&
                a != null && a["type"] in ARTIFACT_TYPES && matches(a["sha256"], SHA_RE), c, "submission invalid$where")
        }
        check((t["state"] == "VERIFYING" || t["state"] == "DONE") == (t["submission"] != null), c,
            "submission must exist exactly while VERIFYING or DONE$where")
        val evidence = t["evidence"] as? List<*>
        check(evidence != null && evidence.size <= MAX_EVIDENCE, c, "evidence list invalid$where")
        evidence!!.forEach { validateEvidence(it, c) }
        if (t["state"] == "DONE") {
            check(evidence.any { evidenceProblems(t, obj(it)!!).isEmpty() }, c, "DONE without matching test evidence$where")
        }
        val history = t["history"] as? List<*>
        check(history != null && history.size <= MAX_HISTORY, c, "history invalid$where")
        for (rawH in history!!) {
            val h = obj(rawH)
            check(h != null && isInt(h["at"], 0, MAX_SAFE) && matches(h["by"], ACTOR_RE) && h["op"] in HISTORY_OPS &&
                h.containsKey("from") && (h["from"] == null || h["from"] in STATES) && h["to"] in STATES &&
                isText(h["reason"], MAX_TEXT, true), c, "history entry invalid$where")
        }
        check(isInt(t["historyDropped"], 0, MAX_SAFE) && isInt(t["evidenceDropped"], 0, MAX_SAFE), c, "dropped counters invalid$where")
        if (t["error"] != null) {
            val e = obj(t["error"])
            check(e != null && matches(e["code"], CODE_RE) && isText(e["message"], MAX_TEXT, true) && isInt(e["at"], 0, MAX_SAFE), c,
                "error invalid$where")
        }
        check(isInt(t["createdAt"], 0, MAX_SAFE) && isInt(t["updatedAt"], (t["createdAt"] as? Long) ?: 0, MAX_SAFE), c,
            "timestamps invalid$where")
        check(validSubject(t["subject"]), c, "subject invalid$where")
    }

    fun validateDocument(doc: Map<String, Any?>) {
        val c = "CORRUPT"
        check(doc["schema"] == SCHEMA, c, "not a development backlog")
        val version = doc["schemaVersion"]
        if (version is Long && version in (SCHEMA_VERSION + 1)..MAX_SAFE) fail("UNSUPPORTED_VERSION", "schemaVersion $version is newer than $SCHEMA_VERSION")
        check(version == SCHEMA_VERSION, c, "schemaVersion must be $SCHEMA_VERSION")
        check(isInt(doc["revision"], 0, MAX_SAFE), c, "revision invalid")
        check(isInt(doc["updatedAt"], 0, MAX_SAFE), c, "updatedAt invalid")
        check(matches(doc["updatedBy"], ACTOR_RE), c, "updatedBy invalid")
        check(!doc.containsKey("integrity") || matches(doc["integrity"], SHA_RE), c, "integrity invalid")
        if (doc.containsKey("recentOpIds")) {
            val r = doc["recentOpIds"] as? List<*>
            check(r != null && r.size <= MAX_RECENT_OPS && r.all { matches(it, OP_ID_RE) } && r.toSet().size == r.size, c, "recentOpIds invalid")
        }
        val tasks = doc["tasks"] as? List<*>
        check(tasks != null && tasks.size <= MAX_TASKS, c, "tasks invalid")
        tasks!!.forEachIndexed { k, raw ->
            validateTask(raw)
            check(k == 0 || (obj(tasks[k - 1])!!["id"] as String) < (obj(raw)!!["id"] as String), c, "tasks must be sorted by id and unique")
        }
        val byId = tasks.associate { (obj(it)!!["id"] as String) to obj(it)!! }
        for (t in byId.values) for (d in t["dependsOn"] as List<*>) check(d in byId, c, "unknown dependency $d of ${t["id"]}")
        val color = HashMap<String, Int>()
        for (start in byId.keys) {
            if (color[start] != null) continue
            val stack = ArrayList<Pair<String, IntArray>>()
            stack += start to intArrayOf(0)
            color[start] = 1
            while (stack.isNotEmpty()) {
                val (id, pos) = stack.last()
                val deps = byId.getValue(id)["dependsOn"] as List<*>
                if (pos[0] < deps.size) {
                    val next = deps[pos[0]++] as String
                    when (color[next]) {
                        1 -> fail(c, "dependency cycle through $next")
                        null -> { color[next] = 1; stack += next to intArrayOf(0) }
                    }
                } else {
                    color[id] = 2
                    stack.removeAt(stack.size - 1)
                }
            }
        }
        canonical(doc)
    }

    // ------------------------------------------------------------------
    // Encoding, integrity, migration
    // ------------------------------------------------------------------
    private fun withoutIntegrity(doc: Map<String, Any?>): Map<String, Any?> = LinkedHashMap(doc).apply { remove("integrity") }

    fun integrityOf(doc: Map<String, Any?>): String = sha256Of(canonical(withoutIntegrity(doc)).toByteArray(Charsets.UTF_8))

    fun emptyDocument(at: Long, actor: String): MutableMap<String, Any?> = linkedMapOf(
        "schema" to SCHEMA, "schemaVersion" to SCHEMA_VERSION, "revision" to 0L, "updatedAt" to at, "updatedBy" to actor,
        "tasks" to ArrayList<Any?>())

    /** Canonical bytes of a validated document including its integrity hash. */
    fun encode(doc: Map<String, Any?>): ByteArray {
        validateDocument(doc)
        val body = withoutIntegrity(doc)
        val full = LinkedHashMap(body).apply { put("integrity", integrityOf(body)) }
        return (canonical(full) + "\n").toByteArray(Charsets.UTF_8)
    }

    data class Decoded(val doc: MutableMap<String, Any?>, val migratedFrom: Int?)

    /** Bytes -> verified document; schema version 1 is migrated only when [migrateAt]/[migrateActor] are given. */
    @Suppress("UNCHECKED_CAST")
    fun decode(bytes: ByteArray, migrateAt: Long? = null, migrateActor: String? = null): Decoded {
        if (bytes.size > MAX_FILE_BYTES) fail("CORRUPT", "backlog larger than $MAX_FILE_BYTES bytes")
        val text = try {
            Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString()
        } catch (e: CharacterCodingException) {
            fail("CORRUPT", "invalid UTF-8")
        }
        val value = parseStrict(text)
        val doc = value as? MutableMap<String, Any?> ?: fail("CORRUPT", "document must be an object")
        if (!doc.containsKey("schema") && doc["schemaVersion"] == 1L) {
            if (migrateAt == null || migrateActor == null) fail("UNSUPPORTED_VERSION", "schemaVersion 1 requires migration")
            return Decoded(migrateV1(doc, migrateAt, migrateActor), 1)
        }
        validateDocument(doc)
        check(doc["integrity"] is String, "CORRUPT", "integrity missing")
        if (integrityOf(doc) != doc["integrity"]) fail("INTEGRITY_MISMATCH", "content does not match its integrity hash")
        return Decoded(doc, null)
    }

    private val CONFIDENCE_BONUS = mapOf("high" to 30L, "medium" to 20L, "low" to 10L)
    private val FINDING_SCORE = mapOf("DEAD_CLUSTER" to 3L, "TEST_ONLY" to 2L, "UNREFERENCED" to 1L)

    /** Planner paths are relative to app/src; backlog paths are repository-relative. */
    fun repoPath(file: String): String = if (Regex("(main|test|androidTest)/.*", RegexOption.DOT_MATCHES_ALL).matches(file)) "app/src/$file" else file

    fun findingTaskId(kind: String, file: String, symbol: String): String =
        "inv-" + sha256Of("$kind\u0000$file\u0000$symbol".toByteArray(Charsets.UTF_8)).take(16)

    fun findingPriority(kind: String, confidence: String): Long =
        minOf(MAX_PRIORITY, (FINDING_SCORE[kind] ?: 0L) * 100 + (CONFIDENCE_BONUS[confidence] ?: 0L))

    private fun scalarText(v: Any?, fallback: String) = when (v) {
        null -> fallback
        is String -> v
        is Long, is Boolean -> v.toString()
        else -> fail("CORRUPT", "v1 task invalid")
    }

    fun findingTask(f: Map<String, Any?>): MutableMap<String, Any?> {
        val kind = scalarText(f["kind"], "")
        val file = singleLine(repoPath(scalarText(f["file"], "")), MAX_SUBJECT_VALUE)
        val symbol = singleLine(scalarText(f["symbol"], ""), MAX_SUBJECT_VALUE)
        val shortName = symbol.split('.').last().ifEmpty { symbol }
        val confidence = scalarText(f["confidence"], "unknown")
        val subject = linkedMapOf<String, Any?>("finding" to singleLine(kind, 40), "symbol" to symbol, "file" to file,
            "confidence" to singleLine(confidence, 40))
        return linkedMapOf("id" to findingTaskId(kind, file, symbol), "title" to singleLine("$kind: $shortName ($file)", MAX_TITLE),
            "kind" to "INVESTIGATE", "source" to "code-analyzer", "priority" to findingPriority(kind, confidence),
            "dependsOn" to ArrayList<Any?>(), "maxAttempts" to 3L, "subject" to subject)
    }

    /** schemaVersion 1 (development-planner) -> 2; nothing of the input is dropped. */
    @Suppress("UNCHECKED_CAST")
    fun migrateV1(v1: Map<String, Any?>, at: Long, actor: String): MutableMap<String, Any?> {
        val rawTasks = v1["tasks"] as? List<*>
        check(rawTasks != null && rawTasks.size <= MAX_TASKS, "CORRUPT", "v1 tasks invalid")
        var doc: Map<String, Any?> = emptyDocument(at, actor)
        val extra = LinkedHashMap<String, Any?>()
        for ((k, v) in v1) {
            if (k == "tasks" || k == "schemaVersion") continue
            check(v is String || isInt(v, -MAX_SAFE, MAX_SAFE) || v is Boolean, "CORRUPT", "v1 field $k cannot be migrated")
            check(SUBJECT_KEY_RE.matches(k), "CORRUPT", "v1 field name $k cannot be migrated")
            extra[k] = v
        }
        var duplicates = 0L
        for (raw in rawTasks!!) {
            val r = obj(raw)
            check(r != null && r["kind"] is String && r["symbol"] is String && r["file"] is String &&
                (r["confidence"] == null || r["confidence"] is String || r["confidence"] is Boolean || isInt(r["confidence"], -MAX_SAFE, MAX_SAFE)),
                "CORRUPT", "v1 task invalid")
            val task = findingTask(r!!)
            val subject = task["subject"] as MutableMap<String, Any?>
            for ((k, v) in r) {
                if (k in setOf("kind", "symbol", "file", "confidence")) continue
                check(isText(v, MAX_SUBJECT_VALUE, true) || isInt(v, -MAX_SAFE, MAX_SAFE) || v is Boolean, "CORRUPT",
                    "v1 task field $k cannot be migrated")
                check(SUBJECT_KEY_RE.matches(k), "CORRUPT", "v1 task field name $k cannot be migrated")
                subject[k] = v
            }
            if ((doc["tasks"] as List<*>).any { obj(it)!!["id"] == task["id"] }) { duplicates++; continue }
            doc = applyOp(doc, linkedMapOf("op" to "propose", "actor" to actor, "at" to at, "task" to task,
                "reason" to "migrated from schemaVersion 1")).doc
            val created = (doc["tasks"] as List<*>).map { it as MutableMap<String, Any?> }.first { it["id"] == task["id"] }
            ((created["history"] as List<*>).last() as MutableMap<String, Any?>)["op"] = "migrate"
        }
        val result = LinkedHashMap(doc)
        result["migratedFrom"] = linkedMapOf<String, Any?>("schemaVersion" to 1L).apply { putAll(extra); put("duplicateTasks", duplicates) }
        return result
    }

    // ------------------------------------------------------------------
    // Operations (same rules as development-backlog.mjs)
    // ------------------------------------------------------------------
    data class OpResult(val doc: Map<String, Any?>, val changed: Boolean, val id: String? = null,
                        val expired: List<String> = emptyList(), val existed: Boolean = false, val duplicate: Boolean = false)

    private class Outcome(val changed: Boolean, val id: String? = null, val expired: List<String> = emptyList(), val existed: Boolean = false)

    @Suppress("UNCHECKED_CAST")
    private fun tasksOf(doc: MutableMap<String, Any?>) = doc["tasks"] as MutableList<MutableMap<String, Any?>>

    private fun requireTask(doc: MutableMap<String, Any?>, id: Any?): MutableMap<String, Any?> {
        check(matches(id, ID_RE), "INVALID_FIELD", "task id invalid")
        return tasksOf(doc).firstOrNull { it["id"] == id } ?: fail("NOT_FOUND", "task $id")
    }

    @Suppress("UNCHECKED_CAST")
    private fun record(task: MutableMap<String, Any?>, actor: String, at: Long, opName: String, to: String, reason: String) {
        val history = task["history"] as MutableList<Any?>
        history += linkedMapOf("at" to at, "by" to actor, "op" to opName, "from" to task["state"], "to" to to,
            "reason" to singleLine(reason, MAX_TEXT))
        while (history.size > MAX_HISTORY) { history.removeAt(0); task["historyDropped"] = (task["historyDropped"] as Long) + 1 }
        task["state"] = to
        task["updatedAt"] = maxOf(task["updatedAt"] as Long, at)
    }

    private fun requireReason(op: Map<String, Any?>): String {
        check(isText(op["reason"], MAX_TEXT), "INVALID_FIELD", "reason required (1..400 characters)")
        return op["reason"] as String
    }

    private fun requireToken(v: Any?): String {
        check(matches(v, TOKEN_RE), "INVALID_FIELD", "token must be 32 hex characters")
        return v as String
    }

    private fun requireLease(v: Any?): Long {
        check(isInt(v, LEASE_MIN, LEASE_MAX), "INVALID_FIELD", "leaseMs must be 60000..86400000")
        return v as Long
    }

    private fun depsDone(doc: MutableMap<String, Any?>, t: Map<String, Any?>) =
        (t["dependsOn"] as List<*>).all { d -> tasksOf(doc).firstOrNull { it["id"] == d }?.get("state") == "DONE" }

    private fun eligible(doc: MutableMap<String, Any?>, t: Map<String, Any?>) =
        t["state"] == "READY" && depsDone(doc, t) && (t["attempts"] as Long) < (t["maxAttempts"] as Long)

    private val priorityOrder = Comparator<Map<String, Any?>> { a, b ->
        val p = (b["priority"] as Long).compareTo(a["priority"] as Long)
        if (p != 0) return@Comparator p
        val c = (a["createdAt"] as Long).compareTo(b["createdAt"] as Long)
        if (c != 0) c else (a["id"] as String).compareTo(b["id"] as String)
    }

    private fun claimTask(doc: MutableMap<String, Any?>, t: MutableMap<String, Any?>, op: Map<String, Any?>, actor: String, at: Long, opName: String) {
        if (t["state"] != "READY") fail("INVALID_TRANSITION", "${t["id"]} is ${t["state"]}, not READY")
        if (!depsDone(doc, t)) fail("DEPENDENCY_NOT_DONE", "${t["id"]} depends on unfinished tasks")
        if ((t["attempts"] as Long) >= (t["maxAttempts"] as Long)) {
            fail("BUDGET_EXHAUSTED", "${t["id"]} used ${t["attempts"]} of ${t["maxAttempts"]} attempts")
        }
        check(matches(op["owner"], ACTOR_RE), "INVALID_FIELD", "owner invalid")
        val token = requireToken(op["token"])
        val lease = requireLease(op["leaseMs"])
        record(t, actor, at, opName, "IN_PROGRESS", "claimed by ${op["owner"]}")
        t["claim"] = linkedMapOf("owner" to op["owner"], "token" to token, "at" to at, "leaseUntil" to at + lease)
        t["attempts"] = (t["attempts"] as Long) + 1
        t["error"] = null
    }

    private fun releaseAfterFailure(t: MutableMap<String, Any?>, actor: String, at: Long, opName: String, error: Map<String, Any?>, reason: String) {
        val exhausted = (t["attempts"] as Long) >= (t["maxAttempts"] as Long)
        record(t, actor, at, opName, if (exhausted) "FAILED" else "READY", reason + if (exhausted) " (attempt budget exhausted)" else "")
        t["claim"] = null
        t["submission"] = null
        t["error"] = error
    }

    @Suppress("UNCHECKED_CAST")
    private fun appendEvidence(t: MutableMap<String, Any?>, e: Map<String, Any?>) {
        val list = t["evidence"] as MutableList<Any?>
        list += deepCopy(e)
        while (list.size > MAX_EVIDENCE) { list.removeAt(0); t["evidenceDropped"] = (t["evidenceDropped"] as Long) + 1 }
    }

    @Suppress("UNCHECKED_CAST")
    private fun handle(doc: MutableMap<String, Any?>, op: Map<String, Any?>, actor: String, at: Long): Outcome {
        val name = op["op"] as String
        when (name) {
            "propose" -> {
                val p = obj(op["task"]) ?: fail("INVALID_FIELD", "task required")
                check(matches(p["id"], ID_RE), "INVALID_FIELD", "task id must match $ID_RE")
                if (tasksOf(doc).any { it["id"] == p["id"] }) {
                    if (op["ifAbsent"] == true) return Outcome(false, p["id"] as String, existed = true)
                    fail("DUPLICATE_ID", p["id"] as String)
                }
                if (tasksOf(doc).size >= MAX_TASKS) fail("LIMIT", "backlog holds at most $MAX_TASKS tasks")
                check(isText(p["title"], MAX_TITLE), "INVALID_FIELD", "title must be 1..200 characters without control characters")
                check(matches(p["kind"], KIND_RE), "INVALID_FIELD", "kind invalid")
                check(matches(p["source"], SOURCE_RE), "INVALID_FIELD", "source invalid")
                val priority = deepCopy(p["priority"] ?: 500L)
                val maxAttempts = deepCopy(p["maxAttempts"] ?: 3L)
                val rawDeps = p["dependsOn"] ?: emptyList<Any?>()
                val subject = deepCopy(p["subject"])
                check(isInt(priority, 0, MAX_PRIORITY), "INVALID_FIELD", "priority must be 0..1000")
                check(isInt(maxAttempts, 1, MAX_ATTEMPTS), "INVALID_FIELD", "maxAttempts must be 1..20")
                check(rawDeps is List<*> && rawDeps.all { matches(it, ID_RE) }, "INVALID_FIELD", "dependsOn must be task ids")
                val dependsOn = (rawDeps as List<*>).map { it as String }.toSortedSet().toMutableList<Any?>()
                check(dependsOn.size <= MAX_DEPENDS_ON, "INVALID_FIELD", "at most $MAX_DEPENDS_ON dependencies")
                check(validSubject(subject), "INVALID_FIELD", "subject must be a flat object of up to 12 strings, integers or booleans")
                for (d in dependsOn) if (tasksOf(doc).none { it["id"] == d }) fail("UNKNOWN_DEPENDENCY", d as String)
                val reason = op["reason"] ?: ""
                check(isText(reason, MAX_TEXT, true), "INVALID_FIELD", "reason invalid")
                val task = linkedMapOf<String, Any?>("id" to p["id"], "title" to p["title"], "kind" to p["kind"], "source" to p["source"],
                    "state" to "PROPOSED", "priority" to priority, "dependsOn" to dependsOn, "attempts" to 0L, "maxAttempts" to maxAttempts,
                    "claim" to null, "submission" to null, "evidence" to ArrayList<Any?>(), "evidenceDropped" to 0L,
                    "history" to arrayListOf<Any?>(linkedMapOf("at" to at, "by" to actor, "op" to "propose", "from" to null,
                        "to" to "PROPOSED", "reason" to reason)),
                    "historyDropped" to 0L, "error" to null, "createdAt" to at, "updatedAt" to at, "subject" to subject)
                tasksOf(doc) += task
                tasksOf(doc).sortWith { a, b -> (a["id"] as String).compareTo(b["id"] as String) }
                validateTask(task)
                return Outcome(true, task["id"] as String)
            }
            "accept" -> {
                val t = requireTask(doc, op["id"])
                if (t["state"] != "PROPOSED") fail("INVALID_TRANSITION", "${t["id"]} is ${t["state"]}, not PROPOSED")
                record(t, actor, at, name, "READY", if (op["reason"] == null) "accepted" else requireReason(op))
                return Outcome(true, t["id"] as String)
            }
            "reject" -> {
                val t = requireTask(doc, op["id"])
                if (t["state"] !in setOf("PROPOSED", "READY", "BLOCKED", "FAILED")) fail("INVALID_TRANSITION", "${t["id"]} is ${t["state"]}")
                record(t, actor, at, name, "REJECTED", requireReason(op))
                t["claim"] = null
                t["submission"] = null
                return Outcome(true, t["id"] as String)
            }
            "claim" -> {
                val t = requireTask(doc, op["id"])
                claimTask(doc, t, op, actor, at, name)
                return Outcome(true, t["id"] as String)
            }
            "claimNext" -> {
                val kinds = op["kinds"]
                check(kinds == null || (kinds is List<*> && kinds.size <= 16 && kinds.all { matches(it, KIND_RE) }), "INVALID_FIELD", "kinds invalid")
                val t = tasksOf(doc).filter { eligible(doc, it) && (kinds == null || it["kind"] in (kinds as List<*>)) }
                    .sortedWith(priorityOrder).firstOrNull()
                    ?: fail("NO_ELIGIBLE_TASK", "no READY task with finished dependencies and remaining budget")
                claimTask(doc, t, op, actor, at, name)
                return Outcome(true, t["id"] as String)
            }
            "renew" -> {
                val t = requireTask(doc, op["id"])
                if (t["state"] != "IN_PROGRESS") fail("INVALID_TRANSITION", "${t["id"]} is ${t["state"]}")
                val claim = t["claim"] as MutableMap<String, Any?>
                if (claim["token"] != requireToken(op["token"])) fail("TOKEN_MISMATCH", "claim belongs to another execution")
                if (at > claim["leaseUntil"] as Long) fail("LEASE_EXPIRED", "lease ended at ${claim["leaseUntil"]}")
                claim["leaseUntil"] = at + requireLease(op["leaseMs"])
                t["updatedAt"] = maxOf(t["updatedAt"] as Long, at)
                return Outcome(true, t["id"] as String)
            }
            "submit" -> {
                val t = requireTask(doc, op["id"])
                if (t["state"] != "IN_PROGRESS") fail("INVALID_TRANSITION", "${t["id"]} is ${t["state"]}")
                val claim = t["claim"] as Map<String, Any?>
                if (claim["token"] != requireToken(op["token"])) fail("TOKEN_MISMATCH", "claim belongs to another execution")
                if (at > claim["leaseUntil"] as Long) fail("LEASE_EXPIRED", "lease ended at ${claim["leaseUntil"]}")
                val a = obj(op["artifact"])
                check(a != null && a["type"] in ARTIFACT_TYPES && matches(a["sha256"], SHA_RE), "INVALID_FIELD", "artifact must be {type,sha256}")
                record(t, actor, at, name, "VERIFYING", "submitted ${a!!["type"]} ${(a["sha256"] as String).take(12)}")
                t["submission"] = linkedMapOf("token" to claim["token"], "by" to actor, "at" to at,
                    "artifact" to linkedMapOf("type" to a["type"], "sha256" to a["sha256"]))
                t["claim"] = null
                return Outcome(true, t["id"] as String)
            }
            "complete" -> {
                val t = requireTask(doc, op["id"])
                if (t["state"] != "VERIFYING") fail("INVALID_TRANSITION", "${t["id"]} is ${t["state"]}, not VERIFYING")
                val e = validateEvidence(op["evidence"])
                val problems = evidenceProblems(t, e)
                if (problems.isNotEmpty()) fail("EVIDENCE_REJECTED", problems.joinToString("; "))
                appendEvidence(t, e)
                val summary = (e["profiles"] as List<*>).joinToString(", ") { val p = obj(it)!!; "${p["profile"]} ${p["executed"]} tests" }
                record(t, actor, at, name, "DONE", "verified by $summary")
                t["error"] = null
                return Outcome(true, t["id"] as String)
            }
            "fail" -> {
                val t = requireTask(doc, op["id"])
                val token = requireToken(op["token"])
                when (t["state"]) {
                    "IN_PROGRESS" -> if (obj(t["claim"])!!["token"] != token) fail("TOKEN_MISMATCH", "claim belongs to another execution")
                    "VERIFYING" -> if (obj(t["submission"])!!["token"] != token) fail("TOKEN_MISMATCH", "submission belongs to another execution")
                    else -> fail("INVALID_TRANSITION", "${t["id"]} is ${t["state"]}")
                }
                val er = obj(op["error"])
                check(er != null && matches(er["code"], CODE_RE) && isText(er["message"], MAX_TEXT, true), "INVALID_FIELD", "error must be {code,message}")
                if (op["evidence"] != null) {
                    val e = validateEvidence(op["evidence"])
                    check(e["taskId"] == t["id"], "INVALID_FIELD", "evidence belongs to another task")
                    appendEvidence(t, e)
                }
                releaseAfterFailure(t, actor, at, name, linkedMapOf("code" to er!!["code"], "message" to er["message"], "at" to at), er["code"] as String)
                return Outcome(true, t["id"] as String)
            }
            "expire" -> {
                val expired = ArrayList<String>()
                for (t in tasksOf(doc)) {
                    if (t["state"] != "IN_PROGRESS") continue
                    val claim = obj(t["claim"])!!
                    if (at <= claim["leaseUntil"] as Long) continue
                    releaseAfterFailure(t, actor, at, name, linkedMapOf("code" to "LEASE_EXPIRED",
                        "message" to "lease of ${claim["owner"]} expired", "at" to at), "LEASE_EXPIRED")
                    expired += t["id"] as String
                }
                return Outcome(expired.isNotEmpty(), expired = expired)
            }
            "abort" -> {
                val t = requireTask(doc, op["id"])
                if (t["state"] in TERMINAL) fail("INVALID_TRANSITION", "${t["id"]} is ${t["state"]}")
                record(t, actor, at, name, "ABORTED", requireReason(op))
                t["claim"] = null
                t["submission"] = null
                return Outcome(true, t["id"] as String)
            }
            "retry" -> {
                val t = requireTask(doc, op["id"])
                if (t["state"] != "FAILED") fail("INVALID_TRANSITION", "${t["id"]} is ${t["state"]}, not FAILED")
                record(t, actor, at, name, "READY", requireReason(op))
                t["attempts"] = 0L
                t["error"] = null
                return Outcome(true, t["id"] as String)
            }
            "setPriority" -> {
                val t = requireTask(doc, op["id"])
                if (t["state"] in TERMINAL) fail("INVALID_TRANSITION", "${t["id"]} is ${t["state"]}")
                check(isInt(op["priority"], 0, MAX_PRIORITY), "INVALID_FIELD", "priority must be 0..1000")
                if (op["priority"] == t["priority"]) return Outcome(false, t["id"] as String)
                record(t, actor, at, name, t["state"] as String, "priority ${t["priority"]} -> ${op["priority"]}")
                t["priority"] = op["priority"]
                return Outcome(true, t["id"] as String)
            }
        }
        fail("INVALID_OP", "unknown operation $name")
    }

    /** READY tasks with a failed dependency become BLOCKED, and back once it recovers. */
    private fun reconcile(doc: MutableMap<String, Any?>, actor: String, at: Long) {
        val byId = tasksOf(doc).associateBy { it["id"] as String }
        for (t in tasksOf(doc)) {
            val broken = (t["dependsOn"] as List<*>).firstOrNull { byId.getValue(it as String)["state"] in DEPENDENCY_FAILED } as String?
            if (t["state"] == "READY" && broken != null) {
                record(t, actor, at, "reconcile", "BLOCKED", "dependency $broken is ${byId.getValue(broken)["state"]}")
            } else if (t["state"] == "BLOCKED" && broken == null) {
                record(t, actor, at, "reconcile", "READY", "dependencies recovered")
            }
        }
    }

    /**
     * Apply one operation; the input document is never mutated. Throws
     * [BacklogException] with a stable code; then nothing changed. An operation
     * with an `opId` that was already applied is a reported no-op (exactly-once op logs).
     */
    @Suppress("UNCHECKED_CAST")
    fun applyOp(doc: Map<String, Any?>, op: Map<String, Any?>): OpResult {
        check(op["op"] in OPS, "INVALID_OP", "unknown operation ${op["op"].toString().take(40)}")
        check(matches(op["actor"], ACTOR_RE), "INVALID_FIELD", "actor invalid")
        check(isInt(deepCopy(op["at"]), 0, MAX_SAFE), "INVALID_FIELD", "at must be a non-negative integer (epoch ms)")
        val opId = op["opId"]
        if (opId != null) {
            check(matches(opId, OP_ID_RE), "INVALID_FIELD", "opId must match $OP_ID_RE")
            if ((doc["recentOpIds"] as? List<*>).orEmpty().contains(opId)) return OpResult(doc, false, duplicate = true)
        }
        val normalized = deepCopy(op) as Map<String, Any?>
        val actor = normalized["actor"] as String
        val at = normalized["at"] as Long
        val next = deepCopy(doc) as MutableMap<String, Any?>
        val outcome = handle(next, normalized, actor, at)
        if (!outcome.changed) return OpResult(doc, false, outcome.id, outcome.expired, outcome.existed)
        reconcile(next, actor, at)
        if (opId != null) {
            val recent = ArrayList<Any?>((doc["recentOpIds"] as? List<*>).orEmpty())
            recent += opId
            while (recent.size > MAX_RECENT_OPS) recent.removeAt(0)
            next["recentOpIds"] = recent
        }
        next["revision"] = (doc["revision"] as Long) + 1
        next["updatedAt"] = at
        next["updatedBy"] = actor
        next.remove("integrity")
        validateDocument(next)
        return OpResult(next, true, outcome.id, outcome.expired, outcome.existed)
    }

    data class OpReport(val index: Int, val ok: Boolean, val code: String? = null, val message: String? = null,
                        val id: String? = null, val expired: List<String> = emptyList(), val duplicate: Boolean = false,
                        val changed: Boolean = false)

    /** Apply several operations; every rejection is reported, never dropped silently. */
    fun applyOps(doc: Map<String, Any?>, ops: List<Map<String, Any?>>, stopOnError: Boolean = true): Pair<Map<String, Any?>, List<OpReport>> {
        var current = doc
        val reports = ArrayList<OpReport>()
        ops.forEachIndexed { index, op ->
            try {
                val r = applyOp(current, op)
                current = r.doc
                reports += OpReport(index, true, id = r.id, expired = r.expired, duplicate = r.duplicate, changed = r.changed)
            } catch (e: BacklogException) {
                reports += OpReport(index, false, e.code, e.detail)
                if (stopOnError) fail(e.code, "operation $index (${op["op"]}): ${e.detail}")
            }
        }
        return current to reports
    }

    fun nextEligible(doc: Map<String, Any?>, kinds: List<String>? = null): Map<String, Any?>? {
        @Suppress("UNCHECKED_CAST") val d = doc as MutableMap<String, Any?>
        return tasksOf(d).filter { eligible(d, it) && (kinds == null || it["kind"] in kinds) }.sortedWith(priorityOrder).firstOrNull()
    }

    private val random = SecureRandom()

    fun newToken(): String = ByteArray(16).also(random::nextBytes).joinToString("") { "%02x".format(it) }

    // ------------------------------------------------------------------
    // Read-only typed view for the Android UI
    // ------------------------------------------------------------------
    data class TaskView(
        val id: String, val title: String, val kind: String, val source: String, val state: String, val priority: Long,
        val dependsOn: List<String>, val attempts: Long, val maxAttempts: Long, val errorCode: String?, val errorMessage: String?,
        val evidenceCount: Int, val claimOwner: String?, val leaseUntil: Long?, val lastReason: String?
    ) {
        val terminal: Boolean get() = state in TERMINAL
    }

    fun views(doc: Map<String, Any?>): List<TaskView> = (doc["tasks"] as List<*>).map { raw ->
        val t = obj(raw)!!
        val error = obj(t["error"])
        val claim = obj(t["claim"])
        TaskView(t["id"] as String, t["title"] as String, t["kind"] as String, t["source"] as String, t["state"] as String,
            t["priority"] as Long, (t["dependsOn"] as List<*>).map { it as String }, t["attempts"] as Long, t["maxAttempts"] as Long,
            error?.get("code") as String?, error?.get("message") as String?, (t["evidence"] as List<*>).size,
            claim?.get("owner") as String?, claim?.get("leaseUntil") as Long?,
            (t["history"] as List<*>).lastOrNull()?.let { obj(it)!!["reason"] as String })
    }
}
