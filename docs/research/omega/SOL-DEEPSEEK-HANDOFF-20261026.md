# DeepSeek authenticated bridge — 2026-10-26

Status: `RESEARCH_SHADOW / PROTOCOL_TESTED / INFERENCE_BLOCKED`; no model inference.

## Checkpoint and scope

- Official repository: `Ullmann27/device-ai`.
- Branch: `sol/deepseek-authenticated-bridge-20261026`.
- Review base: `sol/continuum-brain-breakthroughs-20261026` at `c391b83244bd2cc945a1b37bcdd829da27e15ba6`, following PR #150 at `ad14f8b82f9be2cd0026bc16eef71a16bec41424`.
- EXP-05 / EXP-10 / EXP-11 / EXP-12: optional, authenticated, bounded model proposals through existing interfaces. No Android, Gemma, owner memory or signing changes.
- Read `AGENTS.md`, the experiment contract/register, current Issue #41, selected model config and existing DeepSeek tier/hub scripts before implementation.

## Intended executable slice

Inspect the existing provider/mentor transports, reproduce missing privacy/security and capacity guarantees with negative tests, then extend the existing interface. Pin DeepSeek model/revision identity; require per-job opt-in, authenticated HTTPS, explicit request/output/deadline/cost bounds, sanitized failures and an offline fallback result. Prove only protocol behavior against a local fixture; a fixture is not DeepSeek inference.

The capacity plan must calculate from 48 shards / 510296708312 bytes at revision `1bae0e1088993b9b82de08f59de5be5a34e42170`, expose byte/GiB and bandwidth/time assumptions and distinguish MoE active compute from full weight/offload residency. Hardware names alone are not a verified serving recipe.

## Evidence and limits

- Initial checkpoint: source inspection only. New implementation tests `NOT_RUN`.
- Existing resources only; no paid GPU/API, service creation, large model download or application to capacity programs.
- Actual DeepSeek inference, serving throughput, GPU/DRAM topology and phone integration: `NOT_RUN / BLOCKED` by missing authorized model-serving compute.
- No raw private prompts, tokens or responses in committed evidence.

## Next entry and rollback

Inspect `.github/continuum/local-coding-brain.mjs`, existing backend mentor pairing/transport and tests, then record failing regressions before implementation. Revert this branch's commits to remove the prototype; remote teacher remains opt-in and offline behavior must remain available.

## Capacity/Hub checkpoint

Reproduced capacity defects (7 red cases): unpinned/approximate defaults, GPU names treated as verified requirements, absent transfer assumptions, unsafe integer arithmetic and other valid-looking revisions accepted. Hub negatives reproduced selection through mutable HEAD and a fetch ignoring AbortSignal exceeding its configured deadline. Raw failures: `docs/evidence/sol-deepseek-20261026/{capacity-red,hub-red}.txt`.

Implemented exact selected revision and 48-shard/510296708312-byte inventory validation; byte/GiB and 100/1000/10000 Mbps transfer estimates at explicit 80% utilization; storage policy identified as a local assumption; runtime memory/topology/offload remain unknown. Immutable `config.json` read-only inspection reports 384 routed experts / 6 active per token / 1 shared expert; no active parameter count inferred. Authenticated serving still needs measured runtime/weights. No download authorization is synthesized from GPU names.

`node --test scripts/continuum-deepseek-tier-plan.test.mjs scripts/continuum-deepseek-hub-preflight.test.mjs`: **16 PASS**, raw output `capacity-hub-green.txt`. No inference, resource creation or weight download.

Actual coding seam is `.github/continuum/coding-provider.mjs` -> `scripts/continuum-local-brain-cli.mjs` -> `queryLocalCodingBrain` in `scripts/continuum-local-brain.mjs`; the earlier intended `local-coding-brain.mjs` filename does not exist. Backend Groq mentor already has separate authenticated pairing and per-request consent, but is text-only and not a DeepSeek coding bridge. Reuse the existing canonical proposal/repair contract, not that route's tool permissions.

## Transport unit checkpoint

New `scripts/continuum-deepseek-bridge.mjs` is disabled by absence of an explicitly supplied endpoint/request/consent/key. It shares the existing pure coding request/schema and proposal parser extracted from `continuum-local-brain.mjs`; local inference stays fixed-loopback. `node --test scripts/continuum-coding-brain.test.mjs .github/continuum/coding-rejection-feedback.test.mjs`: **12 PASS** after extraction.

Bridge contract unit tests: **8 PASS** (`bridge-unit-green.txt`; initial absence `bridge-red.txt`). HTTPS only, no URL credentials/query/redirect, pinned model/revision, exact prompt/body/job/source/deadline digest consent, no owner-memory/secrets/training permission, zero-cost declaration, preflight identity/privacy/capacity before POST, byte/token/time bounds, one POST/no retries, fixed sanitized errors and `KEEP_OFFLINE_LOCAL` fallback. Capability declarations and response identities are authenticated server assertions, not independent weight/inference or financial proof.

The first direct Node live metadata attempt failed `BLOCKED_HUB_METADATA: fetch failed` (raw `pinned-hub-live-first-failure.txt`). No inference/weights were attempted. The managed runtime uses inherited HTTP proxy/CA settings; Node's environment-proxy support must be enabled for network metadata requests in this executor.

`coding-provider.mjs` is concurrently owned by the coding-discovery task for durable reservations. Coordinate its minimal optional remote seam with that owner before editing. Real local TLS integration and existing CLI entrypoint still `NOT_RUN` at this checkpoint.

