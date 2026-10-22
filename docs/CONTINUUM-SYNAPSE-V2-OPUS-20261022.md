# EXP-SYNAPSE-ZERO v2 – Prüfung, neuer eingefrorener Benchmark und Ablationen (Opus, 22.10.2026)

**Zweig:** `opus/continuum-synapse-v2-20261022`, abgeleitet von PR #71 (`experiment/continuum-synapse-zero-20261022`, `2b58ed17`).
Der Stand ist reine Forschung im Schatten: keine Gewichts-Promotion, keine Android-UI-Anbindung, kein Merge nach `main`.
SWB1-, CDX1- und CSX1-Gewichte sowie Checkpoints werden weder gelesen noch verändert. Eine Ausnahme ist der dokumentierte,
rein lesende CSX1-V8-Vergleich in einem separaten Scratch-Build.

Status: **in Arbeit**. Dieses Dokument wird mit jedem gepushten Teilschritt fortgeschrieben.

## 1. Reproduktion PR #71 (echtes JUnit 4.13.2, kein Shim)

| Schritt | Befehl (aus `app/src`) | Exitcode | Ergebnis |
|---|---|---|---|
| Kompilieren | `kotlinc main/java/at/heinz/deviceai/learning/synapse/*.kt test/java/at/heinz/deviceai/learning/synapse/*.kt -cp junit-4.13.2.jar:hamcrest-core-1.3.jar -jvm-target 17 -include-runtime -d pr71.jar` (kotlinc 2.0.20, JDK 21) | 0 | – |
| JUnit | `java -cp pr71.jar:junit-4.13.2.jar:hamcrest-core-1.3.jar org.junit.runner.JUnitCore …ContinuumSynapseZeroShadowTest …SynapseContrastiveRoleProbeTest` | 0 | **OK (10 tests)** |
| DEV-Probe | `java -cp pr71.jar at.heinz.deviceai.learning.synapse.SynapseContrastiveShadowBenchmark` | 0 | Seeds 47/99/20261022: vorher 1/4, 1/4, 4/4 → nachher 4/4, 4/4, 4/4 (je 1.080 Updates) |

Damit sind die Angaben aus PR #71 exakt reproduziert, diesmal mit echtem JUnit.
Logs: `docs/evidence/synapse-v2-20261022/pr71-*.txt`. Die vier DEV-Fälle bleiben **DEV** und sind kein Blindtest.

## 2. Architekturprüfung v1 (gemessen, `SynapseV1ReviewDiagnostics`, Log `review-v1.txt`)

| Nr. | Befund | Messung | Bedeutung |
|---|---|---|---|
| R1 | Die Rolle ist `e_1` (one-hot). „Unbind“/„bind“ ist damit eine **feste zyklische Verschiebung**: score = Σ_k q[k]·c[(k+1) mod 16]. | max. Abweichung **0,0** | Die „holographische Rollenbindung“ ist mathematisch ein gewöhnliches bilineares Skalarprodukt. Sie bindet keine Rollen. |
| R2 | Der analytische Gradient von `trainPair` stimmt. | max. Fehler **7,9·10⁻¹¹** über 2.800 Parameter (zentrale finite Differenzen) | Die Implementierung des paarweisen Softmax-Gradienten ist korrekt. |
| R3 | Zeichen-2–4-Gramme werden in 128 Buckets gehasht. | Belegung 62–98 von 128 je Satz; positiver und negativer Satz teilen 43–83 Buckets (Jaccard 0,52–0,75) | Starke Überlappung. Die Unterscheidung hängt an wenigen nicht geteilten Buckets. |
| R4 | Die Kennungs-Regex macht **jede** ID zu `subj_slot`. | \|score(R10-Satz) − score(R99-Satz)\| = **0,0** bei Frage nach R10 | Mehrentitäten-Bindung ist konstruktionsbedingt unmöglich. |
| R5 | Die Begrenzung `coerceIn(-2, 2)` würde exakte Gradienten brechen. | 0 Parameter nach 4.000 Updates mit lr 0,5 an der Grenze | Sie greift praktisch nicht; das ist nur ein theoretischer Befund. |
| R6 | Sinkhorn erzwingt eine 1:1-Zuordnung. | Zwei Roboter in derselben Halle: Rohpräferenz 0,9 → nach Sinkhorn **0,5/0,5**; ein Gleichstand bleibt 0,5/0,5 | Bei nicht-injektiven Relationen ist Sinkhorn schädlich und entscheidet keine Gleichstände. |
| R7 | Adversariale Fälle gegen den Beweiskern (13 Fälle) | 5 OK, **4 unbelegte Antworten**, **4 unbegründete Enthaltungen** | siehe unten |

