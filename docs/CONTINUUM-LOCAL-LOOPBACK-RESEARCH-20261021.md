# Continuum · lokal betriebener Sprachkern als sicherer Ko-Prozessor

Datum 21.10.2026 · Draft PR #48 · **EXPERIMENT / kein produktiver Modellbetrieb**

## Neue, überprüfbare Architekturgrenze

Node/Termux erhält einen **optionalen localhost-Client** in `scripts/continuum-local-brain.mjs` sowie ein schreibgeschütztes CLI und ein ausdrücklich manuell zu startendes `llama.cpp`-Serverskript. Die vorhandenen SWB1-, CDX1-, NEURON-, Teamleiter-, APK- und Besitzer-Gates bleiben unverändert. Es werden keine Modellgewichte verändert und keine fremden Coding-Agent-Anwendungen übernommen.

- Chat: genau `http://127.0.0.1:8089/v1/chat/completions`, ausschließlich POST, fixiertes Loopback-Ziel, verpflichtender Bearer-Token, begrenzte Eingabe-/Ausgabegrößen, bis 60 Sekunden Timeout, Antworten als unbestätigte Daten.
- Schatten-Patch: Schema `{file,oldText,newText}`, nur Dateinamen aus einer explizit gelieferten Positivliste; Kontroll-/Test-/Git-Pfade ausgeschlossen, JSON und Ausgabe unabhängig geprüft. **Kein Patch wird angewendet**. Eigener `repair-critic`, getrennte Worktrees, Test-Gates und Owner-Review bleiben weiterhin notwendig.
- Health: `/health`, keine Geheimnisse und keine gespeicherten Lerndaten im Request.
- Launcher `scripts/continuum-local-llama-termux.sh`: ausschließlich ARM64/Termux, nur mit bereits installierter Server-Binärdatei und bewusst ausgewählter GGUF, zufälliger owner-only API-Schlüssel, Host 127.0.0.1 und Port 8089, nur eine parallele Inferenz. Keine Downloads, kein versteckter Daemon, keine Modifikationen am Repository.
- **Sicherheits-Extra der Recherche:** Cache-Wiederverwendung aus: `--no-cache-idle-slots --cache-ram 0 --no-cache-prompt`. Grund: 2026 wurde eine potenzielle Slot-/Gesprächsvermischung im llama.cpp-Server gemeldet. Das ist noch kein Beweis gegen alle Versionen, aber für private Gesprächsdaten ein sinnvoller vorläufiger Ausschluss.

## Wissenschaftlicher und technischer Quellenabgleich

1. Offizielle llama.cpp API-/Server-Spezifikation für Chat-Completions, JSON-Schema-Ausgabe, API-Key-Datei, Health und Cache-Optionen: https://github.com/ggml-org/llama.cpp/blob/master/tools/server/README.md
2. ARM64/Termux-Bau (CMake, CPU-Kernel, Android): https://github.com/ggml-org/llama.cpp/blob/master/docs/android.md und https://github.com/ggml-org/llama.cpp/blob/master/docs/build.md
3. CRANE: Banerjee et al., ICML 2025; strukturierte Endausgaben verbessern Syntaxkonformität, ohne dass logische Korrektheit garantiert wird: https://proceedings.mlr.press/v267/banerjee25a.html
4. 2026er Studie zu Codegenerierung mit kompakten Modellen: Journal of Systems and Software 236 (2026), 112815, https://doi.org/10.1016/j.jss.2026.112815 – Qualität und Ressourcenbedarf sind modell-/aufgabenspezifisch.
5. Gemeldete llama.cpp-Cache-Kontamination, 2026: https://github.com/ggml-org/llama.cpp/issues/27148 – ungeprüfter Android-Bezug, deshalb vorläufige Vermeidungsstrategie.
6. Initialer **kleiner Modellkandidat**: ggml-org/Qwen3.5-0.8B-GGUF, Q4_0, Apache-2.0; 563 MB; Herausgeber-SHA-256 `57d1997790d1744fba5b40a7317df71ea5e2acee28c47e78f0cce39c0703f8cf`; Modell-URI: https://huggingface.co/ggml-org/Qwen3.5-0.8B-GGUF/blob/9447f74101aeb4e93621884dfa36ee8effb8831b/Qwen3.5-0.8B-Q4_0.gguf
7. Alternative für Coding-Benchmarks: Qwen/Qwen2.5-Coder-1.5B-Instruct-GGUF Q4_K_M, Apache-2.0, 1.12 GB, Herausgeber-SHA-256 `cc324af070c2ecbfd324a30884d2f951a7ff756aba85cb811a6ec436933bb046`: https://huggingface.co/Qwen/Qwen2.5-Coder-1.5B-Instruct-GGUF/blob/main/qwen2.5-coder-1.5b-instruct-q4_k_m.gguf

**Diese GGUF-Dateien sind nicht automatisch vorhanden oder heruntergeladen worden.** Keine Leistungsversprechen vor Samsung-Messungen.

## Prüfungen

```sh
node --test scripts/continuum-local-brain.test.mjs
node --check scripts/continuum-local-brain.mjs
node --check scripts/continuum-local-brain-cli.mjs
bash -n scripts/continuum-local-llama-termux.sh
```

Sechs Node-Regressionstests wurden am 21.10.2026 im isolierten Entwicklungscontainer ausgeführt: **6 PASS / 0 FAIL**. Enthalten: authentifizierter lokaler echter HTTP-Server-Durchlauf, falsche Secrets, unbelegte Facts, falsche Dateipfade/zusätzliche JSON-Felder, HTTP-503, Antwortgrößenlimit, Health ohne Auth-Payload. Ein Schema-konformer Vorschlag bleibt `autoApply:false`. Die derzeitigen Dateien im GitHub-PR werden vor Gerätefreigabe zusätzlich mit den lokalen Testquellen abgeglichen.

