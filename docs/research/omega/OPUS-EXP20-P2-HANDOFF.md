# EXP-20 P2/P3 · Übergabe (Opus, 30.10.2026)

**Status: abgeschlossen auf Host-Labor-Niveau. Nichts davon ist produktiv integriert.**

- **Branch:** `claude/continuum-jarvis-exp20-p2-kxrxy2`.
- **Draft-PR:** #256, gegen #253 (`research/jarvis-voice1-stale-recognizer-session-20261030`).
- **Basis:** `ed408a7d921e47b5730b06f1f514268e249bd5f7`.
- **Den letzten gepushten Commit** zeigt der PR-Verlauf. Die Kette steht unten.

## Ergebnis in drei Sätzen

1. **EXP-20 P2:** Auf 280 versiegelten Stämmen (selber Autor) wählt die vorregistrierte Regel **A, den unveränderten Router**. Das CTRNN ist gegenüber EMA `EQUIVALENT` und gegenüber A `INCONCLUSIVE`; die zustandslose Kontrolle A0 ist am besten (174/280). Also kein Shadow-Hook.
2. **Numerik/Kosten:** Das P1-CTRNN ist faktisch eine zeitdiskrete Abbildung. Eine genauere ODE-Integration kippt 226/1 680 Routingentscheidungen. Sparse hält die Fehlerschranke, spart aber nichts und ist `REJECTED`. Das Zustandsupdate macht < 3 % der Turnkosten aus.
3. **P3 (DG-SDR / Zwei-Strom):** Kein vorhandener lokaler Encoder erkennt Synonym-Paraphrasen (beste 14/60). DG-SDR verschlechtert beide Encoder signifikant und ist `REJECTED`. Dendritisches Gating ist `NOT_STARTED`, weil die semantische Vorbedingung fehlt.

## Die 12 Pflichtantworten

1. **Geänderte Dateien/Caller:**
   - `app/src/main`: **0 Dateien** (`git diff ed408a7 HEAD -- app/src/main`).
   - Kein Caller, kein Router, kein ToolGate, kein EWM2 geändert.
   - Neu oder geändert, nur Testquellsatz:
     - `app/src/test/java/at/heinz/deviceai/research/exp20/BoundedCtrnn.kt` (Aliasing-Fix, Numerik bitgleich);
     - `BoundedCtrnnAliasingTest.kt`;
     - `p2/*` (Config, Encoder, Data, Generator, HoldoutBank, Arms, Eval, Stats, Main, InfraTest, Explore, Numerics, CostBench);
     - `p3/P3Probe.kt`.
   - Skript: `scripts/exp20-p2-host-run.sh`. Dazu Dokumente und Belege unter `docs/research/exp20/` und `docs/evidence/exp20-p2-20261030/` bzw. `exp20-p3-20261030/`.
2. **Baseline und Arme:**
   - A = `ComputeAllocationPolicy.choose()` (Blob `4628ba00`) mit Signalen wie `ChatReasoningRouter.advise` (Blob `f21a114c`).
   - B = P1-`LeakyIntegrators`.
   - C = unverändertes P1-`BoundedCtrnn` (n = 16, ρ = 0,9, W-Seed 2026103002).
   - Kontrollen: A0 (Readout ohne Zustand) und A_DSK (vorhandener `DialogStateKernel`).
3. **Eingefrorene Hypothesen und Prüfsummen:** [P2-Vorregistrierung](../exp20/EXP-20-P2-PREREGISTRATION-20261030.md), Commit `a10e3c29c4b2e93d636aeced438f5f556dc955c0`.
   - `holdout.tsv` `23cd2f25116f07d1d5066edbb17181a89e99fbd941f1017445cf2aeb8b6a12c0`
   - `train.tsv` `9164118b…`, `validation.tsv` `305d6c44…`, `split-assignment.tsv` `18ef79f4…`
   - Encoder-Blob `70ca435b…`, eingefroren in `ae73f995` vor der Holdout-Bank.
   - Freeze `frozen-candidates.txt` `3302e831…`, Commit `d46f87f4…`.
   - [P3-Vorregistrierung](../exp20/EXP-20-P3-PREREGISTRATION-20261030.md), Commit `af235e95…`, Daten `104cd929…`.
