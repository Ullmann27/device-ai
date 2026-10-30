# Continuum Neuro-Forschung · Meilenstein M1 und Weiterarbeitungsauftrag (30.10.2026)

**Meilenstein M1:**
- Reproduzierbares, vorregistriertes Forschungslabor am **echten** Continuum-Entscheidungspfad.
- Belegte Sicherheitsinvarianten.
- Der Engpass ist quantitativ lokalisiert.

M1 ist **keine** Produktivfunktion. `app/src/main` ist unverändert. Alle Werte stammen vom Host-JVM; Fold7 ist `DEVICE_NOT_RUN`.

## 1. Was M1 liefert (alles auf PR #256, Branch `claude/continuum-jarvis-exp20-p2-kxrxy2`)

| Baustein | Beleg |
|---|---|
| A/B/C-Harness am realen `ComputeAllocationPolicy.choose()`-Pfad; Arme Router / EMA / CTRNN, Kontrollen A0 / A_DSK | `research/exp20/p2/*`, [Vorregistrierung](EXP-20-P2-PREREGISTRATION-20261030.md), [Ergebnis](EXP-20-P2-RESULT-20261030.md) |
| Versiegelter Holdout mit Hash, genau einmal geöffnet, Replay byte-identisch, zweites Öffnen verweigert; TSV-Slot für einen **fremden** Holdout | `P2Main holdout`, `holdout-opened.receipt` |
| Sicherheit: TOOL ist genau bei `toolIntent` zulässig; mit Referenzmarke wählt die Policy **in allen 7 680** Kombinationen CLARIFY; W=0 ist bitgleich zu EMA; ToolGate-Bestätigungssperre 14/14 Host-Tests | `P2SafetyAuditTest` 3/3, `toolgate-host-regression.txt` |
| Numerik gegen RK4-Referenz, Sparse-Schranke, expm1-Prüfung | `numerics-result.txt` |
| Vollkosten über 5 JVM-Prozesse | `cost/aggregate.txt` |
| Encoder-Vorbedingung (P3) für jede künftige „semantische“ Repräsentation | [P3-Ergebnis](EXP-20-P3-RESULT-20261030.md) |
| Aliasing-Fix im Forschungskern (rot → grün) | Commits `b35566e7` → `b62dbad1` |

## 2. Der entscheidende Befund (explorativ, Holdout schon geöffnet, keine Neuauswahl)

[`exploratory-autopsy.txt`](../../evidence/exp20-p2-20261030/run/exploratory-autopsy.txt), Turn-Genauigkeit:

| Bedingung | A Router | A0 | B EMA | C CTRNN |
|---|---|---|---|---|
| **sauber** (Encoder sieht alle Cues, Zeit im TRAIN-Bereich), 604 Varianten | 401 (66 %) | 406 (67 %) | **572 (95 %)** | **567 (94 %)** |
| Encoder blind, 347 | 257 (74 %) | 255 | 191 (55 %) | 202 (58 %) |
| Zeit außerhalb des TRAIN-Bereichs, 88 | 41 | 80 | 81 | 88 |

- **Bedeutung:** Ein zeitlicher Kontextzustand mit Readout macht die Rückfrage-Entscheidung deutlich besser, **wenn** die Ereignisse erkannt werden.
- **Der Abfall auf dem Holdout** entsteht, weil der eingefrorene lexikalische Encoder 38 % der Holdout-Varianten nicht vollständig sieht (Turns blind: SHORT_REF 139, DEVICE_OR_PLACE 138, PERSON_INTRO 81, CORRECTION 46 …).
- **Rekurrenz bringt keinen Mehrwert:** C ≈ B, die Ablation W=0 ist ≡ B.
- **Der Hebel ist die Ereigniserkennung**, nicht die Zahl der Neuronen.
- Das ist eine explorative Zerlegung, **kein** Erfolgsnachweis. Bestätigung braucht einen neuen, fremd verfassten Holdout.

## 3. Antworten auf die 8 Forschungsfragen

