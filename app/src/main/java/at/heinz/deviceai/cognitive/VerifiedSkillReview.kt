package at.heinz.deviceai.cognitive

/**
 * Trusted independent verification boundary, supplied by application code that
 * checks test receipts or an authenticated owner decision outside the LLM.
 *
 * Do NOT implement with evaluation.success, sourceIds, a tool-output digest,
 * model feedback, or any other self-reported Experience field alone.
 */
fun interface IndependentExperienceVerifier {
    fun independentlyVerified(experience: Experience): Boolean
}

/**
 * Strictly opt-in, evidence-gated adapter to the existing ExperienceCompiler.
 *
 * It introduces no skill store, no automatic skill activation and no tool
 * authority. The resulting candidates still require owner approval.
 * The verifier defaults to denying every experience.
 */
class VerifiedSkillReview(
    private val compiler: ExperienceCompiler = ExperienceCompiler(),
    private val independentVerifier: IndependentExperienceVerifier =
        IndependentExperienceVerifier { false }
) {
    fun candidates(experiences: Collection<Experience>): List<CrystallizedSkillCandidate> {
        require(experiences.size <= 100_000)
        val unique = linkedMapOf<String, Experience>()
        for (experience in experiences) {
            val previous = unique.putIfAbsent(experience.id, experience)
            if (previous != null && previous != experience) {
                throw SecurityException("Conflicting experience identity; skill review blocked.")
            }
        }

        val accepted = ArrayList<Experience>()
        for (experience in unique.values) {
            val independentlyVerified = try {
                independentVerifier.independentlyVerified(experience)
            } catch (_: Exception) {
                // A broken or unreachable evidence source cannot authorize
                // promotion. Fail closed for this whole review batch.
                return emptyList()
            }
            if (independentlyVerified) accepted.add(experience)
        }

        return compiler.compile(accepted).also { candidates ->
            check(candidates.all { it.requiresOwnerApproval }) {
                "Skill review cannot bypass owner approval."
            }
        }
    }
}
