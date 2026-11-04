package at.heinz.deviceai.cockpit

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CockpitTimelineTest {
    @Test fun scene1OrderAndTimingFollowTheSpec() {
        val boot = CockpitTimeline.boot(CockpitAnimationEvent.BootStarted(BOOT_TURN, 0L, CockpitTimeline.GREETING_TEXT))
        assertEquals(listOf(Cue.AVATAR_FROM_DEPTH, Cue.GREETING, Cue.AVATAR_SHRINK, Cue.COCKPIT_FLY_IN), boot.map { it.cue })
        assertEquals(0L, boot[0].atMs); assertEquals(2_000L, boot[0].durationMs)
        assertEquals(3_000L, boot[3].atMs)
        assertEquals(600L + 4 * 80L, boot[3].durationMs)
        assertEquals(boot.maxOf { it.endMs }, CockpitTimeline.bootEndMs)
        assertEquals("Hallo Heinz, ich fahre die Systeme hoch.", CockpitTimeline.GREETING_TEXT)
        assertEquals(CueOwner.AVATAR, Cue.GREETING.owner)
        assertEquals(CueOwner.INTEGRATION, Cue.COCKPIT_FLY_IN.owner)
    }

    @Test fun bootIsEmittedOncePerProcessAndCockpitEnteredOncePerSession() {
        resetBootForTest()
        val bus = CockpitAnimationBus(); val seen = mutableListOf<CockpitAnimationEvent>(); bus.subscribe { seen += it }
        val t = CockpitAnimationTriggers(bus) { 5L }
        assertTrue(t.bootOnce()); assertTrue(!t.bootOnce())
        assertEquals(1, seen.count { it is CockpitAnimationEvent.BootStarted })
        val first = CockpitTimeline.cockpitEntered("s-1", 9L)
        assertNotNull(first); assertEquals("s-1", first!!.sessionId)
        assertNull(CockpitTimeline.cockpitEntered("s-1", 10L))
        assertEquals(listOf(Cue.HEAD_ICON_BURST), CockpitTimeline.steps(first).map { it.cue })
    }

    @Test fun onlyASecurityFindingStartsTheAlarmScene() {
        for (kind in AlarmKind.values()) {
            val cue = CockpitTimeline.steps(CockpitAnimationEvent.AlarmRaised(ALARM_TURN, 0L, kind, "x", null, null)).single().cue
            assertEquals(kind.name, if (kind == AlarmKind.SECURITY_FINDING) Cue.ALARM_ON else Cue.STATUS_WARNING, cue)
        }
    }

    @Test fun everyEventMapsToOwnerCuesAndDocumentFlowIsHeadThenOrb() {
        val doc = CockpitTimeline.steps(CockpitAnimationEvent.DocumentLearned(MEMORY_TURN, 0L, "d", "ARCHIVE_IMPORT", 2))
        assertEquals(listOf(Cue.FILE_ICONS_FROM_HEAD, Cue.DOCUMENT_INTO_ORB), doc.map { it.cue })
        assertTrue(doc[1].atMs > doc[0].atMs)
        assertTrue(CockpitTimeline.steps(CockpitAnimationEvent.RecallHits(MEMORY_TURN, 0L, "q", 0, null)).isEmpty())
        assertEquals(Cue.AREA_WARP, CockpitTimeline.steps(
            CockpitAnimationEvent.AreaChanged(NAV_TURN, 0L, null, "x", TransitionPattern.C_WARP)).single().cue)
    }
}
