package at.heinz.deviceai.cockpit

// VERBATIM contract from Jarvis Kern (binding, relayed 2026-11-04 09:17). Not yet on #481 at 02097c48;
// this file must be dropped in favour of Kern's copy on rebase. Do not alter.

enum class AnswerPillKind { DIREKT_ANTWORTEN, AUSARBEITEN, STUDIE, ANRUFEN }
data class AnswerPill(val kind: AnswerPillKind, val toolName: String?, val enabled: Boolean, val disabledReason: String?)
interface PillActionGate {
  fun pillsFor(event: CockpitAnimationEvent.DocumentQuestion): List<AnswerPill>
  fun request(pill: AnswerPill, turnId: String, onDecision: (approvedByOwner: Boolean) -> Unit)
}
interface AccountEventSource { fun addListener(l: (CockpitAnimationEvent.AccountCardFlyIn) -> Unit): AutoCloseable }
