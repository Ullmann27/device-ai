# Thermal inference audit — 11 November 2026

Scope: owner-requested heat investigation only, EXP-05 / existing local-model load path.
Coordinator: PR #773, `fix/thermal-root-20261111`; intended integration target
`integrate/omega-apk54-20261111` (PR #769). Baseline:
`f88d7d1936b9c0d53c176a0af31108fb863199e6`.

## Proven redundant work

`LiteRtLmLocalModelRuntime.load()` previously called `catalog.get(id, version)`
and then `catalog.modelFile(model)`. Both calls perform a complete SHA-256 scan:
`modelFile(record)` calls `get(record.id, record.version)` again. This happens
before native inspection and engine initialization, once per model load, not
once per already-loaded inference.

The production lookup sequence was first extracted unchanged into synchronized
`LocalModelCatalog.sourceForLoad`. A JVM SHA-256 provider observer delegates every
digest to the original provider, records real bytes hashed on the test thread,
and removes itself afterward. With a synthetic 262,144-byte file, the unchanged
load sequence produces `[262144, 262144]`; two loads produce four complete
digests. The two count assertions fail while the three integrity/identity tests
pass. RED checkpoint: `e0ef48967`.

The fix returns the file path already validated by `get()` under the same
catalog monitor, without calling the verifying public `modelFile(record)` a
second time. Every new load still hashes all bytes and checks metadata identity
and length. No cross-load verification cache is introduced. Same-size content
tampering, truncation, missing files and invalid identities remain rejected.
The native capability check and all model/backend/thread/token settings remain
unchanged.

For a model of N bytes, the load path hashes N fewer bytes. For a
3,659,530,240-byte file this removes one 3,659,530,240-byte scan. That is a
deterministic work count, not a measured temperature, energy or latency change.

## Verification actually executed

- RED production-path extraction: Kotlin 2.4.0 / JDK21, **5 tests, 3 PASS / 2 FAIL**.
- GREEN patched production sources: **35 tests, 35 PASS / 0 FAIL / 0 SKIP**:
  `LocalModelLoadSourceTest`, `LocalModelCatalogTest`,
  `LocalModelImportStreamTest`, `LocalModelReloadLifecycleTest`,
  `LocalModelThermalGuardTest`, `LocalModelGenerationPolicyTest`,
  `LocalModelComputeBudgetTest`.
- Both changed production files compile against the actual published
  LiteRT-LM 0.18.0 classes and cached real app dependencies. This is a focused
  JVM compile/test, not a complete Android build or native model execution.
- Reproducible source hashes, selected classes, compile logs and JUnit logs:
  `docs/evidence/thermal-inference-20261111/`.

Regular Gradle reproduction uses the same seven class filters with
`:app:testDebugUnitTest` and a JDK21 test launcher. The direct host runner used
for this evidence is retained alongside the logs; its cache locations describe
the current executor and are not project requirements.

## Other inspected paths and limits

- The read code owns one process-wide `LocalModelHost`. Runtime `READY`
  admission serializes generation starts. No simultaneous duplicate native
  engine or infinite Kotlin busy-spin was proven in this audit.
- Native chat history is intentionally fresh for full prompts; only the first
  pristine load-time conversation is reused. Reusing used conversations would
  change prompt/context isolation and was not attempted.
- `LocalModelReasoningAdapter` waits with sleep/latch; its polling is not a
  proven source of sustained CPU saturation.
- The historic comment in `LocalModelComputeBudget` attributes heat to CPU
  cores without current device measurements. This audit does not adopt that
  attribution as proof.
- Current and future model responses retain the same weights, output budget,
  CPU thread policy, tools and safety gates. Thermal protection stays intact.

Native Gemma load/inference, Fold7 thermals, battery readings, CPU energy,
frame timing, full Android integration, APK signing and installation:
**NOT_RUN**. The demonstrated redundant load work alone does not establish the
cause of heat during a later already-loaded query. Root continues the actual
query/renderer/background audit and owns final combined acceptance.

Rollback: revert the source resolver and runtime call-site together. Keep
the regression and audit evidence; do not replace integrity checking with a
cached size/mtime assumption.
