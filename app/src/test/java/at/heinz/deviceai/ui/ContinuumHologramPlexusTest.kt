package at.heinz.deviceai.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/** The hologram's spatial network: deterministic geometry, one shared rotation, exact arc mapping, bounded work. */
class ContinuumHologramPlexusTest {
    private val cx = 206f
    private val cy = 117f
    private val r = 101f

    @Test fun geometryIsDeterministicAndInsideItsShells() {
        val a = ContinuumHologramPlexus(40); val b = ContinuumHologramPlexus(40)
        a.project(.7f, .3f, cx, cy, r, 3f); b.project(.7f, .3f, cx, cy, r, 3f)
        for (i in 0 until a.nodeCount) {
            assertEquals(a.sx[i], b.sx[i], 0f); assertEquals(a.sy[i], b.sy[i], 0f)
            assertTrue(a.depth[i] in -1f..1f)
            val d = sqrt((a.sx[i] - cx) * (a.sx[i] - cx) + (a.sy[i] - cy) * (a.sy[i] - cy)) / r
            if (a.layer[i] != ContinuumHologramPlexus.Layer.HALO) assertTrue("node $i projects inside the glass ($d)", d <= .971f)
            else assertTrue("halo node $i stays near the sphere ($d)", d <= 1.71f)
        }
        for (i in 0 until a.dustCount) {
            val d = sqrt((a.dustX[i] - cx) * (a.dustX[i] - cx) + (a.dustY[i] - cy) * (a.dustY[i] - cy)) / r
            assertTrue("dust $i inside the glass ($d)", d <= .951f)
        }
    }

    @Test fun linksAreValidUniqueAndEveryNodeIsConnected() {
        val p = ContinuumHologramPlexus(40)
        val seen = HashSet<Long>()
        val degree = IntArray(p.nodeCount)
        for (e in 0 until p.edgeCount) {
            val a = p.edgeA[e]; val b = p.edgeB[e]
            assertTrue(a in 0 until p.nodeCount && b in 0 until p.nodeCount && a < b)
            assertTrue("duplicate link $a-$b", seen.add((a.toLong() shl 32) or b.toLong()))
            assertEquals("halo links stay in the halo", p.layer[a] == ContinuumHologramPlexus.Layer.HALO,
                p.layer[b] == ContinuumHologramPlexus.Layer.HALO)
            degree[a]++; degree[b]++
        }
        // the shells inside the glass form one network; sparse halo nodes outside may float as single particles
        for (i in 0 until p.nodeCount) if (p.layer[i] != ContinuumHologramPlexus.Layer.HALO) assertTrue("node $i is linked", degree[i] > 0)
        assertTrue("links are bounded (k nearest, no all-pairs)", p.edgeCount < p.nodeCount * 4)
    }

    @Test fun oneFullTurnGivesTheSameImage() {
        val p = ContinuumHologramPlexus(28)
        p.project(.4f, .3f, cx, cy, r)
        val x = p.sx.copyOf(); val y = p.sy.copyOf()
        p.project(.4f + 2f * PI.toFloat(), .3f, cx, cy, r)
        for (i in x.indices) { assertEquals(x[i], p.sx[i], 1e-3f); assertEquals(y[i], p.sy[i], 1e-3f) }
    }

    @Test fun lightArcsAreExactlyTheProjectedCircles() {
        val p = ContinuumHologramPlexus(28)
        for (yaw in listOf(0f, .9f, 2.3f, 4.1f)) {
            p.project(yaw, .35f, cx, cy, r, 2f)
            for (i in 0 until p.arcCount) {
                val nx = p.arcNormalX[i]; val ny = p.arcNormalY[i]; val nz = p.arcNormalZ[i]
                val l = sqrt(nx * nx + ny * ny)
                if (l < 1e-3f) continue
                val ux = -ny / l; val uy = nx / l                       // u = z x n, unit, in the screen plane
                val vx = -nz * uy; val vy = nz * ux; val vz = nx * uy - ny * ux   // v = n x u
                val rho = r * p.arcRadius[i]
                val alpha = Math.toRadians(p.arcAngle[i].toDouble()).toFloat()
                for (deg in 0 until 360 step 15) {
                    val t = Math.toRadians(deg.toDouble()).toFloat()
                    // the 3D circle point, projected orthographically
                    val px = cx + rho * (cos(t) * ux + sin(t) * vx); val py = cy + rho * (cos(t) * uy + sin(t) * vy)
                    // the point the renderer draws: parametric angle t (or -t) on the rotated ellipse
                    val te = if (p.arcMirror[i]) -t else t
                    val ex = rho * cos(te); val ey = rho * p.arcRatio[i] * sin(te)
                    val qx = cx + cos(alpha) * ex - sin(alpha) * ey; val qy = cy + sin(alpha) * ex + cos(alpha) * ey
                    assertEquals("arc $i at $deg degrees (x)", px, qx, 1e-2f)
                    assertEquals("arc $i at $deg degrees (y)", py, qy, 1e-2f)
                    // circle angles 0..180 are the front half
                    val z = rho * sin(t) * vz
                    if (deg in 1..179) assertTrue(z > 0f) else if (deg in 181..359) assertTrue(z < 0f)
                }
            }
        }
    }

