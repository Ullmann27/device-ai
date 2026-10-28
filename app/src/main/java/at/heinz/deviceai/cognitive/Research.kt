package at.heinz.deviceai.cognitive

import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.InetAddress
import java.net.Proxy
import java.net.URI
import java.util.Locale
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import javax.net.ssl.HttpsURLConnection

data class ResearchRequest(
    val query: String,
    val url: String,
    val maxBytes: Int = 512_000,
    val timeoutMillis: Int = 30_000,
    val maxRedirects: Int = 3
) {
    init {
        require(query.isNotBlank() && query.length <= 2_048 && url.isNotBlank() && url.length <= 4_096)
        require(maxBytes in 1..2_000_000)
        require(timeoutMillis in 1..30_000 && maxRedirects in 0..5)
    }
}

data class ResearchCitation(val url: String, val title: String?, val retrievedAt: Long, val contentHash: String) {
    init { require(url.isNotBlank() && retrievedAt >= 0 && contentHash.matches(Regex("[a-f0-9]{64}"))) }
}

data class ResearchDocument(
    val url: String,
    val title: String?,
    val retrievedAt: Long,
    val contentHash: String,
    val mimeType: String,
    val source: SourceIdentity,
    val content: String
) {
    init {
        require(url.isNotBlank() && retrievedAt >= 0 && content.isNotBlank())
        require(contentHash.matches(Regex("[a-f0-9]{64}")) && mimeType.isNotBlank())
    }

    fun citation() = ResearchCitation(url, title, retrievedAt, contentHash)
}

data class ResearchResult(val documents: List<ResearchDocument>, val citations: List<ResearchCitation>)

data class ResearchProviderPolicy(
    val allowedHosts: Set<String> = emptySet(),
    val connectTimeoutMillis: Int = 5_000,
    val readTimeoutMillis: Int = 8_000,
    val totalTimeoutMillis: Int = 30_000,
    val maxBytes: Int = 512_000,
    val maxRedirects: Int = 3,
    val maxContentCharacters: Int = 32_000
) {
    init {
        require(connectTimeoutMillis in 1..30_000 && readTimeoutMillis in 1..30_000)
        require(totalTimeoutMillis in 1..120_000)
        require(maxBytes in 1..2_000_000 && maxRedirects in 0..5 && maxContentCharacters in 1..100_000)
        require(allowedHosts.all { it.isNotBlank() && it == it.lowercase(Locale.ROOT) })
    }
}

interface ResearchProvider {
    fun retrieve(request: ResearchRequest): ResearchResult
}

class SimulatedResearchProvider(
    private val documents: Map<String, ResearchDocument>,
    private val urlResults: Map<String, List<String>> = emptyMap()
) : ResearchProvider {
    override fun retrieve(request: ResearchRequest): ResearchResult {
        val results = urlResults[request.url]?.mapNotNull(documents::get)
            ?: listOfNotNull(documents[request.url])
        require(results.all { it.content.toByteArray(Charsets.UTF_8).size <= request.maxBytes })
        return ResearchResult(results, results.map(ResearchDocument::citation))
    }
}

internal interface ResearchHttpEnvironment {
    fun resolve(host:String):List<InetAddress>
    fun open(uri:URI,addresses:List<InetAddress>,timeoutMillis:Int):HttpsURLConnection
}

private object PublicResearchHttpEnvironment:ResearchHttpEnvironment {
    override fun resolve(host:String)=InetAddress.getAllByName(host).toList()
    override fun open(uri:URI,addresses:List<InetAddress>,timeoutMillis:Int)=PinnedResearchHttpsConnection(uri,addresses,timeoutMillis)
}

