# Continuum 0.3.20 · Sol → GPT-6/Astra

Stand: 24.10.2026. Ausschließlich `Ullmann27/device-ai`. **Geprüfte, gepushte Integration; keine signierte0.3.20 und keine Installation.**

## Übernahmestand

- Auftrag/Basis: PR[#115](https://github.com/Ullmann27/device-ai/pull/115), tatsächlicher GitHub-HEAD `9794761067e6bd1f8f2188426288f8134de53e4e` (zu Beginn und nach den Reparaturen erneut geprüft).
- Fortsetzung: `sol/continuum-0320-integration-20261024`, Draft-PR[#116](https://github.com/Ullmann27/device-ai/pull/116) gegen den bestehenden PR115-Zweig `chatgpt/continuum-autonomous-dialogue-research-20261024`.
- **Gemeinsamer tatsächlich getesteter Quell-/Testcommit: `074fa80729a088644769df300c23b2e093e56553`.** Node, Backend, SDK/JUnit und Paritätsgates liefen auf genau diesem HEAD. Kein Release wurde daraus signiert.
- Vollständig gesicherter Belegcommit vor dieser Übergabe: `976836d73c0bd34bb93d427f237c78f997866337`. Nachfolgende Übergabe-/Belegcommits ändern keine App-, Gradle-, Test- oder Builderquellen gegenüber074fa807. Den jeweils letzten Dokumentations-HEAD über PR116 beziehungsweise `git ls-remote` feststellen; die Datei kann ihren eigenen späteren Commit-Hash nicht enthalten.
- Frühere, während des Cloud-Ausfalls liegen gebliebene Vorarbeiten sind vollständig auf `sol/continuum-hologram-intelligence-20261024` bei `9291d2bb3bb05d85698e6e7b812d4623fd32b5cb` gesichert. Dortige Hologrammtests waren noch rot. Diesen älteren Renderer-/View-Stand **nicht** über die neueren PR113/114-Reparaturen kopieren.
- Keine Force-Pushes, Rebases, gelöschten Zweige, privaten Patches, Main-Merges oder exklusiven Zugangsvoraussetzungen. Main blieb `190f5eabaa60fc59292b2b80d3086aafd7743a80`. Maintainer dürfen PR116 weiterbearbeiten.

## Drei Kontrollgänge und tatsächlich neue Anschlüsse

**1. Rekonstruktion:** PR75,82,84,92,93,95,99,108–115 direkt abgefragt und gefetcht; gemeinsame Vorfahren und vollständige Quellpfade verglichen. [Integrationsmatrix](CONTINUUM-0320-INTEGRATION-MATRIX-20261024.md), [exakte Metadaten/Dateivergleiche](evidence/sol-0320-20261024/pr-metadata.json), [ModuleConnectionManifest](evidence/sol-0320-20261024/ModuleConnectionManifest.json). Aus75–114 fehlte kein Quellpfad im untersuchten App-/Test-/Skript-/Backendbereich. Deshalb keine redundanten Altbranch-Merges. Vorhandene Implementierung und tatsächliche Android-Erreichbarkeit bleiben getrennte Befunde.

**2. Reparatur/Integration:**

1. **Gesprächs-ID bis in den bestehenden Dialogspeicher durchgezogen.** `ContinuumLearningCore.replyFromMemory` und `replyWithEvidenceInConversation` führen die Identität jetzt an `ContinuumMemoryDialogue.replyInConversation` weiter. Zuvor begrenzte sie zwar CTX3 und den lokalen Modellprompt, aber nicht die deterministische Erinnerungssuche. Neue Chats konnten deshalb auf „und wie?“ eine fremde bestätigte Erinnerung ausgeben, bevor überhaupt Modellrouting stattfand. Eine verspätete Reset-ID konnte außerdem eine neuere Unterhaltung löschen.
2. **Ein bestehender, atomarer Snapshot bleibt erhalten.** `learning-core-v2/dialogue-v1.bin` unterstützt zusätzlich einen checksummengebundenen v2-Header mit validierter ID. Keine zweite Gedächtnisdatei, kein zweiter Dialogmanager, kein separater Kontextworker. v1 bleibt für Legacy-Aufrufe les-/schreibbar; explizite IDs übernehmen ungebundene v1-Historie niemals. Andere IDs erhalten einen frischen Kontext. A→B→A stellt konservativ eine Rückfrage, statt B zu übernehmen; dies ist kein Archiv beliebig vieler Sitzungen. Die eigentliche UI-Dialoghistorie, bestätigte Fakten, Quellen und Gewichte werden nicht migriert oder gelöscht.
3. **Stale-Reset-Grenze ergänzt.** Die vorhandene Lifecycle-Prüfung unter `CORE_LOCK` bleibt bestehen; zusätzlich kann ein alter expliziter Chat den Snapshot eines anderen aktuellen Chats nicht löschen. Ungültige IDs werden vor Dialog-/Modelländerungen abgewiesen. Widerruf wird bei jeder Antwort anhand aktueller Ledger-Einträge berücksichtigt. Beschädigte Dialogbytes werden wie bisher quarantänisiert.
4. **Autonome Dialogforschung aus115 wirklich ausgeführt und abgesichert.** Das vorhandene Curriculum, Opt-in, JobScheduler-Anschluss und CRE1-Kandidatengate wurden übernommen, kompiliert und getestet. Ein dangling Symlink auf den Stufencursor konnte wegen `exists()` vor der Linkprüfung den Stand fälschlich auf0 setzen. Die Prüfung läuft jetzt zuerst. Unterbrechung nach dauerhafter Reservierung verbraucht genau diese Stufe und veröffentlicht keine unvollständigen Gewichte; ein beschädigter Cursor bleibt erhalten und blockiert Training.
5. **Wissensarchiv-Zähler funktional geprüft.** Die bereits in115 eingebaute Korrektur zählt tatsächlich `ContinuumKnowledgeArchive`, nicht die Zahl der Webjournal-Notizen. Ein neuer echter Android-Collector-Test schreibt drei öffentliche Quellenrevisionen, prüft die zunächst gecachten2 und nach31s die3, und vergleicht Besitzerfakten sowie SWB1-Bytes unverändert. Der alte statische Test, der den falschen Journalzähler verlangte, wurde korrigiert.
6. **Hologramm durch zeitgesteuerte Pixel geprüft.** Der neuere Renderer/Frameclock aus113/114 blieb erhalten. Der zusätzliche Test lässt den echten View-Callback bei virtuellen Zeitpunkten0/1/2s laufen, zeichnet drei native Android-Canvas-Bitmaps je Formfaktor und prüft Bildänderungen, Pause und mehrfachen Resume. Keine injizierte Phase in diesem neuen Test.
7. **Version vorbereitet:** Nur der vorhandene Besitzer-Updatezweig in `app/build.gradle.kts` steht jetzt auf0.3.20/21. Paket-/Signing-Konfiguration unverändert. Historische v19/v20-Rezepte und sämtliche attestierten Fingerabdrücke wurden nicht geändert; v21 ist deshalb ausdrücklich gesperrt.

**3. Gesamtregression:** Auf074fa807 vollständige Node-/Backend-/SDK-/Lint-Suite und frische Node/Kotlin-Paritätsdaten ausgeführt. Zwei veraltete Testannahmen wurden vorher korrigiert: ein Tamper-Test muss beide Proben ausdrücklich derselben Chat-ID zuordnen, wenn er zwei Verlaufseinträge erwartet; ein Quelltest darf einen Javadoc-Text nicht als feste Methodengrenze verwenden. Keine produktiven Schutzprüfungen gelockert. Danach keine neue Featurearbeit oder Kontroll-Endlosschleife. Sämtliche geänderten Dateien stehen in [changed-files.txt](evidence/sol-0320-20261024/changed-files.txt).

## Tatsächliche Intelligenzarchitektur und Grenzen

Hauptchat → vorhandene Eingangs-/Intentsteuerung → `runContinuumDialogue` → synchronisierte bestätigte Besitzerfakten/reviewte Quellen → `CognitiveDecisionBridge` → `ContinuumCognitiveRuntime` → ein gesperrter SWB1-/Erfahrungssnapshot → **ID-gebundene** Kontextauflösung/Abruf → zulässige Belege/CRE1-Beobachter → deterministische belegte Antwort. Nur eine unbeantwortete Frage erreicht den bestehenden `LocalFirstPolicy.afterMemory`-/`runLocal`-/LiteRT-LM-Pfad. Modelltext wird nicht automatisch als Erinnerung gespeichert.

Der `null`-Reasoning-Adapter im Hauptchat wurde **bewusst beibehalten**: Der Bridge-Adapter formuliert belegte Besitzerfakten um; bloße Entity-/Zahlenanker beweisen keine semantisch korrekte Paraphrase. Das bestehende Gate erlaubt nur exakte Belegwiederholung. Die nachgelagerte lokale Gesprächs-/Reasoningroute ist bereits vorhanden. Ein blindes Einsetzen eines weiteren Adapters wäre weder ein belegter Intelligenzgewinn noch erforderlich, um unbekannte Fragen lokal zu beantworten. Kein zusätzlicher Inferenzpfad oder konkurrierende Sitzung eingeführt.

- **SWB1:** bestehender produktiver Abruf; keine Gewichtsänderung durch diese Reparaturen. Kontextisolation, Belegidentität, Widerruf und Parität geprüft.
- **CRE1:** bestehender Shadow-Beobachter und unabhängiges, hashgebundenes Promotiongate erhalten. Automatische Dialoge trainieren nur das Forschungscheckpoint. Kein neues Produktmanifest oder automatische Reranking-Freigabe.
- **CRE1-Checkpointgrenze:** Ein neuer Forschungscheckpoint erhält einen neuen Hash; ein altes hashgebundenes Promotionsmanifest autorisiert ihn nicht. Eine bereits separat freigegebene alte CRE1-Rangfolge wird dadurch nicht automatisch auf neue Gewichte übertragen. Vor einem solchen Gerätefall Backup/Modellwechsel und unabhängige erneute Freigabe prüfen; keine Manifest-Autokorrektur.
- **CDX1:** vorhandenes isoliertes Decoder-/Curriculum-/Offlinejob-Labor erhalten und durch Gesamtsuite/Parität geprüft. Kein neuer produktiver Decoderanschluss, kein Training persönlicher Erinnerungen.
- **LM1:** Transformer/minGRU, BPE, Checkpoints und `LmRuntime.generate` vorhanden, real kompiliert und getestet. **Nicht neu trainiert oder in den Hauptchat aktiviert.** Die vorhandene unabhängige Studie dokumentiert geringe Wortvorhersage, Wiederholungsschleifen, Verlust gegen KN-7 und fehlende Anweisungsverarbeitung. Diese Qualitätssperre bleibt real; Quellpräsenz ist kein brauchbarer Gesprächskern.
- **TinyNN/NeuralDialogue:** vorhandene Neuron-/Übungswege erhalten; keine allgemeine Sprachkompetenzbehauptung.
- **CTX3:** weiterhin OFF/SHADOW; negatives unabhängiges TEST3 wird nicht durch andere Tests umgedeutet. I1/I2/NF bleiben Forschung.
- **Wissensarchiv:** korrekte Anzeige und vorhandener gekennzeichneter öffentlicher Quellenabruf. Kein neues semantisches Archiv-Retrieval; Quellenbelege, persönliche bestätigte Erinnerungen, synthetische Übungslabels und unbestätigte Modellantworten bleiben getrennt.
- **CCC1/M4/M5/Termux:** bestehende Hauptchat→Plan→Besitzerkontrolle→Builder→Job/Receipt-/Reparaturkette erhalten. SDK-/Node-/Lifecycle-/Abbruchprüfungen erneut grün. Kein aktueller physischer BUILD-/REPAIR-Rückkanal oder neues APK-Ergebnis in dieser Sitzung ausgeführt. Kein zweiter Worker oder Reparaturmechanismus.
- **Kosten:** kein kostenpflichtiger KI-Dienst als neue Betriebsabhängigkeit; die Änderungen und synthetische Forschung funktionieren lokal.

## Vollständiger autonomer Forschungsdurchlauf

[Reproduzierbares Prüfskript](evidence/sol-0320-20261024/auto-dialogue-probe.kts), [echtes Ergebnis](evidence/sol-0320-20261024/auto-dialogue-probe.txt): frisches temporäres Verzeichnis, vorhandenes Curriculum Stufe1,32 Schritte, nicht unterbrochen, Forschungskandidat akzeptiert. Trainverlust `0.8890785011264535 → 0.8779996561023329`; getrenntes Audit `0.8044421427489398 → 0.8013026808202466`. SWB1 und Besitzerfakten unverändert; Cursor1; keine Produktpromotion. Gemessene302ms sind ein einzelner Hostlauf, kein Android-Latenz-/Thermalbenchmark. Ein zweiter frischer Lauf lieferte dieselben Verluste und denselben Kandidatenhash.

Das ist ein echter lokaler Generieren→Trainieren→Auditieren→Akzeptieren/Verwerfen-Pfad. Es beweist **keine allgemeine Gesprächsverbesserung**. V1 übt vier feste, mechanisch beschriftete Roboterfamilien (Ort/Farbe/Werkzeug/Energie),16 Training +8 neue/8 feste Auditbeispiele je Stufe, maximal64 Stufen. Negation, Korrektur, freie Planung und anspruchsvolle Schlussfolgerung sind noch keine validierten Curriculumfamilien. Wiederverwendete Schutzfälle ersetzen keinen neuen unabhängigen terminalen Generalisierungstest.

## Hologramm: Mechanismus und tatsächlicher Nachweis

Bestehend aus113/114: `AgentActivity` übergibt Vordergrund/Resume/Pause/Destroy sowie den Zustand beim Fold-Neuaufbau. `ContinuumHologramView` besitzt genau einen geplanten Framecallback; dieser aktualisiert Phase/Energie und invalidiert die sichtbare View. Der Renderer verwendet dieselben orbitalen Bahnen mit unterschiedlichen Geschwindigkeiten/Richtungen, sichtbare Sinus-Schwebebewegung, räumliche Projektion und phaseabhängiges Licht. Sichtbarkeit wird live gelesen, nicht aus einem möglicherweise veralteten Aggregatflag. Android-Animationsfreigabe, In-App-Abschaltung und Energiesparen werden respektiert; detach/pause entfernen den Callback, Resume startet ihn kontrolliert neu. Das bestehende dunkelblaue Cyan-/Violettdesign wurde nicht ersetzt.

Neue Nachweise: [Frame0](evidence/sol-0320-20261024/frames/orb-412x307-0.png), [Frame1](evidence/sol-0320-20261024/frames/orb-412x307-1.png), [Frame2](evidence/sol-0320-20261024/frames/orb-412x307-2.png); Fold-Bilder im selben Ordner. [Pixelzählung](evidence/sol-0320-20261024/pixel-differences.json): zwischen1s/2s34.637 geänderte Telefonpixel und31.610 Foldpixel, davon13.976 beziehungsweise7.975 im zentralen Kugelausschnitt. Damit sind auch Kugeländerungen belegt, nicht nur ein Timer oder äußere Ringe.

**Grenze:** Robolectric/native Canvas mit virtueller Uhr ist kein realer Fold7-/GPU-/FPS-/Thermalnachweis. `adb devices -l` ist leer. Die aktuell installierte APK wird durch Branchänderungen nicht verändert. Auf dem Gerät werden neue Funktionen erst nach einem späteren autorisierten Update sichtbar; auch die zuvor gemeldete starre Darstellung muss dort mit der Langdruck-Frame-/Blockierungsdiagnose erneut geprüft werden.

## Tests und Testidentität

Alle finalen Befehle und Exitcodes: [COMMANDS.md](evidence/sol-0320-20261024/COMMANDS.md). Rohlogs,194 JUnit-XML, Lint-XML, Pixelbilder, Toolversionen, ältere RED-Belege: [Belegverzeichnis](evidence/sol-0320-20261024/summary.json).

| Prüfung auf074fa807 | Echtes Ergebnis |
|---|---|
| Node `.github/continuum/*.test.mjs scripts/*.test.mjs` |1109/1109,0 Fehler/Skips, Exit0 |
| Backend Syntax + Tests |60/60,0 Fehler/Skips, Exit0 |
| Echte Android-SDK-/JUnit-Suite |971/971, darin32 Robolectric,0 Fehler/Skips, Exit0 |
| Android-Testquellen | `compileDebugAndroidTestKotlin` erfolgreich; **keine Ausführung auf Gerät** |
| Android Lint |0 Fehler/0 Warnungen, Exit0 |
| CRE1 Node/Kotlin Inferenz und SGD |7 Fälle;526.336 Gewichte, max. Fehler3.47e-18; Exit0 |
| SWB1/SWB2 Node/Kotlin |4.113 Gewichte, SWB2 nach350 Runden/700 Updates; Exit0 |
| Backlog Node→Kotlin→Node |Revision14; Exit0 |
| Ganze synthetische Autodialogstufe |32 Schritte, getrennte Verluste verbessert; Exit0 |
| v21-Dispatcher |Absichtlich blockiert, Exit69 |

Frühere Fehlversuche sind erhalten und werden nicht als Grün ausgegeben: initialer Kontext-RED20 Tests/7 Fehler (sechs echte Isolationsfehler und eine zu strenge neue Promptannahme), Cursor-RED4/2 (ein echter Linkfehler und eine zu spezifische Exceptionannahme), erster voller SDK-Lauf971/1 Fehler/3 Paritäts-Skips, fehlgeschlagene frühere Node-Läufe1108/1109. Die finalen Läufe oben sind vollständig neu ausgeführt. Die alten940/1099-Belege vonec28d661 wurden nicht als Nachweis dieser Änderungen verwendet.

Toolchain: Linux x86_64, Node24.19.0, JDK17.0.21, Kotlin2.4.0, Gradle8.11.1, AGP8.10.0, SDK36. Echte SDK-/LiteRT-Abhängigkeiten; keine Kotlin-/Android-Stubs. Gültige Gradle-Up-to-date-Kompilate wurden wiederverwendet. `SDK_UNAVAILABLE` trifft hier nicht zu; **Gerät fehlt**. Kein Actions-Retry, kein Emulator-/Fold7-Lauf, keine APK-Assembly oder Besitzer-Signierung in dieser Runde.

## APK- und Datenerhaltungsstatus

Tatsächlich heruntergeladene und unabhängig mit `apksigner`, `aapt` und SHA256 geprüfte **vorherige** APK:

- Version0.3.19, Code20, Paket `at.heinz.deviceai.mentorpaired`, Quelle `afd8ea3f0ff6e73c646b71b60d1157977abbcd62`.
- APK-SHA256 `1f486e96439708ff96e7f427276217b835e15f7a029a4f72cea8178f4bf82a19`.
- Ein v2-Signierer, Zertifikat-SHA256 `2b05fa9de378fb6c6c5376143774a573897dc9c9151305f1c44010275c76a9cc`.
- Tatsächlich erfolgreicher Download: <https://device-ai-mentor-apk-preview.onrender.com/DeviceAI-Continuum-v0.3.19-1f486e96439708ff96e7f427276217b835e15f7a029a4f72cea8178f4bf82a19.apk>. Dies ist ausschließlich0.3.19/Rollback, **kein0.3.20-Download**.

**0.3.20/Code21: vorbereitet, nicht gebaut/signiert/veröffentlicht/installiert.** Der vorhandene Dispatcher hat noch kein v21-Rezept und lehnt21 mit Exit69 ab. Sieben der34 alten v20-Blobattestationen weichen am neuen Stand ab; vier zusätzlich geänderte/neue Main-Quellen sind dort nicht attestiert. Alle konkreten alten/neuen Hashes stehen in [release-blockers.json](evidence/sol-0320-20261024/release-blockers.json). Keine Fingerabdrücke, Freigabevariablen, Signatursperren oder privaten Render-Einstellungen wurden angepasst. Der Render-Connector hatte außerdem keinen bestätigten Workspace; es wurde keiner eigenmächtig ausgewählt. Öffentliche APK-Prüfung gelang unabhängig davon.

Vor Signierung: geprüfter v21-Pfad, ausdrückliche Freigabe des exakten neuen Quellstands/Manifests, ausschließlich vorhandener privater Besitzer-Signierer, gehashter0.3.19-Rollback, vollständige Tests und unabhängige Paket-/Signatur-/Downloadprüfung. Kein Ersatzschlüssel. APK-Bytes eines alten Rollbacks sind geprüft; ein datenbewahrender Android-Downgrade von21 auf20 ist damit nicht bewiesen.

Kompatibilität: v1-Dialogkontext bleibt lesbar. Ein Zurückgehen auf alte APK-Software kann einen neuen v2-Arbeitskontext nicht interpretieren und wird ihn über die alte Quarantäne behandeln; Besitzer-Ledger, Gewichte, Archive und UI-Verläufe sind davon getrennt. Keine Datenlöschungen oder Gewichts-/Checkpointmigrationen an echten Nutzerdaten. Alle Forschungsdateien der Hostprobe lagen temporär und wurden danach entfernt.

## Bedienung nach einem späteren autorisierten Update

- **Neuer Chat:** bestehende Aktion verwenden. Derselbe Chat behält seinen belegten Abrufkontext über Core-/Activity-Wiederherstellung; eine andere ID erhält keine fremden Bezüge. Bei unbekanntem Bezug fragt Continuum sicher nach. Kein neuer Knopf nötig.
- **Automatische Dialoge:** Lernzentrum → Experimentell → `AUTO-DIALOGE · HINTERGRUNDFORSCHUNG EIN/AUS` → ausdrücklich freigeben. StandardAUS. Derselbe vorhandene tägliche Offlinejob prüft Akku/Hitze/Speicher/15s-Budget; ab3,5s Restbudget höchstens eine CRE1-Stufe. Status zeigt versuchte Stufen und letzten Bericht. Aus schaltet weitere Starts ab; Android garantiert keinen exakten Ausführungszeitpunkt. Kein Cloudzwang und keine Trainingsprompts vom Besitzer erforderlich.
- **Wissensarchiv:** vorhandene öffentliche Quellen öffnen; der Dashboardzähler folgt spätestens nach Ablauf des30s-Cache. Bestätigte persönliche Erinnerungen sind ein anderer Speicherbereich.
- **Hologramm:** öffnet automatisch; bestehender Langdruck zeigt Frame-/Blockierungsdiagnose. Bei ausgeschalteten Animationen/Energiesparen ist die fehlende Bewegung absichtlich erklärbar.

## Nächste präzise Schritte für Astra

1. **Gerätemodell-Identität und SHA-Fehler:** `AgentActivity.importModel`, `refreshLocalModelUi`, `runLocalSmokeTest`, `localmodel/LocalModelCatalog.kt` und den tatsächlichen `loadedModel`-Datensatz gemeinsam prüfen. In dieser Sitzung sind Modellname/Version/Hash des Fold7 **unbekannt**. Ein fehlgeschlagener Neuimport kann neben einem älteren geladenen Modell bestehen; das ist eine zu prüfende Hypothese, keine hier belegte Ursache. Einen Geräteexport mit erwarteter/tatsächlicher Importdatei und geladener ID/Version/SHA/Bytezahl abgleichen; Hashprüfung niemals deaktivieren. Regression: falscher neuer SHA wird abgewiesen, zuvor geladenes Modell behält Identität, UI erklärt beide Zustände korrekt.
2. **LM1 gezielt weiterentwickeln:** `learning/lm/LmRuntime.kt`, `LmModel.kt`, `LmBpeTokenizer.kt`, `LmCheckpoint.kt`; Tests `learning/lm/LmRuntimeTest.kt`, `LmStep7Test.kt`, Dokument `CONTINUUM-LM1-OPUS-20261022.md`. Zuerst einen neuen unabhängigen deutschen Dialogsplit mit mehreren Zügen/Korrektur/Negation vorregistrieren. Gegen eingefrorenen alten Checkpoint und KN-7 messen: BPB, Wort-/Antworttreffer, Wiederholungsquote, Instruktionsbefolgung und sichere Enthaltung. Erst dann eine begrenzte Decoder-/Trainingsänderung; keine produktive Übernahme bei bloß gesunkenem Trainingsverlust. Checkpoint-/Tokenizerhash und train/audit/test getrennt halten.
3. **Autodialog-Curriculum v2:** `ContinuumAutoDialogueCurriculum.kt`, `ContinuumAutoDialogueResearch.kt`, `Cre1ResearchLab.kt`, `ContinuumAutonomousScheduler.kt`. Neue mechanische Korrektur-/Negationsfamilie und bislang unbenutzten terminalen Holdout einfrieren. Reservation/Abbruch/64-Stufen-Grenze erhalten; keine neue Jobverwaltung. Vor erweitertem Betrieb positive vollständige JobService-Runde auf dem Gerät mit Budget-/Opt-out-Abbruch und Checkpointstatus belegen. V1 läuft nach64 Versuchen aus, nicht endlos weiter.
4. **Archivverwendung verbessern:** vorhandenen `ContinuumKnowledgeArchive.search`-Pfad in `runContinuumDialogue` mit einem unabhängigen Retrieval-/Falschattributionssatz prüfen. Semantik erst read-only/Shadow ergänzen, wenn messbarer Gewinn ohne fremde Sitzungs- oder Provenienzübernahme besteht. Der aktuelle Zählerfix ist schon angeschlossen; nicht erneut kopieren.
5. **Fold7-Abnahme und Release:** nach gültigem Signierauftrag die konkrete aktuelle APK installieren lassen, Langdruckdiagnose/mehrere Screenshots oder Video mit Zeitbezug aufnehmen, geöffnet/geschlossen, Home→Chat→Home, Pause/Resume, Energiesparen/reduzierte Animationen. Erst dann `DEVICE_VERIFIED`. Den v21-Releasepfad mit explizit freigegebener Quellidentität ergänzen; keine alten v19/v20-Pins überschreiben. Nach tatsächlicher Signierung Download, Code21, Paket und vorhandenes Zertifikat unabhängig prüfen. Aktuelle Termux BUILD/REPAIR-Receipt-Abnahme bleibt ein eigener offener Gerätenachweis.

## Vollständig kopierbarer Fortsetzungsauftrag

```text
Arbeite ausschließlich an https://github.com/Ullmann27/device-ai.
Fahre PR116 auf sol/continuum-0320-integration-20261024 fort, nicht main.
GitHub-HEAD zuerst tatsächlich lesen/fetchen; neuen dauerhaft zugänglichen
Nachfolgerbranch direkt vom aktuellen PR116-HEAD erstellen und sofort pushen.
Der letzte gemeinsame wirklich getestete Quell-/Testcommit ist
074fa80729a088644769df300c23b2e093e56553; Belege wurden in
976836d73c0bd34bb93d427f237c78f997866337 veröffentlicht. Danach nur Übergabedokumente.
Lies AGENTS.md, Experimentvertrag/-register, Issue41,
docs/CONTINUUM-SOL-0320-HANDOFF-20261024.md,
docs/CONTINUUM-0320-INTEGRATION-MATRIX-20261024.md und
docs/evidence/sol-0320-20261024/summary.json sowie COMMANDS.md.

Bereits fertig: gespeicherte Dialoge ID-gebunden, alte Resets geschützt,
v1-Legacy getrennt, keine zweite Historie; Autodialog-Cursor symlinkfest,
Unterbrechung/Opt-in/Ressourcen geprüft; 32 echte synthetische CRE1-Schritte
mit verbessertem getrenntem Audit, ohne SWB1/Faktenänderung; echtes
Archiv→Dashboard-Cache getestet; native Canvasbilder über echten Frameclock
auf Telefon/Fold verschieden; Besitzer-Version0.3.20/21 vorbereitet.
Neue Ergebnisse: Node1109, Backend60, SDK/JUnit971 einschließlich32 Robolectric,
keine Skips/Fehler, Lint0/0, CRE1-/SWB1-/SWB2-Parität und Backlog-Roundtrip grün.
Nicht nochmals kopieren oder als neue eigene Arbeit zählen.

Beginne mit der tatsächlichen Fold7-Modellidentität/SHA-Importdiagnose oder,
wenn kein Gerät zugänglich ist, dem genau benannten unabhängigen LM1-Dialogsplit
und einer kleinen gemessenen Decoder-/Trainingsverbesserung. Keine blinde
Aktivierung von LM1/I1/I2/CTX3. CTX3 OFF/SHADOW lassen. Der null-Adapter im
belegten Hauptchat ist eine absichtliche Sicherheitsgrenze; lokaler Fallback
ist bereits separat verbunden. Keine zusätzlichen Inferenzsitzungen/Worker.

Keine signierte0.3.20 vorhanden: v21-Dispatcher fehlt/blockiert Exit69;
7/34 alte v20-Pins abweichend,4 zusätzliche geänderte/neue Quellen nicht attestiert.
release-blockers.json zuerst lesen. Keine Fingerabdrücke oder Owner-SHA-
Freigaben selbst erfinden, kein Ersatzschlüssel, keine private Render-Auswahl
raten. Die tatsächlich verifizierte alte0.3.19/Code20 hat APK-SHA
1f486e96439708ff96e7f427276217b835e15f7a029a4f72cea8178f4bf82a19
und Zertifikat2b05fa9de378fb6c6c5376143774a573897dc9c9151305f1c44010275c76a9cc.
Keine Behauptung einer sichtbaren Fold7-Reparatur ohne echte Gerätesequenz.
Jeden abgeschlossenen Schritt testen, committen, pushen. Keine Force-Pushes,
Branchlöschung, Main-Automerge oder nur lokal gespeicherten Patches.
```
