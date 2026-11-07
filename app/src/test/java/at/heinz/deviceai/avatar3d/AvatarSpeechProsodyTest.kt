package at.heinz.deviceai.avatar3d

import at.heinz.deviceai.avatar.AvatarDirector
import at.heinz.deviceai.avatar.AvatarExpression
import at.heinz.deviceai.avatar.AvatarPose
import at.heinz.deviceai.avatar.AvatarPoseReducer
import at.heinz.deviceai.avatar.AvatarRuntimeSignals
import at.heinz.deviceai.avatar.AvatarSink
import at.heinz.deviceai.avatar.AvatarState
import at.heinz.deviceai.avatar.Viseme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/**
 * Speech mimicry from real Director signals only: the spoken text, the TTS word ranges and the runtime states; no
 * invented audio. A question lifts the brows on its last word, a statement ends with a small settling nod, the
 * speaking head fades out after the last word instead of stopping in one frame, and a blink that would close inside
 * an open syllable waits for the next word boundary. Production chain AvatarDirector → AvatarPoseReducer →
 * AvatarMorphMapper; the TTS word timing is scripted here (host), not measured on a device.
 */
class AvatarSpeechProsodyTest {
    private class Scene(start: Long = 10_000L) {
        var now = start
        val reducer = AvatarPoseReducer()
        val director = AvatarDirector(AvatarSink { reducer.apply(it) }) { now }
        val mapper = AvatarMorphMapper(AvatarGlbContract.TARGETS)
        fun weight(name: String) = mapper.weights[AvatarGlbContract.TARGETS.indexOf(name)]
        fun sample(word: Int): Sample {
            mapper.update(reducer.pose(now), now / 1000f, true)
            return Sample(now, word, weight("BrowUp"), mapper.headPitch, mapper.headYaw)
        }
    }

    private data class Sample(val t: Long, val word: Int, val brow: Float, val pitch: Float, val yaw: Float)

    /** Frames at rest (IDLE) for [ms]. */
    private fun Scene.rest(ms: Long, frameMs: Long = FRAME_MS): List<Sample> {
        val out = ArrayList<Sample>()
        val until = now + ms
        while (now < until) { out += sample(-1); now += frameMs }
        return out
    }

    /**
     * Speaks [text] like AgentActivity: runtime SPEAKING, TTS onStart, one word range every [WORD_MS] (the range
     * covers the letters; trailing punctuation stays outside, as engines report it), no PCM (level 0, word-range
     * fallback), onDone and runtime IDLE after the last word, then [tailMs] more. The callbacks arrive on a fixed
     * [EVENT_MS] tick; the renderer samples every [frameMs] except inside [skipped] (a stalled frame clock).
     */
    private fun Scene.speak(text: String, tailMs: Long = 1_500, frameMs: Long = FRAME_MS,
                            skipped: LongRange = LongRange.EMPTY): List<Sample> {
        val id = "utterance-$now"
        director.onRuntime(AvatarRuntimeSignals(speaking = true))
        assertTrue(director.speechStarted(id, text))
        val words = Regex("\\S+").findAll(text).toList()
        val start = now
        val speechEnd = start + FIRST_WORD_MS + WORD_MS * words.size
        val out = ArrayList<Sample>()
        var next = 0
        var ended = false
        while (now < speechEnd + tailMs) {
            while (next < words.size && now >= start + FIRST_WORD_MS + next * WORD_MS) {
                val word = words[next]
                val letters = word.value.trimEnd('.', ',', '?', '!', ':', ';', '"')
                assertTrue(director.wordRange(id, word.range.first, word.range.first + letters.length))
                next++
            }
            if (!ended && now >= speechEnd) {
                director.speechEnded(id, interrupted = false)
                director.onRuntime(AvatarRuntimeSignals())
                ended = true
            }
            if (!ended) director.audioLevel(id, 0f, measuredPcmAvailable = false)
            if ((now - start) % frameMs == 0L && now - start !in skipped) out += sample(next - 1)
            now += EVENT_MS
        }
        return out
    }

