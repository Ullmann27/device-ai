# ASTRA-NUCLEUS — Generation 1, Anschlussrunde R2

Datum: 2026-11-04. Repository: Ullmann27/device-ai. Koordination: Draft-PR #498.
Auftrag des Eigentümers: Die sieben benannten Arbeitschats mit konkreten Anschlussaufträgen versorgen und einen technisch verfügbaren Startweg prüfen. Keine neue Bot-Generation wird durch dieses Dokument behauptet.

## Ausführungsstatus und Grenzen

Dieses Paket ist eine tatsächliche, versionierte Auftragsübergabe über GitHub. Es startet keine ChatGPT-Unterhaltung. Zum Zeitpunkt der Veröffentlichung sind Empfang, Start und Prozesszustand aller sieben Empfänger UNCONFIRMED. In dieser Koordinationsrunde wurden keine neuen Unteragenten gestartet und keine Produkt-/Modelltests ausgeführt. Frühere Manager-/Helferberichte sind historische Belege, keine Live-Prozessliste.

Die aktuelle Werkzeugprüfung ergab keinen direkt verfügbaren Befehl zum Starten oder Beschreiben bestehender ChatGPT-Chats. Die gelesenen Browserprofile enthalten keinen bestätigten ChatGPT-Login. Es wurde kein Browserlauf, kostenpflichtiger Dienst, fremder Agent oder zusätzlicher Scheduler aktiviert. Die vorhandene stündliche Anschlussprüfung bleibt eine Prüfung/Planung, kein permanenter Sieben-Chat-Worker.

Die unten genannten Prüfresultate sind gelesene Autorenbelege, keine erneuten Testausführungen des Koordinators. Nicht von alten roten Zwischenständen erneut beginnen, wenn inzwischen passende grüne Belege vorliegen. Ebenso nicht einen historischen PASS auf einen neueren Commit übertragen.

## Zuständigkeiten und geprüfte Ausgangsstände

| Chat | PR | Quell-SHA bei der Bestandsaufnahme | Eigene Branch | Auftrag |
|---|---|---|---|---|
| Grafik Avatar und Design | #491 | e10f4785284ab08f1d5d0a77b64ab8631ed5b9cb | codex/astra-jarvis-takeover-20261104 | G1-R2-AVATAR-INTEGRATE |
| Modellgewichte erweitern | #492 | 88d7b05072bdec6250990efaa9914a1e6744c29b | research/continuum-weight-space-audit-20261104 | G1-R2-WEIGHT-ACCEPT |
| Devicce Ai Bots / Device AI Bots | #493 | dbb8e2f7085b0140ed437502b27fac5f988ac834 | feat/continuum-personality-bots-20261104 | G1-R2-BOTS-ACCEPT |
| Gehirn, Wissensabruf, Logik etc. | #494 | 7e2d69a53f42d22c890e56ab5921295ff408ecd5 | feat/continuum-memory-brain-20261104 | G1-R2-BRAIN-INTEGRATE |
| Weiter Bot helfer | #495 | 6cf6e2128463e6a24dfac4652bb65e325ff6f18a | codex/continuum-three-avatar-helpers-20261104 | G1-R2-AVATAR-VERIFY |
| Helfer mit Bots | #496 | fa06738df6da2eb05f1e132c53ad736447c9937a | support/continuum-ten-bot-assist-20261104 | G1-R2-CROSS-QA |
| Bots Helfer für Gewichtsmodelle | #497 | 06f071e5850dd405f374bbb58d826f64740d83af | research/continuum-weight-helper-team-20261104 | G1-R2-WEIGHT-RESUME |

Diese SHAs sind Prüfanker, keine Aufforderung, neuere eigene Arbeit zurückzusetzen. Vor jeder Änderung aktuellen Head, Kommentare, tatsächliche Dateien und den eigenen Handoff lesen. Den neuen Ausgangsstand im Empfangsbeleg festhalten. Keine neuen Aufträge aus einem bloßen Branch-Namen ableiten.

