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

## Weitere Schutzmaßnahmen – Fail-Fast und APK-Integrität

Das lokale vollständige Release-Gate verfügt jetzt über eine verbindliche
**Abhängigkeitsreihenfolge**: Die Android-Kompilierung wird nur nach erfolgreich
ausgeführten `node-continuum`- und `node-backend`-Profilen gestartet; ein
APK-Build wird nur gestartet, wenn zusätzlich `android-compile` und
`android-unit` erfolgreich waren. Fehlende, vertauschte oder als „erfolgreich“
markierte Profile ohne ausgeführte Tests werden zurückgewiesen. Ein
fehlgeschlagener vorgelagerter Test erzeugt einen dokumentierten Fehlerbericht;
Gradle wird in diesem Fall nicht gestartet.

Nach erfolgreicher APK-Erzeugung verarbeitet `sha256ApkFile` das Artefakt
blockweise (64 KiB), begrenzt die Dateigröße auf 250 MiB, schließt Symlinks
aus und kontrolliert den Dateideskriptor über `fstat`. So muss der
Qualitätslauf keine komplette APK in den Arbeitsspeicher laden. Die spätere
`--verify`-Prüfung verwendet denselben SHA-256-Dateileser.

**Prüfnachweis dieser Runde:** Die neuen JavaScript-Dateien bestanden die
Parserprüfung; die tatsächliche Node-Funktion `nextGateStage` bestand fünf
gezielte Quelltext-Funktionstests. Eine lokal in Node.js reproduzierte
`sha256ApkFile`-Funktion bestand zehn Testfälle für verschiedene Dateigrößen,
Symlinks und leere/fehlende Dateien. **Nicht ausgeführt:** der vollständige
GitHub-Checkout-Testlauf, der in GitHub gespeicherte komplette Node-Testcode,
Gradle/Kotlin-Tests und eine reale Android-APK. Die Regressionstests sind
im Entwicklungsbranch hinterlegt und müssen dort noch ausgeführt werden.

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


## Stabilisierungsnachtrag – Worker-Ergebnisdisziplin und Release-Profilparität (17.10.2026)

Die lokale Teamleiter-Schleife akzeptiert nur explizit bekannte, abgeschlossene
Ergebniscodes. `RUNNING`, fehlende und unbekannte Ergebnisse werden als
`ERROR` gezählt und nicht als erfolgreiche Entwicklungsarbeit ausgegeben.
`NO_VERIFIED_REPAIR` und `INTERRUPTED` erhöhen wie `CHECKS_FAILED` und
`ERROR` den Fehlerzähler. Ein während der Wartephase empfangenes
Stoppsignal speichert einen abschließenden `STOPPED`-Heartbeat mit
`nextAttemptAt=null`, statt einen weiter laufenden Timer vorzutäuschen.

Das M4-Gate nutzt in `gateSummary` und `nextGateStage` dieselben
verbindlichen, geordneten Profilprüfungen. Auch ein reiner Node-Bericht muss
aus dem echten `node-continuum`-Profil stammen. Die Android-Releaseabnahme
verlangt `node-continuum`, `node-backend`, `android-compile` und
`android-unit`, erfolgreiche Exitcodes, ganzzahlige Testanzahlen und
keine Timeouts. Ein gespeicherter Bericht kann einen fehlgeschlagenen
Prozess deshalb nicht allein mit `passed=true` zu einem Erfolg umdeuten.

**Prüfumfang dieser Korrektur:** Drei gezielte lokale Node-Regressionstests
zum Worker bestanden; acht weitere funktionale Assertions wurden mit dem
aktuell auf GitHub gespeicherten Worker-Quelltext ausgeführt. Sieben direkte
Assertions gegen die M4-Gate-Funktionen bestanden; die beiden veränderten
Repository-Testdateien wurden zusätzlich syntaktisch geprüft. Diese
isolierten Prüfungen ersetzen weder die gesamten Continuum-/Backend-Suiten
noch einen vollständigen Git-Checkout-End-to-End-Test oder einen
Kotlin-/Android-Build. GitHub-Actions-Jobs mit null ausgeführten Schritten
bleiben ausdrücklich **kein** Abnahmebeleg.

**Der Release-Status bleibt GESPERRT**, bis der vollständige
`m4-local-gate.mjs --android`-Lauf auf einem vollständig ausgestatteten
Entwicklungsrechner und die Installation samt Neustart am echten Android-Gerät
belegt sind. Keine Änderungen an `main` durch diesen Nachtrag.

## Stabilisierungsnachtrag – CLI-Exitcodes, Worker-Taktung und atomare Timer-Installation (17.10.2026)

- `teamleader-cli.mjs --once` setzt den Prozess-Exitcode jetzt auch bei
  `NO_VERIFIED_REPAIR`, `INTERRUPTED` und unbekannten/unvollständigen Ergebnissen
  auf ungleich null. Die Einordnung verwendet dieselbe zentrale Funktion
  `isFailedCycleOutcome` wie der zyklische Worker. Ein nicht verifizierter
  Reparaturversuch darf dem Dienstmanager nicht als erfolgreich gelten.
- Bei explizit längeren Intervallen (z. B. zwei Stunden) darf exponentielles
  Backoff einen Fehler nicht zu häufigeren Aufrufen führen. Die Wartezeit
  unterschreitet deshalb auch bei Fehlern nie das konfigurierte Grundintervall.
- Der optionale systemd-Installer legt temporäre Unit-Dateien mit restriktiven
  Dateirechten an, veröffentlicht diese ohne Überschreiben vorhandener Units,
  weist auch defekte symbolische Links zurück und entfernt bei fehlgeschlagener
  Aktivierung nur seine neu erzeugten Units. Eine neue Installation ist nach
  einem solchen Fehlschlag wieder möglich.

**Gezielte lokale Prüfungen:** In einer isolierten Linux-/Node-22-Testkopie
bestanden fünf Worker-/Zeitplanprüfungen und drei Timer-Installationsprüfungen
(inklusive simuliertem systemd-Fehler, Wiederholung und Symlink-Schutz).
Zusätzlich bestanden neun direkte Assertions gegen die nach dem Commit
erneut aus GitHub geladenen aktuellen Worker-/CLI-Funktionen.
Die korrespondierenden Regressionen liegen im Branch. Es wurden dabei
keine echten Benutzer-Systemdienste installiert. Dies sind begrenzte
Komponentennachweise, **kein ausgeführter vollständiger GitHub-Repository-Test**.

**Freigabe weiterhin gesperrt:** Für den aktuellen Branch fehlen weiterhin
ein kompletter Continuum-/Backend-/Repair-Testlauf in einem echten Checkout,
Kotlin-/Gradle-Build, aufbewahrte und verifizierte APK und Smartphone-Test.
Der lokale Prüfrechner hat Node.js und Git, aber kein installiertes `gradle`;
auf das private GitHub-Repository kann er ohne Netzwerk/Authentifizierung
nicht als Gesamtcheckout zugreifen. Für den aktuellen Head wurden bei der
letzten Statusabfrage keine zugeordneten GitHub-Actions-Läufe geliefert.
