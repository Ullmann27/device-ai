package at.heinz.deviceai.learning.synapse

/** Public synthetic DEV-only smoke corpus. It is NOT a blind holdout:
 * known phrases, templates, and outcomes must not be reported as FROZEN_TEST gains. */
object SynapseContrastiveShadowBenchmark {
    private val training = listOf(
        Triple("Welches Kühlmittel benötigt Roboter Z77?", "Modul M4 benötigt Kühlmittel Helium.", "Modul M4 benötigt Schutzgas Argon."),
        Triple("Wo befindet sich Einheit R10?", "Einheit R10 steht in Wien.", "Einheit R10 nutzt Modul M4."),
        Triple("Welches Schutzgas verwendet Einheit R20?", "Für Modul M9 ist Schutzgas Argon vorgeschrieben.", "Für Modul M9 ist Kühlmittel Neon vorgeschrieben.")
    )
    private val developmentProbes = listOf(
        Triple("Welches Kühlmittel braucht Roboter Z99?", "Modul M7 braucht Kühlmittel Neon.", "Modul M7 benötigt Schutzgas Xenon."),
        Triple("Wo steht Einheit R40?", "Einheit R40 befindet sich in Salzburg.", "Einheit R40 nutzt Modul M8."),
        Triple("Welches Schutzgas benötigt Einheit R50?", "Für Modul M8 ist Schutzgas Helium erforderlich.", "Für Modul M8 ist Kühlmittel Neon erforderlich."),
        Triple("Womit wird Roboter Z14 gekühlt?", "Modul M3 verwendet Kühlmittel Argon.", "Modul M3 verwendet Schutzgas Neon.")
    )
    data class Report(val seed: Int, val updates: Int, val beforeCorrect: Int, val afterCorrect: Int, val total: Int)
    fun run(): List<Report> = listOf(47, 99, 20261022).map { seed ->
        val model = SynapseContrastiveRoleProbe(seed)
        fun successes() = developmentProbes.count { (q,p,n) -> model.rankScore(q,p) > model.rankScore(q,n) }
        val before = successes()
        repeat(360) { for ((q,p,n) in training) model.trainPair(q,p,n,0.12) }
        Report(seed,model.updates,before,successes(),developmentProbes.size)
    }
    @JvmStatic fun main(args: Array<String>) {
        for(r in run()) println("SYNAPSE_DEV_ONLY seed=" + r.seed + " updates=" + r.updates +
            " before=" + r.beforeCorrect + "/" + r.total + " after=" + r.afterCorrect + "/" + r.total)
    }
}