## Gemeinsames Protokoll für alle sieben Empfänger

1. Lies AGENTS.md, die eigenen gültigen Projekt-/Experimentgrenzen, den Handoff dieser Branch und die aktuellen PR-Kommentare. Vergleiche den aktuellen Auftrag mit bereits laufender oder fertiger Arbeit. Wähle nur den zu deinem bestehenden Chat/PR gehörenden Auftrag; keine Rollenübernahme bei unklarer Zuordnung.
2. Bestätige auf dem eigenen PR und verlinkt in #498: task_id, eigener Branch, aktueller Basis-SHA, Ausführungsfähigkeit, konkret beanspruchte Dateipfade und erster überprüfbarer Schritt. Ein Kommentar ist kein technischer Dateilock; auf einem isolierten eigenen Arbeitszweig arbeiten und Überschneidungen vor Schreiben klären.
3. Arbeite in der tatsächlich verfügbaren Sitzung bis zu zehn klar begrenzte, sinnvolle Schritte ab: lesen, reproduzieren, minimal korrigieren, testen, unabhängig prüfen soweit ein echter Reviewer verfügbar ist, committen, pushen, Übergabe. Bei vorhandenem echten Unteragentenwerkzeug nur dessen reale Kapazität nutzen. Sonst selbst nacheinander arbeiten; virtuelle Rollen nicht als gestartete Prozesse zählen.
4. Priorität: reproduzierbarer Fehler vor Integration, Integration vor Optimierung. Nach fertigem Schritt den nächsten bereits freigegebenen Schritt aus diesem Auftrag nehmen. Keine endlose Polling-/Retry-Schleife; bei fehlender Abhängigkeit einen unabhängigen Prüfteil bearbeiten oder BLOCKED mit präzisem fehlendem Eingang sichern. Keine kostenpflichtige Ersatzumgebung aktivieren.
5. Fremde Worktrees und Branches nicht beschreiben. AgentActivity gehört für die Gesamtintegration #491; #493 liefert auf der eigenen Branch eng begrenzte Bot-Hooks, #494 liefert Compiler-/Factory-Vertrag. Diese Änderungen nur nach Vergleich der tatsächlichen Diffs zusammenführen, niemals die ganze Activity aus einer älteren Basis ersetzen.
6. Gradle lokal über den tatsächlich vorhandenen gemeinsamen Build-Lock serialisieren. Keine GitHub Actions, kein S01-Replay, kein neues Modelltraining in dieser Anschlussrunde, keine Modell-Promotion, keine bezahlten APIs. Fehlendes SDK/Gerät bleibt UNAVAILABLE/NOT_RUN; Tests und Toolchain nicht fälschen.
7. Kein Main-Merge, Force-Push, Branch-Löschen, Signieren, Release, Installation, Berechtigungsausbau oder Wechsel von Paket/Originalsignatur/Render-Updatekanal. Kein zweiter Scheduler, Gedächtnisspeicher oder Modellruntime. ToolGate/CCC1, STOP, Consent-/Widerrufsgrenzen erhalten. Keine privaten Daten, Modellgewichte oder Schlüssel in öffentlichen Belegen.
8. Abschluss oder Kontextende: eigene Arbeit früh sichern, präzise Ergebnis-SHA und Dateidiff, tatsächliche Befehle/Exitcodes, Belegpfade, offene Gates, Rückfall und nächster Einstieg. VERIFIED nur für exakt getesteten Umfang; SOURCE_REVIEW nicht mit Runtime-/Geräteabnahme verwechseln. Kein Dauerbetrieb nach Sitzungsende versprechen.

## G1-R2-WEIGHT-ACCEPT — Hauptchat Modellgewichte, #492

Eingänge: aktueller eigener Head; #497 Fix-Commit 06f071e5850dd405f374bbb58d826f64740d83af; docs/evidence/G1-REPAIR-WEIGHT-001/README.md und tatsächliche Tests/Logs. Der Helferbericht enthält 12 synthetische GREEN-Tests ohne Modelltraining. Zusätzlich #496s source-bound Resume-Befund aus dessen Handoff lesen; nicht ungeprüft auf neuere Runner übertragen.

