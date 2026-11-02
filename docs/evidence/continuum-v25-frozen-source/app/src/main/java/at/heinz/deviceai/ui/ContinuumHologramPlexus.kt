package at.heinz.deviceai.ui

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.cbrt
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * The spatial network of the Continuum core: nodes on two jittered Fibonacci shells inside the glass sphere, a sparse
 * halo around it and dust in the volume. One rotation matrix turns all of them, so they move as a single 3D object.
 *
 * Geometry and links are built once: every node is linked to its nearest neighbours by 3D distance (never all pairs
 * per frame). [project] only rotates and projects into preallocated arrays, so a frame allocates nothing. Positions
 * are deterministic (hash jitter, no randomness per frame). Units: sphere radius; screen y grows downwards.
 */
class ContinuumHologramPlexus(haloNodes: Int) {
    enum class Layer { OUTER, INNER, HALO }

    val nodeCount: Int
    val layer: Array<Layer>
    private val bx: FloatArray
    private val by: FloatArray
    private val bz: FloatArray

    /** Projected node positions (px) and depth (-1 back .. +1 front, rotated z over the node's shell radius). */
    val sx: FloatArray
    val sy: FloatArray
    val depth: FloatArray

    val edgeCount: Int
    val edgeA: IntArray
    val edgeB: IntArray

    val dustCount = 420
    private val dx = FloatArray(dustCount)
    private val dy = FloatArray(dustCount)
    private val dz = FloatArray(dustCount)
    val dustX = FloatArray(dustCount)
    val dustY = FloatArray(dustCount)
    val dustDepth = FloatArray(dustCount)
    /** 0 cool (cyan/blue), 1 violet, 2 warm (pink/orange cluster in the upper right, as in the reference). */
    val dustTint = IntArray(dustCount)
    val dustSize = FloatArray(dustCount)

    /**
     * Light arcs: segments of great circles on concentric spheres, turned by the same rotation. Orthographically a
     * circle of radius rho with unit normal n projects to an ellipse with semi-axes rho and rho*|n.z|; with the major
     * axis u = z x n, a circle angle t lands on the ellipse's parametric angle t (or -t when n.z < 0) and lies in front
     * of the sphere centre for t in 0..180 degrees. So each arc is drawn as a plain elliptic arc, without allocation.
     */
    val arcCount = 8
    private val anx = FloatArray(arcCount)
    private val any = FloatArray(arcCount)
    private val anz = FloatArray(arcCount)
    val arcRadius = floatArrayOf(.62f, .95f, .78f, .88f, .7f, .92f, .83f, .74f)
    private val arcStart = FloatArray(arcCount)
    private val arcSweep = FloatArray(arcCount)
    private val arcSpeed = FloatArray(arcCount)
    /** Per frame: rotation of the projected ellipse (degrees), minor/major ratio, mirrored angle direction, and the
     * arc's current circle-angle range [arcFrom, arcFrom + arcSweepNow] in degrees (0..540). */
    val arcAngle = FloatArray(arcCount)
    val arcRatio = FloatArray(arcCount)
    val arcMirror = BooleanArray(arcCount)
    val arcFrom = FloatArray(arcCount)
    val arcSweepNow = FloatArray(arcCount)
    /** Rotated arc normals of the current frame (for tests of the ellipse mapping). */
    internal val arcNormalX = FloatArray(arcCount)
    internal val arcNormalY = FloatArray(arcCount)
    internal val arcNormalZ = FloatArray(arcCount)

