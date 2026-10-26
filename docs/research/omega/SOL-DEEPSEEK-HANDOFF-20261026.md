# DeepSeek authenticated bridge — 2026-10-26

Status: `WIP / UNVERIFIED`; research-only, no model inference.

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
