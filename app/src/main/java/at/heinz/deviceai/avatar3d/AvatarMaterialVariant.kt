package at.heinz.deviceai.avatar3d

import org.json.JSONObject
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Runtime material variant; source remains untouched. */
internal object AvatarMaterialVariant {
    data class Factor(val name: String, val red: Float, val green: Float, val blue: Float, val alpha: Float)
    data class Prepared(val bytes: ByteArray, val factors: List<Factor>)

    fun prepare(original: ByteArray, opacity: Float = 1f): Prepared {
        require(opacity.isFinite() && opacity in 0f..1f)
        val input = ByteBuffer.wrap(original).order(ByteOrder.LITTLE_ENDIAN)
        require(input.int == 0x46546c67 && input.int == 2 && input.int == original.size)
        val jsonLength = input.int
        require(input.int == 0x4e4f534a && jsonLength in 2..(original.size - 20))
        val json = JSONObject(String(original, 20, jsonLength, Charsets.UTF_8))
        val materials = requireNotNull(json.optJSONArray("materials"))
        val factors = mutableListOf<Factor>()
        for (i in 0 until materials.length()) {
            val material = materials.getJSONObject(i)
            if (material.optString("alphaMode", "OPAQUE") == "MASK") continue
            val pbr = material.optJSONObject("pbrMetallicRoughness")
            val factor = pbr?.optJSONArray("baseColorFactor")
            fun component(index: Int) = factor?.optDouble(index, 1.0)?.toFloat() ?: 1f
            factors += Factor(material.getString("name"), component(0), component(1), component(2), component(3))
            if (opacity < 1f) material.put("alphaMode", "BLEND")
        }
        if (opacity == 1f) return Prepared(original, factors)
        val encoded = json.toString().toByteArray(Charsets.UTF_8)
        val padded = (encoded.size + 3) and -4
        val tailOffset = 20 + jsonLength
        val output = ByteBuffer.allocate(20 + padded + original.size - tailOffset).order(ByteOrder.LITTLE_ENDIAN)
        output.putInt(0x46546c67).putInt(2).putInt(output.capacity()).putInt(padded).putInt(0x4e4f534a)
        output.put(encoded)
        repeat(padded - encoded.size) { output.put(0x20.toByte()) }
        output.put(original, tailOffset, original.size - tailOffset)
        return Prepared(output.array(), factors)
    }
}
