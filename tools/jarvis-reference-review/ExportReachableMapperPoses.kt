package at.heinz.deviceai.referencecheck

import at.heinz.deviceai.avatar.*
import at.heinz.deviceai.avatar3d.AvatarGlbContract
import at.heinz.deviceai.avatar3d.AvatarMorphMapper

/** Reviewer-owned export of actual production reducer/mapper output. Synthetic input only.
 * A renderer must replay these joint/morph values at a fixed camera, not invent camera turns.
 */
fun main(args: Array<String>) {
    require(args.size == 1 && args[0].matches(Regex("[0-9a-f]{40}")))
    val records = ArrayList<String>()
    val mapper = AvatarMorphMapper(AvatarGlbContract.TARGETS)
    fun snapshot(sequence: String, frame: Int, pose: AvatarPose, seconds: Float, motion: Boolean = true) {
        mapper.update(pose, seconds, motion)
        val weights = AvatarGlbContract.TARGETS.indices.joinToString(",") {
            "\"${AvatarGlbContract.TARGETS[it]}\":${mapper.weights[it]}"
        }
        records += "{\"sequence\":\"$sequence\",\"frame\":$frame,\"seconds\":$seconds," +
            "\"state\":\"${pose.state}\",\"gazeObserved\":${pose.gazeObserved},\"motion\":$motion," +
            "\"headYaw\":${mapper.headYaw},\"headPitch\":${mapper.headPitch}," +
            "\"eyeYaw\":${mapper.eyeYaw},\"eyePitch\":${mapper.eyePitch},\"jawPitch\":${mapper.jawPitch}," +
            "\"weights\":{$weights}}"
    }
    val idle = AvatarPoseReducer()
    // 224 fixed-camera frames at 24 Hz include two complete real mapper blinks.
    for (frame in 0..223) snapshot("idle-24fps", frame, idle.pose(100_000L + frame * 1_000L / 24), frame / 24f)
    for ((frame, seconds) in listOf(0f, 1f, 2f, 4.42f, 4.51f, 4.6f, 6f, 8f, 9.11f, 9.2f).withIndex())
        snapshot("idle-anchors", frame, idle.pose(100_000L + (seconds * 1_000).toLong()), seconds)
    for ((frame, seconds) in listOf(459.95f, 459.96667f, 459.98334f, 0f, .0166667f, .0333333f).withIndex())
        snapshot("clock-wrap", frame, idle.pose(100_000), seconds)

    for ((index, signals) in listOf(
        AvatarRuntimeSignals(listening = true), AvatarRuntimeSignals(busy = true),
        AvatarRuntimeSignals(researching = true), AvatarRuntimeSignals(busy = true, learning = true),
        AvatarRuntimeSignals(awaitingApproval = true), AvatarRuntimeSignals(blocked = true)).withIndex()) {
        val reducer = AvatarPoseReducer(); val director = AvatarDirector(AvatarSink(reducer::apply)) { 200_000L }
        director.onRuntime(signals)
        snapshot("task-state", index, reducer.pose(200_000), 1f)
    }
    for ((index, gaze) in listOf(GazeTarget(-.5f, -.35f), GazeTarget(.5f, .35f), GazeTarget.OWNER).withIndex()) {
        val reducer = AvatarPoseReducer(); val director = AvatarDirector(AvatarSink(reducer::apply)) { 300_000L }
        require(director.onObservedGaze(gaze, 90, 0L))
        snapshot("observed-look", index, reducer.pose(300_001), 1f)
    }
    // Actual reducer energy smoothing and utterance ownership remain in this path.
    for ((index, viseme) in listOf(Viseme.AA, Viseme.PP, Viseme.O, Viseme.I, Viseme.U).withIndex()) {
        val reducer = AvatarPoseReducer()
        reducer.apply(AvatarEvent.StateChanged(AvatarState.SPEAKING, AvatarExpression.NEUTRAL, GazeTarget.OWNER, 400_000))
        reducer.apply(AvatarEvent.SpeechStarted("synthetic-$index", "synthetic", 400_000))
        for (i in 1..3) reducer.apply(AvatarEvent.AudioLevel("synthetic-$index", 1f, 400_000L + i))
        reducer.apply(AvatarEvent.VisemeCue("synthetic-$index", viseme, 1f, 200, 400_004))
        snapshot("speech-reachable", index * 2, reducer.pose(400_005), 1f)
        reducer.apply(AvatarEvent.SpeechEnded("synthetic-$index", true, 400_006))
        snapshot("speech-stopped", index * 2 + 1, reducer.pose(400_007), 1f)
    }
    snapshot("reduced-motion", 0, idle.pose(500_000), 4.51f, false)
    println("{\"schema\":\"actual-production-mapper-pose-export\",\"version\":1," +
        "\"sourceSha\":\"${args[0]}\",\"inputOrigin\":\"SYNTHETIC_CONTRACT_INPUT\"," +
        "\"notDeviceOrAudioEvidence\":true,\"fixedCameraRequired\":true,\"frames\":[${records.joinToString(",")}]}" )
}
