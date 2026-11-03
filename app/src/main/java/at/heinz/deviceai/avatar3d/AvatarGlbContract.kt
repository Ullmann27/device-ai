package at.heinz.deviceai.avatar3d

import org.json.JSONObject
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Fail-closed preflight of the packaged candidate before any Filament/JNI allocation. */
object AvatarGlbContract {
    const val MAX_BYTES = 16 * 1024 * 1024
    const val ASSET_PATH = "avatar3d/continuum-avatar.glb"
    val TARGETS = listOf("SIL", "PP", "FF", "TH", "DD", "KK", "CH", "SS", "NN", "RR", "AA", "E", "I", "O", "U",
        "BlinkLeft", "BlinkRight", "BrowUp", "BrowDown", "Smile", "JawOpen")
    val NODES = listOf("AvatarRoot", "Neck", "Head", "EyeLeft", "EyeRight", "Jaw")
    data class Metadata(val targets: List<String>, val morphNodeNames: List<String>)

    fun validate(bytes: ByteArray): Metadata {
        require(bytes.size in 28..MAX_BYTES) { "GLB size" }
        val buf = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        require(buf.int == 0x46546c67 && buf.int == 2 && buf.int == bytes.size) { "GLB header" }
        val jsonSize = buf.int
        require(buf.int == 0x4e4f534a && jsonSize in 4..1_048_576 && jsonSize % 4 == 0 && jsonSize <= buf.remaining() - 8) { "GLB JSON" }
        val jsonBytes = ByteArray(jsonSize); buf.get(jsonBytes)
        val root = JSONObject(String(jsonBytes, Charsets.UTF_8))
        val binSize = buf.int
        require(buf.int == 0x004e4942 && binSize == buf.remaining() && binSize % 4 == 0) { "GLB BIN" }
        val binStart = buf.position()
        require(root.getJSONObject("asset").getString("version") == "2.0")
        require((root.optJSONArray("extensionsRequired")?.length() ?: 0) == 0) { "Required extension unsupported" }
        require((root.optJSONArray("animations")?.length() ?: 0) == 0) { "Only event-driven poses" }
        val buffers = root.getJSONArray("buffers")
        require(buffers.length() == 1 && !buffers.getJSONObject(0).has("uri")) { "Embedded buffer only" }
        val dataSize = buffers.getJSONObject(0).getInt("byteLength")
        require(dataSize in 1..binSize && binSize - dataSize < 4)
        root.optJSONArray("images")?.let { images ->
            for (i in 0 until images.length()) require(!images.getJSONObject(i).has("uri")) { "Embedded images only" }
        }
        val views = root.getJSONArray("bufferViews")
        require(views.length() <= 256)
        for (i in 0 until views.length()) {
            val v = views.getJSONObject(i)
            val start = v.optLong("byteOffset", 0); val length = v.getLong("byteLength")
            require(v.getInt("buffer") == 0 && start >= 0 && length > 0 && start + length <= dataSize) { "Buffer view bounds" }
        }
        val accessors = root.getJSONArray("accessors")
        require(accessors.length() <= 512)
        val sizes = mapOf(5120 to 1, 5121 to 1, 5122 to 2, 5123 to 2, 5125 to 4, 5126 to 4)
        val widths = mapOf("SCALAR" to 1, "VEC2" to 2, "VEC3" to 3, "VEC4" to 4, "MAT4" to 16)
        fun accessor(index: Int): JSONObject { require(index in 0 until accessors.length()); return accessors.getJSONObject(index) }
        for (i in 0 until accessors.length()) {
            val a = accessor(i)
            require(!a.has("sparse")) { "Sparse accessor unsupported" }
            val view = a.getInt("bufferView"); require(view in 0 until views.length())
            val v = views.getJSONObject(view)
            val scalar = requireNotNull(sizes[a.getInt("componentType")]); val width = requireNotNull(widths[a.getString("type")])
            val packed = scalar * width; val stride = v.optInt("byteStride", packed)
            val offset = a.optLong("byteOffset", 0); val count = a.getInt("count")
            require(count in 1..600_000 && stride >= packed && stride <= 252 && stride % scalar == 0 && offset >= 0 && offset % scalar == 0L)
            require(offset + (count - 1L) * stride + packed <= v.getLong("byteLength")) { "Accessor bounds" }
            // Actual binary float values, not untrusted min/max metadata.
            if (a.getInt("componentType") == 5126) {
                val start = binStart + v.optInt("byteOffset", 0) + offset.toInt()
                for (n in 0 until count) for (c in 0 until width) require(buf.getFloat(start + n * stride + c * 4).isFinite()) { "Nonfinite accessor" }
            }
        }
        val nodes = root.getJSONArray("nodes"); require(nodes.length() in 6..64)
        val names = (0 until nodes.length()).map { nodes.getJSONObject(it).optString("name", "") }
        NODES.forEach { name -> require(names.count { it == name } == 1) { "Required unique node $name" } }
        val parents = IntArray(nodes.length()) { -1 }
        for (i in 0 until nodes.length()) {
            val n = nodes.getJSONObject(i)
            require(!n.has("matrix")) { "TRS only" }
            n.optJSONArray("scale")?.let { s -> require(s.length() == 3); for (j in 0..2) require(s.getDouble(j).isFinite() && s.getDouble(j) > 0) }
            n.optJSONArray("translation")?.let { t -> require(t.length() == 3); for (j in 0..2) require(t.getDouble(j).isFinite()) }
            n.optJSONArray("rotation")?.let { r -> require(r.length() == 4); for (j in 0..3) require(kotlin.math.abs(r.getDouble(j) - if (j == 3) 1.0 else 0.0) < 0.00001) }
            n.optJSONArray("children")?.let { children -> for (j in 0 until children.length()) {
                val child = children.getInt(j); require(child in parents.indices && parents[child] == -1 && child != i)
                parents[child] = i
            } }
        }
        fun descendant(child: Int, ancestor: Int): Boolean {
            var current = parents[child]; val seen = mutableSetOf(child)
            while (current != -1) { require(seen.add(current)) { "Node cycle" }; if (current == ancestor) return true; current = parents[current] }
            return false
        }
        for (i in parents.indices) { var p = i; val seen = mutableSetOf<Int>(); while (p != -1) { require(seen.add(p)); p = parents[p] } }
        require(descendant(names.indexOf("Neck"), names.indexOf("AvatarRoot")))
        require(descendant(names.indexOf("Head"), names.indexOf("Neck")))
        listOf("EyeLeft", "EyeRight", "Jaw").forEach { require(descendant(names.indexOf(it), names.indexOf("Head"))) }
        val meshes = root.getJSONArray("meshes"); require(meshes.length() in 1..32)
        var targets: List<String>? = null
        val morphMeshes = mutableSetOf<Int>(); var vertices = 0; var triangles = 0
        for (m in 0 until meshes.length()) {
            val mesh = meshes.getJSONObject(m); val primitives = mesh.getJSONArray("primitives")
            require(primitives.length() in 1..16)
            val targetNames = mesh.optJSONObject("extras")?.optJSONArray("targetNames")?.let { a -> (0 until a.length()).map { a.getString(it) } }
            for (p in 0 until primitives.length()) {
                val primitive = primitives.getJSONObject(p); require(primitive.optInt("mode", 4) == 4)
                val attributes = primitive.getJSONObject("attributes")
                attributes.keys().forEach { accessor(attributes.getInt(it)) }
                val position = accessor(attributes.getInt("POSITION")); val count = position.getInt("count")
                require(position.getInt("componentType") == 5126 && position.getString("type") == "VEC3")
                vertices += count
                val idx = accessor(primitive.getInt("indices")); require(idx.getString("type") == "SCALAR" && idx.getInt("componentType") in listOf(5121,5123,5125) && idx.getInt("count") % 3 == 0)
                triangles += idx.getInt("count") / 3
                primitive.optJSONArray("targets")?.let { ts ->
                    require(targetNames != null && targetNames.size == TARGETS.size && targetNames.toSet() == TARGETS.toSet()) { "Morph names" }
                    require(ts.length() == targetNames.size && (targets == null || targets == targetNames)) { "Morph ordering" }
                    targets = targetNames; morphMeshes += m
                    for (t in 0 until ts.length()) {
                        val a = accessor(ts.getJSONObject(t).getInt("POSITION"))
                        require(a.getInt("count") == count && a.getInt("componentType") == 5126 && a.getString("type") == "VEC3")
                    }
                }
            }
            mesh.optJSONArray("weights")?.let { w -> for (i in 0 until w.length()) require(w.getDouble(i) == 0.0) { "Non-neutral rest" } }
        }
        require(vertices <= 100_000 && triangles <= 200_000)
        val morphNodes = (0 until nodes.length()).filter { nodes.getJSONObject(it).optInt("mesh", -1) in morphMeshes }.map { names[it] }
        require(morphNodes.isNotEmpty() && morphNodes.all { it.isNotBlank() && names.count { n -> n == it } == 1 })
        return Metadata(requireNotNull(targets) { "No morph mesh" }, morphNodes)
    }
}
