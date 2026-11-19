package at.heinz.deviceai.briefing

import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.time.LocalDate
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

/** A place the forecast service knows. [label]: name with region for the card („Linz, Oberösterreich“). */
internal data class ContinuumWeatherPlace(val name: String, val label: String, val latitude: Double, val longitude: Double)

/** One forecast day; a value the service did not send is null and is then not spoken. */
internal data class ContinuumWeatherDay(val date: LocalDate, val code: Int?, val max: Double?, val min: Double?, val rainChance: Int?)

internal data class ContinuumWeatherReport(
    val place: ContinuumWeatherPlace,
    /** Current temperature in °C and WMO weather code, as the service reports them. */
    val temperature: Double?,
    val code: Int?,
    val days: List<ContinuumWeatherDay>
)

/** What a weather read for the owner's place ended with. */
internal sealed interface ContinuumWeatherRead {
    data class Report(val report: ContinuumWeatherReport) : ContinuumWeatherRead
    /** The service knows no place of that name. */
    data class PlaceUnknown(val asked: String) : ContinuumWeatherRead
    /** Not read; [reason] is an owner-facing clause („keine Verbindung“). */
    data class Failed(val reason: String) : ContinuumWeatherRead
}

/** What the briefing asks the weather tool for: the place as the owner said it and, when known, its coordinates. */
internal data class ContinuumWeatherRequest(val placeName: String, val known: ContinuumWeatherPlace?)

/**
 * The Open-Meteo forecast and geocoding APIs (free for non-commercial use, no key; shape checked against the live
 * service on 19.11.2026, recorded in `app/src/test/resources/briefing/`). Only URL building, defensive parsing and
 * wording live here – the request itself goes through the app's web grant (see ApprovedWeatherToolRunner). What is
 * transmitted: the place name (geocoding) and its coordinates (forecast). Nothing else of the owner's.
 */
internal object ContinuumWeather {
    const val FORECAST_HOST = "api.open-meteo.com"
    const val GEOCODING_HOST = "geocoding-api.open-meteo.com"
    val hosts: Set<String> = setOf(FORECAST_HOST, GEOCODING_HOST)
    const val SOURCE = "Open-Meteo"

    /** Fixed public label of the web grant and the consent dialog. */
    const val GRANT_LABEL = "Wetter für deinen Ort (Open-Meteo)"

    fun geocodingUrl(place: String): String = "https://$GEOCODING_HOST/v1/search?name=" +
        URLEncoder.encode(place.trim(), "UTF-8") + "&count=5&language=de&format=json"

    fun forecastUrl(place: ContinuumWeatherPlace): String = "https://$FORECAST_HOST/v1/forecast?latitude=" +
        coordinate(place.latitude) + "&longitude=" + coordinate(place.longitude) +
        "&current=temperature_2m,weather_code&daily=weather_code,temperature_2m_max,temperature_2m_min," +
        "precipitation_probability_max&timezone=auto&forecast_days=2"

    private fun coordinate(value: Double): String = String.format(Locale.ROOT, "%.4f", value)

    private fun fold(text: String): String = text.lowercase(Locale.GERMAN).replace("ä", "ae").replace("ö", "oe")
        .replace("ü", "ue").replace("ß", "ss").replace(Regex("[^a-z0-9]+"), " ").trim()

    /** The geocoding answer as places, in the service's order. A body without `results` is an empty list. */
    fun parsePlaces(json: String): List<ContinuumWeatherPlace> {
        val root = JSONObject(json)
        if (root.optBoolean("error", false)) throw IllegalStateException(root.optString("reason", "Fehler des Wetterdienstes"))
        val results = root.optJSONArray("results") ?: return emptyList()
        return (0 until results.length()).mapNotNull { index ->
            val row = results.optJSONObject(index) ?: return@mapNotNull null
            val name = row.optString("name").trim().take(60)
            val latitude = row.optDouble("latitude", Double.NaN)
            val longitude = row.optDouble("longitude", Double.NaN)
            if (name.isEmpty() || latitude.isNaN() || longitude.isNaN() || abs(latitude) > 90 || abs(longitude) > 180)
                return@mapNotNull null
            val region = listOf(row.optString("admin1"), row.optString("country")).map { it.trim().take(40) }
                .filter { it.isNotEmpty() && fold(it) != fold(name) }.distinct()
            ContinuumWeatherPlace(name, (listOf(name) + region).joinToString(", "), latitude, longitude)
        }
    }

    /**
     * The place the owner meant: the first candidate of the same name; else the first whose name contains what he
     * said or is contained in it. No such candidate = unknown – never „the nearest guess“ of the service.
     */
    fun choose(asked: String, candidates: List<ContinuumWeatherPlace>): ContinuumWeatherPlace? {
        val wanted = fold(asked)
        if (wanted.isEmpty()) return null
        return candidates.firstOrNull { fold(it.name) == wanted }
            ?: candidates.firstOrNull { fold(it.name).let { name -> name.startsWith("$wanted ") || wanted.startsWith("$name ") } }
    }

    private fun number(array: JSONArray?, index: Int): Double? =
        array?.takeIf { index < it.length() && !it.isNull(index) }?.optDouble(index, Double.NaN)?.takeIf { !it.isNaN() }

