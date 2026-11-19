# Independent native failure-observation review

Freeze: 2026-11-03 22:32 UTC. Base source:
`d2ae042fb95298ccb849a4ba05c9410f74675006`. Reviewer:
`independence_mcp_challenger`, branch
`codex/independence-native-failure-review-20261103`.
Candidate implementation has not been inspected at freeze. Only the task
contract, proposed public API, existing source identity and the prior S01
failure report are inputs. Quota remains `NOT_VISIBLE`.

## Claim under review

An actual fixed local runtime failure may be reported as a negative result
without retaining a false live-process barrier, **only** after independently
checkable failure/closure evidence is carried through the existing provider,
coding orchestrator and canonical CCC1/service path. This changes neither
success admission nor model, source, tool, network or owner permissions.

The provider proposes `isTrustedLocalRuntimeFailureObservation` and the error
property `localRuntimeFailureObservation`. These names are test interfaces,
not evidence by themselves. Authenticity is a process-local brand on the
actual observation; a role string, JSON shape, HMAC string or copied property
must not grant it. HMAC binds the native launcher response to the current
request; the process-local brand additionally proves the trusted caller
observed the required CLI completion. Both are needed in the product path.

## Frozen invariants/counterexamples

| ID | Counterexample or positive control | Required outcome |
|---|---|---|
| N01 | Arbitrary object, matching shape, role string, getter/prototype injection | Never trusted; no property-access execution used as a substitute for admission |
| N02 | JSON parse/stringify clone, spread clone, structured clone, serialized error | Never recreates process-local trusted provenance |
| N03 | Wrong or absent HMAC, altered response envelope, extra success fields | Refuse trust; no successful inference/repair/experience |
| N04 | Replayed response for another request/execution ID | Reject current admission; never clear an unrelated execution barrier |
| N05 | Different task/task fingerprint/source commit | Reject current admission |
| N06 | Different model/runtime hash, manifest/pair identity or unapproved7B | Reject; no new approved artifacts or bypass flag |
| N07 | Failure phase or termination reason outside finite documented values | Reject or retain unknown/blocking state |
| N08 | Mutable admitted object or mutable nested fields | Mutation cannot change a trusted fact or retarget its task/bindings |
| N09 | Injected spawnSync/spawn, fake close emitter, fake process object | Cannot produce production trusted teardown evidence |
| N10 | Actual C fixture emits authenticated failure | CONTRACT_TEST only, never branded as approved native/model observation |
| N11 | Runtime nonzero exit through actual controlled launcher | Can be a negative closure observation only after real close and full bindings |
| N12 | Timeout or cancellation while child still alive | No early processClosed or released barrier; wait for actual close |
| N13 | Launcher spawn error, CLI killed, abrupt CLI loss, missing close | No claim that unknown descendants are torn down; retain safe unknown semantics |
| N14 | Close observed under arbitrary subprocess wrapper | Not sufficient: exact fixed bwrap unshare-all plus effective fork-denying seccomp required |
| N15 | C fixture attempts fork/clone or network under chosen isolation | No descendant/network escape; measure actual kernel behavior where executable |
| N16 | Malformed model output after actual runtime close | Negative phase-specific result, not a model-quality success |
| N17 | Canonical task remains nonterminal, unrelated task, stale grant/source | Consumer must not create a successful/terminal negative report from stale observation |
| N18 | Canonical negative terminal plus fake/untrusted observation | Barrier stays protected; no fabricated verified completion |
| N19 | Canonical negative terminal plus actual current trusted native close | Negative ECHO report may persist; inference/patch/independent repair success remain false |
| N20 | Raw stderr, stdout, prompt/context canary, bearer or private key content | Only bounded allowlisted codes/counts/digests cross observation/report; no raw payload leak |
| N21 | Signal/exit code/pid/count/elapsed/schema mismatches, NaN or oversized values | Reject inconsistent proof; IDs and hashes alone do not prove execution |
| N22 | Existing normal success and failure paths | No weakened success verifier, new scheduler, changed inference budget or auto-replay |

## Test execution and boundaries

1. Freeze and checkpoint these invariants before implementation inspection.
2. Use independent Node tests and small actual compiled C executables solely
   under clearly identified CONTRACT_TEST fixtures. They are not a substitute
   for pinned Qwen execution and may never be branded as reviewed neural runs.
3. No real service starts, production barrier changes, owner task resubmission,
   model run or budget enlargement is authorized to this reviewer now. A real
   pinned runtime timeout diagnostic, if needed, belongs to the chief's
   separately reconciled CPU lease and explicit task scope.
4. Record exact SHA, commands, exit codes and RED/GREEN outputs. Each proven
   claim names its layer: source review, injected negative fixture, real native
   CONTRACT_TEST, actual pinned runtime, or canonical service integration.
5. Positive trusted-brand/real service release remains NOT_RUN unless actual
   accepted execution evidence is available. A test-only success factory is
   forbidden; absent proof is not converted to PASS by mocked spawn callbacks.

C fixtures must be short, resource-bounded, deterministic, without network or
host-file access. Fixed launcher isolation and seccomp must be inspected and
tested instead of inferred from its display name. Timeout/abort acceptance is
about observed process lifetime; receipt bytes alone cannot prove descendants
gone. If the required isolation is unavailable, report BLOCKED, never run an
unprotected fallback shell. Temporary test state must be removed; existing S01
state is read-only and is not reused as an execution sandbox.

Success criterion: no counterexample creates a production trusted observation,
success claim, cross-task release or secret disclosure. The current trusted
failure path must preserve a conservative unknown result when its required
facts cannot be established. Rollback is omission of this isolated test/docs
branch; it makes no product or owner-data modifications.
