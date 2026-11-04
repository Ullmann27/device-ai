import at.heinz.deviceai.TtsHologramSignal
import at.heinz.deviceai.avatar.*
import at.heinz.deviceai.avatar3d.AvatarGlbContract
import at.heinz.deviceai.avatar3d.AvatarMorphMapper
import java.io.File
import java.security.MessageDigest
import org.json.JSONArray
import org.json.JSONObject

/** Replay real logic, synthetic inputs. */
object ExportAvatarAlive {
    @JvmStatic fun main(args: Array<String>) {
        require(args.size == 3)
        val root = File(args[0]).canonicalFile
        require(args[1].matches(Regex("[0-9a-f]{40}")))
        val output = File(args[2]).canonicalFile
        fun sha(file: File) = MessageDigest.getInstance("SHA-256").digest(file.readBytes())
            .joinToString("") { "%02x".format(it) }
        val paths = listOf("avatar/AvatarContract.kt", "avatar/AvatarDirector.kt",
            "avatar3d/AvatarIdleMotion.kt", "avatar3d/AvatarMorphMapper.kt",
            "avatar3d/AvatarGlbContract.kt", "avatar3d/FilamentAvatarRenderer.kt",
            "TtsHologramSignal.kt", "HologramAudio.kt")
        val hashes = JSONObject()
        for (path in paths) {
            val relative = "app/src/main/java/at/heinz/deviceai/$path"
            hashes.put(relative, sha(File(root, relative)))
        }
        val asset = File(root, "app/src/main/assets/avatar3d/continuum-avatar.glb")
        val metadata = AvatarGlbContract.validate(asset.readBytes())
        require(sha(asset) == AvatarGlbContract.ASSET_SHA256)
        val reducer = AvatarPoseReducer()
        var now = 0L
        val director = AvatarDirector(clock = { now })
        director.attach(AvatarSink(reducer::apply))
        val signal = TtsHologramSignal()
        val mapper = AvatarMorphMapper(metadata.targets, metadata.focus.azimuthDegrees)
        val frames = JSONArray()
        val fps = 16
        val duration = 12
        val speech = "Hallo Heinz. Ich bin da."
        val words = Regex("\\S+").findAll(speech).toList()
        var wordIndex = 0
        for (index in 0 until fps * duration) {
            now = index * 1_000L / fps
            when (index) {
                2 * fps -> director.onRuntime(AvatarRuntimeSignals(listening = true))
                4 * fps -> director.onRuntime(AvatarRuntimeSignals(busy = true))
                6 * fps -> {
                    signal.activate("review")
                    signal.begin("review", 8_000, 2, 1)
                    repeat(40) { chunk ->
                        val level = if (chunk % 4 == 3) 4 else 34 + chunk % 4 * 8
                        signal.append("review", ByteArray(1_600) { if (it % 2 == 0) 0 else level.toByte() })
                    }
                    require(director.speechStarted("review", speech))
                    signal.anchor("review", 0, now)
                    director.onRuntime(AvatarRuntimeSignals(speaking = true))
                }
                10 * fps -> {
                    director.speechEnded("review", true)
                    signal.stop()
                    director.onRuntime(AvatarRuntimeSignals())
                    director.audioLevel("review", 1f)
                    require(!director.wordRange("review", 0, 5, 0))
                }
            }
            if (now in 6_000 until 10_000) {
                if (wordIndex < words.size && now >= 6_000 + wordIndex * 650) {
                    val word = words[wordIndex++]
                    require(director.wordRange("review", word.range.first, word.range.last + 1,
                        ((now - 6_000) * 8).toInt()))
                }
                val measured = signal.measuredLevel("review", now)
                director.audioLevel("review", measured ?: 0f, measuredPcmAvailable = measured != null)
            }
            val pose = reducer.pose(now)
            val seconds = index.toFloat() / fps
            mapper.update(pose, seconds, true)
            if (now >= 10_000) {
                require(mapper.jawPitch == 0f)
                for (v in Viseme.entries) require(mapper.weights[metadata.targets.indexOf(v.name)] == 0f)
            }
            val weights = JSONObject()
            for ((i, name) in metadata.targets.withIndex()) weights.put(name, mapper.weights[i].toDouble())
            frames.put(JSONObject().put("name", "alive-f%03d".format(index)).put("seconds", seconds.toDouble())
                .put("motion", true).put("input", JSONObject().put("state", pose.state.name)
                    .put("expression", pose.expression.name).put("gazeX", pose.gazeX.toDouble())
                    .put("gazeY", pose.gazeY.toDouble()).put("gazeObserved", pose.gazeObserved)
                    .put("jawOpen", pose.jawOpen.toDouble()).put("viseme", pose.viseme.name)
                    .put("visemeWeight", pose.visemeWeight.toDouble()).put("speakEnergy", pose.speakEnergy.toDouble()))
                .put("weights", weights).put("headYaw", mapper.headYaw.toDouble())
                .put("headPitch", mapper.headPitch.toDouble()).put("eyeYaw", mapper.eyeYaw.toDouble())
                .put("eyePitch", mapper.eyePitch.toDouble()).put("jawPitch", mapper.jawPitch.toDouble()))
        }
        val focus = metadata.focus
        val result = JSONObject().put("schema", "continuum-production-mapper-review-poses-v1")
            .put("evidenceClass", "ACTUAL_KOTLIN_PIPELINE_SYNTHETIC_INPUT_NOT_DEVICE")
            .put("sourceCommit", args[1]).put("sourceHashes", hashes).put("sourceAssetSha256", sha(asset))
            .put("focus", JSONObject().put("centerX", focus.centerX).put("centerY", focus.centerY)
                .put("centerZ", focus.centerZ).put("halfWidth", focus.halfWidth)
                .put("halfHeight", focus.halfHeight).put("azimuthDegrees", focus.azimuthDegrees))
            .put("frames", frames).put("animation", JSONObject().put("framesPerSecond", fps)
                .put("frameCount", frames.length()).put("durationSeconds", duration))
            .put("limitations", JSONArray(listOf("Synthetic runtime events and generated PCM; no actual TTS or camera",
                "Actual Director, audio envelope, reducer and mapper; separate Blender renderer",
                "Silent motion review; no audible synchronization or native device claim")))
        output.parentFile.mkdirs()
        output.writeText(result.toString(2) + "\n")
        println(JSONObject().put("frames", frames.length()).put("poseSha256", sha(output)).toString())
    }
}
