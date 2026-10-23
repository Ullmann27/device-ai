# Übergabe Opus → Astra (23.10.2026): KI-Bausteine und ihre Schnittstellen

**Zweck:** Astra soll die KI-Bausteine später in die App einbinden können, ohne Opus-Dateien zu ändern. Opus ändert umgekehrt keine Astra-Dateien.

**Regeln**
- Opus bearbeitet nicht: `AgentActivity.kt`, `ContinuumHologramView.kt`, `app/src/main/java/at/heinz/deviceai/ui/**`, `AndroidManifest.xml`, `app/build.gradle.kts`, Release- und Render-Skripte.
- Das gemeinsame Update braucht dieselbe App-ID, denselben Signierschlüssel und einen versionCode größer als 17. Opus baut keine Besitzer-APK.

**Arbeitszweig:** `opus/continuum-intelligence-next-20261023`, PR #92 (Entwurf). Er liegt auf `astra/render-mentor-apk-builder-20261019` mit der signierten Quelle 0.3.16 (`6706da14`).

## 1. Was läuft und was nicht verbunden ist

| Baustein | Ort | Geprüft | In der App |
|---|---|---|---|
| LM1-Runtime: prüfendes Laden, schrittweise Generierung, Grenzen, Abbruch | `learning/lm/LmRuntime.kt` (Hauptquellen) | JUnit 5/5; echter Checkpoint mit 0,41 ms je Token (x86_64) | **nein**: keine Aufrufstelle, nicht auf ARM64 gemessen |
| CCC1-Worker: Aufträge, Zyklen, Erfahrungsprotokoll, CLI | `.github/continuum/ccc1-worker.mjs`, `ccc1-cli.mjs`, `ccc1-experience.mjs` | Node-Tests, echter Lauf auf einem Repository-Klon | **nein**: Die Termux-Brücke kennt CCC1 noch nicht. |
| Vorprüfung v2: Sandbox mit Repository-Lesezugriff, Netz- und Signalsperre | `.github/continuum/behaviour-probe*.mjs` | Node-Tests | indirekt, über den Worker |
| Code-Index mit Verhaltens-Fingerabdrücken | `.github/continuum/code-index.mjs` | Node-Tests | **nein** |
| Research-Radar (Werkzeug) | `.github/continuum/research-radar.mjs` | Node-Tests; kein Live-Lauf, arXiv ist hier gesperrt | **nein** |
| NF-Forschung (NF-1 bis NF-7) | Testquellen `learning/nf/*` | JUnit | **nein**, nur Forschung |

Nichts davon installiert, signiert oder ändert Besitzerdaten oder Besitzergewichte.

## 2. Schnittstelle A: LM1-Runtime (Kotlin)

Klasse: `at.heinz.deviceai.learning.lm.LmRuntime`.

**Laden**
- `LmRuntime.load(checkpoint: ByteArray, tokenizerBytes: ByteArray, expectedSha256: String)` wirft eine Ausnahme bei:
  - falschem Hash,
  - fremdem Tokenizer,
  - beschädigter Datei,
  - mehr als 64 MB.
- Alternativ `load(store, sha256, tokenizerBytes)` aus einem `LmCheckpointStore`.

**Generieren:** `generate(Request(prompt, maxNewTokens = 48, temperature = 0.0, topK = 1, seed = 0, maxMillis = 2000), cancelled = { … })` liefert `Result`.
- **Felder von `Result`:** `text`, `newTokens`, `promptTokens`, `promptTruncated`, `stop`, `millis`, `kind`.
- **`stop`:** `EOS`, `MAX_TOKENS`, `CONTEXT`, `TIME` oder `CANCELLED`.
- **`kind`:** immer `FREE_GENERATION`. Die Oberfläche muss die Ausgabe als freie Modellausgabe kennzeichnen. Fakten kommen aus dem Wissensarchiv mit Quellen, nie aus der Runtime.
- **Grenzen:** höchstens 512 neue Token, 8 KB Prompt, 60 s.
- **Ausführung:** nicht auf dem Hauptthread aufrufen, sondern etwa in einem Worker oder einer Coroutine auf `Dispatchers.Default`. `cancelled` wird vor jedem Token abgefragt.

**Dateien**
- Checkpoint und Tokenizer liegen auf dem Datenzweig `opus/lm1-checkpoints-20261022`.
- Erprobt ist LM1-GM-v512, Seed 1, Epoche 8, Checkpoint `7a0af1e9…`.
- Der Tokenizer liegt unter `tokenizer/tokenizer-v512-94375c73….lmbp`.

**Empfehlung:** noch **nicht** in der Hauptansicht zeigen. Die Textqualität reicht nicht: Schleifen, Anweisungen auf Zufallsniveau (LM1-Bericht, Schritt 7). Höchstens eine Laboransicht mit klarer Kennzeichnung.

## 3. Schnittstelle B: CCC1-Worker über die Termux-Brücke

**Muster:** wie `ContinuumMobileCodingJob`. Android schickt nur
- ein festes Skript,
- eine feste Operation,
- geprüfte Werte,

und nie Shell-Text. Das Skript prüft dieselbe Grammatik ein zweites Mal.

**Befehle** (Node, Projektordner wie `ContinuumMobileBuildPlan.PROJECT_DIR`)

