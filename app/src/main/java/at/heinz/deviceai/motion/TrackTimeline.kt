package at.heinz.deviceai.motion

/**
 * [MotionTimeline] implementation. [evaluate] writes out[i] = tracks[i].easing.at(local progress):
 * before a track's start the progress is 0 (→ at(0)), after its end 1 (→ at(1)); a zero-length track
 * jumps to 1 at its start. Evaluation is pure (any tMs order, no state) and allocation-free.
 */
class TrackTimeline(override val tracks: List<MotionTrack>) : MotionTimeline {
    private val starts = LongArray(tracks.size) { tracks[it].startMs }
    private val durations = LongArray(tracks.size) { tracks[it].durationMs }
    private val easings = Array(tracks.size) { tracks[it].easing }

    init {
        tracks.forEach { require(it.startMs >= 0 && it.durationMs >= 0) { "Track ${it.key}: start/duration ≥ 0" } }
        require(tracks.map { it.key }.toSet().size == tracks.size) { "Track keys must be unique" }
    }

    override val totalMs: Long = tracks.maxOfOrNull { it.startMs + it.durationMs } ?: 0L

    /** Index of the track with [key], -1 if absent (resolve once, outside the frame loop). */
    fun indexOf(key: String): Int = tracks.indexOfFirst { it.key == key }

    fun isFinished(tMs: Long) = tMs >= totalMs

    override fun evaluate(tMs: Long, out: FloatArray) {
        require(out.size >= starts.size) { "out.size ${out.size} < tracks ${starts.size}" }
        for (i in starts.indices) {
            val local = tMs - starts[i]
            val d = durations[i]
            val p = when {
                local < 0L -> 0f
                d == 0L || local >= d -> 1f
                else -> local.toFloat() / d.toFloat()
            }
            out[i] = easings[i].at(p)
        }
    }
}

/** Factories that return a [MotionTimeline] (scene sequences, staggered entrances). */
object MotionTimelines {
    /** All [keys] with the same [durationMs]/[easing], each delayed by [stagger] (index, count). */
    fun staggered(keys: List<String>, durationMs: Long, easing: Easing, stagger: Stagger, startMs: Long = 0L): TrackTimeline =
        TrackTimeline(keys.mapIndexed { i, k ->
            MotionTrack(k, startMs + Math.round(stagger.delaySec(i, keys.size) * 1000.0), durationMs, easing)
        })

    /** [keys] one after another, [gapMs] apart (negative gap = overlap, clamped so no track starts before [startMs]). */
    fun sequence(keys: List<String>, durationMs: Long, easing: Easing, gapMs: Long = 0L, startMs: Long = 0L): TrackTimeline {
        var at = startMs
        return TrackTimeline(keys.map { k -> MotionTrack(k, at, durationMs, easing).also { at = maxOf(startMs, at + durationMs + gapMs) } })
    }

    /** Explicit scene: tracks as given. */
    fun of(vararg tracks: MotionTrack): TrackTimeline = TrackTimeline(tracks.toList())

    /**
     * Muster A: scene change in Z. "scene.old" (map to scale 1 → [MotionPresets.SCENE_OLD_SCALE], alpha 1 → 0) and
     * "scene.new" (scale/alpha 0 → 1 from the centre) together over 0,8 s exponential ease-in.
     */
    fun sceneChange(startMs: Long = 0L): TrackTimeline = of(
        MotionTrack(MotionPresets.KEY_SCENE_OLD, startMs, MotionPresets.SCENE_MS, MotionPresets.sceneEasing),
        MotionTrack(MotionPresets.KEY_SCENE_NEW, startMs, MotionPresets.SCENE_MS, MotionPresets.sceneEasing),
    )

    /** Muster B: panels 0,6 s cubic ease-out (map to scale 0,8 → 1, alpha 0 → 1), centre first, 0,08 s rings. */
    fun panelsFlyIn(keys: List<String>, startMs: Long = 0L, stagger: Stagger = MotionPresets.panelStagger): TrackTimeline =
        staggered(keys, MotionPresets.FLY_IN_MS, MotionPresets.flyInEasing, stagger, startMs)

    /** Muster F: "envelope.in" 0,5 s from depth (cubic ease-out), then "envelope.flap" 0,3 s (X rotation, cubic in-out). */
    fun envelope(startMs: Long = 0L): TrackTimeline = of(
        MotionTrack(MotionPresets.KEY_ENVELOPE_IN, startMs, MotionPresets.ENVELOPE_IN_MS, Easings.easeOutCubic),
        MotionTrack(MotionPresets.KEY_ENVELOPE_FLAP, startMs + MotionPresets.ENVELOPE_IN_MS, MotionPresets.ENVELOPE_FLAP_MS, Easings.easeInOutCubic),
    )

    /** Muster G: pills on the spring (no fade), centre first. */
    fun pills(keys: List<String>, startMs: Long = 0L): TrackTimeline =
        staggered(keys, MotionPresets.pillSpring.settleMs, MotionPresets.pillSpring.asEasing(), MotionPresets.panelStagger, startMs)

    /** Widgets (Bildbeschrieb 6): 50 → 100 % on the widget spring (~0,5 s, slight overshoot), staggered left to right. */
    fun widgets(keys: List<String>, startMs: Long = 0L): TrackTimeline =
        staggered(keys, MotionPresets.widgetSpring.settleMs, MotionPresets.widgetSpring.asEasing(), Staggers.linear(), startMs)
}
