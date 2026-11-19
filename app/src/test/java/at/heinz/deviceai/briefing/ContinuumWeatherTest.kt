package at.heinz.deviceai.briefing

import at.heinz.deviceai.ToolExecutionResult
import at.heinz.deviceai.ToolExecutionStatus
import at.heinz.deviceai.ToolGate
import at.heinz.deviceai.ToolPolicy
import at.heinz.deviceai.cognitive.ApprovedWebResearchSession
import at.heinz.deviceai.cognitive.ApprovedWebToolGate
import at.heinz.deviceai.cognitive.HttpReadOnlyResearchProvider
import at.heinz.deviceai.cognitive.InternetAccessMode
import at.heinz.deviceai.cognitive.InternetConsentPolicy
import at.heinz.deviceai.cognitive.ResearchHttpEnvironment
import at.heinz.deviceai.cognitive.ResearchMode
import at.heinz.deviceai.cognitive.WebToolRequest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.net.InetAddress
import java.net.URI
import java.net.URL
import java.security.cert.Certificate
import java.time.LocalDate
import java.util.Collections
import javax.net.ssl.HttpsURLConnection

/**
 * The weather part: parsing against answers recorded from the live Open-Meteo service on 19.11.2026
 * (`src/test/resources/briefing/`), the wording, and the real tool runner with grant, ToolGate and research transport
 * – only DNS and HTTPS are deterministic fakes. How the live service behaves on the device is NOT_RUN.
 */
class ContinuumWeatherTest {
    private fun recorded(name: String): String =
        requireNotNull(javaClass.getResourceAsStream("/briefing/$name")) { name }.use { String(it.readBytes(), Charsets.UTF_8) }

    private val today = LocalDate.of(2026, 11, 19)
    private val linz = ContinuumWeatherPlace("Linz", "Linz, Oberösterreich, Österreich", 48.30639, 14.28611)

    // ── Parsing and choosing the place ──────────────────────────────────────────────────────────────────────

    @Test fun theRecordedGeocodingAnswerGivesTheOwnersPlaceNotTheNearestGuess() {
        val places = ContinuumWeather.parsePlaces(recorded("open-meteo-geocoding-linz.json"))
        assertEquals(listOf("Linz", "Liuzhou", "Linz", "Linz", "Linz am Rhein"), places.map { it.name })
        assertEquals(linz, ContinuumWeather.choose("Linz", places))
        assertEquals("the second entry of the service is another city and is never taken", "Linz", ContinuumWeather.choose("linz", places)!!.name)
        assertEquals("Linz am Rhein", ContinuumWeather.choose("Linz am Rhein", places)!!.name)
        assertEquals("Linz", ContinuumWeather.choose("Linz an der Donau", places)!!.name)
        assertNull(ContinuumWeather.choose("Wien", places))
        assertNull(ContinuumWeather.choose("Lin", places))
        assertEquals(emptyList<ContinuumWeatherPlace>(), ContinuumWeather.parsePlaces(recorded("open-meteo-geocoding-none.json")))
    }

    @Test fun theRecordedForecastIsReadAndSpokenNaturally() {
        val report = ContinuumWeather.parseForecast(recorded("open-meteo-forecast-linz.json"), linz)
        assertEquals(8.5, report.temperature!!, 0.001)
        assertEquals(1, report.code)
        assertEquals(listOf(ContinuumWeatherDay(today, 80, 11.6, 6.2, 78), ContinuumWeatherDay(today.plusDays(1), 80, 11.9, 8.0, 65)), report.days)
        assertEquals("In Linz sind es gerade 9 Grad, überwiegend klar. Heute 6 bis 12 Grad, leichte Regenschauer, " +
            "Regenwahrscheinlichkeit 78 Prozent.", ContinuumWeather.spoken(report, BriefingDay.TODAY, today))
        assertEquals("Morgen in Linz: 8 bis 12 Grad, leichte Regenschauer, Regenwahrscheinlichkeit 65 Prozent.",
            ContinuumWeather.spoken(report, BriefingDay.TOMORROW, today))
        assertEquals(listOf("Jetzt: 9 Grad, überwiegend klar", "Heute: 6 bis 12 Grad, leichte Regenschauer, Regenwahrscheinlichkeit 78 Prozent",
            "Morgen: 8 bis 12 Grad, leichte Regenschauer, Regenwahrscheinlichkeit 65 Prozent", "Quelle: Open-Meteo"),
            ContinuumWeather.lines(report, today))
        // A day the answer does not hold is not invented.
        assertNull(ContinuumWeather.spoken(report, BriefingDay.TOMORROW, today.plusDays(1)))
    }