    @Test fun aQuestionLiftsTheBrowsOnItsLastWordAndReleasesThem() {
        val text = "Ist das Cockpit bereit? Ich prüfe es jetzt"
        val question = Scene().speak(text)
        val clause = Scene().speak(text.replace('?', ','))
        val lastWordAt = question.first { it.word == 3 }.t
        val lifted = question.filter { it.t in lastWordAt..lastWordAt + 500 }.maxOf { it.brow }
        assertTrue("the brows rise on the question's last word: $lifted", lifted >= .30f)
        assertTrue("no lift before the question ends", question.filter { it.word < 3 }.all { it.brow <= SPEECH_BROW })
        assertTrue("the brows relax again", question.filter { it.t >= lastWordAt + 1_300 }.all { it.brow <= SPEECH_BROW })
        assertTrue("the same words without a question never lift the brows", clause.all { it.brow <= SPEECH_BROW })
    }

    @Test fun aStatementEndsWithASmallSettlingNodAndASoftRebound() {
        // Identical word timing and mouth; only the sentence end differs (period versus comma).
        val sentence = Scene().speak("Das Cockpit ist bereit. Ich prüfe es jetzt")
        val clause = Scene().speak("Das Cockpit ist bereit, ich prüfe es jetzt")
        val lastWordAt = sentence.first { it.word == 3 }.t
        val difference = sentence.zip(clause) { a, b -> a.t - lastWordAt to a.pitch - b.pitch }
        val dip = difference.maxBy { it.second }
        assertTrue("a small downward settle after the sentence: $dip", dip.second in .6f..1.2f)
        assertTrue("it peaks after the last word, not inside it: $dip", dip.first in 300L..900L)
        val rebound = difference.minOf { it.second }
        assertTrue("a soft rebound instead of a hard stop: $rebound", rebound in -.40f..-.08f)
        assertTrue("then the head is at rest again", difference.filter { it.first > 1_300 }.all { abs(it.second) < 1e-4f })
        assertTrue("the settle is a nod, not a turn", sentence.zip(clause).all { (a, b) -> abs(a.yaw - b.yaw) < 1e-4f })
    }

    @Test fun numbersAbbreviationsAndFileNamesAreNotSentenceEnds() {
        for ((dotted, plain) in listOf(
            "Am 6. November startet das Cockpit" to "Am 6 November startet das Cockpit",
            "Die Kurve steigt um 3.5 Grad weiter" to "Die Kurve steigt um 35 Grad weiter",
            "Das dauert ca. drei Minuten länger" to "Das dauert ca drei Minuten länger",
            "Ich öffne die Datei.txt sofort jetzt" to "Ich öffne die Dateitxt sofort jetzt")) {
            val a = Scene().speak(dotted); val b = Scene().speak(plain)
            assertTrue("no sentence gesture inside \"$dotted\"", a.zip(b).all { (x, y) ->
                abs(x.pitch - y.pitch) < 1e-4f && abs(x.brow - y.brow) < 1e-4f })
        }
    }

    @Test fun theSpeakingHeadFadesInAndOutInsteadOfSwitchingInOneFrame() {
        var worst = 0f
        var where = ""
        // Several clock phases, so the speech sway, the idle sway and the breathing meet at different values.
        for (phase in 0 until 8) {
            val scene = Scene(10_000L + phase * 530L)
            val samples = scene.rest(600) + scene.speak("Ich prüfe die Datei und starte jetzt den Test", tailMs = 1_600)
            samples.zipWithNext { a, b ->
                val step = abs(b.pitch - a.pitch) + abs(b.yaw - a.yaw)
                if (step > worst) { worst = step; where = "phase $phase at ${b.t} ms" }
            }
        }
        assertTrue("largest head step per 16 ms frame: $worst° ($where)", worst <= MAX_HEAD_STEP_DEGREES)
    }

