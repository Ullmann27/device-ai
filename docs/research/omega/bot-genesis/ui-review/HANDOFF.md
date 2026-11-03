# Bot Genesis independent UI review

**407 roster repair independently HOST_TESTED:15/15 PASS,0 FAIL,0 SKIP.** Actual Android production/test compile PASS, Gradle exit0. Exact tested baseline4b881f81ddfbe38729a551d9e460e818c6b3b715, production equivalentc7ec853e2ffaa488b92826306f015bfaa67f5bb7. Reviewer test freeze7bb1cfd5cf3beab9d5a1caf0d84180c815172bd0; all source/test hashes identical before/after. Review-only branch codex/genesis-ui-review-20261103. PR437 merged into Chief Genesis at38427adc; subsequent tests/evidence continue in draftPR446. No production edits.

## What changed and what was actually proved

The original c502 source compiled but16 opens queued16 jobs;13 widget checks gave12PASS/1FAIL. That exact original failure, testsource and handoff remain retained via prior reviewer merge. Root407 reuses one Activity roster controller and admits only one storage operation. The identical16-open/bound1 counterexample now passes. Two earlier tests expected multiple admitted reads; their acceptance intent was preserved with the explicitly documented busy/retry semantics, before this candidate run. An old callback must not fill the new window; explicit retry after completion loads fresh data; an accepted save remains bound to its selected stable identity. Two additional tests prove close-during-save preserves that write without reviving the window, and show-after-Activity-destroy admits zero storage work.

All actual store/UI tests pass:20 initial profiles, pagination, profile21, rename/private-note continuity across new store/dialog instances, asynchronous IO, stale/dismiss/destroy handling, corruption fail-closed, optimistic write conflict, rejected executor,320dp/fontScale2 scrolling and accessible working-note label. These are actual Robolectric widgets and filesystem operations, not Fold7/process-death/voice/GPU or cognitive-quality evidence. Full combined unfiltered Android/Node/lint/parity is a separate final-source gate.

## Evidence and reproduction

`407-resumed/{before.json,after.json,gradle.log,gradle.exit,junit.xml,summary.json}` contains exact source/test hashes, actual raw Gradle/JUnit and results. Source checkout status was clean. Elapsed Gradle50s; testtime11.052s, not app-latency benchmarks.

```
./tools/synapse-ui-review/run-scoped.sh 4b881f81ddfbe38729a551d9e460e818c6b3b715 /absolute/frozen/candidate /absolute/fresh/evidence-directory
```

The helper uses the already provisioned /workspace/continuum-build-env toolchain. Its init script locates its own reviewtest directory; no historical fixed reviewer worktree path is required. It adds tests without excluding production sources. Scoped flag synapseUiReviewOnly is for this narrow run only; omit for final full QA. No CLI --tests is used because the existing QA task allowlist evaluates those values as task names. Release guards are unchanged.

## Next independent package

TEAM-E-FROZEN-CONTRACT.md froze ten connection requirements before future bot-selection implementation inspection: actual registry IDs and model-connected states, one shared model host, private context canaries, late callback identity/model revision, one TTS path, existing AvatarDirector, no invented activity, unchanged permission/lifecycle/budget guarantees. Await Chief's exact source/API contract before implementing those tests. Do not count this roster proof as a model-connected bot or autonomous task success.

CPU window was explicitly released after this completed Gradle run so the native model builder can proceed. No model build/inference ran in parallel with this reviewer's build. No signing, installation, release, Main merge, owner data or production weights changed. Fold7/audio/camera/GPU/energy remain NOT_RUN; quotaNOT_VISIBLE.

## Actual speech interruption baseline — RED retained

On the same exact4b881f81 source, independently frozen real-Activity listener tests executed: **3 total,2 PASS,1 FAIL,0 SKIP**. Compilation PASS; Gradle exit1 after45s; test time2.195s. The failure is the predicted race: `startInterruptedAfterInitialIdentityCheckCannotReviveAvatarOrReplayOnAttach` reports `Interrupted speech must not emit a new start after stop`. Holding the real audio-signal monitor let onStart pass its initial ID check, then stop completed before the callback emitted a new AvatarDirector.SpeechStarted. Ordinary sequential stop and stale A completion while B speaks passed.

Raw evidence: `speech-baseline-4b/{before.json,after.json,gradle.log,gradle.exit,junit.xml,summary.json}`. Before/after hashes match, candidate clean. These tests install the actual Activity listener in Robolectric's Android TTS shadow without onCreate/model/audio hardware. They prove a software callback defect, not audible-device behavior. Chief's proposed UI-thread serialization remains unverified until the corrected combined candidate executes the same assertions.

Reproduction, from the exact source checkout with provisioned toolchain:

```sh
source /workspace/continuum-build-env/env.sh
gradle --no-daemon --max-workers=2 --console=plain \
  -I /workspace/continuum-build-env/test-jvm21.init.gradle \
  -I /workspace/genesis-ui-review/tools/genesis-ui-review/connection.init.gradle \
  -Dorg.gradle.jvmargs='-Xmx2g -XX:MaxMetaspaceSize=768m' \
  -PdeviceAiMentorPreview=true -PcontinuumLocalStandaloneQa=true \
  -PcontinuumStandaloneVersionCode=40 -PcontinuumUiWidgetTests=true \
  -PgenesisSpeechBaselineOnly=true -Pkotlin.compiler.execution.strategy=in-process \
  :app:testDebugUnitTest
```

Reviewer source freezee9492e26 includes the previously frozen speech3, selection8 and router3. Selection/router11 remain NOT_RUN while the Chief obtains the accepted conversation API. `connection.init.gradle` with `-PgenesisUiReviewOnly=true` will run29 scoped tests (15 roster +3 speech +8 selection +3 router); omit both filters for final complete QA. Baseline mode deliberately does not add later selection source because its constructor API did not exist in4b. No production or existing tests are excluded. CPU window released again after baseline completed.
