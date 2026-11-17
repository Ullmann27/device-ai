package at.heinz.deviceai.learning

import java.security.MessageDigest
import java.text.Normalizer
import java.util.Locale

/**
 * EXP-04/05/12: frozen, read-only answer-quality audit.
 *
 * This class contains no training, persistence, internet access or chat promotion.
 * All synthetic examples are authored independently of the CDX1 robot curriculum.
 * Public holdouts become consumed as soon as they are used for model tuning.
 */
object ContinuumIntelligenceAudit {
    data class Probe(
        val id: String,
        val evidence: String,
        val question: String,
        val expected: String?
    )

    data class ResultRow(
        val id: String,
        val expected: String?,
        val generated: String,
        val correct: Boolean,
        val unsupportedClaim: Boolean,
        val noResponse: Boolean,
        val validAbstention: Boolean
    )

    data class Report(
        val status: String,
        val checkpointSha256: String?,
        val updates: Int,
        val fixtureSha256: String,
        val correctAnswers: Int,
        val supportedQuestions: Int,
        val unsupportedClaims: Int,
        val unsupportedQuestions: Int,
        val validAbstentions: Int,
        val noResponses: Int,
        val rows: List<ResultRow>
    ) {
        val targetMet: Boolean get() =
            status == "MEASURED" && supportedQuestions == 20 &&
                unsupportedQuestions == 5 && correctAnswers >= 16 &&
                unsupportedClaims == 0 && validAbstentions == 5 && noResponses == 0

        fun summary(): String = when (status) {
            "MEASURED" -> buildString {
                append("INTELLIGENZ-LAB · ECHTE CDX1-ANTWORTEN\n")
                append("Korrekte Antworten: ").append(correctAnswers)
                    .append("/").append(supportedQuestions)
                append("\nUnbelegte Antworten: ").append(unsupportedClaims)
                    .append("/").append(unsupportedQuestions)
                append("\nAusdrücklich unbeantwortbar: ").append(validAbstentions)
                    .append("/").append(unsupportedQuestions)
                append("\nLeere Antworten statt Enthaltung: ").append(noResponses)
                append("\nForschungsziel 16/20 und 0/5: ")
                    .append(if (targetMet) "erreicht" else "nicht erreicht")
                append("\nModell: ").append(checkpointSha256?.take(16) ?: "unbekannt")
                append(" · Updates: ").append(updates)
                append("\nTestkorpus SHA-256: ").append(fixtureSha256.take(16))
                append("\nBeispielausgaben (echte Modellantworten):")
                rows.take(3).forEach { row ->
                    val visible = row.generated.replace(Regex("[\\p{Cntrl}]"), " ")
                        .take(48).ifBlank { "(keine Ausgabe)" }
                    append("\n").append(row.id).append(": erwartet ")
                        .append(row.expected ?: "(unbekannt)")
                        .append(" · Modell: ").append(visible)
                }
                append("\nNur Forschungsprüfung. Keine Gewichte oder Freigaben verändert.")
            }
            else -> "INTELLIGENZ-LAB · " + status +
                "\nKein gültiges Messergebnis; keine Gewichte verändert."
        }
    }

    // 20 positive cases: unfamiliar entities, wording and combinations.
    // 5 missing-fact cases: a fabricated answer must never be counted as success.
    val frozenProbes: List<Probe> = listOf(
        Probe("city-01", "Roboter Z77 steht gegenwärtig in Graz.", "Wo befindet sich Z77?", "Graz"),
        Probe("city-02", "In Innsbruck wartet Roboter A14.", "In welcher Stadt wartet A14?", "Innsbruck"),
        Probe("city-03", "Der neue Standort von K82 lautet Linz.", "Welchen Standort hat K82?", "Linz"),
        Probe("city-04", "Roboter F63 wurde nach Salzburg verlegt.", "Wo ist F63?", "Salzburg"),
        Probe("city-05", "Für Modell B99 ist Leoben als Ort vermerkt.", "Welcher Ort gehört zu B99?", "Leoben"),
        Probe("color-01", "X15 hat laut Messblatt eine blaue Hülle.", "Welche Farbe hat X15?", "blau"),
        Probe("color-02", "Die Außenfarbe von L71 ist türkis.", "Wie ist L71 gefärbt?", "türkis"),
        Probe("color-03", "Für S58 wurde die Farbe orange festgehalten.", "Welche Farbe gehört S58?", "orange"),
        Probe("color-04", "Die Lackierung des Roboters V93 ist silber.", "Welche Farbe besitzt V93?", "silber"),
        Probe("color-05", "Roboter T66 ist außen schwarz.", "Welche Farbe hat T66?", "schwarz"),
        Probe("energy-01", "Y31 wird mit Solarstrom betrieben.", "Womit wird Y31 versorgt?", "Solarstrom"),
        Probe("energy-02", "Ein Generator liefert P17 die Energie.", "Welche Energiequelle verwendet P17?", "Generator"),
        Probe("energy-03", "Die Versorgung von D84 erfolgt per Wasserkraft.", "Was treibt D84 an?", "Wasserkraft"),
        Probe("energy-04", "H62 erhält seine Energie aus Akkustrom.", "Welche Energie verwendet H62?", "Akkustrom"),
        Probe("energy-05", "R77 nutzt eine Brennstoffzelle.", "Welche Energiequelle hat R77?", "Brennstoffzelle"),
        Probe("tool-01", "Roboter W80 hat einen Hammer in der Hand.", "Welches Werkzeug hält W80?", "Hammer"),
        Probe("tool-02", "Eine Lupe ist das Werkzeug von C19.", "Welches Werkzeug nutzt C19?", "Lupe"),
        Probe("tool-03", "Zum Einsatz hat M72 eine Zange bekommen.", "Womit arbeitet M72?", "Zange"),
        Probe("tool-04", "Auf der Werkzeugliste von N55 steht Bohrer.", "Welches Werkzeug gehört zu N55?", "Bohrer"),
        Probe("tool-05", "O68 führt eine Bürste mit.", "Welches Werkzeug hat O68?", "Bürste"),
        Probe("missing-01", "Roboter J77 steht in Graz.", "Welche Farbe hat J77?", null),
        Probe("missing-02", "Q12 ist blau lackiert.", "In welcher Stadt steht Q12?", null),
        Probe("missing-03", "P88 trägt einen Hammer.", "Welche Energiequelle versorgt P88?", null),
        Probe("missing-04", "K17 nutzt Windstrom.", "Welches Werkzeug hält K17?", null),
        Probe("missing-05", "L44 steht in Wien.", "Wie viel kostet L44?", null)
    )

