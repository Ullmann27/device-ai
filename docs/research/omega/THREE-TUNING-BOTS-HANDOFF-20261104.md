# Continuum: three tuning bots — 2026-11-04

## Owner scope
Exactly three helpers optimize existing components only. No new features, repair scope, cross-branch integration, avatar creation, model growth, training, main merge, release, installation or provider activation. Frontier superiority is an unproven ambition, not a result.

## Frozen source and coordination
- Repository: Ullmann27/device-ai.
- Base: PR #491, branch codex/astra-jarvis-takeover-20261104.
- Base commit: e101e393dd6c80803ac9d9ac91fccd9812c4a18d.
- Base tree: 72e62267ba22309d60ad13abb3e2a5c6a155e544.
- Dedicated branch: tuning/continuum-three-bots-20261104.
- Concurrent repair (#505), integration (#506), model (#507/#508) and research (#509) branches remain independently owned.

## Three real assigned helpers
1. tuning_model_inference: existing model/inference preprocessing, decoding and allocation work; EXP-05.
2. tuning_memory_context: existing retrieval/context selection, preserving ordering, scores, provenance and time/privacy semantics; EXP-06/12/14.
3. tuning_runtime_workers: existing worker/runtime/scheduler efficiency with unchanged authority, cancellation and job behavior; EXP-01/08/11.

## Acceptance
Freeze input and original source before each measurement. Compare identical workloads and outputs, keep raw samples and source hashes, and run relevant regressions. Prefer reduced repeated computation/allocation over relaxed quality or safety limits. A synthetic host benchmark is not end-to-end model, Fold7, battery, thermal or frontier-model evidence. Reject changes with unexplained behavior differences.

## Initial status
WIP / UNVERIFIED. Three agents have been started; read-only source inspection is in progress. New optimizations and measurements are not yet claimed. Results will be recorded here with exact commits, evidence and remaining limits.

## Persistence and rollback
All useful patches, reproducible measurement harnesses and evidence belong in this review branch. Preserve the original source and compare per-file changes. Rollback is a reviewed revert of the individual optimization commit, never a reset of another chat's branch. Existing owner data, model bytes, signatures, package identity, update channel, offline preference, ToolGate and training gates remain unchanged.
