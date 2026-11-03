# SYNAPSE SWARM Ω — gesicherte Übergabe nach Umgebungsausfall

Stand: 03.11.2026. Dieses Dokument ist der aktuelle Einstieg; PLAN.md bleibt die ursprüngliche Vorregistrierung, INTEGRATION.md die fortgeschriebene Aufrufermatrix.
Repository: Ullmann27/device-ai. Chief: codex/synapse-swarm-chief-20261103, Draft-PR #423.
Letzter gesicherter Produktionsquellstand: **c7ec853e2ffaa488b92826306f015bfaa67f5bb7**, Tree **3b4060f6f46fddd19604ee94a2da2e6d0cb89b79**.
Ein nachfolgender Dokumentationscommit verändert diesen Produktionsstand nicht. Den tatsächlichen Branch-HEAD frisch über GitHub lesen.
Kontingent: **NOT_VISIBLE**. Abschluss wegen tatsächlichem Infrastrukturverlust, nicht wegen erfundener Restquote.

## Unterbrechung und Wiederaufnahmegrenze

Die ausgewählte Umgebung ccarenv_b64_Y2NhcmVudl9iYmJlM2E2MGUzNDg4MTkxYTNkMjFkZDg2NTZlZjg1MA wurde UNAVAILABLE.
exec meldete Transportabbruch und anschließend "No usable execution environment is available".
Der Cloud-Status meldete gleichzeitig running/connected; diese widersprüchliche Anzeige ist kein erfolgreicher Wiederanlauf und keine Ursachenanalyse.
GitHub-Connector blieb erreichbar. Der Produktionscommit und sämtliche unten genannten Review-Checkpoints wurden darüber nachgelesen.
Keine neue Ausführungsumgebung, kostenpflichtige Ressource, Installation oder unsichere Ersatz-Ausführung wurde gestartet.

Letzter eigener Android-Lauf: Quelle c50266b27ba65a7e253be75c67b5f4d7f89a6973, Produktion und Testquellen kompiliert; **13 UI-Tests: 12 PASS / 1 FAIL / 0 SKIP**.
Fehler: 16 rasche Öffnungen reihten 16 Speicherzugriffe ein, erlaubt war einer.
Reparatur **407cdea413dbc8f31e5e662e0317eec5daf897dc** ist implementiert und gepusht, aber **NOT_RUN**.
Die gemeinsame Abnahme von c7ec853 ist **NOT_RUN**. Kein neues APK dieser Welle wurde fertig gebaut oder veröffentlicht.

## Tatsächliche Entwicklungsagenten

Sechs getrennte Spezialistensitzungen plus Chief, in Wellen und mit exklusiven Schreibbereichen:
1. synapse_registry_builder: Profilvertrag und bestehender Chief-Koordinator.
2. synapse_registry_challenger: unabhängige Persistenz-, Berechtigungs- und Manipulationsfälle.
3. synapse_lifecycle_builder: Pool-Reparatur; danach getrennte Bereitstellung der genehmigten Modellartefakte.
4. synapse_lifecycle_challenger: unabhängige Poolprüfung; danach Prüfung der neueren MΩ-Laufzeit/Gedächtnisquellen.
5. synapse_p0_audit: unabhängige MΩ-Hostprüfung; anschließend unabhängige Android-UI-Prüfung.
6. synapse_sources: Primärquellen und vorregistrierter Budgetvergleich.
Das sind Entwicklungsagenten, keine 20 laufenden Produktbots oder nachgewiesen unterschiedliche Modellarchitekturen.

## Eingebaute Änderungen und genaue Grenzen

### Persistente Profile

