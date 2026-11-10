# Cinema performance review and device capture

Reviewed source: `4ac9c9b94b9fb5e391646e2e99e986db32ec7997`,
`cinema/fluid-light-stage-20261110`. Integrator: the current Cinema owner.
Scope: existing presentation budgets, actual telemetry, home navigation and original-avatar replay.
Related contracts: EXP-15 frame budget; existing Fold7 quality lab. No new runtime, bus or tracker.

## Result and bounded correction

The existing `tools/fold7_quality_lab/fold7.py::parse_gfxinfo` discarded every
completed frame of 1000 ms or longer. That concealed the worst stalls in the
frame count, p95 and over-budget percentage. It also accepted zero/negative
start timestamps when their difference happened to be small.

The correction validates positive, ordered signed-64-bit timestamps and retains
completed multi-second spans. Zero, reversed, maximum-sentinel, malformed and
nonzero-Flags rows remain excluded. No arbitrary duration ceiling replaces the
old one; a slow completed frame is evidence, not an outlier to erase.

Regression result before correction: two new tests FAIL. The four-frame fixture
reported only one frame, and the invalid-timestamp fixture reported two frames.
After correction: **46/46 existing-lab Python tests PASS**, including the two
regressions. The fixture now yields four frames, p95 5000 ms and 75% over budget.
These numbers describe synthetic parser inputs, not Android performance.

Reproduce software checks from a clean committed checkout, writing receipts outside it:

```bash
python3 -B -m unittest discover -s tools/fold7_quality_lab/tests -v
python3 -B tools/fold7_quality_lab/run_unit.py --source-sha "$(git rev-parse HEAD)" --output /tmp/cinema-perf-host-tests.json
```

The existing runner refuses source-bound PASS for a dirty or changing checkout.
Use a fresh output name; it never overwrites receipts. Rollback: revert this
measurement patch only. No APK, signing, dependency or runtime policy changed.

## Existing Android call paths

| Concern | Actual path at reviewed source | Evidence boundary |
| --- | --- | --- |
| Thermal status | `ContinuumHologramView.observeThermalPressure` reads `PowerManager.currentThermalStatus`, registers API29 listener; detach removes it | Android severity, not temperature or CPU/GPU utilization. API26–28 retain initial zero; no measured-cool claim there |
| Presentation budget | `renderBudget` → `JarvisRenderQuality.adaptiveAnimation` → `JarvisComputePhasePolicy.apply` → `AvatarEssentialMotionPolicy.frameDelayMillis` | Fixed policy from OS/phase inputs; no measured-frame feedback controller |
| Thermal floor | Background → SUSPENDED; power saver, reduced motion or severity ≥3 → LOW; warmth plus inference reduces particles, orbit segments and cadence | Phase shaping cannot raise the base budget. Diagnostic proof bypasses phase shaping only |
| Essential face motion | `updateMotionPolicy` → `frameLoopAllowed`; actual Filament `render` receives `canAnimateFrame()`, not `budget.animate` | Visible mouth/eyes/head remain at LOW; reduced motion and hidden/background stop. LOW speaking requests 50 ms; other LOW requests 80 ms |
| Original Filament quality | `FilamentAvatarRenderer.setQuality` changes bloom resolution 128→64 and levels 4→3 | Same rig, joints, visemes and main transform. No mesh replacement, dynamic resolution or measured GPU optimization |
| Frame requests | Hologram uses `postOnAnimationDelayed`; Shell uses animation invalidation/delayed existing callback | `frameDelayMillis` is a requested schedule, not achieved FPS. Hologram subtracts fixed 16 ms before vsync; variable-refresh pacing must be measured |
| Native submission | Existing Hologram draw → Filament mapper/joint upload → `beginFrame` → `render`/`endFrame` | `beginFrame=false` can skip submission. Callback, Canvas draw, render submission and display presentation are different events |
| Shell diagnostics | `frameLogging`, `sampleFrames`, `JarvisFrames` count draw intervals and CPU `onDraw` time | Disabled by default; repository callers enabling it are tests only. Idle gaps are included; foreground overlay/GPU/present time are not measured |
| Local model heat guard | `LocalModelHost` wraps shared runtime in `LocalModelThermalGuard`; severe heat blocks new generation or cancels current generation | Separate from graphics; no claim that render settings change model throughput |

