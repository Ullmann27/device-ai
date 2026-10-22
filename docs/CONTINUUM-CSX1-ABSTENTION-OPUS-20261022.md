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