## Explizit kontrollierter Samsung-Versuch

Zunächst M1-Checkout und aktuelle BUILD-Arbeit abschließen und lokale Akku-/Thermik-/Speichergrenzen prüfen. Nicht parallel Gradle und Sprachmodell laden.

1. Im getrennten Termux-Verzeichnis `$HOME/llama.cpp` nach Prüfung der aktuellen offiziellen Anleitung `llama-server` aus dem offiziellen ggml-org-Quellcode bauen. **Kein automatischer Fremdskript-Download per curl|sh** und niemals in `~/device-ai` Build-/Lerndaten überschreiben.
2. Falls der Besitzer es freigibt, quantisiertes GGUF gezielt herunterladen, im privaten Termux-Modellverzeichnis ablegen und SHA256 des Herausgebers vergleichen. Speicherplatz und Netzwerkverbrauch vorher kontrollieren.
3. Ein Modell in `$HOME/.local/share/device-ai/models/local-instruct.gguf` platzieren (oder `CONTINUUM_GGUF_PATH` auf einen geprüften existierenden Pfad setzen). Dann **manuell** `bash scripts/continuum-local-llama-termux.sh start` in Termux aufrufen. Dieser Aufruf lädt **nichts** herunter und darf bei unpassender llama.cpp-Version sicher scheitern.
4. Zweite Termux-Sitzung: `cd ~/device-ai && node scripts/continuum-local-brain-cli.mjs health`. Nach `LOCAL_MODEL_READY` für genau diese Sitzung `export CONTINUUM_LOCAL_BRAIN_API_KEY="$(cat "$HOME/.config/device-ai/local-llama.key")"` setzen und `node scripts/continuum-local-brain-cli.mjs chat 'Erkläre mir SWB1 in drei Sätzen.'` ausführen. Secret niemals im Chat teilen.
5. Antwortqualität, Wartezeit, CPU/RAM, Temperatur und Stabilität dokumentieren; keine Modellantworten als Besitzerwahrheit oder SWB1-Trainingslabel übernehmen.

## Reifegrad und nächste technische Integration

**Meilenstein LB-0: Node-Lokalinferenz-Grenze und echte Negativtests, SOURCE/CONTAINER VERIFIED.** Noch **nicht** auf Samsung mit echtem llama-server oder Modell getestet. Auf dem Telefon läuft weiterhin die installierte Continuum 0.3.10 mit dem bestehenden LiteRT-LM-Pfad; eine native Kotlin-Loopback-Integration, sichere IPC-Schlüsselübertragung und UI-Umschaltung müssen gesondert gebaut, signiert und auf dem Gerät getestet werden. M1 App→Termux→App APK-Build des aktuellen Codes bleibt ebenfalls offen. Der autonome Coding-Agent hat noch keinen echten freien Patch unter Nutzung dieses Clients erstellt.

Aus der aktuellen Forschung **nicht übernommen**: ADB-UID-2000-Eskalation, ungeprüfte Aktivierungsvektoren, automatische KV-Snapshot-Wiederverwendung, unbeaufsichtigtes selbstbestätigtes Modelltraining, automatisches Patch-Anwenden, selbstständige Main-Merges oder automatische Checkpoint-Migration.


## Weiterführende 2026er Forschung: Stimme, Caching und Leistung

- **Offline-Spracheingabe/-ausgabe:** https://github.com/k2-fsa/sherpa-onnx dokumentiert Android/ARM64, Online-/Offline-Spracherkennung, Text-to-Speech und VAD. https://github.com/ggml-org/whisper.cpp/tree/master/examples/whisper.android dokumentiert eine alternative native Android-Integration. Das sind mögliche *Laufzeit-Bibliotheken*, keine übernommenen Coding-Agenten. Die bereits vorhandene Continuum-Sprachoberfläche wird deshalb nicht blind ersetzt; zuerst messen wir Qualität, Deutsch-Erkennung, Berechtigungen und Speicher.
- **Prompt-/KV-Caching:** Die offizielle API bietet \`cache_prompt\` und Slot-Snapshots an, die theoretisch Prefill-Kosten reduzieren. Der bekannte 2026er Cache-Isolationshinweis wird für private Gesprächsdaten priorisiert; vor einer Einführung sind Reproduktions- und Isolationsprüfungen mit mehreren Prompts erforderlich. Keine vorab geladene KV-Datei ohne Bindung an Modell-SHA und Eingabegeneration.
- **CPU-Leistung:** https://github.com/ggml-org/llama.cpp/issues/26957 dokumentiert mögliche Versionsregressionen selbst auf Android ARM64. Die eigentliche Token/s-Leistung wird ausschließlich auf dem Besitzergerät bestimmt. Threads, Kontextfenster und GGUF-Größe werden erst nach Messung angepasst.
- **Speculative Decoding:** https://github.com/ggml-org/llama.cpp/blob/master/docs/speculative.md beschreibt eine mögliche spätere Beschleunigung durch ein kleines Draft-Modell. Das erhöht RAM-Bedarf und Komplexität; nicht Teil des ersten Single-Model-Milestones.
- **Wissensübernahme aus generierten Antworten:** keine automatischen SWB1/CDX1-Trainingslabels aus Chatantworten. Nur Besitzerbestätigung beziehungsweise unabhängige, überprüfbare Aufgaben-Ergebnisse dürfen später als Trainingsevidenz vorgeschlagen werden.
