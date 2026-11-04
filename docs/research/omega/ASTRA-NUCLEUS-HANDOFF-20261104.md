# C1 — aktuelle Integrationsleitung, 4. November 2026

Dieser Abschnitt führt den direkten Besitzerauftrag auf demselben zentralen Board-Pfad aus #498 fort. Der erhaltene historische Teil ist keine aktuelle Prozess- oder ACK-Liste.

## Gemeinsamer Kandidat und Quellen

- Einziger C1-Integrationszweig: `integration/continuum-c1-candidate-20261104`, Draft **#519**; kein Main-Merge.
- Gewählte Basis #513: `28b228698aef8c52e46c72b71fdd5e1915d6e76c`; vollständiger Baum `e52acc146e66ae66a95429da2fb0f34242ad1791`. Lokales Äquivalent `2d5ce59b786f427eacb394484d81796568bb87b4` besitzt exakt diesen Baum.
- #491 → #506 → #512 → #513 ist unabhängig als tatsächliche Commit-Abstammung geprüft. #506 `8abea828833d8833d40e3d3d36feaa6d3685b82e` bleibt Vergleichsanker; neuere Arbeit bleibt erhalten.
- Früherer Routing-Android-Fokusprüfstand: lokal `165fc9f58ae9e36295690df60145174fd5dc58e7`, remote `401a5d01a7b2e0e0f8c50531daf5fa24814fbbda`, identischer vollständiger Tree `4b3e47f30517cf952ab50c0a52b27bb96d281504`. Echte App-/UI-Testkompilierung, 29 gezielte Router-/Activity-Tests und konfigurierter NewApi-Lint bestanden; keine vollständige Android-Abnahme.
- Früherer eigener Memory-Android-Fokus: `d3487a83ea1cc61a34afcde4e17601c56def9ed2` / remote `4b6c38902c1ebb93598dd125f5ea0bcc3cddabf8`, Tree `5c9745630cca44da3cb297761c8c59622e6c3a15`: Memory017B mit 23 PASS / 0 FAIL / 0 SKIP, Exit 0; unabhängig nachgerechnet. Der getrennte letzte UI015-Fokus auf cdea744/a8ebdd9 bleibt mit 20 Fällen, 12 PASS / 8 FAIL / 0 SKIP negativ. Layout018 besteht anschließend auf `59d8d7e25a5936403e8579373883fa04bec5c37f` / `a6135f24629ba9151bbc06888bc977325d95e6f1`, Tree `90d68760d9a0533c169697902b61eda126e9b20b`, seinen eigenen 7/7-Fokus.
- Letzter vollständiger eigener Node-Prüfstand: lokal `8873030fdfe23bdd4153f96004b786bff2cddecf`, remote `1a5302c504a78a50d5e64ca727f2915529bf2b06`, gleicher Tree `d1a1b733ad37f21c155527fa0c9532f56fb1b934`.
- Neuer eigener **critical PASS** auf lokal `681bdec2527c6a5ef4c773e8211cf62e4ac4ad06` / remote `11ed97e237f4f939b38e61b53d8942d6b3e22b49`, Tree `1ab9ee719626c15a8510e10e478f5e7da4a41a2b`: 135 PASS / 0 FAIL / 0 SKIP, Exit 0, kanonischer Receiptvalidator Exit 0. Die frühere 131/4-Prüfung bleibt historisch negativ erhalten.
- **Gesamtstatus INTEGRATION IN ARBEIT / VOLLSTÄNDIGES GATE OFFEN:** letzter full-node auf 8873030 bleibt 2349 = 2280 PASS / 32 FAIL / 37 SKIP; UI015 bleibt negativ. Diese Umfänge überlappen und werden nicht addiert. Der aktuelle critical-PASS ersetzt weder full-node noch Android/Backend/APK/Gerät.
- Anschließend sieben vorhandene #517-Quellvertragskorrekturen begrenzt übernommen: 48 gezielte Tests PASS, neun absichtliche Vertragsverletzungen jeweils erkannt, unabhängiger statischer Review positiv. Neuer Gesamtlauf steht nach weiterer serieller Integration aus.

- Neuester gezielter Worker-Prüfstand: lokal `9a442485f2569b39be2bcd8d6687da6f6c15c45d`, remote `0d8a3d46c409e5918c49b6f3407bb63f3519b846`, Tree `8fe54137e976c718c91a4b3bbcfc02d50ca52bdd`: **26 PASS / 0 FAIL / 0 SKIP, Exit0**, Ende22:12:10UTC. Anschließende OPT021-Worker-Änderung hat noch kein eigenes PASS.

## Aufgaben, Eigentümer, Abhängigkeiten und Rückmeldung

