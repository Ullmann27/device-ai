# Continuum device experience implementation plan — 2026-11-04

Status: WIP / UNVERIFIED. Owner explicitly authorized completing the audited repairs, the reference design, integration and the existing original-signer update using parallel teams, without repeated questions.

## Goal and architecture
Make the existing Android app expose working persistent bots, the already requested luminous reference cockpit, and honest state-dependent capability/speech feedback. Reuse the one existing Activity, model, avatar, backlog and update publisher. Preserve owner data and original package/signature. This plan implements the findings in the 2026-11-04 owner screenshots and the existing REFERENCE-COCKPIT-HANDOFF-20261104.md.

## Global constraints
- Repository: Ullmann27/device-ai. No main merge, force-push, branch deletion, owner-data reset or automatic device installation.
- Package at.heinz.deviceai.continuumlocal; original certificate 6354e91dc0ca8df9173885f32402fd817f46e798f369206d4fd710ff3d03ee23; existing Render publisher srv-dbf1tr2vcj2c73ehpfog and Download catalog.
- Code41/a8e58ae09f6f1a8f43bd0a6f3b30b7e81bb79be6 remains the current release until a strictly higher, fully gated release is verified.
- Preserve the consumed failed S01 barrier; no model/service replay, resource-limit weakening or claims of full phone autonomy.
- EXP-05 presentation/local runtime reporting; EXP-11 canonical specialist identity and state; EXP-12 persistence and continuity. No parallel memory, execution or permission system.
- Existing local design eac0c365 and pending modifications are inherited WIP, not prior accepted work. Preserve the original checkout; work only in dedicated worktrees.
- Real regression tests exercise storage and caller/UI behavior; no fake inference, source-grep-only proof or numerical readiness percentages.

## Review focus
- Android system-managed aliases above filesDir must not be confused with unsafe links inside the bot store.
- Corrupt/unsupported existing registries remain untouched; a failed seed/write must retain a precise non-private diagnostic.
- A responsive cockpit must keep real destinations, complete touch targets, readable large text and correct home/chat transitions.
- Capability answers must reflect current readiness without starting models, granting tools, leaking private bot context or claiming host proof as device proof.
- Speech no-match must be distinct from permissions/language availability and cancellation; callbacks must not revive a stale turn.

## Team 1 — bots and storage
Read TEAM-BOTS.md. Reproduce the audited path/diagnostic failure, implement a bounded storage-root contract and accessible diagnostics, verify persistence/legacy schema/adversarial links and real roster caller behavior. Commit source/tests/evidence; coordinator publishes and integrates.

## Team 2 — reference design and avatar
Read TEAM-DESIGN.md. Continue the inherited candidate, fix its known compilation error and real geometry/navigation failures, validate phone/open-Fold/large-font layouts and avatar invariants. No unrelated model changes. Commit source/tests/captures/evidence; coordinator publishes and integrates.

## Team 3 — capabilities and speech
Read TEAM-CAPABILITIES.md. Replace the static self-description on the real owner-chat path with an honest live snapshot; make speech errors actionable and accurately classified. Preserve existing approvals and cancellation. Commit source/tests/evidence; coordinator publishes and integrates.

## Integration and release
- [ ] Preserve inherited WIP in a published research checkpoint and open draft PR.
- [ ] Implement all three lanes with focused failing/passing behavioral checks.
- [ ] Independently inspect each lane and reconcile shared Activity changes.
- [ ] Run immutable combined Android/widget/Node/backend/lint/build gates; retain skips and original failures.
- [ ] Inspect actual phone/Fold captures, functional navigation and the packaged rig/material hashes.
- [ ] Publish only through the existing original-signer path with a fresh higher versionCode; verify live manifest, package, signer, bytes and hash.
- [ ] Record exact source, raw evidence, physical-device limits and continuation. No blanket claim that every historical research milestone is complete.

Rollback is a forward reviewed code change and higher-version original-signed release; no downgrade/data-reset promise.
