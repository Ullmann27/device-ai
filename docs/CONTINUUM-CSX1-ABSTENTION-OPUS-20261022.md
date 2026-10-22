# CSX1 – ehrliche Enthaltung und Generalisierung (Opus, 22.10.2026)

Experiment-IDs EXP-04/05/06 (Shadow-Forschung). Branch `opus/continuum-csx1-abstention-20261022`, gestapelt auf PR #61
(`chatgpt/continuum-residual-pointer-built-20261022`, Basis `21365a4e2cf1e0a8fa101e069fa4e019d36143a6`). Kein Merge nach `main`,
keine Promotion, SWB1/CDX1 und Gerätecheckpoints unberührt. Dieses Dokument wird mit jedem Zyklus fortgeschrieben.

## Umgebung
Linux x86_64-Container, OpenJDK 21.0.12.1, `kotlinc-jvm 2.0.20` (offizielle Maven-Central-Artefakte, Aufbau siehe
`docs/CONTINUUM-OPUS-HANDOFF-20261022.md` auf PR #57), JUnit 4.13.2. **Android-SDK/Gradle-Android-Build: `UNAVAILABLE`**
(Host `dl.google.com` per Netzwerkrichtlinie gesperrt). Kein APK, kein Samsung-Test.

## Gate 1 – exakte Reproduktion von PR #61 (`21365a4`)

```sh
cd app/src
kotlinc main/java/at/heinz/deviceai/learning/span/ContinuumResidualSpanCore.kt \
        main/java/at/heinz/deviceai/learning/span/ContinuumCounterfactualCurriculum.kt \
        test/java/at/heinz/deviceai/learning/span/ContinuumResidualSpanCoreChecks.kt \
        test/java/at/heinz/deviceai/learning/span/ContinuumResidualSpanCoreTest.kt \
        -cp junit-4.13.2.jar:hamcrest-core-1.3.jar -jvm-target 17 -include-runtime -d csx1.jar     # exit 0
java -cp csx1.jar at.heinz.deviceai.learning.span.ContinuumResidualSpanCoreChecks                   # exit 0
java -cp csx1.jar:junit-4.13.2.jar:hamcrest-core-1.3.jar org.junit.runner.JUnitCore \
     at.heinz.deviceai.learning.span.ContinuumResidualSpanCoreTest                                  # OK (1 test), exit 0
```

Tatsächliche Ausgabe (identisch zur früheren Prototypmessung):
`PASS residual gradient checks=38 maximumError=1.5443709852624998E-10`, alle Schutzprüfungen PASS,
`300 steps, loss 1.2136851176488221 -> 0.6067638047117935`, DEV 2/5 → 3/5; `Salzburg`, `XZ-902`, `Zange` richtig;
**beide unbeantwortbaren Fragen falsch beantwortet** (`Salzburg`, `XZ-902` statt Enthaltung).
Gradle-JUnit über die Android-Testvariante: `UNAVAILABLE` (kein SDK).

## Gate 2 – eingefrorener Benchmark (`ContinuumSpanAbstentionBench.kt`, Testquellen, nicht im APK)

Deterministischer Generator, 7 Relationen (Standort, Werkzeug, Modulcode, Farbe, Port, Bibliothek, Entwickler),
**strikt getrennt**: Entitäts-IDs (Anfangsbuchstaben A–H / J–M / P–W), Antwortwerte (disjunkte Pools), Satzfamilien
und eine Fragevorlage nur im TEST. Harte Gegenbeispiele: gleiche Evidenz + Frage nach anderer Relation, andere Entität,
verneinter Wert; dazu „nicht A, sondern B“, zwei Entitäten, zwei Fakten einer Entität, gleiche Teilsatz-Entitäten,
Zeitqualifikator, Einheit/Mehrwort (nur Diagnose, für einen Einzeltoken-Span strukturell unlösbar).
Jeder unbeantwortbare Fall wird aus expliziten Faktobjekten erzeugt und strukturell geprüft (kein gültiger Beleg).
Vor dem Einfrieren entfernt: eine Positionsabkürzung (richtige Antwort stand bei Mehrfachentitäten/Zeit immer am Ende).

- TRAIN 246 (106 beantwortbar), VALIDATION 158, **TEST 264 (124 beantwortbar, 140 unbeantwortbar, 68 ungesehene Formen)**
- `TEST_SHA256 = 974e4ba3c405005a7773c479282f4abc03e2a23530def4da69471a27799b0ba8` – als JUnit-Gate eingefroren
  (`ContinuumSpanAbstentionBenchTest`, 3/3 grün). Schwellen werden nur auf VALIDATION gewählt.
- Öffentliche PR59-/Argon-/PR61-Beispiele sind **nicht** Teil des Tests.
- CDX1-Vergleich: `UNAVAILABLE` – im Repository liegt kein trainierter CDX1-Checkpoint (die Gewichte existieren nur auf
  dem Gerät und werden nicht importiert); ein untrainierter CDX1 wäre kein fairer Vergleich.

## Zyklus 1 – Baseline und Curriculum-Hypothese (TEST, 3 Seeds × 30 Epochen, identische Bedingungen)

Volllog: `docs/evidence/csx1-abstention-20261022/cycle1-baseline-test.txt`.

| Variante | richtig (von 124) | unbelegte Antworten (von 140) | fälschl. Enthaltung | Fallen (Verwechslung/Negation) |
|---|---|---|---|---|
| B0 untrainiertes CSX1 | 0 | 0 | 124 | 0 |
| R1 Oberflächenheuristik (Regel) | 74 | 84 | 0 | 84/202 |
| R2 aus TRAIN abgeleitete Lexikonregel | 74 | 0 | 16 | 21/202 |
| V1 PR61-Kern, Originalrezept (1 Negativ : 4 Positive), argmax | 96 / 96 / 97 | 61 / 62 / 58 | 4 | 48 / 48 / 45 |
| V1 + auf VALIDATION gewählte Schwelle 0,80 | 62 / 62 / 60 | 14 / 15 / 14 | ≈60 | ≈11 |
| **V2 PR61-Kern, hartes Negativcurriculum, argmax** | **94 / 94 / 94** | **9 / 9 / 9** | 14 | 11 / 11 / 11 |

**Befund:** Die erfundenen Antworten des PR61-Kerns entstehen aus dem Trainingsrezept, nicht aus einer fehlenden
Enthaltungsfähigkeit der Architektur. Mit denselben 4.110 Parametern, ohne Regel und ohne Schwelle sinken unbelegte
Antworten von ≈60 auf 9 (−85 %) bei −2 richtigen Antworten. Eine Schwelle allein (V1+0,80) erkauft dasselbe mit
dem Verlust von 34 richtigen Antworten. Verbleibende Schwäche von V2: **ungesehene Satzformen** 9/35 richtig
(bekannte Formen 85/89), gleiche-Teilsatz-Entitäten und Zeitqualifikator auf Zufallsniveau (3/6), Einheiten 0/6.

## Zyklus 2 – Hypothesen H2/H3 zu ungesehenen Satzformen (nur VALIDATION) – **negatives Ergebnis**

VALIDATION erhielt eigene, nur dort vorkommende Satzfamilien/Fragevorlagen (TRAIN/TEST unverändert; TEST-Hash geprüft).
Achtung: Dadurch änderte sich VALIDATION nach Zyklus 1; die Schwellen der Zyklus-1-Tabelle beziehen sich auf die
alte VALIDATION, die argmax-Zahlen sind unverändert reproduzierbar.
- H2 Kennungs-Platzhalter (`<id>`/`<num>` statt Hash-Grams beliebiger IDs) und H3 Entfernen der Positionsabkürzung
  („letztes Token“, absolute Position): **kein Gewinn** auf ungesehenen Formen (alle 1/8; Fehler fast nur Enthaltung).
- Fehleranalyse: Bei unbekannten Formulierungen („Standort von“, „gehört zur Ausstattung“, „steckt das Modul“) erkennt
  das lexikalische Modell die Relation nicht und **enthält sich** (sicheres Versagen, keine Erfindung). Mit zwei
  Trainingsphrasierungen je Relation und 4.110 Parametern ist neue Paraphrase ohne lexikalisch-semantisches Vorwissen
  nicht erschließbar – eine **gemessene Grenze**, kein Tuningproblem. Logs: `cycle2-select-validation.txt`.

## Zyklus 3 – H4 gelernte Entitätsnähe (Bindung im Teilsatz)

Hypothese: Die Positions-Slots tragen nachweislich nichts bei (V3≈V2); als gelernte Eingaben eignen sich stattdessen
die Nähe zur gefragten Entitäts-ID im Teilsatz, gezählt in Kandidaten-Token (Entität davor / danach). Gewicht wird
gelernt, keine Antwortregel, Parameterzahl unverändert 4.110 (`Features(positionFeatures=false, entityProximity=true)`).

Auswahl nur auf VALIDATION (eigene Bindungs-/Zeitfälle mit Relation „Werkzeug“, TEST nutzt „Standort“):
Bindung im Teilsatz 2/6 → 4/6, Fallen dort 3/6 → 0/6, alle Fallen 10 → 5, richtig 64 → 66/79, unbelegt gleich.
Zusätzliches Bindungs-Trainingscurriculum (Modul/Port): kein Gewinn → verworfen.

**Einmalige TEST-Messung** (eingefroren, 3 Seeds × 30 Epochen, argmax; Log `cycle3-final-test.txt`):

| | V2 (PR61-Merkmale) | V6 (Entitätsnähe) |
|---|---|---|
| richtig (von 124) | 94 / 94 / 94 | **97 / 97 / 97** |
| falsche Spanne | 16 | **8 / 9 / 8** |
| Fallen: Verwechslung/Negation (von 202) | 11 | **3 / 5 / 3** |
| gleicher Teilsatz, zwei Entitäten | 3/6 | **6/6** (Verwechslung 0) |
| Zeitqualifikator „heute/früher“ | 3/6 | 4/6 |
| ungesehene Formen | 9/35 | 12/35 |
| unbelegte Antworten (von 140) | **9** | 10 / 12 / 11 |
| davon verneinte Werte | 5 | 7 / 8 / 7 |

Ehrliche Bewertung: V6 verbessert die **Entitätsbindung** messbar und reproduzierbar (Teilsatzbindung 3/6 → 6/6,
Fallen −60 %), kostet aber **1–3 zusätzliche unbelegte Antworten**, konzentriert auf verneinte Werte. Keine der
Varianten löst Zeitqualifikatoren zuverlässig oder Einheiten/Mehrwortantworten (0/6, Einzeltoken-Grenze).

## Zyklus 4 – Diagnose der Negationsschwäche und H5 (Signalwörter sind keine Antworten)

Diagnose (nur VALIDATION, `diagnose`-Modus): Das Modell antwortete bei verneinten Belegen teils **„nicht“ / „keine“** –
die Signalwörter der Negations-/Kontrastmerkmale waren selbst Antwortkandidaten. Mit Entitätsnähe liegt „nicht“
direkt hinter der Entität; im Training drückte das zusätzlich das gelernte Negationsgewicht (V6 −1,29 statt V2 −2,25).
H5: `Features(excludeCueWords = true)` entfernt `nicht, kein, keine, keinen, sondern, stattdessen` aus der
Kandidatenmenge (feste grammatische Einschränkung des Kandidatenraums, **keine Antwortregel**; separat ausgewiesen).
VALIDATION: V8 (Nähe + Ausschluss) ≈ V6 im Rauschbereich (65,7 vs 66 richtig, 4,7 vs 5,3 unbelegt). Gelerntes
Negationsgewicht mit V8 wieder −2,28.

**Einmalige TEST-Messung V8** (eingefroren, 3 Seeds identisch; Log `cycle4-final-test-V8.txt`): 99/124 richtig,
**6/140 unbelegt**, Fallen 3/202, Teilsatzbindung 6/6, Mehrfachentitäten 21/21, Verneinung 3/28 unbelegt.

## Zyklus 5 – H6 begrenzte Mehrwort-/Einheitenspannen – **negatives Ergebnis auf TEST**

Umsetzung (Flag 16, `Features(multiTokenSpans = true)`, Standard aus und bitidentisch zu V8/V2 – beide
Checkpoint-Hashes reproduziert): Kandidaten sind zusätzlich Spannen aus bis zu **3** benachbarten Kandidatentokens
**im selben Teilsatz**, nur durch Leerraum getrennt; Antwort = **exakter Quelltext-Ausschnitt** `evidence[start, end)`;
gelernte Längenmerkmale über einen gehashten `len:n`-Kontextschlüssel; höchstens 192 Kandidaten. Negativtests
(`ContinuumResidualSpanCoreSpanTest`): kein Überschreiten von Komma/Satzende, keine Stoppwörter, keine Spanne > 3,
abgefragte Entität nie Teil der Antwort, Vorhersage == exakter Teilstring, finite Differenzen, v2-Roundtrip.

Daten (`multiword`-Option, TEST unverändert, Hash-Gate grün): TRAIN „Gerät X hat ein Gewicht von N Kilogramm“,
„Roboter X ist N Meter lang“, „Paket X stammt von Vorname Nachname“ + Verneinungen/andere Relation als harte
Negative; VALIDATION eigene Formen („Behälter X fasst N Liter“, „Die Autorin von Paket X ist …“); TEST bleibt die
eingefrorene Form „Maschine X wiegt N Kilogramm.“ / „Wie schwer ist X?“. Einzeltoken-Modelle trainieren nie auf
Mehrwort-Gold (nicht darstellbar). Fairer Vergleich: V8 und V9 mit **denselben** TRAIN-Daten (`trainSha=719fd98e…`).

| (argmax, Seed 1; Seeds 2/3) | richtig | unbelegt | Fallen | Mehrwort | Teilsatzbindung | Verneinung unbelegt |
|---|---|---|---|---|---|---|
| VALIDATION V8 | 65/91 (66/66) | 7/100 | 12/156 | 0/12 | 5/6 | 4/24 |
| VALIDATION **V9** | **73/91 (74/74)** | 6/100 | 7/156 | **10/12** | 4/6 | 3/24 |
| TEST V8 (gleiche Daten) | 96/124 (98/98) | 4/140 | 4/202 | 0/6 | 6/6 | 2/28 |
| TEST V9 | 94/124 (97/96) | **8/140** (8/7) | 4/202 | **1/6** (2/6, 1/6) | 6/6 | 4/28 |

Auswahl auf VALIDATION fiel auf V9; die **einmalige TEST-Messung** (Log `cycle5-final-test-V8-V9-multiword.txt`)
bestätigt das nicht: Mehrwort 1–2/6 statt 0/6, aber **mehr unbelegte Antworten** (7–8 statt 4, v. a. andere Relation
und Verneinung) und nicht mehr richtige. Mit VALIDATION-Schwelle (0,45/0,55/0,60) V9 Mehrwort 0/6. Deutung: Die
VALIDATION-Formen teilen Wörter mit der Frage („fasst“↔„fasst“, „Autorin“↔„Autorin“); die TEST-Form („wiegt“↔„Wie
schwer“) nicht – dieselbe lexikalische Grenze wie bei ungesehenen Paraphrasen, kein gelerntes Einheitenkonzept.
Mehr Kandidatenspannen = mehr Gelegenheiten zu erfundenen Antworten. **Entscheidung: V9 wird nicht übernommen**,
Flag bleibt standardmäßig aus; der Code bleibt als geprüfte, darstellungssichere Option (exakte Offsets) erhalten.
Nebenbefund (nur beobachtet, nicht ausgewählt, weil auf TEST gesehen): V8 mit den zusätzlichen harten Negativen
der `multiword`-Daten hatte 4 statt 6 unbelegte Antworten – Kandidat bleibt dennoch der in Zyklus 4 gewählte V8.

## Meilenstein I1 – zertifizierte Enthaltung (Learn-then-Test)

Eigenes Dokument: `docs/CONTINUUM-INTELLIGENCE-MILESTONE-I1-20261022.md`. Es enthält die Recherche mit Quellen, die
Kritik am Versuchsaufbau, das Verfahren und die Ergebnisse. Kurz: Der Kandidat V8 erhält eine wählbare Fehlergrenze α
mit Garantie. Bei α = 0,02 gibt er auf TEST 3/264 falsche Antworten (argmax: 16/264), bei 96 % Präzision und
78 statt 99 richtigen Antworten. Die Garantie wurde auf einem Holdout und mit 200 Neuaufteilungen geprüft.

## Gesamtvergleich auf dem eingefrorenen TEST (argmax, 3 Seeds, gleiche TRAIN-/Trainingsbedingungen)

| Variante | richtig /124 | unbelegt /140 | falsche Spanne | Fallen /202 | Teilsatzbindung | Verneinung unbelegt /28 | ungesehene Formen |
|---|---|---|---|---|---|---|---|
| V1 PR61-Kern + Originalrezept | 96 | 61 | 24 | 48 | 2/6 | 24 | 9/35 |
| V2 PR61-Kern + hartes Curriculum | 94 | 9 | 16 | 11 | 3/6 | 5 | 9/35 |
| V6 + gelernte Entitätsnähe | 97 | 10–12 | 8–9 | 3–5 | 6/6 | 7–8 | 12/35 |
| **V8 + Nähe + Signalwörter keine Kandidaten** | **99** | **6** | **8–10** | **3** | **6/6** | **3** | **12/35** |
| R2 Lexikonregel (Regel-Baseline) | 74 | 0 | 34 | 21 | 4/6 | 0 | 7/35 |
| V9 V8 + Mehrwortspannen (Zyklus 5, multiword-TRAIN) | 94 | 8 | 13 | 4 | 6/6 | 4 | 11/35 |

(Alle Werte aus den 30-Epochen-Logs, Seed 1; Seeds 2/3 siehe Logs.) Herkunft der Verbesserung:
**Curriculum** (gelernte Gewichte, gleiche Architektur) erklärt den Großteil (unbelegt 61 → 9);
**gelernte Entitätsnähe** (neue Eingaben, gelernte Gewichte) die Bindung; **Kandidatenraum-Einschränkung** (feste
Grammatik) die Negationsfehler; **Schwelle**: keine (argmax). Eine auf VALIDATION gewählte Schwelle senkt bei V8 die
unbelegten Antworten nicht (6 → 6), kostet aber richtige Antworten (98/91/98 statt 99) und wird daher nicht empfohlen.

Öffentliche PR61-DEV-Fälle (nur Regression, kein Blindtest): PR61 3/5 mit 2 erfundenen Antworten → **V8 4/5, beide
Enthaltungen korrekt, 0 erfundene Antworten** (`Salzburg` jetzt fälschliche Enthaltung).

## Isolierter Kandidat (nur zur Besitzerkontrolle, keine Promotion)
`Features(positionFeatures=false, entityProximity=true, excludeCueWords=true)`, Seed 77, TRAIN-Seed 1, 30 Epochen,
7.380 Updates, Checkpoint v2 SHA-256 `1786fa9e58d2a1262c461bd2d9563cddf5478260ac62da88f160e868c3c3b7ef`,
TRAIN `5743baa5…`, TEST `974e4ba3…`. Deterministisch reproduzierbar (siehe Befehle unten); es wird **keine**
Binärdatei eingecheckt. SWB1/CDX1, Gerätecheckpoints und produktiver Chat wurden nicht berührt.

## Gemessene Grenzen (offen)
1. **Ungesehene Paraphrasen** (12/35): ohne lexikalisch-semantisches Vorwissen nicht erschließbar; Modell enthält sich
   meist (sicher). Nächster Schritt: read-only SWB1-/CRE1-Ähnlichkeit als zusätzliche gelernte Eingabe testen.
2. **Zeitqualifikatoren** (4/6): Nähe allein reicht nicht; braucht Qualifikator-Merkmal (heute/früher-Bindung).
3. **Mehrwort/Einheiten** (V8 0/6, V9 1–2/6): jetzt mit exakten Offsets **darstellbar** (Flag 16, getestet), aber für
   die ungesehene TEST-Form nicht gelernt und mit mehr unbelegten Antworten erkauft (Zyklus 5). Nicht gelöst.
4. Verbleibende 6 unbelegte Antworten (3 andere Relation, 3 verneint).

## Reproduktion
```sh
cd app/src
kotlinc main/java/at/heinz/deviceai/learning/span/*.kt test/java/at/heinz/deviceai/learning/span/*.kt \
  -cp junit-4.13.2.jar:hamcrest-core-1.3.jar -jvm-target 17 -include-runtime -d csx1.jar
java -cp csx1.jar:junit-4.13.2.jar:hamcrest-core-1.3.jar org.junit.runner.JUnitCore \
  at.heinz.deviceai.learning.span.ContinuumResidualSpanCoreTest \
  at.heinz.deviceai.learning.span.ContinuumResidualSpanCoreVariantsTest \
  at.heinz.deviceai.learning.span.ContinuumResidualSpanCoreSpanTest \
  at.heinz.deviceai.learning.span.ContinuumSpanAbstentionBenchTest \
  at.heinz.deviceai.learning.span.ContinuumCertifiedAbstentionTest            # OK (16 tests)
java -cp csx1.jar at.heinz.deviceai.learning.span.ContinuumSpanAbstentionExperiment cycle1      # Zyklus 1, TEST
java -cp csx1.jar at.heinz.deviceai.learning.span.ContinuumSpanAbstentionExperiment select V2 V6 V8   # nur VALIDATION
java -cp csx1.jar at.heinz.deviceai.learning.span.ContinuumSpanAbstentionExperiment final V8    # eingefrorener TEST
java -cp csx1.jar at.heinz.deviceai.learning.span.ContinuumSpanAbstentionExperiment diagnose V8 # Gewichte, PR61-DEV
java -cp csx1.jar at.heinz.deviceai.learning.span.ContinuumSpanAbstentionExperiment select multiword V8 V9  # Zyklus 5
java -cp csx1.jar at.heinz.deviceai.learning.span.ContinuumSpanAbstentionExperiment final multiword V8 V9   # einmal TEST
java -cp csx1.jar at.heinz.deviceai.learning.span.ContinuumSpanAbstentionExperiment certify   # Meilenstein I1
```
Status: `SOURCE_INTEGRATED`, `JVM_TESTED` (kotlinc 2.0.20, JDK 21, Linux x86_64). Android-Gradle/APK/Gerät:
`UNAVAILABLE` (kein SDK). Keine Android-Integration vorgenommen (Gates verlangen vorher Besitzerkontrolle).
