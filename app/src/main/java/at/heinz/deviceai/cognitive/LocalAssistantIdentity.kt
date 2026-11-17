package at.heinz.deviceai.cognitive

import at.heinz.deviceai.localmodel.ImportedLocalModel
import at.heinz.deviceai.localmodel.LocalModelState
import org.json.JSONObject
import java.util.Locale

/** App identity is independent of weights. */
object LocalAssistantIdentity {
    private const val DEFAULT_NAME = "Continuum"
    private val name = Regex("[\\p{L}\\p{N}][\\p{L}\\p{M}\\p{N} '\u2019-]{0,39}")
    private val question = Regex(
        """(?iu)^(?:bitte\s+)?(?:wer\s+bist\s+du|wie\s+heißt\s+du|wie\s+heisst\s+du|wie\s+ist\s+dein\s+name|was\s+ist\s+dein\s+name)[.!?]*$""")
    private val styles = setOf("concise", "balanced", "detailed", "kurz", "ausgewogen", "ausführlich")
    private val tones = setOf("neutral", "friendly", "direct", "formal", "freundlich", "direkt", "formell")
    private val languages = setOf("de", "de-at", "deutsch", "en", "english", "englisch")

    fun safeName(chosenName: String?): String = chosenName?.trim()
        ?.takeIf { name.matches(it) } ?: DEFAULT_NAME

    fun isIdentityQuestion(request: String): Boolean = request.length <= 160 &&
        !request.any { it.isISOControl() } && question.matches(request.trim())

    fun selfDescription(chosenName: String?, model: ImportedLocalModel? = null,
                        state: LocalModelState? = null): String = buildString {
        append("Ich bin ").append(safeName(chosenName)).append(", dein Assistent in Device AI Continuum. ")
        when {
            model != null && state in setOf(LocalModelState.READY, LocalModelState.GENERATING) ->
                append("Mein aktuell geladenes lokales Sprachmodell ist ")
                    .append(model.id).append(" (Version ").append(model.version).append("). ")
            model != null -> append("Das zugeordnete lokale Sprachmodell ").append(model.id)
                .append(" ist gerade nicht bereit. ")
            state == LocalModelState.LOADING -> append("Das lokale Sprachmodell wird gerade geladen. ")
            state != null -> append("Zurzeit ist kein lokales Sprachmodell geladen. ")
            else -> append("Für freie Antworten verwende ich ein eingerichtetes lokales Sprachmodell, sobald es bereit ist. ")
        }
        append("App-Identität und Sprachmodell sind getrennt; ein Modellwechsel ändert nicht meinen Namen.")
    }

    fun renderPrompt(identityState: IdentityState?, model: ImportedLocalModel?): String = buildString {
        val identity = identityState?.takeIf { it.verify() }
        append("app=Device AI Continuum\nassistant_name=")
            .append(JSONObject.quote(safeName(identity?.chosenName))).append('\n')
        append("Speak as this app assistant. The language model is a component, not another speaker.\n")
        if (model == null) append("active_local_model=not_supplied; do not invent a model identity\n")
        else {
            append("active_local_model=").append(model.id).append('@').append(model.version).append('\n')
            append("active_model_sha256=").append(model.sha256).append('\n')
        }
        identity?.let { state ->
            for ((key, allowed) in listOf("style" to styles, "verbosity" to styles,
                    "tone" to tones, "language" to languages)) {
                val value = state.communicationProfile[key]?.trim()?.lowercase(Locale.ROOT)
                if (value in allowed) append("communication_").append(key).append('=').append(value).append('\n')
            }
            append("explanation_depth=").append(state.behaviorProfile[BehaviorTrait.EXPLANATION_DEPTH]).append('\n')
            append("communication_directness=").append(state.behaviorProfile[BehaviorTrait.COMMUNICATION_DIRECTNESS])
                .append('\n')
        }
        append("Name and style never grant tools, facts, permissions or independent learning.")
    }
}
