package at.heinz.deviceai.referencecheck

import at.heinz.deviceai.avatar.*
import at.heinz.deviceai.avatar3d.AvatarGlbContract
import at.heinz.deviceai.avatar3d.AvatarMorphMapper

/** Additional family frozen before a composition repair; actual reducers and mapper only. */
fun main(args: Array<String>) {
    require(args.size == 1 && args[0].matches(Regex("[0-9a-f]{40}")))
    val records = ArrayList<String>()
    val mapper = AvatarMorphMapper(AvatarGlbContract.TARGETS)
    var index = 0
    fun snapshot(pose: AvatarPose, sequence: String, level: Float, cue: Float, viseme: Viseme) {
        mapper.update(pose, 1f, true)
        val weights = AvatarGlbContract.TARGETS.indices.joinToString(",") {
            "\"${AvatarGlbContract.TARGETS[it]}\":${mapper.weights[it]}"
        }
        records += "{\"sequence\":\"$sequence\",\"frame\":${index++},\"seconds\":1.0," +
            "\"state\":\"${pose.state}\",\"gazeObserved\":${pose.gazeObserved},\"motion\":true," +
            "\"syntheticInput\":{\"audioLevel\":$level,\"audioCallbacks\":3,\"cueWeight\":$cue,\"viseme\":\"$viseme\"}," +
            "\"headYaw\":${mapper.headYaw},\"headPitch\":${mapper.headPitch}," +
            "\"eyeYaw\":${mapper.eyeYaw},\"eyePitch\":${mapper.eyePitch},\"jawPitch\":${mapper.jawPitch}," +
            "\"weights\":{$weights}}"
    }
    snapshot(AvatarPoseReducer().pose(100_000), "idle-anchors", 0f, 0f, Viseme.SIL)
    for (v in Viseme.entries.filter { it != Viseme.SIL }) for (level in listOf(0f, .18f, .63f, 1f))
        for (cue in listOf(.45f, 1f)) {
            val reducer = AvatarPoseReducer()
            val base = 100_000L + index * 1_000L
            val id = "synthetic-$index"
            reducer.apply(AvatarEvent.StateChanged(AvatarState.SPEAKING, AvatarExpression.NEUTRAL, GazeTarget.OWNER, base))
            reducer.apply(AvatarEvent.SpeechStarted(id, "synthetic", base))
            for (i in 1..3) reducer.apply(AvatarEvent.AudioLevel(id, level, base + i))
            reducer.apply(AvatarEvent.VisemeCue(id, v, cue, 200, base + 4))
            snapshot(reducer.pose(base + 5), "speech-reachable", level, cue, v)
        }
    println("{\"schema\":\"actual-production-mouth-holdout-export\",\"version\":1," +
        "\"sourceSha\":\"${args[0]}\",\"inputOrigin\":\"SYNTHETIC_CONTRACT_INPUT\"," +
        "\"notDeviceOrAudioEvidence\":true,\"fixedCameraRequired\":true,\"frames\":[${records.joinToString(",")}]}" )
}
