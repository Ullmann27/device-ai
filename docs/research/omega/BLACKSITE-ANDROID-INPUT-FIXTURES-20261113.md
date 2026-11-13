# Android input and scroll fixture repair — 2026-11-13

Source: `c83faa2639f1521948b46a10c2438bc4281c93da`.

## Actual failing run

- Full Android QA: [run 43344572298, job 131620673839](https://github.com/Ullmann27/device-ai/actions/runs/43344572298/job/131620673839).
- Tested source: `41306257ce03c1e22e998877bf769ce42b3a0486`.
- Gradle reported 5,321 tests, 18 failures, 18 skipped. This repair addresses ten failures, not the whole suite.
- XML artifact: `Code55-full-Android-source-QA`, ID `14004060039`, 920,799 bytes.
- Artifact SHA-256: `516a90e1925d1ae443a49d9b15348139dbd72535a4aa4487d3ff229423573366`.
- [Artifact-only XML diagnostic](https://github.com/Ullmann27/device-ai/actions/runs/43350986680/job/131641725448) verified that digest and extracted all 18 failures without recompilation.

| Existing case | XML failure |
| --- | --- |
| `floatingImeExcludesVisibleNodeOutsideRenderedViewport` | Expected `TARGET_OUTSIDE_MONITOR_VIEWPORT`; got `COMPUTER_RENDERED_OBSERVATION_MISMATCH` at line 170. |
| `floatingImeRejectsTargetPartlyOutsideRenderedViewport` | Same mismatch at line 185. |
| `floatingImeAllowsAndVerifiesTargetInsideRenderedViewport` | `assertTrue` failed at line 202; no more specific message. |
| `sameContainerIndexMovementConfirmsExactlyOneDispatch` | Expected `CONFIRMED`; got `UNCLEAR_DISPATCH` at line 100. |
| `badgeChangeWithoutScrollEventDoesNotConfirm` | Expected `UNCLEAR_EXPECTATION_NOT_MET`; got `UNCLEAR_DISPATCH` at line 105. |
| `movementOfDifferentContainerDoesNotConfirm` | Same verdict mismatch at line 112. |
| `wrongWindowCannotSupplyScrollEvidence` | Same verdict mismatch at line 118. |
| `queuedOldEventsCannotSupplyScrollEvidence` | Same verdict mismatch at line 124. |
| `futureDatedEventsCannotSupplyScrollEvidence` | Same verdict mismatch at line 130. |
| `unchangedPositionDoesNotConfirm` | Same verdict mismatch at line 136. |

## Contract and correction

`LiveFrameMeta.observationRevision` defaults to `-1`. Both test helpers omitted it. Before dispatch, `AndroidLiveNodePort` invokes `LiveMonitorSubmission.checkActionSource`, which requires a positive observed revision matching the rendered frame. This rejects the old fixtures before viewport checks or Android input. `VerifiedNodeAction` conservatively reports a dispatch exception as `UNCLEAR_DISPATCH`.

The XML proves the revision rejection in both viewport-negative cases. Source tracing explains the shared prerequisite in the other eight cases; the repaired suite still needs execution.

The setup now delivers an initial content event through `ContinuumScreenObservationService`; connection alone does not establish a positive revision. Each helper constructs one immutable submitted frame with that observed revision before the action. The submitted-frame callback returns that frozen frame. It does not manufacture a new receipt from a later observation.

All 14 node-port tests and eight scroll tests remain. Their existing assertions, including every expected viewport rejection, exact text observation, scroll-container identity, wrong-window rejection, event-time rejection, unchanged-position rejection, grant revocation and single-dispatch check, are preserved.

Four new tests cover:
- A positive stale revision substituted at the final node refresh: no Android input.
- An unbound revision substituted at final refresh: no Android input.
- Another rendered window substituted at final refresh: no Android input.
- Unbound scroll receipt: no Android input, followed by a valid independently executed scroll to verify observation-watch cleanup.

Production code, permission checks, render submission checks, action effect verification and CI filters are unchanged. No tests were deleted, ignored or assumed away.

## Validation status

- Source review: completed against the production call chain and actual XML messages.
- All original test methods and assertion lines retained by text comparison: 22 methods, 71 assertion lines.
- New Kotlin compilation and test execution: **NOT_RUN** in this connector-only session.
- Fold7 hardware, frame pacing and thermal measurements: **NOT_RUN**.
- Integration must run the complete Android QA on its new exact SHA. This document does not certify a pass or an installable APK.
