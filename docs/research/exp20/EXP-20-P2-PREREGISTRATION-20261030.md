# EXP-20 · P2 · Vorregistrierung (vor dem ersten Lauf committet)

**Stand:** 30.10.2026 · **Branch:** `claude/continuum-jarvis-exp20-p2-kxrxy2` · **Basis:** #253 `ed408a7d921e47b5730b06f1f514268e249bd5f7`.

Dieses Dokument wird committet und gepusht, **bevor** ein Arm auf irgendeinem Datensatz ausgewertet wird. Bis zu diesem Commit liefen ausschließlich Infrastrukturtests (Statistik gegen Handwerte, Encoder-Cues, Zeitlogik, Router-Invariante, Generator-Determinismus) sowie die Datengenerierung mit Zählstatistik.

Die Teile N (Numerik) und K (Kosten) sind hier mitregistriert und laufen **getrennt** vom Qualitätsvergleich.

## 1. Frage, einzige Eingriffsvariable, Hypothesen

**Frage:** Verbessert ein zeitlicher Zustand, der auf der monotonen Sitzungsuhr läuft, die eine entscheidungsrelevante Stelle des realen Routers? Die Stelle ist `ComputeAllocationPolicy.Signals.unresolvedReference`; ist sie gesetzt, wird `CLARIFY` zulässig und `MODEL_*` unzulässig. Gemessen wird bei zeitabhängigem Kontext, also bei der Entscheidung zwischen einer begründeten Rückfrage und dem normalen Weiterarbeiten.

**Einzige Eingriffsvariable:** der Zustandskern, der den Readout speist (keiner / LeakyIntegrators / P1-BoundedCtrnn). Gleich bleiben Encoder, Ereignisprotokoll, Kanäle, τ, Readout-Architektur, Tuning-Raster, Labeldefinition, Zeitbasis, Router-Code und Signale.

| Arm | Rolle | Definition |
|---|---|---|
| **A** | primär, Baseline | realer `ComputeAllocationPolicy.choose()`-Pfad mit Signalen wie in `ChatReasoningRouter.advise` (Blob `f21a114`); `unresolvedReference=false` |
| **B** | primär | P1-`LeakyIntegrators` (16 Kanäle), Readout → `unresolvedReference` |
| **C** | primär | unverändertes P1-`BoundedCtrnn` (16 Neuronen, dicht, Exponential-Euler, ρ = 0,9, b = 0, W-Seed 2026103002), gleicher Readout |
| A0 | Kontrolle | gleicher Readout nur auf [aktueller Turn, ln(1+Δt/60 s)], **ohne** Zustand. Prüft, ob ein Gewinn aus der Zeitintegration kommt. |
| A_DSK | Kontrolle | bereits vorhandener, unverdrahteter `DialogStateKernel` (turn-basiert, ohne Uhr) → `unresolvedReference` |

- **H-P2-1 (CTRNN):** C löst auf dem versiegelten Holdout mehr unabhängige Dialogstämme als A **und** als B. Kriterium je Paar: Urteil `SUPERIOR` (Abschnitt 5).
- **H-P2-2 (EMA):** B ist gegenüber A `SUPERIOR`.
- **H-P2-3 (Attribution, sekundär):** Der gewählte zeitliche Arm ist gegenüber A0 `SUPERIOR`.

**Falsifikation:** Ist C gegenüber B nicht `SUPERIOR`, gilt H-P2-1 für diese Aufgabe als nicht gestützt; dann wird B oder A genommen. Ist der gewählte Arm gegenüber A0 nicht `SUPERIOR`, wird der Gewinn **nicht** der Zeitintegration zugeschrieben.

## 2. Eingaben, Zeit, Informationsparität

- **Encoder** `P2Encoder.kt`, 8 Merkmale in [0,1]: SWITCH, CORRECTION, SHORT_REF, LONG_REF, PERSON_INTRO, PERSON_PRONOUN, DEVICE_OR_PLACE, PRESENCE_UNCERTAIN.
  - Er sieht nur den öffentlichen Text eines Turns: keine EWM2-Fakten, keine Labels, kein Gold, keine Modellausgabe.
  - Eingefroren in Commit `ae73f9952c252e8981611f13fec3066bfd096d71` (Blob `70ca435b9534f9f2e58f676c532934834e36a0bf`), **bevor** die Holdout-Textbank geschrieben wurde.