Produktionsquelle **2c5cd84ad7f4e19b9f52430856d311bfdf9dc18c**, Builder-HEAD **e12c28f51c0f91f2dfb37dd4bc34d372358c8ab5**, PR426.
ChiefBotProfileStore.kt ergänzt den gezielt übernommenen ChiefDelegationCoordinator.kt aus PR384.
Stabile IDs, versionierte Namen/Rollen/Stile, getrennte Kontext- und Workspace-Bindungen, konservative Modell-/Ressourcenmetadaten; Rollen verleihen keine Fähigkeiten oder Rechte.
Die neuesten 20 Startprofile des Besitzers sind enthalten: Atlas, Mira, Echo, Mnemos, Prisma, Nova, Sigma, Kora, Forge, Lint, Argus, Pulse, Link, Chronos, Vox, Iris, Orbit, Archiv, Scout, Helix.
Die ersten drei Prüfidentitäten sind Atlas/Kora/Lint. Vorhandene Namen und Daten werden beim Seeding erhalten; ein weiterer Bot ist möglich.

Private Arbeitsnotizen verwenden **FileContinuumObjectStore** aus cognitive/ContinuumState.kt, mit eigener Identitätsbindung.
Keine zweite Fakten-/LEX3-Datenbank. Metadatenformat bot-profiles-v1.bin ist checksum-geprüft, atomar ersetzt, prozessübergreifend gesperrt.
Standard: 10.000 Profile / 8 MiB Metadaten, konfigurierbare Ressourcengrenzen; Seiten maximal 100 Einträge.
Dies ist ein **begrenzter Gesamtsnapshot mit Keyset-Pagination, noch keine indizierte Datenbank**.
Kontextobjekte bleiben innerhalb eines begrenzten Stores erhalten; Zeigerwiderruf funktioniert auch bei vollem Store, physische Objektbereinigung fehlt.
Eine passende requesterId ist nur eine vertrauenswürdige App-Schnittstelle, keine Authentifizierung beliebigen Fremdcodes.
Workspace-ID, Postfachname und gemeinsames Rig-/Stimmprofil sind Metadaten; keine Container, dauerhaften Mailboxen, Terminalprozesse oder echte Bot-Gespräche werden daraus behauptet.
Persönlichkeitsdaten sind editierbar; verhaltenswirksame Modellanbindung und E5-Blindtest bleiben offen.

Autor: **25/25**. Unabhängig: **11 Registry-Prüfgruppen + 4 Koordinator-Gegenbeispiele + 8 zusätzliche Manipulationsgruppen PASS**.
Die vier historischen Koordinatorfehler wurden mit echten Tests reproduziert (**0/4 → 4/4**): veränderbare Werkzeug-/Capability-Listen nach Freigabe und ein hängenbleibender Auftrag bei abgewiesenem Pool-Submit.
Review-HEAD **3c155aaee9e6bf4744ccf3d54fb7e8d2366fa1cb**, PR422, bereits integriert.
Belege/Harness: docs/research/omega/synapse-swarm/registry-review/ und tools/synapse-registry-review/.
Separate JVM-Neustarts und konkurrierende Prozesse wurden geprüft.

Unabhängige Speicherbeobachtung (kein Intelligenzbenchmark):
20 Profile 4.268 Byte; get p50/p95 0,487/0,719 ms; page 0,350/0,578 ms; JVM-Start plus Katalog 118,531/133,166 ms.
1.000 Profile 210.068 Byte; get 1,198/1,431 ms; page 1,278/1,640 ms; JVM-Start plus Katalog 248,463/267,618 ms.
5 Warmups + 30 Zugriffsmessungen, 2 Warmups + 10 separate JVM-Neustarts, 512 MiB Heap.
Ein erster Messwerkzeugaufruf scheiterte vor Messbeginn mit Exit127; Rohfehler bleibt erhalten. Kein Energie-, Fold7- oder Modellvorteil daraus abgeleitet.

### Chief-Lifecycle und Ressourcen

