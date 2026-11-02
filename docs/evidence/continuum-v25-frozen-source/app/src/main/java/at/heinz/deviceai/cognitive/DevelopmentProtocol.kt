package at.heinz.deviceai.cognitive

class MalformedDevelopmentOutput(message: String) : Exception(message)
class DevelopmentPolicyViolation(message: String) : Exception(message)

/** Minimal strict JSON reader: objects, arrays and strings only; duplicate keys and numbers/literals are rejected. */
internal class StrictJson(private val text: String) {
    private var pos = 0

    fun parse(): Any {
        skipWs()
        val value = value(0)
        skipWs()
        if (pos != text.length) fail("Trailing content after JSON value.")
        return value
    }

    private fun fail(message: String): Nothing = throw MalformedDevelopmentOutput("$message (offset $pos)")

    private fun skipWs() { while (pos < text.length && text[pos] in " \t\r\n") pos++ }

    private fun value(depth: Int): Any {
        if (depth > MAX_DEPTH) fail("JSON nesting too deep.")
        if (pos >= text.length) fail("Unexpected end of JSON.")
        return when (text[pos]) {
            '{' -> obj(depth)
            '[' -> arr(depth)
            '"' -> str()
            else -> fail("Only objects, arrays and strings are allowed.")
        }
    }

    private fun obj(depth: Int): Map<String, Any> {
        pos++
        val map = LinkedHashMap<String, Any>()
        skipWs()
        if (peek('}')) { pos++; return map }
        while (true) {
            skipWs()
            if (!peek('"')) fail("Expected object key.")
            val key = str()
            if (map.size >= MAX_MEMBERS) fail("Too many object members.")
            if (map.containsKey(key)) fail("Duplicate key '$key'.")
            skipWs()
            if (!peek(':')) fail("Expected ':'.")
            pos++
            skipWs()
            map[key] = value(depth + 1)
            skipWs()
            if (peek(',')) { pos++; continue }
            if (peek('}')) { pos++; return map }
            fail("Expected ',' or '}'.")
        }
    }

    private fun arr(depth: Int): List<Any> {
        pos++
        val list = ArrayList<Any>()
        skipWs()
        if (peek(']')) { pos++; return list }
        while (true) {
            skipWs()
            if (list.size >= MAX_ELEMENTS) fail("Too many array elements.")
            list += value(depth + 1)
            skipWs()
            if (peek(',')) { pos++; continue }
            if (peek(']')) { pos++; return list }
            fail("Expected ',' or ']'.")
        }
    }