| Task-ID | Eigentümer / einziger Einbauer | Quelle und Ergebnis | Status / nächster Schritt |
| --- | --- | --- | --- |
| C1-SOURCE-001 | C1 / source_audit read-only | #491/#506/#512/#513, vollständige Trees und Abstammung | SOURCE_REVIEW_COMPLETE; Botrouting-Fehler konkret belegt |
| C1-INTEGRATE-001 | C1 | Vier ECHO-Dateien aus #515 `572e12ecc4ce5f7c79f2634a5b5773047b6d674f`; gesichert in remote `474a457f5fa6230d45fd4c773fe67e219df73984` | Echter RED, 52 gezielte PASS; unabhängiger statischer Review und eigener unabhängiger 9-Test-Unterlauf PASS; keine echte Modellinferenz |
| C1-QA-SOURCE-002 | C1 | Begrenzter 16-MiB-Git-Puffer, vollständiger Quellenbestand; remote `1a5302c504a78a50d5e64ca727f2915529bf2b06` | 30 gezielte PASS, unabhängiger statischer Review positiv; kanonische Gesamtgates führen jetzt Tests aus |
| C1-CONTRACTS-003 | C1, Lieferant #517 | Vier Archivpatches bei `46e5fa6b09454ad6f6862e80478eb0f2d5545149`, sieben Testdateien | 48 PASS / 0 FAIL / 0 SKIP; 9/9 Negativkontrollen erkannt; statisch unabhängig befürwortet |
| C1-APK-CHANNEL-004 | C1 / apk_readiness | Gradle-Konfiguration, #475, tatsächlicher Kanalversuch und Toolchain-Dateien | Aktueller Kanal UNVERIFIED: Web-Tool kann URL nicht abrufen; historische41 nicht als aktuell ausgeben |
| C1-AVATAR-005 | C1, Lieferant #517 | Signed-zero-Patch AvatarIdleMotion + zwei Grenztests aus demselben Archiv | TARGETED_KOTLIN_PASS; echter ArrayIndexOutOfBounds-RED, danach 2/2 GREEN; unabhängiger statischer Review positiv |
| C1-ROUTING-006 | C1, begrenzter Patch von source_audit | routeBotInput; remote `401a5d01a7b2e0e0f8c50531daf5fa24814fbbda` | TARGETED_ANDROID_PASS; 18 Router + 11 Activity, 0 FAIL/SKIP; App-/UI-Kompilierung und NewApi-Lint Exit 0. Vorheriger isolierter 27-Test-Lauf überlappt; nicht addieren |
| C1-UI-CONTRACT-007 | C1 / apk_readiness read-only | Vorhandenes UI-Vertragspaket des laufenden Teams #517 | TARGETED_SOURCE_PASS; eigener48/48-Lauf, unabhängiger statischer Review,17 Autoren-Negativlogs geprüft; keine Geräteabnahme |
| C1-QA-GUARD-008 | C1; Produktion #505, Tests #517 | Sechste Signing-Umgebungsvariable in bestehendem QA-Guard | TARGETED_HOST_PASS; eigener43-Test-RED mit2 Fehlern, danach43/43 GREEN; direkter #505-Blobabgleich und unabhängiger statischer Review |
| C1-AVATAR-009 | Lieferant #517; alleiniger Einbau C1 | Vier-Dateien-Paket aus `f17ee62f8cfda8914ed217fe2ed88882ba1fa8a4`, Patch SHA256 `3b0fcaa1a9d093bc26aa7e02fb7b262fcfaf7351f2a616761f223e139e350289` | TARGETED_ANDROID_PASS; eigene13/13 auf53ca8b6/5d7ddf,0FAIL/SKIP. Separater fremder Vollgate-Timeout bleibt negativ; kein Gesamt-PASS |
| C1-PATH-010 | C1 / apk_readiness read-only | Quellprüfung bei `ced43fbe26cfc97c5c8fbbc3f429cd0845047c4f` | SOURCE_TRACE_ONLY; tatsächlicher Compiler/Hub/Mapper/Recall-Orb-Anschluss nachvollzogen; kein Modell-/Gerätetest |
| C1-INTAKE-011 | C1 / source_audit read-only | #505 `b2bc510a59e29d4ece478109e12b9785be11ee7d`; drei Tagesgrenzen-Dateien | TARGETED_HOST_PASS; eigener29/29-Lauf aufce5bcf6/9c09a6,0FAIL/SKIP. Tatsächlicher Event-/Androidpfad bleibt eigenes Gate;14 Abschlusszustellungs-Dateien separat als C1-SEQUENCE-013 aufgenommen |
| C1-GATES-012 | C1 / apk_readiness read-only | Geltende Projektverträge und Owner-QA-Skript bei `165fc9f58ae9e36295690df60145174fd5dc58e7` | SCOPE_REVIEW_COMPLETE; volle Unit/UI-Suite und 11 Pflichtsuites plus 5 Mindestzahlen bleiben Pflicht; zusätzliche Tools-FAIL bleiben separat rot |
| C1-SEQUENCE-013 | C1, Lieferant #505; apk_readiness/source_audit prüfen | #505 `b2bc510a59e29d4ece478109e12b9785be11ee7d`, genau 14 Dateien | TARGETED_ANDROID_PASS; eigener gemeinsamer Lauf 54/54 (25 Sequence + 29 Temporal/Recherche), keine Fehler/Skips; separater Host25 überlappt. Quelle3891756/6c34db7 |
| C1-UI-FIXTURE-015 | C1; statischer Review repair_review | Vier-Dateien-WIP-Snapshot SHA256 `4897b424eec14a6c92ce87dfbc1d0b86cba868d362e3929558fd0cb0cdb1f575` | OWN_ANDROID_FAIL; letzter Lauf20Fälle/12PASS/8FAIL/0SKIP aufcdea744/a8ebdd9. Frühere Fenster-/Signaturfehler behoben; ReferenceCockpit-Hierarchie wird unabhängig diagnostiziert, volle Anforderungen bleiben Pflicht |
| C1-MEMORY-017 | Fachlieferant #522; C1 alleiniger Einbau | `eadc72bac95f8b1a554a59e5baff624977b09f73`; externe Testquelle 3704b625 | TARGETED_ANDROID_PASS: 017A 23/23 auf 57656c6/f13578b; 017B 23/23 auf d3487a8/4b6c389. Beide Quellen und Receipts unabhängig geprüft; kein gemeinsamer Vollgate-PASS |
| C1-LAYOUT-018 | Fachlieferant #524; C1 alleiniger Einbau | `235d764f790dee7685c6590cf0a5d07d1fafdce2`; Fix 652e6e30; C1 59d8d7e/a6135f2 | TARGETED_ANDROID_PASS: vier ursprüngliche plus drei neue Fälle, 7 PASS / 0 FAIL / 0 SKIP, Exit 0; unveränderte 48-dp-Grenze und zehn exakte HERO-Bounds geprüft |
| C1-UI-020 | C1; apk_readiness Produkt, repair_review Fixtures, source_audit Gegenprüfung | Eigener RED f318551/c65604c; Testfreeze V1 fdd1d7bc | OWN_RED: 8 Fälle = 1 PASS / 7 FAIL / 0 SKIP, Exit 1. Kompaktwahl fehlt; aktive STATUS-Ziele39dp. V2-Fixture verworfen: manueller Neustart verliert relevante Konfigurationswechsel-Zweige. V3-Diagnose läuft; Nulltag/Kurzfenster separat. V1 unverändert gesichert, Produkt nicht angewendet |
| C1-OWNERSHIP-019 | C1; repair_review read-only | #523 7c1642af; #527 d2aedf2c; #528 bab429e9 | SOURCE_REVIEW_COMPLETE: 60 genaue Pfadzeilen; doppelte Memory-/Layoutbeiträge einmalig führen. #527 UI-Fixtures sind gegenüber C1 veraltet; kein Gesamtbaumimport |
| C1-OPT-021 | C1; source_audit bereitet vor | #523 `7c1642af96ccf626c91a40a602c7127e02fb9eea` | WORKER_SOURCE_INTEGRATED: genau drei Backlog-Auswahl-/Test-/Paritätspfade, Patch65abb186; eigener121-Fokus plus40.960Identitätsentscheidungen ausstehend. Compiler und LM noch Intake |
| C1-MISSING-022 | C1; repair_review Quelle/Intake | Abgeschlossener Zweidateien-Hook aus #515 `5373ec7c1399cb34fb8896f7318fdfa5437fa283` | TARGETED_INTEGRATED_HOST_PASS: auf 9a442485/0d8a3d46 eigener26/26-Kompatibilitätsfokus, Exit0, keine Skips. Zuvor vier echte Git-Canaryfälle0/4 →4/4; überlappen. Vier vorhandene Caller bleiben ungebunden; Nachtstand/#516 separat |
| C1-AVATAR-SLOT-023 | Fachquelle #527; C1 einziger Einbau; apk_readiness Aufnahme | #527 `5410060fcd1558c899fc160c08232f9ed948b8f7`; Ursprungsfix a3f46349 | INTAKE: isolierte Home-/Adapter-Andockkorrektur und drei Lifecycle-Fälle, keine ganze Activity/Fixture übernehmen; eigener Lauf ausstehend |
| MEMORY-PRIVACY-R4 | bestehendes Fachteam #529; späterer Einbau C1 | Neuer gelesener Head `1070b10df50025828a9fd2b18bf1c187c094979b` | WIP: reserviert AiContextCompiler.redactSecrets und MemoryWritePolicy.decide. Fremde Host-RED/35-/53-PASS, zusätzlicher x-token-Holdout und gemeinsame Android-Kette noch nicht fertig abgenommen; kein Import |
| C1-C2-REVIEW-001 | externer C2 | Exakter gemeinsamer Endstand und Rohbelege | PENDING_HANDOFF / ACK_NOT_OBSERVED; C1-Unteragenten ersetzen C2 nicht |
| C3/C4 Gehirn | bestehende Fachzweige; Einbau C1 | Compiler, Memory-Recall/Bus; bestehende Reservierungen | Gedächtnis-ACK und Finalhandoff #522 tatsächlich gelesen; Compiler-Contradiction/Mapper reserviert, M2.9-Hauptchat/Ranking bleibt offen |
| C5/C6 Autonomie | bestehende Fachzweige; Einbau C1 | kanonischer Backlog/CCC1/Worker, #493/#496/#515 | Neue ACKs nicht beobachtet; kein zweiter Worker |
| C7/C8 Modellforschung | bestehende Forschungszweige | #492/#497/#514 | EXPERIMENTAL; keine Promotion/Trainingsbehauptung |
| C9/C10 Avatar | bestehende Fachzweige; Einbau C1 | #491/#495/#502/#503/#518 | Neue ACKs nicht beobachtet; GLB-Rig, Referenz, echte GPU/Sprache offen |

