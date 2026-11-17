# Local inference audit — 03.11.2026

Branch: `repair/continuum-shizuku-composer-20261103`. Experiments: EXP-05
(existing LiteRT-LM model runtime), EXP-12 (failure recovery and retained ownership).
The root repair agent owns commits, pushes and the final combined verification.
This audit does not change model weights, tools, owner authorization or APK signing.

## Fixed source defect

`LiteRtLmLocalModelRuntime` can enter `FAILED` after releasing a conversation
fails, while its engine and loaded identity remain owned. Previously `load()`
accepted that state and could replace the old engine with another allocation.
The runtime now returns `LOCAL_MODEL_UNLOAD_REQUIRED` through its normal result
callback, outside the lifecycle lock, until an explicit unload succeeds.

`closeResources()` previously discarded all resource references before native
cleanup. A thrown close could skip the engine close and lose its only owned
reference. References now remain owned until the complete teardown succeeds;
failure remains `FAILED`, with the explicit unload path available for retry.
No unload success is synthesized for an active or hung native generation.

`ContinuumUiCopy.modelFailure()` explains this specific failure in German and
instructs the owner to unload through Modellverwaltung before retrying a load.

## Executed verification

- `LocalModelReloadLifecycleTest`: the real runtime's failed-state admission
  regression failed before the fix (missing-catalog error instead of unload
  refusal), then passed. The fixture seeds retained identity without initializing
  an engine, loading weights or claiming native inference.
- All localmodel JUnit test classes: **61 tests passed** on the actual published
  LiteRT-LM classes, JDK21, with the patched runtime compiled by Kotlin2.4 targeting
  JVM17. The current test source was recompiled before this run.
- `ContinuumUiCopyTest`: new unload guidance assertion failed before the mapping;
  **7 tests passed** after compiling that source. The final output-limit addition
  brings this class to **8 passing tests**.
- `AgentModelOutputBoundaryTest`: exact8,000-character output remains complete;
  8,001 characters with a proposal now fail with the stable
  `LOCAL_MODEL_RESPONSE_EXCEEDS_AGENT_LIMIT` code. An actual orchestrator fixture
  confirms that the otherwise valid read-only proposal does not execute. The
  two missing-code assertions and UI mapping assertion failed before the change;
  the final boundary/UI run passed **11 tests**. The8,000-character invariant
  remains unchanged; oversized output is rejected, never silently truncated.
- `git diff --check` passed for the four changed source/test files.

The official LiteRT-LM0.18 host classes use Java21 bytecode. The new lifecycle
test explicitly skips on an older host JVM; Android compilation remains JVM17.
A skip on JDK17 is not the lifecycle verification above. Reproduce with the
actual JDK21 unit-test launcher and these Gradle test filters:

```text
:app:testDebugUnitTest
  --tests 'at.heinz.deviceai.localmodel.*'
  --tests 'at.heinz.deviceai.ContinuumUiCopyTest'
```

The root agent performs the authoritative combined Gradle/widget/lint checks.
Standalone audit logs reside in `/workspace/repair-evidence/` during this session
(`local-model-reload-red-jdk21.log`, `local-model-all-green-jdk21.log`,
`uicopy-unload-red.log`, `uicopy-unload-green.log`). They are host evidence only.

## Integration and remaining gates

The root agent owns the `AgentActivity` fix for greeting/capability requests that
previously always returned a bootstrap answer even with a loaded READY model.
Storage questions must retain the existing fresh `get_storage_info` probe.
Native READY confirms engine initialization, not a generated answer.

`generateLocal` can capture up to20,000 characters, while `AgentModelOutput`
accepts only8,000. A completed response above8,000 now produces the explicit
budget-rejection code and German guidance to request a shorter answer or split
the task. This does not provide arbitrary-length answers or increase either cap.

No Fold7, real Gemma generation, native close failure, Shizuku lifecycle,
on-device timing or APK acceptance was executed here. If native cancellation
never supplies a terminal callback, the runtime still retains its active
generation and defers unload; a watchdog error alone does not prove native
cleanup. Obtain actual callback profiles and device evidence before changing
that ownership rule.

Rollback: revert the runtime admission/retention patch, its two lifecycle tests,
and the specific UI failure mapping/test together. Retain the source diagnosis
and device test gate; reverting reintroduces the failed-state resource risk.
