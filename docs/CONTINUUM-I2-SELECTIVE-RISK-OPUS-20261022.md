# I2 – zertifizierte bedingte Antwortqualität P(falsch | beantwortet) für CSX1 (Opus, 22.10.2026)

| | |
|---|---|
| **Zweig** | `opus/continuum-i2-selective-risk-20261022` |
| **Draft-PR** | #72, Basis PR #68 (`07b66f40`) |
| **Art** | reine Forschung; keine Gewichts- oder Checkpointänderung, keine Promotion in den Chat, kein Merge nach `main` |
| **Rohdaten** | `docs/evidence/i2-selective-risk-20261022/` |
| **Status** | `SOURCE_INTEGRATED`, `JVM_TESTED` (kotlinc 2.0.20, JDK 21, echtes JUnit 4.13.2); `APK_BUILT` und `DEVICE_VERIFIED` offen |

**Kurz:** I1 begrenzt nur den Anteil falscher Antworten an *allen* Fragen. Ein Modell, das selten antwortet, besteht
diese Prüfung auch dann, wenn jede zweite gezeigte Antwort falsch ist. Dieser Fall ist als Test nachgebaut. I2
zertifiziert zusätzlich die Qualität der *tatsächlich gezeigten* Antworten.

Ergebnisse für den Kandidaten V8 (`1786fa9e…`):
- **I2 α = 0,05 (Schwelle 0,74):** 2,1 % Fehler auf den Kalibrierantworten, 3,3 % auf dem Holdout,
  2 von 73 Antworten (2,7 %) auf dem bekannten eingefrorenen TEST, mit 30 % Antwortquote.
- **Zum Vergleich I1 α = 0,02:** Die damals zertifizierte Schwelle ließ Antworten mit 4,5 % Fehlern durch.
- **α = 0,02 oder 0,01 ist mit V8 und 1.700 Kalibrierfragen nicht zertifizierbar.** In diesem Fall enthält sich das
  Modell immer. Das ist ein ehrlicher Negativbefund.

## 1. Prüfung von PR #68

Siehe `docs/evidence/i2-selective-risk-20261022/README.md`:
- `kotlinc` mit Exitcode 0;
- JUnit `OK (17 tests)` mit Exitcode 0;
- `certify` liefert eine Ausgabe, die **bytegleich** mit `milestone-i1-certify.txt` ist.

Hash und Vorhersage laufen unter demselben Modellmonitor; alle Methoden von `ContinuumResidualSpanCore` sind
`@Synchronized`. Es gibt keinen Aufrufer der alten API mehr.

In diesem Zweig wurde aus PR #68 nur die Hash-Berechnung in `checkpointSha256(model)` ausgelagert, ohne dass sich
das Verhalten ändert. `certify` ist danach weiterhin bytegleich: `i1-certify-after-i2-refactor.txt` hat denselben
SHA-256 `af097d3c…` wie die Reproduktion.

## 2. Warum I2: Gesamtrisiko ≠ Antwortqualität

- **I1:** R(t) = P(antwortet mit Konfidenz ≥ t **und** ist falsch). Der Nenner sind alle Fragen. Für genau dieses
  Gesamtrisiko ist das richtig.
- **I2:** S(t) = P(falsch | beantwortet bei t) = R(t) / P(beantwortet). Das ist der Anteil falscher Antworten unter
  den gezeigten.

**Test `marginalCertificatePassesWhileHalfOfTheAnswersAreWrong`:**
- 1.000 Fragen, davon 30 beantwortet und 15 dieser Antworten falsch.
- I1 (α = 0,05) zertifiziert, weil 15/1.000 = 1,5 % ≤ 5 %. Trotzdem sind 50 % der gezeigten Antworten falsch.
- I2 verweigert das Zertifikat, sowohl allein als auch in der Kombination mit I1.

**Auf echten Daten (V8, Kalibrierung):**

| I1-Zertifikat | Schwelle | lässt Antworten durch mit Fehleranteil |
|---|---|---|
| α = 0,10 | 0,00 | 80/734 = **10,9 %** |
| α = 0,05 | 0,40 | 70/708 = **9,9 %** |
| α = 0,02 | 0,64 | 26/576 = **4,5 %** |

## 3. Verfahren (`ContinuumSelectiveRiskCertification.kt`)

