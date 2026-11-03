# Fold7 – Operation Autonomy: reale Abnahme

**Aktueller Status: sämtliche nachstehenden Gerätetests NOT_RUN.** Der Besitzer
hat am 03.11.2026 bestätigt: derzeit kein Fernzugriff; Prüfungen vorbereiten.
Dies ist eine ausführbare Prüfreihenfolge, kein Beleg durchgeführter Inferenz.
Ein frischer APK-/Signaturbeleg bestätigt weder Installation noch Modellantwort.

## 0. Unverwechselbare Ausgangslage

Vor Beginn Datum, Gerätemodell/Android, App-Paket/Code, Build-SHA, Modell-ID,
Gewichts-SHA/Bytezahl, Backend und Internetmodus aufzeichnen. Keine Chats,
privaten Dokumente, Schlüssel oder Passwörter in öffentliche Logs kopieren.
Modellstatus READY ist eine Voraussetzung, noch kein Antwortnachweis.

Wenn bereits autorisiertes ADB verfügbar ist, diese **lesenden** Befehle nutzen:

```sh
adb devices -l
adb shell getprop ro.product.model
adb shell getprop ro.build.version.release
adb shell dumpsys package at.heinz.deviceai.continuumlocal
adb shell dumpsys battery
adb shell df -k /data
```

Ohne ADB dieselben verfügbaren Angaben in Android „App-Info“, Continuum
„Systemstatus“/Modellverwaltung und der bestehenden Termux-Statusseite ablesen.
Keinen neuen Fernzugriff oder neue Termux-Einrichtung voraussetzen.
Private App-Verzeichnisse nicht per Root/`run-as` umgehen. Keine Neuinstallation,
Datenlöschung oder Gewichtsänderung als Diagnoseschritt.

Für eine spätere APK zuerst den **dann aktuellen** Katalog am gepinnten Host und
die vollständige APK erneut prüfen. Die am Start dieser Runde verifizierte
Code-38-APK stammt aus d056d776, nicht aus dem neuen Forschungsbranch. Sie
enthält dessen spätere Reparatur nicht. Ein Update benötigt einen geeigneten
Versionscode, Originalsignatur und gesonderte konkrete Besitzerfreigabe.

## 1. Echtes lokales Gehirn – vor CCC1

1. In der vorhandenen Modellverwaltung importierte und geladene Identität
   vergleichen. `LocalModelCatalog` prüft bei `get()` standardmäßig den Datei-
   SHA; LiteRT `load()` nutzt diesen Aufruf und prüft native Metadaten. Ohne
   vorhandene kompatible Gewichte: BLOCKED, keine automatische Beschaffung.
2. Internetmodus OFF wählen; bei Bedarf Flugmodus/WLAN aus über die eigene
   Android-Oberfläche. Kein Cloud-Mentor. Im Chat exakt
   **`Continuum Modelltest Direkt`** eingeben. Dieser vorhandene Einstieg führt
   eine echte toolfreie `LocalModelRuntime.stream`-Generierung aus. Antwort,
   Identität, Dauer, Fehler und numerische Tokenmetriken sichern. Ein nichtleerer
   Text ist ein Inferenz-Smoke, noch keine Qualitäts- oder Toolabnahme.
3. Im normalen lokalen Chat eine neue, öffentlich harmlose Aufgabe verwenden:
   „Lies den aktuellen Akkustand und den verfügbaren Gerätespeicher. Nenne beide
   Messungen mit Einheiten und sage, welche Angaben tatsächlich gemessen wurden.“
   Erforderlich sind neue `get_battery_info`-/`get_storage_info`-Quittungen im
   aktuellen Aufgabenlauf und eine Modellantwort **nach** diesen Ergebnissen.
   Android-Akkuanzeige/`dumpsys battery` und derselbe Datenträger in `df /data`
   dienen als unabhängige Orakel. Speicheränderung zwischen Messzeiten notieren;
   nicht verschiedene Volumes oder reservierten Speicher gleichsetzen.
4. Folgefrage: „Nein, ich meinte den freien Speicher, nicht den Gesamtspeicher.
   Bitte nichts löschen.“ Der zusammenhängende Kontext und das späte NICHT
   müssen erhalten bleiben. Keine Dateilöschung, kein zusätzlicher Schreibzugriff.
   Danach harmlose deutsche/griechische Verneinung und eine Rückfrage testen.
