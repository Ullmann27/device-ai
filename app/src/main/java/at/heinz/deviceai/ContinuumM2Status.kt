package at.heinz.deviceai

/**
 * Visible second-milestone evidence, not a progress percentage and never a
 * model-training claim. Each green check requires an actual measured state.
 * This report has no I/O, tool execution, network or model startup.
 */
internal object ContinuumM2Status {
    data class Snapshot(
        val modelImported: Boolean,
        val modelLoaded: Boolean,
        val modelRuntimeReady: Boolean,
        val directInferenceObserved: Boolean,
        val directInferenceMillis: Long?
    )

    fun render(s: Snapshot): String = buildString {
        val gemmaReady = s.modelLoaded && s.modelRuntimeReady
        val direct = gemmaReady && s.directInferenceObserved &&
            s.directInferenceMillis != null && s.directInferenceMillis >= 0L
        append("CONTINUUM · MEILENSTEIN 2 · ECHTE NACHWEISE\n\n")
        append("1 · Gemma 4 E4B Originaldatei: ")
        append(if (s.modelImported || s.modelLoaded) "SHA-256 im lokalen Katalog bestätigt" else "NOCH NICHT NACHGEWIESEN")
        append("\n2 · Gemma lokal geladen: ")
        append(if (gemmaReady) "JA – Laufzeit meldet bereit" else "NEIN / NICHT BEREIT")
        append("\n3 · Direkte native Antwort in DIESER App-Sitzung: ")
        append(if (direct) "GEMESSEN · ${s.directInferenceMillis} ms" else "NICHT NACHGEWIESEN")
        append("\n4 · Offline-Test bei abgeschaltetem Internet: NICHT NACHGEWIESEN")
        append("\n5 · Mehrstufiger echter Gemma-Dialog: NICHT NACHGEWIESEN")
        append("\n6 · Freigegebener Tool-Aufruf mit echter Quittung: NICHT NACHGEWIESEN")
        append("\n7 · Modellgenerierter CCC1-Patch: NICHT NACHGEWIESEN")
        append("\n8 · RED → GREEN → frischer unabhängiger Test: NICHT NACHGEWIESEN")
        append("\n9 · Isolierte Quelle und Besitzerfreigabe des Patches: NICHT NACHGEWIESEN")
        append("\n10 · Lernen aus geprüfter Reparatur nach Neustart: NICHT NACHGEWIESEN")
        append("\n\nNur ein wirklich ausgeführter Test zählt. Keine Simulation, kein heimlicher Qwen-Ersatz.")
        append("\nFür den tatsächlichen Gemma-Test: 'Meilenstein Modelltest'.")
        append("\nFür sichere Reparaturwerkzeuge: 'Wie aktiviere ich den Reparatur Agent'.")
        append("\nEs wurde nichts installiert, repariert oder freigegeben.")
    }
}
