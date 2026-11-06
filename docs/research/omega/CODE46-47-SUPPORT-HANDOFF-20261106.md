# Code46 / Code47 support team — 2026-11-06

Status: ACTIVE / INDEPENDENT_VERIFICATION. This is a support handoff, not a release receipt.

## Scope and ownership

The owner requested exactly three additional helpers for the Code46 repair and APK build, then continued implementation, improvement, repair and APK building on Code47 after Code46 finishes. The existing integration work in PR #575 and PR #576 remains authoritative. This support branch is based on Code47 `6bd0e172b5615854e021e43aa0ed04a1978eb733`; it does not move either active integration branch or the publisher.

Three helper domains: source/root-cause review; original publisher/build diagnostics; independent regression and release identity verification. No nested agent teams. One heavy local compiler at a time.

## Verified Code46 state

- Corrected release source: `577f0931e7937ffcac02dc855a540f093facdb3b`, code46 / 0.3.45.
- The existing local sequential Android run ended with `BUILD SUCCESSFUL in 35m 21s`. Independent support recount of its 626 JUnit XML reports: 3559 total, 0 failures, 0 errors, 20 skipped, therefore 3539 executed passes. This is readback of that existing source-bound run, not an additional physical-device execution.
- Known five RectF test-harness failures had already been corrected in the primary integration. Support has not duplicated or claimed authorship of that repair.
- Original publisher `srv-dbf1tr2vcj2c73ehpfog`, deploy `dep-dbn1htjncjis73c9vlng`, source `577f0931e7937ffcac02dc855a540f093facdb3b`: observed `build_in_progress` at 2026-11-06T18:29:38Z.
- Last observed gate log: `QA_GREEN backend-lockfile-check-and-tests` at 18:17:53Z. The existing script redirects full Android output to its build-local evidence file. No intervening console output is not proof of a hung build.
- Complete publisher QA, original signing, hosting and independent HTTPS/APK readback remain PENDING here. No duplicate deploy was started.

## Concrete Code47 verification

M0/M1 were already implemented by the primary Code47 team. Support independently materialized and Git-blob-verified source `f26d1063b25c65c22b8d411789d1733c92e3ad72`. The GitHub comparison to `6bd0e172b5615854e021e43aa0ed04a1978eb733` changes two documentation files only; production and test sources are identical.

A new real SDK/Gradle run is ACTIVE on exact source f26d1063b25c65c22b8d411789d1733c92e3ad72, with an isolated checkout, copied Gradle cache, existing JDK17/Android SDK, offline mode and one worker:

```sh
gradle --offline --no-daemon --max-workers=1 --console=plain \
  -Dorg.gradle.jvmargs='-Xmx1600m -XX:MaxMetaspaceSize=512m' \
  -Pkotlin.compiler.execution.strategy=in-process \
  -PdeviceAiMentorPreview=true -PcontinuumUiWidgetTests=true \
  -PcontinuumLocalStandaloneQa=true -PcontinuumStandaloneVersionCode=47 \
  :app:compileDebugKotlin :app:testDebugUnitTest :app:lintDebug
```

This run reached `:app:compileDebugKotlin`. Result: RUNNING, not PASS. The initial filtered invocation was rejected during configuration because its `--tests` arguments did not satisfy the existing strict task-name guard. No product compilation or assertion was executed in that attempt. The guard is unchanged; the retry uses the existing regular full-QA tasks.

## Completion and continuation

1. Observe the current original Code46 build; inspect any new failure before deciding on a correction or retry.
2. Require actual original-package/signature/version/hash/hosting verification before calling Code46 delivered.
3. Record the Code47 SDK run's actual exit code, exact source, XML totals and lint scope; repair only a reproduced remaining defect in a separate support change.
4. After Code46 completion, continue the current Code47 integration plan, coordinate existing M1 work and keep M2/M3 ownership explicit. Do not invent a second journal, model-prompt pipeline, frame clock or renderer.
5. Before a final Code47 APK, rerun the full gates on its final exact candidate and preserve the original update identity.

Package: `at.heinz.deviceai.continuumlocal`. Original certificate:
`6354e91dc0ca8df9173885f32402fd817f46e798f369206d4fd710ff3d03ee23`.

Main, original signing keys, active foreign worktrees, user data, original head/orb and offline policy remain protected. No paid services or GitHub Actions. No Fold7, actual LiteRT, audio or GPU acceptance is claimed. No APK has been created by this support round yet.
