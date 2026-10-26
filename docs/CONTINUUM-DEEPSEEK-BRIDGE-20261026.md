# Optional DeepSeek coding transport

Status: `RESEARCH_SHADOW / PROTOCOL_TESTED / INFERENCE_BLOCKED`.
The selected checkpoint is `deepseek-ai/DeepSeek-V4.1-Flash-1017` at
`1bae0e1088993b9b82de08f59de5be5a34e42170`. There is no configured/deployed teacher,
no allocated GPU, no downloaded DeepSeek weight and no observed DeepSeek inference.

## Executable connection

The existing `.github/continuum/coding-provider.mjs` accepts an optional trusted
caller argument `remoteTeacher: {enabled: true, endpoint, approveRequest}`.
Normal construction remains local. An environment variable alone cannot select
remote inference. Remote use requires the existing per-job `evidenceDir`,
`CONTINUUM_REMOTE_BRAIN_API_KEY` and explicit consent for each prepared request.

The provider builds its normal source-commit/path-bound repository context,
shows `approveRequest({disclosure, userPrompt, allowedPaths})` the exact prompt
and immutable endpoint/model/source/budget manifest, and checks returned consent
before reserving a durable request. The caller must implement the actual owner
review and secret redaction; the module does not display Android UI or invent
consent. No callback is installed in production by this change.

The consent object has exactly these fields:

```js
{
  schema: 'CONTINUUM_REMOTE_MODEL_JOB_CONSENT_V1',
  requestSha256: disclosure.requestSha256,
  expiresAt: disclosure.deadline,
  allowExternal: true,
  allowRepositoryContext: true,
  allowOwnerMemory: false,
  allowSecrets: false,
  allowTraining: false,
  maxCostMicrousd: 0
}
```

The existing fixed CLI's `coding-remote` command takes `{request, consent}` on
stdin. It obtains only the scoped remote secret and configured proxy/CA trust
from its environment. It never inherits GitHub, Render, local-model or owner
credentials. TLS verification remains enabled. A failed/declined request never
selects another external provider or automatically uploads a local-memory fallback.

The same pure coding request/schema builder and proposal parser serve local and
remote transports. The legacy `LOCAL_CODING_PATCH_PROPOSAL_V1` name is retained
for compatibility with the canonical repair loop; `transport` and trace metadata
explicitly identify authenticated HTTPS teacher proposals. They remain
`trusted: false`, `autoApply: false`, `verifiedModelExecution: false` and pass the
existing exact-hunk critic, fixed tests, isolation and independent verification.
The existing `localRequests` field counts one candidate request regardless of
transport; this change does not create another repair pipeline.

## Endpoint contract

An operator-controlled endpoint URL must be canonical HTTPS with the exact path
`/v1/chat/completions`, no credentials, query or fragment. Redirects are forbidden.
Before sending any prompt, the client makes an authenticated, body-free
`GET /v1/continuum/capabilities` to the same origin and requires:

```json
{
  "schema": "CONTINUUM_MODEL_ENDPOINT_CAPABILITIES_V1",
  "modelId": "deepseek-ai/DeepSeek-V4.1-Flash-1017",
  "revision": "1bae0e1088993b9b82de08f59de5be5a34e42170",
  "noCharge": true,
  "retention": "NONE",
  "training": false,
  "tools": false,
  "maxRequestBytes": 98304,
  "maxResponseBytes": 32768,
  "maxOutputTokens": 1800
}
```

The POST uses the existing OpenAI-style JSON schema proposal request with two
messages, `stream:false`, `tool_choice:"none"`, `n:1`, at most 1,800 output tokens,
and a metadata envelope for the job/source/revision, zero cost, no retention and
no training. `X-Continuum-Request-Sha256` binds the consented body and manifest.
The response must carry the exact `model`, `model_revision`, `job_id` and
`request_sha256`, `cost_microusd:0`, bounded `usage.completion_tokens`, one finished
assistant choice and a validated proposal. Tool calls and truncated output are
rejected. Body limits also apply to streamed/chunked responses without a declared
length. One absolute deadline (at most 60 seconds) covers preflight, POST and body;
adapters ignoring AbortSignal are still bounded. There are no transport retries.

