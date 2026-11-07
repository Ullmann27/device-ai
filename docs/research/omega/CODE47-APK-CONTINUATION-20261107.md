# Code47 APK continuation

Status: RELEASED / SERVED_APK_VERIFIED, 2026-11-07 03:27 UTC.
Original-signed Code47 / 0.3.46 is live from source
7732b8668f912419d7aac7a1609a97097d727cbf. The complete original publisher
QA and independent served-binary verification passed. PR588 retains the
continuation and evidence; main is unmerged.

## Recovered result

Original publisher deployment dep-dbn4sbrfm27c73dbo9n0 failed on 06 November.
Its real Android run completed 3718 tests: 3 failed, 20 skipped, 3695 passed.
All failures are in OwnerAppActionWorkSceneUiTest: foreground return,
STOP, and the visible work window. Failure lines 187/176/155 wait for a
backend while the confirmation callback remains on the paused main looper.
Signing and publication correctly remained blocked for that failed run.
Code46 remained live until the verified Code47 deployment below.

The previous uncommitted follow-up was initially LOCAL_ONLY / NOT_PUSHED.
Its exact recovered diff is now `docs/evidence/code47-apk-20261107/
recovered-workscene-wip.patch`, relative to PR587 source 7b9c0380.
The preceding actual focused run (`app-clock-green`) failed return-to-chat
and the missing-permission case: 4 tests, 2 failures. Exact compile/JVM
commands and test-source hashes are preserved in recovered-wip-receipt.json;
raw output is recovered-wip-red.log. This historical patch is WIP/UNVERIFIED
and superseded by verified repair 7732b8668f912419d7aac7a1609a97097d727cbf.
Production is unchanged from PR587.

## Completed build sequence

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

Resume from PR588 actual remote HEAD and recorded receipts. The publisher
branch remains pinned to the built source 7732b8668; do not advance it for
documentation-only commits. Any future repair needs a new exact-source
QA/signing cycle and a version code greater than 47. Do not attempt an
Android downgrade to Code46 over an installed Code47.

## Test repair checkpoint

OwnerAppActionWorkSceneUiTest now pumps the queued actual confirmation
callback until stable device-port entry, keeps the simulated backend alive
while native Canvas renders, and delivers frames across animated close.
All four cases pass individually and together in normal JUnit ordering.
Production, original avatar/orb and mandatory release gates are unchanged.
Raw compile/tests/receipts: docs/evidence/code47-apk-20261107/.
The original publisher passed its complete exact-source gate at 03:25:04 UTC.

## Frozen build and independent reproduction

Original publisher build dep-dbn8vhpsrm7s738np9jg completed from source
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

## Final release receipt

Original deployment `dep-dbn8vhpsrm7s738np9jg` became LIVE on
2026-11-07 at 03:26:55 UTC. Independent served-binary verification passed
at 03:27:24 UTC. Full publisher QA, release lint, original signing, catalog
publication and actual APK hash/package/version/certificate checks passed.

- APK: [0.3.46 / Code47](https://continuum-owner-v23-release-preflight.onrender.com/continuum-local/Continuum-Lokal-35f93b41ed835bf2ec16890b62d4e640d3b1ea28c2e8a098065bd08f64dc2481.apk)
- Source: `7732b8668f912419d7aac7a1609a97097d727cbf`
- Package: `at.heinz.deviceai.continuumlocal`
- Size: `101925655` bytes
- APK SHA-256: `35f93b41ed835bf2ec16890b62d4e640d3b1ea28c2e8a098065bd08f64dc2481`
- Original certificate SHA-256: `6354e91dc0ca8df9173885f32402fd817f46e798f369206d4fd710ff3d03ee23`
- ABI: `arm64-v8a`; original avatar GLB byte identity verified.

Evidence: `docs/evidence/code47-apk-20261107/publisher-pass.json` and
`docs/evidence/code47-apk-20261107/served-apk/`.
Physical Fold7/GPU/local-model/audio/thermal acceptance remains NOT_RUN.