4. **Befehle und Exit-Codes:**
   - Toolchain: kotlinc-jvm 2.0.20 (Release-SHA geprüft), OpenJDK 21.0.12.1, JUnit 4.13.2, Linux x86_64 mit 4 vCPU.
   - Aliasing rot auf P1: 3/3 FAIL, Exit 1. Grün nach Fix: 27/27, Exit 0.
   - `P2Main develop`: Exit 0. `P2Main holdout`: Exit 0; zweites Öffnen Exit 3 (verweigert); `--replay` Exit 0, byte-identisch.
   - `P2Numerics`, `P2CostBench` (5 Prozesse) und `P3Probe`: jeweils Exit 0.
   - Finale Regression 37/37, Exit 0 ([final-regression.txt](../../evidence/exp20-p2-20261030/final-regression.txt)).
   - Android `testDebugUnitTest`, `lintDebug`, `assembleDebug`: **NOT_RUN** (`dl.google.com` 403).
   - VOICE-1-Harness von #253 **nicht erneut ausgeführt**. Belegt ist nur, dass auf diesem Branch keine Datei unter `app/src/main` geändert wurde; die #253-Gates sind damit unberührt.
5. **Absolute Entscheidungen auf dem Holdout** (280 Stämme, 1 120 Turns):
   - A: 153 Stämme / 732 Turns.
   - B: 146 / 879.
   - C: 149 / 883.
   - A0: 174 / 788.
   - A_DSK: 124 / 745.
   - Fehlertypen je Arm stehen in [EXP-20-P2-RESULT](../exp20/EXP-20-P2-RESULT-20261030.md). `UNAUTHORIZED_ACTION_SUGGESTION` = 0 für alle; strukturell bewiesen über 6 400 Signalkombinationen.
6. **Effekte** (Holm, Cluster-Bootstrap):
   - C–A: −1,4 pp [−9,3; +6,8], `INCONCLUSIVE`.
   - C–B: +1,1 pp [−2,5; +4,6] (90 %: [−1,8; +3,9]), `EQUIVALENT`.
   - B–A: −2,5 pp [−10,7; +5,7], `INCONCLUSIVE`.
   - A_DSK–A: −10,4 pp, `INFERIOR`.
   - Sekundär die Turn-Genauigkeit: C–A +13,5 pp [+7,7; +19,3], bei 61 falschen Kontextwechseln. Das wird nicht in einen Erfolg umgedeutet.
7. **Kosten** (Host, 5 JVM-Prozesse, Median der p50/p95/p99 in ns je Turn):
   - Vollpipeline: A 31 213/43 462/48 934; B 33 183/41 460/44 932; C 28 775/43 679/46 782. Etwa 18–19 KB Allokation je Turn, dominiert von Encoder und Klassifikator.
   - Nur Zustand: B 333/426/560; C 894/1 648/1 933; C_SPARSE 918/1 070/1 131; C_EXPM1 1 612/1 918/2 021.
   - Nutzdaten: B 1 120 B, C 3 424 B. Heap-Schätzung je Instanz: B 525 B, C 3 213 B. VmRSS 88–100 MB. Android-App-RAM: NOT_RUN.
8. **Numerik:**
   - DENSE_P1 gegen ODE-Referenz: 1,66·10⁻² (Referenz selbstkonsistent bis 2,6·10⁻¹⁰).
   - Sparse(10⁻²) gegen dense: 1,86·10⁻³, 0/2 480 Schrankenverletzungen, 0 Flips.
   - Teilschritt-Integration: 226/1 680 Flips; exakte ODE: 76/560 Flips; reiner Zerfall in Pausen: 240 Flips.
   - `1−exp(−x)` bei x = 10⁻¹⁷: 100 % Fehler; `−expm1` korrekt. Details in [numerics-result.txt](../../evidence/exp20-p2-20261030/numerics/numerics-result.txt).
9. **Host gegen Fold7:** Alle Werte stammen vom Host (x86_64 JVM). Fold7, ARM64, Energie und Temperatur: **DEVICE_NOT_RUN**.
10. **Gewinner nach Vorabregel:** **A, der unveränderte Router.** CTRNN gewinnt nicht, EMA ebenfalls nicht gegen A.
11. **Grenzen und Blocker:**
    - Der Holdout stammt vom selben Autor.
    - Die Dialoge sind synthetisch, der Encoder ist lexikalisch.
    - λ liegt am Rand des Rasters.
    - Android-SDK gesperrt.
    - Trainierte Geräte-Checkpoints von Subword und CRE1 sind nicht im Repo.
    - EXP-21 (EWC) und EXP-22 (STDP): **NOT_STARTED**. Grund: Laut Auftrag erst nach P2 und der Sparse-Entscheidung, und die Priorität lag auf dem nachgewiesenen Engpass, der Ereignis- und Bedeutungserkennung.
    - Predictive Coding, Reset und Replay: nur Theorie, NOT_STARTED.