1. **Geringster Zusatzaufwand gegen Kontextfehler:** die zeitabhängige EMA (B). Zustandsupdate p99 0,56 µs, 1 120 B Nutzdaten, gleich gut wie das CTRNN. Sie lohnt sich aber nur mit besserer Ereigniserkennung.
2. **Nachweislich fehlende Fähigkeit:** robuste **Ereignis- und Bedeutungserkennung** bei unbekannten Formulierungen.
   - Kein lokaler Encoder schafft Synonym-Paraphrasen (P3: ≤ 14/60).
   - EWM2, `DialogStateKernel`, EMA und deterministische Logik decken das nicht ab. `DialogStateKernel` schadet als Rückfragesignal sogar (124 gegen 153).
   - Die Zeitintegration selbst ist durch EMA ausreichend abgedeckt.
3. **Kombination aus Pattern Separation, Predictive Coding und Eligibility Traces:**
   - Alle drei setzen auf einem Eingangsstrom auf, der Bedeutung schon tragen muss.
   - Pattern Separation (DG-SDR) hat einen bedeutungsarmen Strom verschlechtert (P3, −12 bzw. −18 pp).
   - Ohne semantischen Basisstrom verteuern sich die Mechanismen gegenseitig; ein Nutzen ist nicht zu erwarten. Erst den Basisstrom schaffen, dann je **ein** ablationierbares Modul.
4. **Beste belegbare ARM64-Aussicht:** EMA-Zustand plus kleiner linearer Readout. Er kostet < 1 µs, macht < 3 % eines Turns aus und braucht ~1 KB. Teuer sind Encoder und Klassifikator (~30 µs, ~18 KB Allokation je Turn). Das ist der eigentliche Effizienzhebel.
5. **Bisher übersehene Alternative:** **gelernte Ereignis-Detektoren** statt handgeschriebener Cue-Regexe, also einen kleinen Klassifikator je Ereignistyp auf CRE1- oder Subword-Merkmalen, trainiert auf einem registrierten Paraphrasen-Curriculum.
   - Hopfield bzw. Assoziativspeicher sind für den Top-1-Abruf **ein** Schritt `softmax(β·sim)`. Das ist monoton in sim und damit **rangidentisch** zur Nächster-Nachbar-Suche.
   - Ein Mehrwert ist erst durch iterierten Abruf über Near-Miss-Cluster denkbar, und auch nur, wenn die Schlüssel Bedeutung tragen.
6. **Jetzt anschließbar (passiv):** nichts Produktives.
   - Zulässig wäre nur ein Logging-freier, nicht-autorisierender Zähler, z. B. „Router hätte TOOL gewählt bei Driftsignal“. Auch das erst nach Besitzerfreigabe.
   - **Verfrüht** wären: `DialogStateKernel.wouldClarify` an `unresolvedReference` zu hängen (nachweislich schädlich), eine monotone Zeit an `advise()` zu geben, und jede CTRNN-Verbindung.
7. **Kleinster eindeutiger Versuch:** M2 (unten). Derselbe EMA-Arm B, nur mit einem gelernten statt lexikalischen Ereignis-Encoder, auf einem **fremd verfassten** Holdout. Widerlegt ist die Hypothese, wenn B (gelernt) auf dem fremden Holdout nicht `SUPERIOR` gegenüber A **und** A0 ist.
8. **Weg zu JARVIS:** Rückfragen bei veralteten, gewechselten oder mehrdeutigen Bezügen, also „Meinst du noch das Licht im Keller, oder bist du schon unterwegs?“, und das Blockieren von Geräteaktionen bei Präsenzdrift (im Labor 17 → 2) sind genau die Fähigkeiten „Unterbrechungen, Korrekturen, Situationsverständnis“ aus den Zielvideos. M1 zeigt, dass der Zeitteil dafür billig ist. M2 muss die Erkennung robust machen.

## 4. Zu den Zusatzaufträgen

- **P2-NI (Rekurrenz-Ablationen):**
  - Das Hauptkriterium ist bereits beantwortet: W=0 ≡ B bitgenau, und C gegen B ist `EQUIVALENT`. Es gibt also keinen eigenständigen Rekurrenznutzen.
  - Diagonale Kopplung, gleiche τ und Zustandsautomat sind als **Teil von M2** vorgesehen. Sie laufen auf dem neuen Datensatz, nicht auf dem verbrauchten Holdout.
