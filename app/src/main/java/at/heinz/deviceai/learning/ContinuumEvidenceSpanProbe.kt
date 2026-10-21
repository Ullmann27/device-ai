package at.heinz.deviceai.learning

import java.util.Locale

/**
 * CSX1 research-only extractive baseline. Returns a byte-for-byte source
 * substring, never generates a fact or invokes a model or a device action.
 *
 * This is deliberately a conservative template baseline, NOT a learned
 * neural span pointer and NOT a general-purpose question answerer.
 */
object ContinuumEvidenceSpanProbe {
    enum class Relation { LOCATION, COLOR, ENERGY, TOOL }

    data class Span(
        val text:String,
        val start:Int,
        val endExclusive:Int,
        val relation:Relation
    )

    sealed class Result {
        data class Found(val span:Span):Result()
        data class Abstained(val reason:String):Result()
    }

    fun answer(evidence:String, question:String):Result {
        if(evidence.isBlank() || question.isBlank() ||
            evidence.toByteArray(Charsets.UTF_8).size>160 ||
            question.toByteArray(Charsets.UTF_8).size>160 ||
            evidence.any { it.code<32 || it.code==127 } ||
            question.any { it.code<32 || it.code==127 }) {
            return Result.Abstained("Eingabe leer, zu lang oder enthält Steuerzeichen")
        }
        val names=Regex("""\b[A-Z][0-9]{1,3}\b""",RegexOption.IGNORE_CASE)
            .findAll(question).map { it.value.uppercase(Locale.ROOT) }.toSet()
        if(names.size!=1)return Result.Abstained("Frage enthält nicht genau eine eindeutige Roboterkennung")
        val id=names.single()
        if(Regex("""\b${Regex.escape(id)}\b""",RegexOption.IGNORE_CASE)
                .findAll(evidence).count()!=1) {
            return Result.Abstained("Roboterkennung im Beleg fehlt oder ist mehrdeutig")
        }

        val lower=question.lowercase(Locale.ROOT)
        val relation=when {
            "farbe" in lower || "gefärbt" in lower -> Relation.COLOR
            "energie" in lower || "versorgung" in lower -> Relation.ENERGY
            "werkzeug" in lower || "hält" in lower || "bereit" in lower -> Relation.TOOL
            Regex("""\bwo\b|\bstadt\b|\bort\b""").containsMatchIn(lower) -> Relation.LOCATION
            else -> return Result.Abstained("Unbekannter Fragetyp")
        }
        val prefix="""(?:Der\s+)?Roboter\s+${Regex.escape(id)}\s+"""
        // Anchored patterns prevent taking a value from another entity or
        // from a later unrelated sentence.
        val value="""([\p{L}][\p{L}\p{M} -]{0,54})"""
        val suffix="""[.!?]?\s*$"""
        val patterns=when(relation) {
            Relation.LOCATION -> listOf(
                """^\s*$prefix(?:steht|wohnt|lebt|befindet\s+sich)\s+in\s+$value$suffix""",
                """^\s*In\s+$value\s+steht\s+(?:der\s+)?Roboter\s+${Regex.escape(id)}[.!?]?\s*$"""
            )
            Relation.COLOR -> listOf(
                """^\s*$prefix(?:hat\s+die\s+Farbe|ist)\s+$value$suffix"""
            )
            Relation.ENERGY -> listOf(
                """^\s*$prefix(?:nutzt|verwendet)\s+$value\s+als\s+Energiequelle$suffix"""
            )
            Relation.TOOL -> listOf(
                """^\s*$prefix(?:hält|benutzt|trägt)\s+(?:einen|eine|ein)\s+$value$suffix"""
            )
        }
        val matches=patterns.mapNotNull {
            Regex(it,RegexOption.IGNORE_CASE).matchEntire(evidence)?.groups?.get(1)
        }
        if(matches.size!=1)return Result.Abstained("Keine eindeutige belegte Antwortspanne gefunden")
        val matched=matches.single()
        val text=matched.value.trimEnd()
        if(text.isBlank() || text.split(Regex("""\s+""")).size>4)
            return Result.Abstained("Antwortspanne nicht eindeutig begrenzt")
        val start=matched.range.first
        val end=start+text.length
        if(end>evidence.length || evidence.substring(start,end)!=text)
            return Result.Abstained("Belegposition ungültig")
        return Result.Found(Span(text,start,end,relation))
    }
}
