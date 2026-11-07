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
            .distinctBy { Triple(it.fromEntityId, it.relation.uppercase(), it.toEntityId) }
            .map {
                EntityRoleResolutionEngine.RelationEdge(
                    fromId = it.fromEntityId,
                    relation = it.relation.uppercase(),
                    toId = it.toEntityId,
                    sourceIds = it.sourceIds.toSet()
                )
            }
            .take(256)
            .toList()
}
