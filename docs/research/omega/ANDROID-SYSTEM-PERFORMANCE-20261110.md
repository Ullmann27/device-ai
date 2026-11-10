# System performance — controlled comparisons

Target: root's Android System Intelligence PR #735, then Cinema #715. EXP-08/EXP-11.
No device engine, scheduler, permission authority, memory change or optimization is added here.

## Existing measurements and limits

| Existing source | Reused result | Limit |
| --- | --- | --- |
| `BenchmarkRunner` / `BenchmarkRun` | Selected actual task latencies through `SystemPerformanceBenchmarkAdapter` | Failed runs historically carry zero latency; adapter preserves failure counts and excludes these values. Duplicate, skipped and cancelled tasks invalidate the series. |
| `JarvisShellView.FrameSample` | Draw/interval mean and maximum | These aggregates cannot yield a median or p95. Do not invent samples from the mean. |
| `tools/fold7_quality_lab/fold7.py` | Authorized ADB PSS/CPU/battery/thermal and gfxinfo | Historical frame buffers and uncontrolled sessions do not establish before/after speedup. |
| Android diagnosis collector (root integration) | Real available bytes, own PSS, thermal, battery | One natural snapshot is descriptive; unknown workload/warm/background conditions prevent a speedup claim. |

## Contract

`SystemPerformanceEvaluator.compare(before, after)` is a pure bounded evaluator.
Missing metrics remain null. Zero is retained only if actually measured.
Each metric exposes counts, conventional median and nearest-rank p95
(`sorted[ceil(0.95*n)-1]`). Completed long stalls remain samples.
Maximum 4,096 samples per metric; nonfinite/negative values are rejected.
Timing series need 20 valid samples, frame series 120, PSS five; storage one.
These are minimum descriptive coverage rules, not statistical significance tests.

Comparisons require identical device/build/protocol/workload/display, a declared
cold/warm state, known equal charging and thermal states, battery within five
percentage points, battery temperature within 2 C, and controlled background load.
Unknown conditions default to `NOT_COMPARABLE`. Battery temperature is not CPU temperature.
Raw deltas remain visible when a comparison is rejected.

The practical no-gain band is max(1 ms, 5% baseline) for timing, max(1 MiB, 5%) for
PSS and zero bytes for storage. These thresholds are explicit engineering choices,
not a claim of scientific significance. A median or tail regression overrides any
improved metric. Any missing/failed required series prevents an overall gain verdict.
Even `EXPLORATORY_GAIN` requests controlled repetitions; it cannot authorize or
recommend a permanent change by itself. More free storage does not imply faster CPU.

## Reproduction protocol

1. Fix device, app build, display/refresh rate, local model identity, workload and
   input hash, graphics preset, network mode and all workload parameters.
2. Choose cold or warm explicitly. Do not combine app process start, first activity
   display, resumed activity and model warmup into one launch-time series.
3. Record battery, charging, battery temperature, thermal status and background
   conditions before and after every run. Discard no adverse sample silently.
4. Capture baseline A. Execute only the separately approved bounded intervention.
   Repeat exactly the workload and capture B. Where reversible, repeat A-B-B-A to
   expose order/temperature drift; record the reversal and any cache rebuilding cost.
5. Retain failures, sample counts, condition mismatches and negative effects. Compare
   storage bytes separately from latency. Never call a no-gain optimization effective.
6. App-start/UI/local-model samples require actual instrumentation or the existing
   BenchmarkRunner around the real workload; this evaluator never starts apps,
   manufactures frames or invokes the local model on its own.

Host check:

```sh
KOTLINC=/path/to/kotlinc CONTINUUM_JUNIT_JARS=/path/junit.jar:/path/hamcrest.jar \
  bash scripts/continuum-system-performance-check.sh /tmp/continuum-system-performance
```

Read-only device capture extends the existing Fold7 lab and imports its actual
ADB runner/parsers. It performs no app launch, process stop, counter reset or file
cleanup. Run the same command before and after the separately approved action,
using different output filenames. Substitute the authorized ADB device serial:

```sh
python tools/fold7_quality_lab/system_capture.py \
  --serial DEVICE_SERIAL --consent-read-only \
  --protocol-key system-compare-v1 --workload-key fixed-local-workload-v1 \
  --scenario fixed-local-workload-v1 --display inner --font-scale 1.0 \
  --frame-budget-ms 16.667 --samples 20 --interval 1 \
  --warm-state warm --background-controlled --output before.json
```

Only declare controlled background/warm state if actually controlled. `display`,
font and frame budget are declarations, not device-setting changes. The capture
contains a pseudonymous hash of the ADB serial; keep actual captures private.
The build key binds the readable base/split APK SHA-256 digests plus OS fingerprint;
inaccessible digests yield an unknown build, never a guessed identity. APK hashing
introduces initial IO; inspect conditions and allow stabilization in the protocol.
Frame samples exclude timestamps already present in the initial gfxinfo snapshot;
missing baseline, no fresh completed frames or excess samples are explicit errors.
PSS failures and within-session thermal/temperature instability are preserved.
The capture's field names match the Kotlin contract. Import into a review adapter
only as observations, never as a claim of owner-approved optimization. No JSON
import entry point is added to the live app by this patch.

App-start/UI/model/CPU-task durations remain `NOT_INSTRUMENTED` in this read-only
capture. The existing CPU-percent readings have a different measurement window
and are not fabricated into per-task CPU durations. The collector alone emits no
gain verdict. Python validation: `python -m unittest discover -s tools/fold7_quality_lab/tests -v`.

Initial verification: 24 focused Kotlin/JUnit tests PASS, Kotlin 2.4.0/JDK21,
compiler heap 256 MiB/test heap 128 MiB. Tests use explicit synthetic data and
exercise the production evaluator plus existing benchmark DTOs. They establish
no physical Fold7 performance or model improvement. No Gradle run by this agent.
The extended Fold7 lab passes 57 Python tests (11 added); device calls in new tests
are mocked. Installed SDK `adb devices` actually ran and returned no attached
device. Device capture execution and before/after Fold7 measurements: NOT_RUN.
PORTIERT: root integration pending; VERIFIZIERT: scoped host; GERÄTEBESTÄTIGT: no.
Rollback: revert measurement files; existing collectors and authority remain unchanged.