5. STOP während laufender Generierung betätigen, danach einen neuen kurzen Chat
   starten. Notieren, ob spätere alte Textchunks erscheinen und ob neue Inferenz
   wieder möglich ist. Abgebrochene Generation darf keinen Erfolg belegen.
6. Einen vorhandenen vollständig freigegebenen, ausreichend großen Toolausgang
   testen: entweder vollständig übergeben oder **vor** Übergabe sichtbar
   verweigern. Keine abgeschnittenen Pflichtdaten. Native Tokenangaben getrennt
   von konservativen Bytebudgets protokollieren; Byteheuristik beweist keine
   exakte Modell-Kontextgrenze. Ein absichtlich übergroßer Fall muss BLOCKED
   ergeben, kein Crash, kein stiller Kontextverlust.

**PASS M1:** echte lokale Antwort plus mindestens zwei echte aktuelle
Werkzeugresultate und deren korrekte Nachverarbeitung, Korrektur/STOP geprüft.
Ein vorprogrammierter Adapter, alte Quittung oder bloß READY erfüllt das nicht.

Optionaler GGUF-Pfad: vorhandene Dateien, Upstream-/Gewichts-Pin und Termux-
Binärdatei erst lesen; `scripts/continuum-gguf-preflight.mjs --verify` prüft nur
Zulassung. Der vorhandene `continuum-local-llama-termux.sh start` startet einen
lokalen Server ausschließlich nach explizitem lokalen Opt-in. Dessen bestehender
Smoke-Test zeigt Text/Diagnose/Patchvorschlag, **keinen CCC1-Reparaturzyklus**.
Nicht automatisch starten, keinen Schlüssel ausgeben. Die Android-Host-Factory
erzeugt weiterhin LiteRT; ein GGUF-CLI-Erfolg beweist keinen App-Modellwechsel.

## 2. CCC1-Feuerprobe – reale Modellherkunft ist Pflicht

Erst nach M1. Einen neuen isolierten Forschungscheckout mit eigenem externem
CCC1-Zustand und einer vom Besitzer autorisierten harmlosen Regression nutzen.
Keine produktive Quelldatei und keinen persönlichen Zustand ändern. Zuerst
Baseline/SHA sauber sichern; Regression in diesem Checkout separat committen.
Die Test- und Orakeldateien vor dem Modelllauf festlegen und danach unverändert
lassen. Eine Revision darf ausschließlich den freigegebenen Quellpfad ändern.

Vorhandene Einstiegspunkte, **keine Ersatzpipeline**:

- App `Worker Builder` → vorhandener Android/Termux-Kanal, CCC1-Vorprüfung,
  Reparaturauftrag, Schritt ausführen, Status, Abbrechen.
- Im ausdrücklich ausgewählten isolierten Termux-Checkout:
  `bash scripts/continuum-teamleader-termux.sh ccc1-check --full`.
  Die historische Anleitung `CONTINUUM-CCC1-AUF-DEM-GERAET.md` beschreibt den
  Kanal, aber ihr alter Branch ist nicht die aktuelle Forschungsquelle.
- Für den bestehenden allgemeinen Modellagenten: typisierte Coding-Aufgabe
  (`ContinuumCodingConversation`/`ContinuumTypedCodingBridge` →
  `scripts/continuum-typed-coding-bridge.mjs` → `coding-orchestrator.mjs`).
  `CONTINUUM_CODING_AGENT_LOOP=1` wählt den vorhandenen Agentenprovider;
  freigegebene lokale Endpoint-/Modellkonfiguration und der bestehende gebrandete
  isolierte Executor sind Voraussetzungen. Ohne Executor muss die Android-
  Brücke `CODING_ISOLATION_REQUIRED/UNAVAILABLE` melden. Nicht umgehen.

**Wichtige Unterscheidung:** Der normale `ccc1-submit-repair`-Pfad verwendet
standardmäßig deterministische Reparaturstrategien. Ein erfolgreicher
`REVERT_FILE_TO_PARENT` ist reale Reparatursteuerung, aber kein autonomer
Modellnachweis. Auch `MODEL_INFERENCE_UNVERIFIED` oder ein HTTP-Modellname reichen
nicht: Gewichte/Runtime, tatsächliche lokale Serverausführung und Modellantworten
müssen unabhängig zum konkreten Lauf passen. Keine Cloud-API als Ersatz.

