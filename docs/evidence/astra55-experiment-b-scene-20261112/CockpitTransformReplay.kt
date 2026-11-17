package at.heinz.deviceai.avatar3d

import at.heinz.deviceai.ui.CinematicWorkstationLayout
import at.heinz.deviceai.ui.CockpitMonitorMotion
import at.heinz.deviceai.ui.JarvisShellLayout.Slot
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.json.JSONArray
import org.json.JSONObject

data class ReplayBounds(val left: Float, val top: Float, val right: Float, val bottom: Float)
data class ReplayStep(val frame: CockpitStageFrame, val bounds: ReplayBounds, val phase: Float, val motion: Boolean)
class RecordingEngine(val transformManager: RecordingTransformManager)

class RecordingTransformManager(graph: ReplayGraph) {
    val locals = graph.rest.mapValues { it.value.copyOf() }.toMutableMap()
    val trace = mutableListOf<Pair<Int, FloatArray>>()
    fun setTransform(instance: Int, matrix: FloatArray) {
        check(locals.containsKey(instance))
        locals[instance] = matrix.copyOf(16)
        trace += instance to matrix.copyOf(16)
    }
}

class ReplayGraph {
    val rest = linkedMapOf<Int, FloatArray>()
    private val parents = mutableMapOf<Int, Int>()
    private val names = mutableMapOf<Int, String>()
    init {
        for (path in listOf("cockpit-workstation.glb", "continuum-orb.glb")) {
            val bytes = File("app/src/main/assets/cinema3d", path).readBytes()
            val size = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).getInt(12)
            val nodes = JSONObject(String(bytes, 20, size, Charsets.UTF_8)).getJSONArray("nodes")
            val first = rest.size + 1
            repeat(nodes.length()) { index ->
                val node = nodes.getJSONObject(index)
                names[first + index] = node.optString("name")
                rest[first + index] = local(node)
                node.optJSONArray("children")?.let { children ->
                    repeat(children.length()) { parents[first + children.getInt(it)] = first + index }
                }
            }
        }
    }
    fun id(name: String) = names.filterValues { it == name }.keys.single()
    fun worlds(locals: Map<Int, FloatArray>): Map<Int, FloatArray> {
        val resolved = mutableMapOf<Int, FloatArray>()
        fun world(id: Int): FloatArray {
            resolved[id]?.let { return it }
            val local = locals.getValue(id)
            val result = parents[id]?.let { parent -> multiply(world(parent), local) } ?: local.copyOf()
            resolved[id] = result
            return result
        }
        rest.keys.forEach { world(it) }
        return resolved
    }
    private fun multiply(left: FloatArray, right: FloatArray) = FloatArray(16) { at ->
        var sum = 0f
        for (k in 0..3) sum += left[k * 4 + at % 4] * right[at / 4 * 4 + k]
        sum
    }
    private fun local(node: JSONObject): FloatArray {
        node.optJSONArray("matrix")?.let { values -> return FloatArray(16) { values.getDouble(it).toFloat() } }
        val q = node.optJSONArray("rotation")
        val x = q?.getDouble(0) ?: 0.0; val y = q?.getDouble(1) ?: 0.0
        val z = q?.getDouble(2) ?: 0.0; val w = q?.getDouble(3) ?: 1.0
        val values = doubleArrayOf(
            1 - 2*y*y - 2*z*z, 2*x*y + 2*z*w, 2*x*z - 2*y*w, 0.0,
            2*x*y - 2*z*w, 1 - 2*x*x - 2*z*z, 2*y*z + 2*x*w, 0.0,
            2*x*z + 2*y*w, 2*y*z - 2*x*w, 1 - 2*x*x - 2*y*y, 0.0,
            0.0, 0.0, 0.0, 1.0)
        for (axis in 0..2) {
            val scale = node.optJSONArray("scale")?.getDouble(axis) ?: 1.0
            for (row in 0..2) values[axis * 4 + row] *= scale
            values[12 + axis] = node.optJSONArray("translation")?.getDouble(axis) ?: 0.0
        }
        return values.map(Double::toFloat).toFloatArray()
    }
}

object CockpitTransformReplay {
    private fun exact(left: FloatArray, right: FloatArray) =
        left.size == right.size && left.indices.all { left[it].toRawBits() == right[it].toRawBits() }

    private fun step(width: Float, height: Float, focus: Float, phase: Float, motion: Boolean = true): ReplayStep {
        val shell = CinematicWorkstationLayout.create(width, height)
        val scene = requireNotNull(shell[Slot.AVATAR])
        val monitor = requireNotNull(shell[Slot.WORK_PROCESS])
        val orb = requireNotNull(shell.orbBounds)
        val base = CockpitStageFrame(scene.width.toInt().toFloat(), scene.height.toInt().toFloat(),
            monitor.left - scene.left, monitor.top - scene.top,
            minOf(scene.width.toInt().toFloat(), monitor.right - scene.left),
            minOf(scene.height.toInt().toFloat(), monitor.bottom - scene.top))
        return ReplayStep(CockpitMonitorMotion.pose(base, focus), ReplayBounds(orb.left - scene.left,
            orb.top - scene.top, orb.right - scene.left, orb.bottom - scene.top), phase, motion)
    }