- **Kanäle:** Merkmal k speist Kanal 2k (τ = 60 s) und Kanal 2k+1 (τ = 3600 s).
- **Zeit:** monotone Sitzungssekunden. Kalenderzeit wird nicht verwendet; A_DSK bekommt ein festes Datum, 30.10.2026.
- **Ereignisprotokoll** (B, C identisch):
  - Abklingen mit Eingabe 0 über Δt = t − t_prev, danach Injektion der Turn-Eingabe für 10 s.
  - Erst wird vollständig validiert, dann gerechnet. Nicht endliches t, Δt < 0 (Uhr läuft rückwärts) und ungültige Eingaben werden ohne jede Zustandsänderung verworfen.
  - Exaktes Duplikat (gleiches t, gleiche Eingabe): ignoriert. Δt = 0 mit neuer Eingabe: Injektion ohne Abklingen.
  - Neustart = `reset()`; kein Zustand überlebt Sitzung oder Prozess.
  - Ist die Zeit am Entscheidungs-Turn ungültig, gibt es keinen Vorschlag; es gilt dann das Verhalten von A.
- **Readout-Eingabe:**
  - A0: [x_t (8), ln(1+Δt/60)].
  - B, C: [x_t, ln(1+Δt/60), Zustand (16)].
  - Alle Arme sehen dieselben Zeitstempel aus derselben Quelle. A nutzt sie per Definition nicht.
- **Router-Signale** (alle Arme gleich): `evidence=NONE` (kein `groundedRecall` simuliert), `coverage=0`, `claimFlags=0`, `freshnessNeeded=false`, `toolIntent=ContinuumTaskClassifier.classify(text).expectedToolUse` (Produktivcode), `personal=false`, `modelReady=true`, `internetAllowed=false`, `remainingModelCalls=4`.

## 3. Daten, Splits, Gold

- **Generator** `P2Generator.kt`:
  - 7 Szenariogruppen: S1 Ortswechsel, S2 Korrektur, S3 Unterbrechung mit wechselndem Δt, S4 verschachtelte Entitäten, S5 gleiche Signalfolge mit 5 s gegenüber Stunden, S6 Präsenz-/Gerätedrift, S7 Mehrdeutigkeit.
  - Jede Gruppe enthält Positiv- und Negativkontrollen.
  - Je Stamm 4 Varianten (Namen, Sätze, Pausen). **Ein Stamm = eine Cluster-Stichprobe.**
- **Splits:**
  - DEV-Textbank (`dev`, Seed 20261030): TRAIN mit 40 Stämmen je Szenario (280), VALIDATION mit 20 je Szenario (140).
  - SEALED_HOLDOUT: eigene Textbank `P2HoldoutBank.kt` (Seed 990311), 40 je Szenario (280).
  - Unterschiede des Holdouts: neue Namen und Räume, neue Satzformen, rund ein Drittel der Cue-Formulierungen außerhalb des eingefrorenen Lexikons, Pausen von 1 s bis 12 h, S5 exakt 5 s gegenüber 4 h.
- **Gold-Semantik** (eine Regel-Engine für alle Szenarien, `DialogBuilder.label`):

  | Referenztyp | Gold |
  |---|---|
  | NONE | PROCEED |
  | LONG (Vorhaben, Termin, Auftrag) | PROCEED; EWM2 bleibt autoritativ |
  | SHORT | CLARIFY, wenn kein Referent existiert (NO_REFERENT), er älter als 30 min ist (STALE_GAP) oder danach ein Situationswechsel kam (LOCATION_SWITCH); sonst PROCEED |
  | DEVICE_CMD | CLARIFY bei Präsenz-Cue ≤ 10 min oder Wechsel nach dem Geräte-Referenten (PRESENCE_DRIFT); ebenso, wenn der Referent fehlt (NO_REFERENT) oder veraltet ist (STALE_GAP); sonst PROCEED |
  | PERSON | Kandidaten sind gleichgeschlechtliche Personen, die ≤ 2 h zurück erwähnt und nicht wegkorrigiert wurden. 0 Kandidaten → CLARIFY (NO_REFERENT), ≥ 2 → CLARIFY (AMBIGUOUS_ENTITY), 1 → PROCEED |

  `negControl` markiert PROCEED-Fälle mit Ablenker. Zwischen 15 und 35 Minuten liegt keine Pause, damit die Labels eindeutig sind.