    @Test fun anErrorBodyAndBrokenAnswersAreFailuresNotWeather() {
        val error = assertThrows(IllegalStateException::class.java) {
            ContinuumWeather.parseForecast(recorded("open-meteo-error.json"), linz)
        }
        assertTrue(error.message!!.contains("Latitude must be in range"))
        assertThrows(Exception::class.java) { ContinuumWeather.parseForecast("<html>busy</html>", linz) }
        assertThrows(IllegalStateException::class.java) { ContinuumWeather.parseForecast("{}", linz) }
        assertThrows(Exception::class.java) { ContinuumWeather.parsePlaces("not json") }
        assertEquals("die Antwort des Wetterdienstes war nicht lesbar",
            ContinuumWeather.failureClause(runCatching { ContinuumWeather.parseForecast("{}", linz) }.exceptionOrNull()!!))
        assertEquals("keine Verbindung zum Wetterdienst", ContinuumWeather.failureClause(java.net.UnknownHostException("api.open-meteo.com")))
        assertEquals("der Wetterdienst hat nicht rechtzeitig geantwortet", ContinuumWeather.failureClause(java.net.SocketTimeoutException()))
        assertEquals("der Wetterdienst hat mit einem Fehler geantwortet (HTTP 503)",
            ContinuumWeather.failureClause(IllegalArgumentException("Research request failed with HTTP 503.")))
    }

    @Test fun missingValuesStayUnsaidAndOddValuesAreDropped() {
        val partial = ContinuumWeather.parseForecast("""{"current":{"temperature_2m":-3.4,"weather_code":null},
            "daily":{"time":["2026-11-19","kaputt"],"weather_code":[777],"temperature_2m_max":[null],"temperature_2m_min":[-7.6],
            "precipitation_probability_max":[12]}}""", linz)
        assertNull(partial.code)
        assertEquals(1, partial.days.size)
        assertEquals(ContinuumWeatherDay(today, null, null, -7.6, 12), partial.days.single())
        assertEquals("In Linz sind es gerade minus 3 Grad. Heute ab minus 8 Grad.", ContinuumWeather.spoken(partial, BriefingDay.TODAY, today))
        val cold = ContinuumWeather.parseForecast("""{"daily":{"time":["2026-11-19"],"weather_code":[73],"temperature_2m_max":[0.4],
            "temperature_2m_min":[-5.5],"precipitation_probability_max":[30]}}""", linz)
        assertEquals("Heute minus 5 bis 0 Grad, Schneefall, Regenwahrscheinlichkeit 30 Prozent.", ContinuumWeather.spoken(cold, BriefingDay.TODAY, today))
        // A place without usable coordinates is no place.
        assertEquals(emptyList<ContinuumWeatherPlace>(), ContinuumWeather.parsePlaces(
            """{"results":[{"name":"Nirgends","latitude":948.0,"longitude":14.0},{"name":"","latitude":1.0,"longitude":1.0},{"latitude":1.0}]}"""))
        assertEquals("Graz, Steiermark, Österreich", ContinuumWeather.parsePlaces(
            """{"results":[{"name":"Graz","latitude":47.07,"longitude":15.44,"admin1":"Steiermark","country":"Österreich"}]}""").single().label)
        assertEquals("Wien, Österreich", ContinuumWeather.parsePlaces(
            """{"results":[{"name":"Wien","latitude":48.2,"longitude":16.37,"admin1":"Wien","country":"Österreich"}]}""").single().label)
    }

    @Test fun everyWeatherCodeOfTheServiceHasAPlainGermanWord() {
        listOf(0, 1, 2, 3, 45, 48, 51, 53, 55, 56, 57, 61, 63, 65, 66, 67, 71, 73, 75, 77, 80, 81, 82, 85, 86, 95, 96, 99).forEach {
            assertNotNull("code $it", ContinuumWeather.codeWord(it))
        }
        assertNull(ContinuumWeather.codeWord(42))
        assertNull(ContinuumWeather.codeWord(null))
        assertEquals("Gewitter mit Hagel", ContinuumWeather.codeWord(99))
    }

