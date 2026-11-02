package at.heinz.deviceai

/**
 * One explicit, allowlisted Android destination for the mobile builder UI.
 *
 * Preview variants contain the Builder Activity in their own APK. The regular
 * Device AI package opens the installed on-device Preview instead, without
 * importing its code, requesting new permissions or exposing arbitrary intents.
 */
internal object ContinuumMobileBuilderRoute {
    const val PREVIEW_PACKAGE = "at.heinz.deviceai.preview"
    const val MENTOR_PREVIEW_PACKAGE = "at.heinz.deviceai.mentorpaired"
    const val BUILDER_ACTIVITY = "at.heinz.deviceai.ContinuumMobileBuilderActivity"

    data class Destination(
        val packageName: String,
        val className: String,
        val externalPackage: Boolean
    )

    fun destination(currentApplicationId: String): Destination {
        val internal = currentApplicationId == PREVIEW_PACKAGE ||
            currentApplicationId == MENTOR_PREVIEW_PACKAGE
        return Destination(
            packageName = if (internal) currentApplicationId else PREVIEW_PACKAGE,
            className = BUILDER_ACTIVITY,
            externalPackage = !internal
        )
    }
}