    private fun run(name: String, steps: List<ReplayStep>): JSONObject {
        val graph = ReplayGraph()
        val a = RecordingTransformManager(graph)
        val b = RecordingTransformManager(graph)
        val disabled = RecordingTransformManager(graph)
        val baseline = BaselineStage(graph, a)
        val candidate = CandidateStage(graph, b, true)
        val fallback = CandidateStage(graph, disabled, false)
        var localComponents = 0L
        var worldComponents = 0L
        var projectedCoordinates = 0L
        var movingParentWithFixedChild = 0
        val ring = graph.id("orb-orbit-0")
        var previousRingLocal: FloatArray? = null
        var previousRingWorld: FloatArray? = null
        for ((index, state) in steps.withIndex()) {
            baseline.update(state.frame, state.bounds, state.phase, state.motion)
            candidate.update(state.frame, state.bounds, state.phase, state.motion)
            fallback.update(state.frame, state.bounds, state.phase, state.motion)
            val wa = graph.worlds(a.locals)
            val wb = graph.worlds(b.locals)
            val wd = graph.worlds(disabled.locals)
            for (id in graph.rest.keys) {
                check(exact(a.locals.getValue(id), b.locals.getValue(id))) { "$name/$index local $id" }
                check(exact(a.locals.getValue(id), disabled.locals.getValue(id))) { "$name/$index disabled local $id" }
                check(exact(wa.getValue(id), wb.getValue(id))) { "$name/$index world $id" }
                check(exact(wa.getValue(id), wd.getValue(id))) { "$name/$index disabled world $id" }
                localComponents += 16
                worldComponents += 16
                val ma = wa.getValue(id)
                val mb = wb.getValue(id)
                if (ma[14] < state.frame.distance * state.frame.worldScale) {
                    val pa = state.frame.project(ma[12], ma[13], ma[14])
                    val pb = state.frame.project(mb[12], mb[13], mb[14])
                    check(pa.first.toRawBits() == pb.first.toRawBits() && pa.second.toRawBits() == pb.second.toRawBits())
                    projectedCoordinates += 2
                }
            }
            val local = b.locals.getValue(ring)
            val world = wb.getValue(ring)
            if (previousRingLocal?.let { exact(it, local) } == true &&
                previousRingWorld?.let { !exact(it, world) } == true) movingParentWithFixedChild++
            previousRingLocal = local.copyOf()
            previousRingWorld = world.copyOf()
        }
        check(a.trace.size == steps.size * 9) { "Baseline changed its nine-write contract" }
        check(a.trace.size == disabled.trace.size)
        a.trace.zip(disabled.trace).forEach { (first, second) ->
            check(first.first == second.first && exact(first.second, second.second))
        }
        val reduction = 1.0 - b.trace.size.toDouble() / a.trace.size
        if (name == "twelve-current-keyframes" || name == "continuous-current-flight") check(reduction >= .20)
        if (name == "parent-resize-and-side-visibility") check(movingParentWithFixedChild > 0)
        return JSONObject().put("name", name).put("frames", steps.size).put("nodes_per_frame", graph.rest.size)
            .put("baseline_upload_calls", a.trace.size).put("candidate_upload_calls", b.trace.size)
            .put("disabled_upload_calls", disabled.trace.size).put("upload_reduction", reduction)
            .put("local_components_exact", localComponents).put("world_components_exact", worldComponents)
            .put("projected_coordinates_exact", projectedCoordinates)
            .put("moving_parent_fixed_child_frames", movingParentWithFixedChild)
            .put("disabled_matches_baseline_trace", true).put("matrix_comparison", "FLOAT_RAW_BITS_NO_EPSILON")
    }

    @JvmStatic fun main(args: Array<String>) {
        val profiles = listOf(884f to 760f, 412f to 915f)
        val incoming = CockpitMonitorMotion.flight(0f, 0f, 1f)
        val outgoing = CockpitMonitorMotion.flight(1f, 0f, 0f)
        fun phase(ms: Float) = .4f + ms / 1000f * (.28f + .18f * .72f)
        fun focus(ms: Float) = if (ms <= 420f) incoming.sample(ms).focus else outgoing.sample(ms - 420f).focus
        val keys = profiles.flatMap { (w, h) ->
            listOf(0f, 105f, 210f, 420f, 630f, 840f).map { ms -> step(w, h, focus(ms), phase(ms)) }
        }
        val flight = profiles.flatMap { (w, h) ->
            ((0..50).map { it * (1000f / 60f) } + listOf(420f, 840f)).distinct().sorted()
                .map { ms -> step(w, h, focus(ms), phase(ms)) }
        }
        val frozen = profiles.flatMap { (w, h) -> List(30) { step(w, h, .5f, 8f + it, false) } }
        val resized = listOf(884f to 760f, 600f to 500f, 640f to 500f, 700f to 500f,
            720f to 500f, 360f to 760f, 320f to 500f, 412f to 915f, 884f to 760f)
            .map { (w, h) -> step(w, h, .5f, 1.3f) }
        val reversed = profiles.flatMap { (w, h) ->
            val middle = incoming.sample(170f)
            val reverse = CockpitMonitorMotion.flight(middle.focus, middle.velocityPerMs, 0f)
            listOf(0f, .01f, .2f, .5f, 1f).map { t ->
                step(w, h, reverse.sample(t * reverse.durationMs).focus, phase(170f + t * reverse.durationMs))
            }
        }
        val results = JSONArray(listOf(run("twelve-current-keyframes", keys), run("continuous-current-flight", flight),
            run("motion-disabled-repeated-frames", frozen), run("parent-resize-and-side-visibility", resized),
            run("velocity-preserving-reversal", reversed)))
        val report = JSONObject().put("evidence", "SOURCE_EXTRACTED_STAGE_TRANSFORM_BLOCK_HOST_SPY")
            .put("workloads", results).put("decision", "ACCEPT_CALL_BUDGET_GATE")
            .put("native_gpu", "NOT_RUN").put("device_fps", "NOT_MEASURED").put("thermal", "NOT_MEASURED")
        File(args.single()).writeText(report.toString(2) + "\n")
        println(report.toString(2))
    }
}
