package at.heinz.deviceai.ui

import at.heinz.deviceai.cockpit.ActionAvailability
import at.heinz.deviceai.cockpit.CockpitAction
import at.heinz.deviceai.cockpit.CockpitActionHandler
import at.heinz.deviceai.cockpit.CockpitAnimationTriggers
import at.heinz.deviceai.cockpit.JarvisNavigator
import at.heinz.deviceai.cockpit.TransitionPattern
import at.heinz.deviceai.ui.ContinuumIcons.Icon
import at.heinz.deviceai.ui.ContinuumHomeLayout.Slot

/**
 * Menu navigation of the Jarvis cockpit (Jarvis Integration; Bildbeschrieb Abschnitt 3/5).
 * Look and motion belong here, the triggers (CockpitAnimationTriggers / CockpitAnimationPresenter) to Jarvis Kern.
 * Every target opens an EXISTING owner-controlled screen through AgentActivity.openHomeSlot; nothing here starts work.
 */
object JarvisMenu {
    /** AREA = switches the main pane (Muster B), SCENE = opens its own screen / centre (Muster A). */
    enum class Kind { AREA, SCENE }

    data class Entry(val target: String, val label: String, val icon: Icon, val kind: Kind)

    /** ref5 / VIDEO-SPEC rail (German labels from the spec, same order). */
    val railEntries = listOf(
        Entry("home", "Übersicht", Icon.HOME, Kind.AREA),
        Entry("chat", "Chat", Icon.CHAT, Kind.AREA),
        Entry("agents", "Agenten", Icon.USERS, Kind.SCENE),
        Entry("memory", "Gedächtnis", Icon.DATABASE, Kind.SCENE),
        Entry("research", "Forschung", Icon.SEARCH, Kind.SCENE),
        Entry("development", "Entwicklung", Icon.CODE, Kind.SCENE),
        Entry("system", "System", Icon.GEAR, Kind.SCENE),
        Entry("live", "Live-Status", Icon.PULSE, Kind.SCENE),
    )

    /**
     * Kern's tile/nav routes (JarvisShellContent.routes, JarvisTile.target) plus the three targets the rail and the
     * action bar need that #479 has no tile for. Each maps to an existing AgentActivity.openHomeSlot branch:
     * development → showDevelopmentCenter, live → showDiagnosticsCenter, verify → showDiagnosticsCenter.
     */
    val extraRoutes: Map<String, Slot> = mapOf(
        "development" to Slot.LABEL_DEVELOPING,
        "live" to Slot.SYSTEM_ACTIVITY,
        "verify" to Slot.ACTION_VERIFY,
    )
    val routes: Map<String, Slot> = JarvisShellContent.routes + extraRoutes

    /** Action bar (Video 1, 18–20 s, plus Kern's Ausführen/Überprüfen) → navigation target. */
    val actionBar: List<Pair<CockpitAction, String>> = listOf(
        CockpitAction.THINK to "Denken", CockpitAction.RESEARCH to "Recherchieren", CockpitAction.LEARN to "Lernen",
        CockpitAction.DEVELOP to "Entwickeln", CockpitAction.EXECUTE to "Ausführen", CockpitAction.VERIFY to "Überprüfen",
    )
    val actionTargets: Map<CockpitAction, String> = mapOf(
        CockpitAction.THINK to "chat",          // chat = the thinking turn (TurnLifecycle)
        CockpitAction.RESEARCH to "research",   // showResearchModeDialog
        CockpitAction.LEARN to "learning",      // showLearningCenter
        CockpitAction.DEVELOP to "development", // showDevelopmentCenter
        CockpitAction.EXECUTE to "tasks",       // showCodingTaskDraftDialog (owner confirms; ToolGate unchanged)
        CockpitAction.VERIFY to "verify",       // showDiagnosticsCenter
    )

    const val SOURCE_MISSING = "Quelle fehlt"

    fun kindOf(target: String?): Kind? = railEntries.firstOrNull { it.target == target }?.kind
        ?: target?.takeIf { it in routes }?.let { if (it == "home" || it == "chat") Kind.AREA else Kind.SCENE }

    /** Leaving or entering a scene is a scene change (A, also "Zurück" in Video 1); area ↔ area is B. */
    fun patternFor(from: String?, to: String): TransitionPattern =
        if (kindOf(to) == Kind.SCENE || kindOf(from) == Kind.SCENE) TransitionPattern.A_DEPTH else TransitionPattern.B_FLY_IN

    fun railIndex(target: String?): Int = railEntries.indexOfFirst { it.target == target }
}

/** [JarvisNavigator] on the existing screens: emits the area change for Kern's presenter, then opens the screen. */
class JarvisMenuNavigator(
    private val triggers: CockpitAnimationTriggers?,
    private val open: (Slot) -> Unit,
    private val routes: Map<String, Slot> = JarvisMenu.routes,
    private val onSelected: (String) -> Unit = {},
) : JarvisNavigator {
    @Volatile var current: String? = "home"
        private set

    override fun navigate(target: String) {
        val slot = routes[target] ?: return // unknown target: nothing happens, no event
        val from = current
        current = target
        if (from != target) triggers?.areaChanged(from, target, JarvisMenu.patternFor(from, target))
        onSelected(target)
        open(slot)
    }

    /** Keeps the selection in sync when another path (bottom bar, tile) changed the area; no event. */
    fun syncCurrent(target: String) { if (target in routes) current = target }
}

/** [CockpitActionHandler]: every bar action leads to a real path, otherwise disabled with "Quelle fehlt". */
class JarvisCockpitActions(
    private val navigator: JarvisNavigator,
    private val hasPath: (String) -> Boolean = { it in JarvisMenu.routes },
) : CockpitActionHandler {
    override fun availability(action: CockpitAction): ActionAvailability {
        val target = JarvisMenu.actionTargets[action]
        return if (target != null && hasPath(target)) ActionAvailability(true, null)
        else ActionAvailability(false, JarvisMenu.SOURCE_MISSING)
    }

    /** Opens the action's screen; [turnId] is not needed for navigation (reported to Kern). Disabled → no-op. */
    override fun perform(action: CockpitAction, turnId: String) {
        if (!availability(action).enabled) return
        navigator.navigate(JarvisMenu.actionTargets.getValue(action))
    }
}
