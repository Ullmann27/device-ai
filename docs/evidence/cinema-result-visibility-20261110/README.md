# Result visibility after staggered construction

The real result-forward path requested a settled rank-2 window at 900 ms. `pose`
then subtracted its 140 ms stagger, so the text alpha stayed at **0.843878**.
The process window's return snapshot similarly stayed at **0.9804847**. Thus a
result event dimmed text that was already fully constructed.

`settledPose` includes the rank delay before sampling the existing construction
model. `resultAdvance` and the result-return snapshots use it. Actual time-based
entrances, rank staggering, motion budgets and the canonical clock are unchanged.
There is no new animation engine or cold-open redesign.

The baseline has three focused tests: two reproduce the opacity defect, and the
early-return continuity control passes. The corrected source passes **33/33**
host tests: four focused checks plus 29 existing entrance tests. See `RESULT.json`,
the raw JUnit summaries and source hashes. This is pure JVM evidence; no Android,
Filament, AGSL, native render or Fold7 result is claimed.

## Integration ownership

Integration owner: root, `cinema/fluid-light-stage-20261110`, PR #715.

After applying this patch, root must replace the two *already-settled* result
samples, without changing actual elapsed-time samples:

- `JarvisShellRenderer`: the `clearIdle && resultForward && role == ENTER`
  branch uses `settledPose(kind, rank, side = side, budget = depthBudget,
  animatorScale = animatorScale)`.
- `JarvisShellView.publishWorkWindowMotion`: the non-exiting `workClear &&
  resultForward` branch uses `settledPose(Kind.WORK_WINDOW, rank, side = side,
  budget = budget, animatorScale = animatorScale)`.

Those files remain owned by the integration agent and are not edited here.
Native source compilation and a result-frame capture are the next acceptance
steps. Until then: timing fix **host-verified**, full caller integration **pending**,
device confirmation **NOT_RUN**. Rollback: revert this helper/caller patch; no
assets, models, package identity or saved data are changed.
