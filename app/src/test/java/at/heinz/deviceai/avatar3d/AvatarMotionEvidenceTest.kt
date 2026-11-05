package at.heinz.deviceai.avatar3d

import at.heinz.deviceai.avatar.AvatarDirector
import at.heinz.deviceai.avatar.AvatarPoseReducer
import at.heinz.deviceai.avatar.AvatarRuntimeSignals
import at.heinz.deviceai.avatar.AvatarState
import at.heinz.deviceai.avatar.GazeTarget
import at.heinz.deviceai.avatar.Viseme
import at.heinz.deviceai.ui.JarvisShellLayout
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * One continuous conversation scene through the production chain AvatarDirector → AvatarPoseReducer →
 * AvatarMorphMapper (same calls as AgentActivity: runtime flags, TTS start, word ranges, no-PCM audio callbacks,
 * STOP). The test asserts the behaviour; with `-PcontinuumUiEvidenceDir=…` it also writes the per-frame mapper
 * output and the real head windows for the host 3D render (tools/avatar-host-render). Simulated inputs are labelled:
 * the owner's camera gaze and the TTS word timing are scripted here, not measured on a device.
 */
class AvatarMotionEvidenceTest {
    private class Scene {
        var now = 0L
        val reducer = AvatarPoseReducer()
        val director = AvatarDirector(at.heinz.deviceai.avatar.AvatarSink { reducer.apply(it) }, { now })
        val mapper = AvatarMorphMapper(AvatarGlbContract.TARGETS)
        val frames = JSONArray()
        val marks = JSONArray()
        fun mark(label: String) { marks.put(JSONObject().put("t", now / 1000.0).put("label", label)) }
        fun frame(phase: String) {
            val pose = reducer.pose(now)
            mapper.update(pose, now / 1000f, true)
            frames.put(JSONObject().put("t", now / 1000.0).put("phase", phase).put("state", pose.state.name)
                .put("viseme", pose.viseme.name)
                .put("w", JSONArray(mapper.weights.map { it.toDouble() }))
                .put("headTotal", JSONArray(listOf(mapper.headPitch.toDouble(), mapper.headYaw.toDouble())))
                .put("neck", JSONArray(listOf(mapper.neckPitch.toDouble(), mapper.neckYaw.toDouble())))
                .put("eye", JSONArray(listOf(mapper.eyePitch.toDouble(), mapper.eyeYaw.toDouble())))
                .put("jaw", mapper.jawPitch.toDouble()).put("speak", pose.speakEnergy.toDouble()))
        }
    }

    private fun weight(s: Scene, name: String) = s.mapper.weights[AvatarGlbContract.TARGETS.indexOf(name)]
    private fun mouth(s: Scene) = listOf("PP", "FF", "TH", "DD", "KK", "CH", "SS", "NN", "RR", "AA", "E", "I", "O", "U", "JawOpen")
        .maxOf { weight(s, it) }

    /** 30 fps until [untilMs]; [each] runs before every frame (TTS/audio callbacks at their own cadence). */
    private fun Scene.run(untilMs: Long, phase: String, each: (Long) -> Unit = {}) {
        while (now < untilMs) { each(now); frame(phase); now += 33 }
    }

    private fun Scene.speak(id: String, text: String, wordsToSay: Int = Int.MAX_VALUE, phase: String) {
        assertTrue(director.speechStarted(id, text))
        mark("TTS onStart · $id")
        val words = Regex("\\S+").findAll(text).toList()
        var next = 0
        var nextAt = now + 120
        val end = now + 340L * minOf(words.size, wordsToSay) + 260
        run(end, phase) { t ->
            if (next < minOf(words.size, wordsToSay) && t >= nextAt) {
                director.wordRange(id, words[next].range.first, words[next].range.last + 1); next++; nextAt += 340
            }
            // Engine without PCM: the app reports level 0 with measuredPcmAvailable = false (word-range fallback).
            director.audioLevel(id, 0f, measuredPcmAvailable = false)
        }
    }