- **Fehlertaxonomie:**
  - Gold CLARIFY, Arm fragt nicht zurück: STALE_GAP → `STALE_FACT_LEAKAGE`; AMBIGUOUS_ENTITY oder NO_REFERENT → `WRONG_ENTITY_BINDING`; LOCATION_SWITCH oder PRESENCE_DRIFT → `MISSED_CONTEXT_SWITCH`.
  - Gold PROCEED, Arm fragt zurück: mit negControl `FALSE_CONTEXT_SWITCH`, sonst `UNNECESSARY_CLARIFICATION`.
  - `UNAUTHORIZED_ACTION_SUGGESTION`: Die Endaktion des Arms ist `TOOL`, die Aktion von A ist es nicht. Zulässig sind 0; schon 1 schließt den Arm aus.
  - Zusätzlich berichtet: geerbte TOOL-Aktionen bei PRESENCE_DRIFT, die auch A hat.
- **Wichtige Grenze:** Der Holdout ist versiegelt (Hash vor dem Lauf, genau eine Öffnung, `holdout-opened.receipt`), stammt aber vom **selben Autor**. Er ist **kein** unabhängiger Holdout. Das TSV-Format ist so dokumentiert, dass ein fremder Autor einen echten unabhängigen Holdout liefern kann.

## 4. Training und Auswahl (nur TRAIN/VALIDATION)

- **Readout:** L2-logistische Regression auf z-standardisierten Eingaben, Bias ungestraft, gedämpftes Newton-Verfahren, deterministisch.
- **Raster je Readout-Arm, identisch für A0, B, C:** λ ∈ {1e-3, 1e-2, 1e-1, 1}, Schwelle ∈ {0,3; 0,4; 0,5; 0,6; 0,7}, also 20 Konfigurationen.
- **Auswahl auf VALIDATION:** höchste Cluster-Lösungsquote, dann Turn-Genauigkeit, dann größeres λ, dann die Schwelle näher an 0,5.
- **Freeze:** `frozen-candidates.txt` (λ, Schwelle, SHA-256 der Readout-Gewichte) wird committet, **bevor** der Holdout geöffnet wird. Beim Öffnen wird jeder Readout aus TRAIN neu gefittet; sein Gewichts-Hash muss mit dem Freeze übereinstimmen.

## 5. Primärmetrik, Statistik, Urteile

- **Primärmetrik:** Cluster-Lösungsquote auf SEALED_HOLDOUT. Ein Stamm gilt als gelöst, wenn alle 4 Varianten korrekt entschieden sind.
- **Sekundär:** Turn-Genauigkeit (Clustermittel), Fehlertaxonomie, Szenario-Quoten, NO_PROPOSAL-Anzahl.
- **Tests:** exakter McNemar auf gepaarten Stämmen; Holm über die 3 Primärvergleiche (C–A, C–B, B–A) und getrennt über die 5 Sekundärvergleiche (B–A0, C–A0, A_DSK–A, B–A_DSK, C–A_DSK).
- **Konfidenzintervalle:** Cluster-Bootstrap mit 10 000 Ziehungen (Seed 5), Perzentil-KI für 95 % und 90 %.
- **MDE:** +5 Prozentpunkte Lösungsquote.
- **Urteile:**
  - `SUPERIOR`: Holm-p < 0,05 **und** 95-%-KI-Untergrenze > 0 **und** Schätzer ≥ +5 pp. `INFERIOR` ist das Spiegelbild.
  - `EQUIVALENT`: 90-%-KI liegt in [−5, +5] pp (TOST).
  - Sonst `INCONCLUSIVE`. Nichtsignifikanz wird nicht als Gleichwertigkeit gewertet.
- **Berichtet werden:** absolute Zahlen, Diskordanzen b/c, p, Holm-p, Schätzer, 95- und 90-%-KI.

## 6. Entscheidungsregel (unverändert aus dem Auftrag)

