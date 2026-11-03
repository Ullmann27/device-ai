# Continuum Independence — Coding Agent Release 1

Date: 2026-11-03. Quota: NOT_VISIBLE. Chief branch:
`codex/continuum-independence-chief-20261103`.

Base is Bot Genesis PR439 HEAD `527d43f93f6b1837bd8da38cfefcfc84a84fd464`.
Its product source is `1595389274acb840c4ab544fa699c9eb2a04bd53`:
Android compile + configured lint PASS; 2,224 tests PASS /17 SKIP /0 FAIL,
274 canonical Node checks PASS. Later changes are evidence and a focused host
compiler-script repair. Preserve all original package/signature/release boundaries.

## Authorized next milestone

A user-started local coding service must accept an explicitly authorized owner
task without Codex or an external model, run the existing Qwen1.5B/CCC1 isolated
FORGE→LINT→ECHO chain, expose true progress/evidence, and support safe stop,
cancellation and restart. Existing `runTeamleaderLoop`, canonical task journals,
backlog, model admission, executor, verification and bot profiles remain authority.
No second scheduler or task truth database. No7B model is admitted by this work.

The separate MCP endpoint initially exposes only `get_status`, `list_agents`,
`list_tasks` and `get_evidence`. It is authenticated, bounded and local by default.
It provides no task-submission, approval or mutation tool. A localhost server is
not a demonstrated ChatGPT connection, and this work does not authorize a public
deployment or a tunnel. Owner CLI/postbox authority is separate from MCP reads.

## Actual team and exclusive file ownership

| Session | Responsibility / writes |
|---|---|
| Chief | Architecture, integration, distribution/handoff, final acceptance |
| independence_service_builder | `coding-service*.mjs`, CLI, narrow existing-loop hooks |
| independence_service_challenger | Frozen lifecycle/recovery/budget counterexamples, review tools/docs only |
| independence_mcp_builder | `coding-mcp*.mjs`, MCP CLI, actual protocol/auth integration |
| independence_mcp_challenger | Frozen MCP/security tests and protocol review only |
| genesis_final_native_verifier | Finish prior frozen g04/known-u03 evidence; no new product edits |

Builders must publish concrete interface contracts before dependent integration.
Reviewers freeze counterexamples before inspecting candidate expectations. Only
one heavy build/model workload runs at a time. Branches stay isolated, with early
pushes and draft PRs. No main merge, release, installation or productive weights.

## Verified baseline execution

New unknown g04: one genuine local call, ABSTAINED, no confirmed repair or
technical experience. Only FORGE executed a container stage; ECHO reported the
negative outcome. LINT was not reached. This is not a three-role success.

Separately frozen KNOWN-u03 regression: one genuine local call, RED→GREEN and
independent verification; actual FORGE/LINT observations, ECHO report, confirmed
technical experience and one eligible EXACT_SOURCE recall from a fresh process.
This demonstrates known-case plumbing, not a new unknown-task improvement.
Final raw evidence and independent receipt audit are being secured on PR455.

## First implementation gates

1. Reuse the actual local loop and CCC1 execution route; make process ownership,
   bounded queue admission, current grants and UNKNOWN recovery explicit.
2. Implement owner init/prepare/submit/status/cancel/start/stop with current source,
   immutable task and evidence bindings; never execute free-form shell text.
3. Connect MCP reads to validated canonical views; reject missing/wrong auth,
   path escape, secret access, stale evidence and all mutation methods.
4. Exercise actual daemon lifecycle/process crashes independently, then one
   genuine local-model owner task through the released CLI.
5. Freeze final source and rerun Node/Kotlin/Android/security gates. ARM64/Fold7
   stays a separate acceptance; current ADB inventory is empty.
