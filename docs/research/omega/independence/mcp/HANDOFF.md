# Continuum Coding MCP — bounded read-only local access

Status: SOURCE_IMPLEMENTED / HOST_TESTED; independent counterchecks running. Date: 2026-11-03. Base: `527d43f93f6b1837bd8da38cfefcfc84a84fd464`.
Production ancestor: `1595389274acb840c4ab544fa699c9eb2a04bd53`.
Branch: `codex/independence-mcp-20261103`.
Quota: `NOT_VISIBLE`; use frequent Git checkpoints.

The current owner instruction authorizes this isolated coding-service increment; historical October release freezes do not authorize merging, signing, installing or publishing. None is performed here.

## Contract frozen before implementation

Hypothesis: a separate authenticated loopback MCP endpoint can expose the existing coding service's verified state without adding execution authority, a second scheduler or a second task database.

Four read-only tools: `get_status`, `list_agents`, `list_tasks`, `get_evidence`. The server must use the service builder's `createCodingServiceReader({configPath})` from `.github/continuum/coding-service-reader.mjs`. Lists return `{items,nextCursor}`; evidence accepts task ID plus the allowlisted evidence ID `report` or `result`, never a filename. Reader checks current grant, canonical journal and evidence binding anew. Independent transport tests use explicitly labelled adapters, not claimed local model execution.

Acceptance: actual HTTP initialization, notification, tools/list and tools/call sequence; exact schemas; authentication on every connection; invalid, missing, revoked and query credentials rejected; Origin and Host rebinding rejected; bounded body, result, sessions, concurrency, calls and time; no write tools, arbitrary file reads or stale session authority. Independent challenger freezes and executes separate tests. Service integration must additionally exercise the real reader; adapter tests do not satisfy that condition.

Baseline: existing `mcp-git-readonly-server.mjs` is a two-tool stdio server for fixed Git commands. `mcp-adapter.mjs` already provides owner-allowlisted stdio client semantics. Neither is a protected coding-service observer. Relevant experiment contracts: EXP-01, EXP-02, EXP-08, EXP-11 and EXP-12. Root AGENTS, experiment contract/register, current-state/handoff and Issue #41 metadata inspected. Node available: v24.19.0. No root package.json or additional MCP package is present.

## Transport decision

Pinned MCP version: `2025-06-18`, Streamable HTTP with direct JSON responses and no optional SSE stream. Single `/mcp` endpoint, loopback only; GET returns 405 as the standard permits. Session IDs are random and bounded; calls require the negotiated protocol header. Authentication initially uses a private owner-controlled pre-shared bearer credential. This is an explicit local deployment profile, not the MCP OAuth authorization-server discovery flow. No OAuth/remote-client interoperability is claimed.

Official sources fetched on 2026-11-03:

- https://modelcontextprotocol.io/specification/2025-06-18/basic/transports.md
- https://modelcontextprotocol.io/specification/2025-06-18/basic/lifecycle.md
- https://modelcontextprotocol.io/specification/2025-06-18/basic/authorization.md

No public listener, proxy, tunnel, deployment, paid service or installed ChatGPT connector is created. A cloud ChatGPT client cannot reach this machine's loopback address merely because the endpoint exists. `CHATGPT_CONNECTED: NOT_RUN`.

## Ownership and continuation

MCP builder owns new `coding-mcp*.mjs`, its tests, `scripts/continuum-coding-mcp.mjs` and this evidence folder. Service builder owns canonical read API and service lifecycle. Reviewer: `/root/independence_mcp_challenger`; Chief performs integration. No app, model, CCC1 or existing scheduler code changes in this slice.

## Implemented and measured

