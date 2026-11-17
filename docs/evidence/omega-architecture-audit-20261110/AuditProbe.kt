package at.heinz.deviceai.cognitive

fun main() {
    val request = "Du, schau mal die Animation von vorher. Die ist noch nicht so, wie ich das haben wollte. Die andere war besser. Weißt eh welche. Schau dir das nochmal an und mach es besser. Aber lass die restlichen Sachen so, wie wir es besprochen haben."
    val tracker = LocalConversationSituationTracker()
    tracker.observeOwnerTurn("Animation Build Blau soll bleiben. Avatar und Kugel unverändert lassen.")
    tracker.observeOwnerTurn("Jetzt vergleichen wir CPU Temperatur im Gerät.")
    check(LocalConversationFollowUpPolicy.requiresPreviousOwnerTurn(request))
    println("central_request_followup=true")
    check(tracker.renderHint(request).isEmpty())
    println("central_request_topic_hint=EMPTY")
    val state = tracker.snapshot()
    check(state.currentGoal == null && state.currentTask == null && state.currentProject == null)
    check(state.threads.all { it.openQuestions.isEmpty() && it.recentCorrections.isEmpty() })
    println("structured_goal_task_project=NULL; question_correction_slots=EMPTY")
    val positive = tracker.renderHint("Zurück zur Animation von vorhin.")
    check(positive.contains("SITUATIONSHINWEIS"))
    println("explicit_return_control=HINT_PRESENT")
}