Für PASS müssen derselbe Task-/Quellbezug, echte Modellinterpretation,
selbständige Code-/Git-Leseschritte, ausgeführter RED-Test, vom Modell stammender
Patch, ausgeführtes GREEN und ein zweiter frischer Prüfworktree nachweisbar sein.
Originaltests und zusätzliche unbekannte Orakel bleiben unverändert. Patch,
Quell-/Test-Hashes und alle Quittungen werden zur Besitzerprüfung angeboten,
niemals automatisch übernommen. Vollständige Fehler/Enthaltungen berichten.

Gesonderte Läufe: Abbruch vor Dispatch (kein späterer Backendstart), Abbruch nach
Dispatch (Wirkung eventuell vorhanden), Prozessneustart, gleiche Auftrags-ID,
neue ID bei UNKNOWN, verspäteter Callback. Journal nicht löschen. Replay muss
sichtbar als Replay/Blockade enden; keine neue Ausführung oder Lernerfahrung.

## 3. Android-Handlungsfähigkeit

Nach M1/M2 zuerst read-only: `get_storage_info`, `get_battery_info`, `list_files`
in einem ausdrücklich freigegebenen öffentlichen Testordner. Genau die tatsächlich
registrierten Parameter verwenden; kein aus Modelltext gebauter Shellbefehl.
Datei-/Dokumentanalyse und Nachrichtenentwurf nur testen, wenn der bestehende
Fähigkeits-/UI-Pfad sie wirklich anbietet. Das allgemeine `read_file` ist im
aktuellen Android-ToolRegistry **nicht** registriert; weder neue Rechte noch
einen erfolgreichen Dateizugriff daraus erfinden. Kein Nachrichtenversand.

Bildschirmtest nur mit bereits aktivierter Besitzerfreigabe: frischen
`read_app_screen`-Snapshot, Ablaufzeit, Grant-Epoch und externen Datenstatus
prüfen. Beim Widerruf muss der nächste Aufruf blockieren. Quote-/Rollenheader im
öffentlichen Testinhalt bleiben Daten. Der neue Hosttest prüft den vollständigen
Frame-/Prompt-/Kanalweg, **kein reales Android-Fenster**.

## 4. Worker und Erfahrungen

Den bestehenden `ccc1-cli.mjs loop`/`local-teamleader-loop.mjs` verwenden, keinen
zweiten Worker starten. Zunächst höchstens zwei explizite lokale Zyklen mit
externem Zustand, Batterie-/Temperatur-/Speicherprüfung. Vor jedem Lauf Quelle,
Task, Lease, Journal und Fähigkeit prüfen. Ein Leerlauf ist IDLE, kein Lernbeleg.
STOP, Prozessende und Wiederaufnahme prüfen; UNKNOWN erfordert Nachprüfung und
Besitzerentscheidung. Bereits gestartete Wirkungen sind nicht rückgängig bewiesen.

Nur unabhängig erfolgreiche neue Ergebnisse dürfen `AgentTaskEvidence` und
`ExperienceCompiler` als reviewpflichtigen Kandidaten erreichen. Widerruf,
Timeout, Replay oder fehlendes Orakel: kein Erfolgskandidat. Bestätigte Erinnerungen
und SWB1/CRE1/CDX-Gewichte bleiben unverändert.

## 5. Natürliches Erlebnis – nach Kernabnahme

Chat/Arbeitsstatus, vollständige Freigabedialoge, Falten/Drehen, STOP und
Dateiauswahl prüfen. Sprache nur mit tatsächlich verfügbarem lokalem Dienst
und offline nachweisen; fehlender Offline-Erkenner ist UNAVAILABLE. Archivierte
Avatarquellen sind kein animierter Android-Avatar und kein Abnahmekriterium PASS.

## Protokoll je Fall

Fall-ID; UTC; Quell-SHA/App-Code; Gerät; Gewichts-SHA/Runtime; Task-/Aufruf-ID;
geplant/freigegeben/gestartet/abgebrochen/UNKNOWN/abgeschlossen; Modelltext
(nur öffentliche Testdaten); rohe Toolquittung mit Replaykennzeichen;
unabhängiges Orakel/Nachzustand; PASS/FAIL/NOT_RUN; Fehler; Erfahrungskandidat
ja/nein. Bei erneutem Lauf neue Zeile, frühere negative Ergebnisse erhalten.

`DEVICE_VERIFIED` nur mit tatsächlicher Gerätedurchführung; JVM-/Node-
Testadapter sind `HOST_VERIFIED` mit Simulation ausdrücklich benannt.
