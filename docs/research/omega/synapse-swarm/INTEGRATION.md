# Synapse integration inventory

Observed 2026-11-03; isolated integration `76cfebda994c158ae1156748d661f1f2ae1d5713`.
This combines PR392 `efe622dd` and reviewed MΩ host slices at PR410 `713949a`.
Prior counts apply to their recorded freezes, not this combined tree.

| Function | Actual symbol/path (under app/src/main/java/at/heinz/deviceai unless stated) | Product caller | Evidence and gap | Next change / owner / acceptance |
|---|---|---|---|---|
| Chat/context | AgentActivity.runLocal; cognitive/AiContextCompiler, BoundedAgentOrchestrator | Existing owner chat | Previously built; shared native runtime is not twenty bot sessions | Keep one local generation; bot context admission requires separate tested adapter |
| Specialist registry | chief/ChiefDelegationCoordinator.kt in PR384 `34543f9c` | No current Activity caller at baseline | Staging-only, in-memory, cap20; mutable collection aliases and rejected-submit cleanup independently fail | A builder imports and repairs only this contract; A challenger tests rights, restart, collision and paging |
| Worker lifetime | chief/ChiefWorkerPool.kt | AgentActivity.showChiefWorkerStatus; ChiefWiring; host workspace tests | Actual read-only diagnostic; queued shutdown loses reports, cancelled futures can accumulate behind uncooperative call | B builder fixes bounded executor/accounting; B challenger freezes cancellation/reentry/retention tests |
| Workspace | chief/ChiefWorkspaceWiring.kt; .github/continuum/ccc1-workspace.mjs | Host injected port; canonical CCC1 bridge | Independent host Docker evidence; no installed Android transport or persistent per-bot OS environment | Preserve canonical job/source/claim binding; profile workspace is association only |
| Coding authorization | ContinuumTypedCodingBridge, ContinuumMobileBuilderActivity, ContinuumMobileBuildResultReceiver | Existing reviewed task UI / receiver | MΩ accepted snapshot-bound one-shot/cancel changes; actual device not connected | Final combined Android tests; genuine model producer still missing |
| Coding model | scripts/continuum-local-brain-cli.mjs; continuum-local-model-observation.mjs | Optional owner-provisioned host path | Native process observation tests pass; tier HOST_INTEGRATION; dummy output cannot certify neural inference | BLOCKED_LOCAL_MODEL_UNAVAILABLE, no label/flag workaround |
| Technical experience | .github/continuum/ccc1-technical-recall.mjs + canonical experience/journal modules | Existing CCC1 completion/recall | Current grant and canonical revocation rechecked; same source/file restriction | Re-run exact combined source; no changed-code learning or real model experience claim |
| Bot working context | cognitive/ContinuumState.kt FileContinuumObjectStore | New profile-store adapter (under review) | Reuse content-addressed objects, distinct namespace and author binding; no owner-fact admission | A builder/challenger; explicit same-UID trusted app boundary, not arbitrary-code isolation |
| Shared memory | learning/ContinuumLearningCore, LEX3 FileLearningExperienceStore; cognitive/HybridMemoryRetriever | Existing chat/learning callers | 4096 protected records; snapshot/cache tests on prior source | Preserve correction/revocation semantics; do not promote notes automatically |
| Communication | routines/ContinuumRoutines.kt AgentMailbox/RoutineBook | RoutineDispatcher and workspace reporter | Existing bounded ephemeral queues; no durable typed bot outbox/expiry/task-grant envelope yet | Reuse mailbox, defer persistent message/replay contract until execution path exists |
| Avatar | avatar/AvatarDirector, AvatarPoseReducer; avatar3d/FilamentAvatarRenderer; ContinuumHologramView | Existing runtime/TTS events | Optional real GLB/morph pipeline; original sphere retained; host contract/build proof, appearance/device acceptance open | One selected scene; no per-card model/render loops |
| Team UI | AgentActivity.showMoreWorkAreas / development center | New Team entry + ContinuumBotRosterDialog (WIP) | Native paged profiles/context/edit; cannot advertise absent execution/capabilities | Chief implements; third QA tests UI/async/restart and exact build |

## Architecture choice

One model per identity multiplies weight residency without a demonstrated benefit.
The preferred existing shared runtime retains one active generation and separate
request context. This is a policy choice, not a measured multi-bot LiteRT/KV-cache
isolation claim. Optional authorized host execution uses the same CCC1 task and
proof contract; it is labelled host, never Fold7-local.

Count separately: persisted identities, accepted active tasks, actual model/tool
processes, and visible full-resolution avatars. Profile initialization invokes no
LLM, grants no tool and starts no computer. The initial catalogue is editable
metadata; roles/personality are not verified capabilities. The latest owner roster
(Atlas through Helix) supersedes the earlier draft seed list. Existing stored IDs
and custom names must not be overwritten by seeding.

## Honest current limits

`/workspace/continuum-build-env/android-sdk/platform-tools/adb devices -l` returned
an empty device list on 2026-11-03. Android version, installed package/version,
thermal/energy/audio/display behaviour are `NOT_RUN`. SDK target36 is a build
configuration, not a measured handset OS. No weights were found in workspace.

The first registry snapshot is bounded metadata with keyset paging, not an indexed
database or an unlimited storage guarantee. Private working objects are app-local;
neither directories nor matching requester strings authenticate arbitrary code.
Only trusted app callers use the context API. A persistent workspace identifier
does not establish a container, terminal or desktop stream. No new network grant,
microphone listener, production weight update or owner-information sharing occurs.

M0/S0 can be accepted after exact integration tests. M1/MΩ-1 remains blocked until
genuine approved local model inference produces an unknown-task patch through the
canonical executor and independent verification. Twenty persisted profiles alone
do not complete M2/S4. Model-based E1–E3 and personality E5 remain `NOT_RUN`.
