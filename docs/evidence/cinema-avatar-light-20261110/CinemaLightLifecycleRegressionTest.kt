package at.heinz.deviceai.avatar
import at.heinz.deviceai.avatar3d.CinemaWindowLightState
import at.heinz.deviceai.cockpit.CockpitAnimationEvent
import at.heinz.deviceai.cockpit.WindowProjectedMotion
import org.junit.Assert.*
import org.junit.Test
class CinemaLightLifecycleRegressionTest {
    @Test fun secondTurnReusesTheWindow() {
        var now = 1000L
        val reducer = AvatarPoseReducer()
        val director = AvatarDirector(AvatarSink { reducer.apply(it) }) { now }
        val bridge = CinemaWindowGazeBridge(director)
        CinemaWindowLightState.reset()
        bridge.render(WindowProjectedMotion("a", now, "work-document", .4f, -.2f))
        now = 1100L
        bridge.render(CockpitAnimationEvent.TurnEnded("a", now, true))
        now = 1200L
        bridge.render(WindowProjectedMotion("b", now, "work-document", -.4f, -.2f))
        assertTrue(reducer.pose(now).gazeDirected)
        assertEquals(-.4f, reducer.pose(now).gazeX, .0001f)
    }
    @Test fun lateWindowCannotRelightAnEndedTurn() {
        val bridge = CinemaWindowGazeBridge(AvatarDirector(clock = { 1000L }))
        CinemaWindowLightState.reset()
        bridge.render(WindowProjectedMotion("a", 1000L, "work-document", .4f, -.2f))
        bridge.render(CockpitAnimationEvent.TurnEnded("a", 1100L, false))
        bridge.render(WindowProjectedMotion("a", 1200L, "work-document", .8f, -.2f))
        assertFalse(CinemaWindowLightState.open)
    }
}
