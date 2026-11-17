package at.heinz.deviceai.cockpit

/**
 * The ONE flow timeline (Jarvis Kern owns WHEN; each owner owns HOW it looks). Pure: a real event in, ordered
 * cues with start offset and duration out. The View layer (CockpitAnimationPresenter) only executes the cues by
 * handing them to the registered [CockpitCueRenderer] of the owner, else a minimal default.
 * Timings: VIDEO-SPEC.md video 1 and the #479 JarvisMotion durations (mirrored here, the UI test pins equality).
 */
enum class CueOwner { KERN, AVATAR, INTEGRATION, GEDAECHTNIS, SICHERHEIT, LUECKENFUELLER }

enum class Cue(val owner: CueOwner) {
    AVATAR_FROM_DEPTH(CueOwner.AVATAR), GREETING(CueOwner.AVATAR), AVATAR_SHRINK(CueOwner.AVATAR),
    FILE_ICONS_FROM_HEAD(CueOwner.AVATAR),
    COCKPIT_FLY_IN(CueOwner.INTEGRATION), ACTION_FEEDBACK(CueOwner.INTEGRATION), PROCESS_STEP(CueOwner.INTEGRATION),
    AREA_DEPTH(CueOwner.INTEGRATION), AREA_FLY_IN(CueOwner.INTEGRATION), SETTLE(CueOwner.INTEGRATION),
    DOCUMENT_INTO_ORB(CueOwner.GEDAECHTNIS), MEMORY_PULSE(CueOwner.GEDAECHTNIS), RECALL_POINTS(CueOwner.GEDAECHTNIS),
    CALCULATION(CueOwner.GEDAECHTNIS),
    ACCOUNT_ENVELOPE(CueOwner.SICHERHEIT), DOCUMENT_PILLS(CueOwner.SICHERHEIT),
    AREA_WARP(CueOwner.LUECKENFUELLER), ALARM_ON(CueOwner.LUECKENFUELLER), ALARM_OFF(CueOwner.LUECKENFUELLER),
    /** Scene 2 start: icons burst from the head (Avatar headIconBurst). */
    HEAD_ICON_BURST(CueOwner.AVATAR),
    /** Status-only warning (thermal, memory pressure, data overload): no alarm scene. */
    STATUS_WARNING(CueOwner.KERN), STATUS_CLEARED(CueOwner.KERN),
}

data class TimelineStep(val atMs: Long, val durationMs: Long, val cue: Cue, val event: CockpitAnimationEvent) {
    val endMs: Long get() = atMs + durationMs
}

/** Owner-side executor of a cue; returns false to let the cockpit play its minimal default. Main thread. */
fun interface CockpitCueRenderer { fun play(step: TimelineStep): Boolean }

object CockpitCues {
    private val renderers = java.util.concurrent.ConcurrentHashMap<CueOwner, CockpitCueRenderer>()
    fun register(owner: CueOwner, renderer: CockpitCueRenderer?) {
        if (renderer == null) renderers.remove(owner) else renderers[owner] = renderer
    }
    fun rendererFor(owner: CueOwner): CockpitCueRenderer? = renderers[owner]
}

object CockpitTimeline {
    const val GREETING_TEXT = "Hallo Heinz, ich fahre die Systeme hoch."
    // Video 1: 0–2 s avatar from depth + greeting, 3–5 s cockpit appears (avatar shrinks ~30 %, panels fly in).
    const val BOOT_AVATAR_MS = 2_000L
    const val BOOT_COCKPIT_AT_MS = 3_000L
    // #479 JarvisMotion.Durations (pinned by CockpitTimelineTest against the real constants via the UI module test).
    const val PANEL_FLY_IN_MS = 600L
    const val STAGGER_MS = 80L
    const val SCENE_Z_MS = 800L
    const val ENVELOPE_MS = 500L + 300L
    const val PILL_MS = 450L
    const val STEP_MS = 500L
    /** Panels flying in during scene 1 / B (centre first, staggered). */
    const val PANEL_COUNT = 5
    val panelGroupMs: Long get() = PANEL_FLY_IN_MS + (PANEL_COUNT - 1) * STAGGER_MS

    /** End of scene 1 relative to its start: the cockpit is visible. */
    val bootEndMs: Long get() = BOOT_COCKPIT_AT_MS + panelGroupMs

    private val enteredSessions = java.util.Collections.synchronizedSet(HashSet<String>())

