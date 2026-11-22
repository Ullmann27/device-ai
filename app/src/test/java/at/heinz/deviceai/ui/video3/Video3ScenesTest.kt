package at.heinz.deviceai.ui.video3

import at.heinz.deviceai.ui.ContinuumPainter
import at.heinz.deviceai.ui.Fill
import at.heinz.deviceai.ui.TextAlign
import at.heinz.deviceai.ui.TextStyle
import at.heinz.deviceai.ui.VectorPath
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class Video3ScenesTest {
    /** Records texts, translations, scales and additive (glow) layers. */
    private class RecordingPainter : ContinuumPainter {
        val texts = mutableListOf<String>()
        val translations = mutableListOf<Pair<Float, Float>>()
        val scales = mutableListOf<Float>()
        var additiveOn = 0
        var draws = 0
        override fun save() {}
        override fun restore() {}
        override fun translate(dx: Float, dy: Float) { translations += dx to dy }
        override fun scale(sx: Float, sy: Float, px: Float, py: Float) { scales += sx }
        override fun rotate(degrees: Float, px: Float, py: Float) {}
        override fun clipRect(left: Float, top: Float, right: Float, bottom: Float) {}
        override fun clipRoundRect(left: Float, top: Float, right: Float, bottom: Float, radius: Float) {}
        override fun fillRect(left: Float, top: Float, right: Float, bottom: Float, fill: Fill, alpha: Float) { draws++ }
        override fun fillRoundRect(left: Float, top: Float, right: Float, bottom: Float, radius: Float, fill: Fill, alpha: Float) { draws++ }
        override fun strokeRoundRect(left: Float, top: Float, right: Float, bottom: Float, radius: Float, width: Float, fill: Fill, alpha: Float) { draws++ }
        override fun fillOval(left: Float, top: Float, right: Float, bottom: Float, fill: Fill, alpha: Float) { draws++ }
        override fun strokeOval(left: Float, top: Float, right: Float, bottom: Float, width: Float, fill: Fill, alpha: Float) { draws++ }
        override fun strokeArc(left: Float, top: Float, right: Float, bottom: Float, startDegrees: Float, sweepDegrees: Float, width: Float, fill: Fill, alpha: Float, roundCap: Boolean) { draws++ }
        override fun drawLine(x0: Float, y0: Float, x1: Float, y1: Float, width: Float, fill: Fill, alpha: Float, roundCap: Boolean) { draws++ }
        override fun fillPath(path: VectorPath, fill: Fill, alpha: Float) { draws++ }
        override fun strokePath(path: VectorPath, width: Float, fill: Fill, alpha: Float, roundCap: Boolean) { draws++ }
        override fun drawText(text: String, x: Float, y: Float, style: TextStyle, fill: Fill, align: TextAlign, alpha: Float) { texts += text; draws++ }
        override fun measureText(text: String, style: TextStyle) = text.length * style.size * .55f
        override fun capHeight(style: TextStyle) = style.size * .7f
        override fun setAdditive(on: Boolean) { if (on) additiveOn++ }
    }

    private fun alarm(kind: Video3AlarmKind, measured: Double? = null, threshold: Double? = null,
                      options: List<Video3ActionOption> = emptyList()) =
        Video3AlarmInfo(kind, "Sicherheitsprüfung", measured, threshold, options)

    private val option = Video3ActionOption("quarantine", "Datei prüfen", "file.inspect", true, null)

    @Test fun alarmScenePlaysOnlyForSecurityFinding() {
        val scene = Video3AlarmScene()
        for (k in Video3AlarmKind.entries) {
            val p = RecordingPainter()
            val drawn = scene.render(p, 884f, 760f, .6f, alarm(k))
            assertEquals(k == Video3AlarmKind.SECURITY_FINDING, drawn)
            if (!drawn) assertEquals(0, p.draws)
        }
    }

    @Test fun noReadableDigitsWithoutEventValues() {
        val p = RecordingPainter()
        Video3AlarmScene().render(p, 884f, 760f, 1.2f, alarm(Video3AlarmKind.SECURITY_FINDING))
        assertTrue(p.texts.isNotEmpty())
        assertTrue(p.texts.joinToString().contains(Video3AlarmScene.UNAVAILABLE))
        p.texts.forEach { assertFalse("digit in '$it'", it.any(Char::isDigit)) }
    }

    @Test fun cascadeDigitsComeOnlyFromEvent() {
        val p = RecordingPainter()
        Video3AlarmScene().render(p, 884f, 760f, 1.2f, alarm(Video3AlarmKind.SECURITY_FINDING, 12.5, 3.0))
        val digitTexts = p.texts.filter { t -> t.any(Char::isDigit) }
        assertTrue(digitTexts.isNotEmpty())
        digitTexts.forEach { t -> assertTrue(t, t.contains("12,50") || t.contains("3,00")) }
    }

    @Test fun reducedMotionHasNoShakeNoZoom() {
        val p = RecordingPainter()
        Video3AlarmScene().render(p, 884f, 760f, .3f, alarm(Video3AlarmKind.SECURITY_FINDING), Video3Options(animatorScale = 0f))
        assertTrue(p.translations.none { it.first != 0f || it.second != 0f })
        assertTrue(p.scales.all { it == 1f })
        val moving = RecordingPainter()
        Video3AlarmScene().render(moving, 884f, 760f, .3f, alarm(Video3AlarmKind.SECURITY_FINDING))
        assertTrue(moving.scales.any { it > 1f })
    }

    @Test fun withoutGlowNoAdditiveLayers() {
        val p = RecordingPainter()
        Video3AlarmScene().render(p, 884f, 760f, 1f, alarm(Video3AlarmKind.SECURITY_FINDING), Video3Options(glow = false))
        // only the 1 px light trails stay additive; no bloom, halo or glow strokes
        assertEquals(1, p.additiveOn)
        val glowing = RecordingPainter()
        Video3AlarmScene().render(glowing, 884f, 760f, 1f, alarm(Video3AlarmKind.SECURITY_FINDING))
        assertTrue(glowing.additiveOn > 3)
        val w = RecordingPainter()
        Video3WarpScene().render(w, 884f, 760f, 1.5f, Video3Options(glow = false))
        assertTrue(w.draws > 0)
    }

    @Test fun pillsOnlyWithOptionsAndNeverRunAnything() {
        val none = RecordingPainter()
        val calm = Video3CalmNotice()
        calm.render(none, 884f, 760f, 2f, Video3CalmNoticeInfo("Werkzeug", "Heinz, im System ist gerade ein Fehler aufgetreten", emptyList()))
        assertFalse(none.texts.contains(option.label))
        assertNull(calm.optionAt(442f, 500f))

        val with = RecordingPainter()
        val disabled = Video3ActionOption("retry", "Erneut versuchen", null, false, "ToolGate gesperrt")
        calm.render(with, 884f, 760f, 2f, Video3CalmNoticeInfo("Werkzeug", "Heinz, im System ist gerade ein Fehler aufgetreten",
            listOf(option, disabled)), Video3Options(animatorScale = 0f))
        assertTrue(with.texts.contains(option.label))
        assertTrue(with.texts.contains("ToolGate gesperrt"))
    }

    @Test fun calmNoticeNeverShakesOrZooms() {
        val p = RecordingPainter()
        Video3CalmNotice().render(p, 884f, 760f, .4f, Video3CalmNoticeInfo("Modell", "Heinz, im System ist gerade ein Fehler aufgetreten", emptyList()))
        assertTrue(p.scales.isEmpty())
        assertTrue(p.translations.isEmpty())
    }

    @Test fun shakeIsDeterministicAndBounded() {
        for (t in 0L..3000L step 17L) {
            assertEquals(Video3Curves.shakeX(5, t), Video3Curves.shakeX(5, t), 0f)
            assertTrue(kotlin.math.abs(Video3Curves.shakeX(5, t)) <= Video3Curves.SHAKE_PX)
            assertTrue(kotlin.math.abs(Video3Curves.shakeY(5, t)) <= Video3Curves.SHAKE_PX)
        }
    }

    @Test fun warpReducedMotionIsStill() {
        val a = RecordingPainter(); val b = RecordingPainter()
        val s = Video3WarpScene()
        s.render(a, 884f, 760f, .2f, Video3Options(animatorScale = 0f))
        s.render(b, 884f, 760f, 2.5f, Video3Options(animatorScale = 0f))
        assertEquals(a.draws, b.draws)
    }
}
