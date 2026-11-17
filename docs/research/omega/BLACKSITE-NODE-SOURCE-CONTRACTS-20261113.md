# Source-contract alignment for Code55

Base: `bfef9db3c5d13e24245f617cc2f116a7c5800188`.
Integrator: Astra, `work/jarvis55-master-20261113`, targeting the existing Code55 integration line. This patch changes four source-contract files and this handoff only. No Android production code, test exclusions, workflow or signing changes.

## Failure evidence

Actual prior run: [43354416362](https://github.com/Ullmann27/device-ai/actions/runs/43354416362), source `fafac263d45645b5193640cb73bf703f33509b91`, node-backend job `131652884130`.
The retrieved job log confirms critical PASS then full-node FAIL, with artifact `14005322964`, ZIP SHA-256 `b5ea9218877e941ac3027968a40d0a578fdcb3bb67f2d53370e35f5275c67155`.
The integrator's artifact readout reports 2,498 tests, 2,454 pass, 7 fail and 37 skip.

Independent V8 source-contract evaluation of the four unchanged files against the reviewed base reproduces exactly the seven indicated failures. This uses fetched immutable Kotlin sources and minimal assertion/read-file adapters; it is not native Node, Kotlin or Android execution.

## Exact contract mapping

| Existing contract | Actual production path now required |
| --- | --- |
| Offline owner speech veto | addHomeGreeting formats value once into shown; bubble and deferred speakReply receive shown, preserving allowMaya and speakNow. Raw value or unconditional online speech remains rejected. |
| Silence JVM regression | silenceRetriesAreBoundedAndUseAColdRecognizer replaces the obsolete one-retry test name. Policy remains capped at three retries, increments turn identity and pauses on exhaustion. The Activity consumes the cold-recognizer recommendation only for an explicit conversation turn; owner PAUSED survives backgrounding. |
| Hologram pause lifecycle | foreground becomes false; computer feedback is permitted only while visible, in multiwindow, active, and not awaiting consent. onStop clears visibility. An unconditional foreground grant is rejected. Existing frame-loop/low-budget restrictions remain. |
| Fold state retention | Reflow retains previous dashboard state but takes computer from the rebuilt home, whose existing controller restores the feed. Direct stale computer-state restoration remains rejected. Composer, selected agent, messages, telemetry and IME tests remain. |
| Portrait layout entry | forContent explicitly selects CinematicWorkstationLayout for workstation mode, forwarding viewport, font scale and compactPresentation. The prior create path remains the fallback; the live home selects workstation explicitly. |
| Head/orb geometry | Both regions still use their respective local coordinates. Layered composition follows shell.workstation; absent geometry clears both regions. Pairing and nonlayered overlap validation, plus stage detach on leaving layered mode, remain required. |
| Live surface reflow | The same hologram is detached/re-attached, and both home and hologram inherit foreground OR the strictly gated computer-feedback state. Unconditional true is rejected. |

All 21 original test cases remain (7 + 5 + 4 + 5), and every pre-existing `assert.doesNotMatch` line remains unchanged. Additional assertions cover the actual safety conditions above.

## Validation boundary

V8 source-contract adapter, unchanged control: seven failures, matching the reported seven stale contracts.
V8 source-contract adapter, updated control: all 21 test callbacks completed without assertion failures.
Twelve independent source mutations were rejected:
- `offline-veto-dropped`: rejected by the updated source contract.
- `silence-retry-limit-inflated`: rejected by the updated source contract.
- `owner-pause-overridden`: rejected by the updated source contract.
- `background-consent-guard-dropped`: rejected by the updated source contract.
- `background-visibility-guard-dropped`: rejected by the updated source contract.
- `stale-computer-state-restored`: rejected by the updated source contract.
- `live-feed-restoration-removed`: rejected by the updated source contract.
- `workstation-compact-state-lost`: rejected by the updated source contract.
- `orb-uses-head-coordinates`: rejected by the updated source contract.
- `layered-forced-for-every-layout`: rejected by the updated source contract.
- `unlayered-overlap-guard-removed`: rejected by the updated source contract.
- `reflow-always-foreground`: rejected by the updated source contract.

Native Node execution for this patch: **NOT_RUN** here; runtime unavailable. Kotlin compilation, Android UI/device tests and Fold7 validation: **NOT_RUN** here. These source assertions do not prove visual quality or live device behavior.

Next integration check: run `node --test .github/continuum/continuous-conversation-wiring.test.mjs .github/continuum/continuum-hologram-context-wiring.test.mjs .github/continuum/fold-responsive-reflow.test.mjs .github/continuum/fold7-portrait-reference.test.mjs`, then the existing source-bound full-node QA on the integrated SHA. The root integrator owns that CI run. Revert this test-only commit if contract review rejects a mapping; do not alter production behavior to resurrect obsolete spelling.