    /** Called by the executor when scene 1 ended: CockpitEntered exactly once per app session, else null. */
    fun cockpitEntered(sessionId: String, nowMillis: Long): CockpitAnimationEvent.CockpitEntered? =
        if (enteredSessions.add(sessionId)) CockpitAnimationEvent.CockpitEntered(BOOT_TURN, nowMillis, sessionId) else null

    /** Scene 1, triggered by the real first cockpit start of the session ([CockpitAnimationEvent.BootStarted]). */
    fun boot(e: CockpitAnimationEvent.BootStarted): List<TimelineStep> = listOf(
        TimelineStep(0, BOOT_AVATAR_MS, Cue.AVATAR_FROM_DEPTH, e),
        TimelineStep(0, BOOT_AVATAR_MS, Cue.GREETING, e),
        TimelineStep(BOOT_COCKPIT_AT_MS, PANEL_FLY_IN_MS, Cue.AVATAR_SHRINK, e),
        TimelineStep(BOOT_COCKPIT_AT_MS, panelGroupMs, Cue.COCKPIT_FLY_IN, e),
    )

    fun steps(e: CockpitAnimationEvent): List<TimelineStep> = when (e) {
        is CockpitAnimationEvent.BootStarted -> boot(e)
        is CockpitAnimationEvent.CommandFlyIn -> listOf(TimelineStep(0, panelGroupMs, Cue.COCKPIT_FLY_IN, e))
        is CockpitAnimationEvent.ActionStarted -> listOf(TimelineStep(0, STEP_MS, Cue.ACTION_FEEDBACK, e))
        is CockpitAnimationEvent.ProcessTimeline -> listOf(TimelineStep(0, STEP_MS, Cue.PROCESS_STEP, e))
        is CockpitAnimationEvent.TurnEnded -> listOf(TimelineStep(0, STEP_MS, Cue.SETTLE, e))
        // Scene 2: file icons leave the head (Avatar), then fly into the orb (Gedächtnis).
        is CockpitAnimationEvent.DocumentLearned -> listOf(
            TimelineStep(0, PANEL_FLY_IN_MS, Cue.FILE_ICONS_FROM_HEAD, e),
            TimelineStep(PANEL_FLY_IN_MS / 2, SCENE_Z_MS, Cue.DOCUMENT_INTO_ORB, e))
        is CockpitAnimationEvent.MemoryStored -> listOf(TimelineStep(0, STEP_MS, Cue.MEMORY_PULSE, e))
        is CockpitAnimationEvent.RecallHits ->
            if (e.count > 0) listOf(TimelineStep(0, STEP_MS, Cue.RECALL_POINTS, e)) else emptyList()
        is CockpitAnimationEvent.CalculationShown -> listOf(TimelineStep(0, PANEL_FLY_IN_MS, Cue.CALCULATION, e))
        is CockpitAnimationEvent.AccountCardFlyIn -> listOf(TimelineStep(0, ENVELOPE_MS, Cue.ACCOUNT_ENVELOPE, e))
        is CockpitAnimationEvent.DocumentQuestion -> listOf(TimelineStep(0, PILL_MS, Cue.DOCUMENT_PILLS, e))
        is CockpitAnimationEvent.AreaChanged -> listOf(when (e.pattern) {
            TransitionPattern.A_DEPTH -> TimelineStep(0, SCENE_Z_MS, Cue.AREA_DEPTH, e)
            TransitionPattern.B_FLY_IN -> TimelineStep(0, panelGroupMs, Cue.AREA_FLY_IN, e)
            TransitionPattern.C_WARP -> TimelineStep(0, SCENE_Z_MS, Cue.AREA_WARP, e)
        })
        // Alarm scene H ONLY for a real security finding; the other kinds are status-only.
        is CockpitAnimationEvent.AlarmRaised -> listOf(TimelineStep(0, STEP_MS,
            if (e.kind == AlarmKind.SECURITY_FINDING) Cue.ALARM_ON else Cue.STATUS_WARNING, e))
        is CockpitAnimationEvent.AlarmCleared -> listOf(TimelineStep(0, STEP_MS,
            if (e.kind == AlarmKind.SECURITY_FINDING) Cue.ALARM_OFF else Cue.STATUS_CLEARED, e))
        is CockpitAnimationEvent.CockpitEntered -> listOf(TimelineStep(0, PANEL_FLY_IN_MS, Cue.HEAD_ICON_BURST, e))
        is CockpitAnimationEvent.Extension -> emptyList()
    }
}
