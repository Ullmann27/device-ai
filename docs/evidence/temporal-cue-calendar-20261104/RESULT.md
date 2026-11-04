# TemporalCue: Kalendergrenzen und sichere relative Tageszahlen

Datum: 04.11.2026. Ausgangs-HEAD: `e101e393dd6c80803ac9d9ac91fccd9812c4a18d`.
Isolierter Arbeitszweig: `repair/three-memory-local`.
Quellcommit mit Tests und Hostrunner: `b62101a13ff7fc6c5cd038fb8a412250af19852c`.
Source-identisch veröffentlichte Integration:
[`edd41aeb25c4a0a39247344c27e9f395ebe8a5d2`](https://github.com/Ullmann27/device-ai/commit/edd41aeb25c4a0a39247344c27e9f395ebe8a5d2)
in [Draft-PR #505](https://github.com/Ullmann27/device-ai/pull/505).
Gesamtbaum dieser Integration: `fbcc38ed2bb570f929be4f9f4f94affafe8b424b`.
Der lokale `b62101a` ist nicht der veröffentlichte Commit.

## Ergebnis und Reichweite

Der vorhandene M2.9-Suchbaustein `learning/ContinuumMemoryRetriever.kt` verwendete für
„letzte Woche“ stets 13 bis 6 Tage Abstand und für „letzten Monat“ stets 45 bis 20 Tage.
Das trifft Kalenderwochen und Kalendermonate nicht zuverlässig. Am 04.11.2026 wurde
eine Erinnerung vom 31.10.2026 bei „letzten Monat“ fälschlich abgewertet. Ein Regressionstest
mit zwei ansonsten gleichen Erinnerungen reproduziert dadurch eine falsche Rangfolge.

Zusätzlich wurde eine nicht als `Long` darstellbare Ziffernfolge über den Zahlwortfallback
als ein Tag interpretiert. Eine noch als `Long` darstellbare, aber zu große Tageszahl
warf `DateTimeException`; ein gültiges Kalenderdatum außerhalb des Epoch-Millis-Bereichs
warf `ArithmeticException`. Beide Ausnahmen wurden vor dem Patch tatsächlich beobachtet.

Die Reparatur bleibt auf `TemporalCue` und dessen Import begrenzt:

- Letzte/vorige Woche: vorheriger Montag bis aktueller Montag, jeweils lokaler Tagesbeginn,
  Ende exklusiv.
- Letzter/voriger Monat: erster Tag des vorherigen Monats bis erster Tag des aktuellen Monats,
  Ende exklusiv, jeweils lokaler Tagesbeginn in der konfigurierten Zeitzone. Jeder Endpunkt
  wird unabhängig mit `LocalDate.atStartOfDay(zone)` bestimmt, damit eine heutige
  Mitternachtslücke nicht die Uhrzeit anderer Kalendergrenzen verschiebt.
- „Vor einem Monat“ behält das bestehende ungefähre Fenster. Es wird nicht mit einem
  benannten Kalendermonat gleichgesetzt.
- Nicht darstellbare relative Tageszahlen liefern kein Zeitfenster (`null`). Abgefangen
  werden ausschließlich `DateTimeException` und `ArithmeticException`.
- Wortzahlen zwei bis fünf und vorhandene Tagesausdrücke bleiben erhalten; Speicherformat,
  bestätigter Status, Modell, ToolGate und Internetmodus werden nicht geändert.

**Keine neue Hauptchat-Anbindung:** Die Callsite-Suche im geprüften `app/src/main` findet
die Factory, den Event-Dekorator und den Berechnungs-Dekorator, aber keinen Aufruf dieses
M2.9-Retrievers aus `AgentActivity` oder `ContinuumLearningCore`. Dieser Befund ist eine
Reparatur des vorhandenen Suchbausteins, kein Nachweis einer verbesserten Antwort im
laufenden Hauptchat. Der Patch aktiviert keine neue Integration.

## Abgleich mit parallelen PRs

Die relevanten `TemporalCue`-Zeilen wurden am 04.11.2026 direkt auf GitHub gelesen:

- [PR #494](https://github.com/Ullmann27/device-ai/pull/494),
  `93f1f1130a3963062ad4c43c4a1edfe0cf7e2759`.
- [PR #499](https://github.com/Ullmann27/device-ai/pull/499),
  `999225dc3823cd027c06adf2847e7fec9f772cf4`.

Beide enthalten dieselben ungefähren Kalenderfenster und denselben numerischen Fallback.
Der vorliegende Patch wiederholt keine dortigen Memory-Integritäts- oder Activity-Fixes.

## Tatsächliche Host-Prüfung

| Lauf | Originalquelle | Tests | Ergebnis |
| --- | --- | ---: | --- |
| Initiales RED | unveränderte Produktionsdatei aus Ausgangs-HEAD | 15 | 12 Fehler, 3 bestanden |
| Historisches erstes GREEN | erster TemporalCue-Patch, vor unabhängiger Zeitzonenprüfung | 15 | 15 bestanden, 0 Fehler |
| RED nach unabhängigem Review | erster Patch plus 2 neue Santiago-Grenzfälle | 17 | 2 Fehler, 15 bestanden |
| Finales GREEN | endgültiger TemporalCue-Patch | 17 | 17 bestanden, 0 Fehler |
| Unabhängiger finaler Hostlauf | dieselben endgültigen Quell-/Testdateien | 17 | 17 bestanden, 0 Fehler |

15 neue JUnit-Testmethoden prüfen alle Wochentage, Jahreswechsel, Monatsanfang und -ende,
Schaltjahr/gewöhnlichen Februar, beide Wiener Sommerzeitwechsel, konfigurierte Zeitzone,
inklusive/exklusive Millisekundengrenzen, tatsächliche Rangfolge und erhaltenen Trust-Status,
unveränderte Tages-/ungefähre Monatsausdrücke, die drei Zahlen-/Datumsüberläufe und
Mitternachtslücken in `America/Santiago`.
Die 2 unveränderten Methoden aus `ContinuumRetrievalExperimentTest` laufen im selben Prozess.

Der unabhängige Review fand am ersten Patch einen weiteren reproduzierbaren Grenzfall:
Am 06.09.2026 springt der Tagesbeginn in Santiago auf 01:00. Eine Übernahme dieser Uhrzeit
in die anderen Kalenderdaten verschob die Wochen-/Monatsgrenzen um eine Stunde. Die
zwei daraufhin hinzugefügten Tests schlugen gegen den ersten Patch fehl und bestehen
mit unabhängig zonierten Kalenderdaten. Der alte Tages-/ungefähre Monatsalgorithmus
wurde dafür nicht verändert.

Rohbelege: [initiales RED](red.log), [historisches erstes GREEN](initial-green.log),
[Review-RED](review-red.log), [finales GREEN](green.log),
[unabhängiger finaler Lauf](independent-host.log),
[unabhängige Prüfbilanz](independent-result.json),
[Abgleich des Quellcommits mit den geprüften Bytes](independent-commit-review.json).
Die Textprotokolle bewahren die ausgegebenen Inhalte; ausschließlich leere Zeilen nach
der letzten Ausgabe wurden normalisiert. Quellen- und Testdatei-Hashes beziehen sich
auf unveränderte Eingaben, nicht auf diese Logdateien.
Der Code wird vollständig aus der originalen
Produktionsdatei kompiliert, keine kopierte TemporalCue- oder Retriever-Implementierung.
Ein ausschließlich zum Kompilieren benötigter `EventEmittingRetriever`-Stand-in wirft bei
Instanziierung sofort einen Fehler; dieser außerhalb des Testumfangs liegende Eventpfad
wird nicht simuliert und nicht als geprüft ausgegeben. Die Factory ohne App-Verzeichnis
verwendet den echten `CharNgramHashRetriever`.

Toolchain des protokollierten Laufs: OpenJDK `21.0.13`, Kotlin Compiler `2.0.20`,
Bytecode-Ziel JVM 17, JUnit `4.13.2`; Compiler-/Testprozess jeweils auf `-Xmx1g` begrenzt.
Kein Gradle, kein Netzwerk, kein Android-/Fold7-Lauf, keine Modellinferenz, keine APK.
Der Shell-Runner besteht `bash -n`; `git diff --check` meldet keine Fehler.

Reproduzieren im Repository:

```bash
tools/temporal-cue-host-check/run.sh /path/to/gradle-8.11.1/lib
```

Der Runner benötigt die bereits vorhandenen Kotlin-/JUnit-JARs aus diesem Verzeichnis.
Im Reparaturlauf wurde `/workspace/scratch/b7d23de0e715/build-toolchain/gradle-8.11.1/lib`
verwendet. Er startet keine Gradle-Aufgabe und lädt nichts herunter.

Die protokollierten HEAD-Zeilen der Bot-Läufe zeigen noch die Ausgangsbasis: Der Patch
war bei den RED-/GREEN-Läufen uncommittet. Deshalb binden die folgenden SHA-256-Werte die tatsächlich
kompilierten Inhalte, zusätzlich zu den im Rohlog enthaltenen Pfaden:

| Inhalt | SHA-256 |
| --- | --- |
| Produktionsdatei RED | `aa47dd828e4d337fe83dbd598133f0b8228e7f0e59b1f2762051631e0c954800` |
| Produktionsdatei im ersten Patch / Review-RED | `d130254d403d55b61be5c1afaab638b65511b54fa56ecc10adad533529b85caa` |
| Produktionsdatei final / unabhängig | `8d095be29157246c8f7dad09e2ae3960519ae816c428dfe399ebcdfe65463e1d` |
| Neue Tests im initialen RED / ersten GREEN | `24f71ed1848a692191adc4a6b678bed1bde15fc5af1cca5b8c590c5acbed3970` |
| Neue Tests im Review-RED / final / unabhängig | `1a25f175033f39dfffd8670503a05d62da76432304e80ca59c14d11dce65c0e9` |
| Unveränderte vorhandene Tests | `f278272a5c4cb9fef1a125481486f2c3ca7ddc25a963409d3aaaae1bd5c9d08e` |

## Gemessene Grenze der Retrievalqualität

Das vorhandene Corpus enthält 1.000 synthetische Erinnerungen und 44 Fragen. Der bisherige
Recall@3-Mindestwert von 0,80 bleibt erfüllt. Korrekte, breitere Kalenderfenster verändern
aber die Konkurrenz zwischen Treffern; sie sind keine allgemeine Qualitätsverbesserung:

| Variante / Messwert | RED | GREEN |
| --- | ---: | ---: |
| Char-N-Gramm + Zeit: Recall@1 | 0,886 | 0,886 |
| Char-N-Gramm + Zeit: Recall@3 | 0,977 | 0,955 |
| Char-N-Gramm + Zeit: MRR | 0,924 | 0,921 |
| BM25 + Zeit: Recall@1 | 0,818 | 0,795 |
| BM25 + Zeit: Recall@3 | 0,841 | 0,841 |

Diese Änderung wird offengelegt; weder Corpus noch Abnahmeschwelle wurden angepasst.
Das Ergebnis belegt korrekte Kalendergrenzen und sichere Behandlung ungültiger Zahlen,
keine generelle Verbesserung der Retrieval- oder Modellleistung.

### Konkrete betroffene Frage: echter Rangverlust von 3 auf 5

Frage: **„Wohin ging ich letzten Monat mit Thomas?“**, Auswertungszeitpunkt
`2026-11-04T09:00:00Z`, Zone `Europe/Vienna`. Erwarteter Treffer ist `t14`,
„Letzten Monat war ich mit Thomas im Theater.“, gespeichert am `2026-10-05T08:00:00Z`
(10:00 Uhr Wiener Zeit). Der Treffer liegt in beiden Zeitfenstern; sein Score bleibt
`0.14520920161157846`. Sein Rang fällt jedoch von **3 auf 5** und verliert damit einen
Recall@3-Treffer unter **44 Fragen**.

Die Original-Corpus-Funktion des unveränderten Tests wurde für die Diagnose per Reflection
gelesen. Beide Läufe verwenden die vollständige jeweilige Produktionsdatei und den gleichen
Testdatei-Hash. Keine Daten, erwarteten Antworten oder Scores wurden angepasst.
Diagnoseprogramm: [CorpusProbe.kt](CorpusProbe.kt). Rohbelege:
[Corpus vor Patch](corpus-baseline.log), [Corpus nach finalem Patch](corpus-final.log).

| Treffer | Zeitstempel UTC | Rang vorher | Rang nachher |
| --- | --- | ---: | ---: |
| `d0016`: „Thomas plant das Fahrrad im Park.“ | 21.09.2026 08:00 | 1 | außerhalb Top 6 |
| `d0855`: „Thomas hat den Bus online.“ | 24.10.2026 08:00 | außerhalb Top 6 | 1 |
| `d0570`: „Thomas habe die Jacke zu Hause.“ | 25.10.2026 08:00 | außerhalb Top 6 | 2 |
| `d0757`: „Thomas hat den Staubsauger im Park.“ | 07.10.2026 08:00 | 2 | 3 |
| `d0882`: „Thomas habe den Staubsauger im Park.“ | 30.10.2026 08:00 | außerhalb Top 6 | 4 |
| `t14`: erwarteter Theater-Treffer | 05.10.2026 08:00 | 3 | 5 |

Die späten Oktober-Erinnerungen erhalten im korrekten Monatsfenster nun zeitliches Gewicht
1,0 statt 0,35. Ihre höheren lexikalischen Scores verdrängen den unverändert bewerteten
Zieltreffer. Das ist ein echter Ranking-Nachteil durch die veränderte zeitlich korrekte
Konkurrenz. Der vorherige Spitzenreiter vom September gehört seinerseits nicht zum
angefragten Kalendermonat. Unbestätigte Treffer bleiben unbestätigt; der Patch nimmt
keine zusätzliche semantische oder vertrauensbasierte Rankingoptimierung vor.

## Experimentvertrag, Status und nächste Grenze

Relevante Verträge: EXP-05 (lokaler Modellpfad bleibt unverändert), EXP-06 (kein Aufwerten
unbestätigter Treffer), EXP-07 (negative und Grenzfalltests), EXP-12 (kein Schreiben oder
Migrieren von Besitzerwissen). Schnittstellen und persistente Datenformate bleiben gleich.

Status: `SOURCE_INTEGRATED`, lokal und unabhängig hostgeprüft. Root hat die quellidentische
Reparatur nach Gegenprüfung im oben verlinkten gemeinsamen Draft gesichert. Der
isolierte Bot-Zweig und seine lokalen Commit-IDs sind davon zu unterscheiden;
kein Main-Merge oder Release in diesem Auftrag.

Unabhängiger Hostlauf derselben Quellen ist mit 17/17 bestanden und unveränderten
Eingabe-Hashes abgeschlossen. Root meldete zusätzlich einen eigenen 17/17-Lauf auf dem
kombinierten Stand unter JDK 17; dessen Beleg gehört zum gemeinsamen Koordinatorbericht.
Vollständiger Android-Build und reale Gerätewirkung bleiben gesonderte Gates.
Rücksetzweg: den isolierten TemporalCue-Fix-Commit zurücknehmen; keine Datenmigration
und keine Änderung an Besitzergewichten oder gespeicherten Erinnerungen erforderlich.