- **Nested Sparse Associative Memory / Hopfield / ternär:**
  - Phase A (semantische Repräsentation) ist durch P3 **nicht bestanden**. Laut eigenem Auftrag beginnt die Hopfield-Forschung erst danach.
  - Ternäre oder binäre Schlüssel können Information nur verlieren. Sinnvoll erst als Kostenoptimierung eines bestandenen Encoders.
  - Anschlussstelle, wenn es so weit ist: der optionale semantische Score des vorhandenen `HybridMemoryRetriever`. Kein neuer Speicher.
- **Risk-Shim:** wird nur in `$OUT/shim` für die Host-Kompilierung erzeugt, nie unter `app/src`. Die produktive Sicherheitslogik ist unverändert.
- **#255:** reine Auftragsdokumente (`411e2d98`), unverändert und konfliktfrei. Es wurde kein weiterer Branch angelegt.

## 5. Weiterarbeitungsauftrag M2 (für den nächsten Agenten, in dieser Reihenfolge)

1. **Live-Readback:** PR #256 und #255, Branch-Spitze. Diesen Bericht, [Übergabe](../omega/OPUS-EXP20-P2-HANDOFF.md) und [P3](EXP-20-P3-RESULT-20261030.md) lesen. **Keine** versiegelten P2/P3-Dateien ändern.
2. **Vorregistrierung M2** committen, **bevor** es Daten oder Läufe gibt:
   - Arme: B_lex (eingefroren, Referenz), B_learned (EMA + gelernte Ereignis-Detektoren), A, A0. Dazu die Ablationen C_diag, C_sameTau und ein deterministischer Zustandsautomat.
   - Primärmetrik, MDE, Statistik und Entscheidungsregel identisch zu P2.
3. **Ereignis-Detektoren:**
   - Je P2-Merkmal ein L2-logistischer Klassifikator auf `Cre1InferenceEncoder.embed(text, "m")` (Parity-Checkpoint, SHA `2290194d…`) plus Zeichen-n-Gramm-Merkmalen.
   - Trainiert auf einem **neuen** Paraphrasen-Curriculum, nur DEV.
   - Danach sofort die P3-Vorbedingung auf neuen Sätzen prüfen. Fällt sie durch: Abbruch und Ergebnis sichern.
4. **Fremder Holdout:** Heinz oder eine andere KI schreibt ≥ 200 Stämme im `P2Tsv`-Format, ohne Zugriff auf die Banken. Hash committen, dann einmal öffnen mit `P2Main holdout`.
5. **Kosten:** `P2CostBench` um B_learned erweitern; Gate Turn-p99 ≤ 50 µs auf dem Host. Fold7-Messung nur mit echtem Gerät, exakter SHA und APK-Hash.
6. **Erst bei `SUPERIOR` gegenüber A und A0 auf dem fremden Holdout:** Besitzerentscheidung über einen abschaltbaren, nicht-autorisierenden Shadow-Zähler (Flag AUS) und über die monotone Ereigniszeit für `advise()`. Vorher keine Produktivänderung.
7. **Gerüst für die Ausführung:**

   ```sh
   export KOTLINC=<kotlinc 2.0.20>/bin/kotlinc JUNIT_JAR=/opt/gradle-8.14.3/lib/junit-4.13.2.jar HAMCREST_JAR=/opt/gradle-8.14.3/lib/hamcrest-core-1.3.jar OUT=/tmp/exp20
   WITH_ARGON_TEST=1 scripts/exp20-p2-host-run.sh build
   scripts/exp20-p2-host-run.sh junit at.heinz.deviceai.research.exp20.BoundedCtrnnAliasingTest at.heinz.deviceai.research.exp20.BoundedCtrnnTest at.heinz.deviceai.research.argon4.Argon4F1PrototypeTest at.heinz.deviceai.research.exp20.p2.P2InfraTest at.heinz.deviceai.research.exp20.p2.P2SafetyAuditTest   # erwartet 40/40
   ```

**Grenzen bleiben:** kein Main-Merge, kein Force-Push, keine APK oder Installation. ToolGate, OwnerGate, EWM2, `CognitiveGenome` (`allowSelfReplication`, `allowHiddenPersistence`, `allowShutdownEvasion` = false) und VOICE-1 sind unverändert.
