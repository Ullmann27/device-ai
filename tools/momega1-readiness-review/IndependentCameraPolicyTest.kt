package at.heinz.deviceai.cognitive

import org.junit.Assert.*
import org.junit.Test

class IndependentCameraPolicyTest {
    private val geometry=CameraFrameGeometry(0,0,1000,1000,0,0)
    private val face=CameraFaceBox(600,400,800,600,90)
    @Test fun missingCaptureTimestampCannotManufactureCurrentObservation() {
        val p=AvatarCameraPolicy {1000L};val t=p.start(true,true,true,true)!!
        assertNull("Unknown capture time is not current camera evidence",p.acceptFrame(t,listOf(face),geometry,null))
        assertNull(p.latest())
    }
    @Test fun exactFreshnessBoundaryAndNoRenewalOnArrival() {
        var now=1000L;val p=AvatarCameraPolicy {now};val t=p.start(true,true,true,true)!!
        assertNotNull(p.acceptFrame(t,listOf(face),geometry,250L))
        now=1001;assertNull(p.latest())
        assertNull(p.acceptFrame(t,listOf(face),geometry,250L))
        now=6000;assertTrue(p.expireHeartbeat(t))
    }
    @Test fun futureAndNegativeCaptureTimesDoNotKeepHeartbeatAlive() {
        var now=1000L;val p=AvatarCameraPolicy {now};val t=p.start(true,true,true,true)!!
        for(time in listOf(-1L,1001L,Long.MAX_VALUE))assertNull(p.acceptFrame(t,listOf(face),geometry,time))
        now=6000;assertTrue(p.expireHeartbeat(t))
    }
    @Test fun repeatedStaleCaptureCannotExtendFiveSecondHeartbeat() {
        var now=1000L;val p=AvatarCameraPolicy {now};val t=p.start(true,true,true,true)!!
        for(i in 1..49){now=1000L+i*100;assertNull(p.acceptFrame(t,listOf(face),geometry,0L))}
        now=6000;assertTrue(p.expireHeartbeat(t))
    }
    @Test fun oldGenerationCannotClearOrReplaceNewRunObservation() {
        var now=1000L;val p=AvatarCameraPolicy {now};val old=p.start(true,true,true,true)!!
        p.stop();val current=p.start(true,true,true,true)!!
        val sample=p.acceptFrame(current,listOf(face),geometry,now)
        assertNull(p.acceptFrame(old,emptyList(),geometry,now))
        assertFalse(p.updateGate(old,false,false,false));assertEquals(sample,p.latest())
    }
    @Test fun permissionLossIsStickyUntilAnExplicitNewStart() {
        val p=AvatarCameraPolicy {1000L};val t=p.start(true,true,true,true)!!
        assertFalse(p.updateGate(t,true,true,false))
        assertFalse(p.updateGate(t,true,true,true))
        assertNull(p.acceptFrame(t,listOf(face),geometry,1000))
        assertEquals(CameraTrackingState.PERMISSION_REQUIRED,p.state)
    }
    @Test fun multipleFacesAndLowConfidenceClearExistingSample() {
        val p=AvatarCameraPolicy {1000L};val t=p.start(true,true,true,true)!!
        assertNotNull(p.acceptFrame(t,listOf(face),geometry,1000))
        assertNull(p.acceptFrame(t,listOf(face,face),geometry,1000));assertNull(p.latest())
        assertEquals(CameraTrackingState.AMBIGUOUS,p.state)
        assertNull(p.acceptFrame(t,listOf(face.copy(score=59)),geometry,1000));assertNull(p.latest())
    }
    @Test fun integerOverflowGeometryAndUnsupportedRotationAreRejected() {
        for(g in listOf(geometry.copy(left=Int.MIN_VALUE,right=Int.MAX_VALUE),geometry.copy(right=0),
            geometry.copy(sensorOrientationDegrees=45),geometry.copy(displayRotationDegrees=360)))
            assertNull(CameraCoordinateMapping.normalized(face,g))
        assertNull(CameraCoordinateMapping.normalized(face.copy(left=-1),geometry))
    }
    @Test fun allCardinalTransformsStayFiniteMirroredAndWithinBounds() {
        for(sensor in listOf(0,90,180,270)) for(display in listOf(0,90,180,270)) {
            val pair=CameraCoordinateMapping.normalized(face,geometry.copy(sensorOrientationDegrees=sensor,displayRotationDegrees=display))!!
            assertTrue(pair.first.isFinite()&&pair.second.isFinite())
            assertTrue(pair.first in -1f..1f && pair.second in -1f..1f)
            assertEquals(0.4f,kotlin.math.abs(pair.first)+kotlin.math.abs(pair.second),0.0001f)
        }
        assertTrue(CameraCoordinateMapping.normalized(face,geometry)!!.first<0)
    }
    @Test fun rapidAndOutOfOrderFramesCannotRefreshSampleTimestampOrAccelerateMotion() {
        var now=1000L;val p=AvatarCameraPolicy {now};val t=p.start(true,true,true,true)!!
        val a=p.acceptFrame(t,listOf(face),geometry,1000)!!
        now=1049;assertEquals(a,p.acceptFrame(t,listOf(face.copy(left=0,right=100)),geometry,1049))
        assertEquals(a,p.acceptFrame(t,listOf(face),geometry,990))
        now=1050;val b=p.acceptFrame(t,listOf(face),geometry,1050)!!
        assertTrue(kotlin.math.abs(b.x-a.x)<=0.10001f)
        assertEquals(1050,b.observedAtElapsedMillis)
    }
}
