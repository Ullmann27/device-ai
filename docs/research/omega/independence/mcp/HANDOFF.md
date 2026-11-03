# Continuum Coding MCP — bounded read-only local access

Status: WIP / NOT_RUN. Date: 2026-11-03. Base: `527d43f93f6b1837bd8da38cfefcfc84a84fd464`.
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

Next: implement the transport and isolated read bridge; run adversarial HTTP tests; integrate the actual service reader; secure raw logs and exact source SHAs. Android/Fold7, local inference and coding success are separate gates, not results of the MCP transport.
