# Continuum: drei Helfer, Bot-Aufträge und tatsächliche Aktivierungsgrenze

Stand: 04.11.2026, Europe/Vienna. Heinz beauftragte drei Helfer, vorhandene Bots anzusprechen, Reparatur-, Implementierungs- und Avatar-Aufträge zu verteilen und ihr Verhalten zu prüfen.

**Drei echte Entwicklungshelfer arbeiteten. 105 gezielte Hostprüfungen bestanden. Die vorhandenen lokalen Continuum-Bots wurden in dieser Sitzung nicht live aktiviert. Drei konkrete Aufgaben sind formatgeprüft vorbereitet, aber nicht zugestellt.**

Status: **HOST_CONTRACTS_VERIFIED / LIVE_ACTIVATION_BLOCKED**.

## Quelle und Umfang

- Repository Ullmann27/device-ai; Draft-PR #510, Branch codex/continuum-bot-activation-20261104, auf #491 gestapelt.
- Produktbasis: e101e393dd6c80803ac9d9ac91fccd9812c4a18d.
- Tatsächlicher Ausführungscheckout: 000d413bd5745694d5ff6a8e9e6046af5b591db3. Unterschied zur Basis: ausschließlich der anfängliche Übergabetext.
- Unveränderter app/src/main-Baum: eaeda092326c11676543385eedd2ac86e0e4f194.
- Unveränderter .github/continuum-Baum: 51bb34cfa24c327b6889104564dce7a12dadf6a8.
- Betroffene Verträge: EXP-01, EXP-02, EXP-05, EXP-07, EXP-08 und EXP-11.

Die tatsächlich gestarteten Entwicklungshelfer dieser Sitzung heißen repair_bot_probe, implementation_bot_probe und avatar_bot_probe. Ihre Prüfungen sind keine Antworten laufender App-Bots auf dem Fold7.

## Vorhandene Bots und früherer Reparaturerfolg

ChiefBotProfileStore.kt enthält Kora (bot-kora, Reparatur), Link (bot-link, Integration) und Iris (bot-iris, Avatar/Animation). Die Team-Oberfläche initialisiert 20 Profile. REGISTERED und Rollenbeschreibungen belegen keine laufende Inferenz oder unabhängig geprüfte Fähigkeit.

Der Besitzer-Coding-Service verwendet feste operative Rollen: **FORGE → LINT → ECHO**. Sowohl coding-service.mjs/requireProfiles als auch coding-service-worker.mjs/executeServiceDelivery setzen bot-forge, bot-lint und bot-echo. Kora, Link und Iris werden dort nicht durch ein Aufgabenfeld automatisch zu ausführenden Workern.

Der historische Fall known-u03 enthält einen echten aufgezeichneten Reparaturnachweis: confirmedRepair=true, ein zugelassener echter Modellaufruf, unabhängige RED/GREEN-Prüfung, COMPLETED, beobachtete FORGE-/LINT-Rollen und einen ECHO-Bericht. ECHO ist eine deterministische Berichtsfunktion mit null zusätzlichen Modellaufrufen. Historischer Framework-Stand: 1595389274acb840c4ab544fa699c9eb2a04bd53.

Original: docs/research/omega/bot-genesis/coding-review/final-native/known-u03/independent-audit.json. Das Original wurde gelesen; Hash und eng begrenzter Befund stehen unter historical-known-u03-review.json im neuen Evidenzverzeichnis. **Kein neuer Modelllauf und kein neuer Blindtest.**

## Drei konkrete Aufgaben

Vollständige Eingaben: docs/evidence/bot-activation-20261104/coordinator/task-plan.json. Alle drei bestehen den vorhandenen validateCodingTask-Prüfer; Zielpfade existieren. Dies ist eine Format-/Pfadprüfung, keine kanonische Auftragsannahme.

| ID | Domänenprofil | Konkreter Auftrag | Zustand |
| --- | --- | --- | --- |
| owner-bot-repair-handoff-20261104 | Kora | Doppelte Übergaben, veraltete Quelle, Widerruf und unabhängigen Prüfer kontrollieren; nur reproduzierten Fehler reparieren | VALIDATED_NOT_SUBMITTED |
| owner-bot-implementation-scope-20261104 | Link | Worker→Chief-Mailbox-Anschluss auf Identität, volle Mailbox und Abbruch prüfen | VALIDATED_NOT_SUBMITTED |
| owner-bot-avatar-stop-20261104 | Iris | Arbeits-/Sprachzustand, alte Audio-/Visemesignale und Blickgültigkeit prüfen | VALIDATED_NOT_SUBMITTED |

