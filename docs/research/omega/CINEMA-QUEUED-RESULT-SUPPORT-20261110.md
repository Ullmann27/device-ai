# Cinema queued-result support

## Source and ownership

- Canonical target: `cinema/fluid-light-stage-20261110`, PR #715.
- Integration owner: existing Cinema integrator; this support branch changes no shared branch.
- Verified remote baseline: `5d36c5a57c031308c8de91049e19e9e8cb8365d5`.
- Local baseline: `47a86df300210de7298e7768959cd5bf0c96fb4f`.
- Both complete source trees: `970445b70dcd57d344f43ff95bfd58fd505f42ab`.
- Support branch: `fix/cinema-queued-result-support-20261110`.

## Bounded defect

The current production `JarvisShellView` queues a new work order during the
previous return, but drops its early result in `playWorkWindowEntrance`.
`completeReturn` and `beginWorkStage` reset the shared deferred result flag.
The result therefore never advances after the queued order's construction.

The existing review and OMEGA audit report this same case. The current remote
source still contains it. No later fix or review reply was visible when this
checkpoint was prepared. Null fly-in accent fallback is a separate open finding
and is outside this single lifecycle fix.

## Acceptance and boundaries

Use the actual existing view and Robolectric suite. Reproduce a queued order's
early result before any production edit; then preserve that result through
return and construction. Cancellation, replacement and unrelated turn events
must not transfer the result to another order. Reuse existing clocks/events.

Baseline regression: 8 actual-view tests, 2 expected failures. The queued early
result never emits its forward motion; a repeated queued order also loses it.
The other six existing/negative controls pass. Raw output and dependency/source
hashes are in `docs/evidence/cinema-queued-result-20261110/`.

The first isolated full Gradle attempt lost its daemon during Kotlin compilation;
it did not execute tests. A scoped Kotlin 2.4.0/JDK21 compilation then rebuilt the
actual baseline View and the tests against frozen existing app dependencies and
ran JUnit/Robolectric API28. This is not a fresh full Android build or device test.
Remote #715 advanced to `041abb081ae7dac637d3f0fabdae525f19acf117`; its only
change since the baseline is the AGENTS checkpoint rule, not production code.

Current status: regression reproduced; production fix NOT_RUN.
PORTIERT: no new fix yet. VERIFIZIERT: bounded baseline failure and controls.
GERAETEBESTAETIGT: NOT_RUN.

Existing Cinema motion/thermal policy and the EXP-05/06/07/12/15 visual contracts
remain unchanged. The numerical EXP-15 research registry is unrelated. No
asset, model, permission, package, version, signer or main/RC change is proposed.

## Next step and rollback

Add a failing regression to the existing lifecycle suite, run it on the exact
baseline, implement only the proven cause, then rerun relevant controls. Keep
raw results and source hashes. Integration follows an exact-source review by
the Cinema integrator; physical Fold7 acceptance remains separate. Revert this
support branch's own commits to remove the change.
