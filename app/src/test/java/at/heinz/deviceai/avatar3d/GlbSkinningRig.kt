package at.heinz.deviceai.avatar3d

import org.json.JSONObject
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.cos
import kotlin.math.sin

/**
 * Independent test reader for the packaged GLB: linear-blend skinning of the rendered face meshes with the
 * FilamentAvatarRenderer joint formula `rest · Ry(yaw) · Rx(pitch)`. It shares no code with AvatarGlbContract or
 * AvatarFraming, so it can falsify both. Host arithmetic only; not a Filament/GPU render.
 */
internal class GlbSkinningRig private constructor(val bytes: ByteArray) {
    private val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
    private val jsonLength = buffer.getInt(12)
    private val document = JSONObject(String(bytes, 20, jsonLength, Charsets.UTF_8))
    private val binStart = 20 + jsonLength + 8
    private val nodes = document.getJSONArray("nodes")
    private val names = (0 until nodes.length()).map { nodes.getJSONObject(it).optString("name") }
    private val parents = IntArray(nodes.length()) { -1 }.also { parents ->
        for (i in 0 until nodes.length()) nodes.getJSONObject(i).optJSONArray("children")?.let { c ->
            for (j in 0 until c.length()) parents[c.getInt(j)] = i
        }
    }
    private val skin = document.getJSONArray("skins").getJSONObject(0)
    private val joints = (0 until skin.getJSONArray("joints").length()).map { skin.getJSONArray("joints").getInt(it) }
    private val inverseBind = floats(skin.getInt("inverseBindMatrices"))

    class Mesh(val positions: DoubleArray, val joints: IntArray, val weights: DoubleArray, val indices: IntArray)

    /** Face and neural threads: the skinned, morphing surfaces the owner sees (teeth are hidden by the renderer). */
    val meshes: List<Mesh> = listOf("Face", "NeuralThreads").map { name ->
        val node = nodes.getJSONObject(names.indexOf(name))
        val primitive = document.getJSONArray("meshes").getJSONObject(node.getInt("mesh"))
            .getJSONArray("primitives").getJSONObject(0)
        val attributes = primitive.getJSONObject("attributes")
        Mesh(floats(attributes.getInt("POSITION")), ints(attributes.getInt("JOINTS_0")),
            floats(attributes.getInt("WEIGHTS_0")), ints(primitive.getInt("indices")))
    }

    private fun accessorStart(index: Int): Triple<JSONObject, Int, Int> {
        val accessor = document.getJSONArray("accessors").getJSONObject(index)
        val view = document.getJSONArray("bufferViews").getJSONObject(accessor.getInt("bufferView"))
        val width = mapOf("SCALAR" to 1, "VEC2" to 2, "VEC3" to 3, "VEC4" to 4, "MAT4" to 16).getValue(accessor.getString("type"))
        return Triple(accessor, binStart + view.optInt("byteOffset", 0) + accessor.optInt("byteOffset", 0), width)
    }

    private fun floats(index: Int): DoubleArray {
        val (accessor, start, width) = accessorStart(index)
        require(accessor.getInt("componentType") == 5126)
        return DoubleArray(accessor.getInt("count") * width) { buffer.getFloat(start + it * 4).toDouble() }
    }

    private fun ints(index: Int): IntArray {
        val (accessor, start, width) = accessorStart(index)
        return IntArray(accessor.getInt("count") * width) {
            when (accessor.getInt("componentType")) {
                5121 -> buffer.get(start + it).toInt() and 255
                5123 -> buffer.getShort(start + it * 2).toInt() and 65535
                5125 -> buffer.getInt(start + it * 4)
                else -> error("component")
            }
        }
    }

    fun translation(name: String): DoubleArray = nodes.getJSONObject(names.indexOf(name)).optJSONArray("translation")
        ?.let { t -> DoubleArray(3) { t.getDouble(it) } } ?: DoubleArray(3)