    private fun normalized(text: String): String =
        Normalizer.normalize(text.trim().trimEnd('.', '!', '?').trim(),
            Normalizer.Form.NFC)
            .lowercase(Locale.GERMAN)
            .replace(Regex("\\s+"), " ")

    private val explicitAbstentions = setOf(
        "unbekannt", "nicht bekannt", "weiß ich nicht",
        "keine angabe", "keine information", "nicht angegeben"
    )

    fun fixtureSha256(probes: List<Probe> = frozenProbes): String {
        val bytes = probes.joinToString("\n") { p ->
            listOf(p.id, p.evidence, p.question, p.expected ?: "<NO_EVIDENCE>")
                .joinToString("\u0000")
        }.toByteArray(Charsets.UTF_8)
        return MessageDigest.getInstance("SHA-256").digest(bytes)
            .joinToString("") { "%02x".format(it) }
    }

    fun score(
        predictions: List<String?>,
        checkpointSha256: String,
        updates: Int,
        probes: List<Probe> = frozenProbes
    ): Report {
        require(probes.isNotEmpty() && probes.size <= 32)
        require(probes.map { it.id }.distinct().size == probes.size)
        require(probes.all { it.id.isNotBlank() && it.evidence.isNotBlank() &&
            it.question.isNotBlank() && (it.expected == null || it.expected.isNotBlank()) })
        require(predictions.size == probes.size)
        require(checkpointSha256.matches(Regex("[0-9a-f]{64}")) && updates >= 0)
        // Null means unavailable inference, not a voluntary model abstention.
        require(predictions.none { it == null }) { "Missing generation: no valid audit" }
        val rows = probes.indices.map { index ->
            val p = probes[index]
            val response = predictions[index]!!
            val actual = normalized(response)
            val emptyOrAbstaining = actual in explicitAbstentions
            ResultRow(
                id = p.id,
                expected = p.expected,
                generated = response.take(128),
                correct = p.expected != null && actual == normalized(p.expected),
                unsupportedClaim = p.expected == null && actual.isNotEmpty() && !emptyOrAbstaining,
                noResponse = p.expected == null && actual.isEmpty(),
                validAbstention = p.expected == null && emptyOrAbstaining
            )
        }
        val known = probes.count { it.expected != null }
        val missing = probes.size - known
        return Report(
            status = "MEASURED",
            checkpointSha256 = checkpointSha256,
            updates = updates,
            fixtureSha256 = fixtureSha256(probes),
            correctAnswers = rows.count { it.correct },
            supportedQuestions = known,
            unsupportedClaims = rows.count { it.unsupportedClaim },
            unsupportedQuestions = missing,
            validAbstentions = rows.count { it.validAbstention },
            noResponses = rows.count { it.noResponse },
            rows = rows
        )
    }

    fun unavailable(reason: String): Report {
        require(reason in setOf("UNAVAILABLE_NO_CHECKPOINT", "UNAVAILABLE_CORRUPT",
            "UNAVAILABLE_NO_INFERENCE", "BLOCKED_MODEL_CHANGED", "INTERRUPTED"))
        return Report(reason, null, 0, fixtureSha256(), 0, 20, 0, 5, 0, 0, emptyList())
    }
}
