package at.heinz.deviceai.chief

import at.heinz.deviceai.cognitive.ContinuumObjectId
import at.heinz.deviceai.cognitive.ContinuumObjectType
import at.heinz.deviceai.cognitive.FileContinuumObjectStore
import java.io.*
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.nio.charset.CodingErrorAction
import java.nio.file.Files
import java.nio.file.LinkOption.NOFOLLOW_LINKS
import java.nio.file.StandardCopyOption.*
import java.nio.file.StandardOpenOption.*
import java.security.MessageDigest

/** Requested limits, not proof that a process/container or shared model exists. */
data class ChiefBotResourceBudget(
    val maxModelCalls: Int = 1,
    val maxToolCalls: Int = 4,
    val deadlineMillis: Long = 120_000,
    val workspaceBytes: Long = 16L * 1024 * 1024
) {
    init {
        require(maxModelCalls in 0..8 && maxToolCalls in 0..16)
        require(deadlineMillis in 1..600_000 && workspaceBytes in 1024..256L * 1024 * 1024)
    }
}

/** Identity metadata. It contains no owner facts, execution authority or mutable model session. */
data class ChiefBotProfile(
    val agentId: String,
    val profileVersion: Long,
    val displayName: String,
    val role: String,
    val communicationStyle: String,
    val modelPolicy: String,
    val memoryNamespace: String,
    val workspaceId: String,
    val resourceBudget: ChiefBotResourceBudget,
    val provenance: String,
    val contextObjectId: String? = null
) {
    // V1 cannot import or self-assert verified skills, permissions or successful work.
    val verifiedCapabilities: Set<String> get() = emptySet()
    val permissionScope: Set<String> get() = emptySet()
    val avatarProfile: String get() = "SHARED_CONTINUUM_RIG"
    val voiceProfile: String get() = "JARVIS_COORDINATED"
    val mailbox: String get() = "bot-$agentId"
    val competenceLimits: String get() = "Keine Fähigkeit unabhängig bestätigt"
    val lifecycleState: String get() = "REGISTERED"
    val currentTaskId: String? get() = null
    val lastVerifiedResult: String? get() = null
    init {
        require(ID.matches(agentId) && profileVersion >= 1)
        text(displayName, 80); text(role, 240); text(communicationStyle, 600)
        require(modelPolicy == MODEL_POLICY && provenance == PROVENANCE)
        require(memoryNamespace == "bot-$agentId" && workspaceId == "workspace-$agentId")
        require(contextObjectId == null || contextObjectId.matches(Regex("[a-f0-9]{64}")))
    }
    companion object {
        internal val ID = Regex("[a-z][a-z0-9-]{2,63}")
        const val MODEL_POLICY = "SHARED_RUNTIME_REQUEST_ISOLATED_CONTEXT"
        const val PROVENANCE = "OWNER_REQUEST_SYNAPSE_SWARM_V1"
        internal fun text(value: String, max: Int) {
            require(value.isNotBlank() && value.length <= max && value.none { it.isISOControl() })
            Charsets.UTF_8.newEncoder().onMalformedInput(CodingErrorAction.REPORT).encode(java.nio.CharBuffer.wrap(value))
        }
    }
}

data class ChiefBotProfilePage(val items: List<ChiefBotProfile>, val nextAfterId: String?)

/**
 * Persistent metadata backing the existing Chief specialist coordinator. Loading profiles never
 * registers jobs. Same-UID application code remains trusted; this API is not a filesystem sandbox.
 * Working notes reuse FileContinuumObjectStore and never enter the owner's LEX3/fact store.
 * Whole metadata snapshot is bounded; keyset pagination is a UI contract, not an indexed DB claim.
 */