Erlaubt: experimental/weight-space-20261104/ und eigene Test-/Evidenz-/Handoff-Dateien. Produktives learning/lm und aktive Modelle bleiben unverändert.

Auftrag: Den bereits vorhandenen Pflichtgate-Fix unabhängig gegen den neuesten eigenen Runner prüfen und als kleinen nachvollziehbaren Commit übernehmen, sofern kompatibel. Baseline-Fail, fehlender/ungültiger lastPassed, vorhandene frozen-selection und main(all) müssen vor Trainings-/Final-Aufrufen stoppen. Gültige spätere Kapazitätsstops und echte Wiederaufnahme dürfen nicht beschädigt werden. Parameter-/Checkpoint-/Qualitätsschwellen unverändert lassen. Die historischen Trainingsresultate nachträglich nicht mit einem neuen PASS umetikettieren.

Abnahme: synthetischer RED→GREEN-Nachweis und unabhängige Negativkontrolle auf dem übernommenen Quellstand, gültiger Resume ohne doppelten Trainingsaufruf, keine unzulässige Auswahl. Danach eine neue saubere Run-ID und ein überprüfbares Forschungsprotokoll für 6–10 Millionen eigene Parameter vorbereiten, noch kein Trainingslauf. EOS-/0-von-6-Qualitätsbefunde offen führen.

Rückfall: Nicht übernehmen oder ausschließlich eigenen Integrationscommit zurücknehmen; fehlgeschlagene Baseline-Läufe auch mit altem Code nicht fortsetzen. Checkpoints/Originale bewahren.

## G1-R2-WEIGHT-RESUME — Gewichtehilfe, #497

Eingänge: eigener veröffentlichter Gate-Fix, neuester #492-Head, #496-Handoff zu unterbrochenen/malformed Phasenergebnissen. docs/evidence/G1-REPAIR-WEIGHT-001/README.md ist neuer als die noch offene Formulierung im alten WEIGHT-HELPER-TEAM-Handoff.

Erlaubt: eigener Runner-Zweig unter experimental/weight-space-20261104/, isolierte neue Regressionen sowie eigene Evidenz/Handoff. Keine fremde Branch bearbeiten.

Auftrag: Den fertigen G1-REPAIR-WEIGHT-001 nicht noch einmal implementieren. Eigenen Handoff auf den belegten Abschluss aktualisieren. Prüfe danach auf den real neuesten Runnerbytes, ob vorhandene aber ungültige/abgeschnittene Phase-JSONs nur aufgrund ihrer Existenz übersprungen werden. Untersuche Quellhash-, Befehls-, Heap-, Timeout- und Eingabebindung bei Resume. Zuerst unabhängige synthetische Reproduktion; Korrektur nur für noch bestehende Fälle und getrennt vom fertigen Gate-Fix. Keine JVM-/Trainingsprozesse starten.

Abnahme: gefälschte, fremde, unvollständige oder veraltete Belege werden nachvollziehbar abgelehnt, gültige vollständige Belege bleiben wiederverwendbar; Tests weisen nach, dass kein unzulässiger Trainings-/Final-Aufruf stattfindet. Ergebnis an #492 und #498 mit Patch-Reihenfolge; keine Auswahl oder Gewichtsdatei überschreiben.

Rückfall: Nur neue Helferkorrektur nicht übernehmen; vorhandenen Pflichtgate-Fix bewahren.

## G1-R2-BRAIN-INTEGRATE — Hauptchat Gehirn/Gedächtnis, #494

Eingänge: eigener MEMORY-BRAIN-HANDOFF.md bei 7e2d69a5 oder neuer; #491-Head; ausdrücklich verlinkter isolierter Integrationszweig integration/continuum-memory-avatar-hooks-20261104. Dessen tatsächlichen Head und Inhalt lesen, nicht aus dem Namen als fertig ansehen. Bericht: 9/9 Compiler→Hub→Mapper→Orb-Komponententests, 14/14 Factory- und 3/3 Source-Contract-Tests; Activity/Handler und gemeinsame Endgates noch offen.

