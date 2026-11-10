# OMEGA bounded working context

Owner: OMEGA root integrator. Canonical reviewed destination: Cinema PR #715.
Implementation branch: research/omega-working-context-20261110.
Baseline local515afc28 / remote28bee009; same tree735a663d522e16d56e979c78a908e9f3baa2f031.
Protocol: OMEGA-PROTOCOL-20261110.md on root integration branch3158c346.
EXP-11/14/16 compatible; no new database, bus, engine or model loop.

## Scope

One opt-in prototype extends the existing `LocalConversationContext` →
`LocalConversationSituationTracker` → `ConversationSituationModel` ownership.
The model retains one task with at most8 constraints and8 shown references.
Raw dialogue remains capped at4 turns. A pure resolver creates data-only plans
from trusted source metadata and the untouched current request. Constraints
carry source IDs; candidate identities carry revisions. Conflicting duplicate
constraint keys fail closed rather than applying last-write-wins.

`LocalConversationContext(workingTaskResearchEnabled = false)` is unchanged by
ordinary callers. There is no trusted seed registration caller in AgentActivity.
**Registration into the actual product UI: NOT_WIRED.** Host tests explicitly
opt in and bind fixture metadata. This is source-connected context research,
not active app intelligence, real model reasoning or verified animation editing.

## Caller contract

1. A trusted application caller presents verified candidate identities and
   confirmed owner constraints, then calls `bindWorkingTask(sessionId, seed)`.
   Model output, recalled text and web text must not be used as confirmation.
2. For each owner utterance, call existing `inspectRequest` before inference.
   In opt-in mode this also resolves the work reference or returns the existing
   clarification directive. The direct `resolveWorkingTask` API exposes the
   immutable plan/clarification for the experiment and future adapters.
3. `render` includes goal/reference/constraints only for the current resolved
   plan. Existing prompt budgets include the block whole or omit it whole;
   no constraint or negation is truncated. Whole prompt assembly still has its
   own existing refusal gate. Repeated inspection/resolution does not toggle
   OTHER/PREVIOUS; trusted selected focus changes only through binding.
4. Original request remains the permission input. The plan has no capability,
   grant, confirmed success, memory-write command or executable patch. Existing
   ToolGate, evidence evaluator, experience store and cockpit events remain
   the authority/result chain. Source verification is not implied by a hash.
5. Before consuming asynchronous results use `isWorkingTaskPlanCurrent`, plus
   existing actual receipt/evaluator checks. Each fresh admission has a context
   nonce and monotonic generation. Clear/rebind/same-source ABA cannot revive
   an earlier plan. This check is not itself an outcome verifier.
6. Record/recordUnanswered uses the existing turn path. Topic detours suspend
   the work context; explicit return with a known source can resume. Session
   change, clear and process exit remove it. Source revocation invalidates the
   active plan. A changed seed needs a higher task revision. Invalid/conflicting
   seed registration retains prior metadata but blocks use until corrected.

## Supported boundary

Selectors operate only within the trusted shown candidates: known aliases,
first/second/third/last, previous and other relative to shown focus. Ambiguous,
negated, quoted, conflicting and unknown references clarify. Generic assent
creates no plan and cannot approve a tool. Unsupported natural language is
not claimed understood. General semantic conflict detection between differently
named constraint keys is not implemented; caller validation remains required.

## Validation

50 development/existing context JUnit tests passed on Kotlin2.4.0/JDK21.
The script recompiles the four changed production sources and selected tests,
using a separately frozen Android-compiled dependency snapshot. Exact source
hashes, dependency class digest, toolchain and raw logs are in the development
evidence directory. This script is not a full Android compile. Root owns full
Android compilation and the independent unseen evaluation after source freeze.

Reproduce with existing toolchain paths set in `KOTLINC`,
`CONTINUUM_COMPILED_CLASSES`, `CONTINUUM_JSON_JAR` and
`CONTINUUM_JUNIT_JARS`, then run:

```bash
bash scripts/research/run-omega-working-context.sh /tmp/omega-working-context
```

Fold7, real model output, CPU/thermal/energy on device, voice/lip synchronization,
actual animation edit and APK/signing/install: NOT_RUN.

Rollback: leave the default-off gate disabled; revert only this patch after
root review. No changes to protected Build52, main, Cinema renderer or signing.
