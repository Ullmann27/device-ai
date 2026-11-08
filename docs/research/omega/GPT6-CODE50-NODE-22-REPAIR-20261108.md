# Code50 GitHub Node QA repair — 2026-11-08
Parent PR #630, SHA `c6e29e2f0bd108c141dea885bdd4b34d509c2356`. GitHub Actions Run [42461426218](https://github.com/Ullmann27/device-ai/actions/runs/42461426218) is exact baseline.

## Actual failing tests
Full-node: 2371 PASS / 22 FAIL / 37 SKIP; critical PASS; focused 11/11 Node PASS. Raw artifacts: GitHub run, artifact `code50-github-node-evidence`, JUnit `code50-full/full-node.xml`.

* 18 failures in `branch-discovery.test.mjs` (13) and `branch-bots-cli.test.mjs` (5): `DISCOVERY_CLEAN_FILTER_UNSUPPORTED` cascades. The fixture Git commands already suppress global filters, but `discoverBranches` and CLI child processes inherit runner-global `filter.lfs.clean` on GitHub. This is **not established as a shallow-clone/root branch-name error**. Keep the production filter-denial guard; isolate the synthetic fixture test environment instead.
* 4 stale source tests: continuous conversation speech uses `SPEECH_CONTENT_MAX_CHUNKS` 31, camera framing blends `targetWindow`, budget `task.length > 240` and blank-task guard, assistant same-session default `preserveLocalContext = true`. Repair source assertions against actual code; no weakening safety invariants.

## Planned changes
Only isolated fixture test setup, four source tests, and (if needed) GitHub workflow checkout depth. No local model, ToolGate, memory, signer, version, main or publisher modifications.

## Hard gates
Exact-SHA GitHub full Node / Android unit UI Lint; unsigned QA artifact is not installable. Original P12 signing/publishing requires separate real owner-approved publisher; no fake release URL or SHA.
