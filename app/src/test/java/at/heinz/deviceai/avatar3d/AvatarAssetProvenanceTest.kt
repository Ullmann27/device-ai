package at.heinz.deviceai.avatar3d

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File
import java.security.MessageDigest

/** Checks the packaged bytes, runtime pin and accompanying provenance as one revision. */
class AvatarAssetProvenanceTest {
    @Test fun packagedAssetHashAndLengthMatchRuntimeAndNotice() {
        val directory = sequenceOf(
            File("src/main/assets/avatar3d"),
            File("app/src/main/assets/avatar3d"),
        ).first { File(it, "continuum-avatar.glb").isFile }
        val bytes = File(directory, "continuum-avatar.glb").readBytes()
        val notice = JSONObject(File(directory, "NOTICE.json").readText())
        val actualSha256 = MessageDigest.getInstance("SHA-256").digest(bytes)
            .joinToString("") { "%02x".format(it) }

        assertEquals("The NOTICE must identify this packaged asset", "continuum-avatar.glb", notice.getString("asset"))
        assertEquals("The runtime pin must match the actual GLB", actualSha256, AvatarGlbContract.ASSET_SHA256)
        assertEquals("The NOTICE hash must match the actual GLB", actualSha256, notice.getString("sha256"))
        assertEquals("The NOTICE byte length must match the actual GLB", bytes.size.toLong(), notice.getLong("bytes"))
    }
}