Produktionsquelle **8eddcdb5b307cbab9c1e3bbcfdf37bbac8637cd7**, letzter Builder-HEAD **833f1e09d21d84af1046bd82215a546f60ab2d88**, PR425.
ChiefWorkerPool meldet beim Shutdown auch verworfene wartende Aufträge genau einmal zurück, sperrt neue Aufnahme und bewahrt die bestehenden reentranten Rückmeldungen.
Eine begrenzte interne Worker-Queue und explizites Entfernen abgebrochener Futures verhindern das Zurückhalten unbegrenzt vieler Closures hinter einem nicht kooperierenden nativen Aufruf.
Autor **14/14**, unabhängig **8/8 + 9/9 bestehende Tests**, keine Auslassungen.
Unveränderter Ressourcenversuch: 25 akzeptierte Versuche, ein physischer Worker, **24 → 0** zurückgehaltene Futures; neue Arbeit nach Freigabe ohne Replay.
Das ist keine bestätigte Beendigung eines nativen Aufrufs, der Interrupts ignoriert. UNKNOWN-Nachzustände müssen weiterhin geprüft werden.
Review-HEAD **573525c9ff6bcb63ecefd22fa184070e0d1442ff**, PR424, integriert.
Belege: docs/research/omega/synapse-swarm/lifecycle-review/; tools/synapse-lifecycle-review/run.sh und run-retention.sh.

### Native Teamoberfläche und tatsächlicher Aufrufer

AgentActivity → Mehr → Team · Bots sowie bestehendes Entwicklungszentrum → ContinuumBotRosterDialog.
Paginierte native Karten, Anlegen eines weiteren Profils, Umbenennen, Rolle/Stil bearbeiten, eigene Arbeitsnotiz anzeigen/speichern.
REGISTRIERT und fehlende Ausführung werden ehrlich angezeigt; keine erfundenen Fähigkeiten oder Terminalbilder.
Die vorhandene JARVIS-Statusfrage nutzt weiterhin ChiefWorkerPool und liest jetzt die öffentlichen Profilnamen; private Notizen/Owner-Memory werden dort nicht gelesen.
Dieser Statusanschluss ist **noch keine Bot-Planungs-/CCC1-Ausführungskette**.
Die 407-Reparatur benutzt eine wiederverwendete Activity-Dialoginstanz und maximal einen ausstehenden Speicherzugriff. Weitere Navigation erhält einen sichtbaren Wiederholhinweis, keine versteckte Warteschlange.
Schließen/Activity-Ende invalidiert die Darstellung, nicht akzeptierte Speicherwrites oder unabhängige Coding-Aufträge.
Originalkugel, optionaler echter 3D-Renderer, bestehender TTS-/Avatar-Vertrag und Entwicklerfunktionen bleiben erhalten. Keine neue Kamera, Stimme, zweite Inferenzpipeline oder pro-Karte-Renderloop.

Unabhängige UI-Tests: PR428, gesicherter Rohstand **2c00db420d7bf3dff4b138024ac6e8b7bd35e379**, finaler reiner Handoff-HEAD **de153623a8cc99403b84f239fc3a21d6497117f3**.
tools/synapse-ui-review/IndependentBotRosterUiTest.kt, source.init.gradle; Belege docs/research/omega/synapse-swarm/ui-review/.
Frühere Versuche ehrlich erhalten:
- Gradle-Konfigurationsfehler durch --tests in einer strikt begrenzten QA-Invocation, keine App-Prüfung.
- Android-Kompilierfehler in a479 (View.background statt Int-Farbe), durch c502 behoben.
- c502: **12/13 UI PASS**, Warteschlangenbudget FAIL.
- 407/c7: nur statisch geprüft, **kein GREEN** wegen Umgebungsausfall.
Die alten Stale-Callback-Testannahmen (olderLoad und lateSave) von mehreren eingereihten Vorgängen müssen transparent an die neue Busy/Retry-Semantik angepasst werden; keine Abschwächung der Anti-Stale- und Ein-Auftrag-Grenze.

## MΩ-1: neue Quellen, genehmigtes Modell und offene Kernabnahme

