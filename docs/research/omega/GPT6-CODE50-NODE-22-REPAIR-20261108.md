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

## Real GitHub GREEN (same repair branch, exact SHA before workflow cleanup)
- GitHub Actions run [42462862294](https://github.com/Ullmann27/device-ai/actions/runs/42462862294), SOURCE SHA `89254261edf1ef382129ed2ec2b8b0afdfdaf1ca`.
- `node-backend` **SUCCESS**. Full-node JUnit: **2.430 total = 2.393 PASS, 0 FAIL, 37 SKIP**. `code50-full/report.json` status PASS; `source.commit` equals run SHA and sourceUnchanged=true. Critical PASS. Four targeted cockpit contracts 11/11 PASS. Backend 64/64 PASS.
- Unmodified production `branch-discovery.mjs` still rejects unsafe local Git filters; only test processes isolate fixture-global config. Local repro: host-global LFS clean filter appears in `git config --get-regexp`, disappears under `GIT_CONFIG_GLOBAL=/dev/null`, malicious repository-local clean filter remains detectable.
- This branch's `android-host` was intentionally SKIPPED. No Android conclusion may be inferred from Node PASS.

## Additional Android failures on original parent
- GitHub run [42461426218](https://github.com/Ullmann27/device-ai/actions/runs/42461426218), SHA `c6e29e2f0bd108c141dea885bdd4b34d509c2356`: **4022 JUnit, 27 FAIL, 20 SKIP**, compile reached JUnit. The full Android job FAILED, so `lintDebug` and unsigned APK assembly did not finish.
- Reports at Artifact `code50-github-android-evidence` ID 13609838295. Clusters: 12+ UI/deck/rail/stale 3D test assumptions; one old 16- vs 32-chunk voice contract; three entity-role resolution failures; several Framing/precision/Reasoning mismatches. Must audit source/test contract separately without relaxing real UI hit-target or privacy invariants.
- Code50 APK **NOT_BUILT**, package/signature verification **NOT_RUN**. No GitHub Release.

## Integration constraint
Merge this test-only repair to the Code50 QA staging #630 branch, not main. After merging, run real full-node/Gradle on the new exact staging HEAD. Follow with isolated Android JUnit diagnosis before any original-signer work.
