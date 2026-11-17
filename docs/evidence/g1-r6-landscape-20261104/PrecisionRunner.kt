package at.heinz.deviceai.ui

fun main() {
    val checks = linkedMapOf<String, () -> Unit>(
        "strictRegression" to { ContinuumLandscapeTargetPrecisionChecks.strictRegression() },
        "legacyMatrix" to { ContinuumLandscapeTargetPrecisionChecks.legacyMatrix() },
        "unchangedHeroBounds" to { ContinuumLandscapeTargetPrecisionChecks.unchangedHeroBounds() }
    )
    var failed = 0
    for ((name, check) in checks) {
        try { check(); println("PASS $name") }
        catch (error: IllegalStateException) { failed++; println("FAIL $name ${error.message}") }
    }
    println("checks=${checks.size} failed=$failed")
    if (failed > 0) kotlin.system.exitProcess(1)
}
