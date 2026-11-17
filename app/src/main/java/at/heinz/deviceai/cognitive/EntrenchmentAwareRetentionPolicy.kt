package at.heinz.deviceai.cognitive

/** Proposal-only retention tier; this class never deletes or mutates memory. */
internal object EntrenchmentAwareRetentionPolicy {
    enum class Stage {
        HOT_ACTIVE,
        WARM_INDEXED,
        COLD_ARCHIVE,
        PURGE_ELIGIBLE
    }

    data class Metrics(
        val protectedCore: Boolean,
        val explicitRememberCommand: Boolean,
        val dependentCount: Int,
        val accessCount: Int,
        val daysSinceLastAccess: Float,
        val supersededOrRevoked: Boolean,
        val unresolvedContradiction: Boolean
    ) {
        init {
            require(dependentCount >= 0 && accessCount >= 0)
            require(daysSinceLastAccess.isFinite() && daysSinceLastAccess >= 0f)
        }
    }

    fun stage(m: Metrics): Stage {
        // Never auto-evict anchors, explicit owner memories, dependencies or unresolved contradictions.
        if (m.protectedCore || m.explicitRememberCommand || m.dependentCount > 0 || m.unresolvedContradiction)
            return Stage.HOT_ACTIVE

        // Historical corrections stay retrievable for "what was known then?" even when no longer hot.
        if (m.supersededOrRevoked)
            return if (m.daysSinceLastAccess > 90f) Stage.COLD_ARCHIVE else Stage.WARM_INDEXED

        val activation = (m.accessCount + 1f) / (1f + .15f * m.daysSinceLastAccess)
        return when {
            activation >= .50f -> Stage.HOT_ACTIVE
            activation >= .15f -> Stage.WARM_INDEXED
            m.daysSinceLastAccess < 180f -> Stage.COLD_ARCHIVE
            else -> Stage.PURGE_ELIGIBLE
        }
    }
}