    init {
        val outer = 128; val inner = 48
        nodeCount = outer + inner + haloNodes
        layer = Array(nodeCount) { i -> if (i < outer) Layer.OUTER else if (i < outer + inner) Layer.INNER else Layer.HALO }
        bx = FloatArray(nodeCount); by = FloatArray(nodeCount); bz = FloatArray(nodeCount)
        sx = FloatArray(nodeCount); sy = FloatArray(nodeCount); depth = FloatArray(nodeCount)
        fun place(start: Int, n: Int, rMin: Float, rMax: Float, salt: Int) {
            for (k in 0 until n) {
                val i = start + k
                // Fibonacci direction, jittered so the lattice reads as an organic network instead of a grid
                val yy = 1f - 2f * (k + .5f) / n
                val ring = sqrt(1f - yy * yy)
                val a = k * GOLDEN_ANGLE + salt
                var x = cos(a) * ring + (hash(i, salt + 1) - .5f) * .34f
                var y = yy + (hash(i, salt + 2) - .5f) * .34f
                var z = sin(a) * ring + (hash(i, salt + 3) - .5f) * .34f
                val len = sqrt(x * x + y * y + z * z)
                val radius = rMin + (rMax - rMin) * hash(i, salt + 4)
                x *= radius / len; y *= radius / len; z *= radius / len
                bx[i] = x; by[i] = y; bz[i] = z
            }
        }
        place(0, outer, .9f, .97f, 11)
        place(outer, inner, .5f, .74f, 23)
        place(outer + inner, haloNodes, 1.1f, 1.7f, 37)
        val pairs = LinkedHashSet<Long>()
        fun link(i: Int, candidates: IntRange, k: Int, maxDistance: Float) {
            val best = IntArray(k) { -1 }; val bestD = FloatArray(k) { Float.MAX_VALUE }
            for (j in candidates) {
                if (j == i) continue
                val ddx = bx[i] - bx[j]; val ddy = by[i] - by[j]; val ddz = bz[i] - bz[j]
                val d = ddx * ddx + ddy * ddy + ddz * ddz
                if (d > maxDistance * maxDistance) continue
                var slot = -1
                for (s in 0 until k) if (d < bestD[s] && (slot < 0 || bestD[s] > bestD[slot])) slot = s
                if (slot >= 0) { best[slot] = j; bestD[slot] = d }
            }
            for (j in best) if (j >= 0) pairs += (minOf(i, j).toLong() shl 32) or maxOf(i, j).toLong()
        }
        val shells = 0 until outer + inner
        for (i in 0 until outer) link(i, 0 until outer, 3, .55f)
        for (i in outer until outer + inner) link(i, shells, 3, .7f)
        for (i in outer + inner until nodeCount) link(i, outer + inner until nodeCount, 2, .8f)
        edgeCount = pairs.size
        edgeA = IntArray(edgeCount); edgeB = IntArray(edgeCount)
        for ((e, key) in pairs.withIndex()) { edgeA[e] = (key shr 32).toInt(); edgeB[e] = (key and 0xffffffffL).toInt() }
        for (i in 0 until dustCount) {
            val warm = i % 7 == 0
            var x: Float; var y: Float; var z: Float
            if (warm) {
                // a loose warm cluster towards the viewer in the upper right
                x = .55f + (hash(i, 51) - .5f) * .7f; y = -.45f + (hash(i, 52) - .5f) * .7f; z = .45f + (hash(i, 53) - .5f) * .6f
            } else {
                val u = hash(i, 54) * 2f - 1f; val t = hash(i, 55) * 2f * PI.toFloat()
                val s = sqrt(1f - u * u); x = s * cos(t); y = u; z = s * sin(t)
            }
            val len = sqrt(x * x + y * y + z * z).coerceAtLeast(1e-3f)
            val radius = if (warm) minOf(.92f, len) else .95f * cbrt(hash(i, 56))
            dx[i] = x / len * radius; dy[i] = y / len * radius; dz[i] = z / len * radius
            dustTint[i] = if (warm) 2 else if (i % 5 == 0) 1 else 0
            dustSize[i] = if (i % 13 == 0) 1.15f else if (i % 3 == 0) .72f else .45f
        }
        for (i in 0 until arcCount) {
            val u = hash(i, 61) * 2f - 1f; val t = hash(i, 62) * 2f * PI.toFloat(); val s = sqrt(1f - u * u)
            anx[i] = s * cos(t); any[i] = u; anz[i] = s * sin(t)
            arcStart[i] = hash(i, 63) * 360f
            arcSweep[i] = 70f + hash(i, 64) * 80f
            arcSpeed[i] = (4f + hash(i, 65) * 6f) * (if (i % 2 == 0) 1f else -1f)
        }
    }

    /**
     * Rotates everything by yaw (around the vertical axis) then pitch (towards the viewer) and projects
     * orthographically onto a sphere of radius [r] px centred at ([cx], [cy]); arcs travel along their circles
     * with the animation [phase].
     */
    fun project(yaw: Float, pitch: Float, cx: Float, cy: Float, r: Float, phase: Float = 0f) {
        val cy0 = cos(yaw); val sy0 = sin(yaw); val cp = cos(pitch); val sp = sin(pitch)
        for (i in 0 until nodeCount) {
            val x1 = bx[i] * cy0 + bz[i] * sy0
            val z1 = -bx[i] * sy0 + bz[i] * cy0
            val y2 = by[i] * cp - z1 * sp
            val z2 = by[i] * sp + z1 * cp
            sx[i] = cx + x1 * r; sy[i] = cy + y2 * r
            val shell = sqrt(bx[i] * bx[i] + by[i] * by[i] + bz[i] * bz[i])
            depth[i] = (z2 / shell).coerceIn(-1f, 1f)
        }
        for (i in 0 until dustCount) {
            val x1 = dx[i] * cy0 + dz[i] * sy0
            val z1 = -dx[i] * sy0 + dz[i] * cy0
            val y2 = dy[i] * cp - z1 * sp
            dustX[i] = cx + x1 * r; dustY[i] = cy + y2 * r
            dustDepth[i] = (dy[i] * sp + z1 * cp).coerceIn(-1f, 1f)
        }
        for (i in 0 until arcCount) {
            val x1 = anx[i] * cy0 + anz[i] * sy0
            val z1 = -anx[i] * sy0 + anz[i] * cy0
            val ny = any[i] * cp - z1 * sp
            val nz = any[i] * sp + z1 * cp
            arcNormalX[i] = x1; arcNormalY[i] = ny; arcNormalZ[i] = nz
            val l = sqrt(x1 * x1 + ny * ny)
            // major axis u = z x n = (-n.y, n.x) / |n.xy|; a circle seen face-on has no preferred axis
            arcAngle[i] = if (l < 1e-4f) 0f else Math.toDegrees(kotlin.math.atan2(x1 / l, -ny / l).toDouble()).toFloat()
            arcRatio[i] = kotlin.math.abs(nz)
            arcMirror[i] = nz < 0f
            arcFrom[i] = ((arcStart[i] + phase * arcSpeed[i]) % 360f + 360f) % 360f
            arcSweepNow[i] = arcSweep[i]
        }
    }

    private companion object {
        const val GOLDEN_ANGLE = 2.39996323f
        /** Deterministic jitter in [0, 1). */
        fun hash(i: Int, salt: Int): Float {
            val v = sin(i * 12.9898 + salt * 78.233) * 43758.5453
            return (v - kotlin.math.floor(v)).toFloat()
        }
    }
}
