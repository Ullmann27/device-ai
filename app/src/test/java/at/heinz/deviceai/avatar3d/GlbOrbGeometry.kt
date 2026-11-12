package at.heinz.deviceai.avatar3d

import org.json.JSONObject
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Reads actual orb vertex payloads. */
internal class GlbOrbGeometry {
    val bytes = file("app/src/main/assets/cinema3d/continuum-orb.glb").readBytes()
    val source = JSONObject(file("tools/cockpit-workstation/source/original-orb-geometry.json").readText())
    private val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
    private val jsonLength = buffer.getInt(12)
    private val document = JSONObject(String(bytes, 20, jsonLength, Charsets.UTF_8))
    private val binary = 28 + jsonLength
    private val nodes = document.getJSONArray("nodes")
    val names = (0 until nodes.length()).map { nodes.getJSONObject(it).optString("name") }
    private val parents = IntArray(nodes.length()) { -1 }.also { parents ->
        for (i in parents.indices) nodes.getJSONObject(i).optJSONArray("children")?.let { children ->
            for (j in 0 until children.length()) parents[children.getInt(j)] = i
        }
    }
    private val locals = Array(nodes.length()) { local(nodes.getJSONObject(it)) }
    private val meshes = names.indices.filter { nodes.getJSONObject(it).has("mesh") }.associateWith { index ->
        val primitives = document.getJSONArray("meshes").getJSONObject(nodes.getJSONObject(index).getInt("mesh"))
            .getJSONArray("primitives")
        (0 until primitives.length()).flatMap { primitive ->
            positions(primitives.getJSONObject(primitive).getJSONObject("attributes").getInt("POSITION"))
        }
    }
    data class Vertex(val mesh: String, val point: DoubleArray)

    fun extras(name: String): JSONObject = nodes.getJSONObject(names.indexOf(name)).getJSONObject("extras")
    fun rest(name: String): FloatArray = locals[names.indexOf(name)].map(Double::toFloat).toFloatArray()
    fun localVertices(name: String): List<DoubleArray> = meshes.getValue(names.indexOf(name))

    fun vertices(phase: Float, motion: Boolean = true): List<Vertex> {
        val matrices = arrayOfNulls<DoubleArray>(nodes.length())
        fun world(index: Int): DoubleArray {
            matrices[index]?.let { return it }
            val ring = names[index].removePrefix("orb-orbit-").toIntOrNull()
            val local = if (ring == null) locals[index] else CockpitWorldTransforms.ringTransform(
                rest(names[index]), ring, phase, motion).map(Float::toDouble).toDoubleArray()
            val result = if (parents[index] == -1) local else multiply(world(parents[index]), local)
            matrices[index] = result
            return result
        }
        return meshes.flatMap { (index, points) ->
            val matrix = world(index)
            points.map { Vertex(names[index], transform(matrix, it)) }
        }
    }

    private fun positions(index: Int): List<DoubleArray> {
        val accessor = document.getJSONArray("accessors").getJSONObject(index)
        require(accessor.getString("type") == "VEC3" && accessor.getInt("componentType") == 5126)
        val view = document.getJSONArray("bufferViews").getJSONObject(accessor.getInt("bufferView"))
        val start = binary + view.optInt("byteOffset", 0) + accessor.optInt("byteOffset", 0)
        val stride = view.optInt("byteStride", 12)
        return List(accessor.getInt("count")) { vertex ->
            DoubleArray(3) { axis -> buffer.getFloat(start + vertex * stride + axis * 4).toDouble() }
        }
    }

    private fun local(node: JSONObject): DoubleArray {
        node.optJSONArray("matrix")?.let { return DoubleArray(16, it::getDouble) }
        val q = node.optJSONArray("rotation")
        val x = q?.getDouble(0) ?: 0.0; val y = q?.getDouble(1) ?: 0.0
        val z = q?.getDouble(2) ?: 0.0; val w = q?.getDouble(3) ?: 1.0
        val result = doubleArrayOf(
            1 - 2*y*y - 2*z*z, 2*x*y + 2*z*w, 2*x*z - 2*y*w, 0.0,
            2*x*y - 2*z*w, 1 - 2*x*x - 2*z*z, 2*y*z + 2*x*w, 0.0,
            2*x*z + 2*y*w, 2*y*z - 2*x*w, 1 - 2*x*x - 2*y*y, 0.0,
            0.0, 0.0, 0.0, 1.0)
        for (axis in 0..2) {
            val scale = node.optJSONArray("scale")?.getDouble(axis) ?: 1.0
            for (row in 0..2) result[axis * 4 + row] *= scale
            result[12 + axis] = node.optJSONArray("translation")?.getDouble(axis) ?: 0.0
        }
        return result
    }

    companion object {
        private fun file(path: String): File = File(path).takeIf(File::isFile) ?: File("../$path")
        fun transform(matrix: DoubleArray, point: DoubleArray): DoubleArray = DoubleArray(3) { row ->
            (0..2).sumOf { column -> matrix[column * 4 + row] * point[column] } + matrix[12 + row]
        }
        private fun multiply(left: DoubleArray, right: DoubleArray): DoubleArray = DoubleArray(16) { at ->
            (0..3).sumOf { k -> left[k * 4 + at % 4] * right[at / 4 * 4 + k] }
        }
    }
}