Ursprünglich wurden efe622dd (PR392) und 713949a (PR410) kombiniert.
Das alte HANDOFF/PR-Body war teilweise überholt: MODEL-ADMISSION.md hatte bereits eine ausdrückliche Freigabe für das genaue Qwen-Hostmodell. Unsere anfängliche Formulierung "kein genehmigtes Modell" war zu breit und wurde korrigiert.
Neuere akzeptierte Quellen:
- Chief **5684efb1a2804fe28bbb0b52dc3904e1745afef1**: begrenzte Git-Abstammungsprüfung und expliziter Recall-Ablationsschalter, aktuelle Grants/Widerrufe bleiben nötig.
- Runtime PR427 **c78cf8a551a4d12f5dbb27ec3641fd4ae9e6f76d**, Produktion **9e28d24bcf9ef11d9b19f5fec1be60efe2c6ab96**: exakt geprüfter nativer Modell-/Binary-Paarvertrag, Ablehnungen bewahren echte Inferenzbeobachtung.
- Späterer Chief **d8a927dd6ced4dea489e2042afb4b5624157fa3b**: nur relevante zusätzliche Produktionsänderung repair-loop.mjs aus **19474261**, hier als **c7ec853e2ffaa488b92826306f015bfaa67f5bb7** übernommen.
Die Inferenzzählung akzeptiert nur originale gebrandete, eingefrorene und korrekt zugeordnete Beobachtungen. Dekodieren ohne korrekten Patch ist kein Erfolg.

Unabhängiger neuer Quellreview: **0ff9df3cd894663461b82264a76228a1e8b835b6**, PR431 (Reviewzweig noch separat).
Frische Prüfungen der festgehaltenen Komposition: **50/50 Runtime, 33/33 Lineage/Ablation, 83/83 Vertragsfälle**.
Auf d8 zusätzlich **28/28 Aufrufprüfungen** (explizit synthetische Test-Seams) und **84/84 nicht instrumentierte kanonische Regressionen**, keine Auslassungen.
26 Zertifikats-/Build-/Artefakthashes abgeglichen. Kein frischer Modelllauf durch diesen Reviewer.
docs/research/omega/synapse-swarm/runtime-audit/HANDOFF.md im Reviewzweig enthält genaue Quellen, Logs und Hashmanifeste.

Früher P0-Audit c6bd3072993a5653892a56c8338b21443987f1b1 (PR421, integriert): 73/73, 22/22 native synthetische Prozesse, 35/35 unabhängige Laufzeitfälle; tatsächliche Docker-Ausführung 18/18.
Das lokal neu gebaute FROM-scratch-Isolationsimage hatte Digest sha256:4838093b14e654fcf3b69decfe8b8a1fe75ef6c288ea5dadc4e169131716494c; Testkonfigurations-Pins wurden ausdrücklich substituiert, kein alter Hash vorgetäuscht.
Gedächtnis-Altfälle **45/46**, ein veralteter Export-Abwesenheits-Test unverändert als FAIL erhalten; zwei zusätzliche Fälschungsprüfungen PASS. Nicht stillschweigend als46/46 ausgeben.
Alle diese Counts gelten für ihre eigenen Quellstände, nicht pauschal für c7ec853.

Die andere MΩ-Laufzeitumgebung dokumentiert unabhängig geprüfte echte Host-Inferenz: 142 Tokens / 26,562 s und 50 Kontrollfälle.
Eine blinde Antwort bestand nur **6/8**, mit zwei Regressionen. Das ist **kein verifizierter unbekannter Reparaturerfolg**.
Die QA-Aufgaben aus PR413 sind nicht durch externe Entwicklungsagenten zu lösen oder nachträglich passend umzuschreiben. Neueren PR413-Status zuerst lesen und doppelte Benchmarkarbeit vermeiden.

### Tatsächlich hier bereitgestellte Modellartefakte

