package at.heinz.deviceai.avatar3d

import at.heinz.deviceai.avatar.*
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest

/** Physical asset regressions: actual packaged morph bytes, not source-text assertions. */
class AvatarMouthCouplingTest {
    private fun pose(viseme: Viseme, jaw: Float, weight: Float = 1f) =
        AvatarPose(AvatarState.SPEAKING, AvatarExpression.NEUTRAL, 0f, 0f, jaw, viseme, weight, jaw)
    private fun mapper() = AvatarMorphMapper(AvatarGlbContract.TARGETS)
    private fun weight(mapper: AvatarMorphMapper, name: String) = mapper.weights[AvatarGlbContract.TARGETS.indexOf(name)]

    @Test fun aaCannotAddSharedOpeningTwiceWhileItsLateralShapeIsPreserved() {
        val map = mapper()
        map.update(pose(Viseme.AA, 1f), 1f, true)
        val actual = face.maximumDownward("AA", weight(map, "AA"), weight(map, "JawOpen"))
        val bound = face.maximumDownward("AA", 0f, 1f) + face.maximumResidualDownward("AA", .65f / .8f)
        assertTrue("AA double-adds shared opening: $actual versus $bound", actual <= bound + .000001f)
    }

    @Test fun roundedVowelPreservesPursingWithoutAddingSharedOpeningTwice() {
        val map = mapper()
        map.update(pose(Viseme.O, 1f), 1f, true)
        val actual = face.maximumDownward("O", weight(map, "O"), weight(map, "JawOpen"))
        // The pinned CC0 recipe also contains independent pursing/protrusion. The initial
        // 10% total-deformation bound was overbroad; its actual RED remains archived.
        val bound = face.maximumDownward("O", 0f, 1f) + face.maximumResidualDownward("O", .36f / .8f)
        assertTrue("O double-adds shared opening: $actual versus $bound", actual <= bound + .000001f)
    }

    @Test fun measuredJawAloneKeepsItsFullRangeWithoutInventingVisemes() {
        val map = mapper()
        for (jaw in listOf(0f, .25f, .5f, 1f)) {
            map.update(pose(Viseme.SIL, jaw), 1f, true)
            assertEquals(jaw, weight(map, "JawOpen"), 0f)
            assertEquals(jaw * AvatarMorphMapper.JAW_BONE_DEGREES, map.jawPitch, .0001f)
            for (v in Viseme.entries) assertEquals(0f, weight(map, v.name), 0f)
        }
    }

    @Test fun activeOpeningCueMovesInnerJawAndActualStopClosesBoth() {
        val reducer = AvatarPoseReducer()
        val map = mapper()
        reducer.apply(AvatarEvent.SpeechStarted("synthetic", "A", 100))
        reducer.apply(AvatarEvent.VisemeCue("synthetic", Viseme.AA, 1f, 300, 100))
        map.update(reducer.pose(101), 1f, true)
        assertTrue("Opening face cue must not leave inner jaw at neutral", map.jawPitch > 0f)
        assertTrue(map.jawPitch <= AvatarMorphMapper.JAW_BONE_DEGREES)
        reducer.apply(AvatarEvent.SpeechEnded("synthetic", true, 102))
        map.update(reducer.pose(103), 1f, true)
        assertEquals(0f, map.jawPitch, 0f)
        assertEquals(0f, weight(map, "JawOpen"), 0f)
        for (v in Viseme.entries) assertEquals(0f, weight(map, v.name), 0f)
    }

    private class FaceMorphs {
        private val bytes = sequenceOf(File("src/main/assets/avatar3d/continuum-avatar.glb"),
            File("app/src/main/assets/avatar3d/continuum-avatar.glb")).first { it.isFile }.readBytes()
        private val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        private val jsonLength = buffer.getInt(12)
        private val document = JSONObject(String(bytes, 20, jsonLength, Charsets.UTF_8))
        private val faceNode = (0 until document.getJSONArray("nodes").length()).map {
            document.getJSONArray("nodes").getJSONObject(it)
        }.single { it.optString("name") == "Face" }
        private val mesh = document.getJSONArray("meshes").getJSONObject(faceNode.getInt("mesh"))
        private val names = mesh.getJSONObject("extras").getJSONArray("targetNames")
        private val primitive = mesh.getJSONArray("primitives").getJSONObject(0)
        private val cache = mutableMapOf<String, FloatArray>()

        init {
            val sha = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
            require(sha == AvatarGlbContract.ASSET_SHA256) { "Actual packaged asset differs from production pin" }
        }

        private fun verticalDelta(name: String): FloatArray = cache.getOrPut(name) {
            val targetIndex = (0 until names.length()).single { names.getString(it) == name }
            val accessorIndex = primitive.getJSONArray("targets").getJSONObject(targetIndex).getInt("POSITION")
            val accessor = document.getJSONArray("accessors").getJSONObject(accessorIndex)
            require(accessor.getInt("componentType") == 5126 && accessor.getString("type") == "VEC3")
            val view = document.getJSONArray("bufferViews").getJSONObject(accessor.getInt("bufferView"))
            val start = 28 + jsonLength + view.optInt("byteOffset", 0) + accessor.optInt("byteOffset", 0)
            val stride = view.optInt("byteStride", 12)
            FloatArray(accessor.getInt("count")) { buffer.getFloat(start + it * stride + 4) }
        }

        fun maximumDownward(viseme: String, amount: Float, jawAmount: Float): Float {
            val v = verticalDelta(viseme)
            val j = verticalDelta("JawOpen")
            require(v.size == j.size)
            var maximum = 0f
            for (i in v.indices) maximum = maxOf(maximum, -(v[i] * amount + j[i] * jawAmount))
            return maximum
        }

        fun maximumResidualDownward(viseme: String, commonOpeningRatio: Float): Float {
            val v = verticalDelta(viseme)
            val j = verticalDelta("JawOpen")
            var maximum = 0f
            for (i in v.indices) maximum = maxOf(maximum, -(v[i] - j[i] * commonOpeningRatio))
            return maximum
        }
    }

    private companion object { val face by lazy { FaceMorphs() } }
}
