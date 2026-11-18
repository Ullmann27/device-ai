package at.heinz.deviceai.localmodel

import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * A local stand-in for the Messages API, on 127.0.0.1 only. It replays scripted responses in the wire shape of the
 * real endpoint (server-sent events for a stream, a JSON error body for a refusal of the request) and records what
 * the SDK sent. No test ever talks to the real API and no real key exists here.
 */
class FakeAnthropicEndpoint : AutoCloseable {
    class Request(val line: String, val headers: Map<String, String>, val body: JSONObject)

    sealed interface Reply {
        /** [holdAfter]: wait before sending event number [holdAfter] until [release] is called. */
        class Stream(val events: List<Pair<String, JSONObject>>, val holdAfter: Int = -1,
                     /** Close the socket after this many events without ending the HTTP body. */
                     val dropAfter: Int = -1) : Reply
        class Error(val status: Int, val body: JSONObject, val headers: Map<String, String> = emptyMap()) : Reply
        /** Accept the request and never answer until [release]. */
        object Silent : Reply
    }

    private val server = ServerSocket(0, 16, InetAddress.getLoopbackAddress())
    private val replies = ConcurrentLinkedQueue<Reply>()
    private val gate = CountDownLatch(1)
    private val holding = CountDownLatch(1)
    private val aborted = CountDownLatch(1)
    @Volatile private var closed = false
    val requests = CopyOnWriteArrayList<Request>()
    val baseUrl: String = "http://127.0.0.1:${server.localPort}"

    private val acceptor = Thread({
        while (!closed) {
            val socket = runCatching { server.accept() }.getOrNull() ?: break
            Thread({ runCatching { serve(socket) } }, "fake-anthropic-connection").apply { isDaemon = true }.start()
        }
    }, "fake-anthropic-acceptor").apply { isDaemon = true; start() }

    fun enqueue(vararg reply: Reply) { replies += reply }

    /** Lets a held stream or a silent request continue. */
    fun release() = gate.countDown()

    /** True once a held stream reached its hold point (the client has received everything before it). */
    fun awaitHolding(seconds: Long = 10): Boolean = holding.await(seconds, TimeUnit.SECONDS)

    /** True once writing to the client failed, i.e. the client really closed the HTTP stream. */
    fun awaitClientAbort(seconds: Long = 10): Boolean = aborted.await(seconds, TimeUnit.SECONDS)

    override fun close() {
        closed = true
        gate.countDown()
        runCatching { server.close() }
        acceptor.interrupt()
    }

    private fun serve(socket: Socket) { socket.use {
        val input = socket.getInputStream()
        val output = socket.getOutputStream()
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
        requests += Request(requestLine, headers, JSONObject(String(bytes, Charsets.UTF_8)))
        when (val reply = replies.poll() ?: Reply.Error(500, error("api_error", "No scripted reply."))) {
            is Reply.Error -> {
                val body = reply.body.toString().toByteArray(Charsets.UTF_8)
                val extra = reply.headers.entries.joinToString("") { "${it.key}: ${it.value}\r\n" }
                output.write(("HTTP/1.1 ${reply.status} Scripted\r\nContent-Type: application/json\r\n" +
                    "request-id: req_fake\r\n" + extra + "Content-Length: ${body.size}\r\nConnection: close\r\n\r\n")
                    .toByteArray(Charsets.US_ASCII))
                output.write(body)
                output.flush()
            }
            Reply.Silent -> gate.await(30, TimeUnit.SECONDS)
            is Reply.Stream -> stream(reply, output)
        }
    } }

    private fun stream(reply: Reply.Stream, output: OutputStream) {
        output.write(("HTTP/1.1 200 OK\r\nContent-Type: text/event-stream\r\nCache-Control: no-cache\r\n" +
            "request-id: req_fake\r\nTransfer-Encoding: chunked\r\nConnection: close\r\n\r\n")
            .toByteArray(Charsets.US_ASCII))
        output.flush()
        try {
            reply.events.forEachIndexed { index, (name, data) ->
                if (index == reply.dropAfter) return
                if (index == reply.holdAfter) {
                    holding.countDown()
                    gate.await(30, TimeUnit.SECONDS)
                    // After the hold, keep writing until the closed connection is noticed.
                    repeat(50) {
                        chunk(output, ": keep-alive\n\n")
                        Thread.sleep(20)
                    }
                }
                chunk(output, "event: $name\ndata: $data\n\n")
            }
            if (reply.dropAfter >= 0) return
            output.write("0\r\n\r\n".toByteArray(Charsets.US_ASCII))
            output.flush()
        } catch (failure: java.io.IOException) {
            aborted.countDown()
        }
    }

