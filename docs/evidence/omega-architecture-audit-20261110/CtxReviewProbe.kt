package at.heinz.deviceai.learning.context

fun main() {
    val a = Ctx3Entity("a", "Fokus App", setOf("Fokus App"), setOf("sie"), setOf("s1"))
    val b = Ctx3Entity("b", "Liste App", setOf("Liste App"), setOf("sie"), setOf("s2"))
    val entities = listOf(a, b)
    fun prepared() = Ctx3Context().apply {
        decide("Fokus App und Liste App", entities, "v1")
        observe(setOf("s1"), entities)
    }
    val rejected = prepared().decide("Nicht die zweite, sondern die andere", entities, "v1")
    check(rejected is Ctx3Decision.Query && rejected.sourceIds == setOf("s2"))
    println("relative_contrast_selects_rejected_s2=true")
    val predicate = prepared().decide("Warum startet die zweite nicht?", entities, "v1")
    check(predicate is Ctx3Decision.Abstain)
    println("capitalized_why_intercepts_as_unknown_entity=true")
    val lower = prepared().decide("warum startet die zweite nicht?", entities, "v1")
    check(lower is Ctx3Decision.Clarify)
    println("lowercase_negated_predicate_loses_explicit_ordinal=true")
}
