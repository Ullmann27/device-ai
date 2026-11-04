package at.heinz.deviceai.ui

import android.app.Activity
import android.graphics.Bitmap
import android.graphics.Canvas
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Real Android Canvas frames of the fly-in sequence with synthetic triggers (no app/runtime claims). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class JarvisSequenceEffectsUiTest {
    private val activity = Robolectric.buildActivity(Activity::class.java).setup().get()

    private fun frame(fx: JarvisSequenceEffects, name: String, now: Long): Bitmap {
        val d = 2f; val w = 412; val h = 600
        val bmp = Bitmap.createBitmap((w * d).toInt(), (h * d).toInt(), Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp); canvas.drawColor(0xff050817.toInt())
        val p = AndroidContinuumPainter(activity.assets); p.begin(canvas); canvas.scale(d, d)
        fx.draw(p, w.toFloat(), h.toFloat(), now)
        System.getProperty("continuum.ui.evidenceDir")?.let { path ->
            val out = java.io.File(path).apply { mkdirs() }
            java.io.File(out, "$name.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
        return bmp
    }

    @Test fun commandFliesInThenCardsStackWithReportedOutcomes() {
        val fx = JarvisSequenceEffects()
        fx.command("Suche meine letzte Rechnung in Gmail", 1000)
        val chip = frame(fx, "seq-1-command-flying", 1250)
        val ring = frame(fx, "seq-2-command-arrival", 1700)
        assertFalse(chip.sameAs(ring))
        assertTrue(fx.step("s1", "Gmail wird geöffnet", 1800))
        assertTrue(fx.card("acc", JarvisSequenceEffects.Kind.ACCOUNT, "Gmail-Konto", "vom Nutzer verbunden", 1900))
        frame(fx, "seq-3-cards-flying-in", 2100)
        assertTrue(fx.stepFinished("s1", ok = true, 2600))
        assertFalse("outcome is reported once", fx.stepFinished("s1", ok = false, 2650))
        fx.card("pdf", JarvisSequenceEffects.Kind.DOCUMENT, "Rechnung.pdf", "Soll ich sie zusammenfassen?", 2700)
        fx.step("s2", "Zusammenfassung", 2800)
        fx.stepFinished("s2", ok = false, 3600)
        val stacked = frame(fx, "seq-4-stacked-outcomes", 4200)
        assertEquals(4, fx.cardCount)
        fx.result("r", "Ergebnis bereit", null, 4300)
        assertEquals("oldest card leaves, max 4 stay", JarvisSequenceEffects.MAX_CARDS, fx.cardCount)
        assertTrue(fx.dismiss("acc", 4400))
        frame(fx, "seq-5-result-and-dismiss", 4600)
        fx.clear()
        assertEquals(0, fx.cardCount)
        assertFalse(fx.isAnimating(5000))
        val empty = frame(fx, "seq-6-cleared", 5000)
        assertFalse(stacked.sameAs(empty))
        fx.dataFlight(5, 6000)
        val mid = frame(fx, "seq-7-data-flight-into-orb", 6450)
        assertTrue(fx.isAnimating(6450))
        assertFalse(mid.sameAs(empty))
        assertFalse(fx.isAnimating(9000))
        // brain orb with lightning neurons (existing orb renderer), thinking state
        val orb = ContinuumHologramRenderer().apply { geometry = ContinuumHologramRenderer.Geometry.PHONE }
        val d = 2f
        val a = Bitmap.createBitmap(824, 614, Bitmap.Config.ARGB_8888); val c = Canvas(a)
        val p = AndroidContinuumPainter(activity.assets); p.begin(c); c.scale(d, d)
        orb.layout(412f, 307f)
        orb.draw(p, ContinuumHologramRenderer.Frame.still(ContinuumHologramRenderer.Mode.THINKING, phase = 1.2f))
        System.getProperty("continuum.ui.evidenceDir")?.let { path ->
            java.io.File(path, "orb-lightning-thinking.png").outputStream().use { a.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
        assertFalse("invalid ids are refused", fx.card("bad id with spaces", JarvisSequenceEffects.Kind.INFO, "x", null, 5100))
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class JarvisSequenceViewDockingUiTest {
    @Test fun cockpitEventsDriveTheSequenceAndStaleTurnsAreDropped() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        val view = JarvisSequenceView(activity)
        var now = 1000L
        view.clock = { now }
        val bus = at.heinz.deviceai.cockpit.CockpitAnimationBus()
        bus.subscribe(view)
        bus.emit(at.heinz.deviceai.cockpit.CockpitAnimationEvent.CommandFlyIn("t1", 1, "Mail prüfen"))
        bus.emit(at.heinz.deviceai.cockpit.CockpitAnimationEvent.ProcessTimeline("t1", 2, listOf(
            at.heinz.deviceai.cockpit.ProcessStep("k1", "Gmail lesen", at.heinz.deviceai.cockpit.StepState.ACTIVE))))
        bus.emit(at.heinz.deviceai.cockpit.CockpitAnimationEvent.AccountCardFlyIn("t1", 3, "Gmail", "Konto"))
        bus.emit(at.heinz.deviceai.cockpit.CockpitAnimationEvent.AccountCardFlyIn("old", 3, "Gmail", "stale"))
        assertEquals(2, view.effects.cardCount)
        now = 1500
        bus.emit(at.heinz.deviceai.cockpit.CockpitAnimationEvent.ProcessTimeline("t1", 4, listOf(
            at.heinz.deviceai.cockpit.ProcessStep("k1", "Gmail lesen", at.heinz.deviceai.cockpit.StepState.DONE))))
        assertEquals(2, view.effects.cardCount)
        bus.emit(at.heinz.deviceai.cockpit.CockpitAnimationEvent.TurnEnded("t1", 5, true))
        assertEquals(0, view.effects.cardCount)
    }
}
