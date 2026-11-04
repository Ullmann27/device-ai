# Memory privacy R4 — sequential continuation

Status: **Credential/privacy/framing fixes host-verified; current SDK rerun pending**. Existing #522 remains frozen for
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
   Do not import the rejected R2 regex-cache candidate. Keep appendSection byte accounting and atomic admission unchanged; see the observed framing failure below.
2. Test privacy classification when sensitive and device information coexist.
   Current MemoryWritePolicy checks device status before sensitivity; verify
   the consequence through the actual compiler and proof-carrying memory path
   before making a minimal classification repair.
3. Validate the combined changes with mixed-source, repeated-use and bounded
   concurrent compiler cases; retain actual RED/GREEN and negative controls.
   Execute the appropriate Android gate under the shared locks when available.

Source scope now includes the compiler-local fullUntrusted/appendSection processing order, alongside the two named methods, tests and evidence. C1 can integrate frozen #522 independently. No layout,
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

## Checkpoint 2 — holdout, measured optimization, actual SDK failure

The extra credential holdout initially yielded 5 PASS / 1 FAIL: a newly
introduced `x-token` regression. It was corrected before acceptance. The final
six holdout methods include 180 quoted-value variants and a long quoted field;
combined component runs then passed 59/59. Raw results are retained, including
the rejected behavior.

Nine sequential JVM processes (three variants, rotated order, three rounds)
measured the actual private production redactor. Each workload has 15 measured
samples, each 200 calls, following 300 warmup calls per process/workload.
No pattern cache was introduced. Medians on this host:

| Workload | Unsafe baseline CPU µs/call | First safe repair | Optimized | Optimized allocation B/call |
| --- | ---: | ---: | ---: | ---: |
| Plain short | 21.875 | 37.698 | 21.251 | 1,488 |
| Plain long, no assignments | 233.834 | 405.940 | 48.111 | 1,456 |
| Dense legacy assignments | 84.611 | 156.967 | 145.126 | 44,544 |
| Quoted explicit authorization | 29.923 | 84.077 | 85.860 | 22,240 |

The long plain case reduced CPU by about 88% and allocation by about 95%
against the first safe repair (29,880 → 1,456 B/call). Dense credential handling
remains slower than the unsafe baseline. Corrected outputs have identical
SHA-256 values across the two safe candidates for all four workloads. This is
not whole-app, phone, battery, model-latency or complete-context performance.
Measurement source: public cda3f0b44163ee6115b2a25197516186dca44842. The private
redactor body remains byte-identical after the later ordering repair, SHA-256
14cda176aed810d383ee2101e00dfa40226ee237f3bf5e8da9c895fc0c306ccf.

A real clean SDK/UI run on public 36a7fe1fe0a69d62427e1cac6b32326cf8b9c071
(local e510ce5) compiled the app/test sources and executed 92 tests across 14
classes: **91 PASS / 1 FAIL / 0 SKIP**. The actual Activity/main-looper pair
passed; the new combined privacy/credential/orb case failed its unchanged
negation-preservation assertion. Lint was NOT_RUN because Gradle stopped after
the test failure. Nineteen raw files are sealed in android-framing-red's
archive and SHA-256 manifest. Earlier busy-lock and disk-reserve attempts remain
explicitly NOT_RUN. Only own reproducible build artifacts were removed after
verifying their already-published evidence; no foreign worktree/cache/process
was touched.

## Checkpoint 3 — raw field boundaries preserved

Observed root cause: display sanitization converts a newline to `[U+000A]`.
A later unquoted credential scan then consumed that display token and the next
word, including `Never`. A quote could also be closed in a different memory or
research field when redaction ran over the assembled group.

Eight new frozen regressions produced **1 PASS / 7 FAIL** before repair. The
compiler now redacts each raw untrusted field before display sanitization;
three already-processed evidence groups are not redacted again. Their fields
all pass through fullUntrusted; literal labels/enums/hashes do not carry raw
unprocessed evidence. The whole-section byte calculation and admission rule
are unchanged. Current public source:
**a717658b91c2f9703d8b6ab36bcdc7eb914cc589**, local
ce1a4be00ae551df8e7a6d2799e161b30d961034, tree
6facb6326bc37efc2b40bcec4502105ffd62268a. **67/67 actual-source host tests pass.**

A separate **26/26 host bridge run** combines these freshly compiled source
classes with the unchanged SDK-compiled hub, mapper, renderer and tests from
e510ce5. Exact source comparisons, classpath order/fingerprints and command are
recorded in framing-bridge-host. It exercises the real event path with manually
driven scheduling; it is not a fresh SDK/Activity/GPU result. The renewed SDK
attempt on ce1a4be was blocked by the shared build lock, so acceptance is pending.

C1 received the scope expansion and actual failure before this repair (comment
6643177228 on #519). The original failing integration assertion was not weakened.
The current common compiler at C1 c65604c still has the old credential code;
apply this delta carefully alongside its already-adopted #522 changes.

## Caller integration and next operation

C1 alone owns the common Activity and other entry points. Its current generic
failure handler catches `UNTERMINATED_CREDENTIAL_VALUE` and clears busy state,
but displays an internal model-error code. C1 has been asked to map this to a
short credential-format clarification. ChiefBotConversation.prepare already
clears its pending reservation in the existing exception handler. These are
source-trace findings, not full phone lifecycle acceptance.

The sensitivity flag is not an automatic classifier: existing callers and
persisted records are not migrated; ownerFactMemories currently still assigns
DEVICE_LOCAL_ONLY. No hidden owner-data or permission change is included.

Next: run the source-bound SDK gate with the same 14 classes plus
ContextCredentialFramingTest (15 classes), UI mode and NewApi lint, under both
existing locks. Then publish complete raw receipts and give C1 the final source
and evidence. No prior PASS transfers to a later source tree. The parent #522
remains frozen; C1 has independently passed its two slices (23 each) and the
separate #524 layout slice (7), without a combined end-to-end or APK claim.
