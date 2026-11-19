package at.heinz.deviceai.briefing

import at.heinz.deviceai.ToolCallRequest
import at.heinz.deviceai.ToolRunner
import at.heinz.deviceai.cognitive.ApprovedWebResearchSession
import at.heinz.deviceai.cognitive.HttpReadOnlyResearchProvider
import at.heinz.deviceai.cognitive.ResearchProvider
import at.heinz.deviceai.cognitive.ResearchProviderPolicy
import at.heinz.deviceai.cognitive.ResearchRequest
import at.heinz.deviceai.cognitive.WebToolRequest

/**
 * Reads the weather for the owner's place from Open-Meteo under ONE consumed owner web grant – the same
 * [ApprovedWebResearchSession] that web research and the news feeds use (ASK ticket or ALWAYS_ON grant; OFF never
 * gets here), run through the common ToolGate. The grant is narrowed, not widened: only the two Open-Meteo hosts are
 * reachable (no redirects), GET only, bounded bytes and time, through the pinned research transport.
 *
 * Transmitted: the place name (once, to find its coordinates) and the coordinates. A place that is not found or a
 * failed request is reported as such and never replaced by invented weather; the tool itself only fails when the
 * grant was revoked.
 */
internal class ApprovedWeatherToolRunner(
    private val session: ApprovedWebResearchSession,
    private val request: ContinuumWeatherRequest,
    provider: ResearchProvider
) : ToolRunner {
    private val guarded = session.provider(provider)
    @Volatile private var completed: ContinuumWeatherRead? = null

    /** The weather as read; null until the tool finished or once the grant is no longer current. */
    val result: ContinuumWeatherRead? get() = completed?.takeIf { session.isAuthorized() }

    init { require(request.placeName.isNotBlank() && request.placeName.length <= 80) }

    override fun execute(request: ToolCallRequest): String {
        val call = WebToolRequest.fromTool(request.name, request.arguments)
        val read = session.execute(call) { read() }
        session.checkAuthorized()
        completed = read
        return when (read) {
            is ContinuumWeatherRead.Report -> "Wetter gelesen: " + read.report.place.label
            is ContinuumWeatherRead.PlaceUnknown -> "Ort nicht gefunden"
            is ContinuumWeatherRead.Failed -> "Wetter nicht gelesen (" + read.reason + ")"
        }.also { session.checkAuthorized() }
    }

    private fun fetch(label: String, url: String): String {
        session.checkActive()
        return guarded.retrieve(ResearchRequest(label, url, maxBytes = MAX_BYTES, timeoutMillis = TIMEOUT_MILLIS,
            maxRedirects = 0)).documents.firstOrNull()?.content ?: throw java.io.IOException("leere Antwort")
    }

    private fun read(): ContinuumWeatherRead = try {
        val place = request.known ?: ContinuumWeather.choose(request.placeName,
            ContinuumWeather.parsePlaces(fetch("Wetter-Ort", ContinuumWeather.geocodingUrl(request.placeName))))
        if (place == null) ContinuumWeatherRead.PlaceUnknown(request.placeName.trim())
        else ContinuumWeatherRead.Report(ContinuumWeather.parseForecast(
            fetch("Wettervorhersage", ContinuumWeather.forecastUrl(place)), place))
    } catch (interrupted: InterruptedException) {
        Thread.currentThread().interrupt()
        ContinuumWeatherRead.Failed("abgebrochen")
    } catch (error: Exception) {
        ContinuumWeatherRead.Failed(ContinuumWeather.failureClause(error))
    }

    companion object {
        const val MAX_BYTES = 64_000
        const val TIMEOUT_MILLIS = 8_000

        /** Only the two Open-Meteo hosts, no redirects, bounded bytes and time. */
        val policy = ResearchProviderPolicy(allowedHosts = ContinuumWeather.hosts, totalTimeoutMillis = TIMEOUT_MILLIS,
            maxBytes = MAX_BYTES, maxRedirects = 0, maxContentCharacters = 64_000)

        /** The research transport restricted by [policy]; [accessCheck] is the live grant check. */
        fun transport(accessCheck: () -> Unit): HttpReadOnlyResearchProvider =
            HttpReadOnlyResearchProvider(policy, accessCheck = accessCheck)
    }
}
