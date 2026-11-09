# Continuum – verbindlicher Anschlussvertrag („No Orphans“)

Stand: 09.11.2026. Gültig für **nur** `Ullmann27/device-ai`, APK-Paket `at.heinz.deviceai.continuumlocal`. Kein anderes Projekt und keine Übernahme von Fremd-App-Code.

## Ein verbindlicher Weg statt Forschungssilos

Jeder neue Continuum-PR und jeder als nützlich bewertete Forschungsbranch erhält unmittelbar einen Eintrag im `docs/integration/CODE52_NO_ORPHANS_LEDGER.json` mit exaktem Commit-Checkpoint, Integrationsverantwortlichem, kanonischem Ziel-PR `#671`, konkretem nächsten Schritt und einem überprüfbaren Status. Der Eintrag darf durch einen neuen Chat, einen weiteren Agenten oder eine neue Buildnummer nicht verschwinden.

Ein Prototyp ist erst dann **angeschlossen**, wenn sein echter Android-/Backend-Aufrufpfad und das richtige UI/Werkzeug mit dessen Berechtigungsprüfung, Tests und Beleg gegenüber der originalsignierten Quelle verglichen sind. Neue Forschung liefert einen Kandidaten, keine Betriebserlaubnis.

Prüfreihenfolge:

`GitHub-HEAD → 24h-PR-Priorität → alter vollständiger PR-/Branch-Bestand → Quell-Diff → schon vorhanden/fehlt → Produktionsaufruf → Freigaben und UI → positive+negative Regressionen → Android/Native/Fold7 → SHA-spezifische Release-Entscheidung`

Statusvertrag:

- `AUDIT_REQUIRED`: Quellprüfung fehlt; offen und **nicht** erfolgreich.
- `RESEARCH_ONLY`: Versuchsstand; Sicherheits- und Wirksamkeitsbeleg für Produktivpfad fehlt.
- `SOURCE_STAGED`: produktiver Code gepusht; vollständige Tests/Androidbelege fehlen noch.
- `PARTIAL_SOURCE_CONNECTED`: Teilbereiche nachweisbar angeschlossen; weitere Quellen/Tests fehlen.
- `ALREADY_PRESENT_SOURCE`: gleiche Funktion bereits in Release-/Master-Quelle; exakte Datei-/Callsite-Prüfung nötig.
- `BLOCKED`: konkretes Hindernis, Patch-Commit und ausführbarer nächster Prüfschritt.
- `SUPERSEDED`: begründet durch nachgewiesenen Nachfolger.
- `RELEASE_BASELINE`: historische Referenz, nicht Freigabe für nachfolgende Änderungen.
- `DEVICE_VERIFIED`: nur mit tatsächlichem Fold7-/Android-Beleg, exaktem Test-SHA, Geräteereignis und klarer Nutzerfreigabe.

## Prüftechnik und aktualisierte Vollständigkeit

Das bereits paginierte Archiv enthält **627 PRs und 727 Branches**. Die 29 als zeitnah markierten PRs und die nach dem Snapshot erzeugten #672 und #673 sind explizit im neuen Ledger vertreten. Die älteren Einträge bleiben im Gesamtinventar `AUDIT_REQUIRED` (soweit keine Einzelaudit-Evidenz existiert). Es wird **keiner** dieser Einträge als fertig oder redundant umdeklariert, nur weil seine Metadaten vorliegen.

`.github/continuum/code52-no-orphans-accountability.test.mjs` wird vom vorhandenen Node-Testsammellauf `node --test .github/continuum/*.test.mjs` mit erfasst, sobald dessen Workflow auf dem konkreten Branch ausgeführt wird. Der Test prüft Vollinventur, doppelte oder fehlende PR-Nummern, SHA-Form, Owner/Target, bekannte neuere Nachfolger sowie unzulässige Gerätesuccess-Behauptungen. Nach *jedem* Inventar-Refresh müssen neue zeitnahe PRs im Ledger stehen; sonst wird der Test rot. Kein neuer kostenpflichtiger Runner/Workflow eingerichtet. Die GitHub-Snapshots werden durch diesen Test **nicht automatisch aktualisiert**; ihr Live-Refresh bleibt deshalb eine explizite Integrationspflicht. Es wird keine unbeaufsichtigte Fortsetzung nach Chatende behauptet.

**Release-Grenze:** Ein erfolgreicher Ledger-Strukturtest bedeutet **nicht** „alle 627 PRs integriert“. Vor einem neuen signierten APK-Release muss der Integrator für relevante neue Änderungen alle Sicherheits-/Funktionsblocker, produktiven Anschlussstellen, Node/Android/JUnit/Lint-Prüfungen und den realen Fold7-Nachweis gesondert abnehmen. `main`, `continuum-original-signing`, originale Paket-ID/Signatur, vorhandene App und Nutzerdateien bleiben unverändert. Kein automatisches Senden, Root-Bypass oder Aufweichen von Eigentümerfreigaben.

## Sofortige Code-52-Pflichtkette

`#671` ist weiterhin der gemeinsame Integrationsmaster. `#672` beseitigt isoliert irreführende Fremdprojekt-Spezialpfade; auf `#671` besteht weiterhin ein roter Projektisolations-Test, bis alle Referenzen und davon abhängigen Testannahmen nachweisbar bereinigt sind. `#673` adressiert Fold7 T23/T24 (negierte Appsuche und klare Rückmeldung) sowie die Akku-Neumessungs- und Vergleichsklausel aus dem aktuellen Code-Review. Selektive Übernahme in `#671` erst nach geprüfter Kompatibilität und Tests; T20 (Webfreigabe-Revision in einem lokalen Gespräch), volle Sprach-/Thermik- und sichere Android-Typisierfunktionen bleiben eigenständig **OFFEN**, nicht als erledigt etikettiert.

### Reproduktionsnachweise

- `#673`, vorliegender isolierter Kotlin/JDK21-Test (Klassifizierungslogik-Spiegel): initial **14 PASS/1 FAIL** für `Prüfe nicht den Akku`, danach **15 PASS/0 FAIL** nach Negationskorrektur. Die Klassifizierung wurde quellentreu nachgebildet; **kein** vollständiger Repository-JUnit- oder APK-Test.
- `#673`, neu gepushte Regressions-JUnit-Dateien: `OwnerAppLookupIntentTest.kt`, `OwnerAppActionFlowTest.kt`, `PreviousBatteryObservationTest.kt`, `BootstrapBrainTest.kt`. Lauf auf Gradle: **NOT_RUN**.
- `#672`, separat gepushte Projektgrenzen-Tests: Node / Android / Fold7 **NOT_RUN**.
- `#670` bleibt Feldtest- und Fehlerregister für den Besitzer.

**Wiederanlauf nach Kontext-/Agentwechsel:** Nur GitHub-Live-PR-HEADs und beide JSONL-Inventare als Einstieg. Danach `CODE52_NO_ORPHANS_LEDGER.json`, `CODE52_AUDIT_INDEX.md`, `#670`, `#671`, `#672`, `#673` prüfen. Kein „alles integriert“ ohne datei-, aufruf-, test- und gerätegebundenen Nachweis.
