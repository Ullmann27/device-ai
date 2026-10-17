# Device AI Continuum — stabiler Ausgangspunkt

**Stand:** 17. Oktober 2026. Entwicklungsort: [PR #19](https://github.com/Ullmann27/device-ai/pull/19), Branch `copilot/pr-12-working-first-vertical-slice`.

## Ziel dieser Phase

**Keine neuen Kernfunktionen.** Vor einer neuen Forschungs- oder Produktentwicklungsphase stehen Stabilität, Wiederanlauf, klare Zuständigkeiten, reproduzierbare Tests und ein installierbares Android-Artefakt. Der produktive Git-Branch `main` bleibt unangetastet; Kernänderungen werden in PR #19 geprüft.

Ein erfolgreicher `git push`, Quelltextscan, Hash-Vergleich oder CI-Run ohne Job-Schritte ist **kein** Nachweis für einen grünen Build.

## Zustand des Fundaments

| Bereich | Ist-Stand | Abnahmebedingung |
| --- | --- | --- |
| SWB1 / persönliche Erinnerungen | Bestätigungs- und Widerrufsgrenzen vorhanden | Kotlin-Regressionen, Neustart, Widerruf und Offline-Abruf prüfen |
| CRE1 | Nur mit gebundenem Manifest und unabhängigen Prüfdaten aktivierbar; ohne Manifest Schattenmodus | Kein künstlicher oder unversiegelter Freigabebericht |
| CDX1 | Evidenzkonditioniertes Forschungslabor; keine Chat-Autorität | Belege, Zahlentreue, Negative, Verluste und Gerätemessungen prüfen |
| Android-Backlog | Eindeutige Activity-Result-Codes und gemeinsame Kotlin/Node-Regeln | Import/Export auf realem Gerät und Dateirundreise testen |
| Reparaturpipeline | Isolierter Worktree, Kritiker, Anti-Revert-Priorität und Node-Syntaxvorprüfung | Echte fehlerhafte Fixture lokal reparieren; Regressionen dürfen nicht abgeschwächt werden |
| Lokaler Worker | Stündlicher Node-Zyklus und Backlog-Recovery vorhanden | SIGKILL-/Lock-Tests, Wiederanlauf und idempotente Folgezyklen |
| Android-APK | Kein nachgewiesener Build am gegenwärtigen PR-Head | Kotlin-Tests, Debug-Build, APK-Prüfbericht und Installation |

Die bestehenden Tests dürfen beim Reparieren nicht entfernt, auf `.skip` gestellt oder abgeschwächt werden. Historische Experimente, Gewichte, Erinnerungen und Benutzerdateien werden nicht „auf Verdacht“ gelöscht.

## Ein einziger verbindlicher lokaler Qualitätslauf

Das Repository hat bereits den Offline-Milestone-Gate-Runner `.github/continuum/m4-local-gate.mjs`. Er prüft in einem **separaten Git-Worktree**. Die schnelle Repair-Kette bleibt unverändert; der vollständige `--android`-Lauf verlangt jetzt:

1. Ausgeführte Continuum-Node-Regressionstests mit JUnit-Belegen.
2. Ausgeführte separate Backend-Node-Regressionstests mit JUnit-Belegen.
3. Kotlin-Compilation und Android-JVM-Tests mit wirklich ausgeführten Testfällen.
4. Erfolgreiche Gradle-Debug-APK-Erzeugung, gespeichertes APK-Artefakt, Dateigröße und SHA-256.
5. Unveränderten, sauberen Ausgangscheckout und einen Prüfreport außerhalb des Repositories.

Für den lokalen Rechner: **Node.js 20+, Git, JDK 17, Gradle 8.11.1, Android SDK Platform 36** und ausreichend freien Speicher bereitstellen. Das Repository enthält keinen Gradle-Wrapper; der Runner erwartet ein installiertes `gradle`.

```bash
# Innerhalb eines sauberen Checkouts der PR-Branch ausführen:
node .github/continuum/m4-local-gate.mjs \
  --root . --android --budget-minutes 90 \
  --out "$HOME/.local/state/device-ai-continuum/proofs"

# Vom vorigen Kommando ausgegebenen absoluten Report-Pfad einsetzen:
node .github/continuum/m4-local-gate.mjs \
  --verify "/ABSOLUTER/PFAD/zu/gate-report.json"
```

Die **Verifikation** setzt den Prüfbericht in Beziehung zum **aktuellen Git-HEAD**. Sie prüft die interne Berichtskonsistenz, den SHA-256-Berichtsfingerabdruck, die ausgeführten Node-/Backend-/Android-Testprofile sowie die APK-Datei samt SHA-256 im selben Proof-Verzeichnis. `NODE_ONLY` bleibt ausdrücklich **nicht** freigabefähig. Ungültige oder beschädigte Reports und fehlende APKs führen zu einer Ablehnung.

**Grenze:** Ein selbst erzeugter SHA-256-Hash ist keine digitale Signatur oder unabhängige Bestätigung der Testdurchführung. Das Gate belegt die von ihm protokollierten lokalen Schritte; der praktische Test **Installation, Berechtigungen, Start/Neustart und Verhalten am Android-Gerät** muss zusätzlich erfolgen.

## Stabilisierungsnachtrag – explizite Release-Profilverträge

Die lokale Abnahme akzeptiert im vollständigen Android-Modus nur genau die
vorgeschriebenen Profile in dieser Reihenfolge:

- `node-continuum`: tatsächlich ausgeführte Continuum-Regressionstests;
- `node-backend`: tatsächlich ausgeführte Backend-Regressionstests;
- `android-compile`: erfolgreiche Android-Kotlin-Kompilierung mit Exitcode 0;
- `android-unit`: tatsächlich ausgeführte und erfolgreiche Android-JVM-Tests;
- `app-debug.apk`: erfolgreicher Gradle-Debug-Build mit gespeichertem Artefakt,
  gemessener Byte-Größe und geprüftem SHA-256.

Fehlende, doppelte oder ersetzte Prüfprofile verhindern die Freigabe. Node
darf fehlende Testverzeichnisse nicht durch automatische Suche in anderen
Projektteilen kompensieren. Symlink-Testdateien sind von der festen lokalen
Testauswahl ausgeschlossen. Ein reiner `NODE_ONLY`-Report bleibt
`releaseReady=false`, selbst wenn alle enthaltenen Tests grün sind.

Am 17.10.2026 wurden die tatsächliche JavaScript-Entscheidungsfunktion des
Release-Gates in **8** isolierten Funktionsprüfungen und die tatsächlichen
Continuum-/Backend-Testdatei-Selektoren in **6** isolierten Funktionsprüfungen
erfolgreich geprüft. Außerdem bestanden Parserprüfungen der vier geänderten
Node-Dateien. Diese Teilprüfungen ersetzen ausdrücklich **keinen**
vollständigen Git-Checkout-Testlauf oder Android-Build.

## Warum GitHub Actions aktuell keine Quelle für Build-Aussagen ist

Die zuletzt eingesehenen Runs zu PR #19 melden `failure`, aber die abgefragten Jobs haben **0 ausgeführte Schritte**. Somit existieren daraus weder ein ausführbarer Compilerfehler noch ein bestätigter Testdurchlauf. Die konkrete externe Ursache der Runner-Blockade ist **nicht gesichert**. Ein funktionierender lokaler Testlauf ist der vorrangige, cloudunabhängige Weg.

## Stop-Regeln bis zum nächsten Projektstart

- Keine neuen KI-Architekturen, Trainingsdaten, App-Grafiken oder großen Refactorings.
- Kein unbelegtes Modell-Promotion-Manifest und keine Freigabe experimenteller Antworten.
- Keine automatische Änderung von `main` oder Produktions-Artefakten.
- Kein Entfernen von Tests, Schutzrichtlinien, gesammelten Gewichten oder Speicherdateien ohne Nachweis.
- Kein Kennzeichnen einer Version als „fertig“, bevor Node, Backend, Kotlin, APK und Gerätetest tatsächlich bestanden sind.

## Abschluss und neuer Start

Die Stabilisierung ist **erst abgeschlossen**, wenn der vollständige lokale Qualitätsreport mit **aktueller Commit-SHA** grün und erneut verifizierbar ist, das APK auf einem Testgerät installiert und nach Neustart getestet wurde, Rückfälle aus Erinnerung/Widerruf/Backlog/Worker reproduzierbar ausgeschlossen wurden und die verbleibenden Risiken als dokumentierte offene Punkte vorliegen.

**Bis dahin:** PR #19 bleibt ein Review-/Entwicklungsbranch, kein Produktionsrelease.