    @Test fun aFrameHasABoundedConstantWorkloadAndLeavesNoLightModeBehind() {
        for (geometry in listOf(ContinuumHologramRenderer.Geometry.PHONE, ContinuumHologramRenderer.Geometry.FOLD)) {
            val renderer = ContinuumHologramRenderer()
            renderer.geometry = geometry
            renderer.layout(412f, 307f)
            val counts = (0 until 3).map { k ->
                val painter = CountingPainter()
                renderer.draw(painter, ContinuumHologramRenderer.Frame.still(ContinuumHologramRenderer.Mode.READY, phase = 1.3f + k * 7.1f))
                assertFalse("additive light mode is switched off after a frame", painter.lightMode)
                assertFalse("text is never drawn additively", painter.textWhileAdditive)
                painter.calls
            }
            // links, nodes, dust and arcs are fixed sets: every frame does about the same work, far below an all-pairs network
            assertTrue("$geometry: ${counts.max()} draw calls", counts.max() < 2600)
            assertTrue("$geometry: frames differ only by visibility culling ($counts)", counts.max() - counts.min() < 220)
        }
    }

    private class CountingPainter : ContinuumPainter {
        var calls = 0
        var lightMode = false
        var textWhileAdditive = false
        override fun setAdditive(on: Boolean) { lightMode = on }
        override fun save() {}
        override fun restore() {}
        override fun translate(dx: Float, dy: Float) {}
        override fun scale(sx: Float, sy: Float, px: Float, py: Float) {}
        override fun rotate(degrees: Float, px: Float, py: Float) {}
        override fun clipRect(left: Float, top: Float, right: Float, bottom: Float) {}
        override fun clipRoundRect(left: Float, top: Float, right: Float, bottom: Float, radius: Float) {}
        override fun fillRect(left: Float, top: Float, right: Float, bottom: Float, fill: Fill, alpha: Float) { calls++ }
        override fun fillRoundRect(left: Float, top: Float, right: Float, bottom: Float, radius: Float, fill: Fill, alpha: Float) { calls++ }
        override fun strokeRoundRect(left: Float, top: Float, right: Float, bottom: Float, radius: Float, width: Float, fill: Fill, alpha: Float) { calls++ }
        override fun fillOval(left: Float, top: Float, right: Float, bottom: Float, fill: Fill, alpha: Float) { calls++ }
        override fun strokeOval(left: Float, top: Float, right: Float, bottom: Float, width: Float, fill: Fill, alpha: Float) { calls++ }
        override fun strokeArc(left: Float, top: Float, right: Float, bottom: Float, startDegrees: Float, sweepDegrees: Float,
                               width: Float, fill: Fill, alpha: Float, roundCap: Boolean) { calls++ }
        override fun drawLine(x0: Float, y0: Float, x1: Float, y1: Float, width: Float, fill: Fill, alpha: Float, roundCap: Boolean) { calls++ }
        override fun strokeSegments(points: FloatArray, count: Int, width: Float, fill: Fill, alpha: Float) {
            assertTrue("bounded native batch", count <= 74 * 4 && count % 4 == 0)
            calls++ // Android Canvas.drawLines is one submission, not one call per segment.
        }
        override fun fillPath(path: VectorPath, fill: Fill, alpha: Float) { calls++ }
        override fun strokePath(path: VectorPath, width: Float, fill: Fill, alpha: Float, roundCap: Boolean) { calls++ }
        override fun drawText(text: String, x: Float, y: Float, style: TextStyle, fill: Fill, align: TextAlign, alpha: Float) {
            calls++; if (lightMode) textWhileAdditive = true
        }
        override fun measureText(text: String, style: TextStyle): Float = text.length * style.size * .5f
        override fun capHeight(style: TextStyle): Float = style.size * .7f
    }
}
