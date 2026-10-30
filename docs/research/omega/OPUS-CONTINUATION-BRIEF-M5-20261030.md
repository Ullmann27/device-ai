# Weiterbearbeitungsauftrag · Continuum M5 „Semantik“ (Stand 30.10.2026, Opus)

**Einstieg:** PR #256, Branch `claude/continuum-jarvis-exp20-p2-kxrxy2`. Die letzte gepushte Spitze steht im PR-Verlauf. Vor Arbeitsbeginn live lesen und die SHA rücklesen.

**Pflichtlektüre in dieser Reihenfolge:**
1. dieser Auftrag;
2. [Meilenstein-Bericht](../exp20/EXP-20-MILESTONE-M1-AND-NEXT-20261030.md);
3. [P3](../exp20/EXP-20-P3-RESULT-20261030.md);
4. [P4](../exp20/EXP-20-P4-RESULT-20261030.md);
5. [Übergabe](OPUS-EXP20-P2-HANDOFF.md).

**Nicht ändern:** versiegelte P2- und P3-Dateien, Encoder, Gewichte, Freeze und Entscheidungsregeln.

## 1. Einordnung in den Meilensteinplan (Bilder vom 30.10.2026)

| Roadmap | Stand | Beleg |
|---|---|---|
| M4 · P2-Labor | **abgenommen als Host-Labor** | A 153 / A0 174 / B 146 / C 149 von 280, Regel → A; Sicherheit 7 680/7 680 CLARIFY; 40/40 Host-Tests; ToolGate 14/14 |
| Neuro P1 · Robuste Semantik | **offen, nächster Schritt (M5)** | P3: kein lokaler Encoder erkennt Synonyme (≤ 14/60) |
| Neuro P2 · Hippocampus / DG-SDR | **REJECTED auf heutigen Encodern** | P3: −12 / −18 pp |
| Neuro P3 · Dendritisches Gating | NOT_STARTED, wartet auf P1 | Vorbedingung nicht erfüllt |
| Neuro P4 · Sparse Hopfield / BitLinear | Hopfield-1 rangidentisch, Hopfield-2-Gewinn = **Medoid-Artefakt**, INT8 und Sign **REJECTED** | P4 + `P4MedoidControl` |
| Neuro P5–P7 | NOT_STARTED | – |
| Offene Alpha-Gates (Gradle, Fold7-APK, VOICE-1/Worker, Thermal) | **Infrastruktur, kein Agentenaufwand:** Android-SDK in der Cloud gesperrt (`dl.google.com` 403), Fold7 nur mit echtem Gerät | – |

**Einziger belastbarer Kandidat:** der **vorhandene** `HybridMemoryRetriever` mit dem **vorhandenen** optionalen `semanticScorer = Cre1InferenceEncoder.score` (P4: +15,8 pp gegenüber dem lexikalischen Modus). Er braucht keine neue Neuronenkomponente, muss aber auf neuen Daten bestätigt werden.

## 2. Arbeitspakete (Aufwand in 10-%-Einheiten einer Opus-Sitzung mit extra-hohem Reasoning; Schätzung aus dieser Sitzung, keine Messung)

