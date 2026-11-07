# Code47 APK continuation

Status: WIP / NOT_RELEASED. Owner requests continuation and the original-signed
APK. Fresh GitHub and publisher readback confirms PR587 source
7b9c03804301e00ab9801708eef8a2b50ed3e5f2 on both the prior release branch and
the original publisher branch. No main merge is authorized.

## Recovered result

Original publisher deployment dep-dbn4sbrfm27c73dbo9n0 failed on 06 November.
Its real Android run completed 3718 tests: 3 failed, 20 skipped, 3695 passed.
All failures are in OwnerAppActionWorkSceneUiTest: foreground return,
STOP, and the visible work window. Failure lines 187/176/155 wait for a
backend while the confirmation callback remains on the paused main looper.
Signing and publication correctly remained blocked. Code46 stays live.

The previous uncommitted follow-up was initially LOCAL_ONLY / NOT_PUSHED.
Its exact recovered diff is now `docs/evidence/code47-apk-20261107/
recovered-workscene-wip.patch`, relative to PR587 source 7b9c0380.
The preceding actual focused run (`app-clock-green`) failed return-to-chat
and the missing-permission case: 4 tests, 2 failures. Exact compile/JVM
commands and test-source hashes are preserved in recovered-wip-receipt.json;
raw output is recovered-wip-red.log. This historical patch is WIP/UNVERIFIED
and superseded by verified repair 7732b8668f912419d7aac7a1609a97097d727cbf.
Production is unchanged from PR587.

## Bounded next steps

1. Repair actual host event delivery and controlled backend lifetime while
   retaining approval, STOP, progress, gaze and return assertions.
2. Run the complete affected class, then freeze and push the source.
3. Run the original publisher's full mandatory Node/backend/Android/UI/lint
   and unsigned assembly gates before original signing.
4. Independently verify served APK bytes, package, code47, certificate,
   source identity, ARM64 and unchanged original avatar/orb assets.

EXP-02/07: regression repair and actual source-bound validation. Existing
EXP-05/12 interfaces remain unchanged. No new service, paid API, key export,
package change, user-data deletion, test waiver or device installation.
Physical Fold7/GPU/local-model/audio/thermal acceptance: NOT_RUN.

Resume from this branch's actual remote HEAD and recorded receipts. Rollback
is retaining Code46 and reverting only this continuation's own changes.

## Test repair checkpoint

OwnerAppActionWorkSceneUiTest now pumps the queued actual confirmation
callback until stable device-port entry, keeps the simulated backend alive
while native Canvas renders, and delivers frames across animated close.
All four cases pass individually and together in normal JUnit ordering.
Production, original avatar/orb and mandatory release gates are unchanged.
Raw compile/tests/receipts: docs/evidence/code47-apk-20261107/.
The original publisher must still pass its complete exact-source gate.

## Frozen build and independent reproduction

Original publisher build dep-dbn8vhpsrm7s738np9jg runs source
7732b8668f912419d7aac7a1609a97097d727cbf. This documentation follow-up
does not change that source or restart the publisher.

The completed tests used the actual SDK-compiled production classes and
Robolectric, with commands recorded in methods-receipt.json and
class-receipt.json. For a clean SDK/Gradle checkout of the frozen source,
the equivalent complete-class integration command is:

```sh
gradle --no-daemon --max-workers=2 --console=plain \
  -Dorg.gradle.jvmargs='-Xmx1600m -XX:MaxMetaspaceSize=512m' \
  -Pkotlin.compiler.execution.strategy=in-process \
  -PdeviceAiMentorPreview=true -PcontinuumUiWidgetTests=true \
  -PcontinuumLocalStandaloneQa=true -PcontinuumStandaloneVersionCode=47 \
  :app:testDebugUnitTest --tests at.heinz.deviceai.OwnerAppActionWorkSceneUiTest
```

This command is a reproduction instruction, not a claim of an additional
completed Gradle run. The publisher independently runs the entire suite
without a class filter, followed by lint and original-package assembly.
