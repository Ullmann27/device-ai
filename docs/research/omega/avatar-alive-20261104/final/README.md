# Final host verification — 2026-11-04

Official tested source: [`d2eaa6d8ff8b2bdc4ed277a9987e8efa7c40d39b`](https://github.com/Ullmann27/device-ai/commit/d2eaa6d8ff8b2bdc4ed277a9987e8efa7c40d39b), draft [PR #526](https://github.com/Ullmann27/device-ai/pull/526) on parent #521.

Exact source tree: `4aa6b68d74901eb839b877cc66807ca57a34f835`. Local execution twin: `5a85676afdc44524306a982dba79659e4c029117`. Both gates captured a clean worktree. Evidence-only commits after this source do not change application or test code.

| Check | Result |
|---|---|
| Full unit suite | 2,620 passed, 19 skipped, 0 failures/errors; 2,639 total |
| Avatar/audio subset | 107 passed within the full suite |
| Selected UI/Canvas suite | 40 passed, 0 skipped/failures/errors |
| Android lint | 0 issues |
| Debug APK packaging | PASS |
| Packaged GLB and NOTICE | Byte-identical to source; runtime hash agrees |
| Device / audible speech / native Filament | NOT_RUN; ADB listed 0 devices |

The UI count includes four layout cases also present in the unit count. These are separate gates, not an additive unique-test total. UI callbacks/PCM and head-event inputs are controlled test fixtures. Native Canvas is host software rendering, not an Android GPU or audible TTS result.

`ui.json` and `full.json` contain per-suite counts and exact task arguments. Their corresponding `.txt` files contain the raw Gradle runs. `lint.xml` is the actual report. `package.json` records the APK/asset hashes, manifest package/version, and byte comparisons. `device-availability.json` omits device identifiers and records no installation.

Build profile: JDK17, Gradle8.11.1, Kotlin2.4.0, compile SDK36, build tools35.0.0. UI uses the existing opt-in `continuumUiWidgetTests` source set and Robolectric4.14.1. The bounded UI init script records each test and applies a 240-second task deadline; it changes no app code. Existing compiler/plugin deprecation warnings remain visible in the logs.

Full gate command after configuring that toolchain and dependency cache:

```sh
gradle --offline --no-daemon --max-workers=1 -Pkotlin.compiler.execution.strategy=in-process :app:testDebugUnitTest :app:lintDebug :app:assembleDebug --continue
```

UI reproduction: use the same Gradle profile, add `-I docs/research/omega/avatar-alive-20261104/bounded-ui.init.gradle -PcontinuumUiWidgetTests=true`, and use the explicit `--tests` selectors in `ui.json`. The init script accepts `-PcontinuumTestTimeoutSeconds=75` for the archived diagnostic, while the successful combined gate used its 240-second default.

APK SHA-256: `f79a049992a76e0c2d9f80c1c57674a603bb6db91121448b11d034e63dbf47ee`. Approved GLB SHA-256: `c8b88385c73fa01596a5734d20859440b1ecaef06cb1abea6cd7a47e62b08b9a`.

This is the repository's debug QA package, not an original-signed Continuum Lokal update. Full video equivalence remains open: audible/phoneme timing, complete startup depth cues, full body, document/action/account sources and native device acceptance. See `../../AVATAR-VIDEO-REFERENCE-MATRIX-20261104.md` and `../../AVATAR-ALIVE-HANDOFF-20261104.md`.