class ChiefBotProfileStore(
    directory: File,
    private val maxProfiles: Int = 10_000,
    private val maxBytes: Int = 8 * 1024 * 1024,
    private val maxContextBytes: Int = 16 * 1024,
    private val maxContextStoreBytes: Long = 256 * 1024
) {
    private val root = directory.toPath().toAbsolutePath().normalize()
    private val primary = root.resolve("bot-profiles-v1.bin")
    init {
        require(maxProfiles in 1..100_000 && maxBytes in 1024..64 * 1024 * 1024)
        require(maxContextBytes in 1..64 * 1024 && maxContextStoreBytes in (maxContextBytes + 256L)..16L * 1024 * 1024)
    }

    fun get(agentId: String): ChiefBotProfile? {
        require(ChiefBotProfile.ID.matches(agentId))
        return locked { read()[agentId] }
    }

    fun page(afterAgentId: String? = null, limit: Int = 50): ChiefBotProfilePage {
        require(limit in 1..100 && (afterAgentId == null || ChiefBotProfile.ID.matches(afterAgentId)))
        return locked {
            val remaining = read().values.filter { afterAgentId == null || it.agentId > afterAgentId }
            val items = remaining.take(limit)
            ChiefBotProfilePage(items, if (remaining.size > limit) items.last().agentId else null)
        }
    }

    fun create(agentId: String, name: String, role: String, style: String): ChiefBotProfile {
        val profile = fresh(agentId, name, role, style)
        return locked {
            val profiles = read()
            require(agentId !in profiles) { "BOT_ID_ALREADY_REGISTERED" }
            profiles[agentId] = profile
            write(profiles)
            profile
        }
    }

    /** Explicit owner-selected seeds, editable later. Three is the initial connected-slice target. */
    fun seedDefaults(count: Int = 3): List<ChiefBotProfile> {
        require(count == 3 || count == 20)
        val seeds = if (count == 3) SEEDS.filter { it[0] in setOf("atlas", "kora", "lint") } else SEEDS
        return locked {
            val profiles = read()
            var changed = false
            seeds.forEach { seed ->
                val id = "bot-" + seed[0]
                if (id !in profiles) { profiles[id] = fresh(id, seed[1], seed[2], seed[3]); changed = true }
            }
            if (changed) write(profiles)
            seeds.map { profiles.getValue("bot-" + it[0]) }
        }
    }

    fun rename(agentId: String, name: String, expectedVersion: Long): ChiefBotProfile = locked {
        update(agentId, expectedVersion) { it.copy(displayName = name, profileVersion = nextVersion(it)) }
    }

    fun updateDescription(agentId: String, role: String, style: String, expectedVersion: Long): ChiefBotProfile = locked {
        update(agentId, expectedVersion) { it.copy(role = role, communicationStyle = style, profileVersion = nextVersion(it)) }
    }

    /** Application supplies an authenticated bot principal; a caller-provided name is not authentication. */
    fun saveWorkingContext(requesterId: String, agentId: String, text: String, expectedVersion: Long): ChiefBotProfile {
        require(requesterId == agentId && ChiefBotProfile.ID.matches(agentId)) { "BOT_CONTEXT_SCOPE_REJECTED" }
        val bytes = encodeText(text)
        require(bytes.size <= maxContextBytes) { "BOT_CONTEXT_CAPACITY_EXCEEDED" }
        return locked {
            update(agentId, expectedVersion) { old ->
                val id = if (text.isEmpty()) null else {
                    val payload = contextPayload(old, bytes)
                    val objectId = ContinuumObjectId.of(ContinuumObjectType.TASK_CHECKPOINT, payload)
                    val directory = contextDirectory(old)
                    checkPlain(directory.resolve(objectId.value + ".obj"))
                    val target = directory.resolve(objectId.value + ".obj")
                    if (!Files.exists(target, NOFOLLOW_LINKS)) {
                        require(contextUsedBytes(directory) <= maxContextStoreBytes - payload.size - 256) {
                            "BOT_CONTEXT_CAPACITY_EXCEEDED"
                        }
                    }
                    val stored = FileContinuumObjectStore(directory.toFile(), maxContextBytes + 256, maxContextStoreBytes)
                        .put(ContinuumObjectType.TASK_CHECKPOINT, payload)
                    FileChannel.open(directory, READ).use { it.force(true) }
                    stored.id.value
                }
                old.copy(contextObjectId = id, profileVersion = nextVersion(old))
            }
        }
    }

    fun workingContext(requesterId: String, agentId: String): String? {
        require(requesterId == agentId && ChiefBotProfile.ID.matches(agentId)) { "BOT_CONTEXT_SCOPE_REJECTED" }
        return locked {
            val profile = read()[agentId] ?: throw IllegalArgumentException("BOT_NOT_REGISTERED")
            val id = profile.contextObjectId ?: return@locked null
            val directory = contextDirectory(profile)
            checkPlain(directory.resolve("$id.obj"))
            val stored = FileContinuumObjectStore(directory.toFile(), maxContextBytes + 256, maxContextStoreBytes)
                .get(ContinuumObjectId(id)) ?: error("BOT_CONTEXT_OBJECT_MISSING")
            require(stored.metadata.type == ContinuumObjectType.TASK_CHECKPOINT)
            val input = DataInputStream(ByteArrayInputStream(stored.payload))
            require(input.readUTF() == "BOT_WORKING_CONTEXT_V1" && input.readUTF() == profile.agentId &&
                input.readUTF() == profile.memoryNamespace) { "BOT_CONTEXT_IDENTITY_MISMATCH" }
            val size = input.readInt()
            require(size in 1..maxContextBytes && size == input.available())
            val bytes = ByteArray(size); input.readFully(bytes)
            decodeText(bytes)
        }
    }

    private fun contextPayload(profile: ChiefBotProfile, bytes: ByteArray): ByteArray = ByteArrayOutputStream().also { buffer ->
        DataOutputStream(buffer).use { out ->
            out.writeUTF("BOT_WORKING_CONTEXT_V1"); out.writeUTF(profile.agentId); out.writeUTF(profile.memoryNamespace)
            out.writeInt(bytes.size); out.write(bytes)
        }
    }.toByteArray()

    private fun contextDirectory(profile: ChiefBotProfile): java.nio.file.Path {
        val directory = root.resolve("working-context").resolve(profile.memoryNamespace)
        safeDirectory(directory)
        require(contextUsedBytes(directory) <= maxContextStoreBytes) { "BOT_CONTEXT_CAPACITY_EXCEEDED" }
        return directory
    }

    private fun contextUsedBytes(directory: java.nio.file.Path): Long {
        var bytes = 0L
        Files.newDirectoryStream(directory).use { entries -> entries.forEach { entry ->
            checkPlain(entry)
            bytes = Math.addExact(bytes, Files.size(entry))
        } }
        return bytes
    }

    private fun nextVersion(profile: ChiefBotProfile): Long {
        require(profile.profileVersion < Long.MAX_VALUE) { "BOT_VERSION_EXHAUSTED" }
        return profile.profileVersion + 1
    }

    private fun update(agentId: String, expectedVersion: Long, mutation: (ChiefBotProfile) -> ChiefBotProfile): ChiefBotProfile {
        require(ChiefBotProfile.ID.matches(agentId))
        val profiles = read()
        val old = profiles[agentId] ?: throw IllegalArgumentException("BOT_NOT_REGISTERED")
        require(old.profileVersion == expectedVersion) { "BOT_PROFILE_VERSION_CONFLICT" }
        val next = mutation(old)
        profiles[agentId] = next
        write(profiles)
        return next
    }

    private fun fresh(id: String, name: String, role: String, style: String) = ChiefBotProfile(
        id, 1, name, role, style, ChiefBotProfile.MODEL_POLICY, "bot-$id", "workspace-$id",
        ChiefBotResourceBudget(), ChiefBotProfile.PROVENANCE)

    private fun read(): java.util.SortedMap<String, ChiefBotProfile> {
        checkPlain(primary)
        if (!Files.exists(primary, NOFOLLOW_LINKS)) return sortedMapOf()
        require(Files.size(primary) in 1..maxBytes.toLong()) { "BOT_REGISTRY_CAPACITY_OR_CORRUPTION" }
        val bytes = Files.newInputStream(primary, NOFOLLOW_LINKS).use { input ->
            val output = ByteArrayOutputStream()
            val block = ByteArray(8192)
            var size = input.read(block)
            while (size >= 0) {
                require(output.size() <= maxBytes - size) { "BOT_REGISTRY_CAPACITY_EXCEEDED" }
                output.write(block, 0, size)
                size = input.read(block)
            }
            output.toByteArray()
        }
        require(bytes.size <= maxBytes)
        try {
            val input = DataInputStream(ByteArrayInputStream(bytes))
            require(input.readUTF() == MAGIC && input.readInt() == 1) { "BOT_REGISTRY_UNSUPPORTED_SCHEMA" }
            val size = input.readInt()
            require(size >= 4 && size <= maxBytes && size == input.available() - 32)
            val payload = ByteArray(size); input.readFully(payload)
            val hash = ByteArray(32); input.readFully(hash)
            require(MessageDigest.isEqual(hash, digest(payload))) { "BOT_REGISTRY_CHECKSUM_MISMATCH" }
            val data = DataInputStream(ByteArrayInputStream(payload))
            val count = data.readInt(); require(count in 0..maxProfiles) { "BOT_REGISTRY_CAPACITY_EXCEEDED" }
            val profiles = sortedMapOf<String, ChiefBotProfile>()
            repeat(count) {
                val profile = ChiefBotProfile(data.readUTF(), data.readLong(), data.readUTF(), data.readUTF(), data.readUTF(),
                    data.readUTF(), data.readUTF(), data.readUTF(),
                    ChiefBotResourceBudget(data.readInt(), data.readInt(), data.readLong(), data.readLong()), data.readUTF(),
                    if (data.readBoolean()) data.readUTF() else null)
                require(profiles.put(profile.agentId, profile) == null) { "BOT_DUPLICATE_ID" }
            }
            require(data.available() == 0) { "BOT_REGISTRY_TRAILING_DATA" }
            return profiles
        } catch (e: IOException) { throw IllegalArgumentException("BOT_REGISTRY_CORRUPT", e) }
    }

    private fun write(profiles: Map<String, ChiefBotProfile>) {
        require(profiles.size <= maxProfiles) { "BOT_REGISTRY_CAPACITY_EXCEEDED" }
        val payload = ByteArrayOutputStream().also { buffer ->
            DataOutputStream(buffer).use { out ->
                out.writeInt(profiles.size)
                profiles.toSortedMap().values.forEach { p ->
                    out.writeUTF(p.agentId); out.writeLong(p.profileVersion); out.writeUTF(p.displayName); out.writeUTF(p.role)
                    out.writeUTF(p.communicationStyle); out.writeUTF(p.modelPolicy); out.writeUTF(p.memoryNamespace); out.writeUTF(p.workspaceId)
                    out.writeInt(p.resourceBudget.maxModelCalls); out.writeInt(p.resourceBudget.maxToolCalls)
                    out.writeLong(p.resourceBudget.deadlineMillis); out.writeLong(p.resourceBudget.workspaceBytes)
                    out.writeUTF(p.provenance); out.writeBoolean(p.contextObjectId != null); p.contextObjectId?.let(out::writeUTF)
                }
            }
        }.toByteArray()
        val encoded = ByteArrayOutputStream().also { buffer ->
            DataOutputStream(buffer).use { out ->
                out.writeUTF(MAGIC); out.writeInt(1); out.writeInt(payload.size); out.write(payload); out.write(digest(payload))
            }
        }.toByteArray()
        require(encoded.size <= maxBytes) { "BOT_REGISTRY_CAPACITY_EXCEEDED" }
        checkPlain(primary)
        val temp = Files.createTempFile(root, ".bot-profiles-", ".tmp")
        try {
            FileChannel.open(temp, WRITE, NOFOLLOW_LINKS).use { channel ->
                var buffer = ByteBuffer.wrap(encoded)
                while (buffer.hasRemaining()) channel.write(buffer)
                channel.force(true)
            }
            Files.move(temp, primary, ATOMIC_MOVE, REPLACE_EXISTING)
            FileChannel.open(root, READ).use { it.force(true) }
        } finally { Files.deleteIfExists(temp) }
    }

    private fun <T> locked(action: () -> T): T {
        synchronized(LOCKS[(root.toString().hashCode() and Int.MAX_VALUE) % LOCKS.size]) {
            safeDirectory(root)
            val lock = root.resolve(".bot-profiles.lock")
            checkPlain(lock)
            FileChannel.open(lock, CREATE, WRITE, NOFOLLOW_LINKS).use { channel ->
                channel.lock().use { return action() }
            }
        }
    }

    private fun checkPlain(path: java.nio.file.Path) {
        require(!Files.isSymbolicLink(path)) { "BOT_STORE_SYMLINK_REJECTED" }
        require(!Files.exists(path, NOFOLLOW_LINKS) || Files.isRegularFile(path, NOFOLLOW_LINKS)) { "BOT_STORE_NOT_FILE" }
    }

    private fun safeDirectory(path: java.nio.file.Path) {
        var current = path.root
        for (segment in path) {
            current = current.resolve(segment)
            require(!Files.isSymbolicLink(current)) { "BOT_STORE_SYMLINK_REJECTED" }
            if (!Files.exists(current, NOFOLLOW_LINKS)) {
                try {
                    Files.createDirectory(current)
                    FileChannel.open(current.parent, READ).use { it.force(true) }
                } catch (_: java.nio.file.FileAlreadyExistsException) { }
            }
            require(Files.isDirectory(current, NOFOLLOW_LINKS)) { "BOT_STORE_NOT_DIRECTORY" }
        }
    }

    companion object {
        private const val MAGIC = "CONTINUUM_BOT_PROFILES"
        private val LOCKS = Array(64) { Any() }
        private fun digest(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes)
        private fun encodeText(text: String): ByteArray {
            val encoded = Charsets.UTF_8.newEncoder().onMalformedInput(CodingErrorAction.REPORT).encode(java.nio.CharBuffer.wrap(text))
            return ByteArray(encoded.remaining()).also(encoded::get)
        }
        private fun decodeText(bytes: ByteArray): String = Charsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString()
        private val SEEDS = listOf(
            listOf("atlas", "Atlas", "Koordination im vorhandenen Chief", "Ruhig, priorisiert, hält Zusagen nach."),
            listOf("mira", "Mira", "Gespräch und Nutzerabsicht", "Zugewandt, klar, erkennt Mehrdeutigkeit."),
            listOf("echo", "Echo", "Kontext und Übergaben", "Genau, hält offene Fragen und Zusagen fest."),
            listOf("mnemos", "Mnemos", "Gedächtnis und Zeitbezug", "Sorgfältig, trennt frühere und aktuelle Angaben."),
            listOf("prisma", "Prisma", "Quellen und Belege", "Skeptisch, prüft Herkunft und Aussagekraft."),
            listOf("nova", "Nova", "Forschungshypothesen", "Neugierig, experimentiert nachvollziehbar."),
            listOf("sigma", "Sigma", "Versuchsplanung und Auswertung", "Nüchtern, prüft Vergleichbarkeit."),
            listOf("kora", "Kora", "Codeanalyse und Reparatur", "Konzentriert, kleine Änderungen."),
            listOf("forge", "Forge", "Build und Abhängigkeiten", "Praktisch, reproduzierbare Abläufe."),
            listOf("lint", "Lint", "Unabhängige Codeprüfung", "Präzise, konkrete Gegenbeispiele."),
            listOf("argus", "Argus", "Rechte und Ausführungsgrenzen", "Wachsam, erklärt konkrete Blockaden."),
            listOf("pulse", "Pulse", "RAM, Laufzeit und Temperatur", "Sparsam, schützt Bedienbarkeit."),
            listOf("link", "Link", "Schnittstellen und Integration", "Systematisch, echte Datenflüsse."),
            listOf("chronos", "Chronos", "Aufträge und Wiederaufnahme", "Verlässlich, verhindert doppelte Aktionen."),
            listOf("vox", "Vox", "Sprache und Gesprächswechsel", "Geduldig, flüssige Übergänge."),
            listOf("iris", "Iris", "Avatar und Animation", "Ausdrucksstark, visuelle Konsistenz."),
            listOf("orbit", "Orbit", "Benutzeroberfläche", "Übersichtlich, Lesbarkeit."),
            listOf("archiv", "Archiv", "Dateien und Wissensbestand", "Ordentlich, Versionen und Herkunft."),
            listOf("scout", "Scout", "Freigegebene Webrecherche", "Erkundend, Primärquellen."),
            listOf("helix", "Helix", "Lernverfahren und Rückfallprüfung", "Experimentell, geprüfte Verbesserungen.")
        )
    }
}
