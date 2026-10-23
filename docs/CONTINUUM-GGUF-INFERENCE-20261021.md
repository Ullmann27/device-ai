# Continuum · echte GGUF-Inferenz und begrenzter Diagnosekontext

21.10.2026 · EXP-05, EXP-06, EXP-12 · `RESEARCH_SHADOW`

**Echte CPU-Generierung mit einem offiziellen Qwen-GGUF wurde im isolierten Linux-x86_64-Container ausgeführt.** Das ist kein Samsung-/ARM64-Leistungsbeleg, kein Android-Build und keine Modellpromotion. Keine bezahlte API, keine übernommenen Coding-Agenten, keine Änderungen an vorhandenen Telefongewichten, SWB1/CDX1-Checkpoints oder bestätigten Fakten.

Arbeitsbasis dieser Teilaufgabe: `377bc05b8a8d96494f4cee7be8665fde3317f1e7`; vor dem abschließenden Wiederholungslauf aktueller Integrations-HEAD: `66e6b0d81a131925c1f9a3e2ccb47b938e6f05e7` plus die hier neu hinzugefügten Dateien. Der endgültige Review-Checkpoint wird im Integrations-Handoff separat gebunden. [Experimentvertrag](CONTINUUM-EXPERIMENTVERTRAG.md), [Register](CONTINUUM-EXPERIMENTE.json) und [Issue #41](https://github.com/Ullmann27/device-ai/issues/41) wurden gelesen.

## Geprüfte Artefakte

| Artefakt | Pin / Identität |
|---|---|
| Offizieller Engine-Quellcode | [ggml-org/llama.cpp, Commit `0a1ef309cdbb9f2024f58a38fb4c42352c5db569`](https://github.com/ggml-org/llama.cpp/tree/0a1ef309cdbb9f2024f58a38fb4c42352c5db569), MIT |
| CPU-Server | `0.7.0-dev`, Build 1 des flachen Checkouts, Commit `0a1ef30`; GNU 14.2.0, CMake 3.31.6, Linux x86_64 |
| Gebaute Server-Binärdatei SHA-256 | `205b3b9506d2079009e1c5389c1bfe0f5ae1b6ed5314cce3b047ebe67c4f6ab3` |
| Offizielles Modell | [Qwen/Qwen2.5-Coder-1.5B-Instruct-GGUF](https://huggingface.co/Qwen/Qwen2.5-Coder-1.5B-Instruct-GGUF/tree/f86cb2c1fa58255f8052cc32aeede1b7482d4361), Revision `f86cb2c1fa58255f8052cc32aeede1b7482d4361` |
| Datei / Lizenz | `qwen2.5-coder-1.5b-instruct-q4_k_m.gguf`; [Apache-2.0 LICENSE an derselben Revision](https://huggingface.co/Qwen/Qwen2.5-Coder-1.5B-Instruct-GGUF/blob/f86cb2c1fa58255f8052cc32aeede1b7482d4361/LICENSE) gelesen |
| Publisher-LFS-Pin **und tatsächliche Datei** | **1.117.320.768 Bytes**, SHA-256 `cc324af070c2ecbfd324a30884d2f951a7ff756aba85cb811a6ec436933bb046` |
| Laufzeitumgebung | Node **22.23.3**, Docker **28.4.0**, Host-RAM 9.968 MiB, kein Swap; CPU-Build mit AVX2, ohne GPU-Backend |

Die Hugging-Face-API an der fixierten Revision lieferte dieselben Dateigröße/LFS-SHA-Werte; die heruntergeladenen Bytes wurden unabhängig mit Python und erneut mit dem neuen Node-Admission-Modul gehasht. Die API-Zuordnung ist veröffentlichte Herausgebermetadaten, keine zusätzliche kryptografische Herstellersignatur.

Gezielt geprüft wurden Lizenz, oberste CMake-Datei, Common-/Vendor-/Server-/UI-Baupfade und die offiziellen Build-/Android-Anleitungen am genannten Commit. Subprozesse, TLS, UI-Bau und UI-Downloads, `llama-connect`, RPC, KleidiAI und externe OpenMP-/LLGuidance-Fetches wurden deaktiviert. Das ist eine begrenzte Quell-/Baupfadprüfung, keine vollständige Sicherheitsprüfung sämtlicher C++-Quellen. Keine Quellen oder Modellbytes wurden in dieses Repository kopiert. Alles liegt ausschließlich unter `/workspace/toolchain/continuum-gguf-lab`.

Das zunächst erwogene `ggml-org/models/tinyllamas/stories260K.gguf` wird derzeit auf `ggml-org/models-moved` umgeleitet; dort fehlte eine klare Modelllizenzdeklaration. Es wurde für diesen Nachweis nicht heruntergeladen oder als lizenziertes Modell ausgegeben. Auch ein winziges Story-Modell wäre lediglich ein Decoder-Test, kein Coding-Kompetenznachweis gewesen.

## Neue, abschaltbare Grenzen

- `scripts/continuum-gguf.mjs`: GGUF v2/v3, ausschließlich little-endian; begrenzte Header-/Metadaten-/String-/Array-/Tensoranzahl, striktes UTF-8, eindeutige Namen und Schlüssel, geprüfte Alignment-/Dimensions-/Quantisierungsblockgrößen, keine überlappenden oder außerhalb der Datei liegenden Tensorbereiche. Unbekannte Tensorformate scheitern geschlossen. Tokenizer-Arrays werden nicht als Kontext gespeichert und Chat-Templates nicht ausgewertet.
- `admitGgufFile`: verpflichtender vollständiger Revisions-, Lizenz-, Größen- und SHA-Pin des Aufrufers; höchstens 2 GiB Datei / 32 MiB Header; nur reguläre, nicht gruppen-/weltbeschreibbare Dateien ohne Symlink oder Hardlinks. `O_NOFOLLOW`, Geräte-/Inode-/Größen-/Zeitstempelvergleich und vollständiger Hash über denselben Descriptor erkennen Dateiaustausch während der Prüfung. Ein später separat gestarteter Server muss dieselben geprüften Bytes schreibgeschützt erhalten; eine Pfadangabe allein schließt spätere Änderungen nicht aus.
- `scripts/continuum-local-context.mjs`: flüchtige, deterministische Auswahl aus explizit gelieferten Diagnose-/Quellausschnitten, keine zweite Wissensdatenbank. Maximal sechs Diagnosen, acht Ausschnitte und 256 KiB Eingabe; tatsächliche JSON-/UTF-8-Größe begrenzt, Unicode-sicher gekürzt, vollständiges Auftragsziel oder Ablehnung. Diagnosen zuerst; Kürzung und Auslassungen werden gezählt. 384 Ausgabetokens des bestehenden Clients sowie Template-/Sicherheitsreserve bleiben frei. **Byte-Schätzung, kein exakter Tokenizer-Beweis.** Der Aufrufer muss das konfigurierte Server-Kontextfenster angeben; GGUF-Trainingskontext ist keine Aussage über aktuell reserviertes KV-RAM.
- `queryLocalDiagnosis`: ausschließlich über den vorhandenen authentifizierten `127.0.0.1:8089`-Client, `verifiedFacts:[]`, ohne Tools, Patchanwendung oder Faktpromotion. Antworten bleiben `trusted:false`, `autoApply:false`, `factsPromoted:false`.
- `scripts/continuum-gguf-smoke.mjs --run`: ausdrücklich manuell aufzurufende echte HTTP-Probe, ohne Mock-/Fetch-Injektion am CLI. Sie lädt nichts herunter, startet keinen Server und ändert keine Dateien. Drei synthetische Proben: kurzer Text, kontextuelle Diagnose, begrenzter Schatten-Patch. Auch eine echte Modellantwort kann am getrennten Patch-Schema scheitern; dies wird als `MODEL_OUTPUT_REFUSED` samt unbestätigtem Text dokumentiert.

Der Admission-Status lautet `GGUF_IDENTITY_MATCHED`, aber ausdrücklich weiterhin `trusted:false`, `executionAllowed:false`, `promoted:false`. Ein Hash macht weder Fakten noch Modellqualität wahr. Die bestehenden Repair-/Critic-/Test-/Owner-Gates bleiben notwendig.

## Tatsächliche Messung und aufgedeckter Wire-Fehler

Erster vollständig erfasster Modelllauf: ein Modell, Kontext 2.048, zwei Generierungs-/Prefillthreads, `--parallel 1`, Seed 42, festes eingebautes `chatml`-Template ohne Jinja, Cache-Wiederverwendung aus. Client und Server liefen gemeinsam im netzlosen Container. Docker-Image: lokal aus der leeren `scripts/continuum-isolation.Dockerfile` gebaut, `sha256:8e14a201de9e6d7deda388fc33ed496462162bc42638730a38c15f1898df0b17`.

| Probe | Prompt-/Ausgabetokens | HTTP-Wandzeit | Prefill | Generierung | Generierung Token/s |
|---|---:|---:|---:|---:|---:|
| „HELLO“ | 34 / 3 | 789 ms | 652,759 ms | 125,187 ms | 15,98 |
| Diagnose `sum(a,b) => a-b` | 414 / 103 | 12.584 ms | 7.094,012 ms | 5.480,103 ms | 18,61 |
| Schatten-Patch | 95 / 39 | 3.208 ms | 1.518,738 ms | 1.680,204 ms | 22,62 |

Server-`usage.completion_tokens` enthält den Abschluss-Token, während `timings.predicted_per_second` auf den vom Server gemessenen Decodierschritten beruht; deshalb nicht einfach Ausgabetokens durch Generierungszeit teilen. Das sind drei einzelne synthetische Proben, keine belastbare Geschwindigkeitsverteilung oder unbekannte Coding-Aufgabe.

„HELLO“ wurde exakt erzeugt. Die Diagnose schlug `a+b` vor, beschrieb aber im Einleitungssatz Subtraktion widersprüchlich: fachliche Qualität bleibt begrenzt. Der Patch war ein RFC6902-Objekt `{op,path,value}` und wurde **korrekt clientseitig abgelehnt**.

Ursache: Der bestehende Client sendete `{type:'json_schema',schema:...}`. Die tatsächliche [Server-Implementierung am Pin](https://github.com/ggml-org/llama.cpp/blob/0a1ef309cdbb9f2024f58a38fb4c42352c5db569/tools/server/server-common.cpp#L1387) liest bei diesem Typ `response_format.json_schema.schema`; ohne dieses Feld wird nur irgendein JSON-Objekt erzwungen. Die README enthielt noch ein abweichendes Beispiel. Der bestehende Client wurde minimal auf `{type:'json_schema',json_schema:{name:'continuum_patch',strict:true,schema:...}}` korrigiert. Der verschärfte Request-Shape-Test scheiterte zuerst am alten Code und bestand nach der Korrektur. Die unabhängige Antwortvalidierung blieb bestehen.

Der erste Nachlauf zum Lesen der RSS-Werte scheiterte an einem im leeren Image nicht verfügbaren `awk`-Symlink; dies betrifft die drei erfassten HTTP-Ergebnisse nicht, ließ diesen Prozessaufruf jedoch mit Exit 127 enden. Danach wurde ausschließlich der Metadatenleser auf vorhandenes Node umgestellt und mit dem korrigierten Client wiederholt: **gesamter Container-/Smoke-Aufruf Exit 0**.

| Saubere Wiederholung nach Wire-Korrektur | Prompt-/Ausgabetokens | HTTP-Wandzeit | Prefill | Generierung | Generierung Token/s |
|---|---:|---:|---:|---:|---:|
| „HELLO“ | 34 / 3 | 723 ms | 637,137 ms | 79,236 ms | 25,24 |
| Dieselbe Diagnose | 414 / 103 | 11.211 ms | 6.217,271 ms | 4.987,532 ms | 20,45 |
| Schema-konformer Schatten-Patch | 95 / 34 | 3.459 ms | 1.863,970 ms | 1.583,893 ms | 20,83 |

Der echte Patch lautete `{"file":"app/src/sum.js","oldText":"return a-b;","newText":"return a+b;"}`; er wurde validiert, **nicht angewendet oder ausgeführt**. `trusted:false`, `autoApply:false`. Die Diagnose blieb sprachlich widersprüchlich und wurde nicht zum Fakt erklärt. Kein Retry mit ausgetauschter Modellantwort. Server-`VmHWM` und abschließendes `VmRSS`: **1.893.956 KiB** (ca. **1,81 GiB**), acht Serverthreads; `VmPeak` 2.334.728 KiB bezeichnet virtuellen Adressraum. Das ist ein einzelner kurzer Lauf mit 3-GiB-Containerlimit, kein Dauerlast-/Android-RAM-Budget.

Externe Belege: `qwen-publisher-api.json`, `qwen-manifest.json`, `qwen-admission.json`, `configure.log`, `build.log`, `server-help.txt`, **`real-smoke-refused-report.json`** (vor der Korrektur), **`real-smoke-report.json` / `real-smoke-output.txt` / `real-smoke-evidence.json`** (saubere Wiederholung) im genannten Laborverzeichnis.

## Reproduktion: ausdrücklich opt-in, außerhalb des Repositories

Die folgenden Befehle wurden inhaltlich für den Linux-Laborlauf verwendet. Vor einem neuen Download Größe/Lizenz/Platz prüfen; nicht auf dem Telefon oder in vorhandenen Gewichtsverzeichnissen automatisch ausführen. TLS-Prüfung und konfigurierte Proxy-/CA-Einstellungen bleiben erhalten.

```sh
GGUF_LAB=/workspace/toolchain/continuum-gguf-lab
git init "$GGUF_LAB/llama.cpp"
git -C "$GGUF_LAB/llama.cpp" fetch --depth=1 https://github.com/ggml-org/llama.cpp.git 0a1ef309cdbb9f2024f58a38fb4c42352c5db569
git -C "$GGUF_LAB/llama.cpp" checkout --detach FETCH_HEAD
# Lizenz und Baupfade vor dem Build prüfen; vorhandenes CMake oder die
# außerhalb des Repositories installierte CMake-3.31.6-Wheel verwenden.
python3 -m pip install --disable-pip-version-check --target "$GGUF_LAB/cmake-python" cmake==3.31.6
GGUF_CMAKE="$GGUF_LAB/cmake-python/cmake/data/bin/cmake"
"$GGUF_CMAKE" -S "$GGUF_LAB/llama.cpp" -B "$GGUF_LAB/llama-build" \
  -DCMAKE_BUILD_TYPE=Release -DBUILD_SHARED_LIBS=OFF \
  -DLLAMA_BUILD_TESTS=OFF -DLLAMA_BUILD_EXAMPLES=OFF -DLLAMA_BUILD_APP=OFF \
  -DLLAMA_BUILD_UI=OFF -DLLAMA_USE_PREBUILT_UI=OFF -DLLAMA_CONNECT=OFF \
  -DLLAMA_OPENSSL=OFF -DLLAMA_SUBPROCESS=OFF -DLLAMA_LLGUIDANCE=OFF \
  -DGGML_NATIVE=OFF -DGGML_RPC=OFF -DGGML_CPU_KLEIDIAI=OFF -DGGML_OPENMP_FETCH=OFF
"$GGUF_CMAKE" --build "$GGUF_LAB/llama-build" --target llama-server llama-completion llama-bench -j 4
curl --fail --location --max-time 1200 \
  https://huggingface.co/Qwen/Qwen2.5-Coder-1.5B-Instruct-GGUF/resolve/f86cb2c1fa58255f8052cc32aeede1b7482d4361/qwen2.5-coder-1.5b-instruct-q4_k_m.gguf \
  --output "$GGUF_LAB/qwen2.5-coder-1.5b-instruct-q4_k_m.gguf.part"
```

Erst nach Größen-/SHA-Abgleich auf den endgültigen `.gguf`-Pfad umbenennen, Modus 600 setzen und folgende separate `qwen-manifest.json` ablegen:

```json
{"repository":"Qwen/Qwen2.5-Coder-1.5B-Instruct-GGUF","revision":"f86cb2c1fa58255f8052cc32aeede1b7482d4361","filename":"qwen2.5-coder-1.5b-instruct-q4_k_m.gguf","license":"apache-2.0","sizeBytes":1117320768,"sha256":"cc324af070c2ecbfd324a30884d2f951a7ff756aba85cb811a6ec436933bb046"}
```

Der tatsächliche Labor-Container wurde über den expliziten lokalen Docker-Socket gestartet: `--user=1000:1000 --network=none --read-only --cap-drop=ALL --security-opt=no-new-privileges --cpus=2 --memory=3g --memory-swap=3g --pids-limit=64 --ulimit nofile=128:128 --ulimit core=0`. Nur `/usr/bin` (auch als `/bin`), `/usr/lib`, `/lib`, `/lib64`, die geprüfte Server-/Node-Binärdatei, Modell/Manifest und Skripte wurden read-only eingebunden; `/scratch` als 64-MiB-`noexec,nosuid,nodev`-tmpfs für UID 1000. Start über `/usr/bin/env -i` entfernt übernommene Proxy-/Credential-Variablen. Keine Host-Netzwerkfreigabe, kein privilegierter Container, kein Docker-Socket im Container, kein schreibbares Repository.

Innerhalb **dieser kontrollierten Umgebung** wurde folgende feste Sequenz verwendet. Für die Container-Reproduktion als `$GGUF_LAB/run-smoke.sh` außerhalb des Repositories speichern; die Datei enthält keine dauerhaften Schlüssel:

```sh
set -euo pipefail
umask 077
/opt/node -e "require('node:fs').writeFileSync('/scratch/key',require('node:crypto').randomBytes(32).toString('hex')+'\n',{mode:0o600})"
/opt/llama-server -m /model/qwen2.5-coder-1.5b-instruct-q4_k_m.gguf \
  --host 127.0.0.1 --port 8089 --api-key-file /scratch/key \
  --parallel 1 -c 2048 -t 2 -tb 2 -n 384 --seed 42 --offline --fit off \
  --no-cache-idle-slots --cache-ram 0 --no-cache-prompt --no-context-shift \
  --no-webui --no-slots --no-jinja --chat-template chatml --threads-http 2 \
  > /scratch/server.log 2>&1 &
server_pid=$!
trap 'kill "$server_pid" 2>/dev/null || true; wait "$server_pid" 2>/dev/null || true' EXIT
ready=0
for attempt in {1..60}; do
  kill -0 "$server_pid" 2>/dev/null || exit 1
  if /opt/node --input-type=module -e "const r=await fetch('http://127.0.0.1:8089/health');process.exit(r.ok?0:1)" 2>/dev/null; then ready=1;break;fi
  sleep 0.2
done
test "$ready" -eq 1
# Zweiter Prozess im selben isolierten Netzwerk-Namespace nach /health:
# Schlüssel mit read aus der privaten Datei einlesen, niemals ausgeben.
IFS= read -r CONTINUUM_LOCAL_BRAIN_API_KEY < /scratch/key
export CONTINUUM_LOCAL_BRAIN_API_KEY
export CONTINUUM_GGUF_PATH=/model/qwen2.5-coder-1.5b-instruct-q4_k_m.gguf
export CONTINUUM_GGUF_MANIFEST=/model/pin.json
/opt/node /app/scripts/continuum-gguf-smoke.mjs --run
```

Der äußere Aufruf für diesen vorhandenen Laborpfad und das bereits gebaute leere Image:

```sh
GGUF_SOURCE=/workspace/continuum-next
env -u DOCKER_HOST -u DOCKER_CONTEXT -u DOCKER_TLS -u DOCKER_TLS_VERIFY -u DOCKER_CERT_PATH \
docker --host=unix:///var/run/docker.sock run --rm --user=1000:1000 \
  --network=none --read-only --cap-drop=ALL --security-opt=no-new-privileges \
  --cpus=2 --memory=3g --memory-swap=3g --pids-limit=64 --ulimit nofile=128:128 --ulimit core=0 \
  --tmpfs /scratch:rw,noexec,nosuid,nodev,size=64m,uid=1000,gid=1000,mode=700 \
  --mount type=bind,src=/usr/bin,dst=/usr/bin,readonly \
  --mount type=bind,src=/usr/bin,dst=/bin,readonly \
  --mount type=bind,src=/usr/lib,dst=/usr/lib,readonly \
  --mount type=bind,src=/lib,dst=/lib,readonly --mount type=bind,src=/lib64,dst=/lib64,readonly \
  --mount type=bind,src=/workspace/toolchain/node22/bin/node,dst=/opt/node,readonly \
  --mount "type=bind,src=$GGUF_LAB/llama-build/bin/llama-server,dst=/opt/llama-server,readonly" \
  --mount "type=bind,src=$GGUF_LAB/qwen2.5-coder-1.5b-instruct-q4_k_m.gguf,dst=/model/qwen2.5-coder-1.5b-instruct-q4_k_m.gguf,readonly" \
  --mount "type=bind,src=$GGUF_LAB/qwen-manifest.json,dst=/model/pin.json,readonly" \
  --mount "type=bind,src=$GGUF_LAB/run-smoke.sh,dst=/lab/run-smoke.sh,readonly" \
  --mount "type=bind,src=$GGUF_SOURCE/scripts,dst=/app/scripts,readonly" \
  --entrypoint /usr/bin/env \
  sha256:8e14a201de9e6d7deda388fc33ed496462162bc42638730a38c15f1898df0b17 \
  -i PATH=/usr/bin:/bin HOME=/scratch /bin/bash --noprofile --norc /lab/run-smoke.sh
```

Der zusätzlich tatsächlich verwendete Node-Metadatenleser in der lokalen `run-smoke.sh` las `/proc/$server_pid/status` für RSS/HWM/Threadzahl und filterte danach die Server-Timings. Diese Datei ist eine lokale Laborhilfe; sie wird nicht heruntergeladen oder auf dem Telefon gestartet. Der Smoke-Client allein beweist nicht, welches Modell ein beliebiger zuvor gestarteter Server geladen hat; dafür gehört der geprüfte schreibgeschützte Modell-Mount zum Startbeleg.

## ARM64-/Termux-Capability-Rezept und offene Grenze

Vor der bestehenden Samsung-Integration zuerst deren vorhandene Toolchain prüfen; keine obligatorische Neueinrichtung. `PREFIX=/data/data/com.termux/files/usr`, `uname -m=aarch64`, native Node-/Git-/CMake-/Clang-Fähigkeit und genügend freier Speicher müssen tatsächlich vorhanden sein, sonst `UNAVAILABLE`. Den offiziellen Quell-Pin in einem getrennten Termux-Verzeichnis prüfen und nativ bauen. Keine x86-Binärdateien übertragen und keine globalen `-march`-Erweiterungen für ungeprüfte dotprod/i8mm/SVE-Fähigkeiten erzwingen. KleidiAI/GPU bleiben zunächst aus; CPU-Baseline zuerst messen. Für einen NDK-Bau gelten die [offiziellen Android-Schritte am Pin](https://github.com/ggml-org/llama.cpp/blob/0a1ef309cdbb9f2024f58a38fb4c42352c5db569/docs/android.md), insbesondere `arm64-v8a`, `GGML_NATIVE=OFF`, `GGML_OPENMP=OFF`, `GGML_LLAMAFILE=OFF`, `LLAMA_OPENSSL=OFF`.

Nach bewusster Besitzerwahl eines bereits vorhandenen, gehashten GGUF: Admission separat aufrufen, festes Loopback-/Key-/Cache-Profil prüfen, mit einer Sequenz und 2.048 Kontext beginnen. Threadzahl nicht aus dieser x86-Messung übernehmen; zunächst zwei Threads mit Beobachtung von RAM, Akkuzustand, Temperatur, Drosselung und Abbruchfähigkeit erproben. Unlesbare Ressourcen-/Thermikdaten bleiben `UNAVAILABLE`. Kein paralleler Gradle-/APK-Build. Der vorhandene `continuum-local-llama-termux.sh` wird durch diese Module nicht automatisch gestartet und erhält keine automatische Modellinstallation.

**Offen:** tatsächliche Samsung-Latenz/Tokenrate/RAM/Thermik, Android-OS-Isolation des Servers, native App↔Termux-Schlüsselübergabe, aktueller App→Termux→App-APK-Build, neue unbekannte Coding-Holdouts. Qwen 1.5B plus drei synthetische Proben beweisen keinen autonomen Coding-Agenten. Kein Training, keine Memory- oder Modellpromotion, keine Installation und kein Main-Merge.

## Prüfungen, Kompatibilität und Rollback

```sh
/workspace/toolchain/node22/bin/node --test scripts/continuum-gguf.test.mjs scripts/continuum-local-context.test.mjs scripts/continuum-local-brain.test.mjs .github/continuum/local-brain-repair.test.mjs
/workspace/toolchain/node22/bin/node --check scripts/continuum-gguf.mjs
/workspace/toolchain/node22/bin/node --check scripts/continuum-local-context.mjs
/workspace/toolchain/node22/bin/node --check scripts/continuum-gguf-smoke.mjs
```

**33/33 PASS**, davon 22 neue Parser-/Kontexttests, sechs bestehende Clienttests mit verschärftem Request-Shape-Test und fünf bestehende Repair-Adaptertests; echte HTTP-Fixtures bleiben vom gesonderten realen Modelllauf getrennt. Abgedeckt: beschädigte Längen/Version/UTF-8/Typen, Duplikate, falsche Quantisierungszeilen, Out-of-bounds/Überlappung/Alignment, Hash-/Größenfehler, Links/Rechte, Abbruch, Unicode-/JSON-Escaping-Budget, sensible Pfade, Auslassungen, Prompt-Injektion als unbestätigte Daten, keine Faktpromotion. Kein Android-Gesamtbuild und kein Gerätetest ausgeführt.

Kompatibilität: eigenständige Opt-in-Module, korrigierter vorhandener Loopback-Client und dieselben kanonischen Repair-/Review-Gates; kein zweiter Backlog, keine Gewichts-/Speichermigration. Nächster Schritt: zusätzliche unabhängige Coding-Holdouts über die vorhandene Repair-Pipeline; anschließend Besitzer-gesteuerte ARM64-Messung. Rollback: neue Module nicht aufrufen bzw. ihren einzelnen Integrationscommit zurücknehmen; Labor-Container stoppen. Externe Laborartefakte können separat entfernt werden, ohne Repo-/Telefongewichte oder bestätigtes Gedächtnis anzufassen.
