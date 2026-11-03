package at.heinz.deviceai.avatar3d

import at.heinz.deviceai.avatar.*
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

class Avatar3dContractTest {
    private fun pose(v: Viseme = Viseme.SIL, jaw: Float = 0f, x: Float = 0f, y: Float = 0f) =
        AvatarPose(AvatarState.SPEAKING, AvatarExpression.NEUTRAL, x, y, jaw, v, 1f, jaw)
    @Test fun allVisemesUseNamesEvenWhenAssetOrderChanges() {
        val names = AvatarGlbContract.TARGETS.reversed(); val map = AvatarMorphMapper(names)
        for (v in Viseme.entries) {
            map.update(pose(v, .4f), 0f, true)
            for (other in Viseme.entries) assertEquals(if (other == v && v != Viseme.SIL) 1f else 0f, map.weights[names.indexOf(other.name)], 0f)
            assertEquals(.4f, map.weights[names.indexOf("JawOpen")], 0f)
        }
    }
    @Test fun gazeIsViewerRightAndUpAndNeverNaN() {
        val map = AvatarMorphMapper(AvatarGlbContract.TARGETS)
        map.update(pose(x=1f,y=1f), 0f, true)
        assertEquals(25f, map.headYaw + map.eyeYaw, 0f); assertEquals(-15f, map.headPitch + map.eyePitch, 0f)
        map.update(pose(x=Float.NaN,y=Float.POSITIVE_INFINITY, jaw=Float.NaN), Float.NaN, true)
        assertTrue(map.weights.all { it.isFinite() && it in 0f..1f }); assertEquals(0f,map.headYaw,0f)
    }
    @Test fun reducedMotionClosesSpeechAndBlink() {
        val map = AvatarMorphMapper(AvatarGlbContract.TARGETS)
        map.update(pose(Viseme.AA,1f),4.51f,true); assertTrue(map.weights.sum()>2f)
        map.update(pose(Viseme.AA,1f),4.51f,false); assertTrue(map.weights.all { it == 0f }); assertEquals(0f,map.jawPitch,0f)
    }
    @Test fun interruptionAndOldUtteranceUseExistingReducer() {
        val reducer = AvatarPoseReducer(); val map = AvatarMorphMapper(AvatarGlbContract.TARGETS)
        reducer.apply(AvatarEvent.SpeechStarted("old","not retained by mapper",0))
        reducer.apply(AvatarEvent.AudioLevel("old",1f,1))
        reducer.apply(AvatarEvent.VisemeCue("old",Viseme.AA,1f,200,1))
        map.update(reducer.pose(1),0f,true); assertTrue(map.weights.sum()>0)
        reducer.apply(AvatarEvent.SpeechEnded("old",true,2))
        reducer.apply(AvatarEvent.AudioLevel("old",1f,3)); reducer.apply(AvatarEvent.VisemeCue("old",Viseme.O,1f,200,3))
        map.update(reducer.pose(3),0f,true); assertTrue(map.weights.all { it == 0f })
        reducer.apply(AvatarEvent.SpeechStarted("new","new",4)); reducer.apply(AvatarEvent.AudioLevel("new",1f,5))
        reducer.apply(AvatarEvent.SpeechEnded("old",true,6)); map.update(reducer.pose(6),0f,true)
        assertTrue(map.weights[AvatarGlbContract.TARGETS.indexOf("JawOpen")] > 0)
        reducer.clearSpeech(); map.update(reducer.pose(7),0f,true); assertTrue(map.weights.all { it==0f })
    }
    @Test fun validBinaryMetadataAndMissingTruncatedInvalidAssets() {
        assertEquals(21,AvatarGlbContract.validate(asset()).targets.size)
        assertRejected(asset().copyOf(20)); assertRejected(asset().also { it[0]=0 })
        assertRejected(asset { it.getJSONArray("buffers").getJSONObject(0).put("uri","https://invalid.test/data") })
        assertRejected(asset { it.getJSONArray("nodes").getJSONObject(3).put("name","Head") })
        assertRejected(asset { it.getJSONArray("meshes").getJSONObject(0).getJSONObject("extras").getJSONArray("targetNames").put(0,"PP") })
        assertRejected(asset { it.getJSONArray("accessors").getJSONObject(0).put("count",999999) })
        assertRejected(asset { it.getJSONArray("accessors").getJSONObject(0).put("sparse",JSONObject()) })
        assertRejected(asset { it.getJSONArray("nodes").getJSONObject(2).put("children",JSONArray(listOf(0,3,4,5))) })
    }
    @Test fun binaryNonFiniteAndNegativeScaleRejected() {
        assertRejected(asset(nonFinite = true))
        assertRejected(asset { it.getJSONArray("nodes").getJSONObject(2).put("scale",JSONArray(listOf(1,-1,1))) })
    }
    private fun assertRejected(bytes: ByteArray) {
        try { AvatarGlbContract.validate(bytes); fail("invalid asset accepted") } catch (_: IllegalArgumentException) { } catch (_: org.json.JSONException) { }
    }
    private fun asset(nonFinite: Boolean = false, mutate: (JSONObject)->Unit = {}): ByteArray {
        val root = JSONObject("""{"asset":{"version":"2.0"},"buffers":[{"byteLength":80}],"bufferViews":[{"buffer":0,"byteOffset":0,"byteLength":36},{"buffer":0,"byteOffset":36,"byteLength":36},{"buffer":0,"byteOffset":72,"byteLength":6}],"accessors":[{"bufferView":0,"componentType":5126,"type":"VEC3","count":3},{"bufferView":1,"componentType":5126,"type":"VEC3","count":3},{"bufferView":2,"componentType":5123,"type":"SCALAR","count":3}],"nodes":[{"name":"AvatarRoot","children":[1,6]},{"name":"Neck","children":[2]},{"name":"Head","children":[3,4,5]},{"name":"EyeLeft","translation":[0.03,0.1,0]},{"name":"EyeRight","translation":[-0.03,0.1,0]},{"name":"Jaw"},{"name":"Face","mesh":0}],"meshes":[{"extras":{},"primitives":[{"attributes":{"POSITION":0},"indices":2}]}]}""")
        val mesh=root.getJSONArray("meshes").getJSONObject(0)
        mesh.getJSONObject("extras").put("targetNames",JSONArray(AvatarGlbContract.TARGETS))
        mesh.getJSONArray("primitives").getJSONObject(0).put("targets",JSONArray(List(21){JSONObject().put("POSITION",1)}))
        mutate(root)
        val json = root.toString().toByteArray(); val padded=(json.size+3)/4*4
        val buffer=ByteBuffer.allocate(12+8+padded+8+80).order(ByteOrder.LITTLE_ENDIAN)
        buffer.putInt(0x46546c67).putInt(2).putInt(buffer.capacity()).putInt(padded).putInt(0x4e4f534a).put(json)
        while(buffer.position()<20+padded) buffer.put(32)
        buffer.putInt(80).putInt(0x004e4942)
        for(i in 0 until 18) buffer.putFloat(if(nonFinite && i==0) Float.NaN else i/100f)
        buffer.putShort(0).putShort(1).putShort(2).putShort(0)
        return buffer.array()
    }
}
