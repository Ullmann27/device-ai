# GPT-6: canonical coding-provider outcome-journal checkpoint (27 Oct 2026)

**Branch:** `chatgpt/continuum-coding-outcome-journal-20261027`
**Base:** PR #183, exact `029ba12c2b92a8fc52025b0a557e9baaa4e3a153`, descendant of PR #182 `8695f28bc15e26207c1908845a7e96c089cc2033`. PR #184 storage is already selectively integrated into PR #183. All other branches remain unchanged.
**Scope:** EXP-01, EXP-02, EXP-07, EXP-08, EXP-11, EXP-12. Research-only. No main/owner release, Android install, model promotion, new executor/queue, or owner-memory write.

## Actual baseline and selected minimal change

PR #183 already holds a working bounded Harness SDK -> canonical CodingProvider -> CCC1 -> existing independent checks, with a *negative* actual Qwen 1.5B outcome. The first model-generated patch made the 24-case test worse (20 failures before, 23 after); the second call timed out. The patch was correctly rejected. The 40-case combined native source suite and 18 Harness SDK transport tests are **previous PR #183 results, NOT rerun by this branch**. Real two-process encrypted storage is also existing PR #183/184 code, not a new deployment.

The precise defect addressed here was in `.github/continuum/coding-provider.mjs`: a completed `spawnSync` child was durably classified as `status:RESPONDED` *before* its exit status, error signal, JSON framing and typed proposal were validated. A timeout or rejected output therefore looked like a normal response in the owner-local request journal. The request reservation already consumed the correct budget. No new request identities, queues, authorizers or persistence formats were introduced.

**Updated existing seam:** `.github/continuum/coding-provider.mjs`. The *same* version-1 `request-N.json` is still reserved durably as `RESERVED` before inference. When a child actually returns, it now records:

- `FAILED` with bounded `failureKind`: `TIMEOUT`, `PROCESS_ERROR`, `SIGNALLED`, `NOOP_PATCH`, `INVALID_MODEL_RESPONSE` or `CHILD_EXIT_NONZERO`.
- `INVALID_OUTPUT` with `failureKind`: `INVALID_JSON`, `WIRE_SCHEMA_INVALID`, `REMOTE_IDENTITY_INVALID` or `PROPOSAL_REJECTED`.
- `RESPONDED` with `proposalAccepted:true` **only after** a syntactically valid, bounded, non-authoritative proposal is checked. **This is NOT a verified repair**; the existing CCC1 independent gate still decides success.
- An unexpected synchronous child throw/process interruption leaves `RESERVED` (unknown), not a fabricated terminal status. It still consumes the already reserved call. A resumed provider may not retry that exhausted budget.
- No raw prompt, model output, exception, process stderr, secret or owner data is persisted. Only the prior response hash/byte count, existing identities, elapsed time and finite outcome labels remain.

**New native Node test source:** `.github/continuum/coding-outcome-journal.test.mjs`. Five synthetic test cases cover timeout, model no-op, success exit with broken JSON, valid but unverified proposal and unexpected process loss. Each case explicitly probes persisted outcomes and budget exhaustion after restarting the provider. Tests use local scratch Git repositories only and never download model weights, contact an external endpoint or execute generated patches.

## Test evidence and limits

- **7/7 source-derived pure-JS branch-classification checks PASSED** in an isolated V8 script over the exact GitHub-fetched failureKind expression: timeout, process-error, signal-only, known no-op, known invalid model response, other nonzero, remote nonzero. This is a limited expression test, **not** the new native Node suite.
- Production transformed-source parser **PASS** and new-test transformed-source parser **PASS** in a JS evaluator after removing module declarations; these are *syntax smoke checks, not ESM module loading or native execution*. Exact published blob SHAs are obtainable through GitHub.
- **Native Node five-case test suite: NOT_RUN.** This execution container does not contain an authenticated checkout of the private repository. The relevant Node module imports the wider project; no full module-graph test was executed or falsely claimed.
- **Existing provider/Harness/storage regression after the patch: NOT_RUN.**
- Actual model inference, CCC1 Docker RED->GREEN->freshGREEN, Android SDK/Fold7/Gemma/UI, physical compute/storage fabric: **NOT_RUN**.
- Existing owner-v25 source freeze remains intentionally unchanged; research source changes are NOT approved for APK/release.

Run from a clean authorized checkout of this branch with Node v22+ and Git installed:

```sh
node --test .github/continuum/coding-outcome-journal.test.mjs .github/continuum/coding-provider.test.mjs
node --test .github/continuum/harness-coding-provider.test.mjs .github/continuum/storage-fabric.test.mjs
```

When the reviewed Harness SDK is unavailable, its integration tests may explicitly SKIP; never report them as passed. Preserve source hashes and raw TAP. Review actual `request-N.json` status transitions with an independent reviewer. Do not execute generated patches outside the existing branded executor.

## Current-state matrix

| Component | Actual current status |
| --- | --- |
| Owner order -> canonical backlog / repair / CCC1 | Existing from parent PR #183 (not rerun here) |
| DeepSeek Harness SDK adapter -> local Qwen inference | Existing code; actual prior model repair **FAILED** |
| Model-call reservation -> restart/anti-replay | Existing; unchanged durable reservation |
| Outcome categories in existing request journal | **SOURCE_INTEGRATED**, limited source-only checks, **NATIVE_NOT_RUN** |
| Storage two-process AES-GCM prototype | Existing from PR #184 / PR #183, same physical host |
| ComputeFabric distributed inference | **NOT_IMPLEMENTED**, no additional GPU/RAM capacity |
| Receipt-qualified skill/experience reuse | **NOT_IMPLEMENTED** |
| Genuine autonomous good model-generated patch | **NOT_DEMONSTRATED** |
| Main Android application execution of this research commit | **NOT_RUN**, no APK |

## Immediate executable continuation for GPT-6 or Sol

1. Fetch this exact branch, verify its HEAD and `git diff` against PR #183. Run the two native Node commands above before any merge suggestion. Inspect the generated request traces to verify that INVALID_OUTPUT/FAILED never claim successful work. Reconcile any compatibility failure without weakening previous gates; publish raw red/green receipts.
2. Run existing full Node worker and the separate scripts inventory in a clean authorized worker environment and report owner-v25 research freeze accurately. No background daemon or unbounded retries.
3. On a different frozen, new and owner-authorized coding case, compare actual unassisted/localization-hint/repair-intent-hint arms with the same model identity, new budgets and independently sealed behavioral checks. **Do not reset either original failed 0.5B/1.5B budgets.** Record actual calls, timeouts, patch validity and independent CCC1 outcome, and never merge assisted and unassisted success rates.
4. Integrate a data-only receipt-qualified capability adapter using the **existing** ExperienceLookup/ExperienceCompiler; keep unverified outcomes CANDIDATE_ONLY and preserve revocation and task/source/argument/output bindings. The known PR #184 catalog rollback needs an **independent monotonic anchor** before any higher-trust storage use.
5. End-to-end owner order -> canonical backlog -> provider -> fixed isolated executor -> independent fresh run -> review package -> restart remains **unproven**. Focus on this single bounded vertical slice before staffing multiple new autonomous workers. Physical Android worker/runtime tests and owner permissions remain separate gates.

Rollback consists of reverting only this branch's `coding-provider.mjs` change and removing its new test/doc files; previous `request-N.json` records remain version-1 and budget-consumed. No existing owner journal was edited by this session.
