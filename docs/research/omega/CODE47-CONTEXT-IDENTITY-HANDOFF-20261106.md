# Code47 context and identity repair — 2026-11-06

Status: WIP / TESTS_NOT_RUN / DEVICE_NOT_RUN.

## Source and ownership

Base: `5572edfbb02570ae479a8f0f72179f5ca33027c5`, PR #578.
Branch: `codex/continuum47-context-identity-20261106`.
Target: `codex/continuum-code47-qa-candidate-20261106`.
Experiments: EXP-05 (local model context), EXP-12 (identity and checkpoint continuity); no research promotion.

This branch owns conversation retention/transcript restore, bounded follow-up framing, compiler identity, Bootstrap identity and focused tests. AgentActivity is edited only by the action/UI agent; BoundedAgent only by the output agent. Integration contracts are supplied separately. No model training, deployment, signing, real external message or main merge.

## Repair order

1. Preserve complete admitted owner requests through persistence. Never restore a clipped legacy prefix as complete evidence; missing/oversized/corrupt turns must not resurrect an earlier contradicted assertion.
2. Keep useful bounded dialogue/continuation context, with explicit completeness and the actual final answer section. Preserve the existing total prompt/runtime budgets and tool/privacy boundaries.
3. Project one truthful app identity, validated chosen name and actual loaded model into mandatory context; align fixed self-description. Pure memory recall retains the dialogue; revocation remains invalidating.

## Planned interfaces

- `LocalAssistantIdentity.safeName(chosenName: String?): String` for Bot1's name-aware input routing; no second name store.
- Explicit request/response completeness in persisted context and restoration. Existing call sites stay source-compatible where safe; required Activity integration will be recorded here.
- Partial model text is untrusted dialogue only, never confirmed memory, action success or owner evidence.

## Verification

Current-source API/source inspection only. Fresh Kotlin/JUnit toolchain is being restored centrally. Prepare real regression failures and run the same focused tests against baseline and repair once available; no production stubs or second heavy Gradle build.

Target controls: late negation beyond 1,000 characters, legacy clipped rows, malformed rows, reset/revocation, Unicode byte boundaries, continuation after a long/partial answer, required clock tail, name/profile injection, true model binding and memory-recall context retention.

## Resume and rollback

Read this branch's real remote HEAD and diffs first. Next: regression tests for transcript completeness, then implement the minimal format/restore repair. Revert this branch's commits before integration to return to the candidate; do not delete or rewrite existing transcripts.