Erlaubt: eigene cognitive-/learning-Gedächtnis-/cockpit-Adapterdateien und zugehörige Tests; Activity-Gesamteinbau verbleibt bei #491. Bestehende Recovery-/Consent-Dateien nicht parallel umschreiben.

Auftrag: Gemeinsamen aktuellen Quellenstand und den echten Einstieg AiContextCompiler.withRecallObserver(ContinuumMemoryRecallPublisher(filesDir)) sowie MemoryEventSources.forApp→ContinuumMemoryEventSource.forApp abgleichen. Bereits integrierte Hooks nicht doppelt registrieren. Den echten Activity/Handler-Test abschließen; close/detach, Wiederaufnahme, genau-einmal-Zustellung und keine verspäteten Effekte testen. Die vorhandene Negativkontrolle für abgetrennten Producer erhalten. Alte Test-Race-Fixes dürfen keine Produktbehauptung ersetzen.

Abnahme: überprüfbarer echter Aufrufweg und Lifecycle-Negativtests auf identischem kombinierten Tree; voller relevanter Gradle-/Lint-Lauf; technische Übergabe an #491. Keine erfundene Erinnerung oder synthetische Erfolgsmeldung in der App. Fold7/TTS/GPU getrennt offen halten.

Rückfall: neue Adapter-/Hook-Änderung isoliert nicht übernehmen; keine Gedächtnisdaten löschen und keine Consent-Sperre umgehen.

## G1-R2-BOTS-ACCEPT — Persönlichkeits-Bots, #493

Eingänge: eigener dbb8e2f-Stand oder neuer; PERSONALITY-BOTS-HANDOFF-20261104.md. Die alten Router-/Roster-REDs sind inzwischen durch den Autorenlauf focused-green mit 114 bestandenen Tests überholt. Volle Suite/Lint/Review noch abgleichen.

Erlaubt: eigene chief-Botprofile/-Adressierung/-TaskIntake, Bot-Roster/Task-Dialog und eng begrenzte eigene Activity-Hooks samt Tests. Keine globale Activity-Ersetzung und keine Übernahme fremder Avatar-/Recovery-Arbeit ohne #491.

Auftrag: Volle Unit-/Widgetprüfung und konfigurierten Lint auf tatsächlichen neuesten Quellen abschließen. Prüfe explizite Moduswahl, vollständige mehrzeilige Aufträge samt nachgestellten Verboten, vier tatsächliche Modellaufrufe inklusive Recovery, Abbruch/Profilwechsel/Runtime-busy, Datenisolation und keine Werkzeugrechte aus Personas. Nicht erneut die bereits grünen neun alten Routerfälle als offene Produktfehler melden. Liefere #491 einen auf den neuesten Avatarstand abgestimmten minimalen Activity-Diff und Konfliktliste.

Abnahme: source-bound vollständige Hostbelege, keine verlorenen Verbote/Entwürfe, keine doppelte Ausführung bei unbekanntem Callback-Erfolg, vorhandene ToolGate/CCC1-Grenzen. Gerätemodell-/Fold7-/Releaseprüfung bleibt gesondert.

Rückfall: nur eigene Bot-Hook-Integrationscommits zurücknehmen; Profile, Konversationen und fremde Activity-Korrekturen erhalten.

## G1-R2-AVATAR-VERIFY — Weiter Bot helfer, #495

Eingänge: eigener 6cf6e212-Stand oder neuer; getesteter Produktionscommit 7859d5d212d61829fabc7dfdbd3ca7587852388d; #491 neuester Head; #496 GLB-Vertragsbefund. Der vorhandene kombinierte Autorenlauf enthält 118 bestandene Tests, 26 davon neue Regressionen. Die drei Produktionskorrekturen nicht neu erfinden.