    @Test fun prosodyDoesNotDependOnTheFrameRateAndSurvivesAStalledFrame() {
        val text = "Ist das Cockpit bereit? Das prüfe ich jetzt."
        val fast = Scene().speak(text, frameMs = 8).associateBy { it.t }
        val slow = Scene().speak(text, frameMs = 32)
        assertTrue(slow.size > 100)
        for (s in slow) {
            val f = fast.getValue(s.t)
            assertEquals("brow at ${s.t}", f.brow, s.brow, 1e-2f)
            assertEquals("pitch at ${s.t}", f.pitch, s.pitch, 8e-2f)
            assertEquals("yaw at ${s.t}", f.yaw, s.yaw, 2e-2f)
        }
        // A 400 ms render stall inside the question gesture: the brows continue on time instead of restarting.
        for (s in Scene().speak(text, skipped = 1_200L..1_600L).filter { it.t in 11_600L..12_400L }) {
            assertEquals("brow after a stall at ${s.t}", fast.getValue(s.t).brow, s.brow, 1e-2f)
        }
        // A 400 ms stall during the final settle and the fade-out lands exactly on the continuous motion.
        for (s in Scene().speak(text, skipped = 3_000L..3_400L).filter { it.t >= 13_400L }) {
            val f = fast.getValue(s.t)
            assertEquals("pitch after a stall at ${s.t}", f.pitch, s.pitch, 2e-3f)
            assertEquals("yaw after a stall at ${s.t}", f.yaw, s.yaw, 2e-3f)
        }
    }

    private fun speakingPose(viseme: Viseme, jaw: Float, state: AvatarState = AvatarState.SPEAKING) =
        AvatarPose(state, AvatarExpression.NEUTRAL, 0f, 0f, jaw, viseme, if (viseme == Viseme.SIL) 0f else 1f, jaw)

    /** Lid weight per 16 ms frame from 4.30 s: an open AA (real cue, jaw .6) until [openUntil] s, then a closed mouth. */
    private fun lids(openUntil: Float, state: AvatarState = AvatarState.SPEAKING): List<Pair<Float, Float>> {
        val mapper = AvatarMorphMapper(AvatarGlbContract.TARGETS)
        val blink = AvatarGlbContract.TARGETS.indexOf("BlinkLeft")
        return (0..60).map { frame ->
            val t = 4.30f + frame * .016f
            mapper.update(if (t < openUntil) speakingPose(Viseme.AA, .6f, state) else speakingPose(Viseme.SIL, 0f, state), t, true)
            t to mapper.weights[blink]
        }
    }

    @Test fun aBlinkWaitsForTheNextWordBoundaryInsteadOfClosingInAnOpenSyllable() {
        // The view clock schedules its first blink inside 4.45–4.60 s (AvatarIdleMotion). The open syllable spans it.
        val boundary = lids(openUntil = 4.62f)
        assertTrue("the lid stays open during the open syllable",
            boundary.filter { it.first < 4.62f }.all { it.second == 0f })
        val after = boundary.filter { it.first >= 4.62f && it.first <= 4.62f + .30f }
        assertEquals("a complete blink follows at the word boundary", 1f, after.maxOf { it.second }, 0f)
        // A blink is deferred, never dropped: with the mouth open for longer it still comes within the bound.
        val held = lids(openUntil = 9f)
        val closedAt = held.firstOrNull { it.second == 1f }?.first
        assertTrue("a long open stretch still blinks: $closedAt", closedAt != null && closedAt <= 4.45f + .45f + .10f)
        // Outside speech the lids keep the view clock's schedule exactly.
        val idle = AvatarIdleMotion()
        for ((t, lid) in lids(openUntil = 4.62f, state = AvatarState.LISTENING)) {
            idle.update(t)
            assertEquals("listening blink at $t", idle.blinkWeight, lid, 0f)
        }
    }

    private companion object {
        const val FRAME_MS = 16L
        const val EVENT_MS = 8L
        /** Callback times sit 8 ms after a 32 ms frame, so 8, 16 and 32 ms renderers see each change in the same interval. */
        const val FIRST_WORD_MS = 104L
        const val WORD_MS = 352L
        /** The existing open-syllable brow accent while speaking (AvatarMorphMapper.SPEECH_BROW). */
        const val SPEECH_BROW = .14f + 1e-4f
        /** Normal speech and idle motion move the head well below this per 16 ms frame; a hard switch exceeds it. */
        const val MAX_HEAD_STEP_DEGREES = .50f
    }
}
