# Ausgeführte Prüfungen · Astra 0.3.20

Quell-/Testcommit: `d7429145ad5dff64364ed47f9ceb0c07a5a8ed85`. Danach nur Belege/Übergabe.
Linux x86_64; vorhandene Sol-Toolchain JDK17, Gradle8.11.1, Kotlin2.4.0, Android SDK36, native Robolectric SDK28.

```bash
source /workspace/scratch/continuum-toolchains/env.sh
node .github/continuum/contrastive-parity.mjs prepare /workspace/scratch/continuum-astra-0320/parity/contrastive
node .github/continuum/cre-parity.mjs prepare /workspace/scratch/continuum-astra-0320/parity/cre1
node .github/continuum/backlog-fixtures.mjs roundtrip-prepare /workspace/scratch/continuum-astra-0320/parity/backlog
export CONTINUUM_CONTRASTIVE_PARITY_DIR=/workspace/scratch/continuum-astra-0320/parity/contrastive
export CONTINUUM_CRE1_PARITY_DIR=/workspace/scratch/continuum-astra-0320/parity/cre1
export CONTINUUM_BACKLOG_ROUNDTRIP_DIR=/workspace/scratch/continuum-astra-0320/parity/backlog
gradle --offline --no-daemon --max-workers=2 --console=plain  -PcontinuumUiWidgetTests=true  -PcontinuumUiEvidenceDir=/workspace/scratch/continuum-astra-0320/frames  -Pkotlin.compiler.execution.strategy=in-process  :app:testDebugUnitTest :app:compileDebugAndroidTestKotlin :app:lintDebug
node .github/continuum/cre-parity.mjs verify "$CONTINUUM_CRE1_PARITY_DIR"
node .github/continuum/contrastive-parity.mjs verify "$CONTINUUM_CONTRASTIVE_PARITY_DIR"
node .github/continuum/backlog-fixtures.mjs roundtrip-verify "$CONTINUUM_BACKLOG_ROUNDTRIP_DIR"
```

Alle Exit0: 975 JUnit, 35 davon Robolectric, 0 Fehler/Skips; 195 XML. Lint0/0 im vorhandenen `NewApi`-Gate (keine Behauptung aller denkbaren Lintregeln). Instrumentation kompiliert, nicht auf Gerät ausgeführt. Paritätsdetails in eigenen Logs.

```bash
env -u JAVA_TOOL_OPTIONS -u NODE_TEST_CONTEXT -u CONTINUUM_REPAIR_DEPTH  node --test --test-reporter=tap --test-concurrency=2  .github/continuum/*.test.mjs scripts/*.test.mjs
```

Exit0, 1111 entdeckt, 1105 bestanden, 6 wegen fehlendem Kotlin-PATH übersprungen. Die obige Toolchain war in diesem separaten Shellaufruf zunächst nicht geladen. Keine gesamte Wiederholung: nur drei betroffene Dateien nachgeladen:

```bash
source /workspace/scratch/continuum-toolchains/env.sh
env -u JAVA_TOOL_OPTIONS -u NODE_TEST_CONTEXT -u CONTINUUM_REPAIR_DEPTH  node --test --test-reporter=tap --test-concurrency=2  .github/continuum/owner-kotlin-preview.test.mjs  .github/continuum/kotlin-delegate-synthesizer.test.mjs  .github/continuum/kotlin-delegate-acceptance.test.mjs
```

11/11,0 Fehler/Skips, Exit0 im `node-kotlin-completion.txt`, separate Ausführung auf demselben Quellcommit; nicht als ein einzelner skipfreier Gesamtaufruf ausgeben.

```bash
npm --prefix backend ci --ignore-scripts --no-audit --no-fund
npm --prefix backend run check
npm --prefix backend test
CONTINUUM_OWNER_RELEASE_VERSION=21 bash scripts/render-mentor-preview.sh
/workspace/scratch/continuum-toolchains/android-sdk/platform-tools/adb devices -l
```

Backend60/60 und Syntax Exit0. Dispatcher erwarteter Exit69, keine Signierung angestoßen. ADB Exit0, keine Geräte. Erster unqualifizierter `adb`-Aufruf hatte Exit127 (Platform-Tools nicht im PATH); danach tatsächliches SDK-Programm benutzt.

Gezielte Vorprüfungen nutzten dieselben Gradleoptionen plus `--tests`:
- `*ContinuumHologramMotionUiTest.thermalPressureStopsAndRecoversTheSameClock`: RED1/1, Exit1, fehlender Thermalschutz.
- Erster Hologrammversuch mit 0ms Callbackverzögerung: Robolectric blieb in virtueller Choreographer-Zeit stehen; Stack geprüft, ausschließlich eigener Testworker beendet. Log `aborted-zero-delay-probe.txt`. Korrigiert auf positive8ms/32ms; keine Freigabe des abgebrochenen Versuchs.
- `*ContinuumTelemetryMotionUiTest`: RED1/1, Exit1, Messwertsprung statt Übergang.
- Hologramm/OrbitalRing/Plexus/Telemetry: danach Exit0 (`visual-green.txt`).
- Telemetry/Hologram/Controls/HomeLayout: Exit0 (`dashboard-green.txt`).
- `*LocalModelCatalogTest`: RED3/1 Fehler, Exit1, fehlender erwarteter/tatsächlicher SHA in Diagnose.
- LocalModelCatalog/LocalFirstPolicy/LocalQwenPromptPolicy: GREEN Exit0.
- Node `learning-wave-animation`, `aaa-cockpit-and-effects`, `continuum-owner-mainapp-quality`: Exit0; `model-import-identity`: Exit0.

Keine APK-Assembly, kein Ersatzschlüssel, keine Installation, kein echter LiteRT-Modelllauf auf diesem Host. Pixelbilder sind native Canvas-Renderings unter virtueller Zeit; keine FPS-/Energie-/GPU-Messung.
