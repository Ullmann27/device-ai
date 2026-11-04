# Continuum app team — thirteen concrete assignments

Owner-directed implementation. Worker concurrency is three plus the coordinator. A queued row is not a claim that a bot has been created or is running. At this checkpoint seven named implementation bots have actually been started: 01–06 and 11.

| Bot | Assignment | Current evidence / status | Owned integration surface |
| --- | --- | --- | --- |
| 01 | Bot storage and roster access | Implemented; 21 storage and 6 real roster UI cases passed in its focused lane; integrated in PR478 | ChiefBotProfileStore, roster dialog, own tests |
| 02 | Original reference recovery and visual specification | Completed; six original references recovered privately; direct high-resolution references added by owner | Reference inventory and comparison criteria |
| 03 | Current capabilities and speech lifecycle | Implemented; 42 focused JVM cases passed; Activity regression supplied; integrated in PR478 | Speech adapter/session, capability reply, own tests |
| 04 | Reference cockpit, responsive layout and navigation | Implemented; 20 focused Android/Robolectric cases passed, real 2D fallback captures retained; integrated in PR478 | Home/reference UI, Avatar/Kugel/Kompakt selector |
| 05 | Real humanoid avatar, anatomy and rendering | Running; first actual renders show remaining anatomy differences; geometry refinement in progress | avatar3d, GLB/materials, geometry/render tools |
| 06 | Combined Activity wiring and usable composer | Running; Android compilation passed; real Activity behavior verification in progress | Exclusive AgentActivity integration, integration tests |
| 07 | App permissions, linked-service boundaries and cancellation | Queued for concrete combined source | Review and focused tests; fixes by explicit file ownership |
| 08 | Data, memory, lifecycle and regression review | Queued for concrete combined source | Independent privacy/storage/regression review |
| 09 | Build, original certificate and existing Render update route | Queued; coordinator has verified original publisher exists with autoDeploy off | Release route verification; no new paid infrastructure |
| 10 | Independent combined acceptance | Queued until integrated candidate exists | Fresh source review, exact-source acceptance matrix |
| 11 | Neon, light trails, orbit motion and event states | Running | Existing ContinuumTaskEffects and bounded motion primitives |
| 12 | Fly-in connected-app and document work surfaces | Queued after motion interface is fixed | Native work surface, bounded event adapter, no parallel authorization engine |
| 13 | Rendered animation preview and visual comparison | Queued after actual anatomy/motion integration | Reproducible preview tools, actual captures and visual findings |

## Required final outcomes

1. Avatar silhouette, facial features and materials are compared to the direct references using real renders, including speaking and rotated views. A material improvement alone is not a likeness pass.
2. Actual home, chat, agents, bots and task entry points work. The original orb remains available. Portrait/Fold/large-font composer controls remain reachable and preserve drafts.
3. App and document animation consumes accepted tool observations, not speculative model text. No invented account connection, fake scrolling activity, false progress, false summary or generic-finish success indication.
4. Cancel, background, session change, permission denial and stale callback cases stop obsolete visuals and operations within the existing boundaries.
5. Current capability questions do not start model inference, private bot work, sensors or new grants.
6. Combined source passes actual Android compile/tests/lint and the unchanged existing Render publication gates before an original-signed higher-version APK may be published. Downloaded package, signer, hash, byte length and source must be verified. Physical Fold7/GPU behavior is reported only with actual device evidence.

## Source checkpoints

- Draft PR: https://github.com/Ullmann27/device-ai/pull/478
- Recovered source: https://github.com/Ullmann27/device-ai/commit/000364f9dc1f875cdc83644a4b55945b5c21f6d0
- Storage, capabilities and cockpit integration: https://github.com/Ullmann27/device-ai/commit/2458e43bab37bfd74455ca7a59d45560762fda6d

Original signature, package, consumed failed S01 barrier, private bot boundaries and owner data are preserved. No main merge, force-push, automatic installation or outbound message is part of this development task.
