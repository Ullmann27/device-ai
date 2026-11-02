package at.heinz.deviceai

import java.io.ByteArrayOutputStream
import java.io.InputStream

/**
 * Reads at most [limit] + 1 bytes, so a caller can reject oversized input
 * without reading it whole. InputStream.readNBytes(int) does the same but only
 * exists from API 33; this app supports API 26 without core library
 * desugaring, where readNBytes throws NoSuchMethodError.
 */
fun InputStream.readAtMost(limit: Int): ByteArray {
    require(limit in 0 until Int.MAX_VALUE) { "limit out of range" }
    val out = ByteArrayOutputStream(minOf(limit + 1, 64 * 1024))
    val buffer = ByteArray(8 * 1024)
    var remaining = limit + 1
    while (remaining > 0) {
        val n = read(buffer, 0, minOf(buffer.size, remaining))
        if (n < 0) break
        out.write(buffer, 0, n)
        remaining -= n
    }
    return out.toByteArray()
}