    @Test fun conversationSceneDrivesEyesHeadMouthAndStopsCleanly() {
        val s = Scene()
        s.mark("Ruhe · Blinzeln, Sakkaden, Atmung")
        s.run(3_000, "ruhe")
        s.director.onRuntime(AvatarRuntimeSignals(listening = true)); s.mark("Zuhören (Mikrofon aktiv)")
        s.run(3_400, "zuhoeren")
        // Scripted owner position (camera input is simulated in this host scene).
        val eyeBefore = s.mapper.eyeYaw
        s.director.onObservedGaze(GazeTarget(-.8f, .1f), 90, 0); s.mark("Besitzer links (simulierte Kamera)")
        s.frame("zuhoeren")
        assertTrue("eyes lead immediately", kotlin.math.abs(s.mapper.eyeYaw - eyeBefore) > 8f)
        assertTrue("head lags behind the eyes", kotlin.math.abs(s.mapper.headYaw) < kotlin.math.abs(-.8f * AvatarMorphMapper.HEAD_YAW_DEGREES) * .4f)
        s.now += 33
        s.run(4_600, "zuhoeren") { t -> if (t % 330 < 33) s.director.onObservedGaze(GazeTarget(-.8f, .1f), 90, 0) }
        s.mark("Besitzer rechts (simulierte Kamera)")
        s.run(5_800, "zuhoeren") { t -> if (t % 330 < 33) s.director.onObservedGaze(GazeTarget(.7f, -.15f), 90, 0) }
        s.director.clearObservedGaze()
        s.director.onRuntime(AvatarRuntimeSignals(busy = true)); s.mark("Verarbeiten (Denken)")
        s.run(7_800, "denken")
        s.director.onRuntime(AvatarRuntimeSignals(speaking = true)); s.mark("Sprechen (TTS-Wortbereiche)")
        var opened = 0f; var closedPp = false
        val before = s.frames.length()
        s.speak("u1", "Guten Abend Heinz, das Cockpit ist bereit. Bitte sag mir, was ich tun soll.", phase = "sprechen")
        for (i in before until s.frames.length()) {
            val f = s.frames.getJSONObject(i); val w = f.getJSONArray("w")
            val open = (0 until w.length()).filter { AvatarGlbContract.TARGETS[it] !in listOf("SIL", "BlinkLeft", "BlinkRight", "BrowUp", "BrowDown", "Smile") }
                .maxOf { w.getDouble(it) }.toFloat()
            opened = maxOf(opened, open)
            if (f.getString("viseme") == Viseme.PP.name) closedPp = closedPp || w.getDouble(AvatarGlbContract.TARGETS.indexOf("JawOpen")) == 0.0
        }
        assertTrue("speech visibly opens the mouth ($opened)", opened > .5f)
        assertTrue("M/B/P frames close the lips", closedPp)
        s.director.speechEnded("u1", interrupted = false)
        s.mark("TTS onDone · Lippen schließen")
        s.run(s.now + 700, "ende")
        assertEquals("mouth closed after the utterance", 0f, mouth(s), 1e-6f)
        // Second answer, interrupted by STOP after two words; late engine callbacks must not reopen the mouth.
        s.speak("u2", "Ich öffne jetzt die Nachrichtenübersicht für dich", wordsToSay = 2, phase = "sprechen")
        s.director.speechEnded("u2", interrupted = true); s.mark("STOP · Sprache abgebrochen")
        s.director.onRuntime(AvatarRuntimeSignals())
        s.run(s.now + 200, "stop")
        assertTrue("late word range is rejected", !s.director.wordRange("u2", 0, 3))
        s.director.audioLevel("u2", .9f, measuredPcmAvailable = true); s.mark("verspätete TTS-Ereignisse (verworfen)")
        s.run(s.now + 1_400, "stop")
        assertEquals("STOP keeps the lips closed despite late callbacks", 0f, mouth(s), 1e-6f)
        assertEquals(AvatarState.IDLE, s.reducer.pose(s.now).state)
        s.mark("Ruhe")
        s.run(s.now + 1_600, "ruhe")
        export(s)
    }

    private fun window(w: AvatarFraming.Window) = JSONObject().put("left", w.left).put("right", w.right)
        .put("halfHeight", w.halfHeight).put("cameraCenterY", w.cameraCenterY).put("headScale", w.headScale).put("anchorX", w.anchorX)

    private fun export(s: Scene) {
        val dir = System.getProperty("continuum.ui.evidenceDir") ?: return
        val focus = AvatarGlbContract.validate(listOf(File("src/main/assets/avatar3d/continuum-avatar.glb"),
            File("app/src/main/assets/avatar3d/continuum-avatar.glb")).first { it.isFile }.readBytes()).focus
        val stages = JSONObject()
        for ((w, h) in listOf(884f to 760f, 1536f to 1024f, 840f to 649f)) {
            val shell = JarvisShellLayout.create(w, h); val stage = shell.headStage ?: continue
            val dialog = requireNotNull(shell[JarvisShellLayout.Slot.DIALOG])
            val aspect = stage.width.toDouble() / stage.height
            val keep = AvatarFraming.KeepOut(((dialog.right + 6f - stage.left) / stage.width).toDouble(),
                ((dialog.top - 6f - stage.top) / stage.height).toDouble())
            val full = focus.verticalHalfSpan(aspect); val v = full * shell.headCloseUp; val hz = v * aspect
            val a = shell.headAnchorX.toDouble()
            stages.put("${w.toInt()}x${h.toInt()}", JSONObject()
                .put("stage", JSONArray(listOf(stage.left, stage.top, stage.width, stage.height)))
                .put("dialog", JSONArray(listOf(dialog.left - stage.left, dialog.top - stage.top, dialog.right - stage.left, dialog.bottom - stage.top)))
                .put("former", window(AvatarFraming.Window(-2 * hz * a, 2 * hz * (1 - a), v, focus.centerY + (full - v), 1.0, a)))
                .put("fitted", window(AvatarFraming.window(focus, aspect, a, shell.headCloseUp.toDouble(), keep))))
        }
        val out = JSONObject().put("source", "AvatarDirector → AvatarPoseReducer → AvatarMorphMapper (host JVM, 30 fps)")
            .put("simulated", "Kamera-Blickposition und TTS-Worttakt sind im Host-Szenario geskriptet; kein Gerät, kein PCM")
            .put("targets", JSONArray(AvatarGlbContract.TARGETS)).put("neckShare", AvatarMorphMapper.NECK_SHARE.toDouble())
            .put("focus", JSONObject().put("centerX", focus.centerX).put("centerY", focus.centerY).put("centerZ", focus.centerZ)
                .put("reachLeft", focus.reachLeft).put("reachRight", focus.reachRight))
            .put("stages", stages).put("marks", s.marks).put("frames", s.frames)
        File(dir).apply { mkdirs() }.resolve("avatar-motion-timeline.json").writeText(out.toString())
    }
}
