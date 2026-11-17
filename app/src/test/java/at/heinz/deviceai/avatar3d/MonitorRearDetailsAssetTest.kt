package at.heinz.deviceai.avatar3d

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Code56: the shipped rear housing asset is pinned, real geometry, attached behind the monitor panel only. */
class MonitorRearDetailsAssetTest {
    private fun file(path: String): File = File(path).takeIf(File::isFile) ?: File("../$path")
    private val bytes = file("app/src/main/assets/${CockpitSceneAssets.REAR_PATH}").readBytes()
    private val pin = JSONObject(file("app/src/main/assets/${CockpitSceneAssets.REAR_PIN}").readText())
    private val json: JSONObject
    private val bin: ByteBuffer

    init {
        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        require(buffer.getInt(0) == 0x46546c67 && buffer.getInt(4) == 2)
        val jsonLength = buffer.getInt(12)
        json = JSONObject(String(bytes, 20, jsonLength, Charsets.UTF_8))
        val binStart = 20 + jsonLength + 8
        bin = ByteBuffer.wrap(bytes, binStart, bytes.size - binStart).slice().order(ByteOrder.LITTLE_ENDIAN)
    }

    @Test fun shippedAssetMatchesItsPin() {
        val entry = pin.getJSONObject("monitorRear")
        assertEquals(CockpitSceneAssets.REAR_PATH, entry.getString("path"))
        assertEquals(entry.getInt("bytes"), bytes.size)
        assertEquals(entry.getString("sha256"), CockpitSceneAssets.sha256(bytes))
        assertTrue(bytes.size <= CockpitSceneAssets.REAR_MAX_BYTES)
    }

    @Test fun rearHousingHasRealVentsMountPortsAndCables() {
        val names = (0 until json.getJSONArray("nodes").length()).map {
            json.getJSONArray("nodes").getJSONObject(it).optString("name")
        }
        assertEquals("monitor-rear-details", names.last())
        assertTrue(names.count { it.startsWith("rear-fin-") } >= 8)
        assertTrue(names.count { it.startsWith("rear-side-fin-") } >= 10)
        assertEquals(4, names.count { it.startsWith("rear-vesa-screw-") })
        assertTrue(names.containsAll(listOf("rear-housing-main", "rear-vesa-plate", "rear-hinge", "rear-stand-arm",
            "rear-io-panel", "rear-port-hdmi", "rear-port-power", "rear-status-led", "rear-cable-hdmi")))
        assertEquals(3, names.count { it.startsWith("rear-port-usbc-") })
    }

    @Test fun everyVertexStaysBehindThePanelAndInsideTheMonitorFootprint() {
        val nodes = json.getJSONArray("nodes")
        var vertices = 0
        for (i in 0 until nodes.length()) {
            val node = nodes.getJSONObject(i)
            if (!node.has("mesh")) continue
            val t = node.optJSONArray("translation")?.let { DoubleArray(3) { k -> it.getDouble(k) } } ?: DoubleArray(3)
            val s = node.optJSONArray("scale")?.let { DoubleArray(3) { k -> it.getDouble(k) } } ?: doubleArrayOf(1.0, 1.0, 1.0)
            val r = node.optJSONArray("rotation")?.let { DoubleArray(4) { k -> it.getDouble(k) } } ?: doubleArrayOf(0.0, 0.0, 0.0, 1.0)
            val primitive = json.getJSONArray("meshes").getJSONObject(node.getInt("mesh"))
                .getJSONArray("primitives").getJSONObject(0)
            for (p in positions(primitive.getJSONObject("attributes").getInt("POSITION"))) {
                val world = rotate(r, doubleArrayOf(p[0] * s[0], p[1] * s[1], p[2] * s[2])).let {
                    doubleArrayOf(it[0] + t[0], it[1] + t[1], it[2] + t[2])
                }
                vertices++
                assertTrue("${node.getString("name")} covers the front: z=${world[2]}", world[2] < -.031)
                assertTrue("${node.getString("name")} wider than the monitor: x=${world[0]}", kotlin.math.abs(world[0]) <= .51)
                assertTrue("${node.getString("name")} outside the monitor height: y=${world[1]}", world[1] in -.53..0.51)
            }
        }
        assertTrue(vertices > 1_000)
    }

    private fun positions(index: Int): List<DoubleArray> {
        val accessor = json.getJSONArray("accessors").getJSONObject(index)
        val view = json.getJSONArray("bufferViews").getJSONObject(accessor.getInt("bufferView"))
        val offset = view.optInt("byteOffset") + accessor.optInt("byteOffset")
        return (0 until accessor.getInt("count")).map { v ->
            DoubleArray(3) { c -> bin.getFloat(offset + (v * 3 + c) * 4).toDouble() }
        }
    }

    private fun rotate(q: DoubleArray, v: DoubleArray): DoubleArray {
        val (x, y, z, w) = listOf(q[0], q[1], q[2], q[3])
        val cx = y * v[2] - z * v[1]; val cy = z * v[0] - x * v[2]; val cz = x * v[1] - y * v[0]
        val dx = y * cz - z * cy; val dy = z * cx - x * cz; val dz = x * cy - y * cx
        return doubleArrayOf(v[0] + 2 * (w * cx + dx), v[1] + 2 * (w * cy + dy), v[2] + 2 * (w * cz + dz))
    }
}
