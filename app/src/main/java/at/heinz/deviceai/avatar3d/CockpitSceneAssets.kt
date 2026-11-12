package at.heinz.deviceai.avatar3d

import android.content.res.AssetManager
import org.json.JSONObject
import java.security.MessageDigest
import java.io.InputStream
import java.io.ByteArrayOutputStream

/** APK-only, bounded, integrity-checked assets. */
internal data class CockpitSceneAssets(val workstation: ByteArray, val orb: ByteArray, val monitorMaterial: ByteArray) {
    companion object {
        private fun boundedRead(input: InputStream, limit: Int): ByteArray {
            val output = ByteArrayOutputStream()
            val chunk = ByteArray(8192)
            while (true) {
                val count = input.read(chunk)
                if (count < 0) break
                require(output.size() + count <= limit)
                output.write(chunk, 0, count)
            }
            return output.toByteArray()
        }

        fun load(assets: AssetManager): CockpitSceneAssets {
            val manifest = JSONObject(String(assets.open("cinema3d/cockpit-world.json").use {
                boundedRead(it, 16_384)
            }, Charsets.UTF_8))
            require(manifest.getInt("version") == 1)
            fun read(key: String, expectedPath: String, limit: Int): ByteArray {
                val entry = manifest.getJSONObject(key)
                require(entry.getString("path") == expectedPath)
                val expectedSize = entry.getInt("bytes")
                require(expectedSize in 1..limit)
                val expectedHash = entry.getString("sha256")
                require(expectedHash.matches(Regex("[a-f0-9]{64}")))
                val bytes = assets.open(expectedPath).use { boundedRead(it, limit) }
                require(bytes.size == expectedSize)
                val hash = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
                require(hash == expectedHash)
                return bytes
            }
            return CockpitSceneAssets(
                read("workstation", "cinema3d/cockpit-workstation.glb", 2_000_000),
                read("orb", "cinema3d/continuum-orb.glb", 2_000_000),
                read("monitorMaterial", "cinema3d/live-monitor.filamat", 1_000_000))
        }
    }
}
