# Reference cockpit implementation — 2026-11-04

Status: WIP / UNVERIFIED. Owner explicitly asks to implement the six newly supplied cyan/violet JARVIS design references in the native app. Base cdfb5f752bfb038cfbca2662d3b7891f1205f220 includes released code41 source a8e58ae09f6f1a8f43bd0a6f3b30b7e81bb79be6 and the corrected capability assessment.

## Scope and acceptance

Implement luminous human avatar with visible neck/shoulders, preserve live head/eye/mouth event animation and the original Continuum core, compose legible dark-glass native controls for portrait phones and open Fold/tablet windows. Use the six owner images as visual direction; their model names, percentages, agent counts and activity labels are not measured data and must never be copied as live state. All navigation uses existing AgentActivity destinations and the canonical dashboard collector. Large-font and narrow-window controls remain accessible.

This explicit new owner design request supersedes the previous night-integration decision to preserve the old reference composition. It does not change functionality, permissions, data, model or signing boundaries. Relevant contracts: EXP-05 model presentation only; EXP-11 canonical worker/task state; EXP-12 persistent identity continuity. No second worker, memory or tool pipeline.

## Work split

Avatar/material/light/render lifecycle: progress_evidence. Layout/composition: reference_layout. Independent visual/function review: status_screen_review. Coordinator: styling, real action wiring, source checkpoints, build and release verification.

## Required proof

Read actual GLB/material and native renderer state before modification. Capture and inspect actual Android Canvas layouts at phone/Fold/large-font sizes. Preserve meaningful hit-target, navigation, lifecycle and no-fabricated-data tests; replace only superseded old composition expectations. A host Canvas image cannot prove Filament GPU appearance or physical Fold7 behavior. Run focused checks first; then immutable full publisher gates before any owner release. Recheck exact release source and catalog, original certificate, package, version, APK bytes/hash and Download-button endpoint.

## Boundaries and handoff

No main merge, no key replacement, no owner data migration, no automatic installation. Existing owner authorization covers preparing and publishing an original-signed update through the existing publisher after its required gates. The consumed failed S01 native-model barrier remains intact; no inference, replay or barrier clearance. Current release is independently verified code41 / 0.3.40. This checkpoint contains no implementation and claims no new test, design acceptance or APK.

Rollback: reviewed forward patch to UI/renderer sources and a higher-version original-signed build. Do not promise Android downgrade or restore owner data. Continue from this branch, record exact source and actual checks below, checkpoint to GitHub at meaningful stages.
