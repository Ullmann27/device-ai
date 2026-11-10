package at.heinz.deviceai.cognitive

fun main() {
    val context = LocalConversationContext(true)
    check(context.bindWorkingTask("review", WorkingTaskSeed(
        "animation-task", 1, "Animation prüfen", "owner-turn-1",
        listOf(WorkingTaskConstraint("avatar", "Original behalten", "owner-turn-1")),
        listOf(WorkingTaskReference("alpha", "Animation Alpha", "source-1"),
            WorkingTaskReference("beta", "Animation Beta", "source-2")), "beta")))
    val unrelated = context.resolveWorkingTask("review", "Wie spät ist es?")
    val memoryDirective = context.inspectRequest("review", "Merke dir diese Entscheidung")
    val baseline = LocalConversationContext().inspectRequest("review", "Merke dir diese Entscheidung")
    println("unrelated_decision=${unrelated::class.simpleName}")
    println("unrelated_target=${(unrelated as? WorkingTaskDecision.Resolved)?.plan?.reference?.id}")
    println("opt_in_memory_directive=${memoryDirective?.kind}")
    println("default_memory_directive=${baseline?.kind}")
    val failures = listOf(
        unrelated !is WorkingTaskDecision.Resolved,
        memoryDirective?.kind == ContextDirectiveKind.MEMORY_LITERAL_REQUIRED,
        baseline?.kind == ContextDirectiveKind.MEMORY_LITERAL_REQUIRED
    ).count { !it }
    println("quality_assertions=3 failures=$failures")
    check(failures == 0) { "REJECT_PROMOTION: unresolved opt-in review counterexamples" }
}