    @Test fun onlyThePlaceAndItsCoordinatesAreInTheRequests() {
        assertEquals("https://geocoding-api.open-meteo.com/v1/search?name=Bad+Ischl&count=5&language=de&format=json",
            ContinuumWeather.geocodingUrl(" Bad Ischl "))
        assertEquals("https://geocoding-api.open-meteo.com/v1/search?name=Sankt+P%C3%B6lten&count=5&language=de&format=json",
            ContinuumWeather.geocodingUrl("Sankt Pölten"))
        assertEquals("https://api.open-meteo.com/v1/forecast?latitude=48.3064&longitude=14.2861&current=temperature_2m,weather_code" +
            "&daily=weather_code,temperature_2m_max,temperature_2m_min,precipitation_probability_max&timezone=auto&forecast_days=2",
            ContinuumWeather.forecastUrl(linz))
        // No key, no account, no device or owner identifier in either URL.
        listOf(ContinuumWeather.geocodingUrl("Linz"), ContinuumWeather.forecastUrl(linz)).forEach { url ->
            listOf("key", "token", "user", "device", "id=").forEach { assertFalse("$url has $it", url.contains(it, ignoreCase = true)) }
        }
    }

    // ── The tool runner under the owner's web grant ─────────────────────────────────────────────────────────

    private class Response(url: String, val code: Int = 200, val body: String = "", val mime: String = "application/json",
                           val location: String? = null, val failure: Exception? = null) : HttpsURLConnection(URL(url)) {
        override fun connect() {}
        override fun disconnect() {}
        override fun usingProxy() = false
        override fun getResponseCode(): Int { failure?.let { throw it }; return code }
        override fun getContentType() = mime
        override fun getContentLengthLong() = -1L
        override fun getHeaderField(name: String) = if (name == "Location") location else null
        override fun getInputStream() = ByteArrayInputStream(body.toByteArray(Charsets.UTF_8))
        override fun getCipherSuite() = "test"
        override fun getLocalCertificates(): Array<Certificate>? = null
        override fun getServerCertificates(): Array<Certificate> = emptyArray()
    }

    private class Environment(val responses: Map<String, Response>) : ResearchHttpEnvironment {
        val opened: MutableList<String> = Collections.synchronizedList(mutableListOf())
        override fun resolve(host: String): List<InetAddress> = listOf(InetAddress.getByName("8.8.8.8"))
        override fun open(uri: URI, addresses: List<InetAddress>, timeoutMillis: Int): HttpsURLConnection {
            opened += uri.toString()
            return responses.getValue(uri.toString())
        }
    }

    private val geocoding = ContinuumWeather.geocodingUrl("Linz")
    private val forecast = ContinuumWeather.forecastUrl(linz)
    private fun serving(vararg override: Pair<String, Response>) = Environment(mapOf(
        geocoding to Response(geocoding, body = recorded("open-meteo-geocoding-linz.json")),
        forecast to Response(forecast, body = recorded("open-meteo-forecast-linz.json"))) + override)

    private fun granted(policy: InternetConsentPolicy = InternetConsentPolicy(InternetAccessMode.ALWAYS_ON)): ApprovedWebResearchSession =
        ApprovedWebToolGate(policy).authorizeAlwaysOn(WebToolRequest.Search(ContinuumWeather.GRANT_LABEL), ResearchMode.STANDARD)!!

    private fun run(session: ApprovedWebResearchSession, request: ContinuumWeatherRequest, environment: Environment):
        Pair<ToolExecutionResult, ApprovedWeatherToolRunner> {
        val runner = ApprovedWeatherToolRunner(session, request,
            HttpReadOnlyResearchProvider(environment, ApprovedWeatherToolRunner.policy, accessCheck = { session.checkActive() }))
        val gate = ToolGate(ToolPolicy(timeoutMillis = 30_000), runner)
        return gate.execute(session.call.proposal().invocation("weather-test"), userConfirmed = true) to runner
    }

