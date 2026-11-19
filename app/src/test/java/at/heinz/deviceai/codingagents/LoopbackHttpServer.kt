package at.heinz.deviceai.codingagents

import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket

/**
 * A minimal HTTP/1.1 server on 127.0.0.1 for the fake endpoints of these tests (the JDK's own test server is not on
 * the Android unit-test classpath). One thread per connection, one request per connection.
 */
internal class LoopbackHttpServer(private val handler: (Exchange) -> Unit) : AutoCloseable {
    class Exchange(val method: String, val target: String, val headers: Map<String, String>, val body: String, private val output: OutputStream) {
        val path: String get() = target.substringBefore('?')

        fun reply(status: Int, body: String, headers: Map<String, String> = emptyMap(), contentType: String = "application/json") {
            val bytes = body.toByteArray(Charsets.UTF_8)
            val extra = headers.entries.joinToString("") { "${it.key}: ${it.value}\r\n" }
            output.write(("HTTP/1.1 $status Scripted\r\nContent-Type: $contentType\r\nrequest-id: req_fake\r\n" + extra +
                "Content-Length: ${bytes.size}\r\nConnection: close\r\n\r\n").toByteArray(Charsets.US_ASCII))
            output.write(bytes)
            output.flush()
        }

        /** Server-sent events with chunked transfer; [write] sends one chunk and fails once the client is gone. */
        fun stream(body: (write: (String) -> Unit) -> Unit) {
            output.write(("HTTP/1.1 200 OK\r\nContent-Type: text/event-stream\r\nCache-Control: no-cache\r\nrequest-id: req_fake\r\n" +
                "Transfer-Encoding: chunked\r\nConnection: close\r\n\r\n").toByteArray(Charsets.US_ASCII))
            output.flush()
            body { text ->
                val bytes = text.toByteArray(Charsets.UTF_8)
                output.write((Integer.toHexString(bytes.size) + "\r\n").toByteArray(Charsets.US_ASCII))
                output.write(bytes)
                output.write("\r\n".toByteArray(Charsets.US_ASCII))
                output.flush()
            }
            output.write("0\r\n\r\n".toByteArray(Charsets.US_ASCII))
            output.flush()
        }
    }

    private val server = ServerSocket(0, 32, InetAddress.getLoopbackAddress())
    @Volatile private var closed = false
    val baseUrl: String = "http://127.0.0.1:${server.localPort}"

    private val acceptor = Thread({
        while (!closed) {
            val socket = runCatching { server.accept() }.getOrNull() ?: break
            Thread({ runCatching { serve(socket) } }, "loopback-http-connection").apply { isDaemon = true }.start()
        }
    }, "loopback-http-acceptor").apply { isDaemon = true; start() }

    override fun close() {
        closed = true
        runCatching { server.close() }
        acceptor.interrupt()
    }

    private fun serve(socket: Socket) { socket.use {
        val input = socket.getInputStream()
        val requestLine = readLine(input) ?: return
        val headers = linkedMapOf<String, String>()
        while (true) {
            val line = readLine(input) ?: return
            if (line.isEmpty()) break
            val colon = line.indexOf(':')
            if (colon > 0) headers[line.substring(0, colon).trim().lowercase()] = line.substring(colon + 1).trim()
        }
        val length = headers["content-length"]?.toIntOrNull() ?: 0
        val bytes = ByteArray(length)
        var read = 0
        while (read < length) {
            val count = input.read(bytes, read, length - read)
            if (count < 0) return
            read += count
        }
        val parts = requestLine.split(' ')
        handler(Exchange(parts[0], parts.getOrElse(1) { "/" }, headers, String(bytes, Charsets.UTF_8), socket.getOutputStream()))
    } }

    private fun readLine(input: InputStream): String? {
        val buffer = ByteArrayOutputStream()
        while (true) {
            val next = input.read()
            if (next < 0) return if (buffer.size() == 0) null else buffer.toString("UTF-8")
            if (next == '\n'.code) return buffer.toString("UTF-8").trimEnd('\r')
            buffer.write(next)
        }
    }
}
