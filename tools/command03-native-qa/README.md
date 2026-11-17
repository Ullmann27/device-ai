# Command03 native host QA

This harness invokes the existing Android project, its original SDK dependencies,
and its unchanged tests. It does not install tools, sign or install an APK,
dispatch CI, change repository gates, or connect to a device.

The harness was reconstructed after an executor reset on 2026-11-15. Historical
observations are in `historical-observations-before-reset.json`. Those observations
are not newly verified results and the original local evidence was lost. The
recovery manifest records script checks only; new build/test evidence must come
from an actual later run.

## Configuration

Use Python 3.11 or newer on Linux. Copy `toolchain.example.json` to a configuration
file beside your checkout and toolchain directories, then adjust it to the
already verified local tools. Relative configuration paths are resolved against
the configuration file's directory. No machine-specific configuration belongs in
the repository.

Pass `--config /absolute/path/native-qa-config.json` or set
`CONTINUUM_QA_CONFIG` to that path. Individual overrides include `--repo`,
`--output-dir`, `--build-dir`, `--java-home`, `--android-sdk`,
`--android-user-home`, `--gradle-home`, and `--gradle-user-home`.
The corresponding task-specific environment variables begin `CONTINUUM_QA_`.

When this directory is stored at `tools/command03-native-qa`, the repository can
also be found by walking its ancestors. Evidence defaults to a sibling directory
named `<checkout-name>-native-qa-output`; build files, Gradle project cache,
resource samples, and captures remain outside the checkout. This avoids filling
tmpfs and ensures generated evidence cannot dirty the source being checked.

The project currently pins Gradle 8.11.1, Android SDK 36, and Java bytecode 17.
Use a complete JDK with `javac`, not a system JRE. Toolchain installation and
archive verification are separate responsibilities. The runner records the
paths used and refuses missing tools or assets rather than installing substitutes.

## Compile and dependency preparation

Every invocation requires an explicit candidate version code and a unique label.
Use the exact frozen 40-character source commit with `--expected-sha`.
All source must be clean by default. `--allow-dirty` is an explicitly separate
diagnostic and cannot produce a source-bound result or a capture.

```sh
python tools/command03-native-qa/run.py --config /absolute/path/native-qa-config.json \
  --mode compile --version-code 60 --label command03-recovery-compile-01 \
  --expected-sha "$CONTINUUM_SOURCE_SHA" --resolve-dependencies \
  --heap-mib 2048 --metaspace-mib 512 --resource-sample-seconds 2
```

Offline is the default. `--resolve-dependencies` explicitly permits the normal
project repositories for that invocation. For compile mode only, the adjacent
init script also resolves the app's existing debug, unit-test, and Robolectric
configurations before compilation. It adds no repository, substitute SDK, stub,
task-name exemption, or assertion change. The original `compileDebugKotlin` task
still runs. Normal model preparation performs its pinned downloads; the runner
then independently checks YuNet, SFace, and OpenCV bytes against the repository
lockfile. Missing or mismatched pins fail the result. Subsequent runs omit this
flag and remain offline; a missing dependency is reported rather than fetched.

`--plan` validates and prints the intended command without launching Gradle.
An empty cache requires `--resolve-dependencies` even for a dependency-preparation
plan. The flag is always recorded in the receipt.

## Focused unchanged tests

```sh
python tools/command03-native-qa/run.py --config /absolute/path/native-qa-config.json \
  --mode unit-tests --selection command03-unit --version-code 60 \
  --label command03-recovery-unit-01 --expected-sha "$CONTINUUM_SOURCE_SHA" \
  --heap-mib 1536 --metaspace-mib 384 --resource-sample-seconds 2

python tools/command03-native-qa/run.py --config /absolute/path/native-qa-config.json \
  --mode ui-tests --selection command03-ui --version-code 60 \
  --label command03-recovery-ui-01 --expected-sha "$CONTINUUM_SOURCE_SHA" \
  --heap-mib 1536 --metaspace-mib 384 --resource-sample-seconds 2
```

The unit selection contains 11 classes plus three existing test helpers; the UI
selection contains seven class/method selections plus two existing helpers.
`selections.json` is an explicit, reviewable list. Each run records every physical
source path, size, and SHA256; the Kotlin task verifies its actual source set
against that list before compiling. Main sources, Android resources, classpaths,
test bodies, and assertions remain unchanged. These are **focused host tests**,
not the complete suite or release QA. `--full-suite` is a separate deliberate
choice, never implied by a focused result.