Priority remains mouth/eyes/head and main pose before background detail.
Existing shell/lighting budgets are reused; no unmeasured frame-rate floor is
raised. No claim of zero stutter or a measured GPU/CPU saving follows from code
inspection. Thermal headroom is not consumed by this path. Adding an arbitrary
headroom threshold would require actual device support and calibration; the
[Android API](https://developer.android.com/reference/android/os/PowerManager#getThermalHeadroom(int))
allows unsupported/over-frequent calls to return NaN.

## Independent review of 4ac9c9b

- Home navigation: the external phone bar and its reserved margin are hidden
  while the cockpit owns navigation, restored in Chat, and resynchronized after
  insets/layout changes. The callback is cleared before rebuilding the root.
  The new native-host test exercises Home→Chat→Home and preserves the avatar
  instance. This reviewer ran the two existing Node source-contract suites:
  **7/7 PASS**. These are source checks, not device taps or an independent rerun
  of root's Gradle/native-Canvas suite.
- Original-avatar replay: exported neck and residual head rotations match the
  Filament joint split; mapper receives authored azimuth; tooth-prefix hiding
  follows the production constant. Independent SHA-256 comparison matched all
  seven mapper/renderer source pins, unchanged original GLB and replay script
  against root's `original-avatar-host-render.json` (four poses).
  That receipt names older mapper commit `da3cb58`; the verified identical
  source hashes permit only the stated host-replay attribution. Blender pixels
  do not verify Filament appearance, audio synchronization or FPS.

## Reproducible Fold7 measurement protocol — NOT_RUN

1. Record exact installed APK source SHA, version, APK/certificate hashes from
   the authorized build receipt, device model/SDK, display mode, real refresh
   behavior, font scale, brightness, charging, power saver, cinema settings,
   local model hash and thermal status. `pm path` alone proves no signature or
   source SHA. Do not install or sign as part of this measurement.
2. Use an explicitly authorized attached device and the existing package
   `at.heinz.deviceai.continuumlocal`. Perform the same bounded sequence on each
   candidate: idle 30 s; open/close a work window; fixed local-model prompt;
   actual speech; STOP; Home→Chat→Home; fold/unfold. Repeat each at least three
   times in the same display and thermal conditions. Separate model load/startup
   from warmed steady state. Stop a long run if the OS reports severe heat;
   do not force thermal states or disable protection.
3. Isolate a short scene with the existing `gfxinfo reset` diagnostic, then
   capture `framestats` immediately after it. The table is recent buffered
   frames, not necessarily the whole scene; record its first/last timestamps
   and count. Keep a continuous trace for long runs. A 10-minute run followed
   by one `framestats` dump does not prove ten minutes of smoothness.

```bash
adb -s "$FOLD7_SERIAL" shell dumpsys gfxinfo at.heinz.deviceai.continuumlocal reset
adb -s "$FOLD7_SERIAL" shell dumpsys gfxinfo at.heinz.deviceai.continuumlocal framestats > gfxinfo-scene.txt
adb -s "$FOLD7_SERIAL" shell dumpsys thermalservice > thermal-after.txt
adb -s "$FOLD7_SERIAL" shell dumpsys meminfo at.heinz.deviceai.continuumlocal > meminfo-after.txt
```

Execute the scene between reset and dump. For existing low-frequency telemetry
use `fold7.py capture` as documented in `docs/quality-lab/README.md`; use the
same sampling overhead in both runs. Its historical-frame warning still
applies, and its `over_budget_pct` is not automatically FrameTimeline jank.
Use measured refresh timing, not an assumed 120 Hz or reciprocal requested
cadence. Adaptive deliberate redraw reduction is not itself a dropped frame.

4. For presented-frame timing use an owner-authorized 30 s Perfetto trace with
   Android FrameTimeline explicitly enabled, together with CPU scheduling,
   frequency, `gfx`, `view` and process attribution. The simple older
   `perfetto ... gfx view` command alone does not explicitly request
   `android.surfaceflinger.frametimeline`; inspect actual track presence before
   drawing conclusions. Record enabled data sources and buffer-loss status.
   See the official [FrameTimeline configuration](https://perfetto.dev/docs/data-sources/frametimeline).
   Inspect app and SurfaceFlinger expected/actual timelines separately; attribute
   late/dropped frames and buffer stuffing. TextureView composition is not a
   stand-alone GPU duration for each Filament draw. If driver GPU counters or
   a layer cannot be attributed, report that metric UNAVAILABLE.
5. Report per scene: sampled frame count/window, p95 and maximum frame duration,
   over-budget rate with its actual budget, FrameTimeline late/dropped counts,
   CPU usage/frequency if observed, PSS, thermal severity before/after, and
   visible mouth/head/window continuity. Preserve all completed long frames.
   Battery temperature is not CPU/GPU silicon temperature. Missing metrics
   remain null/UNAVAILABLE. Screen recording adds load; measure it separately
   or include it identically in both arms.
6. Keep raw traces and device dumps locally; publish only reviewed technical
   aggregates. A comparison remains exploratory without controlled repetitions
   and source identity. Host timings and Blender renders never substitute for
   Fold7 CPU, GPU, temperature, presented FPS or power measurements.

Reference: Android [`dumpsys`](https://developer.android.com/tools/dumpsys#framestats)
describes recent frame timing, while [rendering guidance](https://developer.android.com/topic/performance/issues/render)
distinguishes UI/RenderThread work and display-rate budgets. Sources checked
2026-11-10. Physical Fold7, native GPU/CPU tracing, thermals and FPS: **NOT_RUN**.
