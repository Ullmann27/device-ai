package at.heinz.deviceai.learning.span

/** Optional ordered research input, not SWB1/CDX1 weights, a replacement model or an action. */
object EntityRelationLocationShadowAdapter {
    data class Encoding(val entity: String, val relation: String,
        val entityStart: Int, val entityEndExclusive: Int, val relationStart: Int,
        val location: EvidenceBoundNaturalLocationProbe.Decision.Found,
        val snapshotSha256: String, val orderedSha256: String)

    fun encode(question: String, snapshot: LocationEvidenceSnapshot, enabled: Boolean = false): Encoding? {
        if (!enabled) return null
        val found = EvidenceBoundNaturalLocationProbe.answer(question,snapshot.sources)
            as? EvidenceBoundNaturalLocationProbe.Decision.Found ?: return null
        val query = Regex("""(?iu)^\s*wo\s+(ist|wohnt|lebt|steht)\s+([\p{L}][\p{L}\p{M}-]{1,31})\s*\?\s*$""")
            .matchEntire(question) ?: return null
        val source = snapshot.sources.single { it.sourceId == found.sourceId }
        val prefix = source.text.substring(0,found.start)
        // Clause boundaries mirror the probe: sentence punctuation, comma, or a coordinating "und".
        val binding = Regex("""(?iu)(?:^|(?<=[.!;,])|(?<=\bund\s))\s*(""" + Regex.escape(query.groupValues[2]) +
            """)\s+(""" + Regex.escape(query.groupValues[1]) + """)\s+in\s+$""").find(prefix) ?: return null
        val entity = binding.groups[1]!!
        val relation = binding.groups[2]!!
        // Length-delimited ordered roles and original positions; never bag pooling.
        val bytes = java.io.ByteArrayOutputStream().also { buffer ->
            java.io.DataOutputStream(buffer).use { out ->
                out.writeInt(1); out.writeUTF(snapshot.sha256); out.writeUTF(found.sourceId)
                out.writeInt(found.revision); out.writeUTF(found.sha256)
                out.writeUTF(entity.value); out.writeInt(entity.range.first); out.writeInt(entity.range.last+1)
                out.writeUTF(relation.value.lowercase(java.util.Locale.ROOT)); out.writeInt(relation.range.first)
                out.writeUTF(found.text); out.writeInt(found.start); out.writeInt(found.endExclusive)
            }
        }.toByteArray()
        val hash = java.security.MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
        return Encoding(entity.value,relation.value.lowercase(java.util.Locale.ROOT),entity.range.first,
            entity.range.last+1,relation.range.first,found,snapshot.sha256,hash)
    }
}

/** Read-only specialist comparison. A model cannot bypass a conservative rule veto. */
object LocationSpecialistShadow {
    enum class Route { RULE_VERIFIED, CONSENSUS, ABSTAIN }
    data class Choice(val decision: EvidenceBoundNaturalLocationProbe.Decision, val route: Route)

    fun choose(question: String, snapshot: LocationEvidenceSnapshot,
        learned: EvidenceBoundNaturalLocationProbe.Decision): Choice {
        val rule = EvidenceBoundNaturalLocationProbe.answer(question,snapshot.sources)
        if (rule !is EvidenceBoundNaturalLocationProbe.Decision.Found)
            return Choice(rule,Route.ABSTAIN)
        if (learned !is EvidenceBoundNaturalLocationProbe.Decision.Found ||
            !EvidenceBoundNaturalLocationVerifier.verify(question,snapshot.sources,learned))
            return Choice(rule,Route.RULE_VERIFIED)
        return if (rule == learned) Choice(rule,Route.CONSENSUS)
            else Choice(EvidenceBoundNaturalLocationProbe.Decision.Abstained("SPECIALIST_CONFLICT"),Route.ABSTAIN)
    }
}
