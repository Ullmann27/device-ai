# Cinema: ten-worker checkpoint

Integration owner: root. Canonical branch `cinema/fluid-light-stage-20261110`, draft PR715. PORTIERT: production changes below. VERIFIZIERT: targeted source/host/native tests. GERAETEBESTAETIGT: NOT_RUN. Every worker used a distinct branch. Ten workers were scheduled in waves; at most six ran beside root. No main merge, signing, release, installation or branch deletion. Protected PR706 remains17491f4d8103f0e55836369333fab07e80e6e9c2.

## Integrated behavior

- Actual dock/tile native press state drives contact-position lighting and depressed glass. Hit rectangles remain stable. CANCEL, lost visibility, pause and detachment clear pending native input and pressure. The background retains its independent thermal freeze; no new animation clock.
- The native clicked control supplies the one-shot work-window origin. A fixed origin survives intentional deck hiding; changed viewport/source geometry is revalidated. Later voice navigation cannot reuse an old click.
- Existing idle/work layer weights now fade ocular offsets and gaze stabilization. Head, neck, jaw, visemes, original21-target GLB and its materials remain unchanged.
- Old/foreign TurnEnded events cannot close the tracked mission. Navigation STOP/reset, queued cancellation, delayed boot cues and synchronous bus reentry have actual Presenter checks.
- Already-constructed result columns consume their rank delay before sampling the settled pose. Draft content no longer remains at0.843878 alpha. Both real drawing and projected-gaze callers use this settled pose.
- Compact module glyphs fit existing row spacing; large-layout18dp glyphs, text, status data and hit areas are preserved.

## Workers and accessible checkpoints

| Worker | Scope | Published source / review | Integration |
| --- | --- | --- | --- |
| hollywood_flow | Native pressure / control origin | 0f1ff95c, cinema/control-material-pressure-20261110 | Production + tests in715 |
| avatar_timeline | Ocular continuity | 0aa3344b, fix/avatar-idle-light-audit-20261110 | Production + tests in715 |
| brain_lab | Exact PNG inventory / portable capture runner | PR732,6234c9b | Helper + evidence in715 |
| cognitive_budget | Event ownership / stale end guard | qa/cinema-event-review-20261110 | Production + tests in715; native fixture corrected by root |
| memory_skills | Independent layout review | PR729,cfdac5ea | Review retained; compact finding implemented |
| performance_verification | Independent pressure review | 912ddfa6, review/cinema-perf-measurement-20261110 | Review in715 |
| original_avatar_render | Original GLB replay | PR730,0ed9c0e1 | Exporter,5PNGs and source manifests in715 |
| compact_material_layout | Compact glyph spacing | PR731,59a1dfd8 | Production + tests in715 |
| entrance_choreography | Settled result opacity | PR734,19c07ed5 | Helper + both runtime callers in715 |
| thermal_visual_guards | Composed thermal boundary checks | PR733,5dfb7d29 | Tests/evidence in715; no policy weakening |

## Exact validation

Tested production tree `46e8fbd3cf913f11c2d150830e89502f41bc5528`. Main/test Kotlin compilation passed.

Initial native run onbb4da2a:110 tests,99 PASS,11 fail in shared test-window attachment before behavior;37 PNGs,35 with matching provenance. Two legacy crystal sidecars were missing.

Only two test fixtures changed afterward. Native window traversal fixes the Presenter setup; the visibility assertion and all production guards remain. Crystal fixtures now emit source metadata. Latest run onfac2485:19/19 PASS,0fail/error/skip,2/2 bound PNGs; includes all11 Presenter cases plus8 window cases already counted. Production main tree is identical across both runs.

Therefore110 unique targeted tests pass across the two runs, not118 and not a single110-test green run. Full receipts retain the initial failures. See docs/evidence/cinema-ten-workers-20261110/. Earlier38-test pressure milestone and41/47/33/9/3 host receipts overlap and are not added.

Original GLB SHA256: c8b88385c73fa01596a5734d20859440b1ecaef06cb1abea6cd7a47e62b08b9a. Independent export comparison verifies64 pose samples, unchanged non-eye channels, and zero ocular discontinuity at the sampled state boundary. Five actual BlenderCPU32-sample512x640 PNGs include a previous/current identical-time pair. These are studio-lit pose replays, not Filament app rendering.

Deliverable `Continuum_Cinema_PNGs_20261110.zip` preserves42 PNGs:35 native Activity frames,2 isolated window fixtures and5 original-avatar Blender replays, with source metadata. Archive hash is in docs/evidence/cinema-ten-workers-20261110/images-bundle.json. Native inputs are scripted; apparent local-inference labels are fixture UI text, not a model execution claim.

## Remaining acceptance

Physical Fold7, hardwareAGSL/Filament, measuredGPU/frame-time/thermals and actual microphone/TTS/lip-sync remain NOT_RUN. This is an integrated source checkpoint, not completion of the full visual brief or a signed release. Additional header/secondary controls, document/research work surfaces and on-device visual comparison remain review targets.

P2 remains open separately: cognitive budget PR717, controlled lab PR718, memory/skills PR719 and natural dialog PR720 (91 targeted checks atde831d06). No research runtime was silently activated.

Continue the existing checkpoint rule: commit/push each coherent verified step, mark unfinished work, keep branches/PRs accessible, do not merge main or sign without owner approval.
