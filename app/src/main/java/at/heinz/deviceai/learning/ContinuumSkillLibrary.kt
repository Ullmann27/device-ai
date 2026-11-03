package at.heinz.deviceai.learning

import at.heinz.deviceai.cognitive.CrystallizedSkillCandidate
import java.io.File
import java.util.Locale

enum class SkillStatus { DRAFT, APPROVED, TAMPERED }
/** The owner's explicit "yes" for one named skill (e.g. "Ja, Skill wetterbericht freigeben"). */
data class OwnerSkillApproval(val ownerUtterance:String, val atMillis:Long)
data class SkillSummary(val name:String, val whenToUse:String, val status:SkillStatus, val sourceCandidateId:String)
data class LoadedSkill(val name:String, val whenToUse:String, val instructions:String, val sourceCandidateId:String)

/**
 * Chief pattern point 5 — skill library. One text file per skill (`<name>.skill.md`: name, when-to-use, source
 * candidate, status, approval hash, then the instructions). Sources are ONLY ExperienceCompiler
 * [CrystallizedSkillCandidate]s eligible for owner review; a skill is DRAFT until the owner explicitly approves it
 * by name. The approval binds a SHA-256 of name+when+instructions: any later edit makes it TAMPERED (inactive)
 * until re-approved. [index] / [match] expose name + when-to-use only; instructions come via [load] on demand.
 * Nothing here executes a skill or calls tools. Integrity is local-file level (not a security boundary against
 * an attacker with file access); signing via IdentitySigner is a documented follow-up.
 */
class ContinuumSkillLibrary(private val directory:File) {
    private data class Parsed(val name:String, val whenToUse:String, val source:String, val status:SkillStatus,
                              val approvedHash:String?, val approvedAt:Long?, val instructions:String)

    @Synchronized
    fun propose(candidate:CrystallizedSkillCandidate, name:String, whenToUse:String, instructions:String):SkillSummary {
        require(candidate.eligibleForOwnerReview) { "candidate blocked: ${candidate.promotionBlockedReason}" }
        require(candidate.requiresOwnerApproval)
        requireName(name)
        require(whenToUse.trim().length in 3..300 && '\n' !in whenToUse)
        require(instructions.isNotBlank() && instructions.length <= MAX_INSTRUCTIONS && "\n---\n" !in "\n$instructions\n")
        val f = file(name)
        require(!f.exists() || parse(f)?.status != SkillStatus.APPROVED) { "approved skill exists; revoke first" }
        require(directory.listFiles { x -> x.name.endsWith(SUFFIX) }.orEmpty().size < MAX_SKILLS || f.exists())
        write(Parsed(name, whenToUse.trim(), candidate.candidateId, SkillStatus.DRAFT, null, null, instructions))
        return summary(parse(f)!!)
    }

    @Synchronized
    fun approve(name:String, approval:OwnerSkillApproval):SkillSummary {
        requireName(name)
        val u = approval.ownerUtterance.lowercase(Locale.GERMAN)
        require(Regex("\\b(ja|freigeben|genehmigt?|erlaubt?)\\b").containsMatchIn(u) && Regex("\\b${Regex.escape(name)}\\b").containsMatchIn(u)) {
            "owner approval must be explicit and name the skill"
        }
        val p = parse(file(name)) ?: error("unknown skill")
        write(p.copy(status = SkillStatus.APPROVED, approvedHash = hash(p), approvedAt = approval.atMillis))
        return summary(parse(file(name))!!)
    }

    @Synchronized
    fun index():List<SkillSummary> = directory.listFiles { x -> x.name.endsWith(SUFFIX) }.orEmpty()
        .sortedBy { it.name }.take(MAX_SKILLS).mapNotNull(::parse).map(::summary)

    @Synchronized
    fun load(name:String):LoadedSkill? {
        if (!NAME.matches(name)) return null
        val p = parse(file(name))?.takeIf { effective(it) == SkillStatus.APPROVED } ?: return null
        return LoadedSkill(p.name, p.whenToUse, p.instructions, p.source)
    }

    /** Approved skills whose when-to-use overlaps the request (keyword prefix match), best first. */
    fun match(request:String, limit:Int = 3):List<SkillSummary> {
        val q = words(request)
        if (q.isEmpty()) return emptyList()
        return index().filter { it.status == SkillStatus.APPROVED }
            .map { s -> s to words(s.whenToUse + " " + s.name).count { w -> q.any { it.take(5) == w.take(5) } } }
            .filter { it.second > 0 }.sortedByDescending { it.second }.take(limit.coerceIn(1, 10)).map { it.first }
    }

    private fun effective(p:Parsed) = when {
        p.status != SkillStatus.APPROVED -> p.status
        p.approvedHash == hash(p) -> SkillStatus.APPROVED
        else -> SkillStatus.TAMPERED
    }
    private fun summary(p:Parsed) = SkillSummary(p.name, p.whenToUse, effective(p), p.source)
    private fun file(name:String) = File(directory, name + SUFFIX)
    private fun requireName(name:String) = require(NAME.matches(name)) { "unsafe skill name" }
    private fun hash(p:Parsed) = ContinuumTemporalMemory.sha(p.name + "\u0000" + p.whenToUse + "\u0000" + p.source + "\u0000" + p.instructions)

    private fun write(p:Parsed) {
        directory.mkdirs()
        val text = "name: ${p.name}\nwhen: ${p.whenToUse}\nsource: ${p.source}\nstatus: ${p.status.name}\n" +
            "approved-sha256: ${p.approvedHash ?: "-"}\napproved-at: ${p.approvedAt ?: "-"}\n---\n${p.instructions}"
        val tmp = File(directory, ".${p.name}.tmp")
        tmp.writeText(text, Charsets.UTF_8)
        check(tmp.renameTo(file(p.name)) || run { file(p.name).delete(); tmp.renameTo(file(p.name)) })
    }

    private fun parse(f:File):Parsed? = runCatching {
        if (!f.isFile || f.length() > MAX_INSTRUCTIONS + 2048) return null
        val text = f.readText(Charsets.UTF_8)
        val cut = text.indexOf("\n---\n"); if (cut < 0) return null
        val head = text.substring(0, cut).lines().associate { it.substringBefore(": ") to it.substringAfter(": ", "") }
        val name = head["name"]!!; if (!NAME.matches(name) || f.name != name + SUFFIX) return null
        Parsed(name, head["when"]!!, head["source"]!!, SkillStatus.valueOf(head["status"]!!),
            head["approved-sha256"]?.takeUnless { it == "-" }, head["approved-at"]?.takeUnless { it == "-" }?.toLong(),
            text.substring(cut + 5))
    }.getOrNull()

    private companion object {
        const val SUFFIX = ".skill.md"
        const val MAX_SKILLS = 256
        const val MAX_INSTRUCTIONS = 8192
        val NAME = Regex("[a-z0-9][a-z0-9-]{1,63}")
        fun words(t:String) = t.lowercase(Locale.GERMAN).split(Regex("[^\\p{L}\\p{N}]+")).filter { it.length >= 4 }.toSet()
    }
}
