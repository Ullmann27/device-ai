package at.heinz.deviceai.chief

import at.heinz.deviceai.localmodel.LocalModelState

/**
 * Availability line of a bot card. All bots share the one process runtime (LocalModelHost); a bot never has a model
 * of its own. Owner Fold7 report (06.11.2026): after an app restart the original model is restored asynchronously
 * (LOADING, no loaded model yet) and a failed load also has none – both were shown as „nicht geladen“. Without a
 * model the text names the explicit way to load it; nothing is loaded automatically and READY is never invented.
 */
object BotRuntimeAvailability {
    fun text(modelLoaded: Boolean, state: LocalModelState, active: Boolean, busy: Boolean): String = when {
        !modelLoaded -> when (state) {
            LocalModelState.LOADING -> "Lokales Modell wird geladen … Aufträge sind danach möglich"
            LocalModelState.UNLOADING -> "Lokales Modell wird entladen"
            LocalModelState.FAILED -> "Laden des lokalen Modells fehlgeschlagen · Modellverwaltung öffnen und erneut laden"
            else -> "Lokales Modell nicht geladen · in der Modellverwaltung laden"
        }
        active && state == LocalModelState.GENERATING -> "Lokales Modell arbeitet"
        active && busy -> "Lokaler Auftrag läuft"
        busy || state == LocalModelState.GENERATING -> "Gemeinsame Ausführung belegt"
        state == LocalModelState.READY -> "Für einen Auftrag erreichbar"
        else -> "Lokales Modell derzeit nicht bereit"
    }
}
