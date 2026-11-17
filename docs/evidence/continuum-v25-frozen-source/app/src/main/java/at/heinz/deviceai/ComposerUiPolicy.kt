package at.heinz.deviceai

enum class DraftSubmission { READY, NEEDS_LOCAL_MODEL, NEEDS_CLOUD_SETUP }

data class ComposerViewport(val top: Int, val bottom: Int, val compactDashboard: Boolean)

object ComposerUiPolicy {
    fun viewport(systemTop: Int, navigationBottom: Int, imeBottom: Int,
                 imeVisible: Boolean, wide: Boolean): ComposerViewport {
        require(systemTop >= 0 && navigationBottom >= 0 && imeBottom >= 0)
        val keyboardOpen = imeVisible && imeBottom > navigationBottom
        return ComposerViewport(
            top = systemTop,
            bottom = if (keyboardOpen) maxOf(navigationBottom, imeBottom) else navigationBottom,
            compactDashboard = keyboardOpen && !wide
        )
    }

    fun submission(route: AssistantRoute, hasLoadedModel: Boolean,
                   cloudConfigured: Boolean, researchSelected: Boolean): DraftSubmission = when {
        researchSelected -> DraftSubmission.READY
        route == AssistantRoute.LOCAL_MODEL && hasLoadedModel -> DraftSubmission.READY
        route == AssistantRoute.CLOUD && cloudConfigured -> DraftSubmission.READY
        route == AssistantRoute.CLOUD -> DraftSubmission.NEEDS_CLOUD_SETUP
        else -> DraftSubmission.NEEDS_LOCAL_MODEL
    }
}