## Connected provider/TLS checkpoint

Coordinated with coding-discovery. Its reservation commit `216fa837ee8d40b8bb5c32b7ca374d21045a0553` was cherry-picked as `31331293` only to build/test the remote seam safely; preserve the original commit when integrating and do not duplicate it. The handoff did not exist at this branch's base, so the cherry-pick retained its document. No repair-ledger/loop changes were adopted.

The optional `remoteTeacher` argument on the EXISTING provider requires an owner-facing exact-prompt consent callback, scoped server credential and external canonical job evidenceDir. Consent is checked before durable reservation/CLI. The shared 1–3 request allowance, source/task identity, resume/crash reservations and independent repair gates remain the same. `coding-remote` is an explicit mode on the EXISTING CLI; local mode never switches to remote implicitly. Proxy/CA trust is preserved for HTTPS; unrelated credentials are withheld. Remote stdout/error identities are rechecked and failures never select another provider.

- `provider-remote-red.txt`: four failing tests demonstrated the absent remote seam (not an observed production data leak).
- `node --test .github/continuum/coding-provider-remote.test.mjs .github/continuum/coding-provider.test.mjs scripts/continuum-deepseek-bridge.test.mjs scripts/continuum-coding-brain.test.mjs`: **22 PASS**, raw `provider-bridge-green.txt`.
- `node --test scripts/continuum-deepseek-https.test.mjs`: **2 PASS**, real loopback TLS sockets, ephemeral trusted certificate, actual provider→fixed CLI→HTTPS→validated canonical proposal, durable replay budget refusal; wrong token/revision, absent consent, malformed stdin, untrusted TLS and stalled endpoint denied. `https-integration.txt`. Synthetic fixture only, no model/inference.
- Wider scoped local/coding/negative/Hub/capacity/TLS tests: **57 PASS**, raw `scoped-integration-green.txt`; a final config-identity linkage case added afterward awaits the next verification.
- Read-only live selected Hub metadata succeeds with `NODE_USE_ENV_PROXY=1` using inherited proxy/CA: selected revision, 48 shards / 510296708312 bytes, zero LFS digests advertised, **zero downloaded/verified bytes**, inference `NOT_STARTED`. `pinned-hub-live.json` supersedes the recorded first direct-fetch failure.

Full setup/protocol, provenance/train-audit reuse and missing-resource requirements: `docs/CONTINUUM-DEEPSEEK-BRIDGE-20261026.md`. No endpoint/backend deployment, Android UI/runtime test, native training, owner memory or signing changes. Missing resources: separately authorized no-charge compute/storage, verified full checkpoint bytes, supported pinned runtime/conversion/kernels/topology, authenticated operator endpoint with capability/revision/privacy/billing enforcement, owner-reviewed per-job integration. No financial or inference success inferred from an authenticated server assertion.

## Final focused verification

The provider now retains only whitelisted remote failure codes in its hash-only trace and `error.remoteCode`, while preserving the canonical `CODING_MODEL_UNAVAILABLE` code and explicit offline fallback. Malformed/untrusted stderr collapses to `REMOTE_UNAVAILABLE`; no provider text is copied. Capacity rejects fabricated alternate weight-byte counts under the pinned selected identity.

Fresh command on the final source content:

```sh
node --test scripts/continuum-deepseek-tier-plan.test.mjs scripts/continuum-deepseek-hub-preflight.test.mjs scripts/continuum-deepseek-bridge.test.mjs scripts/continuum-deepseek-https.test.mjs scripts/continuum-local-brain.test.mjs scripts/continuum-coding-brain.test.mjs .github/continuum/coding-provider.test.mjs .github/continuum/coding-provider-remote.test.mjs .github/continuum/coding-rejection-feedback.test.mjs
```

**59 PASS / 0 FAIL**, `docs/evidence/sol-deepseek-20261026/scoped-final-green.txt`; `git diff --check` PASS. Runtime Node `v24.19.0`, OpenSSL `3.5.7`. Full repository Node and Android/native suites are not claimed for this checkpoint; root owns integrated verification. Actual model inference, capacity/runtime performance, owner UI/device/training remain `NOT_RUN / BLOCKED`.

## Owner stop / secured continuation

Owner requested STOP at the reported 20% session limit. Development and tests ended; no further experiments/full suites were started. Final verified implementation commit: `a1d62830967b8a223eaa064d1b02fdd7eb2f8abb` (59 focused tests PASS immediately before commit; source unchanged afterward). This closure is documentation only. Draft PR: https://github.com/Ullmann27/device-ai/pull/154 . No unreviewed integration/merge.

Integration order: `2aa878c16f0333d7531b6e2958c2b28247fe319c`, `f0d05435`, `7599d525`, original canonical reservation `216fa837ee8d40b8bb5c32b7ca374d21045a0553`, then `4a606b3c211a9b32584fc058c6fbb075840b9e77`, `a1d62830967b8a223eaa064d1b02fdd7eb2f8abb` and this closure. **Skip duplicate reservation cherry-pick `31331293`** when the original is already integrated. Root must source-review and run combined checks; this branch claims only its focused coverage.

Process inspection found no running DeepSeek/TLS fixture/local-brain CLI process. No services, compute, paid API, device changes or owner-state mutation were performed. Branch remains accessible in the official private repository. Next AI should read this handoff and protocol, compare actual remote HEAD, review consent/operator-adapter requirements and measured compute blockers; do not infer real inference from the local protocol fixture.
