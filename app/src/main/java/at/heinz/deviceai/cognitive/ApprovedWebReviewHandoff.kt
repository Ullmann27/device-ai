package at.heinz.deviceai.cognitive

/**
 * Connects an authorized web research result to the existing owner-reviewed source inbox.
 * The dossier is only staged for review: no fact, memory or neural-weight promotion.
 */
class ApprovedWebReviewHandoff(
    private val session: ApprovedWebResearchSession,
    private val repository: ResearchDossierRepository
) {
    fun offer(dossier: ResearchDossier): Boolean {
        session.checkAuthorized()
        require(dossier.plan.question == session.question && dossier.plan.mode == session.mode) {
            "Recherche-Dossier stimmt nicht mit der Internetfreigabe überein"
        }
        if (WebLearningInbox.candidates(dossier).isEmpty()) return false
        session.checkAuthorized()
        repository.save(dossier)
        // Revoke a result that was saved while the owner changed the access setting.
        // The local review copy is not a memory promotion and still requires owner review.
        session.checkAuthorized()
        return true
    }
}