1. **Fester Schwellenraster.** 1,00, 0,99, …, 0,00 (m = 101), festgelegt vor jeder Kalibrierung.
2. **Pro Schwelle t:** n_t beantwortete Kalibrierfragen, davon k_t falsch.
   - Bei austauschbaren (i.i.d.) Fragen gilt **bedingt auf n_t:** k_t ~ Bin(n_t, S(t)).
   - Begründung: Die Paare (beantwortet, falsch) sind i.i.d.; für die beantworteten Fragen sind die Fehlerindikatoren
     dann unabhängig mit Erfolgswahrscheinlichkeit S(t).
   - Daraus folgt der exakte p-Wert p_S(t) = P(Bin(n_t, α_S) ≤ k_t) für die Nullhypothese „S(t) > α_S“. Er gilt bedingt
     auf n_t und damit auch unbedingt.
   - Bei weniger als `minAnswered` = 30 Antworten wird p = 1 gesetzt. Das bleibt ein gültiger p-Wert; solche Schwellen
     werden nur nie zertifiziert.
3. **Gemeinsam mit I1 (optional):** p_M(t) = P(Bin(n, α_M) ≤ k_t).
   - Für die Vereinigungs-Nullhypothese „R > α_M **oder** S > α_S“ ist max(p_S, p_M) ein gültiger p-Wert.
4. **Holm-Korrektur über alle 101 Schwellen.**
   - Sie kontrolliert die familienweise Fehlerrate δ bei *beliebiger* Abhängigkeit zwischen den Schwellen.
   - Mit Wahrscheinlichkeit ≥ 1 − δ erfüllt damit **jede** zertifizierte Schwelle zugleich ihre Schranke(n).
   - Zurückgegeben wird die großzügigste zertifizierte Schwelle, also die mit der größten Abdeckung. Monotonie von
     S(t) ist dafür nicht nötig.
5. **Warum nicht die feste Reihenfolge von I1?**
   - Von streng nach großzügig geordnet haben die ersten Schwellen fast keine Antworten. Dort ist p = 1, und die Folge
     bräche sofort ab.
   - Holm braucht keine Reihenfolge.
6. **Bezug zu SGR (Geifman & El-Yaniv 2017).**
   - Der p-Wert-Test ist genau dieselbe Entscheidung wie die einseitige Clopper-Pearson-Schranke: p ≤ Niveau genau
     dann, wenn die Schranke ≤ α ist. Ein Test prüft das für 400 Zufallsfälle.
   - SGR sucht binär mit δ/⌈log₂ m⌉ je besuchter Schwelle. Weil die besuchten Schwellen vom Ergebnis der vorherigen
     Tests abhängen, braucht die Union-Bound-Begründung zusätzliche Annahmen (monotones Risiko).
   - SGR wird deshalb nur als Vergleich gemessen; das Zertifikat nutzt Holm.
7. **Ausführung.**
   - `answer(model, evidence, question, certificate)` ist fail-closed und an den Checkpoint-SHA gebunden.
   - Hash und Vorhersage laufen unter **einer** Modellsperre, wie in PR #68.
   - Ohne Zertifikat oder bei abweichendem SHA kommt keine Antwort.

**Grenzen der Garantie:**
- Sie gilt nur für Fragen, die mit der Kalibrierung austauschbar sind.
- Abhängigkeit zwischen ähnlichen Fragen und Verteilungsverschiebung sind **nicht** bewiesen; beides wird getrennt
  gemessen (Abschnitte 6 und 7).
- Ein `Certificate` lässt sich weiterhin von Hand bauen. Es ist kein kryptographisch attestiertes Kalibrierergebnis.

## 4. Tests

`ContinuumSelectiveRiskCertificationTest`, 9 Tests; zusammen mit den bestehenden span-Tests ergibt das 26:

