# C1 — aktuelle Integrationsleitung, 4. November 2026

Dieser Abschnitt führt den direkten Besitzerauftrag auf demselben zentralen Board-Pfad aus #498 fort. Der erhaltene historische Teil ist keine aktuelle Prozess- oder ACK-Liste.

## Gemeinsamer Kandidat und Quellen

- Einziger C1-Integrationszweig: `integration/continuum-c1-candidate-20261104`, Draft **#519**; kein Main-Merge.
- Gewählte Basis #513: `28b228698aef8c52e46c72b71fdd5e1915d6e76c`; vollständiger Baum `e52acc146e66ae66a95429da2fb0f34242ad1791`. Lokales Äquivalent `2d5ce59b786f427eacb394484d81796568bb87b4` besitzt exakt diesen Baum.
- #491 → #506 → #512 → #513 ist unabhängig als tatsächliche Commit-Abstammung geprüft. #506 `8abea828833d8833d40e3d3d36feaa6d3685b82e` bleibt Vergleichsanker; neuere Arbeit bleibt erhalten.
- Aktueller eigener Android-Fokusprüfstand: lokal `165fc9f58ae9e36295690df60145174fd5dc58e7`, remote `401a5d01a7b2e0e0f8c50531daf5fa24814fbbda`, identischer vollständiger Tree `4b3e47f30517cf952ab50c0a52b27bb96d281504`. Echte App-/UI-Testkompilierung, 29 gezielte Router-/Activity-Tests und konfigurierter NewApi-Lint bestanden; keine vollständige Android-Abnahme.
- Letzter vollständiger eigener Node-Prüfstand: lokal `8873030fdfe23bdd4153f96004b786bff2cddecf`, remote `1a5302c504a78a50d5e64ca727f2915529bf2b06`, gleicher Tree `d1a1b733ad37f21c155527fa0c9532f56fb1b934`.
- **Gesamtstatus FAIL / INTEGRATION IN ARBEIT:** critical 135 = 131 PASS / 4 FAIL / 0 SKIP; full-node 2349 = 2280 PASS / 32 FAIL / 37 SKIP. Quellen während beider Läufe unverändert. Diese Umfänge überlappen und werden nicht addiert. Keine APK-/Gerätefreigabe.
- Anschließend sieben vorhandene #517-Quellvertragskorrekturen begrenzt übernommen: 48 gezielte Tests PASS, neun absichtliche Vertragsverletzungen jeweils erkannt, unabhängiger statischer Review positiv. Neuer Gesamtlauf steht nach weiterer serieller Integration aus.

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
| C1-INTAKE-011 | C1 / source_audit read-only | #505 `b2bc510a59e29d4ece478109e12b9785be11ee7d`; drei Tagesgrenzen-Dateien | INTEGRATED_HOST_RUN_PENDING; genau3Dateien, feste Kalendergrenzen und12neueTests. Separate14Abschlusszustellungs-Dateien zurückgestellt |
| C1-GATES-012 | C1 / apk_readiness read-only | Geltende Projektverträge und Owner-QA-Skript bei `165fc9f58ae9e36295690df60145174fd5dc58e7` | SCOPE_REVIEW_COMPLETE; volle Unit/UI-Suite und 11 Pflichtsuites plus 5 Mindestzahlen bleiben Pflicht; zusätzliche Tools-FAIL bleiben separat rot |
| C1-C2-REVIEW-001 | externer C2 | Exakter gemeinsamer Endstand und Rohbelege | PENDING_HANDOFF / ACK_NOT_OBSERVED; C1-Unteragenten ersetzen C2 nicht |
| C3/C4 Gehirn | bestehende Fachzweige; Einbau C1 | Compiler, Memory-Recall/Bus; bestehende Reservierungen | Neue ACKs nicht beobachtet; M2.9-Hauptchat/Ranking bleibt offen |
| C5/C6 Autonomie | bestehende Fachzweige; Einbau C1 | kanonischer Backlog/CCC1/Worker, #493/#496/#515 | Neue ACKs nicht beobachtet; kein zweiter Worker |
| C7/C8 Modellforschung | bestehende Forschungszweige | #492/#497/#514 | EXPERIMENTAL; keine Promotion/Trainingsbehauptung |
| C9/C10 Avatar | bestehende Fachzweige; Einbau C1 | #491/#495/#502/#503/#518 | Neue ACKs nicht beobachtet; GLB-Rig, Referenz, echte GPU/Sprache offen |

**Gemeinsame Einstiegspunkte:** Nur C1 baut AgentActivity, Compiler-/Factory-Verbindungen, Sprachlistener und Worker-Einstieg im gemeinsamen Kandidaten ein. Andere Teams liefern begrenzte Patches mit Ausgangs-SHA. Ihre Worktrees und Dateireservierungen werden nicht verändert.

## Enthalten, fehlend und widersprüchlich

Enthalten: #491-Grundintegration, #506 Bot-/Recall-/Scope-Anschlüsse, #512 Cockpit-Lifecycle/Kalender/QA-Vorprüfung, #513 Sprachcaller/Reattach und #502/#503-Motion. Hinzu kamen genau der negative #515-ECHO-Slice und die getrennte QA-Manifestkorrektur. Branch-Discovery/Scheduler aus #515 bleiben separat.

Offen: gemeinsame vollständige Abnahme der inzwischen gezielt reparierten Quellverträge, die nächsten begrenzten Avatar-/Tagesgrenzen-Slices und tatsächliche Modell-/Gerätekette. Der unbekannte-Bot-Select-Rückweg besteht inzwischen den eigenen Android-Fokus; der Signed-zero-Randfall den eigenen Kotlin RED/GREEN. Neueste #493-Routerbytes enthalten den Normaltextfehler weiterhin. #517s jetzt grüner Avatar-Fokus hebt seinen getrennten Vollgate-Timeout in ReferenceCockpit nicht auf.