Genehmigtes Modell: Qwen/Qwen2.5-Coder-1.5B-Instruct-GGUF, Revision f86cb2c1fa58255f8052cc32aeede1b7482d4361.
Datei qwen2.5-coder-1.5b-instruct-q4_k_m.gguf, **1.117.320.768 Byte**, SHA256 **cc324af070c2ecbfd324a30884d2f951a7ff756aba85cb811a6ec436933bb046**; Apache-2.0.
In dieser Umgebung vollständig heruntergeladen und Hash/GGUFv3 geprüft, außerhalb Git:
 /workspace/synapse-local-model/qwen2.5-coder-1.5b-instruct-q4_k_m.gguf
Offizielle llama.cpp-Quelle **6cb3e672e8d10fd4feab1a048bf2967c40623133**, Tree **42065e172024f95eb242763caa06812c09d363e7**, MIT.
Neun Compiler/CMake/Support-Library-Hashes stimmten mit der genehmigten Buildprovenienz überein.
Erwartetes Binary: **c2c13a2a27c5a85137b6d083074fa83e0d990db129e715f8452352e3730cbf5a**.
Rezept/Logs außerhalb Git: /workspace/synapse-local-model/metadata/replication-recipe.json.
Configure lief mit Exit0 (0,924 s). Build llama-completion startete mit parallel2, dann Umgebungsausfall.
**Buildabschluss, Binaryhash, runtime-env.json und neue Inferenz: UNKNOWN/NOT_RUN.** Vor Neustart laufende Prozesse/Logs/Dateihashes prüfen, nicht blind erneut starten. Bei verlorener Umgebung erneut nur dieselben genehmigten Pins beschaffen.
Prüfbarer Prerequisite-Checkpoint **ddb683c2249b9046a2b9d0b6e4ee650a8f172329**, PR433; finaler Ausfall-Handoff-HEAD **71761204ce64f0d443d5abb2e2e7eba5bd95fde9** mit RESUME-AFTER-EXECUTOR-OUTAGE.md. runtime-env.json wurde nicht erstellt. Keine Gewichte/Binaries in Git.

## Wissenschaftliche Entscheidung

Primärquellen-HEAD **ad5a5d586230f3d658baaf486cfd15da40d20338**, PR429, integriert.
17 Abrufbelege mit Datum/Hashes, Lizenzgrenzen und A0–A4-Protokoll unter sources/.
Grok-Bot-Dokumentation beschreibt geteilte Dateien/Zugangsdaten: diese Eigenschaft wurde nicht als Isolationsvorlage übernommen.
Budgetgleiche Einzelagent-/Teamvergleiche, Android-Ausführungsgrenzen, AVF, TTS-Timing und PRoot wurden geprüft.
CfC/SNN noch nicht integriert: ohne relevante Daten/einfache Baselines kein nachgewiesener Nutzen.
E1–E3/E5/E6-Modell-/Geräteexperimente bleiben NOT_RUN. Keine Intelligenzsteigerung aus Profilzahl, HTTP-Abrufen oder Hostvertragstests behaupten.

## Gerät, Avatar und Release

SDK-ADB war vorhanden; adb devices -l ergab eine leere Geräteliste. Tatsächliche Android-Version/installierte Version: NOT_RUN.
Originalpaket at.heinz.deviceai.continuumlocal; keine Signatur-/Update-/Main-Änderung.
Frisch gelesenes veröffentlichtes Katalogmetadata: versionCode39 /0.3.38, gleiche originale APK-URL; owner-catalog.json.
Keine neue APK dieser Welle. Der letzte zuvor gebaute unsigned Code40 auf7247e99d ist historische Evidenz, keine Abnahme der neuen Bots.
Bestehendes GLB f39a89fc8b119487ab983151f8e56203c4adaa6b3aa9c5edbd0b937d1318f84e und ursprüngliche Kugel bleiben erhalten.
Referenztreue/Photorealismus, Kamera-Tracking, zusätzliche Bot-Avatarvarianten, freihändiger Dialog, GPU/FPS/Energie und Fold/Unfold bleiben offene Abnahmen.

