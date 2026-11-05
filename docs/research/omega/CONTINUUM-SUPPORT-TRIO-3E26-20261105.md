# Continuum additional support trio — source-bound handoff

Status: WIP / NEW_TESTS_NOT_RUN / NOT_RELEASED.

## Scope and coordination

- Repository: Ullmann27/device-ai; owner account and public visibility verified.
- Base: PR #543, 89ae402d88b80ca5c48ab241e20c0369b7db517d.
- Branch: support/continuum-dialog-layout-apk-20261105-3e26.
- Three actual delegated assistants: avatar/dialogue, graphics/layout, build/update.
- A concurrent primary integration team owns Avatar/TTS/Activity/JarvisShellView product changes (integration/continuum-avatar-complete-20261105, observed local checkpoint 2efde7458d3b777a2f828456815351929662a945).
- This additional team supplies independent regression tests, source checks and build assistance; it does not overwrite that team's work or modify the shared candidate.

## Planned independent checks

1. Utterance-bound measured PCM, known silence versus missing audio, STOP and new turn isolation; camera permission and enrolled-identity truthfulness.
2. Ambient focus/visibility/power-save recovery, disabled-motion behavior, detachment, and preserved C1 compact routing. New uniquely named tests only while product files are claimed elsewhere.
3. Current source/build prerequisites, same-package update contract, signer and version checks, then feasible local targeted gates under existing machine locks.

## Current environment findings

Full local source for #543 and Android SDK/Gradle caches were found in existing team workspaces. The new local checkout failed with ENOSPC (workspace volume 100% full); only this session's failed duplicate is being removed. Existing team files, models, locks and caches are preserved. Investigating a bounded isolated source checkout without copying existing Git objects or toolchains.

All new Android, Node, APK and physical Fold7 outcomes remain NOT_RUN at this checkpoint. Historical test counts do not transfer to new source trees. No main merge, paid execution, signing, publishing or installation. Package at.heinz.deviceai.continuumlocal, original avatar/orb, signing identity and update host are retained. EXP-00/05/06/08/11/12 interfaces remain unchanged.

## Recovery

Continue from the exact branch head and update this document with actual commands, source hashes, outcomes, independent review and remaining gates. Revert only this branch's individual changes; never reset another team's branch.
