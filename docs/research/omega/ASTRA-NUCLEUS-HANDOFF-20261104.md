# ASTRA-NUCLEUS — zentrales Management-Board

Stand: 04.11.2026, Europe/Vienna. Generation 1, erster Quellen- und Anschlussreview.
Repository: Ullmann27/device-ai. Koordinations-PR: https://github.com/Ullmann27/device-ai/pull/498
Eigener Zweig: `coordination/astra-nucleus-20261104-1027`.

## Erfüllter Teil und Arbeitsgrenzen

Drei reale Manager-Sitzungen haben ihre Bestandsaufnahme abgeschlossen. Zehn Spezialaufträge sind in Optimierung (4), Implementierung (3) und Reparatur (3) aufgeteilt. Der aktuelle tatsächliche Dispatch steht unten und maschinenlesbar in [GENERATION-1.json](astra-nucleus/GENERATION-1.json).

Dieser Stand implementiert Koordination, Quellenprüfung und sieben eigenständig kopierbare [Chat-Aufträge](ASTRA-NUCLEUS-ACTION-PACKAGE-20261104.md). Er behauptet keine neue Android-Funktion, kein Modelltraining und keinen APK-/Geräteerfolg. Entwicklungsassistenten dieses Chats sind keine in der App installierten Bots.

Technische Kapazität hier: drei gleichzeitig laufende Unteragenten plus der Koordinator. Zehn Rollen sind keine zehn gleichzeitig laufenden Modellprozesse. Externe Chat-Sitzungen sind nicht fernsteuerbar oder als Prozesse einsehbar; ihre Empfangsbestätigung ist noch nicht belegt.

## Verifizierte Quellstände

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
| G1-I3 | IMPLEMENTIERUNG | Gemeinsames Integrationspaket | RUNNING |
| G1-R1 | REPARATUR | Avatar, Mimik und Lebenszyklus | RUNNING |
| G1-R2 | REPARATUR | Worker-Abbruch und Wiedereinstieg | RUNNING |
| G1-R3 | REPARATUR | Unabhängige Abnahme und Update | QUEUED |

SOURCE_REVIEW_COMPLETE heißt: der erste Quellenreview wurde abgeschlossen. Es bedeutet weder behobener Produktfehler noch bestandene Runtime-/Geräteprüfung. Exakte Agentensitzungen, Grenzen und Ergebnisse stehen in GENERATION-1.json.

## Erste belastbare Befunde

1. Modellkapazität: Der Koordinator las `learning/lm/LmModel.kt` am Commit `ee5779725690c573d3310c061c6e2e6faf5f0a8b` und zählte sämtliche Formen aus `LmConfig.layout()` für minGRU unabhängig nach. Die Architekturgrößen 132.160, 460.928, 1.708.288, 6.562.304 und 9.712.128 stimmen; weitere protokollierte Stufen sind mitgezählt. Gebundene Ein-/Ausgabegewichte zählen einmal. [Tensorliste und Rechnung](../../evidence/astra-nucleus-20261104/parameter-layout-counts.json). Keine tatsächlichen Modellgewichte, Trainingsupdates oder Qualitätsgewinne wurden dadurch nachgewiesen.
2. Wiederaufnahme von Experimenten: G1-O3 und der Koordinator prüften `experimental/weight-space-20261104/run_experiment.py` bei `ee577972`. Der Wiederaufnahmepfad vergleicht gespeicherten Orchestrator-Hash sowie Befehls-/Heap-/Timeout-Identität nicht vollständig. Das ist ein statisch belegtes Reproduzierbarkeitsrisiko, kein ausgeführter Fehlernachweis oder bereits erfolgter Fix. HILFE-2 reproduziert mit synthetischen Receipts und übergibt #492 einen geprüften Fix.
3. Gehirn/Avatar: #494 `ca31eb8b` enthält Pläne für `cockpit/ContinuumMemoryEventSource.kt`, noch keine neue Produktionsimplementierung. #494 liefert den Adapter; #491 besitzt Activity, Listener- und Lebenszyklusanbindung.
4. Avatar: Die baseline-probes bei #495 `ca16da89` dokumentieren Visem-/JawOpen-Überlagerung, veraltete Audioproben, eine nicht isolierte Sink-Exception und Zustellung an einen abgelösten Renderer. Autorenbelege sind geprüft gelesen, nicht von diesem Koordinator als Gerätetest ausgeführt. Vor Reparatur bereits laufende Bearbeitung abgleichen.
5. Bots und Routinen: #493 `e4631a03` enthält bereits ChiefBotPersonas, ChiefBotTaskIntake und Änderungen an ChiefBotConversation, aber keine Activity-Änderung im geprüften Diff. #496 `07dca053` enthält einen RoutineBook-Scope-/Zeitüberlauf-Fix samt beschriebenem RED→GREEN-Lauf mit 12 Tests. Diese Nachweise ersetzen keine unabhängige Android-Verbraucher-/Gesamtprüfung.
6. Grafikabnahme: Die bei #491 dokumentierte Java2D-Vorschau ist keine Android-Activity-/Fold7-Aufnahme. Referenztreue, native GPU-, Sprach- und Geräteprüfung bleiben gesonderte Nachweise.

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

## Verifikation und Rücknahme

Eigene Prüfungen: GitHub-Metadaten, Handoffs und ausgewählte Produktions-/Experimentdateien gelesen; unveränderliche Commit-Vergleiche; unabhängige Parameter-Arithmetik. Kein Android-Build, kein Training, kein Modellbenchmark und kein Fold7-Lauf in diesem Koordinationsauftrag. Aussagen aus Autorenlogs bleiben als solche gekennzeichnet.

Kein Main-Merge/Force-Push, keine neuen App-Befugnisse, kein zweiter Scheduler oder Speicher. Keine Actions, kostenpflichtigen Dienste, Modell-Promotion, Signierung, Veröffentlichung oder Installation. Originalpaket `at.heinz.deviceai.continuumlocal`, Originalsignatur, bestehender Render-Updatekanal und ToolGate/CCC1 bleiben erhalten.

Rücknahme betrifft ausschließlich die neuen Dokumentations-/Evidenzdateien dieses Koordinationszweigs. Keine fremde Branch zurücksetzen oder löschen.