## Nächste zehn ausführbare Schritte

1. Umgebung wieder verfügbar machen; nicht nur Providerstatus glauben. gitstatus/Prozesse/Artefakte prüfen, neue SHA von PR423 und unten genannten Reviewzweigen lesen. Keine unbekannte Werkzeugaktion wiederholen.
2. Reviewartefakte PR428/431/433 kontrolliert übernehmen; ursprüngliche REDs erhalten. Zentraler Quellstandc7ec853 ist gepusht.
3. UI407-Reparatur unabhängig mit dem 13-Fälle-Vertrag plus transparenter Busy/Retry-Anpassung prüfen; Activity-Instanzwiederverwendung und echte öffentliche Statusantwort prüfen.
4. Exakte native Buildlogs/Prozesse sichern; ausschließlich genehmigte Buildreproduktion fortsetzen. Binary erst bei exakt erwartetem Hash und Herkunftsprüfung zulassen; keine Registry-Pins passend zum Ergebnis ändern.
5. Aktuellen PR413-Handoff lesen. Einen eingefrorenen unbekannten Fall durch echten lokalen Hostprovider → kanonisches CCC1 → isoliertes RED/PATCH/GREEN → unabhängige Tests laufen lassen; keine externe Lösung/Trainingsdaten nachreichen. Fehler und Ressourcen vollständig zählen.
6. Danach echte genehmigte Erfahrung mit aktuellen Grants/Widerruf sichern, Neustart und verwandten neuen Fall/Recall-off vergleichen. Keine synthetische Durable-Store-Prüfung als Lernerfolg ausgeben.
7. Finalen Produktionsstand einfrieren. Ungefilterte Android-JUnit, neue UI-Prüfungen, konfigurierte NewApi-Lint/Compile, Node/Backend, SWB/CRE-Parität und betroffene MΩ/Persistenz/Pool-Negativfälle genau darauf ausführen. Nur einen schweren Build/Modelllauf gleichzeitig.
8. Reale pro-Bot-Task-/Mailbox-/Workspace-Zuordnung an bestehenden Chief/CCC1 anschließen; immutable Kontexte, aktueller Grant, übergeordnete Budgeterhaltung, kein Default-Replay; Host und Android ausdrücklich trennen. Profil-Snapshot später versioniert indizieren.
9. E1–E3 nach dem eingefrorenen Gesamtbudgetprotokoll ausführen; Persönlichkeit/Sprach-/Avatarereignisse an Bot/Task/Utterance binden und Barge-in getrennt vom Auftragsabbruch prüfen. Kein zweiter Modell-/TTS-/State-Kern.
10. Erst mit tatsächlichem Zielgerät M5: Offline-Inferenz, isolierter Runner, Audio, beide Displays, Lifecycle, RAM/Thermal/Energie. Danach unsigned Artefakt und konkrete APK-Hashes prüfen; Originalsignatur/Release/Installation benötigen die bestehenden Besitzer-Gates.

## Bekannte Testumgebung / Wiederverwendung

Vor Ausfall: /workspace/continuum-build-env/env.sh; Gradle8.11.1, JDK17-Kompilierung/JRE21-Tests, AndroidSDK36, Node24.19; 4CPU/16GiB.
Native Grafik-Hosttests sind keine GPU-/Fold7-Tests.
Vorhandener kombinierter Harness: docs/evidence/breakthrough-quality-20261103/harness/run-combined-qa.sh.
Review-init: tools/synapse-ui-review/source.init.gradle; frühe gezielte Prüfung mit synapseUiReviewOnly, final ohne Filter.
Standalone-QA erlaubt nur testDebugUnitTest/lintDebug/compileDebugKotlin. Keine APK-Assembly unter diesem Profil und keine Schwächung des Releaseguards.
Nicht ungeprüft alte absolute Build-/Reviewpfade auf einer ersetzten Umgebung voraussetzen.