Test execution uses Gradle's existing `Test.filter.includeTestsMatching` API. Raw
`--tests` arguments do not pass through the project's early task-name allowlist.
Only fresh JUnit XML from this invocation is counted. Missing selected results,
no executed tests, failures, source drift, or a changed test-source hash cannot
produce a successful test receipt.

### Separate UI compilation and execution

When retaining the Kotlin compiler beside Robolectric strains the host, run
`--mode ui-compile` first and then `--mode ui-tests` in a fresh invocation, with
the same clean `--expected-sha`, selection, toolchain, and original source/build
paths. Use distinct labels. The compile mode requires either a named UI selection
or explicit `--test` filters with `--focus-test-sources`; it retains the physical
source/hash checks and existing helpers. It runs the real
`:app:compileDebugUnitTestKotlin` task, explicitly allowed by the local QA gate.
The existing test-execution filter and its task-only gate remain unchanged.

```sh
python tools/command03-native-qa/run.py --config /absolute/path/native-qa-config.json \
  --mode ui-compile --selection command03-ui --version-code 60 \
  --label command03-ui-compile-01 --expected-sha "$CONTINUUM_SOURCE_SHA" \
  --heap-mib 768 --metaspace-mib 256 --resource-sample-seconds 2
```

Its successful status is `PASS_LOCAL_TEST_COMPILATION_ONLY`, with
`test_execution: NOT_RUN_COMPILE_ONLY` and no JUnit result. It proves no test
passed. The subsequent `ui-tests` run still requires fresh JUnit results for every
selected filter; normal Gradle history may reuse the genuinely compiled classes.
Neither phase changes application sources, assertions, SDK dependencies, signing,
or the prohibition on assembling an APK in the local QA mode.

For selected `ui-tests` only, `--isolate-test-classes` explicitly sets Gradle
`Test.forkEvery = 1` and `maxParallelForks = 1`. Each test class runs in a fresh,
sequential worker JVM. This keeps the complete selected tests and their assertions,
the original 1024 MiB worker heap, and the existing task/source/JUnit gates.
The receipt records this worker policy. It is unavailable for compile-only,
unit-test, or unsigned modes; it does not make a worker exit into a successful run.

## Original HTML bridge behavior

`native-bridge-behavior.test.mjs` executes the original Android HTML and the
unchanged page, operation, instrument, telemetry, and workspace-motion scripts
with the separate native adapter. It uses jsdom 26.1.0 for DOM behavior. Install
this test-only dependency outside the checkout; no application dependency or
Android runtime package changes are needed.

```sh
npm install --ignore-scripts --prefix /tmp/command03-dom jsdom@26.1.0
COMMAND03_DOM_MODULE=/tmp/command03-dom/node_modules/jsdom/lib/api.js \
  node --test tools/command03-native-qa/native-bridge-behavior.test.mjs
node tools/command03-native-qa/check-visual-source.mjs
```

The DOM checks exercise document visibility after repeated native updates,
rejection and acceptance of commands, draft/session boundaries, and the
separation of native actions and telemetry from the original demonstration.
The clock, graphics seams, and Android navigation transport are test doubles;
these checks do not demonstrate Chromium/WebView pixels, a GPU frame, actual
speech synchronization, or Fold7 behavior. The separate source lock verifies
the original 35 asset bytes and the two-include Android entry point.

The actual Android Activity/WebView routing checks are in
`at.heinz.deviceai.ContinuumCommand03BridgeUiTest` (seventeen cases),
`at.heinz.deviceai.ContinuumCommand03StageUiTest` (four cases), and
`at.heinz.deviceai.ContinuumCommand03MediaStageUiTest` (four cases). They cover
request acknowledgements, pending bot lookup, existing controls during busy
work, draft restoration and preservation of the same WebView across actual
native root replacement. Run all three physical test classes with the existing
harness on a clean, pinned source, for example:

```sh
python tools/command03-native-qa/run.py --config /absolute/path/native-qa-config.json \
  --mode ui-tests --test at.heinz.deviceai.ContinuumCommand03BridgeUiTest \
  --test at.heinz.deviceai.ContinuumCommand03StageUiTest \
  --test at.heinz.deviceai.ContinuumCommand03MediaStageUiTest \
  --focus-test-sources --version-code 60 --label command03-html-ui-01 \
  --expected-sha "$CONTINUUM_SOURCE_SHA"
```

