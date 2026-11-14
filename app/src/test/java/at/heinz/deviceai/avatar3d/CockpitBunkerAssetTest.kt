package at.heinz.deviceai.avatar3d

import java.io.File
import java.io.FileNotFoundException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class CockpitBunkerAssetTest {
    private fun file(path: String) = File("app/src/main/assets/$path").takeIf(File::isFile)
        ?: File("../app/src/main/assets/$path")
    private val bytes = file(CockpitSceneAssets.BUNKER_PATH).readBytes()
    private val pin = file(CockpitSceneAssets.BUNKER_PIN).readBytes()

    @Test fun validPinnedAssetLoads() {
        val actual = CockpitSceneAssets.loadBunker { path -> file(path).inputStream() }
        assertArrayEquals(bytes, actual)
        assertTrue(bytes.size <= CockpitSceneAssets.BUNKER_MAX_BYTES)
    }

    @Test fun absentOrCorruptAssetKeepsFallbackAvailable() {
        assertNull(CockpitSceneAssets.loadBunker { throw FileNotFoundException() })
        val corrupt = bytes.copyOf().apply { this[lastIndex] = (this[lastIndex].toInt() xor 1).toByte() }
        assertNull(CockpitSceneAssets.loadBunker {
            (if (it == CockpitSceneAssets.BUNKER_PIN) pin else corrupt).inputStream()
        })
        assertNull(CockpitSceneAssets.loadBunker {
            (if (it == CockpitSceneAssets.BUNKER_PIN) pin else bytes.copyOf(bytes.size - 8)).inputStream()
        })
    }

    @Test fun wrongPathIsRejectedBeforeAssetRead() {
        val modified = JSONObject(String(pin)).apply {
            getJSONObject("bunker").put("path", "../avatar3d/continuum-avatar.glb")
        }.toString().toByteArray()
        val paths = mutableListOf<String>()
        assertNull(CockpitSceneAssets.loadBunker { paths += it; modified.inputStream() })
        assertEquals(listOf(CockpitSceneAssets.BUNKER_PIN), paths)
    }

    @Test fun independentRootsContainOnlyEnvironmentGeometry() {
        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        assertEquals(0x46546c67, buffer.getInt(0))
        assertEquals(2, buffer.getInt(4))
        assertEquals(bytes.size, buffer.getInt(8))
        val json = JSONObject(String(bytes, 20, buffer.getInt(12), Charsets.UTF_8))
        val nodes = json.getJSONArray("nodes")
        val rootIndices = json.getJSONArray("scenes").getJSONObject(0).getJSONArray("nodes")
        val roots = (0 until rootIndices.length()).map { nodes.getJSONObject(rootIndices.getInt(it)).getString("name") }
        assertEquals(setOf("continuum-bunker", "continuum-mission-desk", "continuum-monitor-engineering"), roots.toSet())
        assertEquals(3, roots.size)
        assertTrue(json.optJSONArray("skins") == null)
        assertTrue(json.optJSONArray("images") == null)
        assertTrue(json.optJSONArray("animations") == null)
        assertTrue(json.getJSONArray("meshes").length() in 20..80)
        assertTrue(json.getJSONArray("materials").length() <= 32)
        assertFalse(json.getJSONArray("buffers").getJSONObject(0).has("uri"))
    }
}
