# Provisional memory → Activity integration

## Exact inputs and conflict report

This isolated integration uses committed Git objects only:

- Avatar/recovery input: `f14ce8e9d6a3864117a9cea3d325024ab4ff8d9b`.
- Published memory input: `8a98ea4d10126041b1a43cb7623c0f324ab18ec1`.
- Common ancestor: `578bdfd12f896cc990bf1a043bc1b248ba3adb6d`.
- Worktree: `/workspace/scratch/624aeb26d481/agent-memory-avatar-integration`.
- Branch: `integration/continuum-memory-avatar-hooks-20261104`.

The merge was automatic and conflict-free. No manual conflict resolution, foreign working-tree copying or existing-branch edits were needed. `AGENTS.md` imports the memory parent's explicit owner authorization for public collaboration; it supersedes the historical private-only text. This integration itself is **LOCAL_ONLY / NOT_PUSHED** as assigned by the integration owner.

At initial inspection, the clean foreign integration worktree had advanced to `19185dcc7f3d55527516c1a08f2f205a7f3ec293`. Relative to the explicitly selected `f14ce8e9` input, that adds `BootstrapRecoveryCallerUiTest.kt`. Those later commits were not silently adopted. Final acceptance must use the other conversations' eventual frozen heads.

## Minimal hooks

Two production files receive integration-specific changes:

1. `cockpit/MemoryEventSource.kt`: `MemoryEventSources.forApp(File)` delegates to `ContinuumMemoryEventSource.forApp`. The legacy nullable `production` property remains unchanged. The Activity-facing factory now uses the adapter's metadata screening and subscription-close behavior.
2. `AgentActivity.kt`: the existing `contextCompiler` becomes a lazy property and applies `withRecallObserver(ContinuumMemoryRecallPublisher(filesDir))`. The existing policy remains `maxBytes=10_500`, `memoryLimit=8`, `researchSourceLimit=4`, `nativeToolCatalog=true`. `filesDir` is used inside the deferred initializer, not in the Activity constructor.

The existing subscription already used `MemoryEventSources.forApp(filesDir)`. Its `mapper.detach(); subscription.close()` teardown and the Activity's closing/clearing of `cockpitSubscriptions` are preserved. The observer publishes measurements of admitted recall through the existing app-directory hub; it adds no learning authority, storage format or model promotion.

Relevant experiment contracts: EXP-06 (memory provenance), EXP-07 (regressions), EXP-12 (lifecycle/identity continuity).

## Executed tests and evidence boundaries

Evidence directory: `docs/evidence/memory-brain-20261104/activity-integration/`.

| Check | RED | GREEN |
| --- | --- | --- |
| Real-hub app-factory regressions | Compilation passed; 2 tests, 2 expected failures | Included in 14/14 passing JVM factory/mapper/adapter tests |
| Activity/factory source contracts | 3 checks, 2 expected failures | 3/3 passed |

The real-hub failures showed that the old inline factory forwarded raw-text metadata and allowed a previously captured hub callback after subscription close. The final JVM suite also exercises the existing mapper and guarded adapter. Two logged `IllegalStateException` warnings are deliberate hostile-sink negative-test inputs; the event hub continues delivering to other subscribers.

The Activity checks verify source wiring only: deferred publisher creation with the unchanged policy, use of the actual compiler path, app-directory subscription, and detach-before-close teardown. They are **not Android runtime or lifecycle execution**. No full Gradle build, Android Activity execution, device test, APK, signing, installation or publication was performed by this integration task.

`hook-source-manifest.json` records both input commits, the absence of merge conflicts, tested source hashes and exact verification categories. The focused JVM receipts include the compiler invocation and frozen real baseline class-jar identity. These small JVM tests use the existing integration harness; they do not substitute for the owner's combined Kotlin/Android gate.

Reproduce the source contracts:

```sh
node --test --test-reporter=tap tools/memory-brain-review/activity-memory-wiring.test.mjs
```

Focused JVM classes for the combined gate:

```text
at.heinz.deviceai.cockpit.MemoryEventFactoryIntegrationTest
at.heinz.deviceai.cockpit.MemoryEventSourceTest
at.heinz.deviceai.cockpit.ContinuumMemoryEventSourceIntegrationTest
```

The hook merge is `7fa0b55119c917e6394a67d40a631fcc43ebd8ff`, with the exact avatar/recovery and published-memory inputs above as its two parents. The integration owner's requested follow-up `bfc427c6d76f08bbe56513c330342be3be997900` was then merged automatically without conflicts. It supplies the shared identity resolver before compiler status projection and cross-module compiler-to-orb tests; those production files match that committed input exactly.

`integration-ready-source-manifest.json` records the final merge's two parents, all relevant source hashes and the absence of conflicts. Post-merge source contracts again passed 3/3, and every source compiled by the 14-test focused JVM receipt remains byte-identical. The newly imported compiler/orb tests are not claimed as executed on this combined tree; the integration owner runs that gate.

The provisional combined commit was `12e66264fa5fb5b4cc7cdb7100206ad9af68a3d2`. At the integration owner's subsequent request, committed memory head `1342e5076b38a7a72cd18a394d12586829e9b54b` was merged into it without conflicts. Relative to `bfc427c6`, this adds five lines to the existing calculation-dispatcher shutdown test plus its reproduction/failed-full-run evidence; there are no production-source changes. All previously tested hook, factory and source-contract bytes remain identical. The final manifest preserves both merge stages. The imported earlier full-Gradle failure is historical evidence from the memory parent, not a combined-tree gate result; the owner runs the combined gate on the new returned HEAD.

## Next gate and rollback

Run the owner's shared-lock Kotlin/Android gate on the exact returned integration commit. The final source must also be reconciled with the avatar/recovery conversations' frozen heads. Parent test results and this provisional integration do not establish a released or device-accepted application. Raw console receipts preserve their original whitespace; whitespace checking of source and handoff files passes.

The integration-specific rollback is removal of the lazy recall observer and restoration of the previous `MemoryEventSources.forApp` body; the two imported source parents remain independently available. No persisted data migration is involved. No `main` merge or branch deletion is authorized by this package.