R7 im Einzelnen:
- **Unbelegte Antworten** (Sicherheitslücken):
  - Verneinung mit „nie“ (nicht in der Vetoliste);
  - Präfix „Falsch:“;
  - verneinte Frage („… benötigt Z77 nicht?“);
  - „Wo steht das Modul von Z77?“ wird mit dem Standort des Roboters beantwortet.
- **Unbegründete Enthaltungen:**
  - eine Verneinung in einem fremden Satz blockiert global;
  - Kette im selben Satz;
  - Passiv;
  - Zeitbezug „früher/heute“.
- **Korrekt:** falsche Brücke, „niemals“ (nur weil die Regex-Wortfolge bricht), zwei Subjekte in einem Satz, kyrillisches Homoglyph-„М4“, Präfix-ID „M4“ vs. „M40“.

## 3. Neuer Benchmark `SynapseV2Bench` (nur Testquellen, eingefroren)

**Neues Material.** Nichts davon kommt im CSX1-TEST (`974e4ba3…`) oder in der PR-#71-DEV-Probe vor.
- **Relationen:** Standort, Energiequelle, Sensor, Material, Betreiber, außerdem Linie als Brücke für 2-Hop-Ketten.
- **Kennungen:** Format `XY-123`; Buchstaben und Ziffernbereiche sind je Aufteilung disjunkt. Brücken heißen `LN-123`.
- **Werte:** je Aufteilung disjunkt, z. B. Orte auf -feld/-ingen/-bach (TRAIN), -hausen/-dorf (VALIDATION) und -stedt/-rode/-heim (TEST).
- **Formen:** VALIDATION und TEST haben je eigene Satzfamilien und Frageformen. Jede neue TEST-Form trägt eine Markierung:
  - `MORPH`: teilt einen Wortstamm mit TRAIN;
  - `LEXICAL`: echtes neues Wort;
  - `ORDER`: neue Wortstellung;
  - `UNSEEN_CUE`: unbekanntes Negations- oder Zeitwort.

| Aufteilung | Fälle | SHA-256 |
|---|---|---|
| TRAIN | 780 | `60d7c341…` |
| VALIDATION | 322 | `4b9c3c1d…` |
| **TEST (eingefroren)** | **592** | **`18640df7caf1462b405d150145cbae9a879877984f1e19d39785b0b5aee53320`** |

**Kategorien:**
- beantwortbar: Antwort, Kontrast „nicht A, sondern B“, Verneinung in fremdem Satz, mehrere Entitäten (eigene Sätze, ein Satz, gemeinsamer Wert), Zeitbezug, 2-Hop, 2-Hop mit Ablenkern;
- unbeantwortbar: andere Relation, fremde Entität, unbekannte Relation (nur TEST), Verneinung, Verneinung mit unbekanntem Wort (nur TEST), gebrochene Brücke, widersprüchliche Kette, verneinte Brücke.

**Leckagekontrolle** (`benchmark-stats.txt` und JUnit-Gate):
- Überlappung von Entitäten und Werten zwischen allen drei Aufteilungen: **0**;
- zurückgehaltene TEST- und VALIDATION-Vorlagen in TRAIN: **0**;
- Wort-Trigramm-Überlappung der Belegtexte mit TRAIN: VALIDATION 4 %, TEST 3 %.

**Ehrlicher Vermerk zum Einfrieren:**
- Der TEST war zuerst unter `c565a541…` eingefroren.
- VALIDATION zeigte dann einen Generatorfehler, der alle Aufteilungen betraf: Satzform und Frageform wurden zyklisch so gekoppelt, dass z. B. „befindet sich“ in TRAIN nie mit „Wo steht“ vorkam.
- Der Generator wurde korrigiert und der TEST einmalig neu eingefroren (`18640df7…`). **Zu diesem Zeitpunkt hatte kein Modell den TEST gesehen.**
- Das alte VALIDATION-Protokoll liegt als `cycle1-…-OBSOLETE-pairing-bug.txt` bei.