    @Test fun thePlaceIsLookedUpOnceAndTheForecastReadUnderOneGrantThroughTheToolGate() {
        val environment = serving()
        val (execution, runner) = run(granted(), ContinuumWeatherRequest("Linz", null), environment)
        assertEquals(ToolExecutionStatus.SUCCEEDED, execution.record.status)
        val report = (runner.result as ContinuumWeatherRead.Report).report
        assertEquals(linz, report.place)
        assertEquals(78, report.days.first().rainChance)
        assertEquals(listOf(geocoding, forecast), environment.opened)
        // With the coordinates known, the place name is not sent again.
        val known = serving()
        val (_, second) = run(granted(), ContinuumWeatherRequest("Linz", linz), known)
        assertTrue(second.result is ContinuumWeatherRead.Report)
        assertEquals(listOf(forecast), known.opened)
    }

    @Test fun anUnknownPlaceAndAFailingServiceAreReportedAndNeverReplacedByWeather() {
        val nowhere = ContinuumWeather.geocodingUrl("Xqzzyqq")
        val environment = Environment(mapOf(nowhere to Response(nowhere, body = recorded("open-meteo-geocoding-none.json"))))
        val (execution, runner) = run(granted(), ContinuumWeatherRequest("Xqzzyqq", null), environment)
        assertEquals(ToolExecutionStatus.SUCCEEDED, execution.record.status)
        assertEquals(ContinuumWeatherRead.PlaceUnknown("Xqzzyqq"), runner.result)
        assertEquals("no forecast request without a place", listOf(nowhere), environment.opened)

        val down = serving(forecast to Response(forecast, code = 503))
        assertEquals(ContinuumWeatherRead.Failed("der Wetterdienst hat mit einem Fehler geantwortet (HTTP 503)"),
            run(granted(), ContinuumWeatherRequest("Linz", linz), down).second.result)
        val offline = serving(forecast to Response(forecast, failure = java.net.UnknownHostException("api.open-meteo.com")))
        assertEquals(ContinuumWeatherRead.Failed("keine Verbindung zum Wetterdienst"),
            run(granted(), ContinuumWeatherRequest("Linz", linz), offline).second.result)
        val garbled = serving(forecast to Response(forecast, body = "{\"daily\":{}}"))
        assertEquals(ContinuumWeatherRead.Failed("die Antwort des Wetterdienstes war nicht lesbar"),
            run(granted(), ContinuumWeatherRequest("Linz", linz), garbled).second.result)
        val wrongType = serving(forecast to Response(forecast, body = "x", mime = "application/octet-stream"))
        assertTrue(run(granted(), ContinuumWeatherRequest("Linz", linz), wrongType).second.result is ContinuumWeatherRead.Failed)
    }

    @Test fun theGrantReachesOnlyTheWeatherServiceAndFollowsNoRedirect() {
        assertEquals(setOf("api.open-meteo.com", "geocoding-api.open-meteo.com"), ApprovedWeatherToolRunner.policy.allowedHosts)
        assertEquals(0, ApprovedWeatherToolRunner.policy.maxRedirects)
        val elsewhere = "https://example.org/forecast"
        val redirected = serving(forecast to Response(forecast, code = 302, location = elsewhere),
            elsewhere to Response(elsewhere, body = recorded("open-meteo-forecast-linz.json")))
        val result = run(granted(), ContinuumWeatherRequest("Linz", linz), redirected)
        assertTrue(result.second.result is ContinuumWeatherRead.Failed)
        assertEquals("the redirect target is never opened", listOf(forecast), redirected.opened)
    }

    @Test fun oneGrantIsOneWeatherReadAndARevokedGrantGivesNoResult() {
        val session = granted()
        val environment = serving()
        assertEquals(ToolExecutionStatus.SUCCEEDED, run(session, ContinuumWeatherRequest("Linz", linz), environment).first.record.status)
        val again = run(session, ContinuumWeatherRequest("Linz", linz), environment)
        assertFalse(again.first.record.status == ToolExecutionStatus.SUCCEEDED)
        assertNull(again.second.result)
        assertEquals("the second run sent nothing", listOf(forecast), environment.opened)

        val revoked = granted()
        revoked.cancel()
        val none = serving()
        val failed = run(revoked, ContinuumWeatherRequest("Linz", linz), none)
        assertFalse(failed.first.record.status == ToolExecutionStatus.SUCCEEDED)
        assertNull(failed.second.result)
        assertTrue(none.opened.isEmpty())
        // OFF never mints a grant.
        assertNull(ApprovedWebToolGate(InternetConsentPolicy(InternetAccessMode.OFF))
            .authorizeAlwaysOn(WebToolRequest.Search(ContinuumWeather.GRANT_LABEL), ResearchMode.STANDARD))
    }
}
