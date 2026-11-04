# BOT04 — Worker-Lifecycle

## Ergebnis

**BLOCKED_MISSING_FIXTURE_PREREQUISITES.** Die beiden angeforderten unabhängigen Fixturetests wurden genau einmal gemeinsam und seriell aufgerufen. Der Testaufbau scheitert vor dem Erstellen seiner Fixture an fehlenden Voraussetzungen. Dies ist kein reproduzierter Produktdefekt und kein bestandener Lifecycle-Test. Es wurde kein Produktionsfix vorgenommen.

## Auftrag und Grenzen

Geprüfter Bereich: Restart, Abbruch, gebundene Auftragszustände und verspätete Ergebnisse in den unabhängigen Control-Plane-Fixtures. Kein zusätzlicher Scheduler, kein Modelllauf, kein S01-Modell-Akzeptanzlauf, kein Gradle-Prozess, kein Commit, keine Remote-Schreiboperation. Die bereits durch Root ausgeführten 45 Worker-/Process-/Service-/Update-Tests wurden nicht wiederholt. Keine app-Dateien wurden geändert.

## Reproduzierbarer Aufruf

- Arbeitsverzeichnis: `/workspace/scratch/fd4940f23688/device-ai-support`
- Branch: `support/continuum-ten-bot-assist-20261104`
- HEAD vor dem Aufruf: `d234f8d8c3efd2efcbc9e6a264c91f50d56337cf`
- HEAD nach dem Aufruf: `d234f8d8c3efd2efcbc9e6a264c91f50d56337cf`
- Node: `v24.22.1`
- Start UTC: `2026-11-04T10:38:25.429884+00:00`
- Dauer: 0.331 Sekunden
- Exit-Code: `1`
- Timeout: `false`

```sh
node --test --test-concurrency=1 tools/independence-service-review/lifecycle.test.mjs tools/independence-service-review/active-recovery.test.mjs
```

`SERVICE_REVIEW_EVIDENCE` zeigte auf den Bot-04-Evidenzordner. Fehlende Voraussetzungen: `SERVICE_REVIEW_CLASSPATH_FILE`, `SERVICE_REVIEW_RUNTIME_ENV`, `SERVICE_REVIEW_ISOLATION`.

## Befund und Aussagegrenze

`process-fixture.mjs` prüft die drei Voraussetzungsschlüssel am Beginn von `fixture()`. Bei einer fehlenden Angabe wirft es ausdrücklich „required: absent prerequisites are not a test PASS“. Diese Prüfung liegt vor dem temporären Projektaufbau und dem Start von Java, Daemon oder Worker. Die Ausgabe dieses Laufs zeigt die dadurch abgebrochenen Tests; ihre fachlichen Assertions zu Restart, Cancellation und Recovery wurden nicht erreicht.

Die gelesenen Fixtures enthalten unter anderem Cancel-vor-Dispatch, Task-/Source-Bindung, konkurrierende Daemon-Starts, Prozessidentität sowie UNKNOWN-/Reconciliation-Szenarien nach Dispatch oder Worker-Tod. Die Active-Recovery-Fixture würde für diese Szenarien ihre private Runtimekopie vor dem Dispatch entfernen und den Worker kontrolliert stoppen. Diese Mechanismen wurden hier nicht ausgeführt und gelten daher nicht als validiert.

Es liegt aus diesem eng begrenzten Lauf **kein belastbar reproduzierter Fehler der Produktionsimplementierung** vor. Es wäre unzulässig, die fehlenden Runtimeangaben durch erfundene Dateien oder ungeprüfte Pfade zu ersetzen oder den Gate-Abbruch als Erfolg zu zählen. Auf Anweisung von Root wurde die Prüfung nach dem dokumentierten Blocker beendet und nicht erweitert.

## Dateien und Nachweis

- `fixture-tests.stdout.log`: vollständige TAP-Ausgabe.
- `fixture-tests.stderr.log`: vollständige Standardfehlerausgabe.
- `result.json`: Befehl, Zeit, Exit-Code, HEADs, ausschließlich Vorhandensein der Voraussetzungen sowie SHA-256 der gelesenen Quelldateien.
- `commands.txt`: reproduzierbare Befehle und Prüfverfahren.
- `SHA256SUMS`: SHA-256 der Evidenzdateien und dieses Berichts.

## Nächster zulässiger Schritt

Ein späterer erneuter Lauf erfordert vom bestehenden lokalen Review-Setup verifizierte Angaben für Classpath, Runtime-Environment und Isolation. Hier wurde dieser Lauf nicht vorbereitet oder gestartet. Der aktuelle Status bleibt **BLOCKED / LOCAL_ONLY / NOT_PUSHED durch Bot 04**. Kein Rollback von Produktionscode erforderlich.
