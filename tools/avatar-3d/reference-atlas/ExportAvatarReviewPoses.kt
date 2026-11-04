import at.heinz.deviceai.avatar.*
import at.heinz.deviceai.avatar3d.AvatarGlbContract
import at.heinz.deviceai.avatar3d.AvatarMorphMapper
import java.io.File
import java.security.MessageDigest
import org.json.JSONArray
import org.json.JSONObject

/** Export synthetic review inputs through the real production mapper/reducer. */
fun main(args: Array<String>) {
    require(args.size in 3..4) { "Usage: source-root source-commit output-json [idle-frame-count-at-12fps]" }
    val root = File(args[0]).canonicalFile
    require(args[1].matches(Regex("[0-9a-f]{40}")))
    val output = File(args[2]).canonicalFile
    fun sha(file: File): String = MessageDigest.getInstance("SHA-256").digest(file.readBytes()).joinToString("") { "%02x".format(it) }
    val paths = listOf(
        "app/src/main/java/at/heinz/deviceai/avatar/AvatarContract.kt",
        "app/src/main/java/at/heinz/deviceai/avatar3d/AvatarIdleMotion.kt",
        "app/src/main/java/at/heinz/deviceai/avatar3d/AvatarMorphMapper.kt",
        "app/src/main/java/at/heinz/deviceai/avatar3d/AvatarGlbContract.kt",
        "app/src/main/java/at/heinz/deviceai/avatar3d/FilamentAvatarRenderer.kt"
    )
    val sourceHashes = JSONObject()
    for (path in paths) sourceHashes.put(path, sha(File(root, path)))
    val asset = File(root, "app/src/main/assets/avatar3d/continuum-avatar.glb")
    val metadata = AvatarGlbContract.validate(asset.readBytes())
    val frames = JSONArray()
    fun pose(state: AvatarState = AvatarState.LISTENING, gazeX: Float = 0f, gazeY: Float = 0f,
             jaw: Float = 0f, viseme: Viseme = Viseme.SIL, weight: Float = 0f) =
        AvatarPose(state, AvatarExpression.NEUTRAL, gazeX, gazeY, jaw, viseme, weight, jaw)
    fun capture(name: String, value: AvatarPose, seconds: Float, map: AvatarMorphMapper = AvatarMorphMapper(metadata.targets, metadata.focus.azimuthDegrees), motion: Boolean = true) {
        map.update(value, seconds, motion)
        val weights = JSONObject()
        for ((index, target) in metadata.targets.withIndex()) weights.put(target, map.weights[index].toDouble())
        val frame = JSONObject().put("name", name).put("seconds", seconds.toDouble()).put("motion", motion)
            .put("input", JSONObject().put("state", value.state.name).put("expression", value.expression.name)
                .put("gazeX", value.gazeX.toDouble()).put("gazeY", value.gazeY.toDouble())
                .put("jawOpen", value.jawOpen.toDouble()).put("viseme", value.viseme.name)
                .put("visemeWeight", value.visemeWeight.toDouble()).put("speakEnergy", value.speakEnergy.toDouble())
                .put("gazeObserved", value.gazeObserved))
            .put("weights", weights).put("headYaw", map.headYaw.toDouble()).put("headPitch", map.headPitch.toDouble())
            .put("eyeYaw", map.eyeYaw.toDouble()).put("eyePitch", map.eyePitch.toDouble()).put("jawPitch", map.jawPitch.toDouble())
        frames.put(frame)
    }
    capture("neutral-listening", pose(), 0f)
    capture("blink", pose(), 4.525f)
    capture("speaking-aa", pose(AvatarState.SPEAKING, jaw = .6f, viseme = Viseme.AA, weight = .6f), 1f)
    capture("speaking-o", pose(AvatarState.SPEAKING, jaw = .6f, viseme = Viseme.O, weight = .7f), 1f)
    capture("gaze-left", pose(gazeX = -1f), 1f)
    capture("gaze-right-up", pose(gazeX = 1f, gazeY = .6f), 1f)
    capture("idle-2s", pose(AvatarState.IDLE), 2f)
    val transition = AvatarMorphMapper(metadata.targets, metadata.focus.azimuthDegrees)
    capture("aa-before-transition", pose(AvatarState.SPEAKING, jaw = .6f, viseme = Viseme.AA, weight = 1f), 1f, transition)
    capture("aa-to-o-16ms", pose(AvatarState.SPEAKING, jaw = .6f, viseme = Viseme.O, weight = 1f), 1.016f, transition)
    capture("aa-to-o-48ms", pose(AvatarState.SPEAKING, jaw = .6f, viseme = Viseme.O, weight = 1f), 1.048f, transition)
    val reducer = AvatarPoseReducer()
    reducer.apply(AvatarEvent.SpeechStarted("review-synthetic", "A", 100))
    reducer.apply(AvatarEvent.AudioLevel("review-synthetic", 1f, 101))
    reducer.apply(AvatarEvent.VisemeCue("review-synthetic", Viseme.AA, 1f, 500, 101))
    val stopping = AvatarMorphMapper(metadata.targets, metadata.focus.azimuthDegrees)
    capture("before-interruption", reducer.pose(102), 1f, stopping)
    reducer.apply(AvatarEvent.SpeechEnded("review-synthetic", true, 103))
    reducer.apply(AvatarEvent.AudioLevel("review-synthetic", 1f, 104))
    reducer.apply(AvatarEvent.VisemeCue("review-synthetic", Viseme.O, 1f, 500, 104))
    capture("after-interruption", reducer.pose(105), 1.016f, stopping)
    require(stopping.jawPitch == 0f)
    for (viseme in Viseme.entries) require(stopping.weights[metadata.targets.indexOf(viseme.name)] == 0f)
    val idleCount = if (args.size == 4) args[3].toInt() else 0
    require(idleCount in 0..240)
    val animatedIdle = AvatarMorphMapper(metadata.targets, metadata.focus.azimuthDegrees)
    for (index in 0 until idleCount) {
        capture("idle-f%03d".format(index), pose(AvatarState.IDLE), index / 12f, animatedIdle)
    }
    val focus = metadata.focus
    val result = JSONObject().put("schema", "continuum-production-mapper-review-poses-v1")
        .put("evidenceClass", "ACTUAL_KOTLIN_MAPPER_SYNTHETIC_INPUT_NOT_DEVICE")
        .put("sourceCommit", args[1]).put("sourceHashes", sourceHashes).put("sourceAssetSha256", sha(asset))
        .put("focus", JSONObject().put("centerX", focus.centerX).put("centerY", focus.centerY).put("centerZ", focus.centerZ)
            .put("halfWidth", focus.halfWidth).put("halfHeight", focus.halfHeight).put("azimuthDegrees", focus.azimuthDegrees))
        .put("frames", frames).put("limitations", JSONArray(listOf(
            "Synthetic inputs through actual compiled source; no real TTS, microphone or camera",
            "Weights and joint angles are exact mapper outputs; render backend remains separate",
            "Legacy diagnostic inputs do not exercise the idle smile; use ExportAvatarAlive",
            "Gaze uses the same portrait-camera azimuth as the native renderer"
        )))
    if (idleCount > 0) result.put("idleAnimation", JSONObject().put("framesPerSecond", 12).put("frameCount", idleCount)
        .put("durationSeconds", idleCount / 12.0).put("inputOrigin", "SYNTHETIC_IDLE_THROUGH_ACTUAL_PRODUCTION_MAPPER"))
    output.parentFile.mkdirs()
    output.writeText(result.toString(2) + "\n")
    println(JSONObject().put("status", "EXPORTED").put("frames", frames.length()).put("output", output.path)
        .put("poseSha256", sha(output)).toString())
}
