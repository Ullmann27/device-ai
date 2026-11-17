package at.heinz.deviceai.avatar3d

import at.heinz.deviceai.avatar.AvatarExpression
import at.heinz.deviceai.avatar.AvatarPose
import at.heinz.deviceai.avatar.AvatarState
import at.heinz.deviceai.avatar.Viseme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Jaw/neck transition (owner report: the jaw looked detached). The packaged face skin blends Neck→Head inside the
 * chin/lower-lip band (y ≈ 0.20–0.24 m); turning only the Head joint stretched exactly that band. Independent
 * linear-blend skinning of the real GLB ([GlbSkinningRig]); host geometry, not a native render.
 */
class AvatarNeckSplitTest {
    private val rig = GlbSkinningRig.packaged()
    private val face = rig.meshes.first()
    private val rest = rig.skinned(emptyMap()).first()

    /** Unique triangle edges whose midpoint lies in the neck/jaw band 0.12–0.27 m. */
    private val edges: List<IntArray> by lazy {
        val seen = HashSet<Long>(); val out = ArrayList<IntArray>()
        for (t in 0 until face.indices.size / 3) for (k in 0..2) {
            val a = face.indices[t * 3 + k]; val b = face.indices[t * 3 + (k + 1) % 3]
            val lo = minOf(a, b).toLong(); val hi = maxOf(a, b).toLong()
            if (!seen.add(lo * 1_000_000 + hi)) continue
            val y = (rest[a * 3 + 1] + rest[b * 3 + 1]) / 2
            if (y in .12..0.27 && length(rest, a, b) > 1e-5) out += intArrayOf(a, b)
        }
        out
    }

    private fun length(p: DoubleArray, a: Int, b: Int): Double {
        val dx = p[a * 3] - p[b * 3]; val dy = p[a * 3 + 1] - p[b * 3 + 1]; val dz = p[a * 3 + 2] - p[b * 3 + 2]
        return sqrt(dx * dx + dy * dy + dz * dz)
    }

    /** Worst 99th-percentile edge strain |L/L0 − 1| over the full reachable pose range. */
    private fun worstStrain(share: Double): Double = GlbSkinningRig.fullRangePoses().maxOf { (yaw, pitch) ->
        val posed = rig.turn(yaw, pitch, share).first()
        val strains = edges.map { (a, b) -> abs(length(posed, a, b) / length(rest, a, b) - 1) }.sorted()
        strains[(strains.size * .99).toInt().coerceAtMost(strains.size - 1)]
    }

    @Test fun splittingTheTurnRemovesTheJawTear() {
        assertTrue("band must contain real geometry", edges.size > 10_000)
        val headOnly = worstStrain(0.0)
        val split = worstStrain(AvatarMorphMapper.NECK_SHARE.toDouble())
        assertTrue("Head-only turn reproduces the torn jaw band: $headOnly", headOnly > 1.0)
        assertTrue("Neck/Head split keeps the band coherent: $split", split < .50)
        assertTrue("at least 2.5× less stretch ($headOnly → $split)", headOnly / split >= 2.5)
    }

    private fun pose(gazeX: Float, gazeY: Float, state: AvatarState = AvatarState.LISTENING, observed: Boolean = true) =
        AvatarPose(state, AvatarExpression.NEUTRAL, gazeX, gazeY, 0f, Viseme.SIL, 0f, 0f, gazeObserved = observed)

    @Test fun mapperSplitsTheSameTotalTurnInMotionAndAtRest() {
        val map = AvatarMorphMapper(AvatarGlbContract.TARGETS)
        for ((motion, seconds) in listOf(false to 0f, true to 1f, true to 1.5f, true to 3f)) {
            map.update(pose(.8f, -.5f), seconds, motion)
            assertEquals(map.headYaw * AvatarMorphMapper.NECK_SHARE, map.neckYaw, 1e-6f)
            assertEquals(map.headPitch * AvatarMorphMapper.NECK_SHARE, map.neckPitch, 1e-6f)
        }
        // The idle sway also travels through the split, and stays inside the framed range.
        val idle = AvatarMorphMapper(AvatarGlbContract.TARGETS)
        var maxYaw = 0f; var maxPitch = 0f
        for (frame in 0..9_200) {
            idle.update(pose(1f, -1f, AvatarState.IDLE, observed = false), frame * .05f, true)
            maxYaw = maxOf(maxYaw, abs(idle.headYaw)); maxPitch = maxOf(maxPitch, abs(idle.headPitch))
            assertEquals(idle.headYaw * AvatarMorphMapper.NECK_SHARE, idle.neckYaw, 1e-6f)
        }
        assertTrue("yaw $maxYaw within framed range", maxYaw <= AvatarMorphMapper.MAX_HEAD_YAW_DEGREES + 1e-4f)
        assertTrue("pitch $maxPitch within framed range", maxPitch <= AvatarMorphMapper.MAX_HEAD_PITCH_DEGREES + 1e-4f)
        assertTrue("the framed range is actually used", maxYaw > AvatarMorphMapper.HEAD_YAW_DEGREES)
    }
}