The existing `command03-ui` selection tests the classic native return path.
Its Activity fixtures explicitly choose that mode; the HTML bridge fixture
explicitly chooses the original HTML main stage. Use the existing selection
to include its two physical test helpers, rather than copying only its filters.

Successful runs and the independently checked unsigned APK for source
`f79d57546516b92788bd83c6039ce474955da8ea` are recorded in
`docs/research/omega/COMMAND03-HTML-VALIDATION-HANDOFF.md` with twelve fresh
JUnit XML files. The normal Gradle incremental history at the original source
and build output paths was reused. Relocating copied caches triggered a full
compile in this constrained workspace; those failed attempts are documented.

## Live-frame and native-speech verification

The later clean source `b56a614b290977684b50910fac3cc8d14e4294c0` has
125 fresh focused Gradle test results (22 HTML/media, 71 unit, 32 classic UI)
and 28 fresh DOM/interop cases. It also has a normal unsigned release build and
an independent APK source/structure audit. The exact evidence, compiler-state
recovery scope, failed attempts, and hardware limitations are recorded in
`docs/research/omega/COMMAND03-LIVE-FRAME-VISEME-HANDOFF.md` and
`docs/research/omega/command03-live-qa-b56a614b/README.md`.
These are targeted local results, not a complete release or physical-device gate.

The strict cross-language DOM case consumes the actual Stage serialization of a
synthetic 2x2 JPEG. Extract the `COMMAND03_COMPUTER_WIRE:` line from the fresh
`ContinuumCommand03MediaStageUiTest` JUnit output, then pass its JSON file as
`COMMAND03_NATIVE_COMPUTER_PACKET` and set `COMMAND03_REQUIRE_NATIVE_PACKET=1`.
The latter flag makes a missing native packet a failure instead of an optional skip.
The source-bound archived packet documents this run; reuse does not establish a
new Android execution. Use `--test-reporter=tap` for an explicit Node TAP report.

## Active computer navigation

The follow-up source `7e783f3ee8bc3e75f800b8249f5cef283187752d` preserves an
already active computer session when opening its original HTML section. Three
new Activity regressions cover repeated navigation, idle controls, and leaving/Stop.
Fresh results: 25 HTML/media UI, 71 unit, 32 classic UI, and 28 DOM/native-packet
cases; 156 total with no failures or skips. The separate build directory began
empty, reused existing offline dependencies, and produced the normal unsigned
release APK. Details and the independent APK audit are in
`docs/research/omega/COMMAND03-COMPUTER-NAVIGATION-HANDOFF.md`.

## Actual Activity captures

Use the existing outer CLI and fixture; do not replace them with a mock layout.
Set `CONTINUUM_QA_CONFIG` and `CONTINUUM_NATIVE_VERSION_CODE=60` for the adapter.
The output must be a fresh directory beneath the configured `output_dir/captures`.

```sh
python tools/capture/capture_jarvis.py \
  --gradle-runner tools/command03-native-qa/capture-runner.py \
  --output-dir /absolute/path/native-qa-output/captures/command03-01 \
  --case commandPhone392 --case commandFold904 --case commandComputer904 \
  --timeout-seconds 900
```

These cases use actual `AgentActivity`, its real composer/work window, and
Robolectric API28 native Canvas at 392×852dp or 904×768dp. The fixture explicitly
selects the existing 2D head and disables animation for deterministic stills.
These are historical native-view captures; they are not a visual comparison
of the new original HTML surface, and no new capture result is claimed here.
This does **not** verify Filament, the original 3D head on a GPU, foldable hardware,
speech synchronization, installation, or physical-device performance. The outer
CLI checks the source commit and per-frame PNG hashes and rejects missing frames.

## Evidence and boundaries

A serial file lock prevents overlapping runs through this harness. Each run writes
its command, source before/after, resource samples, compiler errors, fresh test
counts, and exit status under `output_dir/runs`. Memory sampling does not prove an
OOM cause. A source change invalidates the result. Interrupts terminate the
runner's own Gradle process group; the capture adapter keeps the outer timeout
contract.

The `unsigned` mode is only for an authorized local transport artifact. It checks
package/version, ARM64-only ABI, SDK levels, non-debuggable status, ZIP integrity,
16KiB alignment, original avatar hash, and absence of signatures. An unsigned APK
is never presented as an installable owner update. Original signing, exact-source
release workflows, owner approval, and device acceptance remain separate gates.