Frischer Quellabgleich gegen 20:44 UTC: #515 `5373ec7c1399cb34fb8896f7318fdfa5437fa283` enthält genau dieselben vier bereits integrierten ECHO-Blobs; übrige neue Scheduler-/Branch-Arbeit bleibt gesondert. #492 `0af14f75b04e68ba849a92316b8be51041e8738b` meldet reparierte Forschungsgates, aber verfehlte Modellqualität/-kapazität. #514 bleibt mit Protokollkonflikt experimentell, #518 WIP. Vollständige aktuelle Headliste liegt in den Belegen.

Widersprüchlich/überholt: #506s initiales UNVERIFIED-Handoff, alte starre UI-/Aufrufer-Strings in Tests, historische AGENTS-Releaseangaben. Der Avatar-Vertrag belegt die angeschlossene Live-Hologrammfläche; dauerhaftes Fan-out an sämtliche Registry-Empfänger ist dadurch nicht bewiesen.

## Ressourcen, APK und Grenzen

Schwere C1-Läufe halten beide bestehenden Locks: `/workspace/scratch/b6ee64d418cf/toolchain/gradle-build.lock` und `/workspace/scratch/d663e8444310/qa/gradle.lock`. Maximal 45 Sekunden warten, dann BLOCKED. Keine fremden Prozesse stoppen oder Locks löschen. Cache wiederverwenden; keine parallelen schweren C1-Builds.

Datenträgerplatz schwankt stark. Ein Evidenzverzeichnis scheiterte einmal mit ENOSPC, bevor Dateien kopiert wurden; eine frische Prüfung zeigte anschließend rund 2,0 GB frei und das Anlegen gelang. Ursache unbekannt; kein fremder Bestand gelöscht. Quellen und negative Belege bleiben erhalten.

Originalpaket `at.heinz.deviceai.continuumlocal`; QA-Paket `.qa`, nur Compile/Test/Lint; keine Besitzer-APK. Standalone-Version ist expliziter Buildparameter, Versionsname `0.3.(Code−1)`. Originalalias `continuum-local`/PKCS12 bleibt maßgeblich. Live-Kanalversuch vom 04.11.2026 20:14:10 UTC scheiterte an der Toolzugänglichkeit; kein HTTP-Ausfall des Servers behauptet. #475 belegt nur historisch 0.3.40/Code41.

Kein Main-Merge, Release, Installation, Signaturwechsel, kostenpflichtiger Dienst oder Actions-Lauf. Veröffentlichung/Installation bleiben gesondert freizugeben. APK, echte lokale Modellinferenz und physischer Gerätetest sind NOT_RUN. S01-, GLB-/GPU-/Sprach- und M2.9-Gates werden nicht gesenkt.

Belege: [Manifest und Roharchive](../../evidence/c1-integration-20261104/), [fortsetzbarer Handoff](C1-INTEGRATION-HANDOFF-20261104.md), [unabhängige Reviews](../../evidence/c1-integration-20261104/independent-reviews.json).

---

## Historisches Board aus #498, unverändert erhalten

# ASTRA-NUCLEUS — zentrales Management-Board

Stand: 04.11.2026, Europe/Vienna. Generation 1, erster Quellen- und Anschlussreview.
Repository: Ullmann27/device-ai. Koordinations-PR: https://github.com/Ullmann27/device-ai/pull/498
Eigener Zweig: `coordination/astra-nucleus-20261104-1027`.

## Erfüllter Teil und Arbeitsgrenzen

Drei reale Manager-Sitzungen und alle zehn real gestarteten Spezialisten haben ihre ersten Quellen-/Anschlussprüfungen abgeschlossen. Die zehn Spezialaufträge sind in Optimierung (4), Implementierung (3) und Reparatur (3) aufgeteilt. Das sind 13 abgeschlossene Agentensitzungen, in Wellen ausgeführt; am Abschlusscheckpoint laufen keine Unteragenten. Die Produkt-Folgeaufträge stehen bereit, Generation 2 ist noch nicht gestartet. Der aktuelle tatsächliche Dispatch steht unten und maschinenlesbar in [GENERATION-1.json](astra-nucleus/GENERATION-1.json).

Dieser Stand implementiert Koordination, Quellenprüfung und sieben eigenständig kopierbare [Chat-Aufträge](ASTRA-NUCLEUS-ACTION-PACKAGE-20261104.md). Er behauptet keine neue Android-Funktion, kein Modelltraining und keinen APK-/Geräteerfolg. Entwicklungsassistenten dieses Chats sind keine in der App installierten Bots.

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

1. Modellkapazität: Der Koordinator las `learning/lm/LmModel.kt` am Commit `ee5779725690c573d3310c061c6e2e6faf5f0a8b` und zählte sämtliche Formen aus `LmConfig.layout()` für minGRU unabhängig nach. Die Architekturgrößen 132.160, 460.928, 1.708.288, 6.562.304 und 9.712.128 stimmen; weitere protokollierte Stufen sind mitgezählt. Gebundene Ein-/Ausgabegewichte zählen einmal. [Tensorliste und Rechnung](../../evidence/astra-nucleus-20261104/parameter-layout-counts.json). Keine tatsächlichen Modellgewichte, Trainingsupdates oder Qualitätsgewinne wurden dadurch nachgewiesen.
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
