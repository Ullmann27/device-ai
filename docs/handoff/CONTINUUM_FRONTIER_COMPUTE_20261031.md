# CONTINUUM FRONTIER — verbindliche Forschungsübergabe für Sol / Astra / Opus

**Datum:** 31.10.2026 · **Repository:** `Ullmann27/device-ai` · **Forschung:** [PR #287](https://github.com/Ullmann27/device-ai/pull/287) · **Basis:** [PR #285](https://github.com/Ullmann27/device-ai/pull/285), aus [#284](https://github.com/Ullmann27/device-ai/pull/284) gestapelt

> **Lesehinweis:** Dieser Text beschreibt eine **erreichte und nachvollziehbare Forschungs-Zwischenetappe**, keine produktive Freischaltung oder nachgewiesene neue physikalische Rechenleistung. Sol, Astra, Opus und alle anderen Agenten sollen die Erkenntnisse beim nächsten Projektstart lesen und vor neuer Doppelarbeit berücksichtigen.

## 1. Warum das ein relevanter Forschungsmeilenstein ist

Statt virtuelle Maschinen mit realen Prozessoren zu verwechseln, wurde eine **kapazitätsgebundene virtuelle Compute-Fabric** mit **prüfbarer Wiederverwendung deterministischer Berechnungen** und **gemeinsam genutzten Vorberechnungsstufen** entworfen und als JavaScript-Forschungsprototyp implementiert.

Ein für Continuum interessanter Befund: 3.000 **unterschiedliche** Endaufträge können mit nur 30 verschiedenen, gemeinsam verwendbaren **teuren Zwischenstufen** weiterhin 3.000 individuelle Endergebnisse erzeugen. Im synthetischen Vergleich müssen die Zwischenstufen dann 30 statt 3.000 Mal berechnet werden. Der Gegenversuch mit 3.000 verschiedenen Vorstufen zeigt **keine** Einsparung. Das ist ein überprüfbares Softwareprinzip mit Potenzial für wiederkehrende Repository-Analysen, Gedächtnisabruf und Coding-Agent-Werkzeuge.

**Nicht neu erfunden:** Memoization, Deduplikation, Common-Subexpression-Elimination, Prefix-Caching und Batching sind bekannte Techniken. **Projektspezifischer Fortschritt:** Kombination von 3.000 logischen Experten, geteilten Ressourcenlimits, konservativer Beleggrenze, versionsgebundenen Cache-Keys, expliziten Negativkontrollen und vorregistrierbaren M8-Anschlussbedingungen. Ein weltweiter Neuheits- oder Leistungsrekord wird ausdrücklich **nicht** behauptet.

## 2. Tatsächlich im Forschungszweig vorhanden

| Datei | Zweck |
| --- | --- |
| `tools/continuum-virtual-fabric/fabric.mjs` | Logische CPU-/GPU-/RAM-/VRAM-Reservierungen, Leases, Quoten, Warteschlangen; **keine echte Hardwareerzeugung** |
| `tools/continuum-virtual-fabric/expert-mesh.mjs` | 50 / 100 / 1.000 / 2.000 / **3.000** logische Spezialisten über Coding, Memory, Reasoning, Research und Training |
| `tools/continuum-virtual-fabric/reuse-broker.mjs` | Tenant-isolierte Wiederverwendung rein deterministischer Ergebnisse, In-flight-Zusammenführung, Policy-/Modell-/Input-Digests, Memory-Epoch, TTL/LRU, Default-Deny-Belegchecker |
| `tools/continuum-virtual-fabric/reuse-experiment.mjs` | Künstlicher 3.000-Job-CPU-Matrixvergleich: 30 wiederholte vs. 3.000 einzigartige Eingaben |
| `tools/continuum-virtual-fabric/shared-stage-experiment.mjs` | 3.000 **unterschiedliche Endaufgaben** teilen 30 synthetische Zwischenstufen; unabhängiger Baselinevergleich |
| `tools/continuum-virtual-fabric/cpu-smoke.mjs` | Kleine deterministische CPU-Kernel, keine echte LLM-Inferenz |
| `tools/continuum-virtual-fabric/nemo-smoke.mjs` | Nur Dry-Run-Plan eines NVIDIA-NeMo-26.10-Trainingsrezepts; keine erfolgreiche GPU-Ausführung |
| `tools/continuum-virtual-fabric/FRONTIER_RESEARCH.md` | Hypothesen, Kontrollfälle, Evidenzgrenzen, Architekturentscheidung |
| `tools/continuum-virtual-fabric/*.test.mjs` | Regressions- und Negativtests; Details unten |

**Evidenz:** zuletzt **43/43** reine JS-Prüfungen in einer isolierten V8-Ausführung **mit Node-Kompatibilitätsshims** bestanden. Das ist **kein** vollständiger nativer `node --test`-Lauf. Originale Node-Tests, Kotlin/Gradle-Build, Fold7-Gerätetest, echtes NeMo-Training, GPU-Ausführung, M8-LLM-Benchmark und produktive Integration sind **nicht nachgewiesen**. Aussagen über „100×“ beziehen sich nur auf den **Rückgang duplizierter Zwischenberechnungen** in der synthetischen Testlast, **nicht** auf tatsächliche FLOPS oder gesamte LLM-Geschwindigkeit.

## 3. Reproduzierbarkeit — ab hier weiterarbeiten

```sh
git fetch origin
git switch research/continuum-nemo-virtual-fabric-20261031
node --test tools/continuum-virtual-fabric/*.test.mjs
node tools/continuum-virtual-fabric/reuse-experiment.mjs
node tools/continuum-virtual-fabric/shared-stage-experiment.mjs
node tools/continuum-virtual-fabric/nemo-smoke.mjs --dry-run
```

`nemo-smoke.mjs --dry-run` muss offline bleiben. Kein bezahlter Dienst, keine GPU, keine externe Kontoanlage, keine Signierschlüssel und keine privaten Besitzerinformationen ohne separate Freigabe.

**Verifikation:** Immer exakten HEAD und tatsächliche Testsicherung berichten. Bei Fehlern auf dem kombinierten Quellbaum korrigieren statt das Ergebnis zu behaupten. Die Forschungszweig-Abstammung ist bewusst **nicht** identisch mit `main`.

## 4. Warum dies für den Coding-Agent wichtig ist

Der vorhandene M8-Stand nutzt `AgentActivity.runLocal()` → `AiContextCompiler` → `LocalModelRuntime` → `BoundedAgentOrchestrator` → `ToolGate`; daneben bestehen `ComputeFabric.kt`, `ComputeNodeRegistry`, `ComputeScheduler`, `ComputeResultVerifier`, der autonome Entwicklungs-Backlog und experimentelle Node-Worker.

**Zielarchitektur, noch nicht implementiert:** Owner-autorisierte Aufgabe → exakter Modell-/Kontext-/Quellstand → nur für nachweisbar **PURE + deterministische** Zwischenstufen Wiederverwendung prüfen → andernfalls bestehenden ComputeScheduler benutzen → tatsächlich ausgeführten Worker inklusive signiertem Resultat attestieren → unabhängige Tests → nur freigegebene Resultate in Erfahrung/Gedächtnis übernehmen. **Keine** Wiederverwendung von generativen Antworten als reparaturbestätigte Codepatches, keine Cross-Tenant-Datenabflüsse.

Die existierende Kotlin-ComputeFabric ist kanonisch; der JS-Forschungsscheduler darf nicht heimlich zum zweiten produktiven Scheduler werden. Vor EWM-/Gedächtnis-Anschluss: Privacy, Versionswechsel, Owner-Fakten, STALE/CONTRADICTED und Modellwechsel explizit testen.

## 5. Konkrete Priorisierung für den nächsten Sol-/Astra-/Opus-Durchlauf

1. **P0:** Repo/PR-Abstammung und `node --test` auf dem exakten Forschungs-HEAD ausführen; Fehler, Kosten und Durchsatz dokumentieren.
2. **P0:** Identische Aufgaben mit 3.000 einzigartigen Inputs und **unterschiedlichen** finalen Ergebnissen vergleichen; Skalierungs-/Quota-/Cancellation-/Cachevergiftungs- und Tenant-Isolationstests nachweisen.
3. **P1:** Den echten M8-Coding-Agent zunächst **read-only** instrumentieren. Wiederholung identischer AST-/Datei-/Dependency-/Testergebnisse messen, ohne persönliche Datensätze zu exportieren.
4. **P1:** Erst nach belegtem Wiederholungsanteil einen kleinen Adapter zwischen verifizierten **Zwischenberechnungen** und der vorhandenen Kotlin-ComputeFabric implementieren; niemals OwnerGenehmigung oder ToolGate überspringen.
5. **P2:** NeMo AutoModel 26.10 ausschließlich auf zugelassener realer CPU-/GPU-Hardware betreiben, Checkpoints und Kosten deckeln; kostenloser Drittanbieterzugang ist **kein** dauerhaft vorhandener, kommerziell nutzbarer Cluster.
6. **P2:** Echte Agenten-Reparatur über neue Fehleraufgabe, `FAIL→PATCH→PASS` inklusive Regressionen und unabhängigen Prüfbelegen nachweisen.
7. **P0 Release-Gate:** `main`, installierte APK, Original-P12, Paketidentität, laufendes EWM/SWB/CRE1/Gemma und signierter Updatekanal unberührt lassen, bis unabhängiger Build und Fold7-Abnahme abgeschlossen sind.

## 6. Übergabeformel für alle Agenten

**Bewahre die Forschung, reduziere Doppelarbeit, missachte keine physikalischen Grenzen.** Fortschritt wird an **korrekter, tatsächlich eingesparter Rechenarbeit** und an unabhängigen M8-Reparaturerfolgen gemessen – nicht an der Zahl virtueller Worker. Alle neuen Resultate mit Originalquelle, PR/Commit, Tests, kontrollierten Gegenbeispielen und präzisen Grenzen auf GitHub dokumentieren.

**Referenzen:** [PR #287](https://github.com/Ullmann27/device-ai/pull/287) · [M8 #285](https://github.com/Ullmann27/device-ai/pull/285) · [M7/M8 #265](https://github.com/Ullmann27/device-ai/pull/265) · [NVIDIA-NeMo AutoModel](https://github.com/NVIDIA-NeMo/Automodel)
