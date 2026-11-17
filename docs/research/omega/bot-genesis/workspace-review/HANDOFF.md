# Genesis workspace admission: independent audit and executable contract

Base `4b881f81ddfbe38729a551d9e460e818c6b3b715`; criteria freeze `f0b5405e`.
Own branch `codex/genesis-workspace-review-20261103`. This reviewer changed no
production files. MΩ remote was freshly read as `5feb1a93ffe5db29163e33e4a0ea002ff25252fe`;
its updated handoff records approved real host model admission. Older claims that
no model was approved are obsolete. No model was invoked in this lane.

## Actual caller matrix and smallest adapter

| Existing component | Actual caller / boundary | Missing Genesis connection |
| --- | --- | --- |
| ChiefBotProfileStore | Native roster, selected coordinator profile membership | Stable profile-to-task/attempt association at execution; profile is not grant or competence |
| ChiefDelegationCoordinator | Existing ChiefWorkerPool factory and report callbacks; defaults deny approval/verifier | Stage identity and separate verifier principal; current typed delegation lacks job/source/grant identity |
| ChiefWorkspaceWiring | Pool WorkerJob and existing AgentMailbox reporter; host test ports | Resolve workerId against persisted profile; bind persistent workspace separately from ephemeral sandbox; trusted device transport remains absent |
| AgentMailbox | RoutineDispatcher and ChiefWorkspaceWiring; bounded RAM queues | Typed reference/expiry metadata and consume-time revalidation; no durable replay authority here |
| runCodingTask | Typed CLI/Android worker entry to runTask → runMobileRepairJob → existing localTeamleaderDriver | Hardcoded `typed-coding-worker` must become a strictly validated trusted binding if Genesis profiles execute; not a model-controlled string |
| bindCcc1Workspace | runCodingTask before every canonical repair job | Reuse its journal/current grant/lease/deadline guard, never replace with profile rights |
| isolated-executor | Canonical repair driver, fixed profiles and actual observed resource policy | Bind profile's persistent workspace ID to actual per-run sandbox receipt without claiming the IDs are identical |
| recordTechnicalExperience / recallTechnicalExperiences | runCodingTask post-verification / pre-model context | Mnemos uses these calls and existing journal provenance, not a new memory DB; Echo consumes status/refs only |

Persistent `workspace-bot-forge` is an association. `continuum-<24 hex>` identifies
an observed ephemeral execution sandbox. Neither a matching name nor valid syntax
proves ownership, isolation or correct teardown. Current `validWorkspaceBinding`
intentionally validates bounded correlation only; B01 demonstrates this existing
scope rather than calling it a broken historical security guarantee.

Proposed minimal **Kotlin** API, to be implemented by the assigned builder:
`ChiefTaskHandoff` immutable metadata → `ChiefHandoffAdmission.admit(...)` → existing
`AgentMailbox.send(...)` / `ChiefDelegationCoordinator.delegate(...)`. Metadata:
version, message/from/to IDs, stage, canonical task/job/source/attempt IDs, canonical
request ID/hash, grant fingerprint, persistent workspace ID, expiry, existing-budget
reservation reference and evidence references. Keep the canonical CCC1 journal as
authority. The admission object receives trusted profile/current-task/budget/evidence
ports; a message cannot supply its own authority implementations. No new scheduler.

The Node runner change should be narrowly placed at runCodingTask's existing
bindCcc1Workspace call. A trusted bound identity must be checked against the current
owner-prepared task fingerprint and job claim before use, then copied into actual
executor observations. Keep the default existing caller compatible; do not expose
arbitrary profile grants through JSON. Unknown transport or missing strong isolation
blocks effectful work. Android IPC and same-device inference remain separate gates.

One original budget is debited atomically. Role-specific caps may only restrict it.
Do not multiply a task's maxModelCalls/tool calls by five roles. A TTL mailbox adapter
is not a persistent replay ledger; restart authority stays in CCC1/backlog. Signed
receipt correlation and current revocation must remain checked at each boundary.

## Executable material and actual results

* `tools/genesis-workspace-review/canonical-boundaries.test.mjs`: six independent
  probes on the actual baseline Node modules. **6/6 PASS, zero skips**: bounded
  workspace correlation, lookalike executor rejection, unknown claimed-run recovery,
  stale source refusal, copied model proof refusal despite a real prepared fixture
  grant, and absence of current-task recall authority. No container/model launched.
* Existing `ccc1-technical-recall.test.mjs` and `ccc1-experience-durability.test.mjs`
  rerun in this lane: **50/50 PASS, zero skips**; raw output is in
  `existing-memory-regressions.tap`.
  These are regressions, not fresh independent generalization cases. They cover
  full-store withdrawal and durable canonical grant/revocation semantics already
  implemented by MΩ; do not recreate those mechanisms.
* `adapter-cases.mjs`: 22 frozen admission scenarios, including positive control,
  profile/identity/workspace/source/request/grant/attempt mismatches, expiry,
  deadline/budget expansion, replay, terminal/unknown execution and same-principal
  verification. Cases become development material when disclosed to the builder.
* `run-adapter-cases.mjs`: executable candidate bridge runner. Pass an explicit
  testing module exporting `evaluateCase(fixture) -> {decision, effectCalls}` that
  invokes the real candidate admission. No candidate adapter exists at this freeze:
  baseline exits **2 / NOT_RUN**, not RED and not PASS. A stub returning expected
  outcomes is invalid evidence. The thin bridge itself requires reviewer inspection.
  This checks admission only; concurrent budget debits, crash recovery, actual
  mailbox delivery and independent execution still require separate integration tests.

Reproduce:

```
GENESIS_REVIEW_TARGET=/path/to/frozen/candidate node --test tools/genesis-workspace-review/canonical-boundaries.test.mjs
node --test .github/continuum/ccc1-technical-recall.test.mjs .github/continuum/ccc1-experience-durability.test.mjs
node tools/genesis-workspace-review/run-adapter-cases.mjs /path/to/candidate-review-bridge.mjs
```

A first reviewer fixture used a disallowed `.txt` coding path: four tests passed,
two failed with CODING_TASK_INVALID before reaching the intended assertions. That
reviewer setup error and exit are retained in `fixture-error.*`; changing only the
synthetic path to an allowed `.mjs` fixed the fixture. It is not product RED→GREEN.
`INPUTS.sha256` pins exact tested source/test bytes. No compilation, Android build,
Fold7 execution, firmware/signing, private data or production mutation was performed.

## Next builder/reviewer steps

1. Implement only the admission/typed metadata seam around existing components;
   root assigns exclusive production files before work.
2. Supply a transparent bridge to the 22 cases; reviewer runs it against exact SHA.
3. Add stateful tests for atomic shared reservations, duplicate/expired consumption,
   restart after unknown effects, mailbox-full retry without duplicate execution,
   and separate Builder/Lint principals with actual fresh-worktree verification.
4. Run one genuine canonical task, then Mnemos admission and Echo presentation.
   Distinguish scripted integration fixture, actual local inference, and Fold7 proof.
5. Retain existing full-ledger revocation, lineage and grant negatives in the combined
   acceptance. Never replace those tests with a new successful-looking bot conversation.