1. Ein Arm B oder C mit `UNAUTHORIZED_ACTION_SUGGESTION > 0` ist ausgeschlossen.
2. **C** wird gewählt, wenn C gegenüber A **und** gegenüber B `SUPERIOR` ist und das Kosten-Gate (K) einhält.
3. Sonst **B**, wenn B gegenüber A `SUPERIOR` ist und das Kosten-Gate einhält. Ist C gegenüber B nur `EQUIVALENT` oder `INCONCLUSIVE`, gewinnt B als billigere Lösung.
4. Sonst **A**, der unveränderte Router.
5. **Attributions-Flag:** Ist der Gewinner gegenüber A0 nicht `SUPERIOR`, wird `GAIN_NOT_ATTRIBUTABLE_TO_TEMPORAL_STATE` gesetzt. Zusätzlich wird der Vergleich mit A_DSK berichtet: Reicht der vorhandene Kernel schon aus?
6. **Unabhängigkeits-Gate:** Der Holdout stammt vom selben Autor, deshalb ist der Status höchstens `RESEARCH_ONLY`. **Aus diesem Ergebnis entsteht kein Shadow-Hook.** Das deckt sich mit BIO-NEURONAL-4: keine Shadow-Promotion durch synthetische Tests.

## 7. Teil N · Numerik (getrennt, nur TRAIN/VALIDATION-Dialoge)

- **Referenz:** RK4 der Original-ODE τᵢ ḣᵢ = −hᵢ + Σⱼ Wᵢⱼ tanh(hⱼ) + clip(uᵢ) + bᵢ mit stückweise konstanter Eingabe und Schrittweite ≤ 0,02·min τ. Gültig ist sie nur, wenn Schrittweite h und h/2 auf allen geprüften Dialogen mit ‖·‖∞ < 1e-9 übereinstimmen.
- **Varianten mit identischen Gewichten, Zuständen und Eingaben:**
  - DENSE_P1: Vertrag von C, ein Exponential-Euler-Schritt je Phase.
  - DENSE_SUBSTEP: Exponential-Euler mit Teilschritten ≤ min τ/10.
  - EXPM1: wie DENSE_P1, aber 1−a = −expm1(−Δt/τ).
  - LEAK_ONLY_GAP: in Pausen reiner Zerfall h ← a·h. Das ist nur exakt, wenn der Gesamtantrieb verschwindet, und dient als Demonstration falscher „Exaktheit“.
  - SPARSE(θ) mit θ ∈ {1e-3, 1e-2}: gecachte Aktivierungen; Spalte j wird nur aktualisiert, wenn sich tanh(hⱼ) um mehr als θ ändert; inkrementeller Antrieb.
- **Gemessen:**
  - ‖Variante − DENSE_P1‖∞ (gleicher diskreter Vertrag) und ‖Variante − Referenz‖∞.
  - Für SPARSE die schrittweise Schranke Eₖ₊₁ ≤ qₖEₖ + maxᵢ(1−aᵢ)·ρ·θ, mit 0 erlaubten Verletzungen.
  - Routing-Flip-Rate: Der eingefrorene C-Readout wird auf die Zustände jeder Variante angewandt und gegen DENSE_P1 verglichen, auf TRAIN- und VALIDATION-Dialogen.
- **expm1-Prüfung:** relative Fehler von 1−exp(−x) und −expm1(−x) für x ∈ {1e-6 … 1e-17} gegen eine Reihenentwicklung in BigDecimal. Außerdem die Akkumulation über 10⁶ Schritte bei Δt = 1e-6, τ = 1, konstanter Eingabe 1 gegen 1−e⁻¹, und die Prüfung der Konvexschranke |h| ≤ max(|h₀|, R) bei beiden Formen.
- **Degenerierte Fälle:** Δt = 0, < 0, NaN, ±∞, τ = 0 (Konstruktor lehnt ab), Δt = 1e300, τ = 1e-300, Wiederholung, Kollision.
- **Annahme einer Sparse-/Eventvariante nur bei:** 0 Schrankenverletzungen **und** Flip-Rate 0 auf TRAIN+VAL **und** ≥ 20 % geringeren p50-Vollkosten je Turn gegenüber DENSE_P1 (gemessen nach Teil K). Sonst `REJECTED`.

