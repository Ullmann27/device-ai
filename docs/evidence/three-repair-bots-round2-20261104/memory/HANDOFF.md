# Runde 2: Tagesgrenzen repariert, Rankinggrenze offengelegt

Datum: 04.11.2026. Zuständig: Reparatur-Bot 1, Memory.

## Ergebnis und eingefrorener Quellstand

Der vorhandene `TemporalCue` berechnet relative Tagesfenster jetzt aus lokalen
Kalenderdaten und löst **beide Endpunkte unabhängig** mit `atStartOfDay(zone)` auf.
Damit verschiebt ein heutiger Tagesbeginn um 01:00 nicht die Mitternacht anderer Tage.
Die Reparatur wurde mit echten Fehlern am unveränderten Produktionscode begonnen:
**29 Tests / 8 Fehler → 29 Tests / 0 Fehler**.

Quellkandidat, vor unabhängiger Koordinatorprüfung eingefroren:
`4b120c2a5d5b8425c0a99d0668a9d869c153ab27`
(`fix(memory): resolve relative day boundaries independently [skip ci]`).
Arbeitszweig: `repair/round2-memory-local`.
Lokale Basis: `63dec3b1296b1d4257310296de3781d75b7a266a`.
Der Basisbaum entspricht laut Koordinator dem offiziellen PR-505-Stand
`4ddbcb092d955226bb83c2a10f7ac52bb34f59eb`.
Der Koordinator hat den Rundenauftrag separat in PR #505 unter
`426c0ff9b51aad2f54026b96ed50ae0474e50369` gesichert.

**Die Rankingaufgabe bleibt offen:** Im unveränderten synthetischen Corpus mit
1.000 Erinnerungen und 44 Fragen bleibt Char-N-Gramm + Zeit bei Recall@3
**42/44 = 0,954545…**, im Test auf **0,955** gerundet. Der Theater-Treffer bleibt
auf Rang **5**. Die Tagesreparatur liefert keinen nachgewiesenen allgemeinen
Ranking- oder Modellqualitätsgewinn.

Status des Bot-Kandidaten bei Übergabe: `SOURCE_INTEGRATED`, **HOST_JVM_ONLY**,
lokal committed. Veröffentlichung und unabhängiger Gegencheck erfolgen durch
den Koordinator im vorhandenen offiziellen PR #505. Dieser Bericht beansprucht
keinen eigenen Remote-Push, Android-Build, Gerätetest oder Release. Eine spätere
quellidentische Veröffentlichung kann eine andere Commit-ID erhalten.

## 1. Diagnose des bekannten Rangverlusts

Ausgangspunkt ist der bereits dokumentierte Befund in
[TemporalCue-Kalender-RESULT](../../temporal-cue-calendar-20261104/RESULT.md).
Die frühere Kalenderkorrektur erweiterte das fehlerhafte ungefähre Fenster auf
den tatsächlichen gesamten Oktober. Der erwartete Treffer `t14`,
„Letzten Monat war ich mit Thomas im Theater.“, liegt am 05.10.2026 in beiden
Fenstern. Seine lexikalische Ähnlichkeit und sein Zeitgewicht änderten sich dabei
nicht. Korrekt zugelassene späte Oktober-Treffer kommen in der Konkurrenz hinzu.

Die [historische direkte Corpus-Probe](../../temporal-cue-calendar-20261104/corpus-final.log)
des Ausgangscodes zeigt für „Wohin ging ich letzten Monat mit Thomas?“:

| Rang nach Kalenderkorrektur | Erinnerung | Cosinus = Score | Zeitgewicht |
| ---: | --- | ---: | ---: |
| 1 | `d0855`: Thomas hat den Bus online. | 0,19437710661441088 | 1,0 |
| 2 | `d0570`: Thomas habe die Jacke zu Hause. | 0,18374881613999605 | 1,0 |
| 3 | `d0757`: Thomas hat den Staubsauger im Park. | 0,14960756059736013 | 1,0 |
| 4 | `d0882`: Thomas habe den Staubsauger im Park. | 0,14960756059736013 | 1,0 |
| 5 | `t14`: Letzten Monat war ich mit Thomas im Theater. | 0,14520920161157846 | 1,0 |

