package at.heinz.deviceai.learning.context

/**
 * Deterministic role/centering resolver over externally supplied canonical entities and relation edges.
 * It stores no personal facts and never infers a relation that is absent from [RelationEdge].
 */
internal object EntityRoleResolutionEngine {
    enum class Gender { MASCULINE, FEMININE, NEUTER, PLURAL, UNKNOWN }
    enum class Role { SUBJECT, OBJECT, POSSESSOR }

    data class Entity(
        val id: String,
        val canonicalPath: String,
        val label: String,
        val gender: Gender
    ) {
        init {
            require(id.matches(Regex("[A-Za-z0-9._:-]{1,96}")))
            require(canonicalPath.matches(Regex("[A-Za-z0-9._:\\[\\]-]{1,160}")))
            require(label.isNotBlank() && label.length <= 120)
        }
    }

    data class RelationEdge(
        val fromId: String,
        val relation: String,
        val toId: String,
        val sourceIds: Set<String>
    ) {
        init {
            require(fromId.isNotBlank() && toId.isNotBlank() && fromId != toId)
            require(relation.matches(Regex("[A-Za-z0-9._:-]{1,80}")))
            require(sourceIds.isNotEmpty() && sourceIds.none(String::isBlank))
        }
    }

    data class CenteringFrame(
        val preferredCenterId: String?,
        val backwardCenterId: String?,
        val forwardCandidates: List<Pair<String, Role>>
    )

    sealed interface Resolution {
        data class Resolved(val entity: Entity, val sourceIds: Set<String>): Resolution
        data class Ambiguous(val entityIds: List<String>): Resolution
        data object NoEvidence: Resolution
    }

    fun advanceFrame(
        subjectId: String,
        mentioned: List<Pair<String, Role>>
    ): CenteringFrame {
        val ranked = buildList {
            add(subjectId to Role.SUBJECT)
            addAll(mentioned.filter { it.first != subjectId })
        }.distinctBy { it.first }
        return CenteringFrame(subjectId, subjectId, ranked.take(16))
    }

    fun resolvePersonalPronoun(
        pronoun: String,
        frame: CenteringFrame,
        entities: Map<String, Entity>
    ): Resolution {
        val wanted = when (normalizePronoun(pronoun)) {
            "er" -> setOf(Gender.MASCULINE)
            "sie" -> setOf(Gender.FEMININE, Gender.PLURAL)
            "es" -> setOf(Gender.NEUTER)
            else -> return Resolution.NoEvidence
        }
        val ordered = buildList {
            frame.preferredCenterId?.let(::add)
            frame.forwardCandidates.forEach { add(it.first) }
            frame.backwardCenterId?.let(::add)
        }.distinct()
        val candidates = ordered.mapNotNull(entities::get).filter { it.gender in wanted }
        if (candidates.isEmpty()) return Resolution.NoEvidence
        val preferred = frame.preferredCenterId?.let(entities::get)
        if (preferred != null && preferred.gender in wanted)
            return Resolution.Resolved(preferred, emptySet())
        return if (candidates.size == 1) Resolution.Resolved(candidates.single(), emptySet())
        else Resolution.Ambiguous(candidates.map { it.id })
    }

    fun resolvePossessiveRelation(
        possessive: String,
        relation: String,
        frame: CenteringFrame,
        ownerId: String,
        assistantId: String,
        entities: Map<String, Entity>,
        edges: Collection<RelationEdge>
    ): Resolution {
        val anchor = when (normalizePossessive(possessive)) {
            "mein" -> entities[ownerId]?.let { Resolution.Resolved(it, emptySet()) } ?: Resolution.NoEvidence
            "dein" -> entities[assistantId]?.let { Resolution.Resolved(it, emptySet()) } ?: Resolution.NoEvidence
            "sein" -> resolveAnchorByGender(frame, entities, setOf(Gender.MASCULINE, Gender.NEUTER))
            "ihr" -> resolveAnchorByGender(frame, entities, setOf(Gender.FEMININE, Gender.PLURAL))
            else -> Resolution.NoEvidence
        }
        val resolvedAnchor = anchor as? Resolution.Resolved ?: return anchor
        val relationKey = relation.trim().uppercase()
        val matching = edges.filter {
            it.fromId == resolvedAnchor.entity.id && it.relation.uppercase() == relationKey
        }
        if (matching.isEmpty()) return Resolution.NoEvidence
        val targets = matching.mapNotNull { edge -> entities[edge.toId]?.let { it to edge.sourceIds } }
            .distinctBy { it.first.id }
        if (targets.isEmpty()) return Resolution.NoEvidence
        if (targets.size > 1) return Resolution.Ambiguous(targets.map { it.first.id })
        return Resolution.Resolved(targets.single().first, targets.single().second)
    }

    private fun resolveAnchorByGender(
        frame: CenteringFrame,
        entities: Map<String, Entity>,
        genders: Set<Gender>
    ): Resolution {
        val ordered = buildList {
            frame.preferredCenterId?.let(::add)
            frame.forwardCandidates.forEach { add(it.first) }
            frame.backwardCenterId?.let(::add)
        }.distinct().mapNotNull(entities::get).filter { it.gender in genders }
        if (ordered.isEmpty()) return Resolution.NoEvidence
        val preferred = frame.preferredCenterId?.let(entities::get)
        if (preferred != null && preferred.gender in genders)
            return Resolution.Resolved(preferred, emptySet())
        return if (ordered.size == 1) Resolution.Resolved(ordered.single(), emptySet())
        else Resolution.Ambiguous(ordered.map { it.id })
    }

    private fun normalizePronoun(value: String): String =
        value.lowercase().trim().trim(',', '.', '!', '?')

    private fun normalizePossessive(value: String): String {
        val word = normalizePronoun(value)
        return when {
            word.startsWith("mein") -> "mein"
            word.startsWith("dein") -> "dein"
            word.startsWith("sein") -> "sein"
            word.startsWith("ihr") -> "ihr"
            else -> word
        }
    }
}