## 8. Teil K · Vollkosten (Host-JVM, kein Fold7)

- **Stufen je Turn:** Encoder (Regex), Zustandsupdate (Abklingen + Injektion), Readout, `choose()` samt Klassifikator.
- **Arme:** A, A0, B, C (DENSE_P1), C-SPARSE(1e-2), C-EXPM1.
- **Protokoll:** 5 getrennte JVM-Prozesse, Arm-Reihenfolge je Prozess zufällig (Seed = Prozessnummer). Je Arm 200 000 Aufwärm-Turns, dann 200 Blöcke à 1 000 Turns aus TRAIN-Texten und -Δt. Es werden p50/p95/p99 je Turn über die Blöcke gebildet und über die Prozesse berichtet.
- **Speicher, getrennt ausgewiesen:**
  - Allokation je Turn (ThreadMXBean);
  - primitive Nutzdaten (Bytes);
  - Heap-Schätzung je Instanz (10 000 Instanzen, nach GC; als `ESTIMATE` markiert);
  - `VmRSS` des Prozesses;
  - Android-App-RAM: `NOT_RUN`.
  - Mitprotokolliert werden `/proc/loadavg`, CPU-Modell und JVM.
- **Kosten-Gate für Einsatzfähigkeit:** Zustandsupdate p99 ≤ 10 µs (Host) und Nutzdaten ≤ 4096 B. Das ist **kein** Fold7-Budget. Fold7: `DEVICE_NOT_RUN`.

## 9. Ressourcen und Abbruch

- **Limits:** Host-Läufe je ≤ 10 min Wanduhr, Speicher ≤ 2 GiB Heap.
- **Abbruch:**
  - Build-Fehler, Zeitüberschreitung, OOM oder fehlende Tools → `INFRASTRUCTURE_ABORT` oder `NOT_RUN`, nie ein Ergebnis.
  - Hash-Abweichung bei Holdout oder Freeze → Abbruch.
  - Nicht reproduzierbare Readout-Gewichte → Abbruch.
  - Ein zweites Öffnen des Holdouts ohne `--replay` wird verweigert; `--replay` muss den identischen Ergebnis-Hash liefern.

## 10. Lauf-Reihenfolge

```sh
. env.sh   # KOTLINC, JUNIT_JAR, HAMCREST_JAR, OUT
scripts/exp20-p2-host-run.sh build
D=docs/evidence/exp20-p2-20261030/data; R=docs/evidence/exp20-p2-20261030/run
scripts/exp20-p2-host-run.sh main at.heinz.deviceai.research.exp20.p2.P2Main develop $D $R     # danach Freeze committen
scripts/exp20-p2-host-run.sh main at.heinz.deviceai.research.exp20.p2.P2Main holdout $D $R <holdout.tsv sha256> <frozen sha256>
```

## 11. Prüfsummen zum Zeitpunkt der Registrierung

Code (SHA-256 und Git-Blob), Testquellsatz `app/src/test/java/at/heinz/deviceai/research/exp20/`:

| Datei | SHA-256 | Git-Blob |
|---|---|---|
| `BoundedCtrnn.kt` | `ce5a77b3764e1011bf5d17c9a32ee2611572c29cac4fbcc41c77c7d534564f7e` | `63ad99a107caeb34196c3a205c5927f7e6f5ac70` |
| `p2/P2Config.kt` | `8c1a33f71501d9b20667751bf84ddb0dcf0f35968cf48c6173f4fc2ed0ac59f8` | `8491a59ea146ded7f9879a6c7bc1fa59a94e6ef2` |
| `p2/P2Encoder.kt` | `63f559a3aea1699109356285875af80521991e6acb213c56aa6b666b8ed79332` | `70ca435b9534f9f2e58f676c532934834e36a0bf` |
| `p2/P2Data.kt` | `ed2215770de84452913e950f2504a95e766ce4f74fe0c1998b66a1265cb3859a` | `c3a0415dbb4883e96d89097db686a53399feea83` |
| `p2/P2Generator.kt` | `c7982845d7e82a11c4c9fc397f62df386c9efdc437dd00f9622352161630e40d` | `c03f8e3f8bd81df7127a93b338336c7484c304f4` |
| `p2/P2HoldoutBank.kt` | `1b1dd415ca623fb2b07693d6f4cf49e289fc7c40898cc46c9c314fd3923be946` | `71e5e66202062fabf23be5c187448916a2c302e5` |
| `p2/P2Arms.kt` | `f482091d0b172b634fb691fdf53170e56de2b6670833aec21dd2cd18fc290c69` | `ec7a8a1974f4e6d55ba4e782c57bf3bdc763d2a1` |
| `p2/P2Eval.kt` | `68a6bde039e8a95831bb3220facad3c3f90e6cb9e824a89f1c0037c36f6539e5` | `f7c88125c961a9a2dbd9bb5d9add2d774c76000f` |
| `p2/P2Stats.kt` | `d4b5f65f5cb436e21424cafa3224534cdf30072c66012d0d8f08f05c85565507` | `a5bd552734e606877679903b8c4a10c243d3e429` |
| `p2/P2Main.kt` | `deba80dbba438ce8ff26c88bcccf4fbc03b1c59e6afe588d3106305902036bbb` | `4fdaa7d6a8a56e65adff64ee7c6588d0d2808b26` |
| `p2/P2InfraTest.kt` | `9fdd17d9f7864a4aa0e933b8f5e7a26ff72fc987c721c8062f2b09e33b666db1` | `6d50563248ead44c2ef763df5c4291deeca0bfa2` |
| `app/src/main/java/at/heinz/deviceai/research/argon4/ComputeAllocationPolicy.kt` (unverändert) | `6cc2d4d42a9a1da9c124975d023db63719a816fa822831aab60a57516fc43da0` | `4628ba00cb98e4c6abdc3e637837dc4ca1fc8aa0` |
| `app/src/main/java/at/heinz/deviceai/research/argon4/DialogStateKernel.kt` (unverändert) | `1da42b4e9b7be124cd10a387837cb7cbf5920b9f0502b0daf52e33c51bdf6a77` | `85c816a7579af910f66d1c00cd5f0a51ecd55060` |
| `app/src/main/java/at/heinz/deviceai/cognitive/ContinuumIntelligence.kt` (unverändert) | `37fd455568dcedc7ea7d5e384f91175448bf4a5bb105225a078127642dd5c32f` | `8ab20e1ca371b021e67ee19067105c3cca9d898d` |
| `scripts/exp20-p2-host-run.sh` (unverändert) | `0539588a8350cc575c758996c0f0245b457f92bfc219b9a9b651061e33f0329f` | `73838692f1999faefb7c213a8b3d56f2da2582d0` |

Daten `docs/evidence/exp20-p2-20261030/data/` (erzeugt mit `P2Main generate`):

| Datei | SHA-256 |
|---|---|
| `train.tsv` | `9164118b9587305058aafa38d2864f54ecf1901f2e018887f1b8edd70e97d9fc` |
| `validation.tsv` | `305d6c448f922e166771d7a2119e2b42bc4d6b7e08f56de8f6f9d7d0fcaa3821` |
| `holdout.tsv` | `23cd2f25116f07d1d5066edbb17181a89e99fbd941f1017445cf2aeb8b6a12c0` |
| `split-assignment.tsv` | `18ef79f424f8cf8a824c598edd27ac0829d1ebb4863cc4747e4ec3aafc7fc7d8` |
| `gold-train.tsv` | `239f63307eb6545354949b1327ef124a1d916f6553cb12a115d1c1b0c99572d3` |
| `gold-validation.tsv` | `dd9053984726594c0ec0c9e84169a187ea9b40038de3383481c71b8400880c46` |
| `gold-holdout.tsv` | `a25a6f90896a9ed208596ce989d6910ec4e03a15614d4f903ef34bd72df27650` |
| `dataset-summary.txt` | `64bbadd1c91aabab5358937051c148cd2f96485b747034ee236a7865d49f3b8c` |

**Versiegelter Holdout:** `holdout.tsv` SHA-256 `23cd2f25116f07d1d5066edbb17181a89e99fbd941f1017445cf2aeb8b6a12c0`. Bis zu diesem Commit wurde kein Arm darauf ausgewertet, und sein Inhalt wurde nicht gelesen; nur die Zählstatistik in `dataset-summary.txt` wurde erzeugt.