Diese Zahlen sind die vorhandene frühere Diagnose, keine neu erzeugte Messreihe.
Der frische [RED-Lauf](host-red.log) und [GREEN-Lauf](host-green-before-commit.log)
bestätigen jeweils Rang 5 und dieselben Corpus-Kennzahlen. Die Corpusdatei bleibt
bytegleich; der Inhalt von `GermanText`, BM25 und Char-N-Gramm-Ranking wird von
diesem Patch nicht geändert.

**Bewertung:** Ein tatsächlicher Implementierungsfehler in der Rankingformel ist
für diesen Rangverlust nicht belegt. Der implementierte normalisierte
TF-IDF-Cosinus wertet lexikalische Zeichenfolgen; das Zeitgewicht unterscheidet
zwei korrekt im gleichen Monat liegende Erinnerungen nicht. Die Frage nennt
die Person und den Monat, aber weder „Theater“ noch ein anderes spezifisches
Ereignismerkmal. Die Abbildung von „Wohin ging ich …?“ auf einen Ortsbesuch ist
in diesem Retriever keine eigenständige semantische Fähigkeit. Die vorhandene
Normalisierung kann deshalb andere Thomas-Texte höher bewerten.

Das ist eine sachlich begründete Grenze des hier vorhandenen Verfahrens, kein
Beweis, dass allgemeine Verbesserungen unmöglich wären. Aus dieser einen
bekannten Frage folgt jedoch keine belastbare neue Rangformel. Deshalb werden
weder Theater/Thomas/IDs bevorzugt noch Kalenderfenster verengt, Schwellen
gesenkt, Corpusdaten geändert oder Parameter auf den bekannten Treffer abgestimmt.
Der frühere Rückgang **43/44 → 42/44** durch die Kalenderkorrektur wird nicht
zurückgenommen oder als erledigt ausgegeben.

## 2. Nachgewiesener Implementierungsfehler in `days()`

Der Koordinator meldete eine unabhängige Probe mit sechs roten Santiago-Fällen
und zwei grünen Wiener Kontrollen. Der Bot reproduzierte die Ursache anschließend
mit regulären JUnit-Tests gegen die vollständige unveränderte Retrieverquelle.
Die konkrete Hypothese wurde dem Koordinator vor der Produktionsänderung gemeldet.

Am 06.09.2026 beginnt der lokale Tag in `America/Santiago` wegen der
Mitternachtslücke um 01:00. Der alte Code kürzte einen `ZonedDateTime` auf den
Tagesbeginn und verwendete danach `minusDays` auf diesem zonierten Zeitpunkt.
Die auf 01:00 aufgelöste Uhrzeit blieb dabei für andere Tagesgrenzen erhalten.

Feste UTC-Orakel der Tests bei `now = 2026-09-06T12:00:00Z`:

| Ausdruck | Erster enthaltener Zeitpunkt | Exklusives Ende |
| --- | --- | --- |
| heute / vor 0 Tagen | 06.09.2026 04:00Z | 07.09.2026 03:00Z |
| gestern / vor 1 Tagen | 05.09.2026 04:00Z | 06.09.2026 04:00Z |
| vorgestern / vor zwei Tagen | 04.09.2026 04:00Z | 05.09.2026 04:00Z |
| vor 3 Tagen | 03.09.2026 04:00Z | 04.09.2026 04:00Z |
| vor einem Monat, bestehende Approximation | 23.07.2026 04:00Z | 18.08.2026 04:00Z |

Vor dem Patch verlor „gestern“ seine erste Stunde. „Heute“ enthielt zusätzlich
die erste Stunde des Folgetags. Die übrigen früheren Tagesgrenzen wurden ebenfalls
um eine Stunde verschoben. Zwei Tests zeigen die tatsächliche Folge für die Suche:
Bei identischem Erinnerungstext wird eine Erinnerung innerhalb des korrekten
Tages zu niedrig beziehungsweise eine außerhalb liegende zu hoch gewichtet.