12. **SHAs, Rückbau, Fähigkeit:**
    - Commit-Kette: `8a668bbe` Start · `b35566e7` RED · `b62dbad1` Fix · `ae73f995` Harness/Encoder-Freeze · `a10e3c29` P2-Vorregistrierung · `d46f87f4` Freeze · `fe753d1b` Holdout · `9735ddb1` P2-Ergebnis · `af7c89a4` Numerik · `b7b3ecb7` Kosten · `af235e95` P3-Vorregistrierung · `2df9ecbf` P3-Ergebnis, danach dieser Abschluss.
    - **Rückbau:** PR #256 nicht mergen, oder die Commits in umgekehrter Reihenfolge per `git revert` zurücknehmen. Produktivcode ist nicht betroffen.
    - **Neue, belegte Fähigkeiten:**
      - ein reproduzierbarer A/B/C-Harness am echten `choose()`-Pfad, einschließlich Slot für einen unabhängigen Holdout (TSV, Hash, einmaliges Öffnen, Replay);
      - die Feststellung, dass `DialogStateKernel.wouldClarify` als Routingsignal schadet;
      - der Aliasing-Fix im Forschungskern;
      - ein Encoder-Vorbedingungstest (P3), der jeden künftigen „semantischen“ Encoder sofort falsifizieren kann.

## Nächste drei Entwicklungsschritte

1. **Semantischen Basisstrom schaffen, bevor neue Neuronenmechanik kommt.** Entweder den echten Geräte-CRE1-Checkpoint mit Herkunfts-SHA beziehen, oder CRE1 isoliert auf einem registrierten Synonym- und Paraphrasen-Curriculum trainieren. Danach P3 auf einem **fremd verfassten** Satz wiederholen.
2. **Unabhängigen Holdout für P2 beschaffen.** Ein anderer Autor oder Heinz schreibt TSV im Format `P2Tsv`. Dann `P2Main holdout` mit Hash. Erst ein dort positives Ergebnis rechtfertigt eine Diskussion über eine Signaturänderung an `advise()` (monotone Ereigniszeit).
3. **Erst bei bestandener Vorbedingung:** Zwei-Strom-Gating-Pilot nach dem Aufbau in [EXP-20-P3-RESULT](../exp20/EXP-20-P3-RESULT-20261030.md): A ohne Gate, B deterministischer Filter, C begrenzte Verstärkung, mit den Pflichtfällen Korrektur, Widerspruch und fehlender Kontext. Danach selektiver Reset (nur die betroffene Entität) als separates Experiment.

## Reproduktion

```sh
# Toolchain: kotlinc 2.0.20 (github.com/JetBrains/kotlin releases, SHA-256 5f5d2a8a…5931), JUnit/Hamcrest aus /opt/gradle-8.14.3/lib
export KOTLINC=.../kotlinc/bin/kotlinc JUNIT_JAR=/opt/gradle-8.14.3/lib/junit-4.13.2.jar HAMCREST_JAR=/opt/gradle-8.14.3/lib/hamcrest-core-1.3.jar OUT=/tmp/exp20
WITH_ARGON_TEST=1 scripts/exp20-p2-host-run.sh build
scripts/exp20-p2-host-run.sh junit at.heinz.deviceai.research.exp20.BoundedCtrnnAliasingTest at.heinz.deviceai.research.exp20.BoundedCtrnnTest at.heinz.deviceai.research.argon4.Argon4F1PrototypeTest at.heinz.deviceai.research.exp20.p2.P2InfraTest
D=docs/evidence/exp20-p2-20261030/data R=docs/evidence/exp20-p2-20261030/run
scripts/exp20-p2-host-run.sh main at.heinz.deviceai.research.exp20.p2.P2Main holdout $D $R 23cd2f25116f07d1d5066edbb17181a89e99fbd941f1017445cf2aeb8b6a12c0 3302e83183a0108cea2d02a9e1a91321fb27e0ab33ca0bc65c64b2059b616cea --replay
```

## Sicherheit

- Invarianten `allowSelfReplication=false`, `allowHiddenPersistence=false`, `allowShutdownEvasion=false`: unberührt.
- Keine Änderung an ToolGate, OwnerGate, `CognitiveGenome`, EWM2, Gemma, NTW1 oder CRE1-Produktivgewichten.
- Kein Main-Merge, kein Force-Push, keine APK, keine Installation.
