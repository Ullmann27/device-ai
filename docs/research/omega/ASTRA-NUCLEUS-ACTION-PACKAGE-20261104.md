# ASTRA-NUCLEUS — Aktionspaket Generation 1

04.11.2026. Jeder Block ist eigenständig kopierbar. Maßgeblich ist das zentrale Board in [PR #498](https://github.com/Ullmann27/device-ai/pull/498). Diese Aufträge setzen vorhandene Arbeit fort; sie sind keine Behauptung, dass andere Chat-Sitzungen automatisch gestartet wurden.

## HILFE-1 — Persönlichkeits-Bots und Aufträge

Ziel: #493

```text
@GitHub — ASTRA-NUCLEUS, Generation 1.
Projekt: Ullmann27/device-ai. Lies zuerst Draft-PR #498 und dessen aktuelles docs/research/omega/ASTRA-NUCLEUS-HANDOFF-20261104.md. Lies dann deinen aktuellen PR, seinen tatsächlichen Head und sein Handoff. Bewahre bereits laufende Arbeit.
Arbeite auf deinem eigenen Zweig mit genau benannten Dateizuständigkeiten. Bei einer geteilten Datei liefert der Helfer einen geprüften Anschluss/Patch; der zuständige Hauptchat baut ihn ein. Ein alter PR-Text ist kein aktueller Codebeleg.
Bearbeite bis zu zehn sinnvolle Arbeitsschritte pro Generation. Nach jedem geprüften Baustein sofort Commit, Handoff und nächsten unabhängigen Auftrag vorbereiten. Bei Blockade Ursache und benötigten Eingang festhalten, dann eine andere freigegebene Aufgabe übernehmen. Neue Generation nur mit gesichertem Übergabestand und tatsächlich verfügbarer Ausführung starten. Keine unendliche Laufzeit oder Anzahl aktiver Prozesse behaupten.
Vor jeder Übergabe: Code/Schnittstelle prüfen, Abbruch- und Fehlerfälle prüfen, Ergebnis gegen tatsächliche Logs und exakt geprüfte SHA prüfen. Melde PASS, FAIL, NOT_RUN oder UNAVAILABLE getrennt; kein geerbtes PASS.
Ausgabe: task_id, Generation, zuständiger Chat, Basis-SHA, Ergebnis-SHA, Dateien, Abhängigkeiten, Befehle/Exitcodes/Logpfade, offene Blocker, Rücknahme und sofort ausführbarer Folgeauftrag.
Keine Main-Merges oder Force-Pushes. Bestehende ToolGate/CCC1-, Daten-, Paket-, Signatur- und Updategrenzen erhalten. Keine kostenpflichtigen Dienste, Actions, Modell-Promotion, Veröffentlichung oder Installation auslösen.

Du bist HILFE-1 für den bestehenden Persönlichkeits-Bot-Strang #493. Prüfe außerdem die Übergaben von #491, #494 und #496, damit Profile, Gehirn, Worker und Oberfläche zusammenpassen.
1. Prüfe im aktuellen Code ChiefBotProfileStore, ChiefBotConversation, Adressierung und vorhandene Auftragsübergabe. Bewahre bearbeitete Profile und getrennte private Bot-Kontexte.
2. Verbinde eine ausdrücklich beauftragte Aktion mit dem bestehenden Owner-Dispatcher/ToolGate/CCC1; Botname oder Persönlichkeit erzeugen keine Berechtigung. Erhalte den vollständigen Nutzerauftrag einschließlich Verneinungen.
3. Liefere #491 einen kleinen Anschluss für Auswahl, Chat, Auftrag und echten Status; bearbeite fremde Avatar-/Activity-Abschnitte nicht parallel. Ein privater Dialog darf keine Werkzeuge verdeckt ausführen.
4. Prüfe falschen Bot, Sitzungswechsel, doppelte Aufträge, fehlende lokale Engine, Abbruch und widerrufene Freigabe. Status muss die tatsächlich erreichte Stufe zeigen.
Erster Baustein: ein sourcegebundener Test und minimaler Anschluss für den ersten belegten Fehler im Profil→Auftrag→Ergebnis-Pfad. Danach die nächste reale Lücke aus demselben Pfad abarbeiten. Keine zweite Bot-, Worker- oder Memory-Infrastruktur.
```

## HILFE-2 — Eigene Parameter und Training

Ziel: #497 → #492

```text
@GitHub — ASTRA-NUCLEUS, Generation 1.
Projekt: Ullmann27/device-ai. Lies zuerst Draft-PR #498 und dessen aktuelles docs/research/omega/ASTRA-NUCLEUS-HANDOFF-20261104.md. Lies dann deinen aktuellen PR, seinen tatsächlichen Head und sein Handoff. Bewahre bereits laufende Arbeit.
Arbeite auf deinem eigenen Zweig mit genau benannten Dateizuständigkeiten. Bei einer geteilten Datei liefert der Helfer einen geprüften Anschluss/Patch; der zuständige Hauptchat baut ihn ein. Ein alter PR-Text ist kein aktueller Codebeleg.
Bearbeite bis zu zehn sinnvolle Arbeitsschritte pro Generation. Nach jedem geprüften Baustein sofort Commit, Handoff und nächsten unabhängigen Auftrag vorbereiten. Bei Blockade Ursache und benötigten Eingang festhalten, dann eine andere freigegebene Aufgabe übernehmen. Neue Generation nur mit gesichertem Übergabestand und tatsächlich verfügbarer Ausführung starten. Keine unendliche Laufzeit oder Anzahl aktiver Prozesse behaupten.
Vor jeder Übergabe: Code/Schnittstelle prüfen, Abbruch- und Fehlerfälle prüfen, Ergebnis gegen tatsächliche Logs und exakt geprüfte SHA prüfen. Melde PASS, FAIL, NOT_RUN oder UNAVAILABLE getrennt; kein geerbtes PASS.
Ausgabe: task_id, Generation, zuständiger Chat, Basis-SHA, Ergebnis-SHA, Dateien, Abhängigkeiten, Befehle/Exitcodes/Logpfade, offene Blocker, Rücknahme und sofort ausführbarer Folgeauftrag.
Keine Main-Merges oder Force-Pushes. Bestehende ToolGate/CCC1-, Daten-, Paket-, Signatur- und Updategrenzen erhalten. Keine kostenpflichtigen Dienste, Actions, Modell-Promotion, Veröffentlichung oder Installation auslösen.

Du bist HILFE-2 auf dem Gewichte-Helferstrang #497 für Hauptchat #492. Prüfe dessen neuesten Experimentstand, bevor du etwas ergänzt.
1. Ermittle SWB1, LM1 und das große lokale Basismodell getrennt. Für jeden tatsächlich geprüften Kandidaten: Modellhash, Format, Tensorformen, gespeicherte/trainierbare Parameter, Architektur und Ursprung der neu hinzugefügten Gewichte.
2. Nutze vorhandene LmWeightSurgery-, LmWeightExperiment- und LmCapacityBenchmark-Pfade. Ergänze unabhängige Prüfungen für Widen/Deepen, Speichern/Laden, nicht-finite Werte, Überläufe und Kompatibilität.
3. Unterstütze die stufenweise Erweiterung Richtung sechs bis zehn Millionen eigener trainierbarer Parameter. Importiere dafür keine fremden vortrainierten Gewichte. Größe, erfolgreiche Ausführung und verbesserte Qualität separat belegen.
4. Trenne Entwicklungsdaten, Kandidatenauswahl und versiegelten Holdout. Öffne den versiegelten Holdout nicht. Messe passende Baseline und Kandidaten mit gleicher Aufgabe, gleichem Budget und dokumentierten Seeds. Host-RAM/Laufzeit sind kein Fold7-Nachweis.
Erster Baustein: Prüfe die von G1-O3 und dem Koordinator bei ee577972 bestätigte Wiederaufnahme-Lücke in run_experiment.py: gespeicherter orchestratorSha256, command/Hauptklasse/Heap und timeoutSeconds werden nicht vollständig gegen den neuen Aufruf verglichen. Erstelle eine synthetische Mutationstest-Reihe ohne Modelltraining und liefere #492 den kleinsten kompatiblen Fix. Die vom Koordinator aus LmConfig.layout() nachgezählten Architekturgrößen 6.562.304 und 9.712.128 sind bestätigt; tatsächliche Checkpoints, Training und Qualitätsgewinn bleiben separat nachzuweisen. Liefere #492 einen isolierten Prüfschritt oder Fix; Training, Kandidatenauswahl und Promotion bleiben beim Hauptchat und ihren vorhandenen Gates.
```

## HILFE-3 — Avatar, Mimik und Animation

Ziel: #495 → #491

```text
@GitHub — ASTRA-NUCLEUS, Generation 1.
Projekt: Ullmann27/device-ai. Lies zuerst Draft-PR #498 und dessen aktuelles docs/research/omega/ASTRA-NUCLEUS-HANDOFF-20261104.md. Lies dann deinen aktuellen PR, seinen tatsächlichen Head und sein Handoff. Bewahre bereits laufende Arbeit.
Arbeite auf deinem eigenen Zweig mit genau benannten Dateizuständigkeiten. Bei einer geteilten Datei liefert der Helfer einen geprüften Anschluss/Patch; der zuständige Hauptchat baut ihn ein. Ein alter PR-Text ist kein aktueller Codebeleg.
Bearbeite bis zu zehn sinnvolle Arbeitsschritte pro Generation. Nach jedem geprüften Baustein sofort Commit, Handoff und nächsten unabhängigen Auftrag vorbereiten. Bei Blockade Ursache und benötigten Eingang festhalten, dann eine andere freigegebene Aufgabe übernehmen. Neue Generation nur mit gesichertem Übergabestand und tatsächlich verfügbarer Ausführung starten. Keine unendliche Laufzeit oder Anzahl aktiver Prozesse behaupten.
Vor jeder Übergabe: Code/Schnittstelle prüfen, Abbruch- und Fehlerfälle prüfen, Ergebnis gegen tatsächliche Logs und exakt geprüfte SHA prüfen. Melde PASS, FAIL, NOT_RUN oder UNAVAILABLE getrennt; kein geerbtes PASS.
Ausgabe: task_id, Generation, zuständiger Chat, Basis-SHA, Ergebnis-SHA, Dateien, Abhängigkeiten, Befehle/Exitcodes/Logpfade, offene Blocker, Rücknahme und sofort ausführbarer Folgeauftrag.
Keine Main-Merges oder Force-Pushes. Bestehende ToolGate/CCC1-, Daten-, Paket-, Signatur- und Updategrenzen erhalten. Keine kostenpflichtigen Dienste, Actions, Modell-Promotion, Veröffentlichung oder Installation auslösen.

Du bist HILFE-3 im Avatar-Helferstrang #495 für Hauptchat #491. Prüfe parallel die reale Ereignisschnittstelle aus #494.
1. Ermittle die verwendeten Originalreferenzen, Asset-Hashes und den tatsächlich gerenderten Pfad. Kennzeichne eine nicht zugängliche Referenz ausdrücklich; erfinde keinen Bildvergleich und keinen neuen Ersatzavatar.
2. Prüfe bestehenden Kopf/Orb, Mimik, FaceWeights, LipSync und Animationsmathematik. Fokus: nicht-finite Werte, ungültige Indizes, große/negative Zeitdifferenzen, Abbruch, Surface-Neuerstellung, Pause/Resume sowie mehrfach registrierte Listener.
3. Erarbeite pro Befund einen kleinen reproduzierbaren Regressionstest und eine kompatible Reparatur auf deinem isolierten Zweig. Änderungen an gemeinsamer Activity und finaler Renderer-Anbindung gehen als geprüfter Patch an #491.
4. Bewahre genau eine aktive Darstellung mit den echten Sprach-, Gesprächs- und Gedächtnissignalen. Eine Animation darf keine erfundene Operation anzeigen.
Erster Baustein: Prüfe die vorhandenen baseline-probes aus #495. Der bei ca16da8968e35a490e1f6b54da8a0af03bec22d2 dokumentierte Stand meldet AA→PP/JawOpen-Überlagerung, rückläufige Audiozeitstempel, eine unisolierte Registry-Sink-Exception und veraltete Renderer-Zustellung. Prüfe zuerst, welche davon dein Team inzwischen behoben oder beansprucht hat; übernimm dann die wichtigste noch offene Regression. Liefere danach einen Referenzvergleich mit identischer Perspektive/Größe und klarer Herkunft. Java2D-/JVM-Vorschau, Android-Aufnahme und physischer Fold7-Lauf getrennt kennzeichnen. Keine FPS oder Pixelgleichheit ohne Messung.
```

## HILFE-4 — Worker, Fehlerprüfung und Integrationsbelege

Ziel: #496

```text
@GitHub — ASTRA-NUCLEUS, Generation 1.
Projekt: Ullmann27/device-ai. Lies zuerst Draft-PR #498 und dessen aktuelles docs/research/omega/ASTRA-NUCLEUS-HANDOFF-20261104.md. Lies dann deinen aktuellen PR, seinen tatsächlichen Head und sein Handoff. Bewahre bereits laufende Arbeit.
Arbeite auf deinem eigenen Zweig mit genau benannten Dateizuständigkeiten. Bei einer geteilten Datei liefert der Helfer einen geprüften Anschluss/Patch; der zuständige Hauptchat baut ihn ein. Ein alter PR-Text ist kein aktueller Codebeleg.
Bearbeite bis zu zehn sinnvolle Arbeitsschritte pro Generation. Nach jedem geprüften Baustein sofort Commit, Handoff und nächsten unabhängigen Auftrag vorbereiten. Bei Blockade Ursache und benötigten Eingang festhalten, dann eine andere freigegebene Aufgabe übernehmen. Neue Generation nur mit gesichertem Übergabestand und tatsächlich verfügbarer Ausführung starten. Keine unendliche Laufzeit oder Anzahl aktiver Prozesse behaupten.
Vor jeder Übergabe: Code/Schnittstelle prüfen, Abbruch- und Fehlerfälle prüfen, Ergebnis gegen tatsächliche Logs und exakt geprüfte SHA prüfen. Melde PASS, FAIL, NOT_RUN oder UNAVAILABLE getrennt; kein geerbtes PASS.
Ausgabe: task_id, Generation, zuständiger Chat, Basis-SHA, Ergebnis-SHA, Dateien, Abhängigkeiten, Befehle/Exitcodes/Logpfade, offene Blocker, Rücknahme und sofort ausführbarer Folgeauftrag.
Keine Main-Merges oder Force-Pushes. Bestehende ToolGate/CCC1-, Daten-, Paket-, Signatur- und Updategrenzen erhalten. Keine kostenpflichtigen Dienste, Actions, Modell-Promotion, Veröffentlichung oder Installation auslösen.

Du bist HILFE-4 im bestehenden allgemeinen Helferstrang #496. Prüfe die belegten Ergebnisse aller sieben Arbeitsstränge und suche zuerst unbeanspruchte reproduzierbare Fehler.
1. Erstelle eine aktuelle Pfad-/Abhängigkeitskarte aus den tatsächlichen Heads von #491–#497. Markiere Überschneidungen in AgentActivity, Bot-Auftragsanbindung und Gedächtnisereignissen. Verändere keine fremde Arbeit.
2. Prüfe vorhandenen ChiefWorkerPool/RoutineDispatcher zu doppelter Task-ID, voller Queue, Owner-Abbruch, späten Callbacks, Neustart und Slot-/Claim-Freigabe. Kein zweiter Scheduler.
3. Reproduziere den priorisierten Fehler, liefere den kleinsten Fix samt unabhängiger Gegenprobe und exakt zugehörigem Log. Übernimm fremde PASS-Zahlen nicht.
4. Erstelle ein Integrationsmanifest mit Basis-SHA, zu übernehmenden Commits, Dateizuständigkeiten, Reihenfolge und Rücknahme. Serialisiere schwere Builds über das in deiner aktuellen Umgebung tatsächlich vorhandene Build-Lock.
Erster Baustein: prüfe den bereits vorhandenen RoutineBook-Scope-/Zeitüberlauf-Fix bei 07dca053eef5b20381469a55d107862c65767658 unabhängig und auf dem vorgesehenen Android-Verbraucherpfad. Erfinde oder implementiere denselben Fix nicht erneut. Kläre danach die nächste konkrete Worker-/Integrationsregression. Danach den nächsten bisher ungeprüften Anschluss bearbeiten. Abschlussprodukt ist ein prüfbares Paket für #491 plus gezielte Befunde für #492/#494; keine Signierung, Veröffentlichung oder automatische Main-Integration.
```

## HAUPT-1 — Modellgewichte aufnehmen und auswerten

Ziel: #492

```text
@GitHub — ASTRA-NUCLEUS, Generation 1.
Projekt: Ullmann27/device-ai. Lies zuerst Draft-PR #498 und dessen aktuelles docs/research/omega/ASTRA-NUCLEUS-HANDOFF-20261104.md. Lies dann deinen aktuellen PR, seinen tatsächlichen Head und sein Handoff. Bewahre bereits laufende Arbeit.
Arbeite auf deinem eigenen Zweig mit genau benannten Dateizuständigkeiten. Bei einer geteilten Datei liefert der Helfer einen geprüften Anschluss/Patch; der zuständige Hauptchat baut ihn ein. Ein alter PR-Text ist kein aktueller Codebeleg.
Bearbeite bis zu zehn sinnvolle Arbeitsschritte pro Generation. Nach jedem geprüften Baustein sofort Commit, Handoff und nächsten unabhängigen Auftrag vorbereiten. Bei Blockade Ursache und benötigten Eingang festhalten, dann eine andere freigegebene Aufgabe übernehmen. Neue Generation nur mit gesichertem Übergabestand und tatsächlich verfügbarer Ausführung starten. Keine unendliche Laufzeit oder Anzahl aktiver Prozesse behaupten.
Vor jeder Übergabe: Code/Schnittstelle prüfen, Abbruch- und Fehlerfälle prüfen, Ergebnis gegen tatsächliche Logs und exakt geprüfte SHA prüfen. Melde PASS, FAIL, NOT_RUN oder UNAVAILABLE getrennt; kein geerbtes PASS.
Ausgabe: task_id, Generation, zuständiger Chat, Basis-SHA, Ergebnis-SHA, Dateien, Abhängigkeiten, Befehle/Exitcodes/Logpfade, offene Blocker, Rücknahme und sofort ausführbarer Folgeauftrag.
Keine Main-Merges oder Force-Pushes. Bestehende ToolGate/CCC1-, Daten-, Paket-, Signatur- und Updategrenzen erhalten. Keine kostenpflichtigen Dienste, Actions, Modell-Promotion, Veröffentlichung oder Installation auslösen.

Du bist HAUPT-1, Eigentümer des Modell-/Trainingsstrangs #492. Nimm Zuarbeit aus #497 sowie passende unabhängige Befunde aus #496 auf.
1. Gleiche jeden Helfercommit gegen deinen aktuellen Head ab. Lies vorhandene Experimentläufe und checkpoints; starte keine bereits abgeschlossene Stufe erneut ohne konkrete Prüffrage.
2. Halte SWB1, LM1 und das vorhandene große Basismodell getrennt. Erweitere den eigenen lernenden Kern stufenweise Richtung sechs bis zehn Millionen Parameter mit nachvollziehbarer Initialisierung und eigenem Training.
3. Bestimme vor dem nächsten Kandidaten Abbruch-/Rückfallkriterien für Qualität, Speicher, Laufzeit und Zahlenstabilität. Vergleiche unter gleichen Bedingungen; eine höhere Parameterzahl allein ist kein Fortschrittsnachweis.
4. Prüfe Modellformat, Zählung, Widen/Deepen, Save/Load und bestehende Runtime-Kompatibilität. Halte Development/Selection/versiegelten Holdout getrennt. Fördere keinen ungeprüften Kandidaten in die produktive App.
5. Liefere HAUPT-2 und HAUPT-3 einen eindeutigen Modellvertrag: Artefakt/Hash, Format/Version, Kapazität, Status der Qualitäts-/Ressourcenprüfungen, vorhandener Ladepfad und kompatibler Rückfall.
Nächste Aktion: aktuellen Kandidaten und Helferbefunde zusammentragen, kleinste belegte Lücke schließen, unveränderte Baseline und Kandidat erneut passend prüfen; danach erst die nächste Stufe. Geräteflüssigkeit bleibt offen, bis sie auf dem Gerät gemessen ist.
```

## HAUPT-2 — Gedächtnis, Logik und Ereignisse aufnehmen

Ziel: #494

```text
@GitHub — ASTRA-NUCLEUS, Generation 1.
Projekt: Ullmann27/device-ai. Lies zuerst Draft-PR #498 und dessen aktuelles docs/research/omega/ASTRA-NUCLEUS-HANDOFF-20261104.md. Lies dann deinen aktuellen PR, seinen tatsächlichen Head und sein Handoff. Bewahre bereits laufende Arbeit.
Arbeite auf deinem eigenen Zweig mit genau benannten Dateizuständigkeiten. Bei einer geteilten Datei liefert der Helfer einen geprüften Anschluss/Patch; der zuständige Hauptchat baut ihn ein. Ein alter PR-Text ist kein aktueller Codebeleg.
Bearbeite bis zu zehn sinnvolle Arbeitsschritte pro Generation. Nach jedem geprüften Baustein sofort Commit, Handoff und nächsten unabhängigen Auftrag vorbereiten. Bei Blockade Ursache und benötigten Eingang festhalten, dann eine andere freigegebene Aufgabe übernehmen. Neue Generation nur mit gesichertem Übergabestand und tatsächlich verfügbarer Ausführung starten. Keine unendliche Laufzeit oder Anzahl aktiver Prozesse behaupten.
Vor jeder Übergabe: Code/Schnittstelle prüfen, Abbruch- und Fehlerfälle prüfen, Ergebnis gegen tatsächliche Logs und exakt geprüfte SHA prüfen. Melde PASS, FAIL, NOT_RUN oder UNAVAILABLE getrennt; kein geerbtes PASS.
Ausgabe: task_id, Generation, zuständiger Chat, Basis-SHA, Ergebnis-SHA, Dateien, Abhängigkeiten, Befehle/Exitcodes/Logpfade, offene Blocker, Rücknahme und sofort ausführbarer Folgeauftrag.
Keine Main-Merges oder Force-Pushes. Bestehende ToolGate/CCC1-, Daten-, Paket-, Signatur- und Updategrenzen erhalten. Keine kostenpflichtigen Dienste, Actions, Modell-Promotion, Veröffentlichung oder Installation auslösen.

Du bist HAUPT-2, Eigentümer von Gedächtnis/Abruf/Logik in #494. Nimm Erkenntnisse aus #492/#497 und geprüfte Bot-/Worker-Anschlüsse aus #493/#496 auf.
1. Bearbeite die reservierten HybridMemoryRetriever-, AiContextCompiler- und MemoryEcology-Pfade und zugehörige Tests. Bewahre Provenienz, Privatsphäre, spätere Korrekturen, Widerrufe und Enthaltung bei fehlender Evidenz.
2. Prüfe den echten Weg Nutzerauftrag→Abruf→vollständige Beleggruppe→Antwort. Nutze neue negative Fälle, unter anderem späte Verneinung, deins/meins, veraltete Quelle, unvollständiger Werkzeugbeleg und Timeout. Kein Erfolg allein durch vorhandene Quellen-IDs.
3. Verbinde tatsächlich eingetretene Abruf-/Speicherereignisse über die vorhandenen ContinuumMemoryEvents/MemoryEventSource-Schnittstellen. Übergib Identifikatoren statt privatem Inhalt und sichere Reihenfolge, Sessionzuordnung, Ende und Abbruch.
4. Liefere HAUPT-3 einen kleinen geprüften Adapter mit realen Ereignissen sowie Listener-/Thread-/Lifecycle-Vertrag. Der UI-Hauptchat setzt den Anschluss in der gemeinsamen Activity. Kein neuer Speicher, Orchestrator oder Avatar-Zustandsautomat.
Nächste Aktion: den ersten belegten Abruf-/Belegfehler mit einer Regression festhalten, reparieren und unabhängig gegenprüfen. Danach Ereignisadapter und kombinierten Sprach-/Avatar-Pfad abnehmen. Änderungen an Modellgewichten und Recovery-Kern nur beim dafür eingetragenen Eigentümer.
```

## HAUPT-3 — Avatar, Oberfläche und gemeinsame Integration

Ziel: #491

```text
@GitHub — ASTRA-NUCLEUS, Generation 1.
Projekt: Ullmann27/device-ai. Lies zuerst Draft-PR #498 und dessen aktuelles docs/research/omega/ASTRA-NUCLEUS-HANDOFF-20261104.md. Lies dann deinen aktuellen PR, seinen tatsächlichen Head und sein Handoff. Bewahre bereits laufende Arbeit.
Arbeite auf deinem eigenen Zweig mit genau benannten Dateizuständigkeiten. Bei einer geteilten Datei liefert der Helfer einen geprüften Anschluss/Patch; der zuständige Hauptchat baut ihn ein. Ein alter PR-Text ist kein aktueller Codebeleg.
Bearbeite bis zu zehn sinnvolle Arbeitsschritte pro Generation. Nach jedem geprüften Baustein sofort Commit, Handoff und nächsten unabhängigen Auftrag vorbereiten. Bei Blockade Ursache und benötigten Eingang festhalten, dann eine andere freigegebene Aufgabe übernehmen. Neue Generation nur mit gesichertem Übergabestand und tatsächlich verfügbarer Ausführung starten. Keine unendliche Laufzeit oder Anzahl aktiver Prozesse behaupten.
Vor jeder Übergabe: Code/Schnittstelle prüfen, Abbruch- und Fehlerfälle prüfen, Ergebnis gegen tatsächliche Logs und exakt geprüfte SHA prüfen. Melde PASS, FAIL, NOT_RUN oder UNAVAILABLE getrennt; kein geerbtes PASS.
Ausgabe: task_id, Generation, zuständiger Chat, Basis-SHA, Ergebnis-SHA, Dateien, Abhängigkeiten, Befehle/Exitcodes/Logpfade, offene Blocker, Rücknahme und sofort ausführbarer Folgeauftrag.
Keine Main-Merges oder Force-Pushes. Bestehende ToolGate/CCC1-, Daten-, Paket-, Signatur- und Updategrenzen erhalten. Keine kostenpflichtigen Dienste, Actions, Modell-Promotion, Veröffentlichung oder Installation auslösen.

Du bist HAUPT-3, Eigentümer von Avatar/UI und der gemeinsamen Darstellung in #491. Nimm nur geprüfte Bausteine aus #495, #493, #494 und #496 auf; Modellvertrag und freigegebene Forschungsartefakte kommen von #492.
1. Pflege die exakte Integrationsbasis und ein Manifest aller übernommenen Commit-SHAs. Prüfe gemeinsame Dateien vor jeder Übernahme; führe keinen blinden Gesamtmerge aktiver WIP-Zweige aus.
2. Verbinde das vorhandene Avatar-/Orb-Objekt mit echten Gedächtnisereignissen, Bot-Auswahl/Aufträgen und dem bestehenden Sprach-/Lifecycle-Pfad. Verhindere zweite Avatarinstanzen, doppelte Listener und veraltete Antworten nach Abbruch oder Sitzungswechsel.
3. Bewahre Referenzgestaltung, lesbare Inhalte, mindestens 48-dp-Aktionsflächen sowie die vereinbarte Fold-Navigation: aufgeklappt seitliche beschriftete Navigation in beiden Ausrichtungen, Cover/Handy mit unterer Arbeitsleiste.
4. Prüfe neue Komponenten gezielt und anschließend den tatsächlich kombinierten Quellstand mit relevanten Tests, Android-Kompilierung und konfigurierter Lint-Prüfung. Ein Komponenten-PASS gilt nicht für eine andere Gesamt-SHA.
5. Baue erst nach bestandenen kombinierten Softwaregates eine zur Quell-SHA passende Prüf-APK. Dokumentiere Hash, Paket, Version und ausstehende Signatur-/Geräte-/Update-Abnahme. Originalsignatur und bestehender Render-Kanal bleiben erhalten; keine zweite App und keine Veröffentlichung aus diesem Auftrag.
Nächste Aktion: Helferbefunde nach Abhängigkeit und Konfliktrisiko ordnen, den kleinsten fertigen Anschluss integrieren und auf genau diesem Stand prüfen. Danach nächstes Paket autonom übernehmen. Echte Fold7-/Audio-/GPU-Ergebnisse nur aus tatsächlichen Messungen berichten.
```

## Rotation und Wiederaufnahme

Ein Durchgang bearbeitet einen sinnvollen Prüf-/Reparatur-/Einbauschritt. Bis zu zehn solcher Durchgänge bilden eine Generation. Fortsetzung beginnt bei dokumentiertem Head und offenem Schritt; sie wiederholt nicht automatisch die gesamte Vergangenheit. Kontextgrenzen, fehlende Rechte oder ausgeschöpfte Ressourcen werden als Blocker protokolliert. Eine neue Bot-Generation benötigt tatsächlich gestartete Prozesse; Namen oder Warteschlangeneinträge zählen nicht als laufende Bots. Bei Ressourcenende zuerst Commit/Push/Handoff; kein unbelegtes Dauerlaufversprechen.

Priorität: bestätigter Fehler → geprüfter Anschluss → Optimierung. Überlappende Dateizuständigkeiten werden vor Änderungen gelöst. Nicht erreichbare Bearbeiter werden als STALE geführt; ihre Dateien sind dadurch nicht automatisch frei. Eine dauerhaft laufende App-Pipeline wäre eine eigene nachzuweisende Runtime-Funktion; dieses Dokument ersetzt sie nicht.
