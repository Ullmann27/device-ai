package at.heinz.deviceai.learning.context

/**
 * Read-only relation graph input for EntityRoleResolutionEngine.
 * Only explicit evidence-backed edges may enter. No language-model inference is accepted here.
 */
internal object EvidenceBackedRelationGraph {
    data class EvidenceEdge(
        val fromEntityId: String,
        val relation: String,
        val toEntityId: String,
        val sourceIds: Set<String>,
        val verified: Boolean
    ) {
        init {
            require(fromEntityId.isNotBlank() && toEntityId.isNotBlank() && fromEntityId != toEntityId)
            require(relation.matches(Regex("[A-Za-z0-9._:-]{1,80}")))
            require(sourceIds.isNotEmpty() && sourceIds.none(String::isBlank))
        }
    }

    fun admitted(edges: Collection<EvidenceEdge>): List<EntityRoleResolutionEngine.RelationEdge> =
        edges.asSequence()
            .filter { it.verified }
            .groupBy { Triple(it.fromEntityId, it.relation.uppercase(), it.toEntityId) }
            .entries
            .asSequence()
            .sortedWith(compareBy({ it.key.first }, { it.key.second }, { it.key.third }))
            .map { (key, group) ->
                EntityRoleResolutionEngine.RelationEdge(
                    fromId = key.first,
                    relation = key.second,
                    toId = key.third,
                    sourceIds = group.flatMap { it.sourceIds }.toSortedSet()
                )
            }
            .take(256)
            .toList()
}
