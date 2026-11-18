# Code50 local QA handoff — 2026-11-08

Status: WIP / UNVERIFIED Android source.

## Scope and source identity

- Repository: Ullmann27/device-ai (public project source; account verified as Ullmann27).
- Parent: PR #603, `codex/continuum-code50-integration-20261107`.
- Baseline: `0c59dcc7b249d7eb6bfc9fb21d2306f814105ed6`.
- Repair/evidence branch: `chatgpt/code50-local-qa-20261108`.
- Scope: execute the pending source acceptance gates, repair demonstrated regressions, preserve reproducible evidence.
- Relevant contracts: EXP-00, EXP-07, EXP-12; owner memory, ToolGate and signing authority stay unchanged.
- No main merge, release, signer access, device installation or model promotion.

## Baseline observation

The exact integration SHA was cloned and read locally. Node is `v24.22.1`, matching `.node-version`.

Executed:

```bash
node --test --test-reporter=tap \
  .github/continuum/owner-cockpit-voice-original-orb.test.mjs \
  .github/continuum/grounded-recall-android-wiring.test.mjs
```

Result: **3 tests; 2 PASS; 1 FAIL; exit 1**.

Failure: `home microphone retains the cockpit for continuous voice replies` looks for
`addAssistantMessage(value: String, preserveLocalContext: Boolean = false)`.
The current Activity deliberately defaults `preserveLocalContext` to `true` and
only clears it when a semantic-policy caller explicitly passes false. This is a
stale source-contract expectation, not evidence that the new context behavior
should be reverted.

No source fix is included in this checkpoint. Raw test output and subsequent
RED/GREEN evidence will be committed with the repair.

## Validation in progress

The pinned JDK17, Gradle 8.11.1 and Android command-line tools are being prepared
locally, using the versions and hashes already documented in this repository.
The Render-only QA wrapper is not being impersonated or bypassed; local Gradle
validation is explicitly permitted by AGENTS.md.

- Requested Node contract: pending exact handoff reconciliation.
- Focused Gradle / full unit and UI tests / lint: NOT_RUN.
- 10-sentence Fold7 hands-free test: NOT_RUN.
- 15-second live avatar proof: NOT_RUN.
- Thermal/FUI device acceptance: NOT_RUN.
- Original-signed Code50 APK: NOT_BUILT / NOT_RELEASED.

## Next step and rollback

Reconcile the latest PR handoff, execute the exact Node contract, repair the
demonstrated stale expectation without weakening cockpit/context guards, then
run the focused and full Gradle gates if the local toolchain is available.
Record any dependency or compile blocker precisely. Device acceptance remains
separate. Rollback is to close this child PR or revert its commits; the parent
integration SHA is unchanged by this checkpoint.
