# Code47 release continuation

Status: WIP / NOT_RELEASED. Resumed from PR578 at
2dd1c281685b8f58bb38d902d71dc6628adca26f after fresh remote verification.
Branch: codex/continuum-code47-release-20261106. Target: PR578 candidate.

## Scope and baseline

Finish the existing owner-authorized Code47 integration and original-signed
update. Integrate the reviewed repairs in PR582–586, fix remaining concrete
failures, freeze one source and run complete mandatory QA. No new feature stream.
The prior c3d2 native run failed: 16 Android failures; lint/unsigned build not run.
Code46 remains live at source577f0931e7937ffcac02dc855a540f093facdb3b.

## Known remaining work

- Review both PR578 prompt/transcript and configured-identity defects.
- PR584 review: preserve leading and comma-separated additional actions.
- Finish current context, clock, research and actual workscene fixture failures.
- Run Node/backend, real SDK production/test compile, all JUnit/UI and lint.
- Build original-package ARM64 unsigned release, then existing publisher QA
  and original signing; verify served version, source, package, signer and hash.

## Boundaries and verification

EXP-02/07: regression repair and independent source-bound QA; EXP-05/12:
local inference, identity and context interfaces preserved. No main merge,
signer export, package change, permission bypass, new service or device install.
Original avatar/orb and model weights remain untouched. Physical Fold7/GPU,
audio, model-quality and thermal acceptance remain NOT_RUN.
Current continuation tests: NOT_RUN. Existing receipts remain historical.

## Resume and rollback

Read this branch's current HEAD and subsequent evidence, not a historical SHA.
Use the existing real SDK/cache after inspecting its environment. Keep the
combined source clean during QA; retain raw logs and exact exit codes.
Rollback is retaining the verified Code46 publication and reverting only this
isolated branch's changes. Never reset or overwrite another working tree.
