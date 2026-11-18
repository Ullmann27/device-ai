# Lokales Sprachmodell: Geschwindigkeit und Wärme – Befund, Optionen, Messprotokoll (18.11.2026)

Basis: Commit `827b60a70`. Zielgerät: Samsung Galaxy Z Fold7 (Snapdragon 8 Elite for Galaxy, 12-GB-Klasse).
Auslöser: Der Besitzer berichtet rund eine Minute je Antwort und nach drei bis vier Zügen einen Stopp durch den
Hitzeschutz. Diese Angaben stammen vom Besitzer; im Repository liegt keine Gerätemessung dazu vor.

**Am Gerät wurde in dieser Arbeit nichts gemessen (NOT_RUN).** Jede Zahl in diesem Dokument gehört zu genau einer
von drei Klassen und ist so beschriftet:

| Klasse | Bedeutung |
|---|---|
| **Hersteller** | vom Anbieter veröffentlicht, mit dessen Gerät und Backend; keine eigene Messung |
| **Host** | von mir am Build-Host gemessen (x86_64, Intel Xeon 2,1 GHz, 4 Kerne, geteilt mit anderen Builds); sagt nichts über das Telefon |
| **Code** | aus dem Quelltext dieses Repositorys oder aus dem Quelltext/Artefakt von LiteRT-LM abgelesen |

Begleitmaterial (Quelltext der Host-Proben und Rohausgaben): [`local-model-speed-20261118/`](local-model-speed-20261118/).

---

## 1. Kurzfassung