Erlaubt: fokussierte unabhängige Tests/Prüfberichte zu AvatarMorphMapper, AvatarPoseReducer in AvatarContract.kt und JarvisAvatarSlot in CockpitContracts.kt; neue Produktionskorrekturen nur im eigenen abgegrenzten Helferzweig nach tatsächlicher Reproduktion. Kein Austausch der GLB und kein globales Activity-Rewiring.

Auftrag: Fertiges Paket gegen #491s aktuellen Integrationskandidaten überprüfen, bestehende Tests und gezielte Negative-/Mutation-Kontrollen ausführen. Wichtig: Komponentenfix am JarvisAvatarSlot beweist nicht, dass der normale Sprachpfad diesen Slot aufruft. Tatsächlichen Caller-/Rendererpfad ermitteln und #491 minimalen Anschlussvorschlag liefern. GLB-Augen-/Kiefer-Pivotbefund zunächst lesend auf exakte Assetbytes, Einheiten und Transformationskonventionen prüfen; keine Bildähnlichkeit aus Zahlen ableiten.

Abnahme: Tests des wirklich kombinierten Quellstands, keine veralteten Audiosamples/Renderer-Callbacks, dokumentierte verworfene statt verspätet nachgelieferte Events; verbleibende Rig-/Asset-/Device-Grenzen benennen.

Rückfall: neue Helferänderungen nicht übernehmen; getestetes Dreierpaket und Originalasset bewahren.

## G1-R2-AVATAR-INTEGRATE — Grafik Avatar und Design, #491

Eingänge: eigener e10f4785-Stand oder neuer, #495 Fertigpaket, #494 Gedächtnis-/Activity-Handoff, #493 Bot-Hooks, #496 geprüfte Routine-/Workeränderungen. Einzel-PASS gelten nicht für den gemeinsamen Kandidaten. Konfigurierter NewApi-Lint ist keine Vollprüfung aller optionalen Lintkategorien.

Erlaubt: eigener isolierter Integrationszweig für AgentActivity, vorhandene Avatar-/Orb-/Menü-/Lifecycle-Verbindungen, geprüfte Nachbarpatches und deren Integrationstests. Renderer, Datenbanken und Scheduler nicht verdoppeln. Modellgewichte und produktive Modellwahl bleiben außerhalb dieses Pakets.

Auftrag: Exakte freigegebene Eingangs-SHAs und überlappende Pfade festhalten; bestätigte Patches seriell integrieren, bevor neuere fremde Heads nachgezogen werden. #495s drei korrigierte Produktionsstellen übernehmen und erneut testen. #494s echte Recall-/Factory-Verbindungen mit vorhandenen Recovery-/Consent-Grenzen und #493s Bot-Auftrags-Hooks verbinden. Den bereits gemeldeten vollständigen Activity/Pane-Test-Timeout um CameraDrift/TimeAnimator/Choreographer als offene Prüflücke untersuchen; nicht durch Entfernen von Assertions, Abschalten des getesteten Produktpfads oder bloßes Erhöhen von Timeouts grün machen.

Abnahme: ein sichtbarer bestehender Avatar und eine Gehirnkugel, tatsächliche Ereignisproduzenten, genau-einmal-Subscription mit Pause/Detach/STOP, vollständige kombinierte Unit-/Widget-/relevante Integrationsprüfungen, konfigurierter Lint und lokaler Debug-Build auf demselben Tree. Referenzbilder/GLB-Provenienz und echte Fold7-GPU-/Sprachprüfung getrennt führen. Keine finale Besitzer-APK behaupten.

Rückfall: nur nicht abgenommene Integrationscommits zurücknehmen; keinen alten Recovery-Reader ohne Beachtung des Consent-Markers auf wiederhergestellte Nutzerdaten loslassen. Keine Release-/Installeraktion.

## G1-R2-CROSS-QA — Helfer mit Bots, #496

