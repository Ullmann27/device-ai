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

## Integration checkpoint

PR582–586 exact heads are integrated. The existing uncommitted follow-up
fragments were recovered without altering their original working trees:
configured identity/PDF arguments and planning single-record wiring, transient
research labels, current context limits/clock tests, and real UI fixture setup.
These changes are WIP pending the full combined native run.

PR584 additional-action defect was reproduced against its actual parser:
9 JUnit tests / 1 failure. The same tests plus leading, inner, comma, semicolon
and joined-object cases now pass 9/9 after the bounded guard correction.
Raw compile/JUnit logs and exact source hashes are preserved under
docs/evidence/code47-release-continuation-20261106/pdf-review/.
The screen-read ephemerality case remains UNVERIFIED until combined QA.

## Final gate repairs (06 November, continuation)

The integrated production source is frozen at 6a6f885f952784c79b209ad39b20d14ccf014df8;
68155c3eaaa44b6164e2ad425f490e1a54143ae5 has the identical tree and adds original
Code46 release ancestry. The first original publisher run is blocked by its
mandatory Node contract check. No QA or signing protection has been changed.

- Node special: 23/23; critical: 135/135; backend: 64/64, source unchanged.
- Full Node: 2429 total, 2391 passed, 1 failed, 37 skipped. The single obsolete
  retainedOwnerTurns contract is updated to check actual owner/research bounds;
  focused contract suite passes 3/3. Full final-source rerun is still required.
- Screen STOP failed because the synthetic installed package had no resolvable
  launcher. Register the activity and launcher filter and assert its component
  before traversing the real confirmation, ToolGate and screen observation.
- Tool-effects caller uses the real Activity surface for the full UI path and
  pumps one host frame after each asynchronous actual runner boundary. Retain
  all arrival/departure/no-document assertions and all negative controls.
- All seven affected host UI cases pass together against the actual frozen
  production classes, Android resources and Robolectric. Controlled model and
  device port only; no physical device or model-quality claim. The polite PDF
  picker regression also passes without any model invocation.

Raw focused diagnostics, compile/JUnit logs, commands, test source hashes and
exit codes are in final-fixtures/. The previously ignored raw PDF RED/GREEN logs
are explicitly included now. The complete frozen native run and final publisher
QA, unsigned build, original signing and served-binary verification remain the
release gates. Current status remains NOT_RELEASED.
