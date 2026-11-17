package at.heinz.deviceai.avatar3d

import org.junit.Assert.*
import org.junit.Test

class CockpitTransformUpdatesTest {
    private fun identity() = FloatArray(16).apply { this[0] = 1f; this[5] = 1f; this[10] = 1f; this[15] = 1f }

    @Test fun mutableScratchBuffersStaySeparatePerInstance() {
        val uploads = mutableListOf<Pair<Int, FloatArray>>()
        val updates = CockpitTransformUpdates({ id, matrix -> uploads += id to matrix.copyOf() })
        val scratch = identity()
        updates.setTransform(1, scratch)
        scratch[0] = 0f
        updates.setTransform(2, scratch)
        scratch[0] = 1f
        updates.setTransform(1, scratch)
        scratch[0] = 0f
        updates.setTransform(2, scratch)
        assertEquals(listOf(1, 2), uploads.map { it.first })
        assertEquals(1f, uploads[0].second[0], 0f)
        assertEquals(0f, uploads[1].second[0], 0f)
    }

    @Test fun everyComponentChangeIncludingOneUlpIsUploaded() {
        var count = 0
        val updates = CockpitTransformUpdates({ _, _ -> count++ })
        val matrix = identity()
        updates.setTransform(1, matrix)
        for (component in 0 until 16) {
            val before = matrix[component]
            matrix[component] = Math.nextUp(before)
            updates.setTransform(1, matrix)
            matrix[component] = before
            updates.setTransform(1, matrix)
        }
        assertEquals(33, count)
        matrix[1] = -0.0f
        updates.setTransform(1, matrix)
        assertEquals(34, count)
    }

    @Test fun failedUploadInvalidatesPreviousSuccessfulValue() {
        val native = mutableMapOf<Int, FloatArray>()
        var attempts = 0
        var fail = false
        val updates = CockpitTransformUpdates({ id, matrix ->
            attempts++
            native[id] = matrix.copyOf()
            if (fail) error("Synthetic partial upload")
        })
        val before = identity()
        val after = before.copyOf().apply { this[12] = .1f }
        updates.setTransform(1, before)
        fail = true
        assertThrows(IllegalStateException::class.java) { updates.setTransform(1, after) }
        fail = false
        updates.setTransform(1, before)
        assertEquals(3, attempts)
        assertArrayEquals(before, native.getValue(1), 0f)
        updates.setTransform(1, after)
        assertEquals(4, attempts)
    }

    @Test fun failedFirstUploadIsRetried() {
        var attempts = 0
        val updates = CockpitTransformUpdates({ _, _ -> if (++attempts == 1) error("Synthetic failure") })
        val matrix = identity()
        assertThrows(IllegalStateException::class.java) { updates.setTransform(4, matrix) }
        updates.setTransform(4, matrix)
        updates.setTransform(4, matrix)
        assertEquals(2, attempts)
    }

    @Test fun disableAndEnableResetCachedValues() {
        var attempts = 0
        val updates = CockpitTransformUpdates({ _, _ -> attempts++ })
        val matrix = identity()
        updates.setTransform(1, matrix)
        updates.enabled = false
        repeat(3) { updates.setTransform(1, matrix) }
        updates.enabled = true
        repeat(3) { updates.setTransform(1, matrix) }
        assertEquals(5, attempts)
    }

    @Test fun clearAndNewLifetimeUploadEveryInitialValue() {
        var attempts = 0
        val sink: (Int, FloatArray) -> Unit = { _, _ -> attempts++ }
        val matrix = identity()
        val updates = CockpitTransformUpdates(sink)
        updates.setTransform(7, matrix)
        updates.clear()
        updates.setTransform(7, matrix)
        CockpitTransformUpdates(sink).setTransform(7, matrix)
        assertEquals(3, attempts)
    }

    @Test fun nonFiniteMatricesNeverBecomeCachedState() {
        var attempts = 0
        val updates = CockpitTransformUpdates({ _, _ -> attempts++ })
        val matrix = identity()
        for (invalid in listOf(Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY)) {
            matrix[12] = invalid
            repeat(2) { updates.setTransform(1, matrix) }
        }
        assertEquals(6, attempts)
        assertThrows(IllegalArgumentException::class.java) { updates.setTransform(1, FloatArray(15)) }
        assertEquals(6, attempts)
    }
}