class HttpReadOnlyResearchProvider private constructor(
    private val policy: ResearchProviderPolicy,
    private val now: () -> Long,
    private val accessCheck: () -> Unit,
    private val network:ResearchHttpEnvironment
) : ResearchProvider {
    constructor(policy:ResearchProviderPolicy=ResearchProviderPolicy(),now:()->Long=System::currentTimeMillis,
        accessCheck:()->Unit={}):this(policy,now,accessCheck,PublicResearchHttpEnvironment)
    internal constructor(network:ResearchHttpEnvironment,policy:ResearchProviderPolicy=ResearchProviderPolicy(),
        now:()->Long=System::currentTimeMillis,accessCheck:()->Unit={}):this(policy,now,accessCheck,network)
    override fun retrieve(request: ResearchRequest): ResearchResult = fetch(request,false)
    /** Raw bounded markup is for discovery parsers only, never a model instruction. */
    fun retrieveMarkup(request:ResearchRequest):ResearchResult = fetch(request,true)
    private fun fetch(request:ResearchRequest,keepMarkup:Boolean):ResearchResult {
        accessCheck()
        require(request.maxBytes <= policy.maxBytes)
        var uri = URI(request.url)
        var redirects = 0
        val deadlineNanos = System.nanoTime() +
            minOf(policy.totalTimeoutMillis, request.timeoutMillis) * 1_000_000L
        while (true) {
            accessCheck()
            val validatedAddresses = ResearchUrlValidator.validate(uri, policy.allowedHosts,network::resolve)
            val remainingMillis = remainingTimeoutMillis(deadlineNanos)
            val connection = network.open(uri,validatedAddresses,remainingMillis).apply {
                requestMethod = "GET"
                useCaches = false
                instanceFollowRedirects = false
                connectTimeout = minOf(policy.connectTimeoutMillis, request.timeoutMillis, remainingMillis)
                readTimeout = minOf(policy.readTimeoutMillis, request.timeoutMillis, remainingMillis)
                setRequestProperty("Accept", "text/html,text/plain,application/json,application/xml")
                setRequestProperty("User-Agent", "DeviceAI-Research/1.0")
            }
            val timeoutTask = REQUEST_TIMEOUTS.schedule(
                { connection.disconnect() },
                remainingMillis.toLong(),
                TimeUnit.MILLISECONDS
            )
            try {
                val status = connection.responseCode
                if (status in 300..399) {
                    require(redirects < minOf(policy.maxRedirects, request.maxRedirects)) {
                        "Too many research redirects."
                    }
                    val location = connection.getHeaderField("Location")
                        ?: throw IllegalArgumentException("Redirect has no location.")
                    uri = uri.resolve(location)
                    redirects++
                    continue
                }
                require(status in 200..299) { "Research request failed with HTTP $status." }
                val mime = connection.contentType?.substringBefore(';')?.trim()?.lowercase(Locale.ROOT)
                    ?: throw IllegalArgumentException("Research response has no MIME type.")
                require(mime in SAFE_MIME_TYPES) { "Unsupported research MIME type." }
                val announced = connection.contentLengthLong
                require(announced < 0 || announced <= request.maxBytes) { "Research response is too large." }
                val body = connection.inputStream.use { readLimited(it, request.maxBytes, deadlineNanos) }
                val rawText = String(body, Charsets.UTF_8)
                val content=responseText(rawText,mime,keepMarkup,policy.maxContentCharacters)
                require(content.isNotBlank()) { "Research document is empty." }
                val finalUrl = uri.toASCIIString()
                val sourceHost = uri.host.lowercase(Locale.ROOT).removePrefix("[").removeSuffix("]")
                val title = Regex(
                    "<title\\b[^>]*>(.*?)</title\\s*>",
                    setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
                ).find(rawText)?.groupValues?.get(1)
                    ?.replace(Regex("<[^>]+>"), "")?.trim()?.take(256)?.takeIf(String::isNotBlank)
                val document = ResearchDocument(
                    url = finalUrl,
                    title = title,
                    retrievedAt = now(),
                    contentHash = sha256Hex(content),
                    mimeType = mime,
                    source = SourceIdentity(sourceHost, sourceHost),
                    content = content
                )
                return ResearchResult(listOf(document), listOf(document.citation()))
            } finally {
                timeoutTask.cancel(false)
                connection.disconnect()
            }
        }
    }

    private fun readLimited(input: java.io.InputStream, maximum: Int, deadlineNanos: Long): ByteArray {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(8_192)
        while (true) {
            remainingTimeoutMillis(deadlineNanos)
            val read = input.read(buffer)
            if (read < 0) break
            require(output.size() + read <= maximum) { "Research response is too large." }
            output.write(buffer, 0, read)
        }
        return output.toByteArray()
    }

    private fun remainingTimeoutMillis(deadlineNanos: Long): Int {
        accessCheck()
        require(!Thread.currentThread().isInterrupted) { "Research request cancelled" }
        val remainingNanos = deadlineNanos - System.nanoTime()
        require(remainingNanos > 0) { "Research request exceeded its total time limit." }
        return ((remainingNanos + 999_999) / 1_000_000).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
    }

    object ResearchUrlValidator {
        fun validateSyntax(uri:URI,allowedHosts:Set<String> = emptySet()) {
            require(uri.scheme.equals("https", ignoreCase = true)) { "Research supports HTTPS only." }
            require(uri.userInfo == null && uri.host != null && (uri.port == -1 || uri.port == 443)) {
                "Unsafe research URL authority."
            }
            val host = uri.host.removePrefix("[").removeSuffix("]").lowercase(Locale.ROOT).trimEnd('.')
            require(host.contains('.') || host.contains(':')) { "Single-label research hosts are blocked." }
            require(uri.rawQuery.orEmpty().split('&').none {
                var key=it.substringBefore('=')
                repeat(3) { key=runCatching { java.net.URLDecoder.decode(key,"UTF-8") }.getOrDefault(key) }
                key.matches(Regex("(?i).*(token|secret|password|credential|api[_-]?key|authorization).*"))
            }) { "Credential-like URL parameters are not accepted." }
            require(host !in setOf("localhost", "localhost.localdomain") && !host.endsWith(".local") &&
                !host.endsWith(".localhost") && !host.endsWith(".internal") && !host.endsWith(".lan") &&
                !host.endsWith(".home.arpa") && !host.endsWith(".corp") && !host.endsWith(".intranet") &&
                !host.endsWith(".test") && !host.endsWith(".example") && !host.endsWith(".invalid")) {
                "Private or reserved research hosts are blocked."
            }
            if (allowedHosts.isNotEmpty()) {
                require(allowedHosts.any { host == it || host.endsWith(".$it") }) { "Research host is not allowlisted." }
            }
            if(host.contains(':') || host.matches(Regex("[0-9.]+"))) {
                require(runCatching { isPublicAddress(InetAddress.getByName(host)) }.getOrDefault(false)) { "Private or reserved address literal" }
            }
        }
        fun validate(uri:URI,allowedHosts:Set<String> = emptySet(),resolve:(String)->List<InetAddress> = {InetAddress.getAllByName(it).toList()}):List<InetAddress> {
            validateSyntax(uri,allowedHosts)
            val host=uri.host.removePrefix("[").removeSuffix("]").lowercase(Locale.ROOT).trimEnd('.')
            val addresses = runCatching { resolve(host) }
                .getOrElse { throw IllegalArgumentException("Research host could not be resolved.") }
            require(addresses.size in 1..16) { "Research host returned an unsafe number of addresses." }
            require(addresses.isNotEmpty() && addresses.all(::isPublicAddress)) { "Private or reserved network targets are blocked." }
            return addresses.toList()
        }

        private fun isPublicAddress(address: InetAddress): Boolean {
            if (address.isAnyLocalAddress || address.isLoopbackAddress || address.isLinkLocalAddress ||
                address.isSiteLocalAddress || address.isMulticastAddress) return false
            val bytes = address.address
            if (bytes.size == 4) {
                val first = bytes[0].toInt() and 0xff
                val second = bytes[1].toInt() and 0xff
                val third = bytes[2].toInt() and 0xff
                if (first == 0 || first == 10 || first == 127 || first >= 224) return false
                if (first == 100 && second in 64..127) return false
                if (first == 169 && second == 254 || first == 172 && second in 16..31 ||
                    first == 192 && second == 168 || first == 192 && second == 0 ||
                    first == 192 && second == 2 || first == 192 && second == 88 && third == 99 ||
                    first == 198 && second in 18..19 || first == 198 && second == 51 && third == 100 ||
                    first == 203 && second == 0 && third == 113) return false
            } else if (bytes.size == 16) {
                val first = bytes[0].toInt() and 0xff
                if (bytes.take(10).all { it == 0.toByte() } &&
                    (bytes[10].toInt() and 0xff) == 0xff && (bytes[11].toInt() and 0xff) == 0xff) {
                    return isPublicAddress(InetAddress.getByAddress(bytes.copyOfRange(12, 16)))
                }
                if (bytes.take(12).all { it == 0.toByte() }) {
                    return isPublicAddress(InetAddress.getByAddress(bytes.copyOfRange(12, 16)))
                }
                val second = bytes[1].toInt() and 0xff
                val third = bytes[2].toInt() and 0xff
                val fourth = bytes[3].toInt() and 0xff
                if ((first and 0xe0) != 0x20 || (first and 0xfe) == 0xfc ||
                    first == 0x20 && second == 0x01 && third in 0..1 ||
                    first == 0x20 && second == 0x01 && third == 0x0d && fourth == 0xb8 ||
                    first == 0x20 && second == 0x02) return false
            }
            return true
        }
    }

    companion object {
        fun responseText(raw:String,mime:String,keepMarkup:Boolean,limit:Int):String {
            require(limit in 1..100000)
            if(keepMarkup || mime !in setOf("text/html"))return raw.trim().take(limit)
            return raw.replace(Regex("<script\\b[^>]*>[\\s\\S]*?</script\\s*>",RegexOption.IGNORE_CASE)," ")
                .replace(Regex("<style\\b[^>]*>[\\s\\S]*?</style\\s*>",RegexOption.IGNORE_CASE)," ")
                .replace(Regex("<[^>]+>")," ").replace(Regex("\\s+")," ").trim().take(limit)
        }
        private val SAFE_MIME_TYPES = setOf(
            "text/html", "text/plain", "application/json", "application/xml", "text/xml", "application/rss+xml"
        )
        private val REQUEST_TIMEOUTS = Executors.newSingleThreadScheduledExecutor { runnable ->
            Thread(runnable, "device-ai-research-timeout").apply { isDaemon = true }
        }
    }
}