## 4. Modelle (`SynapseV2Reasoner`, Hauptquelle, nur JVM und Schatten)

**Gelernt** (Softmax über alle Belegtokens plus „Enthalten“, exakte analytische Gradienten):
- gehashte Wort- und Zeichen-Trigramm-Einbettungen für Fragewörter (Q) und Belegfüller (F);
- Rollenvektoren, die jeden Füller über exakte zirkuläre Faltung an seine Position relativ zum Kandidaten binden (HRR). Bei BAG ist jede Rolle die Identität;
- zwei Sonderfüller, die die gefragte Entität von jeder anderen Kennung trennen;
- Brückenvektor und Skalargewichte für 2-Hop, Enthaltungsgewichte.

Die Gradienten sind gegen finite Differenzen geprüft (HRR, BAG, gebundene Einbettungen).

**Fest und offengelegt:**
- Tokenisierung, Satz- und Teilsatzgrenzen, Kennungsform, Kandidatenbildung;
- eine kleine grammatische Stoppwortliste, nur aus dem TRAIN-Vokabular;
- Entscheidungsregeln, die auf VALIDATION gewählt werden: Schwelle und Konfliktverhältnis;
- der **strukturelle Beweis**:
  - exakter Token an geprüften UTF-16-Offsets;
  - SHA-256 des Belegtexts;
  - 1-Hop: Der Antwortsatz nennt die gefragte Entität;
  - 2-Hop: Die Brückenkennung steht wörtlich in zwei verschiedenen Sätzen.

Ein Score kann damit keinen Text außerhalb des Belegs ausgeben. Der Beweis prüft **Quelltreue und Kettenintegrität, nicht die Wahrheit**.

| Variante | Beschreibung |
|---|---|
| A0 | v1-Beweiskern aus PR #71 (nur Regeln) |
| A0b | Oberflächenheuristik: letztes Inhaltswort im Satz der Entität |
| U | A4-Architektur untrainiert (3 Initialisierungen) |
| A1 | bilinear, Bag ohne Bindung, nur 1-Hop |
| A2 | HRR-Rollenbindung, nur 1-Hop |
| A3 | A2 + Sinkhorn-Wettbewerb der Entitäten (nur Inferenz) |
| A4 | A2 + gelernte, beweisgebundene 2-Hop-Ketten |
| A4b, A4bn, A4n, A4t, A4bt | Bindungs- und Kandidatenmerkmal-Ablationen (b = Bag, n = ohne, t = nur Trigramme) |
| S | nur Skalarmerkmale (Einbettungen eingefroren auf 0) |
| A5, A5h | gebundene („siamesische“) Frage- und Beleg-Einbettungen (Bag bzw. HRR) |

Modellgröße: A4 hat 65.753 Parameter (2 × 2.048 Buckets × 16 Dimensionen + Rollen und Skalare).
Zum Vergleich: CSX1 hat 4.110 Parameter, PR-#71-Probe 4.096.

## 5. Protokoll

- **Training:**
  - nur TRAIN;
  - 3 Initialisierungen bzw. Datenreihenfolgen (Seeds 1, 2, 3);
  - SGD, Lernrate 0,05, L2 1e-5 auf berührten Zeilen.
- **Auswahl** von Epochen, Rezept, Variante, Schwelle und Konfliktverhältnis: **nur auf VALIDATION**.
  Kriterium: richtig − 2 × (falsche Antwort + unbelegte Antwort).
- **TEST:** wird erst danach **einmal** für alle Varianten gemessen; der Kandidat ist vorher festgelegt.
- **Zeitmessung:** ms je Frage auf JVM x86_64, nicht ARM64.

## 6. Auswahlzyklen auf VALIDATION (Mittel über 3 Seeds, mit auf VALIDATION gewählter Regel)