    /** Column-major 4×4, as android.opengl.Matrix. */
    private fun multiply(a: DoubleArray, b: DoubleArray) = DoubleArray(16) { i ->
        val col = i / 4; val row = i % 4
        (0..3).sumOf { k -> a[k * 4 + row] * b[col * 4 + k] }
    }

    private fun local(name: String, pitchDegrees: Double, yawDegrees: Double): DoubleArray {
        val t = translation(name)
        val p = Math.toRadians(pitchDegrees); val y = Math.toRadians(yawDegrees)
        val ry = doubleArrayOf(cos(y), 0.0, -sin(y), 0.0, 0.0, 1.0, 0.0, 0.0, sin(y), 0.0, cos(y), 0.0, 0.0, 0.0, 0.0, 1.0)
        val rx = doubleArrayOf(1.0, 0.0, 0.0, 0.0, 0.0, cos(p), sin(p), 0.0, 0.0, -sin(p), cos(p), 0.0, 0.0, 0.0, 0.0, 1.0)
        val rest = doubleArrayOf(1.0, 0.0, 0.0, 0.0, 0.0, 1.0, 0.0, 0.0, 0.0, 0.0, 1.0, 0.0, t[0], t[1], t[2], 1.0)
        return multiply(multiply(rest, ry), rx)
    }

    /** Joint rotations in degrees (pitch, yaw) per node name; unspecified joints stay at rest. */
    fun skinned(rotations: Map<String, Pair<Double, Double>>): List<DoubleArray> {
        val world = arrayOfNulls<DoubleArray>(nodes.length())
        fun worldOf(i: Int): DoubleArray = world[i] ?: run {
            val (pitch, yaw) = rotations[names[i]] ?: (0.0 to 0.0)
            val l = local(names[i], pitch, yaw)
            (if (parents[i] < 0) l else multiply(worldOf(parents[i]), l)).also { world[i] = it }
        }
        val skinMatrices = joints.mapIndexed { k, joint ->
            multiply(worldOf(joint), DoubleArray(16) { inverseBind[k * 16 + it] })
        }
        return meshes.map { mesh ->
            val count = mesh.positions.size / 3
            DoubleArray(count * 3).also { out ->
                for (v in 0 until count) for (k in 0..3) {
                    val w = mesh.weights[v * 4 + k]; if (w == 0.0) continue
                    val m = skinMatrices[mesh.joints[v * 4 + k]]
                    val x = mesh.positions[v * 3]; val y = mesh.positions[v * 3 + 1]; val z = mesh.positions[v * 3 + 2]
                    for (axis in 0..2) out[v * 3 + axis] += w * (m[axis] * x + m[4 + axis] * y + m[8 + axis] * z + m[12 + axis])
                }
            }
        }
    }

    /** Renderer split: the Neck joint carries [share] of the turn, the Head joint the remainder. */
    fun turn(yaw: Double, pitch: Double, share: Double) =
        skinned(mapOf("Neck" to (pitch * share to yaw * share), "Head" to (pitch * (1 - share) to yaw * (1 - share))))

    companion object {
        fun packaged(): GlbSkinningRig = GlbSkinningRig(listOf(File("src/main/assets/avatar3d/continuum-avatar.glb"),
            File("app/src/main/assets/avatar3d/continuum-avatar.glb")).first { it.isFile }.readBytes())

        /** The complete reachable range of the live mapper, corners and midpoints. */
        fun fullRangePoses(): List<Pair<Double, Double>> {
            val yaw = AvatarMorphMapper.MAX_HEAD_YAW_DEGREES.toDouble()
            val pitch = AvatarMorphMapper.MAX_HEAD_PITCH_DEGREES.toDouble()
            return listOf(-yaw, -yaw / 2, 0.0, yaw / 2, yaw).flatMap { y -> listOf(-pitch, 0.0, pitch).map { y to it } }
        }
    }
}
