# NAVADA / Astra — live integration handoff

Date: 2026-11-04. Status: **IN PROGRESS**. This first checkpoint preserves the requested work before implementation.

## Repository and instructions

- Official repository: `Ullmann27/device-ai`.
- Pinned base: `6cb9e760f0a16a6f34a8442abf9b79dcf2705799` from PR #502.
- Working branch: `feature/continuum-navada-live-integration-20261104`.
- Root AGENTS, current-state/frontier handoffs and experiment contract were inspected. Current owner authorization requests this isolated integration on the newer pinned base; historical release-freeze statements do not authorize modifying or merging a release branch.
- Plan: `docs/superpowers/plans/2026-11-04-navada-live-integration.md`.

## Actual starting point

The checkout already contains the native Filament renderer, strict GLB validator, 21-target mapper, portrait framing, a single view-owned frame clock, speech director and Android camera input. The supplied snippets are proposals, not proof of completion: the new AvatarAppBridge body is empty, the sphere generator is not an anatomical head, and force-reset worktrees would risk existing work. Existing implementations are the integration points.

## Tests and evidence at this checkpoint

- Repository cloned and exact pinned HEAD verified; clean initial working tree.
- Blender 4.3.2 and Java 21 are present. Gradle/Android SDK and focused Kotlin test tools are being checked.
- New code tests, new control render, Android build, native app screenshot/video and physical Fold7 test: **NOT RUN** at this checkpoint.
- No APK installed or signed. No main/release merge.

## Next steps

Read actual asset provenance and contracts; render the baseline; repair verified motion/session defects; implement consent-bound camera observations; connect through existing Activity and UI. Record real output rather than inferred success. Preserve brain/orbit layout and shared job/memory infrastructure.

## Rollback

This documentation checkpoint changes no app behavior. Future functional commits can be reverted individually using `git revert <verified-commit>`. The pinned base remains available; do not reset or clean another agent's working tree.
