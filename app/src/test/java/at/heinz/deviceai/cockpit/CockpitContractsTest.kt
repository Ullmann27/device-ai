package at.heinz.deviceai.cockpit

import android.graphics.Rect
import android.view.ViewGroup
import at.heinz.deviceai.avatar.AvatarState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Compile-level checks of the bot contracts plus the ONE area-change trigger. */
class CockpitContractsTest {
    @Test fun contractsAreImplementableByTheOwningBots() {
        val avatar = object : JarvisAvatarRenderer {
            override fun attach(host: ViewGroup, slot: Rect) = Unit
            override fun onAvatarState(state: AvatarState) = Unit
            override fun onAnimationEvent(event: CockpitAnimationEvent) = Unit
            override fun detach() = Unit
        }
        JarvisAvatarSlot.register(avatar); assertTrue(JarvisAvatarSlot.renderer === avatar)
        JarvisAvatarSlot.register(null); assertNull(JarvisAvatarSlot.renderer)
        val calc: CalculationEventSource = object : CalculationEventSource {
            override fun addListener(l: (CockpitAnimationEvent.CalculationShown) -> Unit) = AutoCloseable { }
        }
        calc.addListener { }.close()
        val nav = object : JarvisNavigator { override fun navigate(target: String) = Unit }
        nav.navigate("chat")
        val handler = object : CockpitActionHandler {
            override fun availability(action: CockpitAction) = ActionAvailability(false, "Quelle fehlt")
            override fun perform(action: CockpitAction, turnId: String) = Unit
        }
        assertEquals(false, handler.availability(CockpitAction.VERIFY).enabled)
        val gate = object : PillActionGate {
            override fun pillsFor(event: CockpitAnimationEvent.DocumentQuestion) =
                listOf(AnswerPill(AnswerPillKind.ANRUFEN, null, false, "Quelle fehlt"))
            override fun request(pill: AnswerPill, turnId: String, onDecision: (approvedByOwner: Boolean) -> Unit) = onDecision(false)
        }
        var decision: Boolean? = null
        val pill = gate.pillsFor(CockpitAnimationEvent.DocumentQuestion("t", 0L, "a.pdf", "application/pdf", "?")).single()
        gate.request(pill, "t") { decision = it }
        assertEquals(false, decision)
        val account: AccountEventSource = object : AccountEventSource {
            override fun addListener(l: (CockpitAnimationEvent.AccountCardFlyIn) -> Unit) = AutoCloseable { }
        }
        account.addListener { }.close()
        CockpitAnimationEvent.CalculationShown("t", 0L, "2+2", listOf(CalcStep("a", 2.0)), 4.0, null, ChartKind.NONE)
    }

    @Test fun areaChangedIsEmittedOnTheBusAndBlankTargetsAreIgnored() {
        val bus = CockpitAnimationBus()
        val seen = mutableListOf<CockpitAnimationEvent>()
        bus.subscribe { seen += it }
        val triggers = CockpitAnimationTriggers(bus) { 7L }
        triggers.areaChanged("home", "memory", TransitionPattern.B_FLY_IN)
        triggers.areaChanged(null, "logic", TransitionPattern.A_DEPTH)
        triggers.areaChanged("logic", "  ", TransitionPattern.A_DEPTH)
        assertEquals(listOf(
            CockpitAnimationEvent.AreaChanged(NAV_TURN, 7L, "home", "memory", TransitionPattern.B_FLY_IN),
            CockpitAnimationEvent.AreaChanged(NAV_TURN, 7L, null, "logic", TransitionPattern.A_DEPTH)), seen)
    }
}
