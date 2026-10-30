# Übergabe an ChatGPT 6 · von Claude Opus · Continuum EXP-20 → M5 „Semantik“ (30.10.2026)

**Verbindlich.** Arbeite **strikt** nach diesem Dokument und dem [Weiterbearbeitungsauftrag M5](OPUS-CONTINUATION-BRIEF-M5-20261030.md). Bei Widerspruch gilt die strengere Regel aus beiden, aus `AGENTS.md` und aus `docs/CONTINUUM-EXPERIMENTVERTRAG.md`.

- **Repository:** `Ullmann27/device-ai`. Draft-PR **#256**.
- **Branch:** `claude/continuum-jarvis-exp20-p2-kxrxy2`.
- **Basis:** #253, `ed408a7d921e47b5730b06f1f514268e249bd5f7`.
- Die letzte Spitze steht im PR-Verlauf; **vor dem ersten Schritt live per GitHub rücklesen**.

## 1. Was gesichert ist (nicht neu erfinden, nicht verändern)

| Ergebnis | Dokument |
|---|---|
| P2 (vorregistriert, Holdout verbraucht): Regel → **A Router**; C ≈ B; W=0 ≡ B | `docs/research/exp20/EXP-20-P2-RESULT-20261030.md` |
| Sicherheit: Referenzmarke → CLARIFY in 7 680/7 680; TOOL nur bei `toolIntent`; ToolGate 14/14 | `P2SafetyAuditTest`, `docs/evidence/exp20-p2-20261030/toolgate-host-regression.txt` |
| Autopsie: Der Engpass ist die Ereignis- und Bedeutungserkennung (B/C 95 % bei sichtbaren Cues, 55 % bei blinden) | `docs/research/exp20/EXP-20-MILESTONE-M1-AND-NEXT-20261030.md` |
| P3: Kein lokaler Encoder erkennt Synonyme; DG-SDR REJECTED | `docs/research/exp20/EXP-20-P3-RESULT-20261030.md` |
| P4: Hopfield-1 rangidentisch; Hopfield-2 = Medoid-Artefakt; INT8 und Sign REJECTED; **einziger Kandidat** `HybridMemoryRetriever` + CRE1-`semanticScorer` | `docs/research/exp20/EXP-20-P4-RESULT-20261030.md` |
| 12 Pflichtantworten, Commit-Kette, Reproduktion | `docs/research/omega/OPUS-EXP20-P2-HANDOFF.md` |

## 2. Was ich als Nächstes gemacht hätte, in genau dieser Reihenfolge

**Schritt 1 = WP1, Datensatzvertrag M5.** Etwa 1 Einheit, vor jedem Lauf committen.

1. Lege `app/src/test/java/at/heinz/deviceai/research/exp20/m5/M5Data.kt` an. Generator nach dem Muster von `P3Probe`, aber:
   - Pro Gruppe gibt es **zwei** Near-Miss-Sterne: um die wahre Erinnerung T **und** um einen Lockvogel L. L hat eigene 3 Ein-Slot-Varianten, damit T **nicht** der Medoid aller Kandidaten ist.
   - Neue Namen, neue Aufgaben und eine **neue** Synonymliste; keine Wiederverwendung der P3-Listen.
   - Splits DEV, VAL und SEALED über Gruppen-ID-Hash. Abfragetypen L (wörtlich) und S (Synonym).
2. **Pflichtarme**, unverändert übernehmen bzw. wiederverwenden:
   - JACCARD;
   - HYBRID_LEX und HYBRID_CRE1 (`P4Associative.scores`);
   - CRE1;
   - **QUERY_BLIND_MEDOID** (`P4MedoidControl`-Logik);
   - optional HOPFIELD2_B4, nur als Artefakt-Test.
3. **Vorregistrierung** `docs/research/exp20/EXP-20-M5-PREREGISTRATION-<datum>.md`:
   - Primärmetrik: strikte Top-1 auf SEALED.
   - Statistik: exakter McNemar, Holm, MDE +5 pp, Bootstrap 10 000, Urteile wie in `P2Stats.verdict`.
   - **Gültigkeitsbedingung:** QUERY_BLIND_MEDOID muss auf VAL nahe der Zufallsrate liegen (≤ 1/8 + 5 pp Top-1). Sonst ist der Datensatz ungültig und neu zu entwerfen, **ohne** Läufe auf SEALED.
   - SHA-256 von Generator, Daten und Splits festhalten. Committen, pushen, SHA rücklesen.

**Schritt 2 = WP3, Bestätigung des einzigen Kandidaten.** Etwa 1 Einheit.

4. Es gibt nichts zu tunen, alle Arme haben feste Parameter. SEALED **einmal** öffnen, mit Hash-Prüfung und Quittung wie in `P2Main.holdout`.
5. **Entscheidung:**
   - HYBRID_CRE1 gilt als bestätigt nur bei `SUPERIOR` gegenüber HYBRID_LEX **und** JACCARD **und** QUERY_BLIND_MEDOID.
   - Kosten p50/p95 messen (Host).
   - Ergebnis inklusive negativer Befunde sichern.

