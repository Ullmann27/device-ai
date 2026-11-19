package at.heinz.deviceai.cognitive

import at.heinz.deviceai.localmodel.ClaudeModel
import at.heinz.deviceai.localmodel.ImportedLocalModel
import at.heinz.deviceai.localmodel.LocalModelState

/**
 * Owner order 19.11.2026: „Und wenn Claude antwortet, möchte ich nicht stehen haben Claude, sondern Continuum im
 * Chat.“ Who is talking in the conversation is always Continuum – with the model on the device and with the cloud
 * brain. Which model does the thinking is a fact the owner can ask for, and it is answered truthfully: this object
 * words that answer for the fixed replies of the app („Welches Modell bist du?“, „Wer bist du?“). Nothing here ever
 * denies the cloud, and nothing claims the on-device model while the cloud brain answers.
 */
internal object OwnerBrainIdentity {
    /** „Welches Modell bist du?“ while the cloud brain answers. */
    fun cloud(assistantName: String, modelLabel: String, modelId: String): String =
        "Ich bin $assistantName. Gerade antworte ich über die Cloud: Dahinter arbeitet das Modell $modelLabel von Anthropic " +
            "($modelId). Das Sprachmodell auf dem Gerät ist daran nicht beteiligt; unter „Modelle → Gehirn“ schaltest du um."

    /**
     * „Wer bist du?“ – the app's self-description for whichever brain runs. With the on-device model it is exactly
     * [LocalAssistantIdentity.selfDescription].
     */
    fun selfDescription(chosenName: String?, cloudModel: ClaudeModel?, localModel: ImportedLocalModel?,
                        state: LocalModelState?): String {
        if (cloudModel == null) return LocalAssistantIdentity.selfDescription(chosenName, localModel, state)
        return "Ich bin ${LocalAssistantIdentity.safeName(chosenName)}, dein Assistent in Device AI Continuum. " +
            "Gerade antworte ich über die Cloud; dahinter arbeitet das Modell ${cloudModel.label} von Anthropic. " +
            "Das Sprachmodell auf dem Gerät ist daran nicht beteiligt. " +
            "App-Identität und Sprachmodell sind getrennt; ein Wechsel des Gehirns ändert nicht meinen Namen."
    }

    /** The line of the capability overview while the cloud brain answers: no model of the device is named as loaded. */
    const val CAPABILITY_LINE = "Gehirn: Cloud. Ich antworte gerade über die Cloud; das Sprachmodell auf dem Gerät ist daran " +
        "nicht beteiligt. Die Antwortqualität ist damit nicht geprüft."
}