This contract needs an operator adapter around a real compatible serving runtime;
stock vLLM/OpenAI-style endpoints do not automatically provide the capability and
revision/cost/privacy assertions. No such adapter has been deployed here. HTTPS
authenticates the configured operator, not individual model weights. Response
claims remain `AUTHENTICATED_SERVER_ASSERTION_NOT_WEIGHT_VERIFICATION`.

Zero-cost declarations are eligibility checks, not a financial lock. Only an
already authorized no-charge endpoint whose operator enforces this policy may be
configured; a client cannot prevent a dishonest or misconfigured server billing.
No public paid API call is part of this prototype or its tests.

Durable request slots from the canonical provider are reserved/fsynced before
the CLI starts, bounded by the task's existing maximum of 1–3 model calls. A
restart/interruption consumes its reserved slot. Consent is bound to a request
index and payload; canonical job execution/resume must remain the caller's path,
not repeated direct helper calls. Hash-only traces retain no prompts, source,
responses or credentials. Errors use fixed codes and an explicit
`KEEP_OFFLINE_LOCAL` fallback with no external retry, tool action, memory or
training write. This is a routing contract, not proof that a local model loaded.

## Capacity and data provenance

`continuum-deepseek-tier-plan.mjs` validates the selected 48-shard snapshot and
510,296,708,312 declared bytes (about 475.25 GiB). Its storage policy is explicit:
1.25 times weight bytes plus 100 GiB, rounded up to 695 GiB. This is local planning
headroom, not an official runtime minimum. Transfer scenarios use decimal Mbps
and 80% utilization: about 14.17 hours at 100 Mbps, 1.42 hours at 1 Gbps or
8.50 minutes at 10 Gbps, before verification/retries/conversion beyond that factor.

Pinned official `config.json` declares 384 routed experts, 6 active per token and
1 shared expert. Sparse active compute does not remove inactive weight storage
or guarantee residency/offload speed. The plan leaves GPU residency, host offload,
active parameter count and KV/runtime memory unknown. Hardware names, a network
volume or the previous 4×H200/384 GiB suggestion cannot establish serving capacity.
Measured kernels/format/conversion, interconnect, context/batch/KV budget and a
real native runtime smoke test remain required. No capacity resource is created.

Teacher output is not owner knowledge or a training permission. This bridge
writes neither native experiences nor a distillation corpus. A later separately
authorized distillation experiment must reuse `ContinuumLearningExperience`
provenance/trust/status/content-hash records (teacher/synthetic output cannot be
relabeled `USER_CONFIRMED` or `REVIEWED_WEB_SOURCE`), `LmCorpus` document-level
TRAIN/VALIDATION/TEST assignment and frozen split hashes, and the native
`DecoderSplit.TRAIN/AUDIT` gates. `SYNTHETIC_FIXTURE` is available for experimental
synthetic records; reviewed source observations and owner facts keep their
existing provenance. Separate source/task families before generation, freeze
holdouts before tuning, retain teacher model/revision/request/result hashes and
review decisions, and reject duplicates/contamination. Hash-only repair traces
are audit receipts, not ready-to-train examples or independent correctness labels.

## Reproduce

```sh
node --test scripts/continuum-deepseek-tier-plan.test.mjs \
  scripts/continuum-deepseek-hub-preflight.test.mjs \
  scripts/continuum-deepseek-bridge.test.mjs \
  scripts/continuum-deepseek-https.test.mjs \
  .github/continuum/coding-provider-remote.test.mjs
node scripts/continuum-deepseek-tier-plan.mjs
```

TLS tests generate an ephemeral localhost certificate with installed OpenSSL,
trust it only in the test child, and use the real provider/CLI over local HTTPS.
They reject wrong auth/revision, missing consent, untrusted TLS and stalled reads.
Responses are explicitly synthetic protocol fixtures, never fake model inference.
Optional read-only Hub metadata inspection uses
`node scripts/continuum-deepseek-hub-preflight.mjs`; in this managed Node 24 executor
the inherited proxy is used via `NODE_USE_ENV_PROXY=1`. It downloads no weights.

Evidence and exact checkpoint/test status: `docs/research/omega/SOL-DEEPSEEK-HANDOFF-20261026.md`.
Rollback: revert this branch's DeepSeek commits; no existing local path requires
remoteTeacher configuration, no owner state/model files or keys were changed.