| Zyklus | Variante | Epochen/Rezept | richtig /187 | Fehler /322 | Score (richtig − 2·Fehler) |
|---|---|---|---|---|---|
| 2 | A1 Bag, 1-Hop | 8 / R0 | 109,3 | 8,0 | 93,3 |
| 2 | A2 HRR, 1-Hop | 8 / R0 | 106,7 | 3,7 | 99,3 |
| 2 | A3 A2 + Sinkhorn | 8 / R0 | 76,3 | 13,7 | 48,9 |
| 2 | A4 HRR + 2-Hop | 8 / R0 | 129,7 | 8,0 | 113,7 |
| 2 | A4b Bag + 2-Hop | 8 / R0 | 133,3 | 11,7 | 110,0 |
| 2 | A4n HRR + 2-Hop, ohne Kandidatenmerkmal | 8 / R0 | 136,7 | 9,7 | 117,3 |
| 2 | **A4bn Bag + 2-Hop, ohne Kandidatenmerkmal** | **8 / R0** | **141,3** | **8,7** | **124,0** |
| 2 | A4bn | 25 / R0 | 126,0 | 8,7 | 108,7 |
| 2 | A4bn | 8 / R2 (Kunstwerte + Frage-Dropout) | 148,3 | 13,3 | 121,7 |
| 2 | S nur Skalare | 8 / R0 | 69,0 | 0,0 | 69,0 |
| 3 | A5 gebundene Einbettungen (Bag) | 8 / R0 | 141,3 | 14,3 | 112,7 |
| 3 | A5h gebundene Einbettungen (HRR) | 8 / R0 | 123,7 | 8,0 | 107,7 |
| 3 | A4bn | 15 / R0 | 134,0 | 10,7 | 112,7 |

Untrainiert (U) und Heuristiken auf VALIDATION:
- U: 12–28/187 richtig, 182–210 Fehler;
- A0b: 110 richtig, 188 Fehler;
- A0 (v1-Regeln): 3 richtig, 0 Fehler.

Die Leistung stammt damit vollständig aus den trainierten Gewichten.

**Vorab festgelegt (vor dem TEST-Lauf, eigener Commit):**
- **Kandidat:** A4bn, 8 Epochen, Rezept R0, Schwelle und Konfliktverhältnis aus VALIDATION.
- **Alle anderen Varianten** werden mit derselben Trainingseinstellung einmal auf TEST mitgemessen, nur zum Vergleich.
- **Negative Befunde schon auf VALIDATION:**
  - HRR-Rollenbindung ohne belastbaren Vorteil gegenüber Bag;
  - Sinkhorn verdreifacht die falschen Antworten;
  - gebundene Einbettungen und 25 Epochen verschlechtern;
  - R2 bringt mehr richtige, aber auch mehr Fehler.

## 7. Einmaliger TEST-Lauf (vorab festgelegt) – Ergebnis

**Durchführung**
- Quelle: Commit `61d6009e` (Vorab-Festlegung), sauberer Arbeitsbaum.
- Kompiliert mit kotlinc 2.0.20; JUnit 4.13.2: 18/18 Tests OK.
- Befehl: `SynapseV2Experiment final epochs=8`, Rezept R0, Seeds 1–3, alle Varianten und Baselines.
- Der Runner prüft vor der Auswertung den TEST-Hash `18640df7…`.
- Lauf am 22.10.2026, 15:20:05–15:21:33 UTC, Exitcode 0.
- Vollständiges Protokoll: `docs/evidence/synapse-v2-20261022/final-test-e8-once.txt`.
- Der TEST wurde genau einmal ausgewertet. Danach gab es keine Änderung an Varianten, Regeln oder Code.

**Vorab festgelegter Kandidat A4bn** (Bag + 2-Hop, ohne Kandidatenmerkmal, Regel aus VALIDATION)

| Seed | richtig /304 | falsch | unbelegte Antwort /288 | Fehler /592 | Score (richtig − 2·Fehler) |
|---|---:|---:|---:|---:|---:|
| 1 | 152 | 7 | 47 | 54 | 44 |
| 2 | 148 | 6 | 38 | 44 | 60 |
| 3 | 111 | 5 | 35 | 40 | 31 |
| **Mittel** | **137,0 (45 %)** | **6,0** | **40,0 (13,9 %)** | **46,0** | **45,0** |

Auf VALIDATION hatte derselbe Kandidat einen Score von 124,0 bei 8,7 Fehlern auf 322 Fällen. **Die Auswahl hat sich nicht auf TEST übertragen.**

**Fehleranalyse** (Seed 1, Regel aus VALIDATION)
- Bekannte Satzformen (`SEEN`): 75/75 richtig, 3/57 unbelegte Antworten.
- Neue Formen:
  - nur Beleg neu (`NEW_EVIDENCE`): 29/80;
  - nur Frage neu (`NEW_QUESTION`): 33/75;
  - beides neu (`NEW_BOTH`): 15/74.