fun interface ClaimExtractor {
    fun extract(document: ResearchDocument): List<Claim>
}

class BaselineClaimExtractor(private val now: () -> Long = System::currentTimeMillis) : ClaimExtractor {
    override fun extract(document: ResearchDocument): List<Claim> =
        document.content.split(Regex("(?<=[.!?])\\s+|\\n+"))
            .map(String::trim)
            .filter { it.length >= 12 }
            .take(100)
            .mapIndexed { index, text ->
                Claim(
                    id = "${sha256Hex(document.url).take(12)}-$index",
                    text = text.take(512),
                    provenance = ProvenanceType.EXTERNAL_SOURCE,
                    sourceReferences = listOf(document.url),
                    createdAt = now()
                )
            }
}

data class ClaimRelationship(val fromId: String, val toId: String, val relation: ClaimRelation)

data class CorroborationPolicy(val minimumIndependentSources: Int = 2) {
    init { require(minimumIndependentSources in 2..10) }

    fun verifiedClaims(
        claims: List<Claim>,
        sourcesByUrl: Map<String, SourceIdentity>,
        relationships: List<ClaimRelationship>
    ): List<Claim> {
        val supportedIds = relationships.filter { it.relation == ClaimRelation.SUPPORTS }
            .flatMap { listOf(it.fromId, it.toId) }.toSet()
        return claims.map { claim ->
            val related = relationships.filter {
                it.relation == ClaimRelation.SUPPORTS && (it.fromId == claim.id || it.toId == claim.id)
            }.flatMap { listOf(it.fromId, it.toId) }.filterNot { it == claim.id }.toSet()
            val independent = (claim.sourceReferences.mapNotNull(sourcesByUrl::get) +
                claims.filter { it.id in related }.flatMap { relatedClaim ->
                    relatedClaim.sourceReferences.mapNotNull(sourcesByUrl::get)
                }).distinctBy { it.domain }
            if (claim.id in supportedIds && independent.size >= minimumIndependentSources) {
                claim.validated(
                    ValidationState.VERIFIED,
                    (claim.sourceReferences + claims.filter { it.id in related }.flatMap { it.sourceReferences }).distinct()
                )
            } else claim
        }
    }
}

