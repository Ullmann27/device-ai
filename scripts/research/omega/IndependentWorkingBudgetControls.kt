package at.heinz.deviceai.cognitive

fun main() {
    var passed = 0
    fun verify(name: String, test: () -> Unit) {
        test(); passed++; println("PASS $name")
    }
    val context = LocalConversationContext(workingTaskResearchEnabled = true)
    val seed = WorkingTaskSeed("task-budget", 1, "Fensterübergang verbessern", "owner-goal",
        listOf(WorkingTaskConstraint("avatar", "Originalavatar nicht ersetzen", "owner-avatar"),
            WorkingTaskConstraint("voice", "Stimme nicht verändern", "owner-voice")),
        listOf(WorkingTaskReference("north", "Fassung Nord", "source-north"),
            WorkingTaskReference("south", "Fassung Süd", "source-south")), "south")
    check(context.bindWorkingTask("s", seed))
    val request = "Verbessere Fassung Nord."
    context.inspectRequest("s", request)
    val plan = (context.resolveWorkingTask("s",request) as WorkingTaskDecision.Resolved).plan
    verify("plan_current_after_admission") { check(context.isWorkingTaskPlanCurrent("s",plan)) }
    for (budget in listOf(64, 256, 4096)) {
        verify("whole_constraints_or_no_frame_budget_$budget") {
            val rendered = context.render("s",maxBytes = budget)
            check(rendered.toByteArray().size <= budget)
            if (rendered.contains("ARBEITSKONTEXT")) {
                check(rendered.contains(seed.goal))
                seed.constraints.forEach { check(rendered.contains(it.value)) }
            }
        }
    }
    verify("changed_request_invalidates_prior_plan") {
        context.resolveWorkingTask("s","Verbessere Fassung Süd.")
        check(!context.isWorkingTaskPlanCurrent("s",plan))
    }
    val newer = (context.resolveWorkingTask("s", request) as WorkingTaskDecision.Resolved).plan
    verify("forged_request_is_not_current_plan") {
        check(!context.isWorkingTaskPlanCurrent("s",newer.copy(originalRequest = "Ja.")))
    }
    verify("source_revision_invalidates_plan") {
        check(context.bindWorkingTask("s",seed.copy(revision = 2)))
        check(!context.isWorkingTaskPlanCurrent("s",newer))
    }
    val latest = (context.resolveWorkingTask("s", request) as WorkingTaskDecision.Resolved).plan
    verify("new_session_invalidates_plan") {
        context.inspectRequest("new-session","Hallo")
        check(!context.isWorkingTaskPlanCurrent("s",latest))
    }
    println("$passed/8 PASS; context validity only, no tool authority or receipt verification")
}
