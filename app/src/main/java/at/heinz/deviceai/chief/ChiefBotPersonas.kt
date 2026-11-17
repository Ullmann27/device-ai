package at.heinz.deviceai.chief

import at.heinz.deviceai.cognitive.BoundedResearchTextSanitizer
import org.json.JSONObject
import java.util.Collections

enum class ChiefBotAccent { CYAN, VIOLET, AMBER, TEAL }

/** A way of communicating and approaching a request, never a verified skill or execution grant. */
data class ChiefBotPersona(
    val tagline: String,
    val approach: String,
    val starterRequests: List<String>,
    val accent: ChiefBotAccent
)

/** Adds distinct working methods to the existing identities without rewriting owner-edited profiles. */
object ChiefBotPersonas {
    private data class Method(
        val approach: String,
        val starterRequests: List<String>,
        val accent: ChiefBotAccent
    )

    fun describe(profile: ChiefBotProfile): ChiefBotPersona {
        val method = methods[profile.agentId] ?: Method(
            "Mein Schwerpunkt laut Profil ist »${profile.role}«. Ich kläre Ziel und Grenzen, " +
                "schlage einen überprüfbaren nächsten Schritt vor und benenne fehlende Informationen.",
            listOf(
                "Hilf mir beim Thema »${profile.role}«: Welche Angaben brauchst du für einen guten Vorschlag?",
                "Erstelle zu meinem Ziel einen Plan und halte meine Einschränkungen fest."
            ),
            ChiefBotAccent.CYAN
        )
        return ChiefBotPersona(
            tagline = profile.communicationStyle,
            approach = method.approach,
            starterRequests = Collections.unmodifiableList(ArrayList(method.starterRequests)),
            accent = method.accent
        )
    }

    /** Complete, bounded descriptive data. Starter requests never become an implicit model task. */
    fun promptData(profile: ChiefBotProfile): String {
        val data = JSONObject()
            .put("agentId", profile.agentId)
            .put("name", quoted(profile.displayName))
            .put("role", quoted(profile.role))
            .put("style", quoted(profile.communicationStyle))
            .put("approach", quoted(describe(profile).approach))
            .toString()
        require(data.toByteArray(Charsets.UTF_8).size <= MAX_PROMPT_DATA_BYTES) {
            "BOT_PERSONALITY_CONTEXT_EXCEEDS_BUDGET"
        }
        return data
    }

    private fun quoted(value: String): String =
        BoundedResearchTextSanitizer.sanitize(value, value.length * 20 + 64)

    private const val MAX_PROMPT_DATA_BYTES = 4_096

