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

## Implemented result

`JarvisShellView` now retains the early result under the queued order's turn ID.
After the existing return completes, the existing construction runs and the
retained result advances through the existing `pendingResultAdvance` path.
Repeated events for that order preserve its result. Queue replacement, close
and reset discard it; another turn's STEP cannot supply a result to the queue.
Only one production file changes; no new scheduler, event, clock or asset.

Final scoped verification: **46/46 PASS**, including 8 lifecycle regressions,
existing entrance/projected-motion tests and the actual decor STOP-touch test.
The corrected baseline repeats **8 tests / 2 failures**. Counts overlap the
earlier eight-case green probe and are not added together. The first expanded
run had an ABI linkage error because the scoped compiler used `app_debug` instead
of the existing `DeviceAI_app_debug` module name. Correcting only that harness
option made the same four suites pass; the failed output is preserved.

At final source check, #715 is `5457351a7585eada2393f80aa81db4bff5cc8897`.
Its production sources remain unchanged from this baseline. Its newer commits
add the checkpoint instruction, a separate button-material test and evidence.
No competing queued-result fix is present in its `JarvisShellView` blob.

PORTIERT: actual View source on support PR #724, pending Cinema integration.
VERIFIZIERT: source-bound scoped host compilation and 46 checks; see `green.json`.
GERAETEBESTAETIGT: NOT_RUN.

Existing Cinema motion/thermal policy and the EXP-05/06/07/12/15 visual contracts
remain unchanged. The numerical EXP-15 research registry is unrelated. No
asset, model, permission, package, version, signer or main/RC change is proposed.

## Next step and rollback

Cinema integrator: review and integrate #724, then rerun the actual Android
regression gate on the combined source. The full Gradle build did not complete
on this support host; the scoped proof does not replace that gate. Physical
Fold7 acceptance and the separate null-accent renderer finding remain open.
Revert this support branch's own commits to remove the change.

Normal project reproduction after an available Android build:

```bash
./gradlew -PcontinuumUiWidgetTests=true :app:testDebugUnitTest \
  --tests at.heinz.deviceai.JarvisCinemaReplayUiTest \
  --tests at.heinz.deviceai.ui.JarvisCinemaEntranceTest \
  --tests at.heinz.deviceai.ui.WindowProjectedMotionSamplerTest \
  --tests at.heinz.deviceai.CinemaStopReturnUiTest
```

The actual support run compiled the View and these test sources with `kotlinc`
against the frozen app dependency JAR plus the project's exported test classpath,
using `-jvm-target 17 -module-name DeviceAI_app_debug` and `-Xfriend-paths` for
that app JAR. JUnitCore ran the four named suites with `robolectric.offline=true`
and the existing API28/API35 SDK JAR directory. The new compiled classes precede
the frozen app JAR on the classpath. `baseline.json` pins all dependency JARs;
`green.json` pins each compiled source and output-class directory. Android
resources were copied into the isolated dependency snapshot, and its resource
config points only to that snapshot. No active Cinema build directory was written.