    private fun chunk(output: OutputStream, text: String) {
        val bytes = text.toByteArray(Charsets.UTF_8)
        output.write((Integer.toHexString(bytes.size) + "\r\n").toByteArray(Charsets.US_ASCII))
        output.write(bytes)
        output.write("\r\n".toByteArray(Charsets.US_ASCII))
        output.flush()
    }

    private fun readLine(input: InputStream): String? {
        val buffer = ByteArrayOutputStream()
        while (true) {
            val next = input.read()
            if (next < 0) return if (buffer.size() == 0) null else buffer.toString("UTF-8")
            if (next == '\n'.code) return buffer.toString("UTF-8").trimEnd('\r')
            buffer.write(next)
        }
    }

    companion object {
        /** Obviously fake; never a real credential. */
        const val TEST_KEY = "sk-ant-test-0000-not-a-real-key"

        fun error(type: String, message: String): JSONObject = JSONObject().put("type", "error")
            .put("error", JSONObject().put("type", type).put("message", message)).put("request_id", "req_fake")

        private fun usage(input: Int, output: Int, cacheRead: Int = 0, cacheWrite: Int = 0) = JSONObject()
            .put("input_tokens", input).put("output_tokens", output)
            .put("cache_read_input_tokens", cacheRead).put("cache_creation_input_tokens", cacheWrite)

        fun messageStart(model: String = "claude-opus-5-5", input: Int = 120, cacheRead: Int = 0,
                         cacheWrite: Int = 0) = "message_start" to JSONObject().put("type", "message_start")
            .put("message", JSONObject().put("id", "msg_fake_01").put("type", "message").put("role", "assistant")
                .put("model", model).put("content", JSONArray()).put("stop_reason", JSONObject.NULL)
                .put("stop_sequence", JSONObject.NULL).put("usage", usage(input, 1, cacheRead, cacheWrite)))

        /** A thinking block as these models return it by default: empty text and a signature. */
        fun thinking(index: Int, signature: String = "sig-fake-0001") = listOf(
            "content_block_start" to JSONObject().put("type", "content_block_start").put("index", index)
                .put("content_block", JSONObject().put("type", "thinking").put("thinking", "").put("signature", "")),
            "content_block_delta" to JSONObject().put("type", "content_block_delta").put("index", index)
                .put("delta", JSONObject().put("type", "signature_delta").put("signature", signature)),
            "content_block_stop" to JSONObject().put("type", "content_block_stop").put("index", index))

        fun text(index: Int, vararg parts: String) = listOf(
            "content_block_start" to JSONObject().put("type", "content_block_start").put("index", index)
                .put("content_block", JSONObject().put("type", "text").put("text", ""))) +
            parts.map { part ->
                "content_block_delta" to JSONObject().put("type", "content_block_delta").put("index", index)
                    .put("delta", JSONObject().put("type", "text_delta").put("text", part))
            } + listOf("content_block_stop" to JSONObject().put("type", "content_block_stop").put("index", index))

        /** [inputJson] arrives split into fragments, as the API streams tool input. */
        fun toolUse(index: Int, id: String, name: String, vararg inputJson: String) = listOf(
            "content_block_start" to JSONObject().put("type", "content_block_start").put("index", index)
                .put("content_block", JSONObject().put("type", "tool_use").put("id", id).put("name", name)
                    .put("input", JSONObject()))) +
            inputJson.map { fragment ->
                "content_block_delta" to JSONObject().put("type", "content_block_delta").put("index", index)
                    .put("delta", JSONObject().put("type", "input_json_delta").put("partial_json", fragment))
            } + listOf("content_block_stop" to JSONObject().put("type", "content_block_stop").put("index", index))

        fun stop(reason: String, outputTokens: Int = 40, stopDetails: JSONObject? = null) = listOf(
            "message_delta" to JSONObject().put("type", "message_delta")
                .put("delta", JSONObject().put("stop_reason", reason).put("stop_sequence", JSONObject.NULL)
                    .apply { stopDetails?.let { put("stop_details", it) } })
                .put("usage", JSONObject().put("output_tokens", outputTokens)),
            "message_stop" to JSONObject().put("type", "message_stop"))

        val PING = "ping" to JSONObject().put("type", "ping")
    }
}
