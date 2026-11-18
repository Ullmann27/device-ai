package at.heinz.deviceai.cognitive

import kotlin.math.abs

/** Independent synthetic preflight: experiment is NOT Android or model training. */
fun main() {
    val ids = listOf("config", "data", "lifecycle", "ui")
    val buckets = mapOf(
        "config" to HypothesisWorkspace.Bucket.CONFIG,
        "data" to HypothesisWorkspace.Bucket.DATA,
        "lifecycle" to HypothesisWorkspace.Bucket.LIFECYCLE,
        "ui" to HypothesisWorkspace.Bucket.UI
    )
    val probes = ids.map { wanted ->
        HypothesisWorkspace.Probe(
            id = "p_$wanted", latencyCostMs = 1f, thermalCost = 0f,
            expectedByHypothesis = ids.associateWith { it == wanted }
        )
    }
    fun workspace(order: List<String>): HypothesisWorkspace {
        return HypothesisWorkspace().also { w ->
            order.forEach { id ->
                check(w.add(HypothesisWorkspace.Hypothesis(
                    id = id, bucket = requireNotNull(buckets[id]), claim = "synthetic: " + id, weight = 1f
                )))
            }
        }
    }
    fun allOrders(xs: List<String>): List<List<String>> {
        if (xs.size == 1) return listOf(xs)
        return xs.flatMap { x -> allOrders(xs.filter { it != x }).map { listOf(x) + it } }
    }
    val orders = allOrders(ids)
    var lastHighest = 0
    val probeCounts = linkedMapOf<String, Int>()
    var maxBias = 0f
    orders.forEach { order ->
        val w = workspace(order)
        val snapshot = w.snapshot()
        check(abs(snapshot.sumOf { it.weight.toDouble() } - 1.0) < 1e-5)
        val highest = requireNotNull(w.best()).id
        if (highest == order.last()) lastHighest++
        val bias = snapshot.maxOf { it.weight } - snapshot.minOf { it.weight }
        maxBias = maxOf(maxBias, bias)
        val p = requireNotNull(w.selectNextProbe(probes)).id
        probeCounts[p] = (probeCounts[p] ?: 0) + 1
    }
    check(orders.size == 24)
    println("experiment=equal-prior-permutation; source_sha=8bc695fd0d0fcc10ef0aa88684da8b57acc928f4")
    println("synthetic_permutations=" + orders.size)
    println("highest_weight_is_last_added=" + lastHighest + "/" + orders.size)
    println("max_weight_gap=" + maxBias)
    println("probe_choice_counts=" + probeCounts)
    println("original_order_weights=" + workspace(ids).snapshot().associate { it.id to it.weight })
    println("reverse_order_weights=" + workspace(ids.reversed()).snapshot().associate { it.id to it.weight })
    println("control_correct_equal_prior_each=0.25")
    println("scope=isolated_byte_copy_kotlin_no_production_changes_no_android")
}
