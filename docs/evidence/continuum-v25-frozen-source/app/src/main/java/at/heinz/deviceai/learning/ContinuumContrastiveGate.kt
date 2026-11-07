package at.heinz.deviceai.learning

/** Evidence-only acceptance gate. It cannot save, import or promote a model. */
data class ContrastiveRetrievalEvidence(
    val modelSeed: Int, val domain: String, val drop: Double,
    val beforeMrr: Double, val afterMrr: Double
)
data class ContrastiveSkillEvidence(
    val modelSeed: Int, val task: String, val beforeLoss: Double, val afterLoss: Double,
    val beforeCorrect: Int, val afterCorrect: Int, val total: Int
)
data class ContrastiveGateDecision(
    val status: String, val reasons: List<String>, val automaticPromotion: Boolean = false
)
object ContinuumContrastiveGate {
    fun evaluate(
        retrieval: List<ContrastiveRetrievalEvidence>,
        protectedSkills: List<ContrastiveSkillEvidence>
    ): ContrastiveGateDecision {
        val reasons=linkedSetOf<String>()
        val seeds=retrieval.map { it.modelSeed }.toSet()
        val domains=retrieval.map { it.domain }.toSet()
        val drops=retrieval.map { it.drop }.toSet()
        val ids=retrieval.map { Triple(it.modelSeed,it.domain,it.drop) }
        if(seeds.size<3 || domains.size<3 || drops.size<2 ||
            retrieval.size!=seeds.size*domains.size*drops.size)
            reasons+="INSUFFICIENT_GENERALIZATION_COVERAGE"
        if(ids.toSet().size!=ids.size)reasons+="DUPLICATE_CASE"
        if(retrieval.any {
                it.domain.isBlank() || !it.drop.isFinite() || it.drop !in 0.0..0.95 ||
                !it.beforeMrr.isFinite() || !it.afterMrr.isFinite() ||
                it.beforeMrr<=0 || it.beforeMrr>1 || it.afterMrr<=0 || it.afterMrr>1
            })reasons+="INVALID_RETRIEVAL_DATA"

        val differences=retrieval.map { it.afterMrr-it.beforeMrr }
        if(differences.isEmpty() || differences.average()<0.03)
            reasons+="HOLDOUT_GAIN_INSUFFICIENT"
        if(differences.any { it < -0.01 })reasons+="HOLDOUT_REGRESSION"
        if(differences.count { it>=0.01 } < kotlin.math.ceil(differences.size*0.6).toInt())
            reasons+="HOLDOUT_GAIN_NOT_CONSISTENT"
        retrieval.groupBy { it.domain }.forEach { (domain,items) ->
            if(items.map { it.afterMrr-it.beforeMrr }.average()<0.01)
                reasons+="DOMAIN_GAIN_INSUFFICIENT:$domain"
        }
        if(protectedSkills.map { it.modelSeed to it.task }.toSet().size!=protectedSkills.size ||
            protectedSkills.size!=seeds.size*2 ||
            protectedSkills.map { it.modelSeed }.toSet()!=seeds ||
            seeds.any { seed -> protectedSkills.count { it.modelSeed==seed && it.task in setOf("intent","semantic") }!=2 })
            reasons+="INCOMPLETE_RETENTION_EVIDENCE"
        protectedSkills.forEach {
            if(it.task !in setOf("intent","semantic") || it.total<=0 ||
                it.beforeCorrect !in 0..it.total || it.afterCorrect !in 0..it.total ||
                !it.beforeLoss.isFinite() || !it.afterLoss.isFinite() ||
                it.beforeLoss<0 || it.afterLoss<0)
                reasons+="INVALID_SKILL_DATA"
            else {
                if(it.afterCorrect<it.beforeCorrect)reasons+="SKILL_ACCURACY_REGRESSION"
                if(it.afterLoss>it.beforeLoss+0.015)reasons+="SKILL_LOSS_REGRESSION"
            }
        }
        return ContrastiveGateDecision(
            if(reasons.isEmpty())"REVIEW_REQUIRED" else "REJECTED",reasons.toList()
        )
    }
}