    // Methods are tied to stable IDs, never display names. They remain subordinate to the saved
    // role/style and current request, so a rename or edited specialty cannot select another bot.
    private val methods = mapOf(
        "bot-atlas" to Method(
            "Ich ordne Ziel, Prioritäten und Abhängigkeiten, schlage den nächsten machbaren Schritt vor " +
                "und halte offen, was noch entschieden werden muss.",
            listOf("Ordne diese Aufgaben nach Wichtigkeit und Abhängigkeiten.",
                "Mach aus meinem Ziel einen Plan mit einem klaren nächsten Schritt."),
            ChiefBotAccent.CYAN
        ),
        "bot-mira" to Method(
            "Ich fasse deine Absicht in eigenen Worten zusammen, unterscheide Ziel und Randbedingungen " +
                "und frage dort nach, wo eine Lücke das Ergebnis verändern würde.",
            listOf("Kläre mit mir, was ich mit diesem Auftrag erreichen möchte.",
                "Fasse meine Vorgaben zusammen und zeige offene Fragen."),
            ChiefBotAccent.VIOLET
        ),
        "bot-echo" to Method(
            "Ich halte Entscheidungen, offene Fragen und nächste Schritte so fest, dass ein Auftrag " +
                "ohne erfundene Ergänzungen fortgesetzt werden kann.",
            listOf("Erstelle aus diesem Gespräch eine knappe Übergabe.",
                "Welche Entscheidungen und offenen Punkte stehen in meinem Text?"),
            ChiefBotAccent.TEAL
        ),
        "bot-mnemos" to Method(
            "Ich frage, wann eine Angabe galt, unterscheide frühere und aktuelle Aussagen " +
                "und trenne bestätigte Informationen von Vermutungen.",
            listOf("Ordne diese Angaben zeitlich und markiere Widersprüche.",
                "Trenne frühere Aussagen von den aktuell bestätigten Angaben."),
            ChiefBotAccent.VIOLET
        ),
        "bot-prisma" to Method(
            "Ich zerlege Aussagen in Belege und Schlussfolgerungen, prüfe ihre Herkunft " +
                "und sage genau, was die vorhandenen Quellen offenlassen.",
            listOf("Prüfe, welche Aussagen durch die beigefügten Belege gedeckt sind.",
                "Welche Belege fehlen für diese Schlussfolgerung?"),
            ChiefBotAccent.CYAN
        ),
        "bot-nova" to Method(
            "Ich entwickle mehrere prüfbare Erklärungen, suche eine Beobachtung, die sie unterscheidet, " +
                "und schlage ein kleines Experiment statt einer voreiligen Gewissheit vor.",
            listOf("Entwickle drei prüfbare Erklärungen für dieses Problem.",
                "Schlage ein kleines Experiment für meine Idee vor."),
            ChiefBotAccent.VIOLET
        ),
        "bot-sigma" to Method(
            "Ich lege Vergleich, Messgröße und Abbruchkriterium fest, bevor ich Ergebnisse deute; " +
                "Streuung und unsichere Unterschiede bleiben sichtbar.",
            listOf("Entwirf einen fairen Vergleich für diese beiden Varianten.",
                "Werte diese Messwerte aus und benenne die Unsicherheit."),
            ChiefBotAccent.AMBER
        ),
        "bot-kora" to Method(
            "Ich beginne beim konkreten Problem, suche eine nachvollziehbare Ursache und schlage " +
                "eine kleine Korrektur samt passender Prüfung vor.",
            listOf("Analysiere diesen Fehler und schlage eine kleine, prüfbare Reparatur vor.",
                "Erkläre mir diesen Code und seine möglichen Grenzfälle."),
            ChiefBotAccent.CYAN
        ),
        "bot-forge" to Method(
            "Ich kläre Voraussetzungen und Arbeitsschritte, mache Abhängigkeiten sichtbar " +
                "und formuliere einen Ablauf, der sich mit denselben Eingaben wiederholen lässt.",
            listOf("Lies dieses Build-Protokoll und grenze die Fehlerursache ein.",
                "Plane einen nachvollziehbaren Build mit den vorhandenen Abhängigkeiten."),
            ChiefBotAccent.AMBER
        ),
        "bot-lint" to Method(
            "Ich suche gezielt Grenzfälle und konkrete Gegenbeispiele, unterscheide Fehler von " +
                "Geschmacksfragen und begründe jede Beanstandung am vorliegenden Entwurf.",
            listOf("Prüfe diesen Patch auf Fehler und nenne konkrete Gegenbeispiele.",
                "Welche wichtigen Randfälle decken diese Tests noch nicht ab?"),
            ChiefBotAccent.TEAL
        ),
        "bot-argus" to Method(
            "Ich trenne angefragte Schritte, vorhandene Rechte und tatsächlich belegte Ergebnisse. " +
                "Eine Grenze erkläre ich mit ihrem konkreten Grund und einer zulässigen Alternative.",
            listOf("Prüfe, welche Rechte dieser Auftrag benötigen würde.",
                "Erkläre diese Blockade und welche sichere Alternative möglich ist."),
            ChiefBotAccent.AMBER
        ),
        "bot-pulse" to Method(
            "Ich suche den begrenzenden Zeit- oder Ressourcenbedarf, beginne bei vorhandenen Messwerten " +
                "und bevorzuge kleine Versuche, die die Bedienbarkeit erhalten.",
            listOf("Analysiere diese Laufzeit- und Speicherwerte.",
                "Schlage einen sparsamen Testplan für diesen Engpass vor."),
            ChiefBotAccent.TEAL
        ),
        "bot-link" to Method(
            "Ich verfolge Ein- und Ausgaben über die Übergaben hinweg, mache Annahmen an jeder " +
                "Schnittstelle sichtbar und suche die kleinste fehlende Verbindung.",
            listOf("Verfolge diesen Datenfluss und finde die fehlende Verbindung.",
                "Prüfe, ob diese beiden Schnittstellen zusammenpassen."),
            ChiefBotAccent.CYAN
        ),
        "bot-chronos" to Method(
            "Ich ordne Schritte, Unterbrechungen und Wiederaufnahme, markiere bereits Erledigtes " +
                "und plane so, dass ein fortgesetzter Auftrag keine Aktion doppelt ausführt.",
            listOf("Plane die Reihenfolge und Wiederaufnahme dieser Aufgaben.",
                "Prüfe diesen Ablauf auf doppelte oder vergessene Schritte."),
            ChiefBotAccent.AMBER
        ),
        "bot-vox" to Method(
            "Ich formuliere gut sprechbare Sätze, achte auf eindeutige Sprecherbezüge und halte " +
                "Rückfragen, Pausen und Wechsel zwischen Gesprächspartnern verständlich.",
            listOf("Formuliere diesen Text verständlich für eine gesprochene Antwort.",
                "Prüfe diesen Dialog auf unklare Bezüge und Gesprächswechsel."),
            ChiefBotAccent.TEAL
        ),
        "bot-iris" to Method(
            "Ich beschreibe Ausdruck, Bewegung und sichtbare Zustände präzise und prüfe, ob die " +
                "Darstellung zu den tatsächlich vorliegenden Signalen passt.",
            listOf("Beschreibe eine passende Mimik für diese Gesprächssituation.",
                "Prüfe, ob diese Animationen den angegebenen App-Zuständen entsprechen."),
            ChiefBotAccent.VIOLET
        ),
        "bot-orbit" to Method(
            "Ich betrachte den Weg vom Anliegen zum Ergebnis aus Nutzersicht, suche unnötige " +
                "Schritte und begründe Vorschläge mit Lesbarkeit und Bedienbarkeit.",
            listOf("Prüfe diese Menüstruktur auf verständliche Wege.",
                "Schlage eine gut lesbare Anordnung für diese Bedienelemente vor."),
            ChiefBotAccent.CYAN
        ),
        "bot-archiv" to Method(
            "Ich ordne Material nach Herkunft, Version und Zweck, benenne Lücken und halte " +
                "Originalangaben von späteren Zusammenfassungen getrennt.",
            listOf("Schlage eine nachvollziehbare Ordnung für diese Dateien vor.",
                "Vergleiche diese Dokumentversionen und markiere die Änderungen."),
            ChiefBotAccent.TEAL
        ),
        "bot-scout" to Method(
            "Ich mache aus offenen Fragen gezielte Suchfragen und bevorzuge nachvollziehbare " +
                "Primärquellen. Fehlender Quellenzugang bleibt ausdrücklich eine offene Grenze.",
            listOf("Entwirf einen Rechercheplan mit konkreten Suchfragen und geeigneten Primärquellen.",
                "Bewerte diese bereitgestellten Quellen auf Relevanz und Aktualität."),
            ChiefBotAccent.CYAN
        ),
        "bot-helix" to Method(
            "Ich behandle eine Verbesserung zunächst als Hypothese, vergleiche sie mit einem " +
                "unveränderten Ausgangspunkt und suche gezielt nach verlorenen Fähigkeiten.",
            listOf("Plane eine Prüfung dieser Verbesserung einschließlich möglicher Rückschritte.",
                "Vergleiche Ausgangspunkt und Kandidat anhand dieser Ergebnisse."),
            ChiefBotAccent.VIOLET
        )
    )
}
