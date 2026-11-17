# APK 42 — stabilization and single release checklist

Status: WIP / NOT_RELEASED. Owner requests one clear APK path and repair of the actual build blockers.

## Frozen intake

- Common candidate: PR #544, `c69199cda71c4d561c2f4721ff5d8c3a7eeefcac`.
- Independently read GitHub tree: `db16767156bae99af8a41b3ef56e64b3670182ee`.
- Available local equivalent: `cdd9ed6b49b3da928f2b3f6cd8a4f8d2ef131b31`, identical tree; clean checkout observed.
- Existing publisher: `srv-dbf1tr2vcj2c73ehpfog`, latest API-observed live deploy `dep-dblbct6gekts73fte14g`, source `a8e58ae09f6f1a8f43bd0a6f3b30b7e81bb79be6`.
- Existing binary audit records original package `at.heinz.deviceai.continuumlocal`, version 0.3.40/code41 and original signer. This session has not yet reread the live catalog bytes.

## Actual outstanding evidence

1. Current-source targeted Android receipt: 48 tests, 47 pass, one failure in `OwnerFaceDialogsUiTest.narrowLayoutKeepsControlsReadableAndTouchable`; detached ScrollView drawing throws an attachment NPE. Reproduce before correcting the fixture; preserve readability and touch-target assertions.
2. Earlier full Node receipt on local `6c4150190b0d3ad5a148a21a6808962b44be2a4d`: 2360 pass, two failures, 31 skips. Both failures are in `fold7-portrait-reference.test.mjs`: stale source variable and English visible-label expectations. Verify current behavior before updating those contracts.
3. Execute full combined Android unit/UI, lint, Node, backend and release-identity gates against one frozen current source. Prior component results are not transferable.
4. Build an unsigned same-package release candidate with a higher code for original-host signing. Do not distribute a debug-signed or alternate-package APK as the owner update.
5. Read back the signed binary/catalog only after the original publisher completes. Physical Fold7, microphone, camera, GPU and enrollment results remain unverified until real-device execution.

## Scope and preservation

This is a serial release repair on #544, with no new feature-research chain. Preserve the existing C1/core integration, original avatar/orb, local dialogue, offline control, owner data, ToolGate and original update identity. No main merge, branch deletion, paid runner, secret publication or unapproved device installation. Other specialist drafts remain source candidates, not extra APK candidates.

## Next checkpoint

Archive exact baseline failures, minimal repair diff, executed commands, source tree, exit codes and preserved raw logs. Mark unavailable checks explicitly. Revert only this stabilization branch if needed; never reset another team's checkout.
