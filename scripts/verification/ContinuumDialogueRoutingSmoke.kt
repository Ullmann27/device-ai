import at.heinz.deviceai.AssistantMode
import at.heinz.deviceai.AssistantRoute
import at.heinz.deviceai.LocalFirstPolicy
import at.heinz.deviceai.LocalModelUiState
import at.heinz.deviceai.localmodel.LocalModelState

/**
 * Focused host-only smoke for exactly the app's real LocalFirstPolicy.kt.
 * A package-only copy of LocalModelState's real declaration is extracted by
 * the companion script. The model, Android app and owner memories are not run.
 */
fun main() {
    val direct = listOf(
        "Was habe ich über „analysiere“ gespeichert?",
        "Was weißt du über \"warum\" aus meiner Erinnerung?",
        "Wo steht 'plane' in meiner Notiz?",
        "Was habe ich zu `vergleiche` gespeichert?",
        "Was bedeutet das Wort analysiere?",
        "Wo wohnt Mira?",
        "Was bedeutet das Wort Vergleich?"
    )
    val synthesis = listOf(
        "Bitte analysiere den Widerspruch zur zitierten Frage „warum“.",
        "Warum ist die gespeicherte Zahl unplausibel?",
        "Vergleiche die beiden gespeicherten Projektpläne.",
        "Bitte leite daraus einen vorsichtigen Vorschlag ab.",
        "Analysiere den Widerspruch und begründe deine Einschätzung.",
        "Was bedeutet das Wort analysiere und warum ist es wichtig?"
    )

    var tested = 0
    for (request in direct) {
        val actual = LocalFirstPolicy.afterMemory(true, true, request)
        check(actual == AssistantRoute.LOCAL_CORE) {
            "Unsafe quoted direct recall route: \"$request\" -> $actual"
        }
        tested++
    }
    for (request in synthesis) {
        val actual = LocalFirstPolicy.afterMemory(true, true, request)
        check(actual == AssistantRoute.LOCAL_MODEL) {
            "Lost explicit synthesis: \"$request\" -> $actual"
        }
        tested++
    }
    check(LocalFirstPolicy.afterMemory(true, false, synthesis.first()) == AssistantRoute.LOCAL_CORE); tested++
    check(LocalFirstPolicy.afterMemory(false, true, direct.first()) == AssistantRoute.LOCAL_MODEL); tested++
    check(LocalFirstPolicy.route(AssistantMode.AUTO, false) == AssistantRoute.MODEL_SETUP_REQUIRED); tested++
    check(LocalFirstPolicy.modelUiState(LocalModelState.EMPTY, false, false) == LocalModelUiState.NO_MODEL); tested++

    val original = "Gegebener Kontext: " + "Daten ".repeat(100) + "\nBerechne 19 plus 28."
    check(LocalFirstPolicy.localModelQuestion(original, original.take(512)) == original); tested++
    check(LocalFirstPolicy.localModelQuestion("Und warum?", "Kontext-Frage") == "Kontext-Frage"); tested++
    println("CONTINUUM_ROUTING_SMOKE_PASS: $tested focused JVM cases")
}