**Schritt 3 = WP2, erst danach.** Semantischen Basisstrom schaffen: CRE1 isoliert auf einem registrierten Paraphrasen-Curriculum trainieren (nur DEV). Den Checkpoint-SHA festhalten, dann die P3-Vorbedingung auf VAL prüfen. **Keine** Produktivgewichte ändern.

**Schritt 4 = WP4, nur bei bestandenem WP2.** Zeitkontext M2:
- EMA-Arm B mit gelernten Ereignis-Detektoren gegen A und A0.
- Ablationen C_diag, C_sameTau und ein deterministischer Automat.
- Bewertung ausschließlich auf einem **fremd verfassten** Holdout im `P2Tsv`-Format.

## 3. Harte Regeln (Verstoß = Abbruch)

- **Nicht anfassen:** versiegelte P2/P3/P4-Daten, Encoder, Gewichte, Freeze-Dateien, `holdout-opened.receipt` und Entscheidungsregeln. Neue Experimente bekommen eigene Dateien und eine eigene Vorregistrierung **vor** dem ersten Lauf.
- **Holdout:** genau einmal öffnen; `--replay` nur als unverändertes Audit; kein Refit auf Holdout-Labels.
- **Kontrollen:** Jedes Assoziativ- oder Kontextexperiment braucht eine abfrageblinde Kontrolle sowie die Vergleiche gegen A0 und B. Rekurrenz zählt nur bei C `SUPERIOR` gegenüber B.
- **Keine Produktivänderung** an `app/src/main`, `ChatReasoningRouter`, `ComputeAllocationPolicy`, `DialogStateKernel`, EWM2, ToolGate/OwnerGate, VOICE-1 oder `CognitiveGenome` (`allowSelfReplication`, `allowHiddenPersistence`, `allowShutdownEvasion` = false).
  - `DialogStateKernel.wouldClarify` **nicht** an `unresolvedReference` hängen; das ist nachweislich schädlich.
  - Ein Shadow-Zähler (Flag AUS) bzw. ein Δt in `advise()` erst nach positivem **fremdem** Holdout **und** Besitzerfreigabe.
- **Risk-Shim:** nur in `$OUT` für die Host-Kompilierung (`scripts/exp20-p2-host-run.sh`), nie unter `app/src`.
- **Git:** kein Main-Merge, kein Force-Push, keine APK, keine Installation. Kein neuer konkurrierender Forschungsbranch: auf #256 weiterarbeiten oder einen Draft-PR auf diesen Branch stapeln.
- **Ehrliche Statuswörter:** `NOT_RUN`, `DEVICE_NOT_RUN`, `INCONCLUSIVE`, `REJECTED`. Android-Gradle ist in der Cloud gesperrt (`dl.google.com` 403); Fold7 nur mit echtem Gerät, exakter SHA und APK-Hash. Host-Zahlen sind kein Fold7-Beleg.
- **Sichern:** nach **jedem** geprüften Schritt committen, pushen, die volle SHA per GitHub rücklesen und den PR-Text aktualisieren. Nie mehrere ungesicherte Schritte ansammeln.

## 4. Werkzeuge und Startbefehle

```sh
# kotlinc 2.0.20 aus dem JetBrains-GitHub-Release (ZIP-SHA-256 5f5d2a8ad6a718a002acd0775b67a9e27035872fdbd4b0791e3cb3ea00095931)
export KOTLINC=.../kotlinc/bin/kotlinc JUNIT_JAR=/opt/gradle-8.14.3/lib/junit-4.13.2.jar HAMCREST_JAR=/opt/gradle-8.14.3/lib/hamcrest-core-1.3.jar OUT=/tmp/exp20
WITH_ARGON_TEST=1 scripts/exp20-p2-host-run.sh build
scripts/exp20-p2-host-run.sh junit at.heinz.deviceai.research.exp20.BoundedCtrnnAliasingTest at.heinz.deviceai.research.exp20.BoundedCtrnnTest at.heinz.deviceai.research.argon4.Argon4F1PrototypeTest at.heinz.deviceai.research.exp20.p2.P2InfraTest at.heinz.deviceai.research.exp20.p2.P2SafetyAuditTest at.heinz.deviceai.research.exp20.p3.P4MathTest   # erwartet 42/42
node --input-type=module -e "import('./.github/continuum/cre-parity.mjs').then(m=>m.prepare('/tmp/cre1'))"   # CRE1-Parity-Checkpoint, SHA-256 2290194d…0db6
```

Neue Kotlin-Dateien unter `app/src/test/java/at/heinz/deviceai/research/exp20/` werden vom Skript automatisch mitkompiliert. Neue Produktivabhängigkeiten werden nur dann in die `CLOSURE`-Liste des Skripts aufgenommen, wenn sie Android-frei sind.

## 5. Abschlussbericht, den ich von dir erwarte

- Commit-SHAs mit Readback, Draft-PR.
- Befehle mit Exit-Codes, PASS/FAIL je Test.
- Absolute Trefferzahlen, b/c, Holm-p, KI und Urteil je Vergleich.
- Ergebnis der abfrageblinden Kontrolle.
- Kosten (Host).
- Klar getrennt: Host und Fold7.
- Was **nicht** lief und warum.
- Nächster Schritt nach diesem Plan.
