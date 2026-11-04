# Combined memory / avatar review source

## Current completed source gates

Public tested source: fab5f094862c5abae259196f6be1d7a8bc80a75b (#499), tree
3183aeeeae3532e7f3253de976eabd46efcb940a. The later checkpoint adds documentation
and receipts only; application and test sources remain byte-identical.

| Gate | Actual result |
| --- | --- |
| Full standard Android unit task | 2,489total;2,470passed;0failures/errors;19existing skips |
| Configured NewApi-only lint | 0issues; BUILD SUCCESSFUL |
| Actual Activity/Handler integration | 2/2passed;0failures/errors/skips |

The Activity gate constructs the real Activity, calls its real compiler getter
and memory subscription, executes the actual Android main looper and verifies
real orb effects plus queued-work suppression/re-subscription. It deliberately
does not call full application onCreate/onDestroy or a complete model/chat turn.

Current raw receipts: docs/evidence/memory-brain-20261104/android-combined and
activity-android. Earlier lock-only and failed checkpoints below are historical.
No native GPU/physical Fold7/audio/full-pane success or final owner APK is
claimed. The other workstreams' remaining readiness and original-signing gate
still precede the requested final APK. No combined APK has been built.

## Frozen public inputs

- Avatar and reviewed recovery: #491, e10f4785284ab08f1d5d0a77b64ab8631ed5b9cb,
  exact tree8bbb63663edaa44c85b6645e4151b5c2a3d2eb46. The corresponding local
  8bb6d8b0 history was merged without conflicts. Its later tests/evidence change
  no production file from the already selected f14ce8e9 application source.
- Memory: #494,7e2d69a53f42d22c890e56ab5921295ff408ecd5. The integration includes
  all corresponding source changes and original failure/holdout/review evidence.
- Weight research: #492 remains isolated and does not change the selected
  runtime weights. Its experimental larger checkpoint is not promoted here.

This branch adds only two app-specific hooks: initialize the existing Activity
compiler lazily and attach ContinuumMemoryRecallPublisher(filesDir); delegate
MemoryEventSources.forApp(File) to the lifecycle-safe existing-hub adapter.
Preserve mapper.detach() before subscription.close(). No new memory database,
orchestrator, network model, permission, application ID or signer is introduced.

## Evidence and current limits

Memory component runs passed98 tests including the unchanged25-case independent
holdout; the later identity-projection fix passed47 relevant tests. Compiler to
actual orb rendering values passed9/9; the app factory path passed14/14. These
runs overlap and must not be added together. The first complete memory Android
suite compiled successfully and ran2442 tests with one existing test shutdown
race and19skips. Its corrected bounded-thread-join test passed a fixed100-case
repeat run; the original failed full run is preserved.

The avatar owner separately reports2416 standard tests,0failures and19skips,
NewApi lint0 and assembleDebug success on its own f14 source. These are parent
results, not this combined branch's acceptance. Native pane/full Activity and
Fold7/Filament remain open in that owner's handoff.

Two actual Activity/Handler tests are committed in MemoryRecallActivityUiTest.
They do not invoke the full application onCreate/onDestroy and do not prove
microphone/TTS, native rendering, physical performance or a full dialogue.
Their runtime status is NOT_RUN until a matching execution receipt exists.

The final complete combined Gradle/lint attempts have so far acquired no shared
build slot. They are NOT_RUN_SHARED_BUILD_LOCK_BUSY, not passing or failing
Gradle tasks. No foreign process was stopped and no competing heavy build
started. The SDK's adb devices list is empty. No combined APK has been built;
that step remains after the requested combined readiness gate.

## Reproduce next, under the existing shared build lock

```sh
./gradlew --offline --no-daemon --max-workers=1 --console=plain -Pkotlin.compiler.execution.strategy=in-process :app:testDebugUnitTest :app:lintDebug
./gradlew --offline --no-daemon --max-workers=1 --console=plain -Pkotlin.compiler.execution.strategy=in-process -PcontinuumUiWidgetTests=true :app:testDebugUnitTest --tests at.heinz.deviceai.MemoryRecallActivityUiTest
```

Use the configured JDK17/SDK36 and the common gradle-build.lock. The second command
is a targeted Activity gate with actual Android Handler dispatch, not the full
native visual/device gate. Source identity is in docs/evidence/memory-brain-20261104/combined-source.

Keep this as a draft review branch; no main merge, original-owner signing,
release or installation is claimed. Revert only these integration hooks in this
isolated branch if needed. Do not roll back a recovered ledger to a reader that
ignores its consent metadata. Preserve all original source branches and receipts.
