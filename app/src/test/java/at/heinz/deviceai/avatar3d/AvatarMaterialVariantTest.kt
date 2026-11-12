package at.heinz.deviceai.avatar3d

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest

class AvatarMaterialVariantTest {
    private fun source() = sequenceOf(File("src/main/assets/avatar3d/continuum-avatar.glb"),
        File("app/src/main/assets/avatar3d/continuum-avatar.glb")).first(File::isFile).readBytes()
    private fun jsonLength(bytes: ByteArray) = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).getInt(12)
    private fun json(bytes: ByteArray) = JSONObject(String(bytes, 20, jsonLength(bytes), Charsets.UTF_8))

    @Test fun runtimeTransparencyKeepsOriginalRigGeometryAndTexturesIdentical() {
        val original = source()
        val pinned = MessageDigest.getInstance("SHA-256").digest(original).joinToString("") { "%02x".format(it) }
        assertEquals(AvatarGlbContract.ASSET_SHA256, pinned)
        val snapshot = original.copyOf()
        val variant = AvatarMaterialVariant.prepare(original)
        assertArrayEquals(snapshot, original)
        assertArrayEquals(original.copyOfRange(20 + jsonLength(original), original.size),
            variant.bytes.copyOfRange(20 + jsonLength(variant.bytes), variant.bytes.size))
        val before = json(original)
        val after = json(variant.bytes)
        for (key in before.keys()) if (key != "materials") {
            assertEquals("Original $key must remain", before.get(key).toString(), after.get(key).toString())
        }
        val priorMaterials = before.getJSONArray("materials")
        val resultMaterials = after.getJSONArray("materials")
        assertEquals(priorMaterials.length(), resultMaterials.length())
        for (i in 0 until priorMaterials.length()) {
            val prior = priorMaterials.getJSONObject(i)
            val result = resultMaterials.getJSONObject(i)
            if (prior.optString("alphaMode", "OPAQUE") == "MASK") assertEquals(prior.toString(), result.toString())
            else {
                assertEquals("BLEND", result.getString("alphaMode"))
                prior.remove("alphaMode"); result.remove("alphaMode")
                assertEquals(prior.toString(), result.toString())
            }
        }
    }

    @Test fun truncatedAndNonGlbDataAreRejectedBeforeGpuLoad() {
        for (bad in listOf(ByteArray(32), source().copyOf(25))) {
            try { AvatarMaterialVariant.prepare(bad); fail("Invalid GLB accepted") }
            catch (_: IllegalArgumentException) { }
        }
    }
}
