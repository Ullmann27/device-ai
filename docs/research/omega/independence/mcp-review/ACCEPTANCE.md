# Independent read-only MCP acceptance, frozen before candidate review

Freeze: 2026-11-03 21:42 UTC. Reviewer: `independence_mcp_challenger`.
Baseline: `527d43f93f6b1837bd8da38cfefcfc84a84fd464`.
Experiment families: EXP-00, EXP-02, EXP-04, EXP-05, EXP-06, EXP-07,
EXP-09 and EXP-11; this is protocol/security verification, not a new
model-quality experiment. Quota: `NOT_VISIBLE`.

Before this freeze the reviewer inspected only the existing
`.github/continuum/mcp-git-readonly-server.mjs`, project rules, and official MCP
primary sources. The candidate implementation, its tests and builder outcomes
have not been inspected. These cases become regression material when shared;
passing them is not a claim of broad security certification.

## Scope and oracle

The only product tools are `get_status`, `list_agents`, `list_tasks`,
`get_evidence`. Every call is an authenticated read of current canonical
service state. MCP may neither schedule work nor mutate tasks, grants,
experiences, receipts or files. Names/roles are data, never credentials.
Owner-authorized processes remain the trust boundary; a malicious process
already running as that owner and able to change protected files is outside
the isolation claim. Workspace-controlled bytes are untrusted.

The builder declared Streamable HTTP `2025-06-18` before implementation
inspection. This is a legitimate supported version, not a claim to implement
the latest specification. Official `2025-11-25` was also retrieved to identify
later differences. Source retrieval hashes are in `PRIMARY-SOURCES.json`.
Use the negotiated version's normative requirements as the oracle.

Mandatory spec requirements relevant here:

- Initialization negotiates a supported version and advertises only supported
  capabilities. A supported requested version must be echoed.
- HTTP POST contains one JSON-RPC object, not a batch. Accepted notifications
  return 202 and no body. JSON responses have the appropriate content type.
- Invalid/unsupported `MCP-Protocol-Version` returns 400. Stateful servers bind
  sessions; unknown/terminated session IDs return 404. Stateless operation is
  permitted but must not silently treat foreign session IDs as authorization.
- Validate Origin; local binding should be loopback. GET may return 405 when
  SSE is not offered. Session DELETE may also be explicitly unsupported.
- All tool inputs require validation and access control. Tool annotations alone
  are not enforcement. Protocol errors and tool failures must remain failures.

Additional owner/product acceptance, not invented spec requirements:

- Bearer authentication on every HTTP request, before reads; no query/body token
  alternative. Reject nonlocal Host/Origin and all unapproved write methods.
- Hard bounds on input bytes, page size, response bytes, in-flight requests,
  sessions and time. Reject invalid cursors; prevent cross-collection cursors.
- Current canonical data, not an MCP-maintained shadow registry. No stale
  authorization, forged actor role, cached result or old source receipt can
  create current execution/verification success.
- Evidence uses opaque IDs and approved projected data, not arbitrary paths.
  Traversal, symlinks/hardlinks and workspace substitutions may not disclose
  private state. Include no private keys, bearer tokens, environmental secrets,
  arbitrary terminal transcripts or unchecked model text.

Static local bearer authentication is a limited owner-only transport profile.
It is not full MCP OAuth authorization conformance or proof that ChatGPT can
connect. OAuth protected-resource discovery, remote transport, public exposure
and ChatGPT registration are out of scope unless separately implemented and
tested. Stdio, if present, uses documented inherited owner-process trust.

## Frozen counterexamples and tests

`cases.json` fixes test IDs, inputs/risks and acceptance independently of the
builder API. Harness names/options may later adapt without changing these
expectations. Every negative case must prove the canonical reader/mutator
was not invoked when denial should precede a read. Response validation checks
both HTTP status and JSON-RPC result: HTTP 200 is not proof of tool success.

Test layers:

1. Real HTTP sockets with a counting view fixture: protocol and boundary tests,
   explicitly not evidence of real coding or model inference.
2. Real service read adapter against temporary canonical service data and
   granted test fixtures: freshness, receipt projection, file-integrity and
   privacy checks. Fake fixtures alone do not satisfy this layer.
3. Source review: actual entrypoint imports and call graph; no second scheduler,
   no external models and no caller-supplied path/role privileged dispatch.
4. Chief integration rerun on exact SHA. Android/Fold7 and actual ChatGPT client
   remain `NOT_RUN` until performed in those environments.

Stop/decision criteria: any unauthorized read, mutation, false verified status,
secret disclosure or protocol break that prevents documented use blocks
acceptance. Record RED as well as GREEN and the exact candidate SHA; fixes
remain the builder's responsibility. Report residual limits explicitly.

## Reproduction and rollback

Host Node tests must run without API credentials, external services, an LLM,
Docker or Android builds. Use temporary fixtures and close all sockets/processes.
Capture Node/OS versions, commands, duration, exit code and candidate file hashes.
This branch changes only independent tests and evidence. Rollback requires no
product migration; omit its test/docs commits if a review harness is unsuitable.