**Gemeinsame Einstiegspunkte:** Nur C1 baut AgentActivity, Compiler-/Factory-Verbindungen, Sprachlistener und Worker-Einstieg im gemeinsamen Kandidaten ein. Andere Teams liefern begrenzte Patches mit Ausgangs-SHA. Ihre Worktrees und Dateireservierungen werden nicht verändert.

## Enthalten, fehlend und widersprüchlich

Enthalten: #491-Grundintegration, #506 Bot-/Recall-/Scope-Anschlüsse, #512 Cockpit-Lifecycle/Kalender/QA-Vorprüfung, #513 Sprachcaller/Reattach und #502/#503-Motion. Hinzu kamen genau der negative #515-ECHO-Slice und die getrennte QA-Manifestkorrektur. Der abgeschlossene optionale Scheduler-Quellenhook aus #515 wurde später als Missing022 gezielt übernommen; neue Branch-Discovery-/Nachtarbeit bleibt separat.

Offen: aktive UI020-Anschlüsse für Kompaktansicht und Mehr im Wide-Reflow, vollständige gemeinsame Abnahme sowie tatsächliche Modell-/Gerätekette. Routing006, Avatar009, Temporal011, Sequence013, Memory017A/B und Layout018 besitzen getrennte eigene gezielte Belege. Der historische #517-Timeout ist durch begrenzte VSync-Fixtures ersetzt worden; die aktuelle UI015-Prüfung endet regulär mit acht tatsächlichen ReferenceCockpit-Fehlern. Diese bleiben bis zum neuen begrenzten RED/GREEN rot.