**Einzige Reparaturhypothese:** Lokales Kalenderdatum zunächst als `LocalDate`
bestimmen; für Start- und Enddatum getrennt `atStartOfDay(zone)` anwenden.
Die Umsetzung ersetzt die zonierte Zwischenvariable durch das lokale Datum,
löst beide Tagesgrenzen getrennt auf und entfernt den unbenötigten
`ChronoUnit`-Import. Wochen- und Monatszweige verwenden weiterhin dieselben
Kalenderdaten und lösen die Grenzen wie zuvor unabhängig auf.

Unverändert bleiben Tagesabstände, die ungefähre Spanne „vor einem Monat“ von
45 bis einschließlich 20 Tagen, Zeitgewichte 1,0/0,35, die bestehenden
`toLongOrNull`-/`DateTimeException`-/`ArithmeticException`-Grenzen, Ranking,
öffentliche Schnittstellen, bestätigter Status und persistente Formate.

## 3. Tatsächliche Prüfung

| Lauf | Produktionsquelle | Bestehende Tests | Neue Tests | Ergebnis |
| --- | --- | ---: | ---: | --- |
| RED | unveränderte R2-Basis | 17 | 12 | 29 ausgeführt, 8 Fehler |
| GREEN vor Quellcommit | eingefrorene Kandidatenbytes | 17 | 12 | 29 bestanden |

Die acht roten Methoden sind sechs Tagesfenster-Regressionen und zwei
Retrieval-Regressionen. Die vier neuen grünen Kontrollmethoden prüfen einen
Mitternachtslückentag als Ziel von „gestern“, den Wiener 23-Stunden-Tag, den
Wiener 25-Stunden-Tag und unterschiedliche lokale Tagesdaten desselben
UTC-Zeitpunkts. Die zwölf neuen Methoden enthalten zusätzliche Tabellenfälle
für Wortzahlen zwei bis fünf, numerische Werte null bis fünf sowie mehrere
Zeitpunkte innerhalb desselben Tages. Jede Fensterprüfung kontrolliert die
inklusive/exklusive Millisekundengrenze und beide außerhalb liegenden Punkte.
Ein im RED-Lauf früh scheiternder Tabellenfall verhindert dort die nachfolgenden
Zeilen derselben Methode; im GREEN-Lauf werden alle Tabellenfälle ausgeführt.

Die beiden Retrieval-Regressionen verwenden echte BM25-, Char-N-Gramm- und
Default-Retriever. Identische Texte isolieren das zeitliche Gewicht. Eine
außerhalb liegende bestätigte Erinnerung steht absichtlich zuerst, damit ein
falscher Gleichstand die Tests nicht zufällig bestehen lässt. Die korrekte
Erinnerung ist unbestätigt und bleibt dies; zusätzlich wird das Scoreverhältnis
0,35 geprüft. Keine Corpusdaten werden durch diese separaten Testfixtures ersetzt.

Die bisherigen 15 Kalender-/Overflow-Tests und beide Original-Corpus-/Trust-Tests
laufen vollständig und unverändert im selben JUnit-Prozess. Sämtliche bisherigen
Mindestschwellen bleiben gleich. Frische Werte, jeweils RED → GREEN:

| Variante | Recall@1 | Recall@3 | MRR |
| --- | --- | --- | --- |
| BM25 | 0,773 → 0,773 | 0,818 → 0,818 | 0,792 → 0,792 |
| BM25 + Zeit | 0,795 → 0,795 | 0,841 → 0,841 | 0,818 → 0,818 |
| Char-N-Gramm | 0,841 → 0,841 | 0,909 → 0,909 | 0,871 → 0,871 |
| Char-N-Gramm + Zeit | 0,886 → 0,886 | 0,955 → 0,955 | 0,921 → 0,921 |

Latenzwerte in den Rohlogs stammen aus dem gemeinsamen Host und werden nicht
als Leistungsgewinn oder Geräteleistung interpretiert.

### Reproduzierbarer Befehl und Belegbindung

```bash
JAVA_HOME=/workspace/scratch/b7d23de0e715/build-toolchain/jdk17 \
  bash tools/temporal-cue-host-check/run.sh \
  /workspace/scratch/b7d23de0e715/build-toolchain/gradle-8.11.1/lib
```

