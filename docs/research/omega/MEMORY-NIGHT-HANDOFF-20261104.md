# Memory privacy R4 — sequential continuation

Status: **Two component fixes host-verified; Android integration pending**. Existing #522 remains frozen for
C1 intake; this is a separate stacked specialist branch.

## Input and ownership

- Branch: `fix/continuum-memory-privacy-r4-20261104`.
- Public input: #522 `eadc72bac95f8b1a554a59e5baff624977b09f73`.
- Local equivalent: `61b961bfcd63cf572a06a7d5b31bf2fe622e7ae3`.
- Identical input tree: `187aab3fe44a94b9b01451a867589c8e967cc641`.
- Observed C1: #519 `a8ebdd9ec935949f5a1aeb1b5c8a6bffd37e3fc4`.
- C1-MEMORY-017 is reviewing #522; C1-LAYOUT-018 is reviewing #524.
- C1 owns shared Activity, factory, speech and worker integration.

The owner requests several verified steps while unavailable. Work remains
sequential, with no new subagents. Source and evidence are saved in this official
repository. An hourly continuation is scheduled from 5 November 2026, 00:30
Europe/Vienna; scheduled execution is not a claim of continuous execution.

## Bounded milestones

1. Reproduce the credential leak disclosed by completed tuning #511 R2:
   `authorization=Bearer abcdefgh` loses the scheme but retains the token.
   Inspect quoted credential fields and preserve nonmatching controls. Freeze
   regression inputs before repairing only `AiContextCompiler.redactSecrets`.
   Do not import the rejected R2 regex-cache candidate or alter appendSection.
2. Test privacy classification when sensitive and device information coexist.
   Current MemoryWritePolicy checks device status before sensitivity; verify
   the consequence through the actual compiler and proof-carrying memory path
   before making a minimal classification repair.
3. Validate the combined changes with mixed-source, repeated-use and bounded
   concurrent compiler cases; retain actual RED/GREEN and negative controls.
   Execute the appropriate Android gate under the shared locks when available.

New source scope is restricted to the two named methods, their tests and this
branch's evidence/runner. C1 can integrate frozen #522 independently. No layout,
avatar, model-weight, memory-schema, offline-setting or permission change.

## Execution and continuation

Exact new source commits, raw outputs and negative results will be recorded as
they are produced. No prior PASS transfers to a changed tree. The last #522
whole-suite result remains 2,638 total / 2,618 PASS / 1 FAIL / 19 SKIP; its
separate Activity 2/2 and NewApi 0 were bounded checks, not device acceptance.

Before another session acts, read the latest PRs, comments and this handoff.
Respect a live scope claim and the two existing Android build locks. No foreign
worktree/cache/process may be modified or stopped. The JSON state beside this
file records the latest durable milestone and next operation.

Revert only a failed own patch; preserve tests, negative evidence and owner data.
APK and device acceptance follow the common integrated software gates. No main
merge, signing, release, installation or paid-service operation is authorized
by this specialist checkpoint.

## Checkpoint 1 — actual RED and two repairs

| Run | Public source | Result |
| --- | --- | --- |
| initial-red | `b40a3d8061203bcab5be7c6846f6ad39cadca508` | 4 PASS / 16 FAIL / 0 SKIP, 20 total |
| credential-green | `03b1333d96d36eba5ffc9537a58b489e9c8d5ff5` | 35 PASS / 0 FAIL / 0 SKIP, 35 total |
| privacy-green | `5f9ea0e532d52f819bd69cc8a61189f5ee064474` | 53 PASS / 0 FAIL / 0 SKIP, 53 total |

Frozen tests precede both repairs. Initial failures comprise nine credential
methods and seven sensitive/device classification methods. The first repair
handles explicit Bearer/Basic credentials and paired quoted keys/values with
escaped quotes. Unterminated quoted credential values reject the compile
(`UNTERMINATED_CREDENTIAL_VALUE`), preventing partial misleading contexts.
The second repair gives SENSITIVE precedence over DEVICE_LOCAL_ONLY; the
56-case provenance/flag matrix and actual mutation/ledger logic are checked.
HmacSHA256 is a synthetic test-local signer, not AndroidKeyStore execution.

No existing record migration is performed. Callers must still set the existing
sensitivity flag truthfully. Unlabelled secrets and arbitrary authorization
schemes are outside this bounded recognizer; no universal secret detector is
claimed. Existing appendSection, event transport and privacy enums are unchanged.

Raw logs, immutable source/dependency hashes and commands:
`docs/evidence/memory-privacy-r4-20261104/focused-results.json` and its run folders.
Result groups overlap and must not be added. EXP-06 provenance and EXP-12 memory
continuity interfaces remain compatible; no serialized schema changes.

Protocol references used to define credential boundaries: RFC 6750 section 2.1
(https://www.rfc-editor.org/rfc/rfc6750.html#section-2.1) and RFC 7617 section 2
(https://www.rfc-editor.org/rfc/rfc7617.html#section-2). Explicitly labelled short
Bearer values must also be removed; Basic credentials contain an encoded value
after the scheme. Sources inform tests, not a claim of complete HTTP parsing.
