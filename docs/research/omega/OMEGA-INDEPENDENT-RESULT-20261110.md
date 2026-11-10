# OMEGA independent evaluation: first frozen candidate

**Decision: REJECT_PROMOTION.** The first candidate returns an explicitly rejected animation as its proposed reference. Keep the opt-in prototype disabled and preserve this negative result. A bounded later safety repair is exposed regression work, not a fresh independent holdout.

## Provenance and scope

- Baseline: `515afc28af11b9befeb5a61f9c44411d423635a9`, tree `735a663d522e16d56e979c78a908e9f3baa2f031`; remote tree-equivalent `28bee009ef54e8c60eb1468dc90c9f4a5a51edea`.
- Candidate frozen before inspection: `ad1f9ed6b3d293d47c4cbb01fae3a6d17e197658`, tree `2fec1752f7869b143abf7c23ffb7572d9addee72`.
- Independent scenarios frozen at `6671e2c5d7cf5f1cff70ab86693e66055a76fa22`, before prototype implementation was inspected. All 24 original cases are retained unchanged.
- Procedural separation between implementer and evaluator, on a shared filesystem; no claim of cryptographic blinding or natural population sampling.
- Four actual candidate Kotlin sources were recompiled against an immutable snapshot of the root's baseline SDK-compiled app classes. Baseline recompiles its three corresponding sources. No replacement implementation or behavioral stubs.
- Candidate receives trusted typed seed metadata. Baseline receives the same seed information serialized as an owner context turn because it lacks that API. This representation/ingestion difference is explicit; this is not an equal-prompt model-quality experiment.

## Context findings

Candidate matches the context-field expectations in **17/24** scenarios; seven fail. The baseline has no equivalent typed reference output, so those missing outputs are `null`/unavailable, not invented wrong model answers. Its aggregate context pass count must not be presented as a baseline intelligence score.

| Observable field check | Baseline | Candidate | Meaning |
| --- | ---: | ---: | --- |
| Expected goal available or correctly absent | 19/24 | 24/24 | Baseline checks raw context availability; candidate checks typed state |
| Expected constraint values available or correctly absent | 18/24 | 23/24 | Includes one explicit constraint-change case; not semantic model accuracy |
| Expected reference decision/state | Unavailable | 20/24 | Includes resolved, retained and intentionally absent reference states |
| Expected clarification behavior | 2 existing directives observed; 22 unavailable | 18/24 | Baseline model-generated clarification is NOT_RUN |
| Long-detour goal and three original constraints | Lost after raw four-turn window | Retained | Same semantic seed and six subsequent owner utterances |
| Central instruction with two source-bound candidates | No typed output | Correct other reference; goal/constraints retained | Trusted candidate seed required; no real animation edit |

The raw `context_status` is only the projected context slice. Some scenarios also contain approval, memory or result events which the slice cannot execute. Their `unsupported_events` and `receipt_memory_avatar_e2e=NOT_RUN` must be read with the context result. No complete eleven-stage success is established.

## Unfavorable cases retained

| Case | Observed behavior | Consequence |
| --- | --- | --- |
| H02 | Explicit correction mentioning both names asks for clarification | Conservative but misses a resolvable correction |
| H03 | `Bernstein bitte nicht.` resolves Bernstein | Explicit rejected-target plan; blocks promotion |
| H04 | Negative named reference plus relative alternative asks for clarification | Conservative unresolved contrast |
| H07 | Vague earlier preferred variant returns NoMatch | Required clarification not produced |
| H13 | Explicit new voice preference leaves the former voice constraint in the seed | Requires invalidation/clarification and trusted rebind, not silent reuse |
| H18 | `Kobalt statt Bernstein` asks for clarification | Conservative replacement not resolved |
| H22 | New-session reference returns NoMatch | No previous-session leakage, but missing clarification |

H03 cause: `rejectsNamed` recognizes only the exact trailing words `nicht` or `ist es nicht`; a polite filler before the negation bypasses that check. This is a context proposal error, not evidence of an executed unauthorized tool.

H13's frozen expected update remains unmet even if a later safety repair only blocks the stale contract. Such a repair may improve safety without passing the original broader natural-language requirement.

## Independent controls

Eight real existing web-approval controls pass: bare assent does not become a web request; same-turn network veto; stale callback; changed arguments; replay; OFF mode; cancellation; revoked-result authorization. Actions are synthetic counters under the actual gate. No network is used. The existing gate source is unchanged by the context prototype.

Eight candidate context/budget controls pass: active plan identity; complete frame or omission at 64/256/4096 bytes; request replacement; forged request; source revision; session replacement. These establish context validity checks, not tool authority or external receipt verification.

## Fixed host resource experiment

Ten warmup rounds and forty measured rounds over all 24 scenarios: 240 warmup case executions, 960 measured case executions, **1,160 incoming owner admissions**. Measured span is `inspectRequest` plus candidate `resolveWorkingTask`; setup, recording and model inference are excluded. Candidate reflection-call overhead is retained. One fixed run per arm, no best-of selection.

| Host metric | Baseline | Candidate |
| --- | ---: | ---: |
| Admission wall p50 | 0.011027 ms | 0.051556 ms |
| Admission wall p95 | 0.051716 ms | 0.269216 ms |
| Admission wall max | 0.775074 ms | 2.521700 ms |
| Total measured thread CPU | 19.693617 ms | 100.536648 ms |
| Total measured allocated bytes | 9,614,080 | 73,797,248 |
| Allocation p95 per admission | 15,320 bytes | 128,992 bytes |
| Additional local model calls | 0 | 0 |

The candidate adds compute and allocation. No speedup is claimed. Absolute host overhead is small in this fixture, but Fold7 latency, thermals and inference interactions are unmeasured.

Process RSS after the whole harness is 191,200 KiB baseline and 195,628 KiB candidate; used heap is 45,510,704 and 21,524,416 bytes respectively. These include JIT, harness, setup and different GC timing. The lower candidate ending heap is **not** a memory-efficiency result. Raw before/after process values are retained in the JSON.

## Reproduction

Use a full baseline SDK Kotlin compilation to supply immutable dependency classes; copy them before another build changes the directory. Set `KOTLINC`, `CONTINUUM_COMPILED_CLASSES` and `CONTINUUM_JSON_JAR`. Then:

```bash
bash scripts/research/run-omega-independent-holdout.sh BASELINE_REF CANDIDATE_REF /tmp/omega-independent
```

The baseline remote equivalent above can replace its local SHA. Use the published candidate tree-equivalent SHA when transported through the connector. The script exports exact source bytes from Git, compiles them and retains source/dependency hashes, toolchain, per-case results and fixed timing samples. Gate/budget control source is under `scripts/research/omega/`.

Evidence: `docs/evidence/omega-work-contract-20261110/independent-first/`. The receipt binds source, harness and unchanged holdout hashes. Full Android acceptance is owned by the integrator, not this scoped host evaluation.

**Local model inference, real tool execution, independently verified memory writes, live avatar/voice synchronization and physical Fold7: NOT_RUN.** The prototype remains default-off and has no production seed caller. It cannot currently claim the user's end-to-end animation-improvement request is completed.