| Zweck | Aufruf | Antwort (eine Zeile JSON) |
|---|---|---|
| Ziel vorab prüfen (Task-Compiler, ohne Auftrag und ohne Coding-Job) | `node .github/continuum/ccc1-cli.mjs compile --project <PROJECT_DIR> --goal "<Ziel>"` | Schema `continuum-ccc1-compiled-goal`, Version 1: `status` = `READY`, `AMBIGUOUS`, `NO_MATCH` oder `NEEDS_OWNER`, dazu `question`, `matching`, `unchecked`, `arity`, `examples` |
| Ziel einreichen | `node .github/continuum/ccc1-cli.mjs submit --project <PROJECT_DIR> --state <STATE_DIR> --goal "<Ziel>" [--repo Ullmann27/device-ai]` | `{"taskId":"t-<16 hex>"}`. Gebunden wird der Auftrag an das Repository aus `origin`, ohne `origin` an `local/<Ordner>`, nie an einen Platzhalter. |
| Zustand für die App | `node .github/continuum/ccc1-cli.mjs status --state <STATE_DIR> --format json` | Schema `continuum-ccc1-status`, Version 1 (unten) |
| ein Zyklus | `node .github/continuum/ccc1-cli.mjs run-once --project <PROJECT_DIR> --state <STATE_DIR>` | `{"outcome","ccc1","taskId","detail"}` |
| begrenzte Schleife | `node .github/continuum/ccc1-cli.mjs loop --project <PROJECT_DIR> --state <STATE_DIR> --cycles <n> --interval <Minuten> [--index-on-idle true]` | je Zyklus ein Herzschlag, am Ende eine Zusammenfassung. Mit `--index-on-idle true` frischt ein Zyklus ohne offenen Auftrag den Code-Index im Zustandsordner auf. |

**Empfohlener Ablauf in der App**
1. Erst `compile` aufrufen und das Ergebnis sofort zeigen.
2. Bei `AMBIGUOUS` oder `NEEDS_OWNER` die Rückfrage stellen.
3. Bei `READY` nach Bestätigung durch den Besitzer `submit` aufrufen.

**`continuum-ccc1-status`, Version 1**

```json
{"schema":"continuum-ccc1-status","version":1,"experienceRecords":3,
 "tasks":[{"taskId":"t-8ff2100990de8a53","state":"COMPLETED","waitingForOwner":false,
           "goal":"… (höchstens 200 Zeichen)","question":null,
           "confirmed":".github/continuum/research-radar.mjs#normalizeTitle","uncheckedAlternatives":0}]}
```

**Zustände (`state`)**
- **offen:** `DRAFT`, `VALIDATED`, … (Zwischenzustände des Dialogs);
- **beendet:** `COMPLETED`, `ABSTAINED`, `ABORTED`, `FAILED`;
- **`CORRUPT`:** Das Journal ist beschädigt. Der Worker stoppt; nichts wird repariert.

**Bedeutung der Felder**
- **`question`:** Rückfrage an den Besitzer, höchstens 600 Zeichen.
  - Sie beginnt mit „Mehrdeutig…“, wenn mehrere Funktionen alle Beispiele erfüllen, sich aber verschieden verhalten.
  - Sonst ist es ein Hinweis wie „Welche Eingaben … belegen das Ziel?“.
  - Antworten kann der Besitzer heute, indem er das Ziel mit einem weiteren Beispiel neu einreicht. Geplant sind Ja/Nein-Testvorschläge (Radar, Idee c).
- **`confirmed`:** Der unabhängige Vertragstest hat diese Funktion bestätigt. Das ist ein **Patch, der auf Prüfung wartet**; nichts ist gemergt oder installiert.
- **`uncheckedAlternatives` > 0:** Bestätigt wurde nur unter den prüfbaren Kandidaten. Es gibt gleich hoch eingestufte Kandidaten, die die Vorprüfung nicht ausführen konnte. Die App sollte das als Hinweis zeigen.

**Prüfung auf der Kotlin-Seite** (wie bei der Coding-Quittung)
- `schema` und `version` müssen stimmen.
- `taskId` muss auf `t-[0-9a-f]{16}` passen und `state` auf `[A-Z_]{2,32}`.
- Längengrenzen wie oben.
- Nichts aus diesen Texten wird ausgeführt oder als Befehl verstanden.

**Zustandsordner:** muss außerhalb des Repositorys liegen; das CLI erzwingt das. Vorschlag: `$HOME/.continuum/ccc1-state` in Termux.

## 4. Schnittstelle C: Code-Index

- `node .github/continuum/code-index.mjs build --project <PROJECT_DIR> --state <STATE_DIR>` gibt eine Zeile Statistik aus: Module, Exporte, prüfbare Exporte, deterministische, Cluster, Fast-Dubletten, Sekunden.
- `… show --state <STATE_DIR>` gibt die Statistik, die Cluster und bis zu 20 Fast-Dubletten aus.
- **Möglicher späterer Einsatz:** eine Ansicht „Code-Gesundheit“.
  - Cluster sind Funktionen, die auf dem ganzen Eingabepool gleich antworten: Kandidaten zum Zusammenlegen.
  - Fast-Dubletten sind die Stellen, an denen Besitzerziele am ehesten mehrdeutig werden.

## 5. Selbst prüfen

```
node --test .github/continuum/behaviour-probe.test.mjs .github/continuum/code-index.test.mjs
node --test .github/continuum/ccc1-worker.test.mjs .github/continuum/behaviour-disambiguation.test.mjs   # mit echten Coding-Jobs, einige Minuten
```

Für die LM1-Runtime: JUnit `LmRuntimeTest`. Der Build-Befehl steht in `docs/CONTINUUM-OPUS-RESUME-20261023.md`.