1. **Jeder Zug rechnet den gesamten Prompt neu.** Die App schließt bei jeder neuen Frage die native Konversation
   und legt eine neue an (Code: [`LocalConversationBoundary.kt:19-20`](../../app/src/main/java/at/heinz/deviceai/localmodel/LocalConversationBoundary.kt)).
   LiteRT-LM würde in einer weiterverwendeten Konversation nur den neuen Zug einlesen (Code, LiteRT-LM v0.18.0:
   [`conversation.cc:257-323`](https://github.com/google-ai-edge/LiteRT-LM/blob/v0.18.0/runtime/conversation/conversation.cc)).
   Host-Probe mit derselben Bibliotheksversion: Folgezüge 4,0 s und 3,7 s statt 18,7 s und 18,1 s, eingelesene
   Token 31 und 25 statt 1 868 und 1 912 (Abschnitt 4.1). Das ist der größte Hebel, weil er Arbeit ganz vermeidet –
   also Zeit **und** Wärme –, aber er verlangt einen Umbau des zentralen Antwortpfads und ist hier nur beschrieben
   (Abschnitt 9).
2. **Ein kleineres freies Modell derselben Familie gibt es: Gemma 4 E2B** (`gemma-4-E2B-it.litertlm`, 2,59 GB,
   Apache 2.0, ohne Anmeldung). Hersteller, Samsung S26 Ultra, CPU mit 4 Threads: Einlesen 557 statt 195 Token/s,
   Schreiben 46,9 statt 17,7 Token/s ([Modellkarte E2B](https://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm),
   [Modellkarte E4B](https://huggingface.co/litert-community/gemma-4-E4B-it-litert-lm)). Der Import ist jetzt im
   Modellzentrum vorausgefüllt. Wie viel Antwortqualität das kostet, muss die Evaluation am Gerät zeigen.
3. **LiteRT-LM 0.18.0 hat ein GPU-Backend, die App hat es nie benutzt** – nicht wegen einer Messung, sondern weil es
   nie eingebaut wurde (Abschnitt 2.2). Hersteller, S26 Ultra, Gemma 4 E4B: Einlesen 1 293 statt 195 Token/s,
   Schreiben 22,1 statt 17,7 Token/s. Dafür gibt es jetzt einen abgeschalteten, experimentellen Schalter mit
   CPU-Rückfall und Absturzschutz. Ob die GPU am Fold7 läuft, schneller oder kühler ist, ist unbekannt.
4. Die NPU ist für dieses Gerät mit Gemma 4 derzeit kein gangbarer Weg (Abschnitt 5.1).

Rangfolge und Begründung: Abschnitt 7. Messprotokoll für den Besitzer: Abschnitt 8.

---

## 2. Ist-Stand aus dem Code

### 2.1 Welche Backends kann die App heute wählen?

Stand `827b60a70`: **nur CPU.**

- [`LiteRtLmLocalModelRuntime.kt:192`](../../app/src/main/java/at/heinz/deviceai/localmodel/LiteRtLmLocalModelRuntime.kt)
  (Zeilen des Basis-Commits) baut die Engine fest mit `backend = Backend.CPU(threadCount = threads)`;
  Zeile 173–175 verlangt, dass die Modelldatei CPU meldet („This runtime currently requires CPU support.“).
- Die Aufzählung `LocalModelBackend { CPU, GPU, NPU }` ([`LocalModelCatalog.kt:20`](../../app/src/main/java/at/heinz/deviceai/localmodel/LocalModelCatalog.kt))
  ist nur ein Merkmal, das beim Import aus der Modelldatei gelesen wird (Zeile 80–86); gewählt wurde damit nichts.

### 2.2 Warum wurde GPU/NPU nicht benutzt?

Es gibt **keine dokumentierte Entscheidung gegen GPU oder NPU und keine Messung.** `git log -S"Backend" -- app/src/main/java/at/heinz/deviceai/localmodel`
liefert genau einen Commit: `463953ede` (12.10.2026, „Add local LiteRT-LM inference foundation“). Dessen Begleittext
sagt in [`docs/AGENT_TOOLS_AND_RESEARCH.md:77-79`](../AGENT_TOOLS_AND_RESEARCH.md): „The implementation has not been
exercised with native inference on a physical Android device … The current runtime selects CPU only; GPU/NPU fallback
… remain future integration work.“ Zusätzlich fehlten im Manifest die beiden `uses-native-library`-Einträge, die
der offizielle Leitfaden für das GPU-Backend auf Android verlangt
([LiteRT-LM Kotlin-Leitfaden, v0.18.0](https://github.com/google-ai-edge/LiteRT-LM/blob/v0.18.0/docs/api/kotlin/getting_started.md)) –
ein GPU-Start hätte also gar nicht gelingen können.

### 2.3 Rechenbudget (Threads) und eine Korrektur

[`LocalModelComputeBudget`](../../app/src/main/java/at/heinz/deviceai/localmodel/LocalModelComputeBudget.kt)
(Commits `f3f90736f`, `29aa8c574`): „ausgewogen“ = Kerne/2, höchstens 4; „Kühlmodus“ = Kerne/4, höchstens 2;
das Fold7 (`SM-F966…`) startet im Kühlmodus, also mit **2 Threads**; ist das Gerät beim Laden schon warm
(Thermalstufe ≥ 2), höchstens 2.

Korrektur einer Annahme im Kommentar dieser Datei: LiteRT-LM startet ohne Vorgabe **nicht** einen Thread je Kern
(8 am Fold7), sondern **4** – Code, LiteRT-LM v0.18.0:
[`llm_executor_settings.h:136-137`](https://github.com/google-ai-edge/LiteRT-LM/blob/v0.18.0/runtime/executor/llm_executor_settings.h)
(„Number of threads. The default value is 4.“) und
[`llm_executor_settings.cc:180`](https://github.com/google-ai-edge/LiteRT-LM/blob/v0.18.0/runtime/executor/llm_executor_settings.cc);
die JNI-Schicht überschreibt den Wert nur, wenn die App einen angibt
([`litertlm.cc:588`](https://github.com/google-ai-edge/LiteRT-LM/blob/v0.18.0/kotlin/java/com/google/ai/edge/litertlm/jni/litertlm.cc)).
„Ausgewogen“ entspricht am Fold7 also dem Bibliotheksstandard, nur der Kühlmodus liegt darunter. Die
Herstellerzahlen unten sind mit 4 Threads gemessen; mit 2 Threads ist das Fold7 langsamer, um wie viel, ist
nicht gemessen. Der Kommentar wurde berichtigt.

Kernzuordnung: Die API kennt nur die Threadzahl (`Backend.CPU(threadCount)`,
[`Config.kt:140-147`](https://github.com/google-ai-edge/LiteRT-LM/blob/v0.18.0/kotlin/java/com/google/ai/edge/litertlm/Config.kt)),
`CpuConfig` hat kein Feld für eine Kernbindung (gleiche Header-Datei, Zeile 123–141). Auf welchen Kernen Android
die zwei Threads laufen lässt, steuert die App nicht. Der Snapdragon 8 Elite hat laut
[Qualcomm-Produktblatt](https://www.qualcomm.com/content/dam/qcomm-martech/dm-assets/documents/Snapdragon-8-Elite-Platform-Product-Brief.pdf)
„Prime core, up to 4.32 GHz“ und „Performance cores, up to 3.53 GHz“; Effizienzkerne führt das Blatt nicht auf.

### 2.4 Welche Modelle nimmt der Katalog an?

Es gibt **keine Namensliste.** Angenommen wird jede Datei, für die gilt (Code,
[`LocalModelCatalog.kt`](../../app/src/main/java/at/heinz/deviceai/localmodel/LocalModelCatalog.kt)):

- LiteRT-LM-Sprachmodell mit Texteingabe **und** CPU-Backend laut `ModelInfo` (Zeilen 59, 89, 196);
- SHA-256 stimmt mit dem vom Besitzer eingegebenen Wert überein;
- höchstens 20 GiB (Zeile 363), höchstens 256 Katalogeinträge (Zeile 370);
- Kennung und Version: `[A-Za-z0-9][A-Za-z0-9._-]{0,63}`.

Vor dem Laden prüft [`LocalModelLoadAdmission.kt:40-51`](../../app/src/main/java/at/heinz/deviceai/localmodel/LocalModelLoadAdmission.kt):
Thermalstufe ≥ 3 → abgelehnt; Android meldet Speichernot → abgelehnt; Datei größer als drei Viertel des
physischen RAM → abgelehnt; fehlende Messwerte → abgelehnt.

Reine GPU- oder NPU-Dateien (ohne CPU-Backend) nimmt der Katalog nicht an. Das bleibt so: der CPU-Rückfall setzt es voraus.

### 2.5 Kontextfenster und Ausgabelimit

- Fenster: 4 096 Token; 6 144 oder 8 192 nur, wenn die Modelldatei ein Maximum > 0 meldet und Gerät, Speicher und
  Thermalstufe es zulassen ([`LocalModelContextWindow.kt`](../../app/src/main/java/at/heinz/deviceai/localmodel/LocalModelContextWindow.kt), Zeile 17).
- **Host-Befund an der Originaldatei:** `gemma-4-E4B-it.litertlm` (SHA-256 `0b2a8980…`, also genau die Datei des
  Besitzers) meldet in LiteRT-LM 0.18.0 `maxContextTokens = 0`. Damit bleibt das Fenster bei E4B **immer 4 096**;
  die Vergrößerung greift bei dieser Datei nie. `gemma-4-E2B-it.litertlm` meldet 32 003 – dort kann das Fenster
  auf 6 144/8 192 wachsen ([`host-modelinfo.txt`](local-model-speed-20261118/host-modelinfo.txt)).
- Ausgabe: höchstens 2 048 Token im Werkzeugpfad, 3 072 im reinen Textpfad bei 4 096 Fenster (4 608 bei 6 144,
  6 144 bei 8 192) – [`LocalModelGenerationPolicy.kt:14-27`](../../app/src/main/java/at/heinz/deviceai/localmodel/LocalModelGenerationPolicy.kt).
  Das sind Obergrenzen, keine Zielwerte.

### 2.6 Was pro Zug eingelesen wird

- Reiner Textzug (die meisten Gesprächsfragen): eigene, werkzeugfreie Konversation, die für jeden Aufruf neu
  angelegt und danach geschlossen wird (Basis-Commit `LiteRtLmLocalModelRuntime.kt:323-338`). Der Prompt wird in
  `AgentActivity` jedes Mal vollständig neu gebaut (Anweisungsblock, kompilierter Kontext, Verlauf, Stilblock,
  Zeitanker; Basis-Commit Zeile 13034–13186). Obergrenze bei 4 096 Fenster: (4 096 − 512 − 512 − 256) × 3 =
  **8 448 Bytes** ([`LocalNativePromptBudget.kt:23-41`](../../app/src/main/java/at/heinz/deviceai/localmodel/LocalNativePromptBudget.kt)).
- Werkzeugzug: zusätzlich das Präfix aus Systemanweisung und 33 Werkzeugbeschreibungen, laut Kommentar im Code
  „≈ 7 kB JSON ≈ 1.8–2.4 k tokens on the host count“ (`LocalNativePromptBudget.kt:29-33`), plus der Prompt.
- Host, bestehender Test `LocalPromptSizeAndStabilityTest`: der kompilierte Kontext für eine kurze Frage ohne
  Erinnerungen ist 1 831 Bytes (mit doppeltem Werkzeugkatalog wären es 4 156).
- Zeichenobergrenzen des Prompts: 6 000 für eine kurze erste Frage, sonst 15 500
  ([`LocalChatPromptBudget.kt:19-20`](../../app/src/main/java/at/heinz/deviceai/cognitive/LocalChatPromptBudget.kt)),
  jeweils begrenzt durch das Byte-Budget oben.

Größenordnung je Zug also etwa 1 000 bis gut 3 000 eingelesene Token (Planungsgrenzen aus dem Code mit dessen
eigener Schätzung von 3 Bytes je Token). **Die tatsächliche Tokenzahl am Gerät ist nicht gemessen.**

### 2.7 Die Laufzeit meldet der App keine Tokenzahlen

Die App fragt nach jedem Zug `getBenchmarkInfo()` ab (Basis-Commit `LiteRtLmLocalModelRuntime.kt:549`). Das
liefert nur etwas, wenn vor dem Erzeugen der Engine `ExperimentalFlags.enableBenchmark = true` gesetzt wurde
(Code, v0.18.0: [`ExperimentalFlags.kt:28-34`](https://github.com/google-ai-edge/LiteRT-LM/blob/v0.18.0/kotlin/java/com/google/ai/edge/litertlm/ExperimentalFlags.kt),
[`session_advanced.cc:409-416`](https://github.com/google-ai-edge/LiteRT-LM/blob/v0.18.0/runtime/core/session_advanced.cc)).
Die App setzt das nirgends. Host-Probe mit litertlm-jvm 0.18.0: `getBenchmarkInfo()` wirft dann
„Benchmark is not enabled“. Folge: Die Anzeige „Tokenzahl nicht gemeldet“ und ein leeres „Tokens pro Sekunde“ im
Evaluationsbericht sind zu erwarten. `getTokenCount()` funktioniert ohne den Schalter (Host: 1 833).

---

## 3. Was die Bibliothek 0.18.0 wirklich enthält (Artefakt geprüft)

`litertlm-android-0.18.0.aar` aus dem Gradle-Cache, SHA-256 `706cbb8a…fab7595f`; `javap` auf `classes.jar`,
`strings` auf die native Bibliothek.

| Befund | Beleg |
|---|---|
| `Backend.CPU(threadCount)`, `Backend.GPU()`, `Backend.NPU(nativeLibraryDir)`, `Backend.GOOGLE_TENSOR()` | `javap`; Quelltext [`Config.kt:133-161`](https://github.com/google-ai-edge/LiteRT-LM/blob/v0.18.0/kotlin/java/com/google/ai/edge/litertlm/Config.kt) |
| `EngineConfig`: `backend`, `maxNumTokens` („equivalent to the size of the kv-cache“), `cacheDir`, `activationDataType` | wie oben, Zeile 181–223 |
| `ConversationConfig`: `initialMessages`, `prefillPrefaceOnInit`, `maxOutputToken`, `thinkingConfig`, `enableSpeculativeDecoding` | wie oben, Zeile 225–285 |
| `Conversation`: `sendMessage…`, `cancelProcess`, `getTokenCount`, `getBenchmarkInfo`; **kein `clone()`** | `javap` |
| `BenchmarkInfo`: Token und Token/s für Einlesen und Schreiben, Zeit bis zum ersten Token | [`Benchmark.kt:19-38`](https://github.com/google-ai-edge/LiteRT-LM/blob/v0.18.0/kotlin/java/com/google/ai/edge/litertlm/Benchmark.kt) |
| `ModelInfo.Llm`: `supportedBackends`, `hasSpeculativeDecodingSupport`, `supportsThinking`, `maxContextTokens` | `javap` |
| Native Bibliotheken im AAR: nur `liblitertlm_jni.so` (arm64-v8a 22,2 MB, x86_64) | `unzip -l` |
| GPU-Beschleuniger ist in diese Bibliothek eingebunden; OpenCL wird vom Gerät nachgeladen | Zeichenketten „Statically linked GPU accelerator registered.“, `libOpenCL.so`, OpenCL-Aufrufe |
| GPU-Sampler-Bibliothek liegt nicht bei; die Bibliothek fällt dann auf CPU-Sampling zurück | Zeichenkette „GPU sampler unavailable. Falling back to CPU sampling…“ |
| Keine Qualcomm-NPU-Bibliotheken im AAR | `unzip -l`; [NPU-Seite](https://ai.google.dev/edge/litert/next/litert_lm_npu) verlangt QAIRT-Bibliotheken zusätzlich |

Die Referenz-App von Google (AI Edge Gallery) nutzt `Backend.GPU()` mit demselben Artefakt in Version 0.18.1
(öffentliche API identisch mit 0.18.0, Abschnitt 5.3) und den zwei Manifest-Einträgen, ohne weitere native
Bibliotheken beizulegen:
[`AndroidManifest.xml`](https://github.com/google-ai-edge/gallery/blob/main/Android/src/app/src/main/AndroidManifest.xml),
[`LlmChatModelHelper.kt`](https://github.com/google-ai-edge/gallery/blob/main/Android/src/app/src/main/java/com/google/ai/edge/gallery/ui/llmchat/LlmChatModelHelper.kt),
[`libs.versions.toml`](https://github.com/google-ai-edge/gallery/blob/main/Android/src/gradle/libs.versions.toml).

---

## 4. Host-Belege

Alle Proben: litertlm-jvm 0.18.0 (gleiche Version wie die App, Desktop-Variante), CPU, 2 Threads, Fenster 4 096.
Der Host ist kein Telefon und lief gleichzeitig andere Builds: absolute Zeiten sagen nichts über das Fold7. Wo
auch das Verhältnis zweier Zeiten nicht belastbar ist (4.3), steht das dabei.

### 4.1 Wiederverwendung der Konversation (KV-Cache)

Modell: `Qwen3-0.6B_dynamic_wi4b32_afp32.litertlm` (343 MB, Apache 2.0, SHA-256 `bf1aba9c…`), gewählt, weil es
klein ist; der Mechanismus liegt in LiteRT-LM, nicht im Modell. Systemtext rund 1 800 Token als Stellvertreter
für das Präfix der App, drei kurze Fragen, höchstens 24 Antwort-Token.
Quelltext [`KvReuseProbe.java`](local-model-speed-20261118/KvReuseProbe.java), Rohausgabe
[`host-kv-reuse-probe-qwen3-0.6b.txt`](local-model-speed-20261118/host-kv-reuse-probe-qwen3-0.6b.txt).

| Muster | Zug | eingelesene Token | Zeit gesamt |
|---|---|---|---|
| A: neue Konversation je Zug, ganzer Verlauf im Prompt (**so arbeitet die App**) | 1 / 2 / 3 | 1 812 / 1 868 / 1 912 | 20,6 s / 18,7 s / 18,1 s |
| B: eine Konversation, nur der neue Zug wird gesendet | 1 / 2 / 3 | 1 809 / 31 / 25 | 18,1 s / 4,0 s / 3,7 s |
| C: Präfix schon beim Anlegen eingelesen (`prefillPrefaceOnInit = true`), dann erster Zug | 1 | 34 | Anlegen 14,8 s, Zug 3,8 s |

Lesart: In Muster B kostet ein Folgezug am Host rund ein Fünftel (Faktor 4,7 bzw. 4,9), weil 98 % der Token
nicht mehr eingelesen werden. Die Tokenzahlen sind exakt und hängen nicht von der Hostlast ab; die Zeiten
schwanken (Zug 1 in A und B: 20,6 s und 18,1 s bei gleicher Arbeit), der Unterschied zwischen den Mustern ist
aber ein Vielfaches davon. Muster C zeigt, dass das Präfix vorab eingelesen werden kann; die Arbeit entfällt
dabei nicht, sie wandert vor den Zug.

### 4.2 Was die Modelldateien selbst melden

[`ModelInfoProbe.java`](local-model-speed-20261118/ModelInfoProbe.java),
[`host-modelinfo.txt`](local-model-speed-20261118/host-modelinfo.txt). Die Dateien wurden nur in ein
Arbeitsverzeichnis außerhalb des Repositorys geladen, gehasht, gelesen und wieder gelöscht.

| Datei | Bytes | SHA-256 (nachgehasht) | Text-Backends | Max. Kontext | Spekulatives Dekodieren |
|---|---|---|---|---|---|
| `gemma-4-E4B-it.litertlm` | 3 659 530 240 | `0b2a8980…45bd52e0` (= Datei des Besitzers) | CPU, GPU | 0 (nicht dynamisch) | ja |
| `gemma-4-E2B-it.litertlm` | 2 588 147 712 | `18193810…7139a63c` | CPU, GPU | 32 003 (dynamisch) | ja |
| `Qwen3-0.6B_dynamic_wi4b32_afp32.litertlm` | 343 490 560 | `bf1aba9c…72ce83e0` | CPU, GPU | 4 096 | nein |

Beide Gemma-4-Dateien melden `supportsFunctionCalling = false` und `supportsThinking = false`. Das ist ein
Metadatenfeld der Datei; ob das Modell Werkzeugaufrufe erzeugt, zeigt erst ein Lauf (Abschnitt 4.3).

### 4.3 Gemma 4 E2B am Host: spekulatives Dekodieren, Deutsch, Werkzeugaufruf

Datei `gemma-4-E2B-it.litertlm` (SHA-256 nachgehasht), Systemanweisung auf Deutsch, drei kurze Aufgaben,
höchstens 96 Antwort-Token, Standard-Sampler der Datei. Zwei Durchgänge mit umgekehrter Reihenfolge.
Quelltext [`Gemma4E2bProbe.java`](local-model-speed-20261118/Gemma4E2bProbe.java), Rohausgaben
[`host-gemma4-e2b-probe-run1-off-first.txt`](local-model-speed-20261118/host-gemma4-e2b-probe-run1-off-first.txt)
(Umlaute dort wegen der Konsolenkodierung als „?“) und
[`host-gemma4-e2b-probe-run2-on-first.txt`](local-model-speed-20261118/host-gemma4-e2b-probe-run2-on-first.txt).
Der Host war währenddessen mit anderen Builds belastet (1-Minuten-Lastmittel zwischen 3,6 und 4,8 bei 4 Kernen).

**Spekulatives Dekodieren** (`ExperimentalFlags.enableSpeculativeDecoding` vor dem Erzeugen der Engine),
Schreibgeschwindigkeit in Token/s für die Aufgaben 1 / 2 / 3:

| Durchgang | aus | an |
|---|---|---|
| 1 (zuerst „aus“, dann „an“) | 10,7 / 13,1 / 13,6 | 12,9 / 15,0 / 16,3 |
| 2 (zuerst „an“, dann „aus“) | 12,8 / 12,9 / 13,1 | 8,3 / 9,9 / 14,3 |

Lesart: Die jeweils zuerst gelaufene Einstellung ist langsamer, gleich welche. Reihenfolge und Fremdlast wirken
hier stärker als der Schalter. **Am Host lässt sich weder ein Gewinn noch ein Verlust belegen.** Belegt ist nur:
die Engine startet mit dem Schalter ohne Fehler, der Wortlaut der Antworten ist in beiden Einstellungen gleich,
und die gezählten Antwort-Token unterscheiden sich reproduzierbar (73 statt 74; an der Obergrenze 98 statt 96) –
ein Hinweis, dass ein anderer Dekodierpfad lief, kein Nachweis seiner Wirkung. Im Cache-Verzeichnis lag nach dem
Lauf neben dem Gewichte-Cache eine eigene Datei `…mtp_drafter.xnnpack_cache` (44 MB); das Entwurfsmodell wurde
also geladen. Die an der Obergrenze abgeschnittene Antwort war mit Schalter zwei Token länger; die Obergrenze
kann also leicht überschritten werden.

Nebenbefund Speicherplatz: Auf der CPU legt LiteRT-LM im `cacheDir` einen Gewichte-Cache an – am Host 788 MB
für E2B und 339 MB für Qwen3 0.6B. Am Telefon ist für ein zweites importiertes Modell also mit Dateigröße plus
Cache zu rechnen; die Größe dort ist nicht gemessen.

**Deutsch und Werkzeugaufruf** (Stichprobe, keine Qualitätsbewertung):

- Die drei Antworten sind flüssiges Deutsch, z. B. „Die Stadtbibliothek verlängert ab Montag ihre Öffnungszeiten
  auf werktags von 8 bis 20 Uhr und samstags von 9 bis 14 Uhr, um der gestiegenen Nachfrage nach Lernplätzen
  gerecht zu werden. Die Ausleihe von Medien bleibt wie gewohnt kostenlos.“ Auffällig: Auf „Formuliere höflicher“
  kam eine Liste mit mehreren Varianten statt einer Formulierung, bis zur Obergrenze.
- Mit einem registrierten Werkzeug `get_weather(city)` und `automaticToolCalling = false`: „Wie ist das Wetter in
  Wien?“ → ein strukturierter Aufruf `get_weather{city=Wien}`, kein Text. „Was ist die Hauptstadt von
  Österreich?“ → kein Aufruf, Antwort „Die Hauptstadt von Österreich ist **Wien**.“

Das zeigt, dass E2B über LiteRT-LM 0.18.0 deutsche Antworten und strukturierte Werkzeugaufrufe liefern kann. Ob
es mit den 33 Werkzeugen der App zuverlässig das richtige wählt, zeigt es nicht.

---

## 5. Antworten auf die Forschungsfragen

### 5.1 (a) Backends in LiteRT-LM Android 0.18.0

| Backend | Voraussetzungen | Bekannte Grenzen / offen | Sicherheit der Aussage |
|---|---|---|---|
| **CPU** (XNNPACK) | keine | Standard 4 Threads, keine Kernbindung (2.3) | hoch (Code) |
| **GPU** (ML Drift, OpenCL) | Manifest: `uses-native-library` für `libvndksupport.so` und `libOpenCL.so` ([Leitfaden](https://ai.google.dev/edge/litert-lm/android)); Modelldatei muss GPU melden (E4B und E2B tun das, 4.2); OpenCL-Treiber am Gerät (Snapdragon 8 Elite: „OpenCL 3.0 FP“ laut [Produktblatt](https://www.qualcomm.com/content/dam/qcomm-martech/dm-assets/documents/Snapdragon-8-Elite-Platform-Product-Brief.pdf)) | Sampling läuft ohne beigelegte Sampler-Bibliothek auf der CPU (Abschnitt 3); erstes Laden dauert länger, `cacheDir` hilft ab dem zweiten ([Leitfaden](https://ai.google.dev/edge/litert-lm/android)); ob 4 096/8 192 Token Fenster, die 33 Werkzeuge und der Abbruch mitten in der Antwort auf dieser GPU stabil laufen, ist nirgends dokumentiert und am Gerät **NOT_RUN** | Voraussetzungen hoch (Leitfaden, Referenz-App); Verhalten am Fold7 unbekannt |
| **NPU** (Qualcomm AI Engine Direct) | eigene, für den Chip übersetzte Modelldatei; QAIRT-Bibliotheken und Dispatch-Bibliothek müssen der App beigelegt werden ([NPU-Seite](https://ai.google.dev/edge/litert/next/litert_lm_npu)) | Für SM8750 gibt es dort nur Gemma3-1B mit **1 280 Token** Kontext (`Gemma3-1B-IT_q4_ekv1280_sm8750.litertlm`) und Gemma 3 270M; das Gemma-4-Repository enthält NPU-Dateien für QCS8275, Google Tensor G5/G6 und Intel, **keine für SM8750** ([Dateiliste E2B](https://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm/tree/main)); die Modellkarte sagt zur QCS8275-Datei ausdrücklich: „Compatibility with other Qualcomm chips or Android runtimes is not established“. 1 280 Token reichen nicht einmal für das Werkzeugpräfix der App (2.6). | hoch, dass es heute nicht passt |
| **GOOGLE_TENSOR** | Pixel-Geräte | nicht relevant | – |

Hersteller-Hinweis am Rand: Die Gemma-4-Modellkarten nennen für unterstützte Android-Geräte den Systemdienst
AICore (Gemini Nano) als empfohlenen Produktionsweg. Das wäre eine andere Schnittstelle (ML Kit) und eine neue
Abhängigkeit; ob das Fold7 dazugehört, habe ich nicht geprüft.

### 5.2 (b) Frei nutzbare Modelle im `.litertlm`-Format

Dateinamen, Größen, Lizenz und Zugangssperre stammen aus der Hugging-Face-Schnittstelle
(`/api/models/<repo>` und `/tree/main`, abgerufen am 18.11.2026). Die Geschwindigkeiten sind
**Herstellerzahlen** des jeweils genannten Geräts; Zeilen mit verschiedenen Geräten sind nicht miteinander
vergleichbar. Direkt vergleichbar sind nur Gemma 4 E2B und E4B (gleiches Gerät, gleiche Methode: 1 024 Token
einlesen, 256 schreiben, Fenster 2 048, CPU mit 4 Threads).

| Modell | Datei (Bytes) | Lizenz · Zugang | Hersteller: Gerät · CPU Einlesen/Schreiben · GPU Einlesen/Schreiben (Token/s) | Werkzeuge | Kontext | Deutsch |
|---|---|---|---|---|---|---|
| **Gemma 4 E4B** (heute) | `gemma-4-E4B-it.litertlm` (3 659 530 240) | Apache 2.0 · frei, ohne Anmeldung | S26 Ultra · 195 / 17,7 (erster Token 5,3 s) · 1 293 / 22,1 (0,8 s) – [Karte](https://huggingface.co/litert-community/gemma-4-E4B-it-litert-lm) | laut [Hauptkarte](https://huggingface.co/google/gemma-4-E4B-it) „native function-calling“ | Karte: bis 32k; Datei meldet 0 (4.2) | Hauptkarte: „35+ languages“, Deutsch nicht einzeln genannt |
| **Gemma 4 E2B** | `gemma-4-E2B-it.litertlm` (2 588 147 712) | Apache 2.0 · frei, ohne Anmeldung | S26 Ultra · 557 / 46,9 (1,8 s) · 3 808 / 52,1 (0,3 s) – [Karte](https://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm) | wie E4B; Host-Stichprobe in 4.3 | Karte: bis 32k; Datei meldet 32 003 | wie E4B; Host-Stichprobe in 4.3 |
| Gemma 3n E2B | `gemma-3n-E2B-it-int4.litertlm` (3 655 827 456) | Gemma-Bedingungen · **gesperrt, manuelle Freigabe** | S24 Ultra · 111 / 16 · 816 / 16 – [Übersicht](https://ai.google.dev/edge/litert-lm/overview) | nicht geprüft (Karte gesperrt) | nicht geprüft | nicht geprüft |
| Gemma 3n E4B | `gemma-3n-E4B-it-int4.litertlm` (4 919 541 760) | Gemma-Bedingungen · gesperrt, manuelle Freigabe | S24 Ultra · 74 / 9 · 548 / 9 – Übersicht | nicht geprüft | nicht geprüft | nicht geprüft |
| Gemma 3 1B | `gemma3-1b-it-int4.litertlm` (584 417 280) | Gemma-Bedingungen · **Anmeldung und Zustimmung** | S24 Ultra · 177 / 33 · 1 191 / 24 – Übersicht | nicht dokumentiert | 4 096 laut Dateiname der gleich großen Variante `…_q4_ekv4096.litertlm` | nicht geprüft |
| Gemma 3 270M | `gemma3-270m-it-q8.litertlm` (304 005 120) | Gemma-Bedingungen · Anmeldung und Zustimmung | keine Zahl gefunden | nicht dokumentiert | nicht geprüft | nicht geprüft |
| FunctionGemma 270M | `mobile_actions_q8_ekv1024.litertlm` (288 964 608) | Gemma-Bedingungen · Anmeldung und Zustimmung | S25 Ultra · 2 238 / 154 · – – Übersicht | ja, dafür feinabgestimmt | 1 024 (Dateiname) | nicht geprüft |
| Qwen2.5 1.5B | `Qwen2.5-1.5B-Instruct_multi-prefill-seq_q8_ekv4096.litertlm` (1 597 922 856) | Apache 2.0 · frei | S25 Ultra, Fenster 4 096 · 162,7 / 26,1 (6,6 s) · – – [Karte](https://huggingface.co/litert-community/Qwen2.5-1.5B-Instruct); Übersicht nennt 298 / 34 · 1 668 / 31 (dort Fenster 1 280) | auf der Karte nicht dokumentiert | 4 096 | auf der Karte nicht dokumentiert |
| Qwen3 0.6B | `Qwen3-0.6B_dynamic_wi4b32_afp32.litertlm` (343 490 560) | Apache 2.0 · frei | Galaxy S26 · 231 / 21,9 · 3 442 / 81,4 (GPU-Pfad braucht 0.19.0) – [Karte](https://huggingface.co/litert-community/Qwen3-0.6B) | Datei meldet `false` (4.2) | 4 096 | Karte nennt als Sprache nur Englisch |
| Phi-4 mini | `Phi-4-mini-instruct_multi-prefill-seq_q8_ekv4096.litertlm` (3 910 090 752) | MIT · frei | S24 Ultra · 66,5 / 7,3 (15,9 s) · 314 / 10,4 – [Karte](https://huggingface.co/litert-community/Phi-4-mini-instruct) | nicht dokumentiert | 4 096 | nicht dokumentiert |

Einordnung:

- **Gemma 4 E2B ist der einzige Kandidat, der zugleich frei ohne Anmeldung, auf demselben Gerät belegt schneller
  als E4B und aus derselben Familie ist** (gleiche Vorlage für System-, Werkzeug- und Gesprächsrollen; die App
  erkennt die Kennung bereits im Ausgabebudget). Verhältnis nach Herstellerzahlen: Einlesen ×2,9, Schreiben ×2,6.
- Gemma 3n ist größer, gesperrt und in den Herstellerzahlen (älteres Gerät, daher nicht direkt vergleichbar)
  langsamer; nichts spricht dafür, es Gemma 4 E2B vorzuziehen. Dasselbe gilt für Phi-4 mini gegenüber E4B.
- Die Modelle unter 1,5 Mrd. Parametern (Gemma 3 1B, 270M, Qwen3 0.6B) sind kleiner, aber für deutsches
  Gespräch mit 33 Werkzeugen nicht belegt; FunctionGemma und die NPU-Varianten haben zu kleine Fenster.
- Qwen2.5 1.5B ist frei und klein, auf der Karte aber ohne Angabe zu Werkzeugen und Deutsch; als zweiter
  Vergleichskandidat denkbar, nicht empfohlen ohne Messung.

### 5.3 (c) Neuere LiteRT-LM-Version

Google Maven ([`group-index.xml`](https://dl.google.com/dl/android/maven2/com/google/ai/edge/litertlm/group-index.xml)):
nach 0.18.0 gibt es **0.18.1, 0.19.0 und 0.19.1** (neueste, Metadaten vom 05.11.2026).

| Version | Was sich ändert | Quelle |
|---|---|---|
| 0.18.1 | Korrektur einer ABI-Unverträglichkeit für EmbeddingGemma 2 beim Bauen aus dem Quelltext. Öffentliche Kotlin-API identisch mit 0.18.0. | [Releases](https://github.com/google-ai-edge/LiteRT-LM/releases); `javap`-Vergleich beider AARs: keine Differenz |
| 0.19.0 | Schnelleres „constrained decoding“ auf der GPU, auch mit spekulativem Dekodieren; GPU-Sampling liefert bei gleichem Startwert dasselbe wie CPU; `ModelInfo` schneller; C-API 1.0.0 (Bruch nur in der C-Schnittstelle) | [README „What's New: v0.19.0“](https://github.com/google-ai-edge/LiteRT-LM/blob/main/README.md) |
| 0.19.1 | Bild und Ton für Gemma 4 auf einem weiteren GPU-Backend | Releases |

Im AAR 0.19.1 gegenüber 0.18.0 neu (`javap`-Vergleich):

- **`Conversation.clone()`** und `Session.clone()` – „sharing the current history, KV cache state, and configuration“
  ([`Conversation.kt` auf `main`](https://github.com/google-ai-edge/LiteRT-LM/blob/main/kotlin/java/com/google/ai/edge/litertlm/Conversation.kt)).
  Das ist der Baustein, um ein einmal eingelesenes Präfix je Zug zu kopieren statt neu zu rechnen (Abschnitt 9, Stufe 3).
- `SpeculativeDecodingConfig` mit `OFF`, `ON`, `ADAPTIVE`; `ExperimentalFlags.enableYnnpack`; zusätzliche Felder in `BenchmarkInfo`.
- Alle Signaturen, die die App benutzt, sind noch vorhanden; die POM-Abhängigkeiten sind unverändert.

Bewertung: 0.18.1 bringt für diese App nichts. 0.19.1 ist laut Signaturvergleich quelltextverträglich, aber
**nicht übersetzt und nicht ausgeführt**; eine neue native Bibliothek kann sich am Gerät anders verhalten. Deshalb
hier kein Versionswechsel. Sinnvoll wird er zusammen mit Stufe 3 aus Abschnitt 9, nach einem Gerätelauf.
Die Release-Notizen habe ich über die Release-Seite gelesen (zusammengefasst, nicht wörtlich); die GitHub-API
war in dieser Sitzung gesperrt.

### 5.4 (d) Günstige Verbesserungen ohne neues Modell

1. **Präfix und Verlauf nicht jedes Mal neu einlesen** – siehe 4.1 und Abschnitt 9. Größter Hebel.
2. **Spekulatives Dekodieren (MTP).** Die Datei des Besitzers bringt den Entwurfsteil mit (4.2), die API hat den
   Schalter (`ConversationConfig.enableSpeculativeDecoding`, `Config.kt:251-258`), die Ausgabe bleibt laut Anbieter
   gleich ([Blog](https://blog.google/innovation-and-ai/technology/developers-tools/multi-token-prediction-gemma-4/):
   „Zero quality degradation“). Hersteller, S26 Ultra, **E4B auf CPU**: 17,0 → 21,1 bis 29,5 Token/s je nach
   Aufgabe, Speicher 2 800 → 2 922 bis 3 116 MB. **E2B auf CPU**: 40,7 → 36,3 bis 47,5, also teils langsamer.
   Wärme ist nirgends angegeben. Am Host startet der Schalter mit der E2B-Datei, ein Geschwindigkeitseffekt
   war dort nicht belegbar (4.3). Nicht eingebaut.
3. **Threads:** Kühlmodus (2) gegen ausgewogen (4) mit der Evaluation vergleichen; der Schalter existiert.
   Erwartung laut Herstellermethode: 4 Threads schneller; ob die Wärme je Antwort dann höher oder – wegen der
   kürzeren Rechenzeit – ähnlich ist, ist offen.
4. **Antwortlänge in gesprochenen Zügen:** Der Stilblock verlangt dort schon „ein bis drei kurze Sätze“
   ([`ConversationRegister.kt:58-65`](../../app/src/main/java/at/heinz/deviceai/cognitive/ConversationRegister.kt)),
   die harte Obergrenze bleibt aber 2 048/3 072 Token, und der Textpfad sagt weiterhin „Antworte direkt und
   ausführlich“ (Basis-Commit `AgentActivity.kt:13024`). Eine kleinere Obergrenze nur für gesprochene Züge würde
   den schlimmsten Fall begrenzen. Nicht eingebaut (zentraler Pfad).
5. **Zusätzliche Modellaufrufe vor der Antwort:** Im Werkzeugpfad kann `ChatReasoningRouter` bis zu vier weitere
   Aufrufe mit zusammen 20 s Frist starten ([`ChatReasoningRouter.kt:12-13`](../../app/src/main/java/at/heinz/deviceai/cognitive/ChatReasoningRouter.kt),
   Basis-Commit `AgentActivity.kt:13159-13166`). Wie oft das am Gerät passiert, zeigt das Laufzeitprofil (Abschnitt 8.4).
6. **Präfix beim Laden nicht wegwerfen:** Beim Laden wird das Werkzeugpräfix einmal eingelesen, nur um es zu
   zählen, dann verworfen (Basis-Commit `LiteRtLmLocalModelRuntime.kt:205-212`); der erste Werkzeugzug liest es
   erneut ein. Einmaliger Effekt nach dem Laden, klein.
7. **Messbarkeit:** `ExperimentalFlags.enableBenchmark` beim Laden würde Token/s für Einlesen und Schreiben liefern
   (2.7). Nicht eingebaut, weil es den zentralen Pfad berührt: mit Tokenzahlen würde auch die tokenbasierte
   Ausgabelimit-Erkennung der App aktiv, die ohne Tokenzahlen nicht auslösen kann.

„Thinking“ kostet nach Aktenlage keine versteckten Token: Die Vorlage setzt das Denk-Token nur, wenn
`enable_thinking` gesetzt ist ([`chat_template.jinja`](https://huggingface.co/litert-community/gemma-4-E4B-it-litert-lm/blob/main/chat_template.jinja), Zeile 157–162),
und die App übergibt weder `thinkingConfig` noch `extraContext`. Am Gerät nicht geprüft.

---

## 6. Optionen im Überblick

| Option | Erwarteter Effekt (Quelle) | Risiko | Aufwand | Am Gerät zu messen |
|---|---|---|---|---|
| **A · Konversation/KV-Cache wiederverwenden** (Abschnitt 9) | Folgezüge lesen nur den neuen Zug ein. Host: 31/25 statt 1 868/1 912 Token, 4,0/3,7 s statt 18,7/18,1 s (4.1). Weniger Rechenarbeit, daher voraussichtlich auch weniger Wärme (Schluss, nicht gemessen). | Hoch: zentraler Antwortpfad, Verlaufsfilter und Widerruf müssen neu gedacht werden; eine abgebrochene Konversation ist laut [`conversation.h:634-637`](https://github.com/google-ai-edge/LiteRT-LM/blob/v0.18.0/runtime/conversation/conversation.h) nicht weiterverwendbar | groß, in Stufen | Zeit bis zum ersten Text je Zug 1–6, Thermalstufe nach 6 Zügen, vorher/nachher |
| **B · Gemma 4 E2B statt E4B** | Hersteller, S26 Ultra, CPU 4 Threads: Einlesen ×2,9, Schreiben ×2,6; Speicher 1 733 statt 3 283 MB ([E2B](https://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm), [E4B](https://huggingface.co/litert-community/gemma-4-E4B-it-litert-lm)) | Antwortqualität unbekannt; Fenster kann auf 8 192 wachsen und der Prompt damit länger werden (2.5); kein automatisches Laden nach Neustart | klein: Import ist vorausgefüllt | Evaluation: formal bestanden, Zeit, Zeichen/s, Thermalstufe; `contextTokens` im Bericht |
| **C · GPU-Backend** (Schalter eingebaut, aus) | Hersteller, S26 Ultra, E4B: Einlesen ×6,6 (1 293/195), Schreiben ×1,25 (22,1/17,7), CPU-Speicher 710 statt 3 283 MB | Unbekannt: Start, Stabilität, Wärme, Ladezeit am Fold7; nativer Absturz möglich (Absturzschutz vorhanden) | klein für den Versuch | lädt es? `backend` im Bericht; Zeit bis zum ersten Text, Zeichen/s, Thermalstufe; drei Ladevorgänge hintereinander |
| **D · Spekulatives Dekodieren** | Hersteller, S26 Ultra, CPU: E4B 17,0 → 21,1–29,5 Token/s; E2B 40,7 → 36,3–47,5 | Mehr Speicher (+120 bis +320 MB bei E4B); Wärme unbekannt; bei E2B auf CPU teils langsamer | klein, aber im zentralen Pfad | Zeichen/s mit/ohne, Thermalstufe, gleiche Antworten? |
| **E · 4 statt 2 Threads** | Herstellerzahlen gelten für 4 Threads; Faktor am Fold7 unbekannt | Wärme – der Grund für den Kühlmodus | keiner (Schalter existiert) | Evaluation Kühlmodus gegen ausgewogen |
| **F · Kürzere gesprochene Antworten** | Schreibzeit ist proportional zur Tokenzahl | kürzere Antworten | klein, zentraler Pfad | Antwortlänge und Gesamtzeit in gesprochenen Zügen |
| **G · NPU** | Hersteller nennt für andere Chips hohe Werte | Für SM8750 gibt es kein Gemma 4, nur 1 280 Token Kontext (5.1) | groß, neue native Bibliotheken | derzeit nichts |
| **H · LiteRT-LM 0.19.1** | Voraussetzung für `clone()` (Abschnitt 9, Stufe 3) | neue native Bibliothek, ungeprüft | mittel | vollständiger Regressionslauf, dann A |

Rechenbeispiel, **nur mit Herstellerzahlen des S26 Ultra bei 4 Threads und nur zur Einordnung der Hebel**
(kein Fold7-Wert, keine Vorhersage): ein Zug mit 3 000 eingelesenen und 150 geschriebenen Token.

| Fall | Einlesen | Schreiben | Summe |
|---|---|---|---|
| E4B, CPU | 3 000 / 195 = 15,4 s | 150 / 17,7 = 8,5 s | 23,9 s |
| E4B, CPU, nur 300 neue Token eingelesen (Option A) | 1,5 s | 8,5 s | 10,0 s |
| E2B, CPU | 5,4 s | 3,2 s | 8,6 s |
| E4B, GPU | 2,3 s | 6,8 s | 9,1 s |
| E2B, GPU | 0,8 s | 2,9 s | 3,7 s |

Das Beispiel zeigt nur, dass bei kurzen Antworten das Einlesen den größeren Teil der Rechenzeit ausmacht. Mit
2 Threads auf dem Fold7 sind die CPU-Werte voraussichtlich größer; gemessen ist das nicht.

---

## 7. Empfehlung in Rangfolge

1. **Einlesen vermeiden (Option A).** Einziger Hebel, der Rechenarbeit streicht statt sie zu beschleunigen – wirkt
   auf Zeit und Wärme, unabhängig von Modell und Backend. Beleg: Mechanismus im LiteRT-LM-Quelltext, Host-Probe 4.1.
   Nicht umgesetzt; als nächste Entwicklungsarbeit in Abschnitt 9 beschrieben. Vorher am Gerät klären, wie groß
   der Einlese-Anteil wirklich ist (Abschnitt 8.4).
2. **Gemma 4 E2B gegen E4B messen (Option B).** Sofort möglich, kein Risiko für die App, frei und ohne Anmeldung.
   Senkt die Rechenarbeit je Token und damit voraussichtlich auch die Wärme. Entscheidet sich an der Antwortqualität.
3. **GPU-Schalter ausprobieren (Option C).** Größter Herstellergewinn beim Einlesen, aber vollständig ungeprüft am
   Gerät. Erst mit E4B, dann mit dem Sieger aus Schritt 2.

Danach, nach Messung: D (spekulatives Dekodieren) für E4B auf CPU, E (Threads), F (Antwortlänge). G und H nicht jetzt.

Die Schritte 2 und 3 liefern außerdem die Zahlen, die für Schritt 1 fehlen.

---

## 8. Messprotokoll am Gerät

Werkzeug ist der vorhandene Evaluationslauf ([`docs/evaluation/continuum-eval-v1/README.md`](../evaluation/continuum-eval-v1/README.md),
[`ModelEvaluation.kt`](../../app/src/main/java/at/heinz/deviceai/evaluation/ModelEvaluation.kt)). Er misst das
Rohmodell im werkzeugfreien Textpfad mit Prompts bis 6 000 Bytes. Für den echten Gesprächspfad mit Verlauf und
Werkzeugpräfix kommt Abschnitt 8.4 dazu.

### 8.1 Vorbereitung (für jeden Lauf gleich)

1. Akku über 50 %, nicht am Ladegerät, Energiesparen aus, Gerät fünf Minuten liegen und abkühlen lassen,
   Continuum im Vordergrund, zugeklappt oder aufgeklappt immer gleich.
2. Modellzentrum öffnen. Unter „WÄRME · RECHENBUDGET“ die Thermalstufe ablesen: sie muss 0 sein, sonst warten.
3. Nach jeder Änderung von Modell, Kühlmodus oder Beschleuniger: **Modell entladen und neu laden.**

### 8.2 Konfigurationen

| Nr. | Modell | Beschleuniger | Kühlmodus | Zweck |
|---|---|---|---|---|
| 1 | Gemma 4 E4B | CPU | an (2 Threads) | Ausgangslage |
| 2 | Gemma 4 E4B | CPU | aus (4 Threads) | Option E |
| 3 | Gemma 4 E4B | GPU | – | Option C |
| 4 | Gemma 4 E2B | CPU | an | Option B |
| 5 | Gemma 4 E2B | CPU | aus | B + E |
| 6 | Gemma 4 E2B | GPU | – | B + C |

E2B importieren: Modellzentrum → „KLEINER · GEMMA-4-E2B-QUELLE ÖFFNEN“, die Datei `gemma-4-E2B-it.litertlm`
(2,59 GB) herunterladen → „KLEINER · GEMMA-4-E2B-IMPORT VORAUSFÜLLEN“ → Datei auswählen. Laden über
„2 · MODELL LADEN“. GPU: Modellzentrum → „BESCHLEUNIGER · GPU VERSUCHEN (EXPERIMENTELL)“ → bestätigen → Modell
entladen und neu laden. Erscheint danach ein Hinweis, dass das Modell auf der CPU läuft, ist die Konfiguration
„GPU nicht möglich“ – das ist ein Ergebnis, kein Fehler des Protokolls.

### 8.3 Ablauf je Konfiguration

1. „4 · EVALUATION · ENTWICKLUNGSSATZ“ starten (34 Fälle), Gerät liegen lassen.
2. Bericht sichern: `Download/Continuum/continuum-eval-dev-<zeit>.json`.
3. Fünf Minuten Pause, Thermalstufe 0 abwarten, **zweiter Lauf** derselben Konfiguration (Streuung).
4. Nächste Konfiguration. Reihenfolge an einem zweiten Tag umdrehen, wenn Zeit ist.
5. „5 · EVALUATION · HOLDOUT“ nur einmal je Konfiguration und erst, wenn die Wahl auf dem Entwicklungssatz steht.

Aus jedem Bericht vergleichen:

| Feld | Bedeutung für diese Frage |
|---|---|
| `meta.backend`, `meta.backendRequested`, `meta.backendNotice` | lief wirklich, was eingestellt war? (neu) |
| `meta.contextTokens`, `meta.computeThreads` | Fenster und Threads des Laufs (neu) |
| `meta.modelSha256` | welche Datei |
| `latencyMs.firstTextP50` / `P95` | Zeit bis zum ersten Text ≈ Einlesen |
| `latencyMs.totalP50` / `P95` | Gesamtzeit je Antwort |
| `charactersPerSecondMean` | Schreibgeschwindigkeit (steht jetzt auch in der Zusammenfassung) |
| `tokensPerSecondMean` | voraussichtlich leer (2.7) |
| `formal.pass` / `fail`, `heuristic.pass` / `fail` | Antwortqualität |
| `outputLimitReached` | abgeschnittene Antworten |
| `thermalStatus.max`, je Fall `thermalBefore` / `thermalAfter` | Wärme; ab welchem Fall steigt die Stufe? |
| `stoppedEarly`, `casesRun` | hat die Wärme den Lauf beendet? |
| `batteryPercent.start` / `end`, `availableRamBytesMin` | Energie und Speicher |

### 8.4 Echter Gesprächspfad (Einlese-Anteil, Wärme nach mehreren Zügen)

Je Konfiguration, nach Abkühlen, ein neues Gespräch mit diesen sechs Zügen, immer gleich getippt (nicht gesprochen):

1. „Was ist der Unterschied zwischen Wetter und Klima?“
2. „Erklär mir das zweite noch einmal mit einem Beispiel.“
3. „Warum ist der Himmel blau?“
4. „Und warum ist er abends rot?“
5. „Fass unser Gespräch in drei Sätzen zusammen.“
6. „Schreib mir dazu einen kurzen Merksatz.“

Die Fragen sind so gewählt, dass das Modell antworten muss und der Verlauf wächst. Uhrzeit, Rechenaufgaben mit
Gegenständen und Gerätebefehle beantwortet die App teils über feste Pfade ohne Modell; solche Züge messen nichts.

Nach jedem Zug notieren: Sekunden bis zum ersten Text und bis zum Ende (Anzeige im Arbeitsfenster: „… Zeichen ·
… Chunks · … s“), die Thermalstufe im Modellzentrum, und ob der Hitzeschutz gestoppt hat. Nach Zug 1, 3 und 6
zusätzlich das Laufzeitprofil öffnen (Lernzentrum → „EINE HAUPT-APP · LERNSTAND UND DATENÜBERNAHME“, Abschnitt
„Letztes numerisches Laufzeitprofil“) und abschreiben:

- die Profilnummer (`INFERENCE_PROFILE #n`): sie muss seit dem letzten Ablesen gestiegen sein, sonst hat kein
  Modellaufruf stattgefunden und der Zug zählt nicht; steigt sie je Zug um mehr als eins, liefen zusätzliche
  Modellaufrufe (5.4, Punkt 5),
- `FIRST_NATIVE_TEXT_CALLBACK` minus `NATIVE_DISPATCH_ENTER` = Einlesen bis zum ersten Text,
- `NATIVE_TERMINAL_CALLBACK_ENTER` minus `FIRST_NATIVE_TEXT_CALLBACK` = Schreiben,
- `textCharacters`, und die Art des Aufrufs (`CHAT`, `ISOLATED_THINKING`, `TOOL_CONTINUATION`).

Der Werkzeugpfad mit seinem Präfix aus 33 Werkzeugen wird von diesen sechs Zügen nicht erfasst. Wer ihn messen
will, nimmt eine Geräteaufgabe aus dem eigenen Alltag und zählt sie nur, wenn die Profilnummer gestiegen ist.

Ist der Einlese-Anteil in Zug 2 bis 6 mehr als die Hälfte der Gesamtzeit, bestätigt das Option A als nächsten Schritt.

### 8.5 Entscheidungsregeln

- Ein Modell- oder Backendwechsel gilt nur, wenn er im Holdout formal nicht schlechter ist **und** bei Zeit,
  Thermalstufe und Speicher besser (Regel aus dem Evaluations-README).
- GPU gilt erst als benutzbar, wenn drei Ladevorgänge und zwei vollständige Evaluationsläufe ohne Absturz,
  ohne Rückfall und ohne höhere maximale Thermalstufe als die CPU-Konfiguration durchlaufen.
- Zwei Läufe derselben Konfiguration, die sich um mehr als ein Fünftel unterscheiden, werden wiederholt.

---

## 9. Folgearbeit Nr. 1: Konversation und KV-Cache wiederverwenden

Nicht in diesem Auftrag umgesetzt, weil es den zentralen Antwortpfad umbaut.

**Heute.** `stream()` → `selectLocalConversation(freshPrompt = true)` schließt die alte und erzeugt eine neue
Konversation; `streamTextOnly()` erzeugt je Aufruf eine eigene und schließt sie am Ende. Die Budgetprüfung
`LocalNativePromptBudget.plan(fresh = true)` verlangt ausdrücklich `existingTokens == 0` (Zeile 55). Nur
Werkzeugantworten innerhalb eines Zuges laufen in derselben Konversation weiter.

**Was LiteRT-LM kann.** Eine weiterverwendete Konversation rendert je Nachricht nur den Zuwachs und liest nur
diesen ein (`conversation.cc:300-323`); `prefillPrefaceOnInit` liest das Präfix beim Anlegen ein
(`conversation.cc:445-485`); ab 0.19.1 kopiert `clone()` Verlauf und KV-Cache.

### Stufe 1 – Präfix behalten (0.18.0, klein)

- `LiteRtLmLocalModelRuntime.startEngine`: die mit `prefillPrefaceOnInit = true` angelegte Konversation als
  Startkonversation behalten, statt sie zu schließen und eine zweite anzulegen.
- `LocalNativePromptBudget.plan`: einen dritten Fall „frischer Prompt auf unbenutzter, vorbefüllter Konversation“
  zulassen (`existingTokens == prefaceTokens`), mit derselben Schätzung wie `fresh`.
- Wirkung: nur der erste Werkzeugzug nach dem Laden. Tests: `LocalNativePromptBudgetTest`, `LocalConversationBoundaryTest`.

### Stufe 2 – eine Textkonversation je Gesprächssitzung (0.18.0, der eigentliche Gewinn)

- Laufzeit: `streamTextOnly` erhält einen Sitzungsschlüssel; die Textkonversation bleibt zwischen den Zügen
  offen. Neue Methode zum ausdrücklichen Verwerfen. Budget über `getTokenCount()` (funktioniert ohne
  Benchmark-Schalter, 2.7) statt über die Byte-Schätzung des Fortsetzungsfalls, die heute ein Byte als ein Token zählt.
- `AgentActivity` (Bereich um Basis-Commit Zeile 13034–13260): den Prompt in einen festen Sitzungsanfang
  (Anweisungen, Identität) und einen Zuganteil (neue Frage, in diesem Zug gewählte Erinnerungen, Zeitanker,
  Stilblock) teilen. Der Verlauf wird nicht mehr als Text mitgeschickt.
- **Die Konversation muss verworfen und neu aufgebaut werden bei:** Abbruch oder Hitzestopp (eine abgebrochene
  Sitzung ist laut `conversation.h:634-637` „poisoned“), Modellwechsel oder Entladen, neuem Gespräch, Wechsel des
  Internetmodus oder einer Freigabe, Widerruf oder Korrektur einer Erinnerung, Wechsel des Dokuments, Fehler,
  und sobald `getTokenCount()` sich dem Fenster nähert.
- Offene Entscheidung, nicht technisch: Heute läuft der Verlauf in jedem Zug durch
  `LocalConversationPromptFirewall.safeHistory` und frühere Modellantworten gelten als unbestätigte Daten. In
  einer weiterverwendeten Konversation stehen frühere Prompts und Antworten unverändert im nativen Kontext und
  lassen sich nachträglich nicht filtern – nur durch Verwerfen. Das muss der Besitzer bzw. die Architektur billigen.
- Technische Falle: Der neu gerenderte Text muss mit dem alten beginnen, sonst bricht LiteRT-LM ab
  (`conversation.cc:316-320`); Vorlagenparameter dürfen sich innerhalb einer Sitzung nicht ändern.
- Tests: Matrix der Verwerfungsgründe mit einer Schein-Laufzeit; die Host-Probe 4.1 als wiederholbarer Beleg;
  am Gerät Abschnitt 8.4 vorher/nachher.

### Stufe 3 – Präfix kopieren statt neu einlesen (braucht ≥ 0.19.1)

- Für den Werkzeugpfad: eine Vorlage-Konversation mit eingelesenem Präfix (System + 33 Werkzeuge) je Engine, je
  Zug `clone()`. Die heutige Regel „jeder Zug beginnt ohne nativen Verlauf“ bleibt erhalten; es entfällt das
  Einlesen von rund 1,8–2,4 Tsd. Token Präfix je Werkzeugzug (Zahl aus dem Codekommentar, Host-Zählung).
- Voraussetzung: Versionswechsel (5.3) mit vollständigem Regressionslauf; am Gerät messen, was `clone()` an Zeit
  und Speicher kostet.

Ein Vorwärmen der nächsten Konversation im Leerlauf (Host-Muster C) verkürzt nur die Wartezeit; die Rechenarbeit
bleibt, und sie ist verloren, wenn der nächste Zug ein Textzug ist. Für das Wärmeproblem ist das kein Gewinn.

---

## 10. Was in diesem Auftrag am Code geändert wurde

| Änderung | Dateien | Tests (Host) | Am Gerät |
|---|---|---|---|
| Gemma 4 E2B im Modellzentrum vorausgefüllt (Kennung `gemma4-e2b-it`, SHA-256, Quelle, eigener Status) | `ContinuumSelectedModels.kt`, `AgentActivity.kt` | `ContinuumSelectedModelsTest` (7, davon 2 neu), `ContinuumFastModelImportUiTest` (2, neu; echter Dialog unter Robolectric) | NOT_RUN |
| Schalter „Beschleuniger: CPU (Standard) / GPU (experimentell)“: aus; GPU nur, wenn die Datei sie meldet; Rückfall auf CPU bei Fehlstart; Absturzschutz über gespeicherten Marker für das Laden und die erste GPU-Antwort | `LocalModelAcceleration.kt`, `LocalModelAccelerationHost.kt` (neu), `LiteRtLmLocalModelRuntime.kt`, `LocalModelHost.kt` (nur in `get()`: Controller holen, der Laufzeit übergeben, Laufzeit vor dem Hitzeschutz in den Guard hüllen), `AndroidManifest.xml` (2 Einträge, `required="false"`), `AgentActivity.kt` | `LocalModelAccelerationTest` (18), `LocalModelAccelerationHostUiTest` (7) | **nativer GPU-Start NOT_RUN** |
| Evaluationsbericht nennt Backend, Schalterstellung, Rückfallhinweis, konfiguriertes Fenster, Threads; Zusammenfassung zeigt Zeichen/s | `ModelEvaluation.kt`, `AgentActivity.kt`, `docs/evaluation/continuum-eval-v1/README.md` (Feldliste) | `ModelEvaluationTest` (12, davon 2 neu) | NOT_RUN |
| Kommentar zur Threadzahl berichtigt | `LocalModelComputeBudget.kt`, `LiteRtLmLocalModelRuntime.kt` | – | – |

Commits (lokal, in dieser Reihenfolge): `ae9d797c2` (E2B-Vorausfüllung, unabhängig), `3d880058b`
(Beschleuniger-Schalter), `470d122a7` (Evaluationsbericht, setzt `3d880058b` voraus).

Testlauf am Host auf dem Endstand (`:app:testDebugUnitTest` mit Auswahl): **56 Klassen, 423 Tests, 0 Fehler.**
Enthalten sind alle `localmodel`- und `evaluation`-Tests, `ContinuumSelectedModelsTest`,
`ContinuumGemmaRestorePolicyTest`, `LocalPromptSizeAndStabilityTest`, `OwnerLanguageStyleTest` sowie 24
Robolectric-Testklassen rund um die Activity, ausgewählt nach Bezug zum lokalen Modellpfad, zum Modellzentrum
oder zum Importdialog (darunter `ContinuumModelImportUiTest`, `AgentActivityReopenLifecycleUiTest`,
`ContinuumDocumentFlowUiTest`, `OwnerFactRevocationUiTest`). Die gesamte Testsuite wurde nicht ausgeführt.

Unverändert: Hitzeschutz, Ladeprüfung, Thread-Budget, die LiteRT-LM-Version und der Antwortpfad der Laufzeit
(`streamMessage`). Vor der Laufzeit sitzt neu ein Durchreicher für den Absturzschutz; bei einer CPU-Engine tut er nichts.

---

## 11. Nicht geprüft, nicht erreichbar

- **Am Gerät: alles.** Kein GPU-Start, keine Geschwindigkeit, keine Wärme, kein Speicher, keine Antwortqualität
  von E2B.
- Host-Proben laufen mit der Desktop-Variante derselben Bibliotheksversion, nicht mit der Android-Bibliothek.
- LiteRT-LM 0.19.1 wurde nur als Signaturvergleich betrachtet, nicht übersetzt.
- Modellkarten der gesperrten Repositories (Gemma 3n, Gemma 3 1B, Gemma 3 270M, FunctionGemma) waren ohne
  Anmeldung nicht lesbar (HTTP 401); Dateilisten und Lizenzangaben stammen aus der offenen Schnittstelle.
- GitHub-API für `google-ai-edge/LiteRT-LM`: in dieser Sitzung gesperrt (HTTP 403). Quelltexte habe ich über
  die Rohdateien gelesen, Release-Notizen über die Webseite (zusammengefasst wiedergegeben).
- Qualcomm-Produktseite und Samsung-Produktseite lieferten keinen lesbaren Inhalt; verwendet ist das
  Qualcomm-Produktblatt (PDF, SM8750-AB). Dass das Fold7 den „Snapdragon 8 Elite for Galaxy“ mit 12 GB hat,
  stammt aus dem Auftrag, nicht aus einer von mir gelesenen Samsung-Seite.
- Ob das Fold7 Gemma 4 über AICore anbietet, ist nicht geprüft.
