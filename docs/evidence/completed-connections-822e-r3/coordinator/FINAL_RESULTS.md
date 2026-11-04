# Completed connections: final host acceptance

Status: **ALL_REQUESTED_HOST_GATES_PASSED**, with explicit skips. This is source/host acceptance, not a device or release approval.

## Exact tested source

- Draft PR: https://github.com/Ullmann27/device-ai/pull/527
- Tested published commit: https://github.com/Ullmann27/device-ai/commit/80c01939e8a1fdd04cfa9a818c352be494bc1719
- Local equivalent: `191ee80fe2a916f2a8e4041df386bfa29a6a03b0`
- Identical full tree: `2defed4632b02a78478f9ecc1e910557181343cf`
- C1 history included through `13c37f55789bf67b07beeb37cc1a5035526e623e`.
- Source remained unchanged during every completed gate. The following closure commit adds only documentation and original evidence; it does not substitute a new runtime source.

## Actual results

| Gate | Passed | Failed/errors | Skipped | Result |
| --- | ---: | ---: | ---: | --- |
| Complete canonical Android Unit/UI, 535 fresh XML suites | 3,032 | 0 | 19 | PASS |
| NewApi lint, fresh analysis/report | 0 issues | 0 | — | PASS |
| Critical Node | 135 | 0 | 0 | PASS |
| Full Node | 2,362 | 0 | 31 | PASS |
| Backend, 13 test files | 64 | 0 | 0 | PASS |

Groups overlap and must not be added. Android has 3,051 total cases; full Node has 2,393 total cases. All 11 required Android suites and all five minimum-case requirements passed without skips. The 12 Reference cockpit cases, three added avatar ownership/lifecycle cases and 40 credential/privacy cases passed inside the complete Android run. An independent per-case recount agrees with every Android XML header and finds no duplicate Android case identity.

The real local SDK/JDK/Kotlin toolchain executed the canonical Gradle Unit/UI and NewApi tasks, with no class filters. The existing standalone QA flags use versionCode 41 solely as a compilation/test parameter. Critical and full-node use the existing repository QA runner and a real Kotlin compiler. Backend tests use a local mock service, not live API inference. Exact command arrays, environment/tool identities, original XML/TAP/logs and before/after source hashes are in the archive.

## Connections delivered

| Area | Attached behavior |
| --- | --- |
| Memory #522/#529 | Source-bound contradiction privacy and atomic event accounting; recognized credential values redacted before field framing; explicitly sensitive memories take privacy precedence. Existing incremental byte budgeting, recall observation and complete evidence framing remain. |
| Bot practice #516 | 18 private Kora/Link/Iris exercises use the existing preparation, generation and accepted-completion flow. Feedback persists in V3 history and appears live and on reopening; ordinary history writes remain V2, readers accept V1/V2/V3. |
| Worker/Termux #515 | Existing branch discovery, bounded worker loop and awaited trusted-source preflight are connected. Discovered work remains PROPOSED/BRANCH_REVIEW; existing authorization and durable boundaries remain. |
| Avatar #521/#524 | Completed packaged reference GLB with matching hash, NOTICE and provenance; stale docking callbacks and live-surface ownership are guarded; minimum touch targets stay at least 48 dp. |
| Active Shell | Native Compact/Kugel choice reuses the same surface, persists its choice, and feeds measurement, hit geometry and painting. Systemstatus → Mehr reaches existing work areas and the coding draft. |
| Measured connection repairs | Compact reflow no longer activates overlapping head/orb regions; a short wide window retains its actual chat rail; large-font archive dialogs retain both native actions. Existing #523 sampling, byte-budget and backlog optimizations remain. |

The final Node-only adjustment preserves strict checks for the shared layout profile, the unchanged default Reference branch, default-false Compact and complete flag propagation. It changes no Android production source or Android acceptance case.

## Preserved negative evidence

Earlier failures are retained with their original source identities; none are relabelled as passing:

- `first-run-evidence.tar.xz`: initial combined Android 15 failures.
- `second-run-evidence.tar.xz`: canonical combined Android eight failures.
- `ui-followups-evidence.tar.xz`: original focused follow-ups and source-specific intermediate success.
- `third-run-node-guard-evidence.tar.xz`: complete Android/NewApi success on prior source, the one stale full-node source assertion, and intervening focused negative evidence. Its summary remains GATE_FAILURE_RETAINED.
- `final-acceptance-evidence.tar.xz`: the fresh passing common source recorded above.

Final archive SHA-256: `ec890dc9c3a9118ca9b0710d8753c1dbb0e028c9fc1e1057a948c47f48b14304`; 728,864 bytes; 578 original files. The adjacent manifest contains a SHA-256 and byte count for every archived file. The archive was reopened and every entry verified before publication.

## Explicit exclusions and follow-up

- The 19 Android skips are 12 existing PENDING Chief scenarios, two explicit host-proof opt-ins, three missing model/parity fixture cases and two JDK21-only cases on this JDK17 host. These are not accepted as executed.
- All 31 Node skip records remain in the original JUnit and the independent recount. They include unconfigured Docker/SDK proofs and unavailable native isolation/kernel prerequisites. No live worker or native isolation proof is inferred from the successful aggregate gate.
- No physical Fold7/Filament GPU acceptance, live model inference/quality/training claim, complete live Termux job, owner APK assembly, signing, release, installation or main merge is included.
- The later corrected-head candidate and #526 WIP remain outside this intake. The previously documented independent rig-window checker disagreement remains unresolved by these host tests.
- Unterminated credential values are safely rejected; a dedicated understandable Activity error message is still a separate caller task. Existing stored records are not reclassified automatically.
- Once practice feedback is used, a rollback must retain the V3-capable history reader or provide a reviewed migration. This source work did not alter owner app data.

## Continue or roll back

Use the published tested commit and its exact original receipts before any device/release work. The closure changes are evidence only. Revert an individual bounded R3 change to withdraw its slice; preserve both parent histories, previous receipts and the compatible history reader. Follow the repository's existing owner/device/release gates.
