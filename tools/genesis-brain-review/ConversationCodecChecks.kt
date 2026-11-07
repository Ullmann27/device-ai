package at.heinz.deviceai.chief

import java.io.*
import java.nio.file.Files

/** New format regression checks; positive V1 payload is emitted by real old classes. */
object ConversationCodecChecks {
    private var passed = 0
    private var failed = 0
    private fun entry(request: String = "Frühere Frage 🙂", response: String = "Frühere Antwort Ω 🙂") =
        ChiefBotConversationEntry(request, response, "review-model", "v1", "a".repeat(64))
    private fun test(name: String, block: () -> Unit) {
        try { block(); passed++; println("PASS $name") }
        catch (t: Throwable) { failed++; println("FAIL $name: ${t.javaClass.simpleName}: ${t.message}"); t.printStackTrace(System.out) }
    }
    private fun reject(block: () -> Unit) {
        var denied = false
        try { block() } catch (_: IllegalArgumentException) { denied = true }
        catch (_: IOException) { denied = true }
        check(denied) { "Malformed payload was accepted" }
    }
    private fun payload(profile: ChiefBotProfile, marker: String = "BOT_CONVERSATION_V2",
                        request: ByteArray = "question".toByteArray(), declaredSize: Int = request.size): ByteArray =
        ByteArrayOutputStream().also { buffer ->
            DataOutputStream(buffer).use { out ->
                out.writeUTF(marker); out.writeUTF(profile.agentId); out.writeUTF(profile.memoryNamespace)
                out.writeBoolean(false); out.writeLong(0); out.writeInt(1)
                out.writeInt(declaredSize); out.write(request)
                val answer = "answer".toByteArray(); out.writeInt(answer.size); out.write(answer)
                out.writeUTF("review-model"); out.writeUTF("v1"); out.writeUTF("a".repeat(64))
            }
        }.toByteArray()
    @JvmStatic fun main(args: Array<String>) {
        val fixtureRoot = File(args[1])
        if (args[0] == "write-v1") {
            val store = ChiefBotProfileStore(fixtureRoot); store.seedDefaults(3)
            val bytes = ChiefBotConversationCodec.encode(store.get("bot-kora")!!, ChiefBotConversationHistory(listOf(entry()), 2))
            check(DataInputStream(ByteArrayInputStream(bytes)).readUTF() == "BOT_CONVERSATION_V1")
            File(fixtureRoot, "real-v1.payload").writeBytes(bytes)
            println("REAL_V1_CONVERSATION_PAYLOAD_CREATED bytes=${bytes.size}")
            return
        }
        val store = ChiefBotProfileStore(fixtureRoot)
        val p = store.get("bot-kora")!!
        test("actual-old-codec-v1-payload-recalls-and-rewrites-v2") {
            val old = ChiefBotConversationCodec.decode(p, File(fixtureRoot, "real-v1.payload").readBytes())
            check(old.turns.single() == entry() && old.discardedTurns == 2L)
            val next = old.append(entry("Neue Frage", "Neue Antwort"))
            val bytes = ChiefBotConversationCodec.encode(p, next)
            check(DataInputStream(ByteArrayInputStream(bytes)).readUTF() == "BOT_CONVERSATION_V2")
            val read = ChiefBotConversationCodec.decode(p, bytes)
            check(read.turns == next.turns && read.discardedTurns == 2L)
        }
        test("v2-maximum-unicode-history-preserves-whole-turns") {
            var history = ChiefBotConversationHistory(emptyList())
            repeat(16) { history = history.append(entry("🙂".repeat(1000), "🙂".repeat(1500))) }
            check(history.turns.size == 4 && history.discardedTurns == 12L)
            val bytes = ChiefBotConversationCodec.encode(p, history)
            check(bytes.size <= ChiefBotConversationCodec.MAX_BYTES)
            val read = ChiefBotConversationCodec.decode(p, bytes)
            check(read.turns == history.turns && read.discardedTurns == history.discardedTurns)
        }
        test("v2-malformed-utf8-and-untrusted-lengths-fail-closed") {
            val invalid = listOf(byteArrayOf(0xc0.toByte(), 0xaf.toByte()),
                byteArrayOf(0xed.toByte(), 0xa0.toByte(), 0x80.toByte()),
                byteArrayOf(0xf0.toByte(), 0x9f.toByte(), 0x99.toByte()), byteArrayOf(0x80.toByte()))
            invalid.forEach { reject { ChiefBotConversationCodec.decode(p, payload(p, request = it)) } }
            listOf(-1, 0, 4001, Int.MAX_VALUE).forEach { size ->
                reject { ChiefBotConversationCodec.decode(p, payload(p, declaredSize = size)) }
            }
        }
        test("unknown-format-and-foreign-identity-remain-rejected") {
            reject { ChiefBotConversationCodec.decode(p, payload(p, marker = "BOT_CONVERSATION_V3")) }
            reject { ChiefBotConversationCodec.decode(store.get("bot-lint")!!, payload(p)) }
            val valid = payload(p)
            reject { ChiefBotConversationCodec.decode(p, valid + byteArrayOf(0)) }
        }
        println("RESULT passed=$passed failed=$failed; codec software tests only")
        check(failed == 0)
    }
}
