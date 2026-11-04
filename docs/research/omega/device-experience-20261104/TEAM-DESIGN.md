# Team 2 — reference cockpit and live avatar

Status: WIP / UNVERIFIED at assignment. Base source: 337ab0195434c8657bf131bf431f4c391aacc28c (PR477).

## Requirements
Read AGENTS.md, docs/CONTINUUM-EXPERIMENTVERTRAG.md, relevant EXP-05/11/12 entries and REFERENCE-COCKPIT-HANDOFF-20261104.md. The requested visual direction is a luminous human avatar with visible neck/shoulders, cyan/violet light, legible dark-glass controls, and the existing live Continuum sphere. Preserve real gaze, mouth, head and speech-event animation and one canonical dashboard/worker state. Provide usable Chat, Agents/Bots, Tasks and existing owner-only developer destinations without inventing state, counts, model names or readiness percentages. Support narrow phones, open Fold/tablet and large font scaling.

## Recovery limits
The previously mentioned eac0c365 local design commit is not available from GitHub in this resumed workspace; PR476 and PR477 contain planning documents only. Inspect existing renderer and UI source before implementing. The earlier six images are not attached here: search existing checked-in reference evidence when useful, and explicitly distinguish design interpretation from verified image matching. Do not claim visual equality to unseen images.

## Ownership
Own app/src/main/java/at/heinz/deviceai/ui cockpit/home/layout/render files, avatar3d/FilamentAvatarRenderer.kt, direct visual/layout/avatar tests, and reviewed existing avatar assets only if necessary. Inspect actual GLB materials before changes; keep rig identities and event connections. AgentActivity.kt is coordinator-owned: provide the minimum precise wiring patch as a separate report. Do not edit speech, bot storage, permissions, model/runtime or release files.

## Acceptance
Compile actual Android classes, preserve navigation/hit targets/lifecycle, and capture real host Canvas layouts at phone/open-Fold/large-font sizes if tooling permits. Root prepares the Android environment. Label host Canvas separately from Filament GPU and physical Fold7; no fabricated device success. Reuse existing behavior tests and replace only expectations explicitly superseded by the design request. Preserve rendering resource lifecycle and low allocation update paths.

## Delivery
Use an isolated worktree/branch in Ullmann27/device-ai, checkpoint early, open a draft PR to fix/continuum-device-experience-resume-20261104 and push meaningful milestones. Never force-push or merge main. Write TEAM-DESIGN-RESULT.md, with exact test commands and evidence paths, unavailable acceptance and any coordinator wiring. No additional subagents. Return status, commits, PR and concise test summary.
