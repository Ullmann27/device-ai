# Code59: gemeinsamer Kontext- und Laufzeitstand

## Quelle und Verantwortung

- Ziel: `fix/code59-context-complete-20261114`, PR #848; Integrator: Astra/Root mit drei Entwicklungsagents.
- Vorheriger geprüfter Kontextstand: `b8c31554ce7416540692f1d30b559be3712495d7`.
- Eingefrorener Laufzeitkandidat aus PR #845: `68eaa5bda8e34dee49eced8ae40d0d4bd0012640`.
- Gemeinsame Basis: `c5e2faeabed5b421796c83afde19d5ea391c5f63`.
- Der Merge bewahrt beide Abstammungen. Kein Ersetzen des Kontextstands durch den Laufzeitstand.
- Betroffene Verträge: EXP-01/05/11/12/14; bestehende Android-, Modell-, Gedächtnis-, Routine- und Chief-Aufrufpfade.
- Dieser Checkpoint dokumentiert die Integration vor der neuen CI. Aktuelle, SHA-gebundene Ergebnisse stehen im selben PR; Elternresultate gelten nicht als Abnahme dieses Merge-Commits.

## Angeschlossene vorhandene Funktionen

| Kandidat | Tatsächlicher Anschluss | Status am Checkpoint |
| --- | --- | --- |
| Langer Dialog aus b8 | 11.500 Zeichen/UTF-8-Bytes Eingangsprüfung, optionaler Kontext nach Pflichtteilen, adaptiver Modellkontext, 64.000 Zeichen Owner-Ausgabe, gespeicherte Fortsetzungsanker | PORTIERT; Eltern-QA vorhanden, gemeinsame QA ausstehend |
| Routinen aus PR #845 | Owner-Bestätigung → persistierter Routinebestand → Android JobScheduler → begrenzter vorhandener Worker → exakt gebundene Chat-Zustellung | PORTIERT; gemeinsame QA ausstehend |
| Routine-Recovery | Stabile Auftragsidentität, ausstehende Berichte, Wiederanlauf ohne Werkzeugwiederholung, Widerruf vor spätem Ergebnis, echte periodische Fälligkeit | PORTIERT; gemeinsame QA ausstehend |
| Denkgedächtnis | Vorhandener lokaler Agent liest aktuelle Erinnerung pro Schritt und schreibt nur geeignete belegte Ergebnisse; Faktenwiderruf erreicht laufende Arbeit und Journal | PORTIERT; gemeinsame QA ausstehend |
| Private und geteilte Bot-Erinnerungen | Datierte aktuelle private Datensätze; ausdrücklich ausgewählte Besitzerfakten an konkrete Teamfreigabe gebunden | PORTIERT; gemeinsame QA ausstehend |
| Folgevorschläge | Typisierter Worker-Vorschlag → Chief-Prüfung → neuer Entwurf mit neuer ausdrücklicher Freigabe; keine geerbten Rechte | PORTIERT; gemeinsame QA ausstehend |
| Native Oberfläche | Synchronisierte initiale Filament-Surface, neue SwapChain nach wirklicher Größenänderung, Consumer-Frames und Zeitstempel statt bloßer Einreichung | PORTIERT; gemeinsame native QA ausstehend |
| Snapshot-Speicherung | Geschlossene PNG-/JSON-Daten und vollständige MediaStore-Rücknahme bei fehlgeschlagenem Schreiben/Veröffentlichen | PORTIERT; gemeinsame QA ausstehend |

## Beim Zusammenführen behobene Fehler

1. `LocalModelHost.memoryGuarded` prüfte nur die drei älteren Inferenz-Einstiege. Der zusätzliche `streamTextOnly`-Override führt den langen werkzeugfreien Dialog jetzt ebenfalls durch die aktuelle Android-Speicherprüfung. Der getrennte native Texteinstieg bleibt erhalten. Die vier Pfade werden bei Speichermangel, fehlender Messung und thermischer Sperre geprüft; ein bereits geladenes Modell wird dabei nicht unnötig entladen.
2. Die neue Routinenroute lag vor dem Verbrauch einer offenen Computer-Suchrückfrage. Eine veraltete Frage konnte deshalb nach dem neuen Auftrag fortbestehen. `handleOwnerRoutineRequest` verwirft nun auch diese Rückfrage. Der echte Activity-Test führt fehlenden WhatsApp-Suchbegriff, unabhängigen Routinenbefehl und nachgereichten Namen durch den Composer und verlangt, dass keine alte App-Aktion wieder startet.
3. Nach fehlgeschlagenem Kontext-Restore konnten leere RAM-Turns verhindern, dass die Faktenlöschung einen persistenten Kontextreset auslöst. Die Löschroute reiht nun einen ausdrücklichen Reset für die ursprüngliche Sitzung auf dem bestehenden Chat-Schreiber ein. Der Lern-I/O-Pfad wartet begrenzt auf dessen Ergebnis, ohne den UI-Thread zu blockieren. Resetfehler werden als unvollständige Löschung gemeldet; der Faktenwiderruf wird trotzdem versucht. Der Store bestätigt den Reset erst nach geprüftem Dateiziel und erzwungenem Schreiben. Activity-Regressionen prüfen fehlgeschlagenen Restore, Wiederanlauf, Sitzungs-/Schreibreihenfolge sowie Schreibfehler und Wiederholung; Store-Regressionen prüfen ungültige Ziele und Symlinks.
4. Der native Lauf `43637050924` auf dem eingefrorenen Kandidaten scheiterte vor Emulatorstart: Xvfb besaß kurzzeitig eine leere `/proc/PID/cmdline`. Der Startprüfer toleriert ausschließlich diesen Zwischenzustand innerhalb der unveränderten fünf Sekunden, bindet dabei bereits PID, Elternprozess, Benutzer und Startzeit und verlangt danach weiterhin die genaue ausführbare Datei, Argumente und Displayantwort. Fehlerhafte Prozessidentität bleibt ein Fehler.

