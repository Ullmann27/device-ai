package at.heinz.deviceai.cognitive

/**
 * Code55 field bug E (Fold7, Code54): every app reopen appended the generic first-start greeting below the restored
 * conversation, and a slow restore (2 s timeouts) fell back to an empty session or model context without a word.
 *
 * One rule for what a new AgentActivity instance shows in the central chat: the greeting only for a genuinely new,
 * empty conversation; nothing for a restored one; a short, honest notice when the restore did not complete. Pure
 * decision, no I/O; the notices are never persisted as conversation and never claim that data was lost or deleted.
 */
internal object ConversationStartupNotice {
    enum class ContextRestore { KEPT_IN_MEMORY, RESTORED, NOTHING_STORED, FAILED }

    const val GREETING = "Hallo! Ich bin Device AI. Mein eigener Startkern läuft ohne Modellimport und ohne Cloud. " +
        "Sprich oder schreibe mit mir. Mit „Merke dir: ...“ kannst du mein lokales Wissen ausdrücklich erweitern. " +
        "Mit „Was kannst du?“ erfährst du meine aktuellen Fähigkeiten."

    const val CONVERSATION_NOT_LOADED = "Hinweis: Das bisherige Gespräch konnte beim Öffnen nicht rechtzeitig geladen " +
        "werden. Ich mache vorläufig ohne den früheren Verlauf weiter; frühere Angaben stehen mir deshalb gerade nicht " +
        "zur Verfügung. Gespeicherte Gespräche wurden nicht gelöscht."

    const val CONTEXT_NOT_LOADED = "Hinweis: Der bisherige Verlauf wird angezeigt, aber mein Gesprächskontext dazu " +
        "konnte beim Öffnen nicht rechtzeitig geladen werden. Frühere Angaben aus diesem Gespräch stehen mir deshalb " +
        "gerade nicht sicher zur Verfügung. Gespeicherte Daten wurden nicht gelöscht."

    const val HISTORY_NOT_SHOWN = "Hinweis: Der bisherige Verlauf konnte beim Öffnen nicht rechtzeitig angezeigt " +
        "werden; mein Gesprächskontext dazu ist geladen. Gespeicherte Gespräche wurden nicht gelöscht."

    /**
     * [conversationLookupFailed]: the active conversation id could not be read; [restoredBubbles]: visible entries
     * re-rendered, null when loading them failed; [context]: outcome of the model/object-state context restore.
     * Returns the chat line to show, or null when the restored conversation simply continues.
     */
    fun message(conversationLookupFailed: Boolean, restoredBubbles: Int?, context: ContextRestore): String? = when {
        conversationLookupFailed -> CONVERSATION_NOT_LOADED
        restoredBubbles == null -> if (context == ContextRestore.RESTORED || context == ContextRestore.KEPT_IN_MEMORY)
            HISTORY_NOT_SHOWN else CONVERSATION_NOT_LOADED
        context == ContextRestore.FAILED -> if (restoredBubbles > 0) CONTEXT_NOT_LOADED else CONVERSATION_NOT_LOADED
        restoredBubbles > 0 || context != ContextRestore.NOTHING_STORED -> null
        else -> GREETING
    }
}
