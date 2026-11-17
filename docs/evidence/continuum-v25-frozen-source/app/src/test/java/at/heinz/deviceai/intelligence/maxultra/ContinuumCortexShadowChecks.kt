package at.heinz.deviceai.intelligence.maxultra

import at.heinz.deviceai.intelligence.maxultra.ReviewedKnowledgeEdge.Relation as R

/**
 * Portable safety and behavior checks, executed with Kotlin/JVM in the isolated
 * local sandbox before being copied into the repository.
 */
object ContinuumCortexShadowChecks {
    @JvmStatic fun main(args: Array<String>) {
        val fast = EphemeralFastWeightCore()
        val a = DoubleArray(16) { if (it == 0) 1.0 else 0.0 }
        val b = DoubleArray(16) { if (it == 1) 1.0 else 0.0 }
        check(fast.observe(a, "Z77") == 0.0)
        check(fast.observe(b) > 0.0)
        check(fast.updates == 1)
        check(fast.focusedSubjectFor("Und welche Netzfrequenz gilt dort?") == "Z77")
        check(fast.focusedSubjectFor("Neue Frage ohne Pronomen") == null)
        for (i in 0 until 10000) fast.observe(if (i % 2 == 0) a else b)
        check(fast.weightNorm().isFinite() && fast.weightNorm() <= 2.500001)
        val n = fast.weightNorm()
        check(runCatching { fast.observe(DoubleArray(16) { Double.NaN }) }.isFailure)
        check(fast.weightNorm() == n)
        fast.reset(); check(fast.updates == 0 && fast.weightNorm() == 0.0)

        fun e(id: String, s: String, r: ReviewedKnowledgeEdge.Relation, t: String) =
            ReviewedKnowledgeEdge(id, s, r, t, ReviewedKnowledgeEdge.pin(id, s, r, t), true)
        val facts = listOf(
            e("src1", "Z77", R.LOCATED_IN, "Werkhalle-Nord"),
            e("src2", "Werkhalle-Nord", R.CITY, "Graz"),
            e("src3", "Graz", R.NOMINAL_GRID_FREQUENCY, "50Hz")
        )
        check(runCatching { EvidenceBoundedThoughtSearch(facts + facts[0].copy(sourceId = "src4")) }.isFailure)
        val planner = EvidenceBoundedThoughtSearch(facts)
        val city = EvidenceBoundedThoughtSearch.Query("Z77", EvidenceBoundedThoughtSearch.Question.CITY)
        val grid = EvidenceBoundedThoughtSearch.Query("Z77", EvidenceBoundedThoughtSearch.Question.NOMINAL_GRID_FREQUENCY_AT_LOCATION)
        val unknown = EvidenceBoundedThoughtSearch.Query("X99", EvidenceBoundedThoughtSearch.Question.CITY)
        check(planner.solve(city)?.value == "Graz")
        check(planner.solve(city)?.hops == 2)
        check(planner.solve(grid)?.value == "50Hz")
        check(planner.solve(grid)?.hops == 3)
        check(planner.solve(unknown) == null)
        val conflict = EvidenceBoundedThoughtSearch(facts + e("src4", "Werkhalle-Nord", R.CITY, "Wien"))
        check(conflict.solve(city) == null)
        check(conflict.solve(grid) == null)
        check(runCatching { EvidenceBoundedThoughtSearch(facts.map {
            if (it.sourceId == "src3") it.copy(target = "60Hz") else it
        }) }.isFailure)

        val incomplete = WakeSleepShadowHabitCore(planner)
        check(incomplete.stage(grid) && incomplete.stage(city))
        check(!incomplete.consolidate(listOf(
            WakeSleepShadowHabitCore.Probe(grid, "50Hz"),
            WakeSleepShadowHabitCore.Probe(unknown, null),
            WakeSleepShadowHabitCore.Probe(EvidenceBoundedThoughtSearch.Query(
                "missing", EvidenceBoundedThoughtSearch.Question.CITY), null)
        )).accepted)
        check(incomplete.habitCount() == 0)

        val habits = WakeSleepShadowHabitCore(planner)
        check(habits.stage(grid))
        check(!habits.consolidate(emptyList()).accepted)
        val probes = listOf(
            WakeSleepShadowHabitCore.Probe(grid, "50Hz"),
            WakeSleepShadowHabitCore.Probe(city, "Graz"),
            WakeSleepShadowHabitCore.Probe(unknown, null)
        )
        check(habits.consolidate(probes.mapIndexed { index, v ->
            if (index == 0) v.copy(expected = "60Hz") else v
        }).reason == "HOLDOUT_MISMATCH_NO_PROMOTION")
        check(habits.habitCount() == 0)
        val accepted = habits.consolidate(probes)
        check(accepted.accepted && accepted.negativeProbes == 1 && accepted.positiveProbes == 2)
        check(habits.habitCount() == 1)
        check(habits.read(grid)?.value == "50Hz" && habits.read(grid)?.hops == 3)
        habits.reset(); check(habits.habitCount() == 0)
        println("PASS: Cortex shadow safety and deterministic behavior assertions")
    }
}