| Test | Prüft |
|---|---|
| `clopperPearsonUpperBoundIsExact` | 7 Fälle gegen eine BigDecimal-Binomialverteilung (relativ ≤ 1e-8); geschlossene Formen für k = 0 und k = n |
| `pValueTestAndClopperPearsonBoundMakeTheSameDecision` | 400 Zufallsfälle |
| `holmIsStepDownAndStopsAtTheFirstFailure` | Rangfolge, kritische Werte, Abbruch beim ersten Misserfolg |
| `tooFewAnswersFailClosed` | 20 Antworten reichen nicht; ohne Zertifikat kommt keine Antwort |
| `marginalCertificatePassesWhileHalfOfTheAnswersAreWrong` | siehe Abschnitt 2 |
| `jointModeAlsoRequiresTheMarginalBound` | Gemeinsamer Modus verlangt beide Schranken |
| `guaranteeHoldsInSimulation` | 2.000 Läufe: Verletzungsrate ≤ δ + 0,02, Zertifikat in ≥ 95 % der Läufe, Schwelle nicht trivial streng |
| `naiveThresholdSelectionViolatesFarMoreOftenThanDelta` | Das Mehrfachtest-Problem wird sichtbar |
| `certificateIsBoundToTheExactCheckpoint` | anderes Modell oder ein Trainingsschritt mehr → keine Antwort |

Befehl (im Verzeichnis `app/src`):

```sh
kotlinc main/java/at/heinz/deviceai/learning/span/*.kt test/java/at/heinz/deviceai/learning/span/*.kt \
  -cp junit-4.13.2.jar:hamcrest-core-1.3.jar -jvm-target 17 -include-runtime -d csx1.jar        # Exitcode 0
java -cp csx1.jar:junit-4.13.2.jar:hamcrest-core-1.3.jar org.junit.runner.JUnitCore \
  at.heinz.deviceai.learning.span.ContinuumResidualSpanCoreTest \
  at.heinz.deviceai.learning.span.ContinuumResidualSpanCoreVariantsTest \
  at.heinz.deviceai.learning.span.ContinuumResidualSpanCoreSpanTest \
  at.heinz.deviceai.learning.span.ContinuumSpanAbstentionBenchTest \
  at.heinz.deviceai.learning.span.ContinuumCertifiedAbstentionTest \
  at.heinz.deviceai.learning.span.ContinuumSelectiveRiskCertificationTest                       # OK (26 tests), Exitcode 0
java -cp csx1.jar at.heinz.deviceai.learning.span.ContinuumSelectiveRiskExperiment            # Exitcode 0, 105 Zeilen
java -cp csx1.jar at.heinz.deviceai.learning.span.ContinuumSpanAbstentionExperiment certify   # Exitcode 0, bytegleich mit I1
```

## 5. Mehrfachtest-Problem in der Simulation

Synthetisches Modell: wahres S(t) = 0,1 (1 − t)²; n = 3.000 Kalibrierfragen; 2.000 Läufe; δ = 0,1.

| Regel | α = 0,05: Verletzungen | Zertifikat | wahre Abdeckung | α = 0,02: Verletzungen | Zertifikat | wahre Abdeckung |
|---|---:|---:|---:|---:|---:|---:|
| **Holm (I2-Zertifikat)** | **0,05 %** | 100 % | 0,461 | **0,0 %** | 65,6 % | 0,206 |
| Bonferroni | 0,0 % | 100 % | 0,452 | 0,0 % | 65,6 % | 0,205 |
| SGR binär (Vergleich) | 0,95 % | 100 % | 0,487 | 0,65 % | 85,7 % | 0,242 |
| unkorrigiert p ≤ δ je Schwelle | 7,65 % | 100 % | 0,519 | 8,2 % | 100 % | 0,292 |
| naiv „empirisch ≤ α“ | **49,3 %** | 100 % | 0,562 | **50,6 %** | 100 % | 0,355 |

Die Orakel-Abdeckung beträgt 0,566 (α = 0,05) und 0,358 (α = 0,02).

**Deutung:**
- Die naive Wahl der besten Schwelle verletzt die Zusage in etwa jedem zweiten Lauf.
- Ohne Mehrfachkorrektur steigt die Verletzungsrate deutlich, liegt hier aber noch unter δ.
- Holm hält die Zusage. Der Preis ist Abdeckung: 0,46 statt 0,57 bei α = 0,05. Bei α = 0,02 entsteht nur in 66 % der
  Läufe überhaupt ein Zertifikat.

## 6. Kalibrierumfang

Mindestzahl **beantworteter** Kalibrierfragen, damit der erste Holm-Schritt (δ/101) α zertifizieren kann:

