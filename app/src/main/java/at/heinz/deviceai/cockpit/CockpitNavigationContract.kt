package at.heinz.deviceai.cockpit

/*
 * KERN-VERTRAG (vorläufig): verbatim aus Jarvis Kerns Nachricht vom 04.11.2026 an Integration.
 * Auf #481 @02097c48 noch nicht gepusht; Integration hat ihn hier unverändert angelegt, damit die Menüführung
 * dagegen bauen kann. Kern ersetzt diese Datei beim Einbau durch seine eigene (gleiche Signaturen).
 */

/** Navigation between cockpit areas; [target] is an existing JarvisTile.target (plus the rail targets in JarvisMenu). */
interface JarvisNavigator {
    fun navigate(target: String /* bestehendes JarvisTile.target */)
}

/** Action bar (Denken, Recherchieren, Lernen, Entwickeln, Ausführen, Überprüfen). */
interface CockpitActionHandler {
    fun availability(action: CockpitAction): ActionAvailability
    fun perform(action: CockpitAction, turnId: String)
}

data class ActionAvailability(val enabled: Boolean, val reason: String?)