Frischer Quellabgleich gegen 20:44 UTC: #515 `5373ec7c1399cb34fb8896f7318fdfa5437fa283` enthält genau dieselben vier bereits integrierten ECHO-Blobs; übrige neue Scheduler-/Branch-Arbeit bleibt gesondert. #492 `0af14f75b04e68ba849a92316b8be51041e8738b` meldet reparierte Forschungsgates, aber verfehlte Modellqualität/-kapazität. #514 bleibt mit Protokollkonflikt experimentell, #518 WIP. Vollständige aktuelle Headliste liegt in den Belegen.

Widersprüchlich/überholt: #506s initiales UNVERIFIED-Handoff, alte starre UI-/Aufrufer-Strings in Tests, historische AGENTS-Releaseangaben. Der Avatar-Vertrag belegt die angeschlossene Live-Hologrammfläche; dauerhaftes Fan-out an sämtliche Registry-Empfänger ist dadurch nicht bewiesen.

## Ressourcen, APK und Grenzen

Schwere C1-Läufe halten beide bestehenden Locks: `/workspace/scratch/b6ee64d418cf/toolchain/gradle-build.lock` und `/workspace/scratch/d663e8444310/qa/gradle.lock`. Standard45Sekunden warten, dann BLOCKED; v3 erlaubt einen ausdrücklich protokollierten einzelnen Warteplatz bis900Sekunden mit5-Sekunden-Heartbeat. Erst nach beiden Locks starten; keine automatische Wiederholung fachlich fehlgeschlagener Tests. Keine fremden Prozesse stoppen oder Locks löschen. Cache wiederverwenden; keine parallelen schweren C1-Builds.

Datenträgerplatz schwankt stark. Ein Evidenzverzeichnis scheiterte einmal mit ENOSPC, bevor Dateien kopiert wurden; eine frische Prüfung zeigte anschließend rund 2,0 GB frei und das Anlegen gelang. Ursache unbekannt; kein fremder Bestand gelöscht. Quellen und negative Belege bleiben erhalten.

Originalpaket `at.heinz.deviceai.continuumlocal`; QA-Paket `.qa`, nur Compile/Test/Lint; keine Besitzer-APK. Standalone-Version ist expliziter Buildparameter, Versionsname `0.3.(Code−1)`. Originalalias `continuum-local`/PKCS12 bleibt maßgeblich. Live-Kanalversuche vom 04.11.2026 20:14:10 und21:15UTC scheiterten an der Toolzugänglichkeit; kein HTTP-Ausfall des Servers behauptet. #475 belegt nur historisch 0.3.40/Code41.

Kein Main-Merge, Release, Installation, Signaturwechsel, kostenpflichtiger Dienst oder Actions-Lauf. Veröffentlichung/Installation bleiben gesondert freizugeben. APK, echte lokale Modellinferenz und physischer Gerätetest sind NOT_RUN. S01-, GLB-/GPU-/Sprach- und M2.9-Gates werden nicht gesenkt.

Belege: [Manifest und Roharchive](../../evidence/c1-integration-20261104/), [fortsetzbarer Handoff](C1-INTEGRATION-HANDOFF-20261104.md), [unabhängige Reviews](../../evidence/c1-integration-20261104/independent-reviews.json).

---

## Historisches Board aus #498, inhaltlich erhalten; Quellenlinks fixiert

# ASTRA-NUCLEUS — zentrales Management-Board

Stand: 04.11.2026, Europe/Vienna. Generation 1, erster Quellen- und Anschlussreview.
Repository: Ullmann27/device-ai. Koordinations-PR: https://github.com/Ullmann27/device-ai/pull/498
Eigener Zweig: `coordination/astra-nucleus-20261104-1027`.

## Erfüllter Teil und Arbeitsgrenzen

