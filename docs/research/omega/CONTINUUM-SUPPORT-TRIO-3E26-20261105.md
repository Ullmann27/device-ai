# Continuum additional support trio — source-bound handoff

Status: WIP / INDEPENDENT_NODE_BASELINE_EXECUTED / NOT_RELEASED.

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

## Independent execution checkpoint

Exact #543 source was tested: 2,397 Node cases / 2,360 PASS / 0 FAIL / 37 SKIP, exit 0; 327 original files, 154.8 seconds. Both existing machine locks held; tracked source unchanged. Initial backend failed (57 PASS / 7 setup failures, missing Express). With available runtime dependencies the backend passed 64/64; express-rate-limit 8.7.3 differs from locked 8.7.1, explicitly not exact-lock release evidence. Full original Node TAP is archived at `docs/evidence/continuum-support-trio-3e26-20261105/node-full.tap.gz`, with exact facts and limits in RESULT.json and README.md.

The active primary team is #544, dialogue #545, graphics #546. It owns production repairs; this team supplies independent tests/reviews. Its reported AvatarExpression.WARM compile mismatch remains outside the Node result. Android, GPU, audible TTS, identity recognition, original APK and Fold7 have not been validated by this team.

After completed executions the temporary checkout and evidence directories disappeared from the executor. The previously uploaded Node archive was recovered into this branch. Backend raw logs and the local launcher were not uploaded before that incident; do not claim they are preserved. Backend counts/hashes are recorded tool observations only. Continue from this remote checkpoint, recheck the actual available environment and primary team's exact head before further testing.