Je Aufgabe: höchstens ein Modellaufruf, 8.192 Kontextzeichen, ein erlaubter Kotlin-Produktionspfad und die Android-Testkette. Kein Aufruf wurde verbraucht. Der Service würde weiterhin FORGE/LINT/ECHO für die Ausführung verwenden.

**Quellenbindung:** Die Eingaben zielen exakt auf e101e393. Vor späterer Einreichung muss der tatsächlich autorisierte Zielcheckout genau passen. Ein anderer HEAD, auch bei identischem App-Code, benötigt eine neue Prüfung. Keine alte Freigabe stillschweigend umschreiben.

Zusätzliche Pfadprüfung: geschützte Orchestrierungsdatei, Testdatei und AndroidManifest werden als automatische Modell-Patchziele abgewiesen. Der Plan ist keine zweite Queue, kein Grant und kein Scheduler.

## Frisch ausgeführte Prüfungen

| Helfer / Prüfbereich | PASS | FAIL | SKIP | Aussage |
| --- | ---: | ---: | ---: | --- |
| Reparatur: Bot-Zuordnung und ECHO-Bericht | 17 | 0 | 0 | Identität, Budget, getrennte Rollen, Bindung, Belegintegrität und Ablehnungen |
| Implementierung: Service und Teamleiter-Schleife | 31 | 0 | 0 | Konfigurationsschutz, Wiederanlauf, Quellenwechsel, Abbruch und begrenzter Ablauf |
| Avatar: sieben vorhandene JUnit-Klassen | 57 | 0 | 0 | Reale reine Kotlin-Produktionslogik für Zustand, Sprachreihenfolge, Blick, Kamerapolicy und Bewegung |
| **Gesamt der getrennten Prüfblöcke** | **105** | **0** | **0** | Hostnachweis; keine neue Modellreparatur oder Geräteabnahme |

Ausgeführte Node-Befehle auf sauberem Checkout:

~~~sh
node --test .github/continuum/ccc1-bot-assignment.test.mjs .github/continuum/ccc1-bot-report.test.mjs
node --test .github/continuum/coding-service.test.mjs .github/continuum/local-teamleader-loop.test.mjs
~~~

Node v24.22.1. Avatar: OpenJDK 21.0.13, vorhandener Kotlin-Compiler 2.0.20, JUnit 4.13.2, JSON 20240303. Direkte JVM-Kompilierung, keine Downloads, kein Gradle-/APK-Build, keine ersetzten Produktionsklassen oder Android-Stubs. Die tatsächlich verwendeten Aufrufe und Pfade stehen in avatar/run_existing_avatar_tests.py und den *.command.json. Das Skript zeichnet diesen Prüfaufbau auf; bei Reproduktion außerhalb der Umgebung sind die vorhandenen Compiler-/Bibliothekspfade anzupassen.

Der Koordinator prüfte Node-Rohprotokolle, Exitcodes und Ausgabehashes, alle sieben JUnit-Protokolle sowie alle 19 ausgewählten Avatar-Produktions-/Testdateien gegen ihre Git-Blobs auf e101e393. Keine Abweichung. Keine Tests gelockert oder Produktionsdateien geändert.

**Avatar-Grenze:** Der Endetest verwendet interrupted=false; der Abbruchzustand wird direkt über clearSpeech() geprüft. Kein echter Android-TTS-Abbruchcallback mit interrupted=true, keine sichtbare App, Filament/GPU, Hardwarekamera oder Fold7-Ausführung. Die 57 Tests sind keine Iris-/FORGE-Erfolgsquittung.

## Warum die Live-Aktivierung offen bleibt