Drei reale Manager-Sitzungen und alle zehn real gestarteten Spezialisten haben ihre ersten Quellen-/Anschlussprüfungen abgeschlossen. Die zehn Spezialaufträge sind in Optimierung (4), Implementierung (3) und Reparatur (3) aufgeteilt. Das sind 13 abgeschlossene Agentensitzungen, in Wellen ausgeführt; am Abschlusscheckpoint laufen keine Unteragenten. Die Produkt-Folgeaufträge stehen bereit, Generation 2 ist noch nicht gestartet. Der aktuelle tatsächliche Dispatch steht unten und maschinenlesbar in [GENERATION-1.json](https://github.com/Ullmann27/device-ai/blob/e95076c2a396a785ae80bceca0610890ff1af5d9/docs/research/omega/astra-nucleus/GENERATION-1.json).

Dieser Stand implementiert Koordination, Quellenprüfung und sieben eigenständig kopierbare [Chat-Aufträge](https://github.com/Ullmann27/device-ai/blob/e95076c2a396a785ae80bceca0610890ff1af5d9/docs/research/omega/ASTRA-NUCLEUS-ACTION-PACKAGE-20261104.md). Er behauptet keine neue Android-Funktion, kein Modelltraining und keinen APK-/Geräteerfolg. Entwicklungsassistenten dieses Chats sind keine in der App installierten Bots.

Technische Kapazität hier: drei gleichzeitig laufende Unteragenten plus der Koordinator. Zehn Rollen sind keine zehn gleichzeitig laufenden Modellprozesse. Externe Chat-Sitzungen sind nicht fernsteuerbar oder als Prozesse einsehbar; ihre Empfangsbestätigung ist noch nicht belegt.

## Ursprüngliche Prüfrevisionen

Eine Momentaufnahme aus GitHub; vor Bearbeitung/Übernahme neuesten Head und eigene Übergabe erneut lesen. Ein Commit oder ein PR belegt keinen laufenden Bot. Für #493/#495/#496/#497 wurden zusätzlich unveränderliche Vergleiche gegen deren jeweilige Basis gelesen.

| Zuständigkeit | PR | geprüfter Head | Zweig |
| --- | --- | --- | --- |
| Vergleichsbasis | [#483](https://github.com/Ullmann27/device-ai/pull/483) | `578bdfd12f896cc990bf1a043bc1b248ba3adb6d` | `integration/jarvis-on-chatgpt-untested-20261104` |
| HAUPT-3 Avatar/UI/Integration | [#491](https://github.com/Ullmann27/device-ai/pull/491) | `d352bab6855db2a44def0bbf60fd93202b4e3066` | `codex/astra-jarvis-takeover-20261104` |
| HAUPT-1 Gewichte | [#492](https://github.com/Ullmann27/device-ai/pull/492) | `ee5779725690c573d3310c061c6e2e6faf5f0a8b` | `research/continuum-weight-space-audit-20261104` |
| HILFE-1 Persönlichkeits-Bots | [#493](https://github.com/Ullmann27/device-ai/pull/493) | `e4631a034ae25381acd3043cbb95a753b92614f8` | `feat/continuum-personality-bots-20261104` |
| HAUPT-2 Gehirn/Gedächtnis | [#494](https://github.com/Ullmann27/device-ai/pull/494) | `ca31eb8b24b65d084750f0ea10290dc0d6be6733` | `feat/continuum-memory-brain-20261104` |
| HILFE-3 Avatar | [#495](https://github.com/Ullmann27/device-ai/pull/495) | `ca16da8968e35a490e1f6b54da8a0af03bec22d2` | `codex/continuum-three-avatar-helpers-20261104` |
| HILFE-4 Worker/QA/Integration | [#496](https://github.com/Ullmann27/device-ai/pull/496) | `07dca053eef5b20381469a55d107862c65767658` | `support/continuum-ten-bot-assist-20261104` |
| HILFE-2 Gewichte | [#497](https://github.com/Ullmann27/device-ai/pull/497) | `f9ea9ba461360fb6b4f5369416eda605a8354766` | `research/continuum-weight-helper-team-20261104` |

#492 baut auf #478 auf; die übrigen Hauptstränge auf #483. #495 ist auf #491, #497 auf #492 gestapelt. Eine bestandene Einzelprüfung gilt deshalb nicht automatisch für den kombinierten Kandidaten.

## Zentrales Management

| Manager | reale Sitzung | Erstauftrag |
| --- | --- | --- |
| M1 Integration | `/root/m1_integration_manager` | abgeschlossen; ausschließlich Quellenprüfung |
| M2 Cognition | `/root/m2_cognition_manager` | abgeschlossen; ausschließlich Quellenprüfung |
| M3 Experience | `/root/m3_experience_manager` | abgeschlossen; ausschließlich Quellenprüfung |

M1 führt Abhängigkeiten und Eigentümer. M2 führt Modell-, Trainings- und Gedächtnisbelege. M3 führt Avatar-, UI- und Abnahmekriterien. Der Koordinator konsolidiert das Board seriell.

## Generation 1 — tatsächlicher Dispatch

| Auftrag | Gruppe | Aufgabe | dokumentierter Stand |
| --- | --- | --- | --- |
| G1-O1 | OPTIMIERUNG | Branch- und Eigentumsabgleich | SOURCE_REVIEW_COMPLETE |
| G1-O2 | OPTIMIERUNG | Parameter und Modellkapazität | SOURCE_REVIEW_COMPLETE |
| G1-O3 | OPTIMIERUNG | Training und Ressourcengrenzen | SOURCE_REVIEW_COMPLETE |
| G1-O4 | OPTIMIERUNG | Gedächtnis und Belegqualität | SOURCE_REVIEW_COMPLETE |
| G1-I1 | IMPLEMENTIERUNG | Gehirn–Avatar-Anschluss | SOURCE_REVIEW_COMPLETE |
| G1-I2 | IMPLEMENTIERUNG | Persönlichkeits-Bots anschließen | SOURCE_REVIEW_COMPLETE |
| G1-I3 | IMPLEMENTIERUNG | Gemeinsames Integrationspaket | SOURCE_REVIEW_COMPLETE |
| G1-R1 | REPARATUR | Avatar, Mimik und Lebenszyklus | SOURCE_REVIEW_COMPLETE |
| G1-R2 | REPARATUR | Worker-Abbruch und Wiedereinstieg | SOURCE_REVIEW_COMPLETE |
| G1-R3 | REPARATUR | Unabhängige Abnahme und Update | SOURCE_REVIEW_COMPLETE |

SOURCE_REVIEW_COMPLETE heißt: der erste Quellenreview wurde abgeschlossen. Es bedeutet weder behobener Produktfehler noch bestandene Runtime-/Geräteprüfung. Exakte Agentensitzungen, Grenzen und Ergebnisse stehen in GENERATION-1.json.

## Befunde des ersten Quellenreviews — teilweise inzwischen überholt

1. Modellkapazität: Der Koordinator las `learning/lm/LmModel.kt` am Commit `ee5779725690c573d3310c061c6e2e6faf5f0a8b` und zählte sämtliche Formen aus `LmConfig.layout()` für minGRU unabhängig nach. Die Architekturgrößen 132.160, 460.928, 1.708.288, 6.562.304 und 9.712.128 stimmen; weitere protokollierte Stufen sind mitgezählt. Gebundene Ein-/Ausgabegewichte zählen einmal. [Tensorliste und Rechnung](https://github.com/Ullmann27/device-ai/blob/e95076c2a396a785ae80bceca0610890ff1af5d9/docs/evidence/astra-nucleus-20261104/parameter-layout-counts.json). Keine tatsächlichen Modellgewichte, Trainingsupdates oder Qualitätsgewinne wurden dadurch nachgewiesen.
2. Wiederaufnahme von Experimenten: G1-O3 und der Koordinator prüften `experimental/weight-space-20261104/run_experiment.py` bei `ee577972`. Der Wiederaufnahmepfad vergleicht gespeicherten Orchestrator-Hash sowie Befehls-/Heap-/Timeout-Identität nicht vollständig. Das ist ein statisch belegtes Reproduzierbarkeitsrisiko, kein ausgeführter Fehlernachweis oder bereits erfolgter Fix. HILFE-2 reproduziert mit synthetischen Receipts und übergibt #492 einen geprüften Fix.
3. Gehirn/Avatar: #494 `ca31eb8b` enthält Pläne für `cockpit/ContinuumMemoryEventSource.kt`, noch keine neue Produktionsimplementierung. #494 liefert den Adapter; #491 besitzt Activity, Listener- und Lebenszyklusanbindung.
4. Avatar: Die baseline-probes bei #495 `ca16da89` dokumentieren Visem-/JawOpen-Überlagerung, veraltete Audioproben, eine nicht isolierte Sink-Exception und Zustellung an einen abgelösten Renderer. Autorenbelege sind geprüft gelesen, nicht von diesem Koordinator als Gerätetest ausgeführt. Vor Reparatur bereits laufende Bearbeitung abgleichen.
5. Bots und Routinen: #493 `e4631a03` enthält bereits ChiefBotPersonas, ChiefBotTaskIntake und Änderungen an ChiefBotConversation, aber keine Activity-Änderung im geprüften Diff. #496 `07dca053` enthält einen RoutineBook-Scope-/Zeitüberlauf-Fix samt beschriebenem RED→GREEN-Lauf mit 12 Tests. Diese Nachweise ersetzen keine unabhängige Android-Verbraucher-/Gesamtprüfung.
6. Grafikabnahme: Die bei #491 dokumentierte Java2D-Vorschau ist keine Android-Activity-/Fold7-Aufnahme. Referenztreue, native GPU-, Sprach- und Geräteprüfung bleiben gesonderte Nachweise.

## Anti-Drift-Abgleich — 04.11.2026, 11:51 Europe/Vienna

Die folgenden späteren Heads wurden erneut direkt gelesen. Die ersten Spezialistenberichte behalten ihre ursprünglichen Prüfrevisionen; sie werden nicht nachträglich als Tests der neuen Heads ausgegeben.

| Strom | zuletzt gelesener Head | Fortsetzung |
| --- | --- | --- |
| HAUPT-3 Avatar/UI [#491](https://github.com/Ullmann27/device-ai/pull/491) | `d352bab6855db2a44def0bbf60fd93202b4e3066` | neuesten Head vor tatsächlicher Übernahme erneut fixieren |
| HAUPT-1 Gewichte [#492](https://github.com/Ullmann27/device-ai/pull/492) | `88d7b05072bdec6250990efaa9914a1e6744c29b` | neuesten Head vor tatsächlicher Übernahme erneut fixieren |
| HILFE-1 Bots [#493](https://github.com/Ullmann27/device-ai/pull/493) | `e4631a034ae25381acd3043cbb95a753b92614f8` | neuesten Head vor tatsächlicher Übernahme erneut fixieren |
| HAUPT-2 Gehirn [#494](https://github.com/Ullmann27/device-ai/pull/494) | `8a98ea4d10126041b1a43cb7623c0f324ab18ec1` | neuesten Head vor tatsächlicher Übernahme erneut fixieren |
| HILFE-3 Avatar [#495](https://github.com/Ullmann27/device-ai/pull/495) | `7859d5d212d61829fabc7dfdbd3ca7587852388d` | neuesten Head vor tatsächlicher Übernahme erneut fixieren |
| HILFE-4 Integration/QA [#496](https://github.com/Ullmann27/device-ai/pull/496) | `76184a860cf78c054d6cd94ea83882148cee191d` | neuesten Head vor tatsächlicher Übernahme erneut fixieren |
| HILFE-2 Gewichte [#497](https://github.com/Ullmann27/device-ai/pull/497) | `06f071e5850dd405f374bbb58d826f64740d83af` | neuesten Head vor tatsächlicher Übernahme erneut fixieren |

**Dieser Abgleich ersetzt die ursprünglichen offenen Aufträge, soweit inzwischen Code geliefert wurde.**

- **Gewichte #492:** `88d7b050` publiziert Kapazitäts-/Paritäts- und Trainingsbelege. Laut gelesenem `final.json` ist die unverändert erweiterte 16.011.776-Parameter-Version kapazitätsgeeignet; beide fortgesetzten Varianten sind nicht geeignet und scheitern an der verlangten vollständigen Tokengenerierung. Beim großen fortgesetzten Modell verschlechtern sich Development und Holdout. Diese eingesehenen Autorenbelege wurden hier nicht als eigener Modelllauf reproduziert. Die richtige nächste Aufgabe ist Qualitäts-/EOS- und Resume-Abnahme, kein blindes Wiederholen der fertigen Größenleiter. Die ursprüngliche unabhängige Auditdatei bleibt `pass:false` wegen einer dokumentierten 840-KiB-Speicher-Summaryabweichung; konservative Neuberechnung erhält die Kapazitätsentscheidung. Spätere Quellabsicherung und Baseline-Smoke wurden separat dokumentiert, ohne den ursprünglichen Versuch neu auszuführen.
- **Gewichtehilfe #497:** `06f071e5` repariert das Pflichtgate vor `continuation()`, inklusive Wiederaufnahme mit vorhandener Auswahl. Gelesener Autorenbericht: zwölf synthetische RED→GREEN-Fälle, kein Training. G1-O3 hat erneut gelesen: die getrennte Orchestrator-/Timeout-/Befehlsidentität bleibt sowohl hier als auch bei #492 `88d7b050` offen. Vorhandenen Gate-Fix erhalten, Rest gezielt schließen.
- **Gehirn #494:** `8a98ea4d` enthält jetzt Kontext-/Snapshot-/ID-Reparaturen, `ContinuumMemoryRecallPublisher` und `ContinuumMemoryEventSource`. Der Koordinator las das neue Handoff und den kombinierten Ergebnisbeleg: 98 Komponentenprüfungen gemeldet, darunter unverändert 25/25 Holdout-Fälle gegenüber 7/25 Baseline. Die Einzelprüfungen überlappen. Die frühen G1-O4/O1/I1-Aufträge werden deshalb zu Review-/Integrationsaufträgen; ihre Vorzustandsfehler nicht erneut implementieren.
- **Tatsächliche Aktivierung:** Am direkt gelesenen veröffentlichten #491-SHA `d352bab6` hat `cockpit/MemoryEventSource.kt` weiterhin `production = null` und keine `forApp`-Factory. Eine neuere Factory wird in #494-Handoffs mit lokalen Zwischenständen beschrieben; ihre Existenz auf diesem veröffentlichten #491-Head ist nicht belegt. #491 muss den gelieferten Adapter und den Compiler-Observer auf einem gemeinsamen gepushten Stand anschließen und prüfen. Korrekte Reihenfolge beim Schließen: Mapper detach, danach Subscription close.
- **Avatar #495:** `7859d5d2` enthält neue Änderungen an `AvatarContract.kt`, `AvatarMorphMapper.kt`, `CockpitContracts.kt` und dazugehörige Regressionen. Die frühere Baseline-Priorität ist bereits in Bearbeitung. Zuerst neue Fixes und exakte aktuelle Testbelege unabhängig abnehmen; kein zweites Reparaturpaket für dieselben Dateien.
- **Allgemeine Hilfe #496:** `76184a86` ergänzt Quellen-/Prüfbelege. Dokumentiert sind 31 gewählte Routine/Dispatcher/Pool-Prüfungen einschließlich der früheren zwölf. Dienstprüfungen sind durch fehlende Ausführungsgrundlagen blockiert; der tatsächliche unveränderte GLB verletzt laut Prüfbeleg den Augen-/Kiefer-Pivotvertrag. Diese offenen Befunde getrennt an Eigentümer geben. G1-R2s alter/neuer Routinen-Callback ist ein bedingter Prüfauftrag, bis die reale Dispatcher-Abwehr untersucht ist.

## Gemeinsames Gedächtnis und Zuständigkeiten

Dieses Board ist die zentrale Koordinationsübersicht, keine zusätzliche Datenbank oder App-Runtime. ROOT schreibt es seriell auf seinem eigenen Zweig. Die sieben Arbeitschats behalten ihre eigenen Handoffs auf ihren eigenen Zweigen und verlinken überprüfte Ergebnisse zurück.

Jede Übergabe enthält:
`task_id`, Generation, Phase, zuständigen Chat, eigene Branch, Basis-SHA, Ergebnis-SHA, reservierte Dateipfade, benötigte Eingänge/Abhängigkeiten, ausgeführte Befehle und Exitcodes, Log-/Artefaktpfade, Belegart, offene Blocker, Rückfall, nächsten Schritt und Empfangsstatus.

- #491: AgentActivity, gemeinsamer Avatar/Orb, UI, Sprache/Lifecycle und bestehende Recovery-Koordination.
- #494: HybridMemoryRetriever, AiContextCompiler, MemoryEcology, Ereignisadapter und zugehörige Tests.
- #492: Modellgewichte, Tensorerweiterung, Training, Modellformat und Kandidatenentscheidungen.
- #493: Botprofile, Persönlichkeit, Adressierung, Auftragsannahme und fokussierte Tests.
- #495/#496/#497: isolierte Zuarbeit; reservierte fremde Bereiche bleiben bei ihrem Eigentümer.

Statusfolge: QUEUED → CLAIMED → RUNNING → REVIEW_READY → VERIFIED; bei Quellenaufträgen gesondert SOURCE_REVIEW_COMPLETE. BLOCKED/FAILED nennen Ursache und fehlenden Eingang. Ein nicht erreichbarer Bearbeiter wird STALE; das gibt seine Dateien nicht automatisch frei. Vor Wiederaufnahme Head und Zuständigkeit abgleichen. Dokumentierte Zuweisung ist keine Empfangsbestätigung eines anderen Chats.

## Rotation und Ressourcen

Eine Generation kann bis zu zehn sinnvoll abgegrenzte Prüf-/Einbau-/Reparaturschritte pro Arbeitsstrom bearbeiten. Jeder Schritt endet mit prüfbarem Ergebnis und Rückgabe des Slots. Anschließend übernimmt ein tatsächlich gestarteter Nachfolger den gesicherten Stand. Überlappende Schreibaufträge werden serialisiert; unabhängige Prüfungen laufen parallel. Priorität: belegter Fehler → geprüfter Anschluss → Optimierung.

Bei Blockade eine andere freigegebene unabhängige Aufgabe bearbeiten. Wiederholungen ohne neue Eingaben oder eine offene Testhypothese sind kein Fortschritt. Bei fehlender Ausführungskapazität Commit/Push/Handoff sichern. Persönliche Rest-Credits, Tokenbudgets oder Chat-Prozesszustände sind nicht verifiziert messbar; keine erfundenen Laufzeit-/Budgetanzeigen.

## Wiederkehrende Fortsetzung

Eine neue Aufgabe „Continuum Anschlussaufträge“ wurde tatsächlich aktiviert: stündliche sourcebasierte GitHub-Prüfung mit Folgeaufträgen bei wesentlichen neuen Befunden. Zeitzone Europe/Vienna. Die Aufgabe liest dieses Board und die vorhandenen Stränge. Sie versendet keine Nachrichten in andere Chats, startet keinen permanenten Sieben-Chat-Dienst und ändert keine fremden Produktionszweige.

## Abnahmevertrag für einen späteren APK-Kandidaten

G1-R3 hat die Koordinationsunterlagen unabhängig geprüft und keine materielle Statusinkonsistenz gefunden. Die Prüfung gilt für dieses Dokumentenpaket, nicht als App-Abnahme. Neue Heads erben weder PASS noch einen Fehlerbefund der alten SHA.

| Gegenstand | Erforderlicher Nachweis |
| --- | --- |
| Quellstand | unveränderliche Gesamt-SHA und Komponentenmanifest des tatsächlich gebauten Kandidaten |
| Softwareprüfungen | zugehörige Befehle, Werkzeugversionen, Exitcodes und vollständige Logs für erforderliche Tests, Android-Kompilierung und konfigurierte Lint-Prüfung |
| APK | tatsächliche Datei, SHA-256 und ausgelesenes Paket `at.heinz.deviceai.continuumlocal` |
| Version und Signatur | ausgelesene Versionen und nachgewiesene Signaturkontinuität zur Originalinstallation |
| Updatepfad | bestehender Render-Kanal, identischer Download-Hash und autorisierter Aktualisierungstest mit erhaltenen App-Daten |
| Physisches Gerät | tatsächlicher Fold7-Lauf mit dokumentierter Umgebung, lokaler Inferenz, Gedächtnis, Avatar, Audio, Worker-Abbruch/Wiederaufnahme und Lebenszyklus |

Diese Nachweise stehen für den gemeinsamen neuen Kandidaten aus. Dieses Koordinationspaket gibt keine Freigabe zur Veröffentlichung, Signierung oder Installation.

## Verifikation und Rücknahme

Eigene Prüfungen: GitHub-Metadaten, Handoffs und ausgewählte Produktions-/Experimentdateien gelesen; unveränderliche Commit-Vergleiche; unabhängige Parameter-Arithmetik. Kein Android-Build, kein Training, kein Modellbenchmark und kein Fold7-Lauf in diesem Koordinationsauftrag. Aussagen aus Autorenlogs bleiben als solche gekennzeichnet.

Kein Main-Merge/Force-Push, keine neuen App-Befugnisse, kein zweiter Scheduler oder Speicher. Keine Actions, kostenpflichtigen Dienste, Modell-Promotion, Signierung, Veröffentlichung oder Installation. Originalpaket `at.heinz.deviceai.continuumlocal`, Originalsignatur, bestehender Render-Updatekanal und ToolGate/CCC1 bleiben erhalten.

Rücknahme betrifft ausschließlich die neuen Dokumentations-/Evidenzdateien dieses Koordinationszweigs. Keine fremde Branch zurücksetzen oder löschen.
