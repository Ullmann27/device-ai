# Continuum OMEGA – laufender, übernehmbarer Arbeitsstand

Stand: 25.10.2026, erster veröffentlichter Forschungscheckpoint. **Kein Release.**

## Einstieg

- Repository: https://github.com/Ullmann27/device-ai
- Arbeitsbranch: `research/continuum-omega-vhsr-20261025`
- Basis: `integration/continuum-m6-all-verified-20261025`, PR #132.
- Vor Beginn und vor dieser Sicherung live bestätigter Basis-HEAD: `821df5d1acf68f0caf10369298352a3589f56a97`.
- `main`: `190f5eabaa60fc59292b2b80d3086aafd7743a80`, 24 eigene Commits gegenüber der Integrationslinie. Nicht gemergt.
- PR #131 und #132: offen, Draft, gleicher HEAD; Ziele `release/continuum-owner-0321-20261024` bzw. `chatgpt/continuum-m6-run-binding-preflight-20261025`.
- Arbeitskopie dieser Sitzung: `/workspace/device-ai`; neue Belege unten im Repository. Keine Abhängigkeit von dieser lokalen Kopie für die Fortsetzung.

## Was schon geschehen ist

`AGENTS.md`, Experimentvertrag, vollständiges Experimentregister, Issue #41, aktuelle Übergaben, PR-Beschreibungen und alle PR-Diskussionsbeiträge gelesen. Vollständiges Repository einschließlich Branchhistorien geklont. Tatsächliche Coding-/Repair-/Bridge-/Lernpfade untersucht. **In diesem ersten Checkpoint gibt es noch keine neue OMEGA-Implementierung und keinen behobenen Produktfehler.** Alle sechs Forschungsrichtungen sind noch Entwurf; keine zusätzliche KI, Gewichtsänderung oder automatische Wissenspromotion.

### Reale Prüfungen der unveränderten Basis

Belege: [`evidence/baseline-summary.json`](evidence/baseline-summary.json), vollständige Node- und Backend-Ausgaben und `SHA256SUMS` im selben Verzeichnis.

```bash
env -u NODE_TEST_CONTEXT node --test --test-concurrency=2 .github/continuum/*.test.mjs scripts/*.test.mjs
npm --prefix backend ci --ignore-scripts --no-audit --no-fund
env -u NODE_TEST_CONTEXT node --test --test-concurrency=2 backend/test/*.test.js
```

- Node v24.19.0, Linux x64: **1190 Tests, 1181 bestanden, 3 fehlgeschlagen, 6 übersprungen**; 52.889 s. Zwei unterschiedliche Fehler; die Bridge-Datei wird zusätzlich von einem anderen Test importiert und deshalb doppelt gezählt.
- Backend: **60/60 bestanden**, 0 übersprungen, 1.293 s.
- Die sechs Node-Skips benötigen `kotlinc`, beim Baseline-Lauf nicht auf PATH.
- GitHub Actions Run `40173640084`: Node failure vor dem ersten Schritt, Android skipped. Check-Run `121109606842` hat die konkrete Annotation: **"The job was not started because recent account payments have failed or your spending limit needs to be increased. Please check the 'Billing & plans' section in your settings"**. Das frühere unbekannte Runnerproblem ist damit auf diese Account-/Budgetblockade eingegrenzt. Keine Kontoeinstellungen verändert.
- GitHub-Commit-Abfragen für `53dfe75a` und `4f01eab3`: jeweils HTTP 422, kein Commit gefunden. Keine Originalinhalte rekonstruiert oder als integriert behauptet.

### Reproduzierte Fehler, noch nicht behoben

1. `.github/continuum/code-analyzer.mjs`: `collectKotlin` verwirft Dateien über 400000 Bytes still. `AgentActivity.kt` hat **401879 Bytes** und fehlt deshalb im Analysegraphen. `code-analyzer.test.mjs:74` findet den real vorhandenen Lernpfad nicht. Ein stilles Weglassen kann weitere falsche Verwaist-Meldungen verursachen. Geplante Reparatur: nachvollziehbar begrenztes höheres Dateilimit, konsistente Byteprüfung und explizites Scheitern bei Überschreitung.
2. `.github/continuum/typed-android-coding-wiring.test.mjs:40`: veralteter Quelltext-Regex erwartet `status:verified?`; die bereits gehärtete Bridge verwendet `reviewable` und prüft zusätzlich die Patch-Datei. Sicherheitsprüfung nicht zurückbauen; stattdessen tatsächliches Verhalten prüfen.
3. `scripts/continuum-m6-review-qa.sh` verwendet `bash ./gradlew`, obwohl kein Gradle-Wrapper im Baum existiert. Noch nicht als Android-Lauf ausgeführt. Installierte, explizite Gradle-Toolchain nutzen; kein APK-Task.
4. Backend-Paketinstallation erzeugt unignoriertes `backend/node_modules/`; das widerspricht dem abschließenden Clean-Checkout-Gate des QA-Scripts. Abhängigkeiten nicht committen; passende Ignore-Regel vorsehen.

