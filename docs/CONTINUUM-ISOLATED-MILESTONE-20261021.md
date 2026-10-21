# Continuum – gesicherter Einstieg nach dem Isolation-/GGUF-Meilenstein

Stand 21.10.2026. Draft [PR #53](https://github.com/Ullmann27/device-ai/pull/53), Branch `sol/continuum-isolated-research-20261021`, ausführbarer Code-Checkpoint **`3f09c5427b59d8a944b7ceaacc3cb5d0d77837a9`**. Der nachfolgende Dokumentationscommit enthält diese Belege; beim Einstieg immer den tatsächlichen Remote-HEAD lesen.

Basis unverändert: PR #49 / `377bc05b8a8d96494f4cee7be8665fde3317f1e7`; PR #53 ist darauf gestapelt. Main blieb bei `190f5eabaa60fc59292b2b80d3086aafd7743a80`. Aktive EXP-00/01/02/03/04/05/06/07/08/11/12. Issue #41 zuletzt aktualisiert 19.10.2026, 20:26:30 UTC; tatsächlich erneut gelesen. Besitzer meldete zuletzt **5 % Wochenkontingent**: keine neuen Aufgabenstränge, Abschluss/Prüfung/Handoff. Die Quote wurde nicht durch ein Tool ausgelesen.

## Gesicherte Funktionsabschnitte

- `66e6b0d`: bestehender Teamleiter erhält optional persistente endliche Zyklusbudgets, atomare Checkpoints, Backoff und Crash-/Lock-Recovery; 22 gezielte Tests. CLI-Anschluss in `a541683`: `--loop --durable-loop`, Gesamtlimit standardmäßig 24, Neustart setzt es nicht zurück.
- `a60d056`: drei ausschließlich berichtende Forschungsprototypen, 14 Tests, vollständige feste Holdout-/Adversarialmessung. Weniger Oracleabfragen bzw. Reparaturversuche; zusätzliche Rechen-/Admissionkosten und schlechtere Kontextpräzision. Keine allgemeine Intelligenzsteigerung, Neuheitsbehauptung oder Übernahme.
- `a541683`: echter optionaler OS-Ausführer für dieselbe Repair-/Backlog-Pipeline, feste Profile, kein Netzwerk/Hostschreibzugriff, CPU/RAM/PIDs/Laufzeit begrenzt. Compiler-, Kernel- und kanonischer JS-Repair-Nachweis. Breite Modellpatches bleiben prüfpflichtig und von Lernen/Replay ausgeschlossen. Zusätzlich falsche Timeoutbehandlung und unbestätigtes Docker-Teardown behoben.
- `3f09c54`: echte offizielle Qwen2.5-Coder-1.5B-Q4_K_M-Inferenz auf Linux x86_64; strikte GGUF-Admission, begrenzter Diagnosekontext. Tatsächlicher llama.cpp-Schemafehler repariert: `response_format.json_schema.schema`. Danach gültiger Schattenpatch, keine Ausführung von Modelltext.

## Tatsächliche Prüfung

**828/828 Node-/Werkzeugtests, 60/60 Backendtests, jeweils null übersprungen.** Node 22.23.3; echte Kotlin-CLI 2.0.20/JDK17 für bestehende Compilerintegrationstests. Gesamtsuite lief vor der letzten eng begrenzten Teardown-Härtung; danach wurden ihre fünf Negativtests, der reale Kernel-/Kotlin-/Timeoutlauf und die echte Repairintegration erneut grün ausgeführt. GGUF-/Client-/Adaptertests anschließend nochmals **33/33**.

Reale OS-Prüfung: vier Negativproben zu Besitzerdateien, Credentials, Privilegien, Prozess-/Netzwerkgrenzen und PID-Ressourcen; Kotlin kompiliert und ausgeführt; endloser Kandidat samt Container zuletzt in 1.221 ms beendet. Die neu ausführbare Reproduktion liegt unter `scripts/continuum-isolation-proof.mjs` und `scripts/continuum-isolated-repair-proof.mjs`; externe geprüfte Toolchainkonfiguration nötig.

Der injizierte mehrfunktionale JS-Listenfall hatte zwei echte Testfehler: Baseline 1/3, Kandidat und unabhängiger zweiter Worktree jeweils 3/3. Keine Besitzerquelle verändert, keine gelernten Erfahrungen, kein Merge. Das war ein Pipelinebeleg mit festem Kandidaten, kein Modellbenchmark.

Das reale GGUF lief mit dem gepinnten offiziellen llama.cpp-Commit, netzlos, 2 CPUs/3 GiB. Kontextdiagnose 11.211 ms; Schattenpatch 3.459 ms, Generierung ca.20,8 Token/s, Server-RSS/HWM 1.893.956 KiB. Drei synthetische Proben reichen nicht als unbekannter Coding-Holdout. Der erste falsche Patch wurde korrekt abgewiesen und ebenfalls als Evidenz erhalten.

Alle maschinenlesbaren Belege und vollständigen grünen Node-/Backendlogs: [evidence/isolated-20261021](evidence/isolated-20261021/). `verification.json` enthält Befehle, Prüfumfang und Dateihashes. Ein anfänglicher Backendtestlauf scheiterte am fehlenden Express-Paket im neuen Worktree; nach `npm ci --offline --ignore-scripts` 60/60. Keine Tests abgeschaltet. Die lokal erzeugten node_modules wurden danach außerhalb des Quellbaums aufbewahrt.

## Offen – beim nächsten Mal hier weiter

1. Aktuellen PR-53-HEAD, `AGENTS.md`, Experimentvertrag/-register und diesen Einstieg lesen; sauberen geschützten Branch verwenden. Nichts aus PR #49/#53 neu erfinden oder Main mergen.
2. **Erster technischer Folgeschritt:** genau ein festes Androidprofil des neuen optionalen Linux-Ausführers mit bereits vorbereiteten öffentlichen Offline-Gradle-Caches tatsächlich ausführen. Das ist noch nicht nachgewiesen. Bei fehlenden Werkzeugen `UNAVAILABLE`, kein Host-Fallback. Erst danach größerer Android-Repair-Fall.
3. Getrennte Samsung-Abnahme: vorhandene Termux-Integration prüfen; tatsächliche ARM64-Inferenz/RAM/Thermik und persistenten Worker-Wiederanlauf messen. Keine Docker-/proot-/Worktree-Sicherheitsfähigkeit auf dem Telefon behaupten. Breite Modellpatches auf Android bleiben bis zum echten OS-Grenznachweis aus.
4. Neue externe, bislang unbenutzte Coding-Holdouts vor weiterem Tuning versiegeln. Kandidatenberichte sind keine manipulationssicheren Orakel. Keine positiven Lernlabels, automatischen Gewichtsupdates oder Gedächtnisförderung aus dem breiten Modellpfad.

Kein aktueller Android-Gesamtbuild/Lint/Signatur-/APK-/Installationsbeleg in diesem Abschnitt. Android-Appquellen, Paketidentität und Signierung wurden nicht verändert. Der vorhandene APK-/Termux-Rückkanal wird nicht ersetzt. Keine bestehenden Telefongewichte, Checkpoints oder Besitzerdateien gelesen/überschrieben. Kein vollständiger autonomer allgemeiner Softwareentwickler behauptet.

ChatGPT im Plus-Chat kann mit verfügbarem GitHub-Zugriff PR/Diffs/Evidenz prüfen, den nächsten begrenzten Patch planen und konkrete Termux-Prüfbefehle vorbereiten. Ohne Terminal-/Gerätewerkzeuge kann es keine neuen Tests, Builds oder Gerätebelege ausführen; solche Ergebnisse müssen aus echten Workerläufen kommen. Keine Quote erfinden. Nach jedem kleinen belegten Teil Commit/Push und diesen Einstieg aktualisieren.

Rücksetzung: neue Opt-ins nicht aktivieren oder einzelne Meilensteincommits im geschützten Branch zurücknehmen; keine Datenmigration, kein Löschen von Besitzerzustand. Details: [OS-Isolation](CONTINUUM-OS-ISOLATION-20261021.md), [persistenter Worker](CONTINUUM-PERSISTENT-WORKER-20261021.md), [GGUF](CONTINUUM-GGUF-INFERENCE-20261021.md), [Forschung](CONTINUUM-CAPABILITY-RESEARCH-20261021.md).
