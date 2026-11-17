package at.heinz.deviceai.learning

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.security.MessageDigest

/** Bounded checksummed snapshots; checksum detects damage, not a hostile writer. */
internal object LearningSnapshot {
    fun encode(write: (DataOutputStream) -> Unit): ByteArray {
        val buffer = ByteArrayOutputStream()
        DataOutputStream(buffer).use(write)
        val body = buffer.toByteArray()
        require(body.size <= 65504)
        return body + MessageDigest.getInstance("SHA-256").digest(body)
    }
    fun <T> decode(bytes: ByteArray, read: (DataInputStream) -> T): T {
        require(bytes.size in 36..65536)
        val copy = bytes.copyOf()
        val body = copy.copyOfRange(0, copy.size - 32)
        require(MessageDigest.isEqual(MessageDigest.getInstance("SHA-256").digest(body),
            copy.copyOfRange(body.size, copy.size))) { "Learning snapshot checksum mismatch" }
        try {
            return DataInputStream(ByteArrayInputStream(body)).use { input ->
                read(input).also { require(input.available() == 0) }
            }
        } catch (e: java.io.IOException) {
            throw IllegalArgumentException("Invalid learning snapshot", e)
        }
    }
}