## Merge-Entscheidungen

- `AgentActivity`: geklärter Computerauftrag und Bot-Abgrenzung aus b8 bleiben erhalten; Routinezustellung wird zusätzlich angebunden.
- `BoundedAgent`: vollständiger zugelassener Owner-Auftrag und 64K-Owner-Ergebnis bleiben erhalten; begrenzte ThinkingMemory-Abfragen verändern den Originalauftrag nicht.
- `LocalChatTranscriptStore`: 64K-Ausgabe plus Statusreserve/UTF-16-Grenze und exakte Routinen-JSON-/Offsetbindung koexistieren; Routine-Mailbox bleibt unverändert begrenzt.
- Manifest/Painter: identische Berechtigungs-/Konstantenkorrekturen vereinigt, vorhandene Begründung erhalten.
- STOP-Regressionsfixture: kontrolliert zurückgehaltenes spätes Ergebnis aus b8 plus tatsächlicher Erfolgscounter und Abschlussgarantie aus PR #845.
- Native Smoke: vollständige neuen Routinen-/Offline-/Navigationsprüfungen plus begrenzter b8-ANR-Collector. Beide Fehlerkollektoren werden unabhängig geschützt ausgeführt.
- Full-QA: Vereinigung beider Pflichtfallmengen, keine verlorenen Elternfälle. Kontext- und Laufzeitzweig führen dieselben vollständigen Jobs aus; Diagnosezweig behält seinen begrenzten Modus.

## Tatsächlich vor Veröffentlichung geprüft

- Native Helfer nach Xvfb-Korrektur: `python3 -B -m unittest discover -s tools/code55-apk -p 'test_native_*.py' -v`: 150 bestanden, 0 Fehler/Skips. Darin 20 Xvfb-Fälle mit 13 neuen Start-/Identitätsregressionen; zwei Fehler vor Korrektur tatsächlich reproduziert.
- Native Smoke: Shellsyntax und eingebettetes Python kompiliert; keine doppelten Funktionsdefinitionen.
- Kontrollierter verspäteter App-Callback: exakt extrahierter Test-Port mit realen Protokollklassen, 50 Unterbrechungsrennen und 3 Fehlergrenzen bestanden. Dies ersetzt nicht Robolectric.
- Beide QA-Dateien: YAML und sämtliche 27 Shell-Schritte syntaktisch geprüft.
- Pflichtfallvergleich gegen beide Eltern: keine entfernten Pflichtfälle; beide Schnittstellenfälle und fünf Resetfälle zusätzlich verbindlich.
- Gesamte Pflichtauswahl: 314 Methoden im Full-Gate, 20 im Fokus-Gate; jede referenzierte Methode im tatsächlichen Quellbaum vorhanden.
- Store-Hosttest mit echten Kotlin-/JSONObject-/StrictJson-Quellen: vorher 14 Fälle mit 2 reproduzierten Fehlern, nach Korrektur 14 bestanden ohne Fehler/Skips. Die drei Activity-Resetfälle bleiben bis CI-Abschluss unbestätigt.
- Node-Verdrahtung: `node --test .github/continuum/android-integration-wiring.test.mjs`: 21 bestanden, 0 Fehler/Skips; reine Quellverträge.
- `git diff --check`: bestanden. Vollständige Android-/Node-/native QA auf dem Merge-SHA ist erst nach ihrem tatsächlichen Abschluss bestätigt.

## Native Evidenz und nächste Abnahme

Der ältere Lauf `43627444448` auf `f24ea7cc1b245c58d00740a583089236d4807338` bestätigt PDF, Filament, tatsächlich konsumierte Surface-Bilder, vier SwapChain-Größenwechsel und den damaligen Routinenpfad im Emulator. Der allgemeine App-Ablauf scheiterte später an der Diagnose-Navigation. Der eingefrorene Kandidat enthält die danach entwickelte Navigation nach messbarem Scrollfortschritt sowie echte periodische Routine- und Offline-Web-Prüfungen. Diese müssen im gemeinsamen Stand neu bestehen.

Nächster Einstieg: exakten PR-Head lesen, die beiden bestehenden Kontext-/Full-QA-Läufe und deren Originalartefakte prüfen. Fehler am gemeinsamen Stand reparieren; weder frühere PASS-Zahlen übertragen noch ausgelassene Schritte als bestanden zählen.

`GERÄTEBESTÄTIGT`: für diesen Integrationsstand NOT_RUN. Emulator, Host-JUnit und Robolectric sind keine Fold7-Bestätigung. Originalpaket, Code59/0.3.58, Signaturidentität und Berechtigungsgrenzen bleiben erhalten. Keine Besitzerinstallation oder Veröffentlichung durch diesen Checkpoint.

Rollback: den Integrations-Merge auf diesem Entwicklungszweig mit Hauptelternteil 1 revertieren; niemals fremde aktive Zweige zurücksetzen. Persistierte Routinen-/Botdaten bei einem Quellrollback erhalten und auf Formatkompatibilität prüfen.
