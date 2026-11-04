package at.heinz.deviceai.avatar3d

import org.json.JSONObject
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.cos
import kotlin.math.sin

/** Actual packaged head geometry, independent projection, no Filament or screenshot substitute. */
class AvatarPortraitFramingTest {
    @Test fun narrowCockpitUsesHeadWidthInsteadOfFittingTheWholeShoulderStage() {
        val head = readHead()
        assertTrue("The reference camera must remain a three-quarter view", head.focus.azimuthDegrees in -28.0..-16.0)
        val horizontal = head.focus.verticalHalfSpan(204.0 / 398.0) * 204.0 / 398.0
        val projected = head.points.map { projectX(it, head.focus) }
        val occupied = (projected.max() - projected.min()) / (2 * horizontal)
        assertTrue("The real head occupies only ${occupied * 100}% of the 204dp cockpit slot", occupied >= .74)
        assertTrue("Framing must retain a visible margin around the head", occupied <= .95)
    }

    @Test fun theRealNeutralHeadFitsCompactMediumAndExpandedPortraitViewports() {
        val head = readHead()
        for ((width, height) in listOf(204 to 398, 280 to 398, 343 to 400)) {
            val aspect = width.toDouble() / height
            val vertical = head.focus.verticalHalfSpan(aspect)
            val horizontal = vertical * aspect
            for (point in head.points) {
                val x = projectX(point, head.focus)
                val y = point[1] - head.focus.centerY
                assertTrue("$width x $height clips head vertex horizontally at $x", x in -horizontal..horizontal)
                assertTrue("$width x $height clips head vertex vertically at $y", y in -vertical..vertical)
            }
        }
    }

    private data class Head(val focus: AvatarGlbContract.Focus, val points: List<DoubleArray>)

    private fun projectX(point: DoubleArray, focus: AvatarGlbContract.Focus): Double {
        // The requested three-quarter presentation is independent of the event-driven pose.
        val angle = Math.toRadians(focus.azimuthDegrees)
        return cos(angle) * (point[0] - focus.centerX) - sin(angle) * (point[2] - focus.centerZ)
    }

    private fun readHead(): Head {
        val asset = listOf(File("src/main/assets/avatar3d/continuum-avatar.glb"),
            File("app/src/main/assets/avatar3d/continuum-avatar.glb")).first { it.isFile }
        val bytes = asset.readBytes()
        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        val jsonLength = buffer.getInt(12)
        val document = JSONObject(String(bytes, 20, jsonLength, Charsets.UTF_8))
        val nodes = document.getJSONArray("nodes")
        val parents = (0 until nodes.length()).associateWith { child ->
            (0 until nodes.length()).firstOrNull { parent ->
                nodes.getJSONObject(parent).optJSONArray("children")?.let { children ->
                    (0 until children.length()).any { children.getInt(it) == child }
                } ?: false
            }
        }
        fun world(index: Int, point: DoubleArray): DoubleArray {
            val node = nodes.getJSONObject(index)
            val transformed = DoubleArray(3) { axis ->
                point[axis] * (node.optJSONArray("scale")?.getDouble(axis) ?: 1.0) +
                    (node.optJSONArray("translation")?.getDouble(axis) ?: 0.0)
            }
            return parents[index]?.let { world(it, transformed) } ?: transformed
        }
        val headIndex = (0 until nodes.length()).first { nodes.getJSONObject(it).optString("name") == "Head" }
        val headHeight = world(headIndex, doubleArrayOf(0.0, 0.0, 0.0))[1]
        val faceIndex = (0 until nodes.length()).first { nodes.getJSONObject(it).optString("name") == "Face" }
        val primitive = document.getJSONArray("meshes").getJSONObject(nodes.getJSONObject(faceIndex).getInt("mesh"))
            .getJSONArray("primitives").getJSONObject(0)
        val accessor = document.getJSONArray("accessors").getJSONObject(primitive.getJSONObject("attributes").getInt("POSITION"))
        val view = document.getJSONArray("bufferViews").getJSONObject(accessor.getInt("bufferView"))
        val offset = 28 + jsonLength + view.optInt("byteOffset", 0) + accessor.optInt("byteOffset", 0)
        val stride = view.optInt("byteStride", 12)
        val points = (0 until accessor.getInt("count")).map { vertex ->
            world(faceIndex, DoubleArray(3) { axis -> buffer.getFloat(offset + vertex * stride + axis * 4).toDouble() })
        }.filter { it[1] >= headHeight }
        assertTrue("Packaged head geometry must be present", points.size > 100)
        return Head(AvatarGlbContract.validate(bytes).focus, points)
    }
}
