package at.heinz.deviceai.avatar3d

import org.json.JSONObject
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Fail-closed preflight of the packaged candidate before any Filament/JNI allocation. */
object AvatarGlbContract {
    const val MAX_BYTES = 16 * 1024 * 1024
    const val ASSET_PATH = "avatar3d/continuum-avatar.glb"
    const val ASSET_SHA256 = "c8b88385c73fa01596a5734d20859440b1ecaef06cb1abea6cd7a47e62b08b9a"
    val TARGETS = listOf("SIL", "PP", "FF", "TH", "DD", "KK", "CH", "SS", "NN", "RR", "AA", "E", "I", "O", "U",
        "BlinkLeft", "BlinkRight", "BrowUp", "BrowDown", "Smile", "JawOpen")
    val NODES = listOf("AvatarRoot", "Neck", "Head", "EyeLeft", "EyeRight", "Jaw")
    /**
     * [reachLeft]/[reachRight]: camera-horizontal extent of the head left/right of [centerX] over the complete pose
     * range the mapper can produce (Neck+Head turn, see [AvatarMorphMapper.MAX_HEAD_YAW_DEGREES]). The framing keeps
     * this reach inside the asymmetric cockpit window. Defaults keep hand-built foci symmetric.
     */
    data class Focus(val centerX: Double, val centerY: Double, val centerZ: Double, val halfWidth: Double,
        val halfHeight: Double, val azimuthDegrees: Double = 0.0,
        val reachLeft: Double = halfWidth, val reachRight: Double = halfWidth,
        /** Posed face silhouette, including skin blends. */
        val face: FaceProfile? = null) {
        /** Bust framing includes the neck and upper shoulders; only the outer torso is cropped. */
        fun verticalHalfSpan(aspect: Double): Double {
            require(aspect.isFinite() && aspect > 0)
            return maxOf(halfHeight, halfWidth / aspect) * 1.08
        }
    }
    /**
     * [left] holds, for each world-height band `[bottom + i·step, bottom + (i + 1)·step)`, the leftmost projected
     * x across head poses, widened by the reach margin.
     */
    data class FaceProfile(val bottom: Double, val step: Double, val left: List<Double>) {
        init { require(bottom.isFinite() && step.isFinite() && step > 0 && left.isNotEmpty() && left.all { it.isFinite() }) }
    }
    data class Metadata(val targets: List<String>, val morphNodeNames: List<String>, val focus: Focus)

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
        fun values(index: Int): List<DoubleArray> {
            val a = accessor(index); val v = views.getJSONObject(a.getInt("bufferView"))
            val component = a.getInt("componentType"); val scalar = sizes.getValue(component)
            val width = widths.getValue(a.getString("type")); val stride = v.optInt("byteStride", scalar * width)
            val start = binStart + v.optInt("byteOffset", 0) + a.optInt("byteOffset", 0)
            return List(a.getInt("count")) { n -> DoubleArray(width) { c ->
                val at = start + n * stride + c * scalar
                when (component) {
                    5121 -> (buf.get(at).toInt() and 255).toDouble()
                    5123 -> (buf.getShort(at).toInt() and 65535).toDouble()
                    5125 -> (buf.getInt(at).toLong() and 0xffffffffL).toDouble()
                    5126 -> buf.getFloat(at).toDouble()
                    else -> throw IllegalArgumentException("Unsupported geometry component")
                }
            } }
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
        // Rest rotations were checked above. Resolve positive TRS once, not once per frame.
        val worldFrames = arrayOfNulls<DoubleArray>(nodes.length())
        fun worldFrame(index: Int): DoubleArray {
            worldFrames[index]?.let { return it }
            val n = nodes.getJSONObject(index)
            val local = DoubleArray(6) { axis -> if (axis < 3) n.optJSONArray("scale")?.getDouble(axis) ?: 1.0
                else n.optJSONArray("translation")?.getDouble(axis - 3) ?: 0.0 }
            if (parents[index] >= 0) {
                val parent = worldFrame(parents[index])
                for (axis in 0..2) { local[axis + 3] = parent[axis + 3] + parent[axis] * local[axis + 3]; local[axis] *= parent[axis] }
            }
            require(local.all { it.isFinite() })
            worldFrames[index] = local
            return local
        }
        val headY = worldFrame(names.indexOf("Head"))[4]
        val neckY = worldFrame(names.indexOf("Neck"))[4]
        var minHeadX = Double.POSITIVE_INFINITY; var maxHeadX = Double.NEGATIVE_INFINITY
        var minHeadZ = Double.POSITIVE_INFINITY; var maxHeadZ = Double.NEGATIVE_INFINITY
        // Owner requirement (05.11.): frontal default view; the head turns itself instead of a fixed 3/4 camera.
        val portraitAzimuth = 0.0
        val portraitAngle = Math.toRadians(portraitAzimuth)
        val portraitCos = kotlin.math.cos(portraitAngle); val portraitSin = kotlin.math.sin(portraitAngle)
        var minPortraitX = Double.POSITIVE_INFINITY; var maxPortraitX = Double.NEGATIVE_INFINITY
        var maxHeadY = Double.NEGATIVE_INFINITY
        // World positions (x, y, z interleaved) of the rendered face from the chin line up, for the pose fit below.
        // Chin line: 30 % of the Neck→Head joint distance below the Head joint (packaged rig: 0.2015 m; chin ≈ 0.20 m).
        val chinY = headY - (headY - neckY) * .30
        val facePoints = ArrayList<Double>()
        var minBustY = Double.POSITIVE_INFINITY
        var minBustX = Double.POSITIVE_INFINITY; var maxBustX = Double.NEGATIVE_INFINITY
        val skins = root.getJSONArray("skins"); require(skins.length() in 1..8)
        for (i in 0 until skins.length()) {
            val skin = skins.getJSONObject(i); val joints = skin.getJSONArray("joints")
            require(joints.length() in 1..64)
            val jointIndices = (0 until joints.length()).map { joints.getInt(it) }
            require(jointIndices.toSet().size == jointIndices.size && jointIndices.all { it in parents.indices })
            val inverse = accessor(skin.getInt("inverseBindMatrices"))
            require(inverse.getInt("count") == joints.length() && inverse.getString("type") == "MAT4" && inverse.getInt("componentType") == 5126)
        }
        val meshes = root.getJSONArray("meshes"); require(meshes.length() in 1..32)
        for (i in 0 until nodes.length()) {
            val n = nodes.getJSONObject(i)
            if (n.has("mesh")) require(n.getInt("mesh") in 0 until meshes.length())
            if (n.has("skin")) require(n.has("mesh") && n.getInt("skin") in 0 until skins.length())
        }
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
                require(values(primitive.getInt("indices")).all { it[0] >= 0 && it[0] < count }) { "Vertex index bounds" }
                triangles += idx.getInt("count") / 3
                primitive.optJSONArray("targets")?.let { ts ->
                    require(targetNames != null && targetNames.size == TARGETS.size && targetNames.toSet() == TARGETS.toSet()) { "Morph names" }
                    require(ts.length() == targetNames.size && (targets == null || targets == targetNames)) { "Morph ordering" }
                    targets = targetNames; morphMeshes += m
                    val morphAccessors = (0 until ts.length()).map { ts.getJSONObject(it).getInt("POSITION") }
                    require(morphAccessors.toSet().size == morphAccessors.size) { "Repeated morph accessor" }
                    val neutral = values(attributes.getInt("POSITION"))
                    val spans = DoubleArray(3) { axis -> neutral.maxOf { it[axis] } - neutral.minOf { it[axis] } }
                    val diagonal = kotlin.math.sqrt(spans.sumOf { it * it })
                    require(diagonal in 0.02..2.0) { "Invalid metre-scale face bounds" }
                    require(attributes.has("JOINTS_0") && attributes.has("WEIGHTS_0")) { "Skinned face required" }
                    val jointAccessor = accessor(attributes.getInt("JOINTS_0"))
                    val weightAccessor = accessor(attributes.getInt("WEIGHTS_0"))
                    require(jointAccessor.getInt("componentType") in listOf(5121, 5123) && weightAccessor.getInt("componentType") == 5126)
                    require(jointAccessor.getString("type") == "VEC4" && weightAccessor.getString("type") == "VEC4")
                    require(jointAccessor.getInt("count") == count && weightAccessor.getInt("count") == count)
                    val skinNodes = (0 until nodes.length()).filter { nodes.getJSONObject(it).optInt("mesh", -1) == m }
                    require(skinNodes.isNotEmpty())
                    for (node in skinNodes) {
                        val world = worldFrame(node)
                        for (vertex in neutral) {
                            val y = vertex[1] * world[1] + world[4]
                            val x = vertex[0] * world[0] + world[3]
                            minBustY = minOf(minBustY, y)
                            minBustX = minOf(minBustX, x); maxBustX = maxOf(maxBustX, x)
                            if (y >= chinY) {
                                facePoints += x; facePoints += y; facePoints += vertex[2] * world[2] + world[5]
                            }
                            if (y >= headY) {
                                val z = vertex[2] * world[2] + world[5]
                                minHeadX = minOf(minHeadX, x); maxHeadX = maxOf(maxHeadX, x)
                                minHeadZ = minOf(minHeadZ, z); maxHeadZ = maxOf(maxHeadZ, z); maxHeadY = maxOf(maxHeadY, y)
                                val portraitX = x * portraitCos - z * portraitSin
                                minPortraitX = minOf(minPortraitX, portraitX); maxPortraitX = maxOf(maxPortraitX, portraitX)
                            }
                        }
                        val skin = skins.getJSONObject(nodes.getJSONObject(node).getInt("skin"))
                        val jointCount = skin.getJSONArray("joints").length()
                        require(values(attributes.getInt("JOINTS_0")).all { j -> j.all { it >= 0 && it < jointCount } })
                    }
                    require(values(attributes.getInt("WEIGHTS_0")).all { w -> w.all { it in 0.0..1.0 } && kotlin.math.abs(w.sum() - 1.0) < 0.005 })
                    for (t in 0 until ts.length()) {
                        val a = accessor(ts.getJSONObject(t).getInt("POSITION"))
                        require(a.getInt("count") == count && a.getInt("componentType") == 5126 && a.getString("type") == "VEC3")
                        val deltas = values(ts.getJSONObject(t).getInt("POSITION"))
                        val maxDelta = deltas.maxOf { kotlin.math.sqrt(it.sumOf { value -> value * value }) }
                        require((targetNames[t] == "SIL" || maxDelta > 0.0000001) && maxDelta <= diagonal * .2) { "Empty or excessive morph" }
                    }
                }
            }
            mesh.optJSONArray("weights")?.let { w -> for (i in 0 until w.length()) require(w.getDouble(i) == 0.0) { "Non-neutral rest" } }
        }
        require(vertices <= 100_000 && triangles <= 200_000)
        val morphNodes = (0 until nodes.length()).filter { nodes.getJSONObject(it).optInt("mesh", -1) in morphMeshes }.map { names[it] }
        require(morphNodes.isNotEmpty() && morphNodes.all { it.isNotBlank() && names.count { n -> n == it } == 1 })
        // The former crop began above the Neck joint, leaving a floating face.
        // Keep the existing rig and bring its actual neck/upper shoulder skin
        // into frame, without shrinking the head to fit the entire chest mesh.
        val bottom = maxOf(minBustY, neckY - (headY - neckY) * .85)
        val top = maxHeadY + (maxHeadY - bottom) * .05
        require(listOf(minHeadX, maxHeadX, minHeadZ, maxHeadZ, bottom, top).all { it.isFinite() } &&
            maxHeadX > minHeadX && maxHeadZ > minHeadZ && top > bottom)
        val centerZ = (minHeadZ + maxHeadZ) / 2
        val portraitCenter = (minPortraitX + maxPortraitX) / 2
        val centerX = (portraitCenter + centerZ * portraitSin) / portraitCos
        // Frame the real projected head, with 10 mm of pose margin. Fitting the
        // shoulder width made the head occupy only 53% of the narrow cockpit.
        // The neck remains in the vertical frame; the broad torso can crop.
        val neckFrame = worldFrame(names.indexOf("Neck"))
        val headFrame = worldFrame(names.indexOf("Head"))
        val fit = poseFit(facePoints, neckFrame, headFrame, centerX, centerZ, portraitAzimuth)
        val focus = Focus(centerX, (bottom + top) / 2, centerZ,
            (maxPortraitX - minPortraitX) / 2 + .010, (top - bottom) / 2, portraitAzimuth,
            fit.reachLeft, fit.reachRight, fit.face)
        return Metadata(requireNotNull(targets) { "No morph mesh" }, morphNodes, focus)
    }

    /** Antialiasing and bloom fringe kept inside the viewport beyond the posed silhouette (metres). */
    private const val REACH_MARGIN = .004

    private class PoseFit(val reachLeft: Double, val reachRight: Double, val face: FaceProfile)

    /** Height bands of [FaceProfile] (packaged rig: about 5 mm each). */
    private const val FACE_BANDS = 40

    /**
     * Camera-horizontal reach and posed face silhouette over the mapper's full pose range, mirroring
     * FilamentAvatarRenderer: world = T(neck)·R(share·turn) · T(head − neck)·R((1 − share)·turn) with
     * R(yaw, pitch) = Ry(yaw)·Rx(pitch). Every face vertex is visited fully Head-driven and Neck-driven; a vertex
     * of the Neck→Head blend band is a convex mix of those and its rest position, so the reach is an upper bound.
     * Runs once, never per frame.
     */
    private fun poseFit(points: List<Double>, neck: DoubleArray, head: DoubleArray, centerX: Double,
                        centerZ: Double, azimuthDegrees: Double): PoseFit {
        require(points.size >= 3 && points.size % 3 == 0) { "No face vertices" }
        val maxYaw = AvatarMorphMapper.MAX_HEAD_YAW_DEGREES.toDouble()
        val maxPitch = AvatarMorphMapper.MAX_HEAD_PITCH_DEGREES.toDouble()
        val share = AvatarMorphMapper.NECK_SHARE.toDouble()
        val azimuth = Math.toRadians(azimuthDegrees)
        val cosA = kotlin.math.cos(azimuth); val sinA = kotlin.math.sin(azimuth)
        // Head pivot relative to the Neck pivot (rest rotations are identity, checked above).
        val hx = head[3] - neck[3]; val hy = head[4] - neck[4]; val hz = head[5] - neck[5]
        val v = DoubleArray(3)
        fun rotate(yawDegrees: Double, pitchDegrees: Double) {
            val p = Math.toRadians(pitchDegrees); val y = Math.toRadians(yawDegrees)
            val cp = kotlin.math.cos(p); val sp = kotlin.math.sin(p); val cy = kotlin.math.cos(y); val sy = kotlin.math.sin(y)
            val ry = v[1] * cp - v[2] * sp; val rz = v[1] * sp + v[2] * cp
            v[1] = ry; v[2] = rz
            val rx = v[0] * cy + v[2] * sy; val rz2 = -v[0] * sy + v[2] * cy
            v[0] = rx; v[2] = rz2
        }
        /** Visits every posed vertex as (projected x, world y). */
        fun forEachPosed(blend: ((Double, Double, Double) -> Unit)? = null, visit: (Double, Double) -> Unit) {
            for (yawStep in -4..4) for (pitchStep in -2..2) {
                val yaw = maxYaw * yawStep / 4.0; val pitch = maxPitch * pitchStep / 2.0
                var i = 0
                while (i < points.size) {
                    // Full chain (Head-weighted vertices) ...
                    v[0] = points[i] - head[3]; v[1] = points[i + 1] - head[4]; v[2] = points[i + 2] - head[5]
                    rotate(yaw * (1 - share), pitch * (1 - share))
                    v[0] += hx; v[1] += hy; v[2] += hz
                    rotate(yaw * share, pitch * share)
                    val headX = (v[0] + neck[3] - centerX) * cosA - (v[2] + neck[5] - centerZ) * sinA
                    val headY = v[1] + neck[4]
                    visit(headX, headY)
                    // ... and the Neck turn alone (Neck-weighted part of the chin blend band).
                    v[0] = points[i] - neck[3]; v[1] = points[i + 1] - neck[4]; v[2] = points[i + 2] - neck[5]
                    rotate(yaw * share, pitch * share)
                    val neckX = (v[0] + neck[3] - centerX) * cosA - (v[2] + neck[5] - centerZ) * sinA
                    val neckY = v[1] + neck[4]
                    visit(neckX, neckY)
                    val restX = (points[i] - centerX) * cosA - (points[i + 2] - centerZ) * sinA
                    blend?.invoke(minOf(headX, neckX, restX), minOf(headY, neckY, points[i + 1]),
                        maxOf(headY, neckY, points[i + 1]))
                    i += 3
                }
            }
        }
        var right = Double.NEGATIVE_INFINITY; var left = Double.NEGATIVE_INFINITY
        var low = Double.POSITIVE_INFINITY; var high = Double.NEGATIVE_INFINITY
        forEachPosed { x, y ->
            if (x > right) right = x
            if (-x > left) left = -x
            if (y < low) low = y
            if (y > high) high = y
        }
        require(left.isFinite() && right.isFinite() && left > 0 && right > 0 && high > low) { "Invalid head reach" }
        val bottom = low - REACH_MARGIN
        val step = (high - low + 2 * REACH_MARGIN) / FACE_BANDS * (1 + 1e-9)
        val bandLeft = DoubleArray(FACE_BANDS) { Double.POSITIVE_INFINITY }
        forEachPosed(blend = { x, minY, maxY ->
            val first = ((minY - REACH_MARGIN - bottom) / step).toInt().coerceIn(0, FACE_BANDS - 1)
            val last = ((maxY + REACH_MARGIN - bottom) / step).toInt().coerceIn(0, FACE_BANDS - 1)
            for (band in first..last) bandLeft[band] = minOf(bandLeft[band], x)
        }) { _, _ -> }
        // Close gaps conservatively.
        for (band in bandLeft.indices) if (!bandLeft[band].isFinite()) bandLeft[band] = if (band > 0) bandLeft[band - 1] else -left
        return PoseFit(left + REACH_MARGIN, right + REACH_MARGIN,
            FaceProfile(bottom, step, bandLeft.map { it - REACH_MARGIN }))
    }
}
