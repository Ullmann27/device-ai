# GPT-6 · CONTINUUM SOVEREIGN GGUF / TERMUX — Entwicklungsübergabe vom 02.11.2026

**Status:** `SOURCE_INTEGRATED / UNIT_TESTED` auf separatem Forschungsbranch; kein Android-Modellwechsel und keine Besitzer-APK. **Scope:** EXP-05 (lokale Alternativmodelle) und EXP-12 (modellunabhängige Kontinuität).

## Originaler GitHub-Stand und Konfliktfreiheit
- Eigenständiger Draft-PR: [#356](https://github.com/Ullmann27/device-ai/pull/356), Branch `research/continuum-sovereign-gguf-admission-20261102`, aus `41e1897b9099b911286d312a53c8c19307a49484` erstellt.
- Elternteil: bestehender Forschungs-PR #354; **keine Änderungen** an dessen `AndroidNativeClick.kt`, `ContinuumScreenObservationService.kt`, `ToolGate`, `AgentActivity`, `ToolRegistry`, APK, Main, Signierung oder Besitzerinformationen.
- Unveränderte bestehende Implementierung: `scripts/continuum-gguf.mjs` mit `admitGgufFile`, `scripts/continuum-gguf-smoke.mjs`, `scripts/continuum-local-brain.mjs`. Kein zweiter GGUF-Parser oder neuer KI-Dienst.

## Wirklich implementierte Änderung
- Neu `scripts/continuum-gguf-preflight.mjs`: liest nur einen explizit vom Besitzer angegebenen Manifestpfad als Datei; fordert absolute kanonische Pfade, normale Datei, eine einzige Hardlink-Referenz, keine symbolischen Verweise, passende Besitzer-UID, keine Gruppen-/Fremd-Schreibrechte, maximal 8192 Bytes und unveränderte Dateiidentität vor/nach dem Lesen.
- Genau sechs Provenienzfelder im JSON: `repository`, `revision` (40 Hex), `filename` (.gguf), `license`, `sizeBytes`, `sha256`. Der Manifest-Dateiname und ausgewählte Modellpfad müssen übereinstimmen. Fremde/zusätzliche Felder sind nicht zulässig.
- Beim tatsächlichen CLI-Aufruf `--verify` wird der bereits vorhandene `admitGgufFile` auf die **echten Modellbytes** angewendet: begrenzter GGUF-Header, zugelassene Tensorstrukturen, Originaldatei-SHA256 und Größenprüfung. Nur `GGUF_IDENTITY_MATCHED` ergibt eine knappe, nicht autorisierende `GGUF_PIN_VERIFIED`-Quittung. Keine Netzwerkoperation, Downloads, Ausführung, Fakten-Promotion oder Gewichtsänderung.
- `scripts/continuum-local-llama-termux.sh` verlangt jetzt `CONTINUUM_GGUF_MANIFEST` und einen funktionierenden Node-Verifier. Die Prüfung läuft **vor** jeglichem `llama-server --help` oder `exec`. Fehlt Manifest, Datei oder Prüfung, startet kein Modellserver. Symlinked Server-Datei wird abgelehnt; bestehende Loopback-Adresse, manuelle Startfreigabe und Noncache-Flags bleiben. Der API-Key-Fileinhalt muss 64 Hex-Zeichen aufweisen.
- Keine neue Produktions-Laufzeitintegration mit Android `LocalModelHost`; dieser bleibt vorläufig auf LiteRT/Gemma. Das ist ein abgesicherter alternativer **Termux-Startpfad**, nicht der fertige Continuum-Modellrouter.

## Tatsächlich ausgeführte lokale Hostprüfungen
```bash
node --test scripts/continuum-gguf-preflight.test.mjs
bash -n scripts/continuum-local-llama-termux.sh
```
- Auf Linux x86_64, Node v22.16.0: **7 PASS, 0 FAIL, 0 SKIP**; Exit 0, TAP-Ausgabe. Testmodule prüften sichere Manifest-Dateien, zusätzliche/falsche Felder, Symlink-/Hardlink-/Permission-Fehler, fehlende Prüfbestätigung, ungültige Enginequittung und einen simulierten Termux-Aufruf, bei dem ohne Manifest kein Servercode ausgeführt wird.
- **Wichtige Evidenzgrenze:** Die 7 Tests verwenden für positive bzw. negative `preflightPinnedGguf`-Quittungen einen kontrollierten Test-`admit`-Adapter. Der ursprüngliche `admitGgufFile` hat im Repository eigene GGUF-Tests; seine erneute gemeinsame Prüfung mit diesem neuen CLI ist **NOT_RUN** in diesem Host-Lauf. `bash -n` ist Syntaxprüfung, keine reale ARM64-Ausführung.
- Vollständige Android-Gradle-, Node-Gesamtsuite, lokale GGUF-Inferenz auf Fold7 und realer Termux-Serverstart: **NOT_RUN**. Kein `DEVICE_VERIFIED`, kein APK-Update.

## Fortsetzung: tatsächlicher GGUF-CLI-Integrationspfad (gleicher Forschungstag)

Neu [`scripts/continuum-gguf-preflight.integration.test.mjs`](../../../scripts/continuum-gguf-preflight.integration.test.mjs): fünf gesonderte tatsächliche CLI- und Shell-Integrationsprüfungen. Eine strukturell gültige winzige GGUF-`llama`-Testdatei wird auf Platte erzeugt, als echte Datei gehasht und durch `continuum-gguf-preflight.mjs --verify` mit dem vorhandenen `admitGgufFile` geprüft. Die Tests verlangen die Quittung `GGUF_PIN_VERIFIED` und `serverModelIdentityVerified=false`.

Negativkontrollen: Byte-Manipulation mit `GGUF_HASH_MISMATCH`, gefälschter GGUF-Magic mit dazu passender neu berechneter SHA (`GGUF_MAGIC`), falsche deklarierte Größe (`GGUF_SIZE_MISMATCH`), fehlende Pin-Datei (`GGUF_PIN_UNAVAILABLE`). Ein simulierter ARM64-Termux-Einstieg verwendet ein **künstliches** Shell-Serverprogramm und prüft, dass nach manipulierten Modellbytes nicht einmal `--help` am Server aufgerufen wird.

**Zusammen ausgeführte lokale Tests:** `node --test scripts/continuum-gguf-preflight.test.mjs scripts/continuum-gguf-preflight.integration.test.mjs` → **12 PASS / 0 FAIL / 0 SKIP, Exit 0**. `bash -n scripts/continuum-local-llama-termux.sh` → Exit 0. Originaler berichteter Linux-Lauf mit Node v22.16.0, kein Android-Gerät.

**Wichtige Einschränkung zur Quellbindung:** Die lokale Testumgebung wurde aus GitHub-Quellen/Spiegeln manuell zusammengestellt; `scripts/continuum-gguf-preflight.mjs` stimmt bytegenau mit dem GitHub-Blob `447f4a515f07152f661391896e0febf99146ddac` überein. Die übrigen lokal eingesetzten Spiegel wurden nicht alle als byteidentische GitHub-Objekte nachgewiesen. Deshalb ist dies ein ehrlicher **Host-Integrationsbeleg**, noch kein lückenloser, am PR-HEAD reproduzierter Release-QA-Beleg. Der Einzelnachweis liegt in [`host-preflight-results.json`](../../evidence/continuum-sovereign-gguf-20261102/host-preflight-results.json); vollständige aktuelle Node-/Gradle-Prüfung und autorisierter ARM64-GGUF-Modellstart fehlen weiterhin.

Die Test-GGUF enthält nur einen synthetischen Tensorheader. Ein echter trainierter Decoder oder `llama-server` wurde dabei nicht betrieben; `DEVICE_VERIFIED=false`, `MODEL_SWITCH=false`.

## Nicht aufgelöste Grenzen und nächste Schritte
1. Vorhandene GGUF-Tests und die 12 Host-Fälle in einem **exakten, vollständigen GitHub-Checkout** ausführen, danach einen echten lokal vom Besitzer bereitgestellten GGUF-Gewichtsstand mit exaktem Quellen-/Lizenz-Pin offline verifizieren.
2. Auf Fold7/Termux manuell `CONTINUUM_GGUF_PATH` und `CONTINUUM_GGUF_MANIFEST` setzen; echten Start + echte `llama.cpp`-Generierung und Energie-/RAM-/Latenzwerte erheben. Kein automatischer Download.
3. Modellidentität im laufenden llama-server ist **noch nicht** an den SHA des vor dem Start geprüften Modells gebunden. Ein Austausch der Modelldatei zwischen Verifikation und Öffnen durch den Server wäre ein TOCTOU-Risiko. Lösen mit vertrauenswürdiger unveränderlicher Stage-/Inode-Bindung oder unabhängiger Server-Ladequittung, bevor ein Produkt-Erfolg behauptet wird.
4. `repository/revision/license` stammen aus dem vom Besitzer erstellten Manifest und beweisen **nicht** die Echtheit des Modellherausgebers oder die rechtliche Verwendung. Die tatsächliche Quelle/Lizenz separat verifizieren.
5. Später einen zweiten lokalen `LocalModelRuntime`-Adapter an **dieselbe** vorhandene Android-Dialog-/Gedächtnis-/ToolGate-Architektur anschließen; vollständige Offline-Testmission mit deaktiviertem Gemma und erhaltenen Speicherinhalten erst dann abnehmen.
6. Ungeprüfter modellgenerierter Text darf keine Shell-Kommandos oder Zugriffsrechte erteilen. `main`, Besitzer-Signierung, Arbeitsdaten und produktive neuronale Gewichte bleiben unberührt.

**Rollback:** Nur diesen Draft-PR nicht integrieren oder Startskript/Preflight-Dateien aus dem Elternstand verwenden. Keine bereits gespeicherten Modelle oder Besitzerdateien wurden durch diesen GitHub-Commit verändert.

**Kontrollpunkt:** Aktuelle SHA in GitHub immer vor einer Fortsetzung erneut prüfen, anschließend vollständige Node-/Android-Tests und Code Review. Keine automatische Zusammenführung.