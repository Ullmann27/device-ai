package at.heinz.deviceai.chief

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.security.MessageDigest

enum class ChiefTeamStatus { ACCEPTED, ANALYZING, REVIEWING, RESPONSES_RECORDED, BLOCKED, FAILED, CANCELLED, INTERRUPTED }

/** Generation evidence is not an independent correctness, compiler or tool receipt. */
data class ChiefTeamResponse(
    val agentId: String,
    val stage: String,
    val promptSha256: String,
    val text: String,
    val outputSha256: String,
    val elapsedMillis: Long
) {
    init {
        require(ChiefBotProfile.ID.matches(agentId) && stage in setOf("ANALYZE", "REVIEW"))
        require(HASH.matches(promptSha256) && outputSha256 == hash(text))
        require(text.isNotBlank() && text.toByteArray(Charsets.UTF_8).size <= 6_000 && elapsedMillis >= 0)
    }

    companion object {
        internal val HASH = Regex("[a-f0-9]{64}")
        internal fun hash(text: String): String = MessageDigest.getInstance("SHA-256")
            .digest(text.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
    }
}

data class ChiefTeamTask(
    val id: String,
    val owner: String,
    val chiefId: String,
    val analystId: String,
    val reviewerId: String,
    val profileVersions: List<Long>,
    val teamRevision: Long,
    val request: String,
    val modelSha256: String,
    val createdAt: Long,
    val status: ChiefTeamStatus,
    val diagnostic: String = "",
    val responses: List<ChiefTeamResponse> = emptyList()
) {
    val active: Boolean get() = status in setOf(ChiefTeamStatus.ACCEPTED, ChiefTeamStatus.ANALYZING, ChiefTeamStatus.REVIEWING)
    init {
        require(Regex("team-[a-f0-9-]{36}").matches(id) && owner == "LOCAL_OWNER_UI")
        val ids = listOf(chiefId, analystId, reviewerId)
        require(ids.all(ChiefBotProfile.ID::matches) && ids.distinct().size == 3)
        require(profileVersions.size == 3 && profileVersions.all { it > 0 } && teamRevision > 0)
        require(request.isNotBlank() && request.length <= 1_800 && Charsets.UTF_8.newEncoder().canEncode(request))
        require(ChiefTeamResponse.HASH.matches(modelSha256) && createdAt > 0)
        require(diagnostic.length <= 80 && diagnostic.all { it in 'A'..'Z' || it in '0'..'9' || it == '_' })
        require(responses.size <= 2)
        responses.forEachIndexed { index, r ->
            require(r.agentId == if (index == 0) analystId else reviewerId)
            require(r.stage == if (index == 0) "ANALYZE" else "REVIEW")
        }
        require(status != ChiefTeamStatus.RESPONSES_RECORDED || responses.size == 2)
    }
}

data class ChiefBotTeamState(
    val revision: Long = 0,
    val chiefId: String? = null,
    val specialistIds: List<String> = emptyList(),
    val tasks: List<ChiefTeamTask> = emptyList()
) {
    init {
        require(revision >= 0 && (chiefId == null || ChiefBotProfile.ID.matches(chiefId)))
        require(specialistIds.size <= 2 && specialistIds.distinct().size == specialistIds.size &&
            specialistIds.all { ChiefBotProfile.ID.matches(it) && it != chiefId })
        require(tasks.size <= 40 && tasks.map { it.id }.distinct().size == tasks.size)
        require(tasks.count { it.active } <= 1)
    }
}

/** Explicit, bounded encoding in the existing profile store; never Java object deserialization. */
internal object ChiefBotTeamCodec {
    const val MAX_BYTES = 1_000_000
    fun encode(state: ChiefBotTeamState): ByteArray = ByteArrayOutputStream().also { bytes ->
        DataOutputStream(bytes).use { out ->
            out.writeUTF("CONTINUUM_BOT_TEAM_V1")
            out.writeLong(state.revision); out.writeUTF(state.chiefId.orEmpty())
            out.writeInt(state.specialistIds.size); state.specialistIds.forEach(out::writeUTF)
            out.writeInt(state.tasks.size)
            state.tasks.forEach { t ->
                listOf(t.id, t.owner, t.chiefId, t.analystId, t.reviewerId).forEach(out::writeUTF)
                t.profileVersions.forEach(out::writeLong); out.writeLong(t.teamRevision)
                out.writeUTF(t.request); out.writeUTF(t.modelSha256); out.writeLong(t.createdAt)
                out.writeUTF(t.status.name); out.writeUTF(t.diagnostic); out.writeInt(t.responses.size)
                t.responses.forEach { r ->
                    listOf(r.agentId, r.stage, r.promptSha256, r.text, r.outputSha256).forEach(out::writeUTF)
                    out.writeLong(r.elapsedMillis)
                }
            }
        }
    }.toByteArray().also { require(it.size <= MAX_BYTES) { "BOT_TEAM_CAPACITY" } }

    fun decode(bytes: ByteArray): ChiefBotTeamState {
        require(bytes.size in 1..MAX_BYTES) { "BOT_TEAM_CORRUPT" }
        DataInputStream(ByteArrayInputStream(bytes)).use { input ->
            require(input.readUTF() == "CONTINUUM_BOT_TEAM_V1") { "BOT_TEAM_SCHEMA" }
            val revision = input.readLong()
            val chief = input.readUTF().ifEmpty { null }
            val ids = List(input.readInt().also { require(it in 0..2) }) { input.readUTF() }
            val tasks = List(input.readInt().also { require(it in 0..40) }) {
                val fields = List(5) { input.readUTF() }
                val versions = List(3) { input.readLong() }
                val teamRevision = input.readLong()
                val request = input.readUTF(); val model = input.readUTF(); val at = input.readLong()
                val status = ChiefTeamStatus.valueOf(input.readUTF()); val diagnostic = input.readUTF()
                val responses = List(input.readInt().also { require(it in 0..2) }) {
                    ChiefTeamResponse(input.readUTF(), input.readUTF(), input.readUTF(), input.readUTF(),
                        input.readUTF(), input.readLong())
                }
                ChiefTeamTask(fields[0], fields[1], fields[2], fields[3], fields[4], versions,
                    teamRevision, request, model, at, status, diagnostic, responses)
            }
            require(input.available() == 0) { "BOT_TEAM_TRAILING_DATA" }
            return ChiefBotTeamState(revision, chief, ids, tasks)
        }
    }
}