data class ResearchSynthesis(
    val documents: List<ResearchDocument>,
    val claims: List<Claim>,
    val relationships: List<ClaimRelationship>,
    val citations: List<ResearchCitation>
)

class ResearchPipeline(
    private val provider: ResearchProvider,
    private val extractor: ClaimExtractor = BaselineClaimExtractor(),
    private val corroborationPolicy: CorroborationPolicy = CorroborationPolicy(),
    private val sourceTrust: SourceTrustStore? = null
) {
    fun research(request: ResearchRequest): ResearchSynthesis {
        val result = provider.retrieve(request)
        val claims = result.documents.flatMap(extractor::extract)
        val relationships = mutableListOf<ClaimRelationship>()
        for (left in claims.indices) for (right in left + 1 until claims.size) {
            val a = claims[left]
            val b = claims[right]
            val aSource = result.documents.firstOrNull { it.url in a.sourceReferences }?.source
            val bSource = result.documents.firstOrNull { it.url in b.sourceReferences }?.source
            if (aSource != null && bSource != null && aSource.domain != bSource.domain) {
                val similarity = similarity(a.text, b.text)
                when {
                    // Conflict evidence must take precedence over lexical similarity:
                    // "supports charging" and "supports charging nicht" overlap heavily.
                    contradicts(a.text, b.text) -> relationships += ClaimRelationship(a.id, b.id, ClaimRelation.CONTRADICTS)
                    similarity >= 0.7 -> relationships += ClaimRelationship(a.id, b.id, ClaimRelation.SUPPORTS)
                }
            }
        }
        val sources = result.documents.associate { it.url to it.source }
        val verified = corroborationPolicy.verifiedClaims(claims, sources, relationships)
        return ResearchSynthesis(result.documents, verified, relationships, result.citations)
    }

    fun recordEvaluatedSource(
        document: ResearchDocument,
        accuracy: Double,
        corroborated: Boolean,
        contradicted: Boolean
    ): SourceTrustProfile? = sourceTrust?.observe(
        SourceObservation(
            document.source, evaluated = true, accuracy = accuracy,
            freshness = 1.0, corroborated = corroborated, contradicted = contradicted
        )
    )

    fun storeClaimMemory(
        claim: Claim,
        store: MemoryStore,
        writePolicy: MemoryWritePolicy,
        durableKnowledge: Boolean
    ): MemoryRecord? {
        val decision = writePolicy.decide(
            MemoryWriteContext(
                memoryType = MemoryType.SEMANTIC,
                provenance = claim.provenance,
                confidence = claim.confidence,
                durableKnowledge = durableKnowledge
            )
        )
        if (!decision.shouldWrite) return null
        return MemoryRecord(
            id = "research-${claim.id}",
            type = MemoryType.SEMANTIC,
            content = claim.text,
            provenance = claim.provenance,
            confidence = claim.confidence,
            createdAt = claim.createdAt,
            updatedAt = claim.createdAt,
            sourceReference = claim.sourceReferences.firstOrNull(),
            validationState = claim.validationState,
            privacyClass = decision.privacyClass,
            validationEvidenceReferences = claim.validationEvidenceReferences
        ).also(store::put)
    }

    private fun similarity(first: String, second: String): Double {
        val a = tokens(first)
        val b = tokens(second)
        if (a.isEmpty() || b.isEmpty()) return 0.0
        return a.intersect(b).size.toDouble() / a.union(b).size
    }

    private fun contradicts(first: String, second: String): Boolean {
        return similarity(first, second) >= 0.45 &&
            ResearchNegationSignals.hasOppositePolarity(first, second)
    }

    private fun tokens(text: String) = text.lowercase(Locale.ROOT)
        .split(Regex("[^\\p{L}\\p{N}']+")).filter(String::isNotBlank).toSet()
}

