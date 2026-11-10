package at.heinz.deviceai.cognitive

fun main() {
    val context = LocalConversationContext(workingTaskResearchEnabled = true)
    val seed = WorkingTaskSeed("animation", 1, "Übergang der Arbeitsfenster verbessern", "owner-goal",
        listOf(WorkingTaskConstraint("avatar", "Originalavatar behalten", "owner-avatar"),
            WorkingTaskConstraint("orb", "Energiekugel behalten", "owner-orb"),
            WorkingTaskConstraint("voice", "Stimme unverändert lassen", "owner-voice")),
        listOf(WorkingTaskReference("anim-cobalt", "Kobalt", "revision-a"),
            WorkingTaskReference("anim-amber", "Bernstein", "revision-b")), "anim-amber")
    check(context.bindWorkingTask("s",seed))
    check(context.resolveWorkingTask("s","Bernstein bitte nicht.") is WorkingTaskDecision.Clarify)
    println("PASS polite_rejected_target_clarifies")
    val before = (context.resolveWorkingTask("s","Wieso läuft Bernstein nicht flüssig?") as WorkingTaskDecision.Resolved).plan
    println("PASS ordinary_negative_predicate_resolves")
    val correction = "Korrektur: Die Stimme darf jetzt heller werden. Avatar und Energiekugel bleiben unverändert."
    check(context.resolveWorkingTask("s",correction) is WorkingTaskDecision.Clarify)
    check(!context.isWorkingTaskPlanCurrent("s",before))
    println("PASS changed_constraint_invalidates_plan")
    check(context.resolveWorkingTask("s","Verbessere Bernstein.") is WorkingTaskDecision.Clarify)
    println("PASS later_reference_cannot_revive_stale_constraint")
    check(context.workingTaskSnapshot("s")!!.blockedReason == "CONSTRAINT_CHANGE_REQUIRES_REBIND")
    println("PASS block_reason_retained")
    val updated = seed.copy(revision = 2,constraints = seed.constraints.map {
        if (it.key == "voice") it.copy(value = "Stimme darf heller werden",sourceId = "owner-voice-revision-2") else it
    })
    check(context.bindWorkingTask("s",updated))
    val after = (context.resolveWorkingTask("s","Verbessere Bernstein.") as WorkingTaskDecision.Resolved).plan
    check(after.constraints.any { it.value == "Stimme darf heller werden" })
    println("PASS trusted_higher_revision_rebind_restores_planning")
    println("6/6 PASS; exposed safety regression, no autonomous constraint update")
}