| α | bei 0 beobachteten Fehlern | bei Fehlerquote α/4 | bei Fehlerquote α/2 |
|---|---:|---:|---:|
| 0,10 | 66 | 108 | 277 |
| 0,05 | 135 | 221 | 590 |
| 0,02 | 343 | 558 | 1.486 |
| 0,01 | 689 | 1.120 | 2.979 |

V8 hat bei strengen Schwellen weniger Antworten als nötig und nicht null Fehler. Deshalb gibt es für α ≤ 0,02 kein
Zertifikat. Mehr Kalibrierdaten oder ein besseres Modell wären nötig; eine lockerere Rechnung ist keine Lösung.

## 7. Messung an V8 (`1786fa9e…`, dieselbe Kalibrierung und derselbe Holdout wie `certify`)

| Zertifikat (δ = 0,1) | Schwelle | Kalibrierung: beantwortet / Abdeckung | Fehler (selektiv) | p / Holm-Niveau | Bonferroni-Schranke | Holdout selektiv [95 %] | Holdout-Abdeckung |
|---|---:|---|---|---|---:|---|---:|
| selektiv α_S = 0,10 | 0,57 | 609/1.700 = 35,8 % | 38 = 6,2 % | 6,8e-4 / 1,6e-3 | 9,9 % | 5,97 % [4,34; 8,15] | 35,5 % |
| selektiv α_S = 0,05 | 0,74 | 513/1.700 = 30,2 % | 11 = 2,1 % | 7,7e-4 / 1,05e-3 | 4,9 % | 3,25 % [2,04; 5,14] | 30,8 % |
| selektiv α_S = 0,02 | – | **kein Zertifikat → immer Enthaltung** | | | | | |
| selektiv α_S = 0,01 | – | **kein Zertifikat → immer Enthaltung** | | | | | |
| gemeinsam α_M = 0,05 / α_S = 0,10 | 0,57 | wie selektiv 0,10; Gesamtfehler 2,2 % | | | | | |
| gemeinsam α_M = 0,02 / α_S = 0,05 | 0,74 | wie selektiv 0,05; Gesamtfehler 0,65 % | | | | | |
| gemeinsam α_M = 0,02 / α_S = 0,02 | – | kein Zertifikat | | | | | |

**Neuaufteilungen auf Ziehungsebene:** 40 weitere Ziehungen mit 6.800 Fragen, 200 zufällige 20/20-Aufteilungen ganzer
Ziehungen, Kalibrierung jeweils mit etwa 3.400 Fragen.

| Regel | α_S = 0,10 | α_S = 0,05 | α_S = 0,02 | α_S = 0,01 |
|---|---|---|---|---|
| I2-Zertifikat (Holm): Verletzungen / ohne Zertifikat | 0/200 / 0 | 0/200 / 0 | 0/200 / 94 | 0/200 / 200 |
| Bonferroni | 0 / 0 | 0 / 0 | 0 / 94 | 0 / 200 |
| SGR binär | 0 / 0 | 0 / 0 | 0 / 3 | 0 / 199 |
| unkorrigiert | 4/200 | 14/200 | 2/200 | 1/200 |
| naiv | **75/200** | **106/200** | **57/200** | **52/200** |

Mittlerer selektiver Holdout-Fehler des I2-Zertifikats: 7,45 % (α_S = 0,10), 2,89 % (α_S = 0,05) und 0,55 % (α_S =
0,02, in den 106 Aufteilungen mit Zertifikat). Eine „Verletzung“ bedeutet, dass die *Schätzung* auf dem Holdout über α
liegt. Diese Schätzung ist selbst verrauscht; sie ist eine empirische Prüfung, kein Beweis.

**Abhängigkeit zwischen Fragen:**
- Die Pearson-Dispersion der Fehlerzahlen je Ziehung bei der zertifizierten Schwelle beträgt φ = 0,45 (α_S = 0,10) und
  φ = 0,44 (α_S = 0,05).
- φ < 1 heißt: Die Fehler verteilen sich *gleichmäßiger* auf die Ziehungen als bei einer Binomialverteilung. Es gibt
  keinen Hinweis auf Fehlerhäufung innerhalb einer Ziehung.
- Mit der Korrektur n/max(1, φ) bleiben beide Zertifikate gültig.

