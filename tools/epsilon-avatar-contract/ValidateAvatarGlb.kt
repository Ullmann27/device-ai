import at.heinz.deviceai.avatar3d.AvatarGlbContract
import org.json.JSONArray
import org.json.JSONObject

/** Runs the exact Android preflight against real candidate bytes, without Android/JNI. */
fun main(args: Array<String>) {
    val result = JSONObject()
    try {
        require(args.size == 1) { "Expected one GLB path" }
        val file = java.io.File(args.single())
        require(file.length() in 28..AvatarGlbContract.MAX_BYTES.toLong()) { "GLB size" }
        val bytes = file.readBytes()
        val metadata = AvatarGlbContract.validate(bytes)
        result.put("status", "PASS").put("bytes", bytes.size)
            .put("sha256", java.security.MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) })
            .put("targets", JSONArray(metadata.targets)).put("morphNodes", JSONArray(metadata.morphNodeNames))
            .put("focus", JSONObject().put("centerX",metadata.focus.centerX).put("centerY",metadata.focus.centerY)
                .put("centerZ",metadata.focus.centerZ).put("halfWidth",metadata.focus.halfWidth).put("halfHeight",metadata.focus.halfHeight))
    } catch (failure: Exception) {
        result.put("status", "FAIL").put("reason", failure.message ?: failure.javaClass.simpleName)
    }
    println(result.toString(2))
    if (result.getString("status") != "PASS") kotlin.system.exitProcess(1)
}