| # | Paket | Einheiten | Abnahme |
|---|---|---|---|
| WP1 | **Neuer M5-Datensatzvertrag:** Generator ohne Medoid-Struktur (Lockvögel als Zentren eigener Near-Miss-Sterne), neue Synonymliste, Splits DEV/VAL/SEALED, **abfrageblinde Medoid-Kontrolle als Pflichtarm**, TSV-Slot für fremden Holdout. Vorregistrierung committen, bevor ein Arm läuft. | 1 | Prereg-Commit plus Daten-Hashes gepusht |
| WP2 | **Semantischen Basisstrom schaffen** (isoliert, nur Forschungsgewichte): CRE1-Training auf einem registrierten Paraphrasen-Curriculum über die vorhandenen `train`- und `trainParityRounds`-Pfade in `.github/continuum/cre-*.mjs` bzw. das Kotlin-Gegenstück; Checkpoint-SHA festhalten. Dann **P3-Vorbedingung** auf WP1-DEV/VAL. | 2 | P3-Vorbedingung `PASS` auf VAL, oder dokumentierter Abbruch |
| WP3 | **Bestätigung `HybridMemoryRetriever` + CRE1** gegen HYBRID_LEX, JACCARD und die Medoid-Kontrolle auf WP1-SEALED, einmal öffnen. Kosten (p50/p95, Allokation) messen. | 1 | Urteil nach Vorabregel |
| WP4 | **Zeit-Kontext M2:** Arm B mit gelernten Ereignis-Detektoren (L2-Logit je P2-Merkmal auf WP2-Repräsentation) gegen A und A0. Ablationen C_diag, C_sameTau, deterministischer Automat. Bewertung erst auf einem **fremd verfassten** Holdout. | 2 | `SUPERIOR` gegenüber A **und** A0 auf dem fremden Holdout, sonst Verwerfung |
| WP5 | **Sicherung:** Ergebnisberichte, Register-Zeile EXP-20, PR-Text, Handoff, SHA-Readback. | 0,5 | Readback-SHA = lokale Spitze |

**Gesamt etwa 6,5 Einheiten.** Bei nur ~20 % Restbudget **nur WP1 und WP5** ausführen.

## 3. Feste Regeln

- **Jedes** Assoziativ- oder Kontextexperiment bekommt eine abfrageblinde Kontrolle. Das ist die Lehre aus P4.
- Ein Gewinn gegenüber dem Router allein reicht nicht. Verglichen wird immer gegen **A0** (Encoder + Δt) und **B** (EMA). Rekurrenz zählt nur, wenn C gegenüber B `SUPERIOR` ist (W=0 ≡ B).
- Synthetische Erfolge sind **kein** Grund für Produktivänderungen. Ein Shadow-Zähler (Flag AUS) bzw. ein Δt in `advise()` erst nach positivem fremdem Holdout **und** Besitzerfreigabe.
- `DialogStateKernel.wouldClarify` **nicht** an `unresolvedReference` hängen; das ist nachweislich schädlich.
- Der Risk-Shim nur in `$OUT` für die Host-Kompilierung, nie in `app/src`.
- Unverändert bleiben ToolGate, OwnerGate, EWM2, VOICE-1 und `CognitiveGenome` (`allowSelfReplication`, `allowHiddenPersistence`, `allowShutdownEvasion` = false).
- Kein Main-Merge, kein Force-Push, keine APK. Keine weiteren Forschungsbranches; auf #256 weiterarbeiten oder einen Draft-PR auf #256 stapeln.

## 4. Startbefehle

```sh
# kotlinc 2.0.20 (JetBrains-Release, SHA-256 5f5d2a8a…5931); JUnit/Hamcrest aus /opt/gradle-8.14.3/lib
export KOTLINC=.../kotlinc/bin/kotlinc JUNIT_JAR=/opt/gradle-8.14.3/lib/junit-4.13.2.jar HAMCREST_JAR=/opt/gradle-8.14.3/lib/hamcrest-core-1.3.jar OUT=/tmp/exp20
WITH_ARGON_TEST=1 scripts/exp20-p2-host-run.sh build
scripts/exp20-p2-host-run.sh junit at.heinz.deviceai.research.exp20.BoundedCtrnnAliasingTest at.heinz.deviceai.research.exp20.BoundedCtrnnTest at.heinz.deviceai.research.argon4.Argon4F1PrototypeTest at.heinz.deviceai.research.exp20.p2.P2InfraTest at.heinz.deviceai.research.exp20.p2.P2SafetyAuditTest   # 40/40
node --input-type=module -e "import('./.github/continuum/cre-parity.mjs').then(m=>m.prepare('/tmp/cre1'))"   # node-cre1.cre1, SHA 2290194d…
scripts/exp20-p2-host-run.sh main at.heinz.deviceai.research.exp20.p3.P4Associative docs/evidence/exp20-p3-20261030/p3-dataset.tsv /tmp/cre1/node-cre1.cre1 /tmp/p4.txt   # Reproduktion P4
```
