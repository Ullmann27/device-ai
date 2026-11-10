package at.heinz.deviceai.cognitive

fun main() {
    var failures = 0
    val utterances = listOf("Bernstein", "Aber Bernstein nicht.", "Also Bernstein nicht.", "Lieber Bernstein nicht.", "Okay, Bernstein nicht.")
    for (request in utterances) {
        val context = LocalConversationContext(true)
        check(context.bindWorkingTask("review", WorkingTaskSeed(
            "animation-task", 1, "Animation prüfen", "owner-turn-1",
            emptyList(), listOf(WorkingTaskReference("bernstein", "Bernstein", "source-1"),
                WorkingTaskReference("kobalt", "Kobalt", "source-2")), "kobalt")))
        val decision = context.resolveWorkingTask("review", request)
        val target = (decision as? WorkingTaskDecision.Resolved)?.plan?.reference?.id
        println("request=$request decision=${decision::class.simpleName} target=$target")
        if ((target == "bernstein") != (request == "Bernstein")) failures++
    }
    println("quality_assertions=${utterances.size} failures=$failures")
    check(failures == 0) { "REJECT_PROMOTION: negated target selected" }
}
