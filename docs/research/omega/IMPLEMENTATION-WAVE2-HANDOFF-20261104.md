# Zusätzliche Implementierungsrunde — gemeinsamer Stand für die spätere APK-Referenz

Stand: 4. November 2026. **SOURCE_INTEGRATED / TARGETED_COMBINED_HOST_PASS.**

Genau drei zusätzliche Entwicklerhelfer wurden gestartet. Ihre Aufgaben waren Gedächtnis/Gehirn, Worker/Werkzeuge und Avatar/Oberfläche. Die fertigen Beiträge wurden auf einem isolierten Integrationszweig verbunden und tatsächlich gemeinsam geprüft. Die bereits arbeitenden Teams und ihre Zweige blieben erhalten. Diese Runde erstellt keine APK und ersetzt keine Geräteabnahme.

Review: [PR #513](https://github.com/Ullmann27/device-ai/pull/513), Zweig `integration/continuum-apk-connections-wave2-20261104`. Der gemeinsame Stand enthält den fertigen Parallelstand #512 auf #506 sowie die gezielt übernommenen #502/#503-Avatarquellen und die hier reparierten Anschlüsse.

## Eingefrorene Herkunft und geprüfter Quellstand

| Bestandteil | Nachgewiesener Stand |
| --- | --- |
| Ausgangsintegration #506 | `8abea828833d8833d40e3d3d36feaa6d3685b82e`; lokaler Produktstand `e5c06e6bf657db556bc4861c7f46a55ebcd6e03a` |
| Native Ruhebewegung #502 | `6cb9e760f0a16a6f34a8442abf9b79dcf2705799` |
| Sprach-/Artikulationsregeln #503 | `2582be86c121d7f6d1179c7edc49e979bee8ba9c` |
| Fertiger Parallelstand #512 | remote `f9267a3743a87a77bf6421011c014b2ca2d6b0d6`, lokal `58b2f12eecffde498b5cdc3718e07370250cd364` |
| Vollständiger identischer #512-Baum | `fba27bc2200f0d318ce3ea7fc175499277ad5aba` |
| Tatsächlich gemeinsam getesteter lokaler Commit | `faa41d4028a8c6cd113d7a73f205295d39f1a055` |
| Geprüfter vollständiger App-Unterbaum | `471dccb8844a9aba34de5fa33b7f7ffc619ee163` |
| Geprüfter Scripts-Unterbaum | `d5bc5ebea9ce73901ec8b773fbc845e6250b99b8` |

Die Abschlussänderungen betreffen ausschließlich Dokumentation und Evidenz. Die Produkt-/Testquellen stimmen mit dem geprüften App-Unterbaum überein. Während des finalen Laufs blieben sämtliche 1071 erfassten Dateien unter `app/src` unverändert; die archivierten Hashes wurden danach erneut gegen die tatsächlich vorhandenen Dateien verglichen.

## Was angeschlossen und repariert wurde

### Sprache, Audio und sichtbarer Avatar

Der tatsächliche `AgentActivity`-Sprachlistener erhält vom Android-TTS-System eine Audioframe-Position. Bisher reichte er diese nur an den PCM-Anker, nicht an die neue Reihenfolgeprüfung im `AvatarDirector` weiter. Außerdem wurde der PCM-Anker bereits verändert, bevor ein doppelter oder rückwärtiger Rückruf verworfen werden konnte.

`AvatarDirector.speechStarted` und `wordRange` melden nun die Annahme zurück. Der vorhandene Activity-Aufrufer prüft diese Annahme vor einer Änderung von Audio-Anker, Sprachstatus oder Frame-Schleife und reicht den tatsächlichen Audioframe weiter. Gültige neue Wortbereiche mit gleichem Frame bleiben erlaubt. Interpunktion aktualisiert gültige Text-/Frame-Marken, ohne erfundene Viseme oder ein verlängertes Ersatzanimationsfenster zu erzeugen. Sink-Ausnahmen beeinflussen die Annahme eines gültigen Rückrufs nicht.

Beim Wechsel zwischen Startansicht und Chat wird dieselbe Live-View umgehängt. Weil das Abhängen ihren flüchtigen Sprachzustand löscht, stellt der Director nun höchstens 64 noch gültige oder bevorstehende Cues des letzten Wortes wieder her. Ihre ursprünglichen Zeitmarken bleiben erhalten. Ein wiederholtes Anbinden verlängert das Wort nicht, vervielfacht die Queue nicht und belebt beendete beziehungsweise ersetzte Sprache nicht wieder.

Die fertigen #502/#503-Quellen bringen begrenzte Ruhebewegungen, zeitbezogene Audioglättung, Wiederholungsschutz und den expliziten Lippenschluss bei PP/M/B/P mit. Ihre Herkunft wurde vor den Anschlussänderungen anhand der Git-Blobs geprüft. Kein Avatar-Asset wurde ersetzt.

### Gedächtnis, Worker und Cockpit

Der Gedächtnishelfer verfolgte den bestehenden Weg vom Kontextcompiler über die tatsächlich in den Kontext aufgenommenen Erinnerungen und den kanonischen Ereignisbus bis zur sichtbaren Gehirn-/Recall-Darstellung. Der Workerhelfer prüfte Auftragsticket, Budget, Orchestrator, ToolGate, tatsächlichen Executor-Rückruf, veraltete Ergebnisse und Abschluss/Abbruch. Dort wurde kein zusätzlicher fehlender Anschluss nachgewiesen; es wurde keine zweite Infrastruktur eingeführt.

Nach Fertigstellung des Parallelteams wurde dessen kompletter, veröffentlichter #512-Baum mit unveränderter Herkunft übernommen. Er enthält die #505-Korrekturen für Cockpit-Vorgangslebensdauer, Kalender-/Überlaufbehandlung und frühe QA-Versionsprüfung. Die drei Helfer prüften die Kombination nochmals unabhängig. Der Hauptchat verwendet weiterhin `cognitive.HybridMemoryRetriever`; der zusätzliche M2.9-Retriever wurde nicht aktiviert.

## Tatsächliche gemeinsame Prüfung

| Prüfung | Ergebnis |
| --- | --- |
| Finaler Android-/JVM-Lauf einschließlich App- und Testkompilierung | **352 Tests in 58 Klassen, 352 bestanden, 0 Fehler, 0 übersprungen; Exit 0** |
| Konfigurierte Android-Lint-Prüfung | **NewApi: 0 Befunde, Exit 0**; dies ist kein vollständiger Lint-Lauf |
| QA-Vorprüfung und Update-Verträge | **70/70 bestanden, 0 Fehler/Skips, Exit 0** |
| Avatarhelfer: ergänzendes Motion-Prüfwerkzeug | **26/26 bestanden** auf den unverändert übernommenen Avatarquellen |
| Quellen während und nach dem finalen Android-Lauf | **1071 Dateien unverändert**, keine Hashabweichung |
| Unabhängige Anschluss-/Kompatibilitätsreviews | Keine verbleibenden blockierenden Befunde im geprüften Umfang |

Die 352 enthalten unter anderem die 85 Avatar-Komponententests, die sechs neuen Activity-Rückrufprüfungen, zwei strengere Live-Reparent-/PCM-Fälle, die drei übernommenen echten Listener-Fälle, die 11 Cockpit-Lifecycle-Fälle, die 15 Kalenderfälle sowie Bot-, Worker-, Routine-, Gedächtnis- und Navigationsprüfungen. Untergruppen und frühere Läufe dürfen nicht addiert werden.

Die 70 Node-Prüfungen liefen tatsächlich auf `4b81e076717dfceb4b07dd576c3cf16ae9c1084d`; anschließend änderte sich nur ein Kotlin-Test und Dokumentation. Der gesamte geprüfte Scripts-Unterbaum und die erfassten Script-Dateihashes sind zum Endstand identisch. Die 48 Prüfungen des fremden Workerhelfers werden hier nicht als eigener erneuter Lauf ausgegeben.

JDK 17, Gradle 8.11.1, Kotlin 2.4.0 und der vorhandene Android-SDK-36-Cache wurden lokal/offline verwendet. Compilerwarnungen sind im Protokoll sichtbar. Es wurde keine frische vollständige normale Unit-Suite behauptet; dies ist eine gezielt zusammengestellte gemeinsame Integrationsprüfung.

### Nachvollziehbare Gegenproben und Testmigration

Die sechs neuen Activity-Prüfungen scheiterten zunächst sämtlich am bisherigen Produktstand. Nach bloßem Einbau der Director-Schutzregeln, aber noch unverändertem Aufrufer, scheiterten weiterhin fünf von sechs. Erst der tatsächliche Anschluss von Frame und Annahmeergebnis schloss die Lücke; alle sechs bestehen im Endstand. Die PCM-Gegenproben messen zwei unterschiedliche Signalabschnitte (.5 und .25), sodass ein zurückgesetzter Audio-Anker tatsächlich sichtbar wird.

Bei der Kombination wurden außerdem zwei ältere Testannahmen ausdrücklich korrigiert:

- Der übernommene #495-Reparent-Fall erwartete bei etwa 380 ms ein durch einen identischen Rückruf neu gestartetes Wort mit ursprünglichem Ende bei 320 ms. Genau dieser Fall scheiterte, die zwei anderen Listener-Fälle bestanden. Der Test verwendet jetzt einen tatsächlichen nachfolgenden Wortbereich. Die strengere zusätzliche 210/380-ms-Prüfung verlangt weiterhin Wiederherstellung vor Ablauf und keine Verlängerung durch Replay.
- Der statische `LipSyncFaceSourceTest` verwendete einen zustandsbehafteten Mapper für alle Mundformen bei demselben Zeitpunkt und erwartete auch bei PP einen offenen Kiefer. Der erste breitere Lauf zeigte 351/352 bestanden und genau diesen Fehler. Jede unabhängige statische Probe erhält jetzt einen eigenen Mapper; PP prüft zusätzlich ausdrücklich Morphgewicht 1 und Kieferrotation 0. Geometrische Koeffizienten und Fehlertoleranzen wurden nicht gelockert. Der erneute komplette 352er-Lauf ist grün.

Originale XML-Dateien, exakte Testquellen der Gegenproben, Aufrufe, Status und Quellhashes liegen unter `docs/evidence/implementation-wave2-20261104/`. Gradle-Protokolle entfernen ausschließlich abschließende horizontale Leerzeichen je Zeile; drei JUnit-Helferprotokolle entfernen ausschließlich zusätzliche leere Schlusszeilen. Original- und Archivhashes sowie die genaue Normalisierung sind separat dokumentiert. Inhalte und Reihenfolge der Ausgaben bleiben erhalten.

## Umfang der Branch-Kontrolle

`IMPLEMENTATION-WAVE2-BRANCH-AUDIT-20261104.md` erfasst 40 am Stichtag aktualisierte offene Entwicklungs-PRs mit festgehaltenen Köpfen und Abstammungsverhältnissen. 206 Vergleiche von Produktionsdateien über zehn abweichende ältere PRs betreffen 100 verschiedene Kotlin-Pfade: 75 identische Paare, 119 unterschiedliche Paare und 12 fehlende Paare.

Diese Bestandsaufnahme ist keine vollständige inhaltliche Freigabe jedes historischen Branches. Die 119 unterschiedlichen Paare bleiben außerhalb der gezielt überprüften Anschlusswege semantisch zu prüfen. Die fehlenden Dateien aus #487 und #490 sind in ihren eigenen Übergaben als unvollständig beschrieben. Forschung, ungeprüfte Modellgewichte und solche unfertigen Ports werden durch die Zusammenführung nicht automatisch aktiviert.

## Vor der späteren APK-/Geräteabnahme offen

- Tatsächliche Fold7-Nutzung offen/geschlossen, hörbare TTS-Synchronität, native Filament-/GPU-Darstellung und visuelle Referenztreue sind hier **NOT_RUN**. Robolectric verwendet reale Android-nahe Appklassen mit simulierten Diensten und synthetischen akzeptierten Sprachaufträgen/PCM.
- Der bekannte GLB-Befund `rig_hierarchy_orientation` bleibt gemäß der übernommenen Abnahmedokumentation offen; dieser Lauf ersetzt keine Asset-/Gerätefreigabe.
- Der bekannte M2.9-Top-3-Rangverlust von 43/44 auf 42/44 bleibt offen. Kalenderkorrektheit beweist keine verbesserte gesamte Suchqualität.
- Der in #510 dokumentierte externe Owner-/CCC1-/ECHO-Rückweg ist damit nicht ausgeführt oder freigegeben. Vorhandene Android-Worker-Verbindungen und Host-Vertragstests beweisen keinen laufenden externen Dienst.

Paket, Signatur, aktuelle Appversion, Offline-/Zustimmungssteuerung und kanonische Werkzeugfreigaben blieben unverändert. Kein Main-Merge, kein APK-Bau/Release/Installation, kein kostenpflichtiger Dienst und keine Modellpromotion wurden durchgeführt.

## Reproduktion und Rücknahme

Die finale Auswahl steht in `docs/evidence/implementation-wave2-20261104/combined-final/selected-tests.json`, der tatsächlich ausgeführte Gradle-Aufruf in `result.json`, der Node-Aufruf in `preflight-result.json`. `summary.json` enthält Klassen, Ergebnisse, App-/Scripts-Bäume und Integritätsprüfung; `run_gradle.py` dokumentiert den tatsächlich verwendeten lokalen Offline-Launcher und dessen Toolchain-Pfade.

Die detaillierte Herkunft des Avatarbeitrags steht in `WAVE2-AVATAR-HANDOFF-20261104.md`, die tatsächlichen Helfer- und Zweitprüfungen in `IMPLEMENTATION-WAVE2-INDEPENDENT-REVIEWS-20261104.md`. Die #512-Übergabe bleibt als historische Quelle erhalten und wird nicht als eigener Endstand-Testbeleg umgedeutet.

Rücknahme erfolgt auf dem isolierten Integrationszweig. Director-Annahmevertrag und Activity-Aufrufer gemeinsam zurücknehmen; Mapper und IdleMotion gemeinsam behandeln. Die getrennten #512-Korrekturen bleiben einzeln rücknehmbar. Es gibt keine Besitzerdatenmigration und keinen Anlass, aktive fremde Zweige zurückzusetzen oder zu löschen.