Eingänge: neuester eigener Handoff und exklusiver Worker-Admission-Auftrag, #491 Integrationskandidat, #492 Gate-Übernahme, #493/#494/#495 finale Belege. Bei fa06738d sind zehn Helfereinsätze dokumentiert; 09/10 noch als laufend geführt. Das ist keine aktuelle Prozessmessung.

Erlaubt: eigener Routine-/ChiefWorkerPool-Admission-Fix nach Abgleich der aktuellen exklusiven Zuständigkeit; isolierte Test-/Evidenzdateien. Fremde Gewichte-/Avatar-/Activity-Produktionsdateien nur lesen und konkrete Findings an Eigentümer geben.

Auftrag: Zuerst ausstehende Admission-/Review-Ergebnisse sichern, keine doppelte Workerreparatur starten. Bestehende Scope-/Überlaufregressionen erhalten. Dann den von #491 tatsächlich bereitgestellten gemeinsamen Tree unabhängig gegen Recall→Orb, Botauftrag→ToolGate/CCC1→STOP und Avatar-Lifecycle prüfen. Modellgate- und fehlerhafte Resume-Belege als getrennte Fehlerklassen behandeln. Die 13 Service-Setup-Ausfälle bleiben BLOCKED, bis echte Klassenpfad-/Runtime-/Isolationsvoraussetzungen vorliegen; kein S01 starten oder Fixtures erfinden. GLB-Originalbefund und 18 synthetische Updatetests nicht als Geräte-/Signaturprüfung darstellen.

Abnahme: eindeutig abgegrenzter, unabhängiger Ergebnisbericht mit exakter Tree-/SHA-Bindung und FAIL/BLOCKED/NOT_RUN, ggf. reproduzierbare Regressionen; keine bloße Wiederholung fremder Testzahlen. Abschlussmeldung an #498 und verantwortliche PRs.

Rückfall: geprüfte Unterstützungskorrekturen nicht übernehmen, falls eigene Negativkontrollen oder kombinierte Gates scheitern; fremde Branches unverändert lassen.

## Zehn logische Zuständigkeiten — keine neuen Prozesse

O1 Abstammung/Zuständigkeit: #496. O2 Parameterkapazität: #497→#492. O3 Daten/Resume/Ressourcen: #497→#492. O4 Gedächtnisbelege: #494. I1 Gehirn→Avatar: #494→#491. I2 Botauftrag: #493→#491. I3 gemeinsame Integration: #491. R1 Avatar/Mimik/Lifecycle: #495→#491. R2 Worker/Abbruch: #496. R3 unabhängige Gesamt-/Updateabnahme: #496.

Quellen: die oben gepinnten PR-Heads; docs/evidence/G1-REPAIR-WEIGHT-001/README.md; THREE-AVATAR-HELPERS-HANDOFF-20261104.md; MEMORY-BRAIN-HANDOFF.md; PERSONALITY-BOTS-HANDOFF-20261104.md; TEN-BOT-SUPPORT-HANDOFF-20261104.md; #491s sourcegebundene QA-Beschreibung. Vor Ausführung die Originale erneut abgleichen.

## Empfangs-/Ergebnisbeleg

Jeder tatsächliche Empfänger verwendet einen eigenen Kommentar, keine Veränderung dieses gemeinsamen Dokuments:
`task_id | status=ACK/RUNNING/REVIEW_READY/VERIFIED/BLOCKED | base_sha | result_sha oder NOT_PRODUCED | claimed_paths | tatsächlich gestartete agent_ids oder NONE | commands+exitcodes | evidence | blockers | rollback | next_step`.

Ein bereitgestellter Kommentar bleibt PUBLISHED_WAITING_ACK, bis eine nachweisbare Antwort des bearbeitenden Chats vorliegt. Kein Timeout gibt fremde Dateizuständigkeiten automatisch frei. Nach zehn Schritten oder Sitzungsende geprüften Handoff sichern; eine weitere Generation nur mit tatsächlich neu gestartetem Bearbeiter und bestätigtem Eingang, nicht als bloße Statusdekoration.