## 8. Bekannter eingefrorener TEST: einmal je Strategie, Verteilungsverschiebung

Das sind die 264 Fragen mit `974e4ba3…`. Sie werden seit Zyklus 1 als Evaluationsmenge verwendet und sind **keine
neue Blindprobe**. Keine Wahl in diesem Dokument hängt vom TEST ab.

| Strategie | richtig /124 | selektiver Fehler [95 %] | Abdeckung | unbelegt /140 | falsche Spanne | gesehene Formen | ungesehene Formen |
|---|---:|---|---:|---:|---:|---|---|
| I1 argmax (Bezug, `certify`-Log) | 99 | 16/115 = 13,9 % | 43,6 % | 6 | 10 | 6/93 | 10/22 |
| I1 α = 0,02 (Bezug) | 78 | 3/81 = 3,7 % | 30,7 % | 2 | 1 | 2/78 | 1/3 |
| **I2 α_S = 0,10** | 86 | 4/90 = **4,4 %** [1,7; 10,9] | 34,1 % | 3 | 1 | 3/85 = 3,5 % | **1/5 = 20 %** [3,6; 62,5] |
| **I2 α_S = 0,05** | 71 | 2/73 = **2,7 %** [0,75; 9,45] | 27,7 % | 2 | **0** | 2/73 = 2,7 % | 0 beantwortet |

**Deutung:**
- Auf den gesehenen Satzformen bleibt die Antwortqualität innerhalb der Zusage.
- Auf ungesehenen Formen antwortet das Modell bei α_S = 0,10 nur 5-mal, davon 1-mal falsch. Bei α_S = 0,05 enthält es
  sich dort ganz.
- Unter Verteilungsverschiebung gilt **keine** Garantie. Neue Fragearten müssen neu kalibriert werden.

## 9. Negative Befunde und Grenzen

1. **α_S ≤ 0,02 ist mit V8 nicht zertifizierbar** (1.700 Kalibrierfragen). Auf ungesehenen Formen bringt das
   Zertifikat keine Fähigkeit; es enthält sich nur ehrlich. Die bekannte Grenze von 12/35 bleibt unverändert.
2. **Abdeckung ist der Preis.** I2 α_S = 0,05 beantwortet 30 % der Kalibrierfragen und 71 von 124 beantwortbaren
   TEST-Fragen; I1 α = 0,02 beantwortet 78.
3. **Holm bei 101 Schwellen ist konservativ.** SGR wäre bei α = 0,02 stärker (Zertifikat in 85,7 % statt 65,6 % der
   Simulationsläufe), braucht aber Monotonie-Annahmen. Eine gröbere, vorab festgelegte Schwellenliste wäre ein
   gültiger Mittelweg; das ist nicht umgesetzt.
4. **Die Garantie setzt Austauschbarkeit der Fragen voraus.** Die Dispersion ist nur eine Diagnose.
5. **Gemessen nur auf der JVM (x86_64).** Keine Messung auf ARM64 oder einem Gerät, keine App-Integration.

## 10. Übergabe

Nächste Schritte, die jede KI vom letzten gepushten SHA dieses Zweigs aus angehen kann:
- Gröbere, vor der Kalibrierung festgelegte Schwellenliste (z. B. 0,00 / 0,05 / … / 0,95) gegen 101 Schwellen messen.
- Kalibrierumfang erhöhen, indem mehr Validierungsziehungen generiert werden. Prüfen, ob α_S = 0,02 dann
  zertifizierbar wird. Der TEST darf dafür nicht verwendet werden.
- Für neue Fragearten wie SYNAPSE-ZERO oder neue Satzfamilien eigene Kalibrierungen anlegen.
- Erst nach Besitzerfreigabe: einen schreibgeschützten Prüfstand in der App, der Zertifikat, Abdeckung und
  selektiven Fehler anzeigt. Keine stille Promotion in den Chat.

**Quellen:**
- Angelopoulos, Bates, Candès, Jordan, Lei: *Learn then Test*, arXiv:2110.01052.
- Geifman, El-Yaniv: *Selective Classification for Deep Neural Networks*, NeurIPS 2017.
- Holm: *A simple sequentially rejective multiple test procedure*, Scand. J. Statist. 1979.
- Clopper, Pearson, Biometrika 1934.
