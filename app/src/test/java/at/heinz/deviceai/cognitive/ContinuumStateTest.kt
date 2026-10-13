package at.heinz.deviceai.cognitive

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files

class ContinuumStateTest {
    @Test
    fun immutableObjectRoundTripsAndDeduplicates() {
        val dir = Files.createTempDirectory("continuum-object-test").toFile()
        try {
            val store = FileContinuumObjectStore(dir)
            val payload = "persistent-state".toByteArray()
            val first = store.put(ContinuumObjectType.MEMORY_BLOCK, payload, 100)
            val second = store.put(ContinuumObjectType.MEMORY_BLOCK, payload, 999)

            assertEquals(first.id, second.id)
            assertEquals(1, dir.listFiles { file -> file.name.endsWith(".obj") }.orEmpty().size)
            assertArrayEquals(payload, store.get(first.id)!!.payload)
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun corruptionIsRejected() {
        val dir = Files.createTempDirectory("continuum-corrupt-test").toFile()
        try {
            val store = FileContinuumObjectStore(dir)
            val metadata = store.put(ContinuumObjectType.CRITICAL_BLOCK_FOR_TEST(), "abc".toByteArray(), 1)
            val file = dir.listFiles { item -> item.name.endsWith(".obj") }!!.single()
            val bytes = file.readBytes()
            bytes[bytes.lastIndex] = (bytes.last().toInt() xor 0x01).toByte()
            file.writeBytes(bytes)

            assertThrows(IllegalArgumentException::class.java) { store.get(metadata.id) }
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun boundedStoreRejectsCapacityOverflow() {
        val dir = Files.createTempDirectory("continuum-capacity-test").toFile()
        try {
            val store = FileContinuumObjectStore(dir, maxObjectBytes = 128, maxStoreBytes = 256)
            store.put(ContinuumObjectType.CACHE, ByteArray(120) { 1 }, 1)
            assertThrows(IllegalArgumentException::class.java) {
                store.put(ContinuumObjectType.CACHE, ByteArray(120) { 2 }, 2)
            }
            assertTrue(store.totalStoredBytes() <= 256)
        } finally {
            dir.deleteRecursively()
        }
    }

    private fun ContinuumObjectType.CompanionStub(): ContinuumObjectType = ContinuumObjectType.MEMORY_BLOCK
    private fun ContinuumObjectType.CRITICAL_BLOCK_FOR_TEST(): ContinuumObjectType = ContinuumObjectType.IDENTITY_SNAPSHOT
}