Implementation: `83b805cb`; independent B02 schema repair: `3ca1dd9a`; real process tests: `af26cb09`; canonical integration driver: `60074b69`; GET-session repair: `5cf463a7`. Draft [PR #459](https://github.com/Ullmann27/device-ai/pull/459). Existing Git MCP files remain unchanged.

- Authenticated loopback-only Streamable HTTP and exact four-tool allowlist; token outside source/project/profile/service roots, owner-only file/parent permissions, no symlinks/hardlinks, immediate credential rotation/removal revocation, bounded random sessions.
- Session/tool-bound opaque pagination, strict JSON schemas, Host/Origin defenses, no query authentication, fixed read-only child entrypoint, cleared child environment, 9-second process-group timeout, no arbitrary module/command/path input.
- Service builder's canonical reader checks actual profile metadata, current task grant/source/journal/receipt, evidence ID and projected content hashes. The transport recomputes returned JSON hashes and does not invent terminal results.
- First independent finding B02: `limit:null` incorrectly selected the default via null coalescing. Corrected by distinguishing an absent property; explicit `arguments:null` is rejected too. The original failure and fix remain documented rather than reporting an uninterrupted green history.
- Follow-up builder counterexample: an unknown session on GET returned optional-stream 405 before session validation. Corrected to 404 consistently with POST; independent reviewer agreed with the protocol reading and added separate checks. Full RED 15/16 and GREEN 16/16 preserved as `get-session-red.log` and `get-session-green.log`.

On clean combined source **`1aae431d08e01342ed509b57513e73e118fdd877`** (service `fd51d648` + MCP `60074b69`), 15 own transport/process tests passed, 0 failed/0 skipped. Raw integrated log: `integrated-tests.log`.

The separate real CLI/HTTP integration used the actual Kotlin registry, fresh temporary profiles and canonical owner task intake. Five checks passed: authenticated separate process, all 20 persisted profiles, actual owner-reviewed pending task without execution claims, fresh-process persistence, and canonical owner cancellation with evidence denial. Byte digests confirmed reads left service/profile files unchanged. No neural model or isolated repair was run by this check. The test task was canceled and all temporary state/processes were removed.

Actual 12 service reads: p50 **139.03 ms**, p95 **257.13 ms**; total **3.96 s**. These are host observations from one check, not Fold7/performance guarantees. Controller-only maxRSS 65,108 KiB excludes Java/Node children; it is not total RAM or energy. Full source/limits/counts in `canonical-first.json`.

After service hardening, clean combined **`c900dfa8ec022cb0db31f5f74a172000e6d6ca3d`** (service `0c54a7bc`, MCP `5cf463a7`) passed all **16** own transport/process tests and the same five actual canonical integration checks. `canonical-hardened.json` and `hardened-tests.log` preserve exact evidence. Fresh hash checks before Java profile reads raise measured observer overhead in this single run: 12 reads, p50 **184.64 ms**, p95 **526.39 ms**, total **5.24 s**. This is a safety/measurement tradeoff, not a claimed speedup. The first run remains historical, not the final hardened timing.

Independent [PR #460](https://github.com/Ullmann27/device-ai/pull/460) reported 19 socket groups against `3ca1dd9a` and 8 real-reader groups against `1aae431d`, with separate new GET/classpath counterchecks in progress. These are the reviewer's own results, not added to the builder's test count. Final shared Chief acceptance must still pin its resulting SHA.

```sh
node --test .github/continuum/coding-mcp.test.mjs .github/continuum/coding-mcp-process.test.mjs
node .github/continuum/coding-mcp-canonical-check.mjs \
  --classpath-file /workspace/independence-profile-773fbc94/runtime-classpath.txt \
  --runtime-env /workspace/synapse-local-model/runtime-env.json \
  --isolation-config /workspace/genesis-g04-experiment/isolation.json \
  --output /outside/source/canonical-check.json
```

The canonical driver is an explicit opt-in check, not an automatic Node test glob or a service startup side effect. Its source must be clean; paths point to the actually available approved host artifacts, never downloaded alternatives. The integration check branch is `codex/independence-mcp-integration-check-20261103`; it is a test combination, not independent service acceptance.

Defaults: 16 KiB request, 64 KiB projected result, 16 sessions, 2 concurrent reads globally/1 per session, 100 calls per session, 120 requests/minute globally, 32 cursors/session, 5-minute idle/15-minute absolute session lifetime, 10-second transport/9-second reader timeout. Ordinary read failure emits a fixed error without private exception text. These limits do not alter the coding task budget.

Next: finish independent socket negatives, rerun against the Chief's exact hardened combined SHA, and preserve separate service/general QA acceptance. Actual MCP observation of a completed independently verified repair is still an integration gate; this pending-task check does not satisfy it. Android/Fold7, local inference, coding success, full OAuth and real ChatGPT connection remain separate open gates. See [owner access instructions](OWNER-ACCESS.md) for real startup and the verified ChatGPT transport/authentication gap.
