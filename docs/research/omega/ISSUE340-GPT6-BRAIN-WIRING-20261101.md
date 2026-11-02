# Continuum GPT-6 — Anschlussrunde an PR #341 (01.11.2026)

## Arbeitsursprung und ältere Übergaben

Offizielles Repository: `Ullmann27/device-ai`. Baseline dieses Branches: `6e3857e4b2a64295cf225b4d848d2d7cafb693ca` (Draft-PR #341), nicht das unveränderte `main`. Originalsignierte Geräte-App weiterhin `0.3.36` / versionCode 37; kein neuer Besitzerbuild oder Modellimport.

Wiederverwendete, tatsächliche Quellpläne:

- Fable #334: `docs/research/omega/FABLE51-GENESIS-MASTER-PROMPT-NEXT-AGENT.md` — vorhandene Beleg-/Dialog-Brücken, CDX2 nur isoliert und mit Blind-C, kein zweiter Memory-Store.
- Sol #335 und #341: `docs/research/omega/ISSUE340-SOL-ASTRA-HANDOFF.md` — Graph/Kommits, M1 Sitzungsbeleg/Terminalroute, CDX2-Freeze, M6 gezielte PR337-Übernahme.
- Opus: `docs/CONTINUUM-MODULE-WIRING-AUDIT-20261024.md` — echte Aufrufer statt bloßer Dateipräsenz; produktive und Shadow-Module unterscheiden.
- Opus/Argon: `docs/CONTINUUM-VERIFIER-FIRST-SPINE-ARGON4-20261023.md` — Snapshot-/Quellenidentität und unabhängiger Prüfer, keine unbescheinigten Fakten.
- Opus CCC1: `docs/CONTINUUM-OPUS-TO-GPT6-HANDOFF-20261023.md` — Termux-/CCC1-Quittungen, lokale Verifikation, keine ungeprüften Reparatur-Merges.
- Historisches Sol M6: `docs/CONTINUUM-M6-INTEGRATION-REGISTER-20261025.md` — echte Quellabstammung, kein verstecktes Branch-Overwrite.

Historische Dokumente enthalten frühere Geräteversionen und Testzahlen. Der jetzige Fold7-Stand aus #339 und die aktuellste Git-DAG haben Vorrang. `#341` liegt als offener, separater Draft vor. Dieser neue Folgebranch schreibt nicht in #341 oder `main`.

## Drei tatsächliche Meilensteine dieser Runde

### R1 — Sicherer nachgelagerter Beleg- und Erinnerungspfad

`CertifiedLocationReflex.answer`: Literal aus dem expliziten Beleg ohne nachträgliches `trim()` für SHA/UTF-16-Offsets. `answerFromConfirmedFacts`: benannte Ortswiderrufe/Umzüge blockieren alte zertifizierte Orte, selbst wenn sie wegen fehlendem `in` zuvor aus dem geprüften Faktenset verschwanden. Keine Gewichtsmutation, keine Toolrechte, keine zweite Gedächtnisdatenbank.

`ExplicitEvidenceAndRevocationTest.kt` enthält vier neue gezielte JUnit-Testfälle; sie werden erst nach einem source-exakten Kotlin-/Gradle-Lauf als bestanden erklärt. Ein zunächst fehlerhaft maskierter Raw-Regex wurde korrigiert; **Kotlin 1.9.0 Host-Smoke** auf dem exakten Pattern: vier Widerrufsformen erkannt, zwei Negativkontrollen ohne Treffer. Die komplette Android-JUnit-Suite auf diesem Branch: **NOT_RUN**.

Grenze: Diese Schutzlogik sieht nur Fakten, die der bestehende bestätigte SWB1-Speicher zur Beweisprüfung auswählt. Ein nicht abgerufener Widerruf bleibt ein höherstufiges Retrieval-/Revisionsproblem. Keine Behauptung, dass alle historischen Fakten global abgeglichen sind.

### R2 — CDX2 unabhängige Sicherheitsmetrik + 100 negative Manipulationsfälle

`scripts/cdx2-blind-score.mjs` akzeptiert ausschließlich eingefrorene Quelllabels und separat erzeugte `RAW`, `VERIFIED`, `RULE`-Vorhersagen. Corpus-SHA wird vor Auswertung mit dem historischen Freeze `3bd5bddb76a50d020c9216db9eebf7ba9b717967a7b0947b3415f3a2c0fd5a1c` verglichen. Alle Case-IDs müssen einmalig und vollständig sein. Ein Treffer erfordert den exakten Originaltext mit Quelle-ID/Revision/SHA/UTF-16-Spanne; zusätzliche falsche positive Behauptungen, falsche Quellidentitäten, Enthaltung und Malformed-Outputs werden separat gezählt. Wilson 95-%-Intervalle, striktes Primärgate, keinerlei Modellpromotion.

`scripts/cdx2-blind-score.test.mjs` mit **11 lokalen erfolgreichen Node22-Tests**, darunter eine Schleife mit **100 deterministischen unterschiedlichen Provenienz-/Positionsmutationen** (alle korrekt als fehlerhaft erkannt). Das sind 100 echte negative Testproben **des Scorers**, keine 100 neuen trainierten Modelle und keine 100 Besitzergeräte-Experimente.

Wichtig: Der eigentliche `55`-Fälle-Blind-C-Lauf des isolierten Kotlin-Pointer-Kandidaten ist noch **NOT_RUN**. Dessen `11` Parameter/Handgrammatik sind keine reparierte CDX1-Antwortgenerierung. Keine Prozentangaben zur Modellintelligenz.

### R3 — Vorhandene Verbindungen aus alten Branches als Release-Gate

`scripts/continuum-brain-connection-audit.mjs` erfasst **15 konkrete Aufrufer-/Quellenkanten** der existierenden Haupt-App: Session-Evidenz → lokaler zertifizierter Reflex → SHA/Verifier/Rollenprüfung, bestätigter SWB1-Faktenspezialist, `ContinuumCognitiveRuntime` → `ContinuumLearningCore`, OFF/ASK/ALWAYS_ON-Consent und lokales Gemma-Modell. Vorhandene CDX2-/Ranker-Forschungsmodule sind explizit `RESEARCH_SOURCE_PRESENT_NOT_PROMOTED`.

Alle **15/15** statischen Marker wurden zusätzlich direkt gegen die aktuellen GitHub-Dateien geprüft; **3 lokale negative Node-Tests** beweisen, dass fehlende oder bloß deklarierte Kanten nicht als aktiv gelten. **Es handelt sich um STATIC_SOURCE_ONLY, nicht um 15 bestandene Geräte-End-to-End-Tests.**

Das bestehende `scripts/render-continuum-m8-qa.sh` ist auf dem isolierten Branch um die 14 neuen Node-Tests, den vollständigen Auditrept und die SHA-Prüfung des eingefrorenen Blind-C-Korpus erweitert. Keine alten Prüfungen entfernt, keine Signierung/Installation aktiviert.

## Ausgeführte Prüfungen / Statusklassen

| Test | Codebasis | Ergebnis |
|---|---|---|
| Node22 scorer+Wiring (11+3) | Lokale gleichwertige Forschungsdateien | **14/14 PASS**, inkl. 100 negativer Mutationen |
| Kotlin-Pattern (Host JDK21/Kotlin1.9) | Widerrufs-Regex | **6/6 Patternproben PASS** |
| GitHub Quellaufrufer | neuer Forschungsbranch | **15/15 STATIC_CALL_FOUND** |
| Vollständige Kotlin-Gradle-JUnit/lint auf diesem Branch | neue Folgeänderungen | **NOT_RUN** |
| CDX2 55 Blind-C reale Kotlin-Vorhersagen | eingefrorener Kandidat | **NOT_RUN** |
| Fold7 E2E & original signierte APK | Besitzerkanal | **NOT_RUN / NOT_RELEASED** |
| Render QA für Elternstand `6e3857e4` | PR #341 vor Folgeänderungen | **PASS**, vom unabhängigen Render-Dienst bestätigt |

Der ältere Render-Run von PR #341 ist ein echtes **source-exaktes** Build/Unit/Lint/unsigned-Artefakt für `6e3857e4`, **kein** QA-Ergebnis dieses neuen Branches. Der Render-QA-Vertrag für diesen neuen Branch verlangt die 14 neuen Node-Tests, 15 statischen Kanten und frozen SHA und bleibt solange unbestanden, bis ein neues Source-exaktes Ergebnis vorliegt.

## Nächste Arbeitsschritte in technischer Reihenfolge

1. Vollständige Quelle/JUnit/lint auf exaktem neuen Commit prüfen; zugehörigen Render-Receipt archivieren; jede fehlgeschlagene Probe tatsächlich reparieren, nicht überspringen.
2. Gefrorenen CDX2-Kandidaten, Checkpoint und 55 Blind-C-Fälle unverändert reproduzieren. Vorhersagen aus **wirklichem Kandidaten** einmalig speichern; erst dann mit dem neuen unabhängigen Scorer auswerten (RAW/VERIFIED/RULE getrennt). Keine Schwellenänderung nach Offenlegung.
3. Bestätigte SWB1-Fakten-Auswahl gegen nachträgliche Widerrufe/Zeitkollisionen über **alle Revisionen** prüfen, nicht nur die gerade ausgewählten Kandidaten. Keine Web-/LLM-Antwort automatisch als Besitzerfakt.
4. Aus #339 offene F08-Nichtmodell-Turn-Erfassung, F14-Not-Stopp, F16 deaktivierten Builder und CCC1-Termux-Checkout in begrenzten separaten PRs mit eindeutiger Laufzeitbelegung beheben; Code-Marker ersetzen keine Geräte-Quittung.
5. Erst nach vollständigem gemeinsamen Host-/Render-/Fold7-Test einen höheren versionCode als 37 mit identischer Paketkennung und Originalbesitzerzertifikat veröffentlichen, ausschließlich auf ausdrückliche Besitzerfreigabe.

**Übergabe für jeden neuen Agenten:** `Ullmann27/device-ai`; PR #341 (Eltern), diesen Folge-Draft-PR (Kind), Issue #339 Fold7-Fakten, Issue #340 Ziel; immer `git fetch`, Quell-/Git-DAG prüfen und an den bestehenden Beleg-/Gedächtnis-/CCC1-Gates andocken. Keine Force-Pushs und kein Main-Merge. Resultate nach realen Messungen dokumentieren, nicht nach überlieferten Prozentangaben.
