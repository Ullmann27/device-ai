# Continuum — Reparaturen, Optimierung und Fortsetzung, 27.10.2026

**Forschungszweig, kein Besitzer-Release.** [Draft PR179](https://github.com/Ullmann27/device-ai/pull/179), Branch `integration/continuum-autonomy-learning-20261027`. Geprüfter Code: **a4fee812d5d3b99e3504f599432fe8f39d0b3051**. Dieser Bericht und die letzten Rohbelege werden anschließend als Dokumentationscommit gesichert. Main, bestehende Besitzergewichte, Paket-/Signaturkonfiguration und Freigabemanifeste wurden nicht verändert.

## Tatsächlich repariert

1. **Falsche grüne Shell-Ergebnisse:** Der vorhandene String-Rückkanal verlor Exitstatus, fehlende Verbindung und Argumentfehler. DeviceToolExecutor/Runner verwenden jetzt Result; der bestehende Shizuku-Kanal trägt einen streng geprüften versionierten Ergebnisvertrag. Exit37 bleibt FAILED, auch wenn stdout SUCCEEDED behauptet. Es gibt keine Rückstufung auf ungeprüfte Antworten und keinen automatischen zweiten Ausführungsversuch.
2. **Blockierendes Ausgabelesen:** Das alte90s-Zeitlimit begann erst nach dem synchronen Lesen bis EOF. Ein stiller Prozess/offener Ausgabekanal konnte es umgehen. Ein gemeinsames monotones Zeitbudget umfasst jetzt Ausgabedrain und Prozessende; stdin wird geschlossen. Ausgabe ist auf120000UTF-8-Bytes begrenzt; Überlauf, ungültige Kodierung und Unterbrechung sind explizite Nichterfolge. Zwei reale100ms-Gegenproben endeten in101/103ms. Der Toolpfad verwendet15s innerhalb des bestehenden20s-Gates; der manuelle Konsolenpfad behält90s, jetzt einschließlich Ausgabelesen.
3. **Unvollständige Tool-Evidenz:** ToolGate markierte abgeschnittene Ausgaben als SUCCEEDED. Jetzt FAILED, begrenzte Vorschau, kein erfolgreicher Ausgabehash und kein positives Lernsignal. Backend-Timeouts behalten TIMED_OUT bis zum dauerhaften Receipt.
4. **Sicherer Umgang mit altem Dienst:** Das Binder-Interface/Transaktionsschema bleibt unverändert. Die neue Anfrage ist eine einzelne POSIX-No-op-Zeile mit Kommentar und kodiertem Inhalt. Ein alter Dienst führt damit nicht versehentlich den eigentlichen Auftrag aus; seine alte Textantwort wird für Tools abgelehnt. Die menschliche Konsole kann alte Antworten weiter anzeigen. Nach späterem App-Update alten Shizuku-Dienst neu verbinden; nicht den Auftrag ungeprüft wiederholen.
5. **Numerischer Prüfer unter CPU-Last:** Reale Fehlläufe wechselten zwischen PASS und ABSTAIN. Zusätzliche Diagnose des echten Workers belegte OMEGA_VM_TIMEOUT bei25ms pro VM-Aufruf, auf diesem Host mit2CPU-Quota. Jetzt werden dieselben primitiven Eingaben einmalig in einem VM-Kontext berechnet; bis zu512Kontexte/Watchdogs entfallen. Deklarierte VM-Gesamtfrist1s innerhalb unveränderter5s/48MiB-Unterprozessgrenzen. Quellen-Grammatik, Fähigkeitsverbote, eingefrorene Orakel, Hashes und die Erfolgskriterien wurden nicht gelockert. Neue maximale Suite:256Beispiele+128Relationen=384Checks; konstante0/1-Gegenkandidaten müssen scheitern.

Keine zweite App, kein zweites Gehirn/ToolGate und kein konkurrierender Worker. Andere Bootstrap-Aktionen bleiben für Lernen PARTIAL: Exit0 beweist nicht die fachliche Richtigkeit einer Aktion. Keine produktiven Modellupdates aus diesen Ergebnissen.

## Code und Commits

- `92ebfff6`: Ausgangslage/Fehlerhypothesen vor Änderungen veröffentlicht.
- `2ce876b8`: begrenzte Prozessausführung, tatsächliche Exit-/Timeout-Ergebnisse, erster nativer66-Test-Nachweis.
- `3c0ca70afaed18b029f162960619e26f7a21f355`: tatsächliche Android-/Shizuku-Adapter verbunden, vollständige Ausgabeprüfung und sichere Protokollframing-Regel,69native Tests.
- `a4fee812d5d3b99e3504f599432fe8f39d0b3051`: numerische Verifikation in einem begrenzten Kontext, maximale Suite und Gegenkontrollen.

Produktquellen: `ShellExecution.kt`, `BoundedShellProcess.kt`, `ShellUserService.kt`, `ShizukuBridge.kt`, `DeviceToolExecutor.kt`, `DeviceToolExecutorRunner.kt`, `AgentToolLayer.kt`; selektive Optimierung in `experimental/omega/repair/numeric-worker.mjs`. Tests: `ShellExecutionTest.kt`, bestehende `numeric-verifier.test.mjs`; nativer Prüfanschluss im bestehenden `cognitive-action-integration.test.mjs`. Neues Prüfskript `scripts/continuum-shell-android-compile.sh`; bestehender kognitiver JVM-Launcher erweitert. Keine ganzen älteren Integrationsdateien übernommen.

## Abschließende echte Tests

Gleicher sauberer gepinnter Code a4fee812, zwei disjunkte native Node-Dateiinventare:

| Prüfung | Ergebnis |
|---|---|
| Vorhandener Worker:190Dateien unter .github/continuum | **1147PASS,0FAIL,0SKIP**, ALL_EXECUTED_PASS, Exit0 |
| Ergänzende scripts/*.test.mjs | **283PASS,1FAIL,0SKIP**,284Tests, Exit1 |
| Gesamt, keine Doppelzählung | **1430PASS,1FAIL,0SKIP**,1431Node-Tests |
| Darin: tatsächliche kognitive/ToolGate-JVM-Prüfung | **69JUnit PASS**, nicht zusätzlich zum Node-Zähler addiert |
| Darin: geänderte Android-Shell-Adapter | Reale Java-/Kotlin-Kompilierung PASS gegen offizielle Shizuku13.1.5/Annotation1.9.1 und Android-Frameworkklassen |
| Gezielte bestehende Android-/Binder-Quellprüfungen |32NodePASS |
| OMEGA/Controlled-Repair nach Optimierung |9NodePASS |
| Maximale Suite während Workerlast |10/10Wiederholungen PASS, je384Checks; dieselben Daten, keine3840neuen Aufgaben |

**Der eine FAIL bleibt die absichtliche Besitzer-v25-Quellfreigabesperre**, erste Meldung altered autonomous-teamleader.mjs. Dieser Forschungsstand besitzt keine neue Releasefreigabe. Keine Testentfernung, kein Skip zum Verbergen, keine Änderung des Freigabemanifests. Beide vorher optionalen Infrastrukturtests werden im konfigurierten Worker tatsächlich ausgeführt.

Worker1147-Prüfungen:182.44s, sourceUnchanged=true, leaseHeld=true. Receipt `2026-10-27T06-04-20-104Z-b7f5b170-c984-43c4-8294-86da1c923d95`, Tree d85b712d6536bb4e6e5654eddf44e7b934f51066. Node-Heap512MiB pro Prozess, Parallelität2, Deadline360s, Evidenzbudget64MiB. Reale Docker-Reportprüfung verwendet die bestehende feste Isolationskonfiguration, kein Netz/keine Capabilities; keine Modellinferenz oder Android-Buildbehauptung. Kein Testcontainer und kein dauerhafter Worker zurückgelassen.

Rohdaten: `docs/evidence/shell-repair-20261027/`. Vollständige initiale Fehlerläufe, deren Skript-Recheck, Diagnosecode, RED→GREEN-Proben und behobene Host-Prüfaufbaufehler sind erhalten. Der erste maximal formulierte Test enthielt irrtümlich256statt erlaubten128Relationen und wurde korrekt abgelehnt; dieser Testaufbaufehler ist separat gekennzeichnet. Finale Prüfkriterien sind strenger, nicht abgesenkt.

## Messbare Optimierung und Ressourcen

- Ein VM-Kontext statt bis zu512pro numerischer Maximalsuite; unveränderte eingefrorene Labels und Sicherheitsgrammatik. Zehn volle Wiederholungen unter tatsächlicher Worker-/Skriptlast:99.675–154.451ms, Median129.732ms. Vorherige Ausführung war instabil; deshalb kein erfundener Geschwindigkeitsfaktor und keine neuronale Generalisierungsaussage.
- Prozessausgaben und Wire-Pakete sind vor weiterer Verarbeitung begrenzt. Überlauf wird abgebrochen statt unbegrenzt weiter eingelesen/als Erfolg gespeichert.
- Geprüfte Tool-Rückgaben benötigen den UI-Thread nicht mehr zur Fertigstellung. Keine behauptete Fold7-Latenz-/Akkuverbesserung ohne Gerätetest.
- Node24.19.0, Java21.0.12.1, realer Kotlin2.2.0-Hostcompiler; JRE ohne javac. Offizieller Eclipse-Compiler3.39 dient nur dem Host-Kompilierungscheck. Alle öffentlichen Abhängigkeits-URLs/Hashes sind veröffentlicht; keine Schlüssel oder privaten Daten. Etwa177MiB temporäre Framework-Datei für diesen optionalen Hostcheck; Heaplimits sind keine aggregierten RSS-Limits.

## Noch offen und ausdrücklich nicht behauptet

- Vollständiger Gradle/AGP8.10/Kotlin2.4/SDK36-Build, Fold7/Shizuku-Binder-Lauf, Besitzer-Keystore, APK-/Signatur-/Migration-/Installationstest **NOT_RUN**. Adapterkompilierung nutzt echte API-Klassen und den vorhandenen Termux-Binder, aber generierte BuildConfig-Konstanten sind lediglich ein Kompilierungsfixture.
- Direkten Prozess beenden garantiert weder das Ende aller privilegierten Nachkommen noch Rollback bereits begonnener Geräteaktionen. Unklare Belege dürfen nicht automatisch erneut ausgeführt werden. OS-Prozessstart und Binder haben eigene Latenzgrenzen.
- Ein Pipeline-Exit0 garantiert nicht, dass jede Pipeline-Stufe fachlich erfolgreich war. Vor weiteren positiven Aktions-/Lernbelegen sind werkzeugspezifische unabhängige Orakel nötig.
- Keine neue Gemma/DeepSeek-Inferenz, kein neuer echter Modell-Coding-/CCC1-Doppelworktree-Nachweis und kein neuronales Qualitätsupgrade. Der verworfene SWB2-Kandidat bleibt aus; vorhandener SWB1-Hash/Statistiken unverändert.
- Keine unabhängige Fremdreview in dieser Fortsetzung. Eigene Kontrollgänge: Syntax/API-Kompilierung, Nebenläufigkeit/Fristen, Evidenz-/Transportnegativtests, tatsächlicher Worker-Gesamtpfad.

## Unmittelbar weiterarbeiten

1. Aktuellen Draft179-Head fetch/prüfen, neuen eigenen Branch daraus ableiten; diese geprüfte Code-SHA und die Dokumentationscommits unterscheiden. Keine fremden Branches resetten, keine Freigaben neu einfrieren.
2. Vorhandene Host-Umgebung: CONTINUUM_FABRIC_JVM_DEPS=offizielle Kotlin/JUnit/android-all-Jars; zusätzlich CONTINUUM_SHELL_ANDROID_DEPS=offizielle Shizuku/Annotation/optionalECJ-Jars. URLs/Hashes in der Evidenz. `bash scripts/continuum-cognitive-action-jvm.sh /externer/neuer/beleg` und `bash scripts/continuum-shell-android-compile.sh /externer/neuer/adapterbeleg`. Keine Downloads im Worker hinzugefügt.
3. `node --test scripts/continuum-omega.test.mjs` wiederholt die9Orakeltests. Das gesicherte repeat-check.mjs nimmt Repositorywurzel und externe JSON-Ausgabedatei als Argumente. Keine Experimente auf privaten Chats/Gewichten.
4. Vollständige native Reproduktion: vorhandener Worker --once mit den publizierten Budgets und Kotlin-/Shell-/Fabric-Snapshot-/Docker-Variablen; anschließend exakt die scripts-Argumentliste aus scripts-final/run.json. Die beiden Inventare sind disjunkt. Budgetfixture maxCycles=1 ist ein Kontrolllauf; Besitzer-Dauerbetrieb benötigt explizit passenden maxCycles/Intervall und STOP-/Signalsteuerung.
5. Priorität ist nun Geräteabnahme in der bestehenden Hauptapp: Speicher-/Geräteanfragen, alter/neuer/getrennter Shizuku-Dienst, Nichtnull-Exit, Ausgabeüberlauf, Stop/Chatwechsel/Appneustart sowie unveränderte Besitzergewichte/Erinnerungen. Bestehendes Paket at.heinz.deviceai.mentorpaired und Besitzerzertifikat erhalten. Releasefreeze zuerst separat reviewen; keine APK als updatefähig bezeichnen, bevor Build/Signatur/Datenmigration tatsächlich bestätigt sind.

Rückfall: eigene Änderungen auf einem neuen Reviewbranch revertieren; niemals gemeinsam verwendete Historie/Quellen oder Besitzergewichte überschreiben. Zwischenschritte2ce876b8 sind Prototypquellen, keine Gerätefreigabe. Vollständiger aktueller Ausgangshandoff: SOL-JARVIS-HANDOFF-20261027.md; neuronale vollständige Negativbefunde/Checkpoints weiterhin in PR178.
