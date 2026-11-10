# Cinema event ownership regression — 2026-11-10

Later integrated Android result: **11/11 Presenter + 8/8 existing window tests PASS** on root `fac2485`; see [INTEGRATED-HANDOFF.md](INTEGRATED-HANDOFF.md). The results below describe the earlier isolated host checkpoint.

Integration owner: root, `cinema/fluid-light-stage-20261110`, PR715. This branch is the reviewable fix candidate; no merge, signing, APK, device or model run occurred. Four owned code/test commits: `ca65f6c`, `c02690d`, `7985e24`, `d4d7971`.

## Source findings and bounded change

Pinned baseline `a7b4d1a` mapped every `TurnEnded` to shell exit; neither Presenter nor `WindowProjectedMotionSampler.closeActive` checked the active owner. The sampler also preferred the last published sample to the latest observed sample, so a suppressed reduced-motion ownership change was lost. A delayed boot cue shared a presentation generation with a newer mission and could replay the entrance later.

Presenter now tracks the current mission and at most 24 retired IDs, matching the producer history bound. A current end closes its own projections and requests the existing return. Old/foreign ends cannot exit the newer scene. Acknowledged replacement retires its predecessor; standalone work can claim an idle scene, while independent worker events cannot steal an active owner scene. Duplicate starts still use the existing Shell replay policy. Delayed timeline cues additionally require the scene revision they captured.

Global NAV STOP remains global: it retires the current mission, closes its own and NAV projections, and requests return. NAV fly-in resets mission ownership but retains NAV projection binding. Session rebuilds already close old bus subscriptions and construct a new Presenter (AgentActivity baseline lines 1169–1212); no session reset mechanism was replaced. Background terminal projection cleanup remains possible. `closeActive` has no independent global-reset callers: callers are Shell.closeProjectedMotion and Shell.completeReturn(previous turn). The former pending-turn cleanup remains unchanged.

Projected motion now closes with `SystemClock.uptimeMillis`, the same domain as shell/window frames. Event wall-clock timestamps remain unchanged. Shell, window and FUI retain the existing shared frame source; this review did not measure device clock drift. No second clock, bus, scheduler or model call was added. JarvisShellView and Activity were not edited.

The bus dispatches synchronously, but Presenter renders post to the UI queue. A CLOSED extension generated during replacement is ignored by its own presenter, and a listener's newly emitted command is processed on the next queue step. The new UI regression exercises this path; it is still NOT_RUN until root's Android batch.

## Actual controlled host result

Same 12 JUnit tests: baseline sampler 8 PASS / 4 FAIL, candidate 12 PASS / 0 FAIL. The four discriminating cases are foreign close, bulk close preserving another owner, reduced-motion owner reuse, and reuse after closure. `source.txt` pins source and dependency class hashes. `run-sampler.sh` records the actual local command and absolute toolchain locations. Compiler heap 256 MB; no Gradle was used. Dependencies came from the earlier actual Android-compiled QA snapshot, not test replacements.

11 actual Presenter/Robolectric tests are prepared but NOT_RUN here. Root must run `at.heinz.deviceai.ui.CockpitAnimationPresenterTurnUiTest` plus existing replay/STOP tests after integrating the four commits. Host sampler green is not Android Presenter or Fold7 acceptance. No quality/performance claim follows from this regression.