    private fun str(): String {
        pos++
        val sb = StringBuilder()
        while (true) {
            if (pos >= text.length) fail("Unterminated string.")
            val c = text[pos++]
            when {
                c == '"' -> return sb.toString()
                c == '\\' -> {
                    if (pos >= text.length) fail("Bad escape.")
                    when (val e = text[pos++]) {
                        '"', '\\', '/' -> sb.append(e)
                        'b' -> sb.append('\b')
                        'f' -> sb.append('\u000c')
                        'n' -> sb.append('\n')
                        'r' -> sb.append('\r')
                        't' -> sb.append('\t')
                        'u' -> {
                            if (pos + 4 > text.length) fail("Bad unicode escape.")
                            val hex = text.substring(pos, pos + 4)
                            if (!hex.all { it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F' }) fail("Bad unicode escape.")
                            sb.append(hex.toInt(16).toChar())
                            pos += 4
                        }
                        else -> fail("Bad escape.")
                    }
                }
                c.code < 0x20 -> fail("Raw control character in string.")
                else -> sb.append(c)
            }
        }
    }

    private fun peek(c: Char) = pos < text.length && text[pos] == c

    private companion object {
        const val MAX_DEPTH = 6
        const val MAX_MEMBERS = 16
        const val MAX_ELEMENTS = 16
    }
}

/** Deterministic, bounded parsing of worker output. No leniency beyond one optional markdown fence. */
object DevelopmentOutputParser {
    const val MAX_EDITS = 4
    const val MAX_NOTES = 8
    const val MAX_FINDINGS = 8
    const val MAX_TESTS = 8

    fun parseDeveloper(raw: String, maxBytes: Int, task: DevelopmentTask): DeveloperResult {
        val o = root(raw, maxBytes, setOf("summary", "edits", "notes"), setOf("summary", "edits"))
        val edits = list(o, "edits", MAX_EDITS).map { e ->
            val m = obj(e, setOf("path", "expectedBaseSha256", "replacement", "rationale"),
                setOf("path", "expectedBaseSha256", "replacement"))
            ProposedEdit(
                path = text(m, "path", 200),
                expectedBaseSha256 = text(m, "expectedBaseSha256", 64),
                replacement = text(m, "replacement", maxBytes, allowEmpty = true),
                rationale = if (m.containsKey("rationale")) text(m, "rationale", 400, allowEmpty = true) else ""
            )
        }
        val notes = if (o.containsKey("notes")) list(o, "notes", MAX_NOTES).map { str(it, "notes", 300) } else emptyList()
        validateEdits(edits, task)
        return DeveloperResult(text(o, "summary", 500), edits, notes)
    }

    fun validateEdits(edits: List<ProposedEdit>, task: DevelopmentTask) {
        val byPath = task.sources.associateBy { it.path }
        val seen = HashSet<String>()
        for (edit in edits) {
            DevelopmentPathPolicy.validate(edit.path)?.let { throw DevelopmentPolicyViolation("${edit.path.take(80)}: $it") }
            if (!seen.add(edit.path)) throw DevelopmentPolicyViolation("Duplicate edit path ${edit.path}.")
            val base = byPath[edit.path] ?: throw DevelopmentPolicyViolation("Path ${edit.path} was not selected by the user.")
            if (edit.expectedBaseSha256 != base.sha256) throw DevelopmentPolicyViolation("Stale base hash for ${edit.path}.")
            if (edit.replacement.any { it == '\u0000' }) throw DevelopmentPolicyViolation("NUL in replacement for ${edit.path}.")
            if (edit.replacement == base.content) throw DevelopmentPolicyViolation("No-op edit for ${edit.path}.")
        }
    }

    fun parseCritic(raw: String, maxBytes: Int): CriticResult {
        val o = root(raw, maxBytes, setOf("verdict", "findings"), setOf("verdict", "findings"))
        val findings = list(o, "findings", MAX_FINDINGS).map {
            val m = obj(it, setOf("severity", "message"), setOf("severity", "message"))
            CriticFinding(enumOf<FindingSeverity>(text(m, "severity", 16)), text(m, "message", 400))
        }
        return CriticResult(enumOf<CriticVerdict>(text(o, "verdict", 16)), findings)
    }

    fun parseTests(raw: String, maxBytes: Int): TestDesignResult {
        val o = root(raw, maxBytes, setOf("tests"), setOf("tests"))
        return TestDesignResult(list(o, "tests", MAX_TESTS).map {
            val m = obj(it, setOf("name", "description"), setOf("name", "description"))
            SuggestedTest(text(m, "name", 100), text(m, "description", 400))
        })
    }

    fun parseJudge(raw: String, maxBytes: Int): JudgeResult {
        val o = root(raw, maxBytes, setOf("decision", "rationale"), setOf("decision", "rationale"))
        return JudgeResult(enumOf<JudgeDecision>(text(o, "decision", 16)), text(o, "rationale", 500))
    }

    private inline fun <reified E : Enum<E>> enumOf(value: String): E =
        enumValues<E>().firstOrNull { it.name == value } ?: throw MalformedDevelopmentOutput("Unknown value '${value.take(32)}'.")

    private fun root(raw: String, maxBytes: Int, allowed: Set<String>, required: Set<String>): Map<String, Any> {
        if (utf8Bytes(raw) > maxBytes) throw MalformedDevelopmentOutput("Output exceeds $maxBytes bytes.")
        var body = raw.trim()
        if (body.startsWith("```")) {
            val firstNewline = body.indexOf('\n')
            if (firstNewline < 0 || !body.endsWith("```") || body.length < firstNewline + 4) {
                throw MalformedDevelopmentOutput("Malformed code fence.")
            }
            val tag = body.substring(3, firstNewline).trim()
            if (tag.isNotEmpty() && tag != "json") throw MalformedDevelopmentOutput("Unexpected fence language.")
            body = body.substring(firstNewline + 1, body.length - 3).trim()
        }
        return obj(StrictJson(body).parse(), allowed, required)
    }

    @Suppress("UNCHECKED_CAST")
    private fun obj(value: Any, allowed: Set<String>, required: Set<String>): Map<String, Any> {
        val map = value as? Map<String, Any> ?: throw MalformedDevelopmentOutput("Expected JSON object.")
        map.keys.firstOrNull { it !in allowed }?.let { throw MalformedDevelopmentOutput("Unknown field '${it.take(32)}'.") }
        required.firstOrNull { it !in map }?.let { throw MalformedDevelopmentOutput("Missing field '$it'.") }
        return map
    }

    private fun list(m: Map<String, Any>, key: String, max: Int): List<Any> {
        val l = m[key] as? List<*> ?: throw MalformedDevelopmentOutput("Field '$key' must be an array.")
        if (l.size > max) throw MalformedDevelopmentOutput("Field '$key' has more than $max entries.")
        return l.map { it!! }
    }

    private fun text(m: Map<String, Any>, key: String, maxBytes: Int, allowEmpty: Boolean = false): String =
        str(m[key] ?: throw MalformedDevelopmentOutput("Missing field '$key'."), key, maxBytes, allowEmpty)

    private fun str(v: Any, key: String, maxBytes: Int, allowEmpty: Boolean = false): String {
        val s = v as? String ?: throw MalformedDevelopmentOutput("Field '$key' must be a string.")
        if (!allowEmpty && s.isBlank()) throw MalformedDevelopmentOutput("Field '$key' is empty.")
        if (utf8Bytes(s) > maxBytes) throw MalformedDevelopmentOutput("Field '$key' exceeds $maxBytes bytes.")
        if (s.any { it.code < 0x20 && it != '\n' && it != '\t' && it != '\r' }) {
            throw MalformedDevelopmentOutput("Field '$key' contains control characters.")
        }
        return s
    }
}

/** Escapes text as a JSON string literal so untrusted content cannot break out of its data block. */
internal fun jsonString(value: String): String = buildString {
    append('"')
    for (c in value) when {
        c == '"' -> append("\\\"")
        c == '\\' -> append("\\\\")
        c == '\n' -> append("\\n")
        c == '\r' -> append("\\r")
        c == '\t' -> append("\\t")
        c.code < 0x20 -> append("\\u%04x".format(c.code))
        else -> append(c)
    }
    append('"')
}

object DevelopmentPatchPreview {
    /** Deterministic read-only preview: one hunk per file with common prefix/suffix trimmed. */
    fun render(task: DevelopmentTask, edits: List<ProposedEdit>): String = buildString {
        appendLine("# REVIEW ONLY — NOT APPLIED. Nothing has been written, compiled or tested.")
        if (edits.isEmpty()) appendLine("# No file edits proposed.")
        val byPath = task.sources.associateBy { it.path }
        for (edit in edits) {
            val old = byPath.getValue(edit.path).content.split("\n")
            val new = edit.replacement.split("\n")
            var head = 0
            while (head < old.size && head < new.size && old[head] == new[head]) head++
            var tail = 0
            while (tail < old.size - head && tail < new.size - head &&
                old[old.size - 1 - tail] == new[new.size - 1 - tail]) tail++
            appendLine("--- a/${edit.path} (sha256 ${edit.expectedBaseSha256})")
            appendLine("+++ b/${edit.path}")
            appendLine("@@ -${head + 1},${old.size - head - tail} +${head + 1},${new.size - head - tail} @@")
            for (i in head until old.size - tail) appendLine("-${old[i]}")
            for (i in head until new.size - tail) appendLine("+${new[i]}")
        }
    }
}