## Prüfstand und sichere Fortsetzung

- Lokale Entwicklungsabhängigkeiten unter `/workspace/continuum-tools`, keine Besitzerdateien. JDK 21 bereits vorhanden. Offizielles Android CLI `13114758`, SHA-256 `7ec965280a073311c339e571cd5de778b9975026cfcbe79f2b1cdcb1e15317ee`, sowie Gradle 8.11.1 heruntergeladen und Hash geprüft; SDK 36 und Build Tools 35.0.0 installiert.
- **Android-Unit/Lint/Compile noch nicht gelaufen.** Reproduktionsvorlage: vorhandenes `scripts/render-continuum-all-connected-qa-20261024.sh` enthält offizielle Downloadadressen und Versionen, aber nicht als Ganzes ausführen: es baut eine Preview-APK. Ausschließlich `:app:testDebugUnitTest :app:lintDebug :app:compileDebugKotlin` verwenden; keine Preview-/Signing-Optionen.
- Docker-Daemon 28.4.0 erreichbar. Noch kein neuer Container-/Modellversuch.
- Render-Dienste read-only geprüft. Bestehende QA-Dienste sind an ältere Branches gebunden; Android-QA-Kommandos enthalten teilweise `assembleDebug`. Deshalb bislang keine dieser Konfigurationen verändert oder gestartet. Lokal ist ein geeigneterer Prüfstand vorhanden.
- Keine Verbindung zum Fold7 in dieser Sitzung; Kugelbewegung, Termux-Rückkanal und aktuelle Gerätefunktion bleiben ungeprüft.

## Nächste Schritte in Reihenfolge

1. Die beiden reproduzierten Node-Ursachen mit echten Regressionen beheben; QA-Launcher/Abhängigkeitsverzeichnis reparieren. Baseline-Belege unverändert erhalten.
2. Gesamte Node-/Backend-Suite und echte Android-Unit/Lint/Compile-Prüfung durchführen; fehlende Prüfungen präzise ausweisen.
3. Architekturkarte anhand von Aufrufern und Tests vervollständigen. SWB1 ist über `ContinuumLearningCore` erreichbar; CDX1 hat explizite Forschungsaufrufe. `ExperienceCompiler` und CEFR existieren schon und sollen nicht dupliziert werden. EWC ist bisher nicht in den tatsächlich untersuchten aktiven Lernquellen nachgewiesen.
4. VH-SR als explizit aktivierten Forschungsadapter derselben `repairSession` bauen: unabhängige Invarianten/Gegenbeispiele, unveränderte Standardtests, feste Budgets, Enthaltung bei unvollständiger Evidenz. Zunächst die bereits zugelassene endliche numerische Kandidatensprache verwenden. Kein eigener CCC1-/Backlog-/Receipt-Stack.
5. Unveränderte M6-Schleife und VH-SR unter gleichem Kandidatenbudget vergleichen; kontrollierte Kandidaten klar von echter Qwen-Inferenz unterscheiden. Öffentlich gewordene Aufgaben nicht als blinden Holdout bezeichnen.
6. Zeilen-Codec nur gegen vorhandene `oldText/newText`-Schnittstelle bei Temperatur 0 testen; Vorteil nicht voraussetzen. CEFR als vorhandene Grundlage für RPK1 prüfen, danach Skill-Abstraktion/EIG-SC; übrige OMEGA-/AEGIS-/ARCHETYP-Bausteine mit klaren Reifegates planen.
7. Jeden verwendbaren Schritt committen und auf diesen Branch pushen; Draft-PR aktualisieren. Kein Force-Push und kein Merge.

## Eigentümergrenzen und Rollback

Besitzerpaket `at.heinz.deviceai.mentorpaired`, Updatekonfiguration 0.3.21 / 22, Signierschlüssel, Gewichte, persönliche Erinnerungen, installierte App und `main` unverändert. Dies ist autorisierte Forschung auf eigenem Entwicklungsbranch. Rückfall: Forschungsbranch nicht übernehmen; Baseline-SHA oben bleibt reproduzierbar. Keine älteren Zweige oder Belege löschen.