- Herkunft der 47 unbelegten Antworten:
  - 28 aus **Verneinung mit unbekanntem Verneinungswort** (`NEGATED_UNSEEN_CUE`, 28/48);
  - 8 aus widersprüchlichen 2-Hop-Ketten (8/12);
  - 8 aus fremder Relation (8/96);
  - 2 aus unbekannter Relation;
  - 1 aus gebrochener Brücke.

**Vergleich aller Varianten auf TEST** (Mittel aus 3 Seeds, Regel aus VALIDATION). *Nur Beobachtung, keine Auswahl.*

| Variante | richtig /304 | falsch | unbelegt /288 | Score |
|---|---:|---:|---:|---:|
| A4n HRR + 2-Hop, ohne Kandidatenmerkmal | 140,7 | 9,7 | 25,0 | **71,3** |
| A4 HRR + 2-Hop | 115,3 | 6,7 | 18,0 | 66,0 |
| A4t HRR + 2-Hop, nur Trigramme | 118,3 | 8,3 | 19,3 | 63,0 |
| S nur Skalare | 91,7 | 4,0 | 14,0 | 55,7 |
| A5h gebundene Einbettungen (HRR) | 124,0 | 7,7 | 30,7 | 47,3 |
| A2 HRR, 1-Hop | 95,3 | 9,3 | 15,7 | 45,3 |
| **A4bn (Kandidat)** | **137,0** | **6,0** | **40,0** | **45,0** |
| A4bt Bag, nur Trigramme | 134,7 | 9,7 | 40,3 | 34,7 |
| A4b Bag + 2-Hop | 123,3 | 14,0 | 34,0 | 27,3 |
| A1 Bag, 1-Hop | 104,3 | 13,0 | 27,0 | 24,3 |
| A5 gebundene Einbettungen (Bag) | 142,3 | 16,0 | 44,3 | 21,7 |
| A3 A2 + Sinkhorn | 66,7 | 12,3 | 11,7 | 18,7 |
| A0 v1-Regeln | 2 | 0 | 0 | 2 |
| U untrainiert (argmax) | 29,0 | 214,0 | 173,3 | −745,7 |
| A0b Oberflächenheuristik | 151 | 153 | 252 | −659 |

Bei unbekannten Verneinungswörtern (Seed 1) gab A4n 13/48 unbelegte Antworten, A4bn 28/48. Bei fremder Relation waren es 1/96 gegenüber 8/96.

**Bewertung**
1. **Ergebnis des vorab festgelegten Kandidaten:** Score 45,0. Er gibt in 13,9 % der unbeantwortbaren TEST-Fälle eine unbelegte Antwort und ist **nicht** für eine Übernahme geeignet. Es wird nichts befördert; App und Gewichte bleiben unverändert.
2. **Ursache der Übertragungslücke:** VALIDATION enthielt die TEST-eigenen Verschiebungen nicht, also keine unbekannten Verneinungswörter und keine unbekannte Relation. Die Auswahl belohnte damit Abdeckung in der VALIDATION-Verteilung statt Robustheit.
3. **Beobachtung nach dem Lauf (nicht bestätigt):** HRR-Rollenbindung erzeugt unter Verschiebung weniger unbelegte Antworten als Bag (A4n 71,3 gegenüber A4bn 45,0). Das widerspricht dem VALIDATION-Befund aus Abschnitt 6. Weil der TEST jetzt verbraucht ist, bleibt das eine **Hypothese**. Sie braucht einen neuen eingefrorenen TEST.
4. **Kernschwäche:** Ungesehene Satzformen bleiben die größte Schwäche, wenn Frage und Beleg zugleich neu sind (A4bn 15/74, A4n 6/74). Das passt zur bekannten CSX1-Grenze von 12/35.

**Folgen für weitere Experimente**
- Der TEST `18640df7…` ist **verbraucht**. Er darf nie wieder zur Auswahl oder Abstimmung dienen, höchstens als historischer Vergleich.
- Ein nächster Zyklus braucht:
  - eine Verschiebungs-VALIDATION mit zurückgehaltenen Verneinungs- und Zeitwörtern und eigenen Relationen;
  - einen neuen eingefrorenen TEST mit anderen Wörtern;
  - die Vorab-Festlegung, ob HRR oder Bag gewählt wird.
