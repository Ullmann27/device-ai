# BLACKSITE: recurring neural practice gates

Source: `626309c49474e45b1d6b1371868e53b3249d235f`, tree `f3abfe28a79c99753b6f343796a0f694dde9d8b5`.
Integration owner: root; target `integrate/code55-apk-fable-20261112`.

## Demonstrated source gap

`AgentActivity.foregroundLearningCheck` checked opt-in, battery and power
saving before calling `NeuralPracticeScheduler.practiceOnce`.
`NeuralPracticeJobService` checked opt-in and battery.
The shared practice method itself checked opt-in only before and after
training, passed only caller cancellation into the training loop, and did
not consult global autonomy PAUSED or the existing thermal/resource budget.
An already opted-in classifier could therefore continue practice while
recurring autonomy was paused or the device exceeded learning heat limits.

This is the existing 273-weight classifier, limited to 200 updates and a
1200 ms training window. It is not evidence of unbounded Gemma inference.
The separate process-wide LocalModelHost already wraps generative runtime
calls with LocalModelThermalGuard; it is unchanged.

## Correction

The existing practice method now combines current opt-in, thread/caller
cancellation, ContinuumAutonomyControl and ContinuumLearningRunBudget.
The public constructor uses the existing live Android resource sampler.
Unavailable resource sampling defers conservatively through unknown battery.

The per-run stop state stays latched: an observed pause, revocation,
cancellation, thermal/resource limit or deadline cannot be cleared by a
later callback. The same callback guards entry, individual training updates
and the final save decision. Cancellation returns without counting a
training attempt or changing the saved neural snapshot.

Resource reads retain the shared budget's 1500 ms sampling interval.
Preference/cancellation checks remain cheap per-update checks.
No new worker, periodic job, scheduler, journal, model, event bus or background
retry was added. Existing job IDs, cadence, global RUNNING exclusion,
three-stagnation limit, corpus fingerprints, holdout acceptance and
compare-and-swap persistence remain unchanged.

## Regression cases

Eleven Robolectric cases exercise the real scheduler and persisted fixture:
disabled entry; global pause; initial moderate heat; unavailable telemetry;
disable at first progress; pause at first progress; observed heat followed
by recovery; one-shot caller cancellation; shared deadline; a newly allowed
run plus duplicate exclusion and bounded resource sampling; public
constructor using an actual synthetic Android battery broadcast.

The interrupted cases require unchanged stored bytes, saved report,
attempt count and improvement count. Allowed execution retains the existing
acceptance decision; no test assumes that training must improve the model.

Validation here: source/control-flow review only. No runtime is available.
Kotlin compilation, Robolectric execution and hardware checks: NOT_RUN.
The two existing Node source contracts referencing this scheduler were
reviewed; their required call patterns remain present, but they were not run.
Root must integrate and run the new tests on the exact final source.

The previously running full Android QA on source 41306257 had step 8 report
failure while lint was still running when this handoff was prepared.
That failure predates this patch; its actual log/XML must determine the
cause. No test gate or timeout was changed.

## Limits

Cancellation is cooperative at the existing callback boundaries, and
telemetry is sampled, not an instantaneous thermal interrupt. This patch
does not change JobService stop/restart token lifetime or claim hardware,
voice, FPS, energy, temperature, model-quality or autonomous-action results.
No original signing, publication or installation was performed.
