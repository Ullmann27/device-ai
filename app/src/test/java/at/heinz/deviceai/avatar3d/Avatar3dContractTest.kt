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
    @Test fun malformedSkinIndicesMorphAliasingAndEmptyShapeAreRejected() {
        assertRejected(asset { it.getJSONArray("skins").getJSONObject(0).put("joints",JSONArray(listOf(1,99))) })
        assertRejected(asset { it.getJSONArray("meshes").getJSONObject(0).getJSONArray("primitives").getJSONObject(0)
            .getJSONArray("targets").put(1,JSONObject().put("POSITION",5)) })
        fun editBinary(accessorIndex: Int, edit: (ByteBuffer,Int)->Unit): ByteArray {
            val bytes=asset(); val b=ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
            val length=b.getInt(12); val doc=JSONObject(String(bytes,20,length,Charsets.UTF_8))
            val a=doc.getJSONArray("accessors").getJSONObject(accessorIndex)
            val view=doc.getJSONArray("bufferViews").getJSONObject(a.getInt("bufferView"))
            edit(b,28+length+view.optInt("byteOffset",0)+a.optInt("byteOffset",0))
            return bytes
        }
        assertRejected(editBinary(1) { b,at -> b.putShort(at,99) }) // Actual triangle index, not metadata.
        assertRejected(editBinary(2) { b,at -> b.putShort(at,99) }) // Actual joint influence.
        assertRejected(editBinary(3) { b,at -> b.putFloat(at,0f) }) // Weight sum zero.
        assertRejected(editBinary(6) { b,at -> repeat(9) { b.putFloat(at+it*4,0f) } }) // PP must deform.
        assertEquals(21,AvatarGlbContract.validate(editBinary(5) { b,at -> repeat(9) { b.putFloat(at+it*4,0f) } }).targets.size)
    }
    @Test fun focusPreservesHeadAcrossNarrowPhoneAndWideFold() {
        val focus=AvatarGlbContract.validate(asset()).focus
        for (aspect in listOf(213.0/310,279.0/390,415.0/390,587.0/470,602.0/470)) {
            val vertical=focus.verticalHalfSpan(aspect)
            assertTrue(vertical >= focus.halfHeight)
            assertTrue(vertical * aspect >= focus.halfWidth)
            assertTrue(vertical.isFinite() && focus.centerY.isFinite())
        }
        try { focus.verticalHalfSpan(Double.NaN); fail("nonfinite viewport") } catch (_: IllegalArgumentException) { }
    }
    private fun assertRejected(bytes: ByteArray) {
        try { AvatarGlbContract.validate(bytes); fail("invalid asset accepted") } catch (_: IllegalArgumentException) { } catch (_: org.json.JSONException) { }
    }
    private fun asset(nonFinite: Boolean = false, mutate: (JSONObject)->Unit = {}): ByteArray {
        val root = JSONObject("""{"asset":{"version":"2.0"},"buffers":[{}],"bufferViews":[],"accessors":[],"nodes":[{"name":"AvatarRoot","children":[1,6]},{"name":"Neck","children":[2]},{"name":"Head","children":[3,4,5]},{"name":"EyeLeft","translation":[0.03,0.1,0]},{"name":"EyeRight","translation":[-0.03,0.1,0]},{"name":"Jaw"},{"name":"Face","mesh":0,"skin":0}],"meshes":[{"extras":{},"primitives":[{"attributes":{}}]}],"skins":[{"joints":[1,2]}]}""")
        val data = java.io.ByteArrayOutputStream()
        fun accessor(values: List<Number>, type: String, component: Int = 5126): Int {
            val width = mapOf("SCALAR" to 1,"VEC3" to 3,"VEC4" to 4,"MAT4" to 16).getValue(type)
            val scalar = if(component==5123) 2 else 4
            val bytes=ByteBuffer.allocate(values.size*scalar).order(ByteOrder.LITTLE_ENDIAN)
            values.forEach { if(component==5123) bytes.putShort(it.toShort()) else bytes.putFloat(it.toFloat()) }
            val views=root.getJSONArray("bufferViews"); val accessors=root.getJSONArray("accessors")
            val index=accessors.length()
            views.put(JSONObject().put("buffer",0).put("byteOffset",data.size()).put("byteLength",bytes.capacity()))
            accessors.put(JSONObject().put("bufferView",views.length()-1).put("componentType",component).put("type",type).put("count",values.size/width))
            data.write(bytes.array()); while(data.size()%4!=0) data.write(0)
            return index
        }
        val mesh=root.getJSONArray("meshes").getJSONObject(0); val primitive=mesh.getJSONArray("primitives").getJSONObject(0)
        val attrs=primitive.getJSONObject("attributes")
        attrs.put("POSITION",accessor(listOf(if(nonFinite) Float.NaN else .03f,.1f,0f,-.03f,.1f,0f,0f,0f,.04f),"VEC3"))
        primitive.put("indices",accessor(listOf(0,1,2),"SCALAR",5123))
        attrs.put("JOINTS_0",accessor(List(12){0},"VEC4",5123))
        attrs.put("WEIGHTS_0",accessor(List(12){if(it%4==0) 1f else 0f},"VEC4"))
        root.getJSONArray("skins").getJSONObject(0).put("inverseBindMatrices",accessor(List(32){if(it%16 in listOf(0,5,10,15)) 1f else 0f},"MAT4"))
        mesh.getJSONObject("extras").put("targetNames",JSONArray(AvatarGlbContract.TARGETS))
        primitive.put("targets",JSONArray(List(21){JSONObject().put("POSITION",accessor(List(9){.001f},"VEC3"))}))
        root.getJSONArray("buffers").getJSONObject(0).put("byteLength",data.size())
        mutate(root)
        val json = root.toString().toByteArray(); val padded=(json.size+3)/4*4
        val buffer=ByteBuffer.allocate(12+8+padded+8+data.size()).order(ByteOrder.LITTLE_ENDIAN)
        buffer.putInt(0x46546c67).putInt(2).putInt(buffer.capacity()).putInt(padded).putInt(0x4e4f534a).put(json)
        while(buffer.position()<20+padded) buffer.put(32)
        buffer.putInt(data.size()).putInt(0x004e4942).put(data.toByteArray())
        return buffer.array()
    }
}