Toolchain: Temurin OpenJDK 17.0.20.1+1, Kotlin-Compiler 2.0.20,
JVM-Ziel 17, JUnit 4.13.2, Hamcrest 1.3, je Prozess `-Xmx1g`.
Der Runner kompiliert die vollständige Originaldatei. Der bestehende ausschließlich
zum Kompilieren benötigte Event-Dekorator-Stand-in schlägt bei Instanziierung sofort
fehl; dessen Android-/Eventpfad ist nicht Gegenstand dieser Prüfung. Die Default-
Factory ohne App-Verzeichnis benutzt den echten Char-N-Gramm-Retriever.

Die HEAD-Zeile beider Rohlogs zeigt noch die Basis, weil die Tests vor dem
Quellcommit liefen. Die SHA-256-Werte binden die tatsächlich kompilierten Bytes:

| Datei/Zustand | SHA-256 |
| --- | --- |
| Retriever RED | `8d095be29157246c8f7dad09e2ae3960519ae816c428dfe399ebcdfe65463e1d` |
| Retriever GREEN / Quellkandidat | `ff8671fc578403f5ad53c3b7b0c50ea689dfffdf5f5d6a62ac8ec5e43871486e` |
| Neue Tagesgrenzentests, RED und GREEN | `d4209213a7ddd6df96e09ad73281ed942b1cf0459da6613a76cf99410acdb3f6` |
| Bestehende Kalender-/Overflow-Tests | `1a25f175033f39dfffd8670503a05d62da76432304e80ca59c14d11dce65c0e9` |
| Original-Corpus-/Trust-Tests | `f278272a5c4cb9fef1a125481486f2c3ca7ddc25a963409d3aaaae1bd5c9d08e` |

[source-commit-audit.json](source-commit-audit.json) bindet die geprüften Dateien
an den Quellcommit und prüft die bytegleiche Erhaltung der bestehenden Tests
und Rankingimplementierungen. Die Rohlogs sind direkte umgeleitete
Standardausgabe/Fehlerausgabe und wurden nicht nachbearbeitet.
`bash -n tools/temporal-cue-host-check/run.sh` und `git diff --check` für den
Quellpatch von Basis bis Quellkandidat bestehen. Beim zusätzlichen Belegcommit
meldet `git diff --check` ausschließlich je eine neue Leerzeile am Dateiende
der beiden Rohlogs. Diese vom JUnit-Prozess ausgegebenen Leerzeilen bleiben
für unveränderte Rohbelege erhalten; Quellcode und Tests haben diesen Hinweis nicht.

## 4. Kompatibilität, Dateien und nächster Schritt

EXP-06 bleibt erhalten: kein Aufwerten unbestätigter Erinnerungen oder
Quellen zu Fakten. EXP-07: reproduzierbares RED→GREEN mit Negativgrenzen und
unverändertem Corpus; unabhängiger Koordinatorlauf ist ein eigener Beleg.
EXP-12 bleibt erhalten: keine Besitzerdateien, Speicherformate, Gewichte,
Snapshots oder Wiederherstellungszustände werden geschrieben oder migriert.

Quelländerungen dieses Kandidaten:

- `app/src/main/java/at/heinz/deviceai/learning/ContinuumMemoryRetriever.kt`
- `app/src/test/java/at/heinz/deviceai/learning/TemporalCueDailyBoundaryTest.kt`
- `tools/temporal-cue-host-check/run.sh`

Belege liegen ausschließlich in diesem `memory/`-Ordner und werden getrennt
vom Quellcommit gesichert. Hauptchat/AgentActivity, cognitive-Module,
ToolGate, Offline-/Owner-Grenzen und der parallele PR-512-Zweig bleiben unberührt.

Nächster Schritt: Der Koordinator prüft den eingefrorenen Quellkandidaten mit
seinen zuvor festgelegten UTC-Orakeln und veröffentlicht den geprüften Inhalt
im vorhandenen PR #505. Weitere Rankingoptimierung ist hier nicht freigegeben
oder als erledigt markiert. Android-Gesamtbuild und reale Gerätewirkung sind
separate, von diesem Hostlauf nicht belegte Gates.

Rücksetzweg: ausschließlich den isolierten Quellcommit zurücknehmen. Dafür
ist keine Datenmigration nötig; ein Rücksetzen würde die nachgewiesene
Tagesgrenzenlücke wieder einführen.