    /** The forecast answer. Missing blocks or values stay null; an error body throws with the service's reason. */
    fun parseForecast(json: String, place: ContinuumWeatherPlace): ContinuumWeatherReport {
        val root = JSONObject(json)
        if (root.optBoolean("error", false)) throw IllegalStateException(root.optString("reason", "Fehler des Wetterdienstes"))
        val current = root.optJSONObject("current")
        val temperature = current?.takeIf { !it.isNull("temperature_2m") }?.optDouble("temperature_2m", Double.NaN)
            ?.takeIf { !it.isNaN() && it in -90.0..70.0 }
        val code = current?.takeIf { !it.isNull("weather_code") }?.optInt("weather_code", -1)?.takeIf { it in 0..99 }
        val daily = root.optJSONObject("daily")
        val dates = daily?.optJSONArray("time")
        val days = (0 until (dates?.length() ?: 0)).mapNotNull { index ->
            val date = runCatching { LocalDate.parse(dates!!.optString(index)) }.getOrNull() ?: return@mapNotNull null
            ContinuumWeatherDay(date,
                number(daily?.optJSONArray("weather_code"), index)?.roundToInt()?.takeIf { it in 0..99 },
                number(daily?.optJSONArray("temperature_2m_max"), index)?.takeIf { it in -90.0..70.0 },
                number(daily?.optJSONArray("temperature_2m_min"), index)?.takeIf { it in -90.0..70.0 },
                number(daily?.optJSONArray("precipitation_probability_max"), index)?.roundToInt()?.takeIf { it in 0..100 })
        }
        check(temperature != null || days.isNotEmpty()) { "Antwort ohne Wetterwerte" }
        return ContinuumWeatherReport(place, temperature, code, days)
    }

    /** The WMO weather interpretation code in plain German; null for a code the table does not know. */
    fun codeWord(code: Int?): String? = when (code) {
        0 -> "klarer Himmel"
        1 -> "überwiegend klar"
        2 -> "teils bewölkt"
        3 -> "bedeckt"
        45, 48 -> "Nebel"
        51, 53, 55 -> "Nieselregen"
        56, 57 -> "gefrierender Nieselregen"
        61 -> "leichter Regen"
        63 -> "Regen"
        65 -> "starker Regen"
        66, 67 -> "gefrierender Regen"
        71 -> "leichter Schneefall"
        73 -> "Schneefall"
        75 -> "starker Schneefall"
        77 -> "Schneegriesel"
        80 -> "leichte Regenschauer"
        81 -> "Regenschauer"
        82 -> "heftige Regenschauer"
        85, 86 -> "Schneeschauer"
        95 -> "Gewitter"
        96, 99 -> "Gewitter mit Hagel"
        else -> null
    }

    /** „9 Grad“, „minus 3 Grad“, „1 Grad“. */
    fun degrees(value: Double): String = value.roundToInt().let { if (it < 0) "minus ${-it} Grad" else "$it Grad" }

    private fun dayClause(day: ContinuumWeatherDay): String? {
        val parts = buildList {
            when {
                day.min != null && day.max != null && day.min.roundToInt() != day.max.roundToInt() ->
                    add(degrees(day.min).removeSuffix(" Grad") + " bis " + degrees(day.max))
                day.max != null -> add("bis " + degrees(day.max))
                day.min != null -> add("ab " + degrees(day.min))
            }
            codeWord(day.code)?.let(::add)
            day.rainChance?.takeIf { it >= RAIN_MENTION_PERCENT }?.let { add("Regenwahrscheinlichkeit $it Prozent") }
        }
        return parts.takeIf { it.isNotEmpty() }?.joinToString(", ")
    }

    /** The weather of [day] at the report's place as spoken text; null when the report holds nothing for that day. */
    fun spoken(report: ContinuumWeatherReport, day: BriefingDay, today: LocalDate): String? {
        val place = report.place.name
        if (day == BriefingDay.TOMORROW) {
            val clause = report.days.firstOrNull { it.date == today.plusDays(1) }?.let(::dayClause) ?: return null
            return "Morgen in $place: $clause."
        }
        val now = report.temperature?.let { temperature ->
            "In $place sind es gerade " + degrees(temperature) + (codeWord(report.code)?.let { ", $it" } ?: "") + "."
        }
        val rest = report.days.firstOrNull { it.date == today }?.let(::dayClause)?.let { "Heute $it." }
        return listOfNotNull(now, rest).takeIf { it.isNotEmpty() }?.joinToString(" ")
            ?: report.days.firstOrNull()?.let(::dayClause)?.let { "In $place: $it." }
    }

    /** Card lines for [report]: now, today, tomorrow and the source. */
    fun lines(report: ContinuumWeatherReport, today: LocalDate): List<String> = buildList {
        report.temperature?.let { add("Jetzt: " + degrees(it) + (codeWord(report.code)?.let { word -> ", $word" } ?: "")) }
        report.days.firstOrNull { it.date == today }?.let(::dayClause)?.let { add("Heute: $it") }
        report.days.firstOrNull { it.date == today.plusDays(1) }?.let(::dayClause)?.let { add("Morgen: $it") }
        add("Quelle: $SOURCE")
    }.take(4)

    /** Why there is no weather, in the owner's language. */
    fun failureClause(error: Throwable): String {
        val message = error.message.orEmpty()
        return when {
            error is java.net.UnknownHostException || message.contains("could not be resolved") -> "keine Verbindung zum Wetterdienst"
            error is java.net.SocketTimeoutException || error is java.util.concurrent.TimeoutException ||
                message.contains("time limit") || message.contains("timeout", ignoreCase = true) -> "der Wetterdienst hat nicht rechtzeitig geantwortet"
            message.contains("HTTP ") -> "der Wetterdienst hat mit einem Fehler geantwortet (HTTP " + message.substringAfter("HTTP ").take(3) + ")"
            message.contains("widerrufen") -> "die Internetfreigabe wurde widerrufen"
            error is org.json.JSONException || message.contains("Wetterwerte") -> "die Antwort des Wetterdienstes war nicht lesbar"
            error is java.io.IOException -> "keine Verbindung zum Wetterdienst"
            else -> "der Wetterdienst war nicht erreichbar"
        }
    }

    const val RAIN_MENTION_PERCENT = 30
}
