# Continuum 0.3.20 · Astra-Abschluss und übernehmbare Freigabevorlage

24.10.2026 · Nur Ullmann27/device-ai. Abschluss auf ausdrückliche Besitzeranweisung bei gemeldeten20% Restlimit: keine weiteren Implementierungsstränge. **Code vollständig im Repository, Besitzer-APK noch nicht signiert.**

## Stand und Zugriff

- Ausgangspunkt PR116, Remote-HEAD bestätigt: `3d7326d047ee91a91105f6f3e352ada613b6ff87`.
- Neuer, unmittelbar gepushter Branch: `astra/continuum-0320-visual-integration-20261024`.
- Draft-PR: [#117](https://github.com/Ullmann27/device-ai/pull/117), gegen `sol/continuum-0320-integration-20261024`.
- **Gemeinsamer getesteter Quell-/Testcommit: `d7429145ad5dff64364ed47f9ceb0c07a5a8ed85`.** Nachfolgende Commits enthalten nur Dokumentation/Belege. Aktuellen Dokumentations-HEAD über `git ls-remote` oder PR117 lesen; keine selbstbezügliche SHA in dieser Datei.
- Funktionsschritte jeweils sofort gepusht: `303ccfeb` Hologramm/Thermal; `5483cbb1` Telemetrie/Lifecycle; `d7429145` Importidentität.
- Keine Main-Änderung, kein Force-Push/Rebase, keine gelöschten Zweige, Worktree-Sperren oder exklusiven Abhängigkeiten. Jeder Repository-Mitwirkende kann den Branch unmittelbar übernehmen. Keine privaten Patches oder Secrets.

## Tatsächlich neu

**Hologramm:** `ContinuumHologramRenderer.drawEnergyCore` nutzt dieselbe `ContinuumOrbitalRing`-Projektion mit zwei wiederverwendeten Puffern (.35/.32 Kugelradien, gegenläufig1.35/−.95). Rück-/Vorderhälften werden vor/nach dem Unendlichkeitszeichen gezeichnet. Geordnete Lichtpakete, dezenter Energiepuls und wandernder innerer Reflex machen den Mittelpunkt lebendig. Keine zweite Renderarchitektur oder Modell-/Telemetriesimulation. Bestehende äußere Ringe, Plexus, Schweben, Navy/Cyan/Violett, Text und State-Crossfades bleiben erhalten. Vorherbilder: `docs/evidence/sol-0320-20261024/frames`; neue Bilder: `docs/evidence/astra-0320-20261024/frames`.

**Temperatur:** `ContinuumHologramView` registriert ab API29 einen PowerManager-Thermallistener beim Attach und entfernt ihn beim Detach. Moderate2 →32ms Mindestabstand, Severe3+ →Ruhebild, Abkühlung →derselbe Framecallback. Normal8ms positiver Abstand plus Android-Vsync; keine gemessene60-FPS-Zusage. `onPause`/`onResume`, Sichtbarkeit, Systemanimationsfreigabe und Energiesparen bleiben maßgeblich. Langdruck erklärt Temperaturpause. Listeneranschluss kompiliert/lintgeprüft; Statusweitergabe unter Robolectric28 geprüft, kein reales Temperatursignal gemessen.

**Messwertringe:** `ContinuumSystemGaugesView` interpoliert450ms nur zwischen bekannten echten Prozentwerten. Zahlen/TalkBack zeigen stets den neuesten tatsächlichen Messwert, nur die Ringgeometrie gleitet. Unbekannt→bekannt und bekannt→unbekannt springen sofort ehrlich auf Messwert/Strich; GPU bleibt unbekannt. Aktualisierung während eines Übergangs startet am aktuellen Bildzustand. Pause/Detach/Unsichtbarkeit stoppen und setzen die korrekte Endmessung. Keine neue Sensorabfrage oder Dauerschleife.

**Dashboard/Lifecycle:** `ContinuumHomeView` erhält explizit Activity-Vordergrund. Licht auf Lernhistorie, Modellqualität und Systemkurve erscheint höchstens900ms nach einer tatsächlichen Änderung der zugehörigen Daten. Unveränderte Fünfsekunden-Refreshes starten keine Animation. Navigation/Rail haben keine endlose dekorative Pulseschleife mehr; ihre vorhandenen Auswahl-/Touchzustände bleiben erhalten. Ambient-Glas und Scanlines bleiben hochwertig statisch. `AgentActivity` übergibt die Vordergrundlage auch beim Fold-Neuaufbau und an offene Hardwareanzeigen. Keine Eingabe-/Speichermigration.

**Modellimport:** `LocalModelCatalog.import` weist falsche SHA weiterhin vor nativer Inspektion/Übernahme ab, nennt jetzt erwarteten Hash, tatsächlichen Hash und gelesene Bytezahl. Negativtest importiert ein altes korrektes Modell, verwirft einen neuen falschen Kandidaten und beweist Erhalt des alten Datensatzes/Bytes sowie Entfernung des temporären Kandidaten. `AgentActivity.localImportError` trennt abgewiesene Kandidaten einschließlich Importtimeout vom Laufzeitfehler. Die Modellkarte zeigt volle geladene Katalog-ID/Version/SHA/Bytes; ein Fehler behauptet nicht mehr pauschal, das alte Modell sei unbrauchbar. `runLocalSmokeTest` benennt keine festverdrahtete Qwen-Familie mehr, bindet seine Ausgabe an die vor/nach Inferenz verglichene geladene Identität. Keine zusätzliche Inferenzsitzung. Ein erfolgreicher alter Lauf trotz fehlgeschlagenem Neuimport ist damit als Codepfad nachvollziehbar; **die konkrete Ursache und tatsächliche Modellidentität auf Heinz’ Fold7 sind weiterhin unbekannt**. IDs/Versionen sind beim Import vergebene Kataloglabels, kein Beweis des Hersteller-Modellnamens. Die Importwarnung ist Activity-lokal und kein dauerhaftes forensisches Importjournal.

## Prüfungen und Grenzen

Drei Runden: (1) aktuelle Übergabe/Verträge/Originalpfade, (2) gezielte RED→Reparatur→GREEN, (3) gemeinsamer eingefrorener Quellstand mit vollständiger SDK-/Node-/Backendregression und erneuter Gateprüfung. Keine Wiederholung der alten Sol-Suite als neue eigene Leistung.

- SDK/JUnit **975/975**, davon **35 Robolectric**, 0 Fehler/Skips, Exit0;195 XML.
- Android-Instrumentation kompiliert; Lint **0 Fehler/0 Warnungen** im bestehenden NewApi-Gate.
- Backend **60/60**, Syntaxcheck Exit0.
- Node Gesamt:1111 entdeckt,1105 bestanden,0 Fehler,6 Kotlin-PATH-Skips. Nur die drei betroffenen Dateien mit vorhandener Kotlin-Toolchain nachgeprüft:11/11,0 Fehler/Skips, Exit0. Damit1111 eindeutige Node-Fälle über beide Läufe ausgeführt; kein behaupteter einzelner skipfreier Gesamtlauf. Ergebnis in `node-kotlin-completion.txt`. Details in [COMMANDS.md](evidence/astra-0320-20261024/COMMANDS.md).
- CRE1 Inferenz7 Fälle, SGD526336 Gewichte; SWB1/SWB2 und Backlog-Roundtrip Exit0.
- Zeitframes0/1/2s: zentrale Glasscheibe und Außenband getrennt geprüft. Zwischen1/2s: Telefon3158 zentrale und12596 äußere geänderte Pixel; Fold1999/12944. [Exakte Masken/Zählung](evidence/astra-0320-20261024/pixel-differences.json).
- Native Bilder visuell geprüft. Keine physische Fold7-Abnahme, GPU-/60-FPS-/Energie-/Thermalmessung. `adb devices -l` leer. Die installierte0.3.19 wurde nicht verändert; Branchcode wird erst durch autorisiertes Update sichtbar.
- Keine Hauptbereich-Neugestaltung, neuen Navigationseffekte, neue Lernkurven-Erfindungen oder neue Fehlerzustandsanimation. Bestehende Zustandsquellen für Hören/Denken/Sprechen/Forschung/Lernen unverändert genutzt.

Alle geänderten Quell-/Testpfade: [changed-files.txt](evidence/astra-0320-20261024/changed-files.txt). Rohlogs und195 XML liegen im selben Verzeichnis. Frühere negative/abgebrochene Versuche sind ausdrücklich erhalten und nicht als bestanden gezählt.

## KI- und Datenstand

SWB1/CRE1/CDX1, Ledger, Wissensarchiv, Chat-ID-Isolation, M4/M5/CCC1/Termux unverändert und durch neue Gesamtsuite regressionsgeprüft. Keine Gewichte verändert, kein Training persönlicher Erinnerungen, kein zweites Gedächtnis oder Worker. CTX3 weiterhin OFF/SHADOW. LM1 bleibt Forschungsruntime mit dokumentierter unzureichender freier Sprachqualität. **Kein neuer LM1-Checkpoint, unabhängiger Sprachqualitätsgewinn oder Curriculum-v2-Fortschritt behauptet.** Die früher belegte autonome CRE1-Stufe aus PR116 bleibt erhalten; in dieser Abschlussrunde nur bestehende Regressionstests, kein neuer produktiver Lernanschluss. Kostenpflichtige APIs nicht erforderlich.

## Exakte APK-/Freigabeblockade

Ziel0.3.20/Code21, unverändertes Paket `at.heinz.deviceai.mentorpaired`, erforderliches Zertifikat `2b05fa9de378fb6c6c5376143774a573897dc9c9151305f1c44010275c76a9cc`.

**NICHT APK-freigegeben, NICHT gebaut, NICHT signiert, NICHT veröffentlicht/installiert.** Grund: keine ausdrückliche Besitzerfreigabe für diesen exakten neuen Quellstand/dieses Manifest; Dispatcher besitzt weiterhin kein attestiertes v21-Rezept (echter Exit69). Privater Render-Signierer nicht verändert/aufgerufen. Keine alten Pins überschrieben und kein Ersatzschlüssel eingesetzt.

Vorlage zur Prüfung:
- Quellcommit `d7429145ad5dff64364ed47f9ceb0c07a5a8ed85`.
- [v21-source-proposal.json](evidence/astra-0320-20261024/v21-source-proposal.json):245 getrackte Hauptquellen/Assets/Gradle-Eingaben und historische Signierrezepte, jeweils Gitblob und SHA256, Vergleich zur signierten0.3.19. **Vorschlag, keine Freigabe**. Vollständiger Gitcommit bindet zusätzlich Tests/Skripte.
- Manifest-SHA256 `134e847b8f2fad2f8131cdf3e962679eb1630ed12801973ad0f6cbb0799e177d`.
- [release-blockers.json](evidence/astra-0320-20261024/release-blockers.json): **10/34** historische v20-Attestationen abweichend, **6** geänderte/neue Hauptquellen nicht im alten Manifest. Sämtliche Originalattestationen unangetastet.
- Nächster Releaseentwickler: separates v21-Rezept/Dispatcher-Option samt negativen Tests für fehlende/falsche exakte Besitzerfreigabe, falsches Zertifikat, falsches Paket/Version, fehlenden Testbeleg und Rollback anlegen. Nicht einfach alte v20-Werte ersetzen. Neues Rezept ist selbst prüfpflichtig; danach ggf. neuen Quell-/Buildercommit eindeutig festlegen.

Vorherige von Sol unabhängig geprüfte0.3.19 bleibt Referenz: Quellcommit `afd8ea3f0ff6e73c646b71b60d1157977abbcd62`, Code20, APK-SHA256 `1f486e96439708ff96e7f427276217b835e15f7a029a4f72cea8178f4bf82a19`. Keine erneute Downloadprüfung in dieser Runde. **Kein neuer0.3.20-Downloadlink vorhanden.** Datenbewahrender Android-Downgrade21→20 weiterhin nicht belegt.

## Bedienung und Fold7-Abnahme

Nach späterem freigegebenen Update: Start öffnet die Kernanimation automatisch; Langdruck zeigt Blockierungsgrund/Framephase. System → Hardware zeigt gleitende echte Ringe. Historische Diagramme markieren nur neue Daten kurz. Modellverwaltung zeigt vollständige Katalogidentität und getrennte Importfehler; direkter Modelltest nennt dieselbe Identität.

Geräteabnahme: konkrete APK/SHA/Version zuerst erfassen, dann Video oder Zeitframes0/1/2s mit zentralem Ausschnitt und Außenringen; geschlossen/offen/Multiwindow; Home→Chat→Home; Pause→Resume; Eingabetext bei Foldwechsel; reduzierte Animationen/Energiesparen; Frametiming und Temperaturprotokoll. Bei fehlendem Gerätezugang `DEVICE_VERIFIED=false` lassen. Kein manuelles Überhitzen zur Prüfung.

## Kopierbarer Fortsetzungsauftrag

```text
Arbeite nur an Ullmann27/device-ai. Lies den tatsächlichen HEAD von PR117,
astra/continuum-0320-visual-integration-20261024, und diese Übergabe.
Quell-/Testcheckpoint d7429145ad5dff64364ed47f9ceb0c07a5a8ed85; spätere
Commits nur Belege. Neuen Nachfolgerbranch erstellen/pushen; main unberührt.
Nicht nochmals Sols alte Module kopieren. Hologramm/Kern/Thermalschutz,
finite echte Telemetrieanimation und Import-/Laufzeittrennung sind vorhanden.
Prüfbelege: docs/evidence/astra-0320-20261024/COMMANDS.md und summary.json.
Vor Release zuerst release-blockers.json und v21-source-proposal.json prüfen.
0.3.20/21 NICHT signiert: exakte Owner-Quellfreigabe und geprüftes v21-Rezept
fehlen. Alte v19/v20-Pins nicht ersetzen; separates Rezept samt Negativtests,
Owner-SHA-Bindung, bestehendes Zertifikat, Rollback und Testbeleg überprüfen.
Nach tatsächlicher Zustimmung nur den vorhandenen privaten Render-Signierer
verwenden und APK-Paket/21/0.3.20/Zertifikat/Hash/HTTPS-Download unabhängig prüfen.
Kein Ersatzschlüssel, keine automatische Installation, kein behaupteter Fold7-Test.
Bei UI-Weiterarbeit native Frames mit zentralem/äußerem Pixelmaskenvergleich
beibehalten; Fold7-Performance und API29+-Thermallistener real abnehmen.
Beim SHA-Problem geladene Katalog-ID/Version/Hash/Bytes und erwartete/tatsächliche
Importdatei vergleichen. Alter geladener Kern kann trotz abgewiesenem Neuimport
arbeiten. Kataloglabel ist kein Herstelleridentitätsbeweis.
LM1/Curriculum-v2 bleiben eigene nächste Arbeit: frozen Baseline und unabhängiger
deutscher Mehrturn-/Negations-/Korrekturtest vor Änderungen; keine Promotion
auf bloßen Trainingsverlust. CRE1/SWB1-Fakten und CTX3-OFF/SHADOW bewahren.
Jeden überprüften Schritt committen/pushen und Übergabe aktualisieren.
```