1. Der vorhandene Statusleser meldete hier NO_LOCAL_STATE. Die historischen Pfade für Modellkonfiguration, llama-Binary, Profilbridge und Experimenthost fehlen. Der bekannte Dienstordner war nicht vorhanden. Das betrifft nur den beobachteten Executor; andere Rechner, Ausführungsumgebungen und das Telefon wurden nicht geprüft.
2. Kein aktiver Continuum-/Android-/ADB-/SSH-Gerätekanal ist als Werkzeug angebunden. GitHub-Quellzugriff erreicht keinen laufenden Besitzer-Service.
3. Der MCP-Server hat exakt vier lesende Methoden: get_status, list_agents, list_tasks, get_evidence. Die tatsächliche Dispatch-Liste enthält keine Auftragsannahme. prepareServiceTask und submitOwnerTask sind getrennte lokale Besitzer-Schnittstellen.

Die vorhandene Dokumentation docs/research/omega/independence/mcp/OWNER-ACCESS.md kennzeichnet die direkte ChatGPT-Verbindung als NOT_CONFIGURED. Es wurde keine neue Verbindung, kein Tunnel, öffentlicher Listener oder zusätzlicher Dienst eingerichtet. Ein Profil, ein grüner Hosttest oder ein PR-Kommentar ist keine Live-Auftragsannahme.

## P0: fehlender negativer ECHO-Rückkanal

Der spätere echte Dienstversuch **S01** scheiterte. Originale: docs/research/omega/independence/CLOSEOUT.md und native-review/S01-RESULT.md. Dokumentiert sind ABSTAINED/REFUTED, UNKNOWN/CODING_MODEL_UNAVAILABLE und fehlende LINT-/ECHO-Ausführung. Die genaue native Fehlerursache ist nicht abschließend belegt.

Der aktuelle Quellpfad erklärt die fehlende abschließende Rückmeldung: runCodingTask wirft bei strenger Ausführung mit null zugelassenen Inferenzbeobachtungen vor der Berichtserstellung. executeServiceDelivery schreibt das öffentliche Ergebnis erst nach erfolgreicher Rückkehr; sein äußerer IPC-Catch liefert nur ein Fehlerobjekt. **Die 105 bestandenen Prüfungen schließen diese gesonderte Lücke nicht.**

Konkreter Reparaturauftrag für den bestehenden Dienstzweig: einen wahrheitsgemäßen negativen, an Auftrag und Quelle gebundenen Besitzerbericht anschließen. Bericht, bestätigtes Ressourcenende, erfolgreiche Reparatur und Lernerfahrung bleiben getrennte Entscheidungen. Vorhandene Verträge/Gegenprüfungen in PR #471–#473 verwenden. Die geschützte Steuerung wird nicht durch ein automatisch erzeugtes Modellpatch verändert; die Korrektur gehört in einen isolierten und unabhängig geprüften Entwicklungszweig.

Die historische S01-Sperre wird weder entfernt noch durch neuen Ledger umgangen. S01 ist verbraucht und wird nicht automatisch wiederholt. Das Fehlen der alten Sperrdatei in diesem anderen Executor belegt keine Freigabe des ursprünglichen Hosts.

## Nächster operativer Schritt und Rücknahme

Zuerst den vorhandenen Besitzer-/Termux-Worker oder bisherigen Coding-Host in der ausführenden Sitzung erreichen. Tatsächliche Konfiguration, Modell-/Runtime-Identität, Profilbridge und Isolation prüfen. Bestehende Geräteintegration zuerst prüfen; keine neue obligatorische PC- oder Termux-Einrichtung. Dann exakte Quelle binden, bestehenden lokalen prepare/submit-Weg verwenden und Annahme, Ausführung sowie Rückmeldung tatsächlich beobachten.

Der Nutzer hat den Aktivierungsauftrag erteilt; die aktuelle Grenze ist technische Erreichbarkeit, keine noch einzuholende pauschale Zustimmung.

Dieser PR enthält ausschließlich Übergabe, nicht zugestellte Aufgaben und Prüfbelege. Kein Produktivcode, Modell, Paket, Versionswert, Signierer oder Updatekanal geändert. Kein Main-Merge, Release, Installationsversuch, kostenpflichtiger Aufruf, GitHub-Actions-Lauf oder Wechsel des Offline-Schalters. Rücknahme: diesen Dokumentations-/Evidenzzweig nicht integrieren. Keine Datenmigration und keine gestartete Daueraufgabe.

Evidenzindex: docs/evidence/bot-activation-20261104/summary.json. Dateiintegrität: SHA256SUMS.json.
