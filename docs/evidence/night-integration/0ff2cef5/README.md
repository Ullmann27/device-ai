# Local integration evidence — 0ff2cef5

**LOCAL_GATES_PASS / PUBLISHER_BUILD_RUNNING.** Tested application source: `0ff2cef550db8fc84c32690793921edf3a7830e9`, branch `release/continuum-night-integration-20261103`. This evidence-only checkpoint was assembled after the tests; it does not change the source identified by their receipts.

The original publisher deployment `dep-dbl70arm8hqs73ch40pg` on service `srv-dbf1tr2vcj2c73ehpfog` was reported running this exact source by the integration coordinator. Its signed output and live code40 catalog have **not** been verified by this local evidence bundle. A later publisher/readback receipt must establish those facts separately. No duplicate deployment was requested for evidence assembly.

## Results and original evidence

| Gate | Total | Passed | Failed/errors | Skipped | Evidence |
|---|---:|---:|---:|---:|---|
| Android/JVM including native-widget Robolectric and independent review fixtures | 2501 | 2487 | 0 | 14 | [441 original XML reports](android-junit.tar.gz), [build log](android-log.txt), [suite index](android-suite-summary.json) |
| Full Node | 2306 | 2269 | 0 | 37 | [Original XML](full-node.xml), [compact report](full-node-summary.json) |
| Critical Node subset rerun | 135 | 135 | 0 | 0 | [Four original XML reports](critical-junit.tar.gz), [compact report](critical-summary.json) |
| Backend | 64 | 64 | 0 | 0 | [Original XML](backend.xml) |
| Fresh Kotlin backlog roundtrip rerun | 1 | 1 | 0 | 0 | [Original XML](roundtrip-junit.xml), [log](roundtrip-gradle-log.txt), [complete fixture receipt](roundtrip-receipt.tar.gz) |

Critical and roundtrip reruns overlap the complete suites and must not be added as unique coverage. Android counts were recomputed from all 441 original XML files; Node/backend counts were also recomputed from original XML. [SUMMARY.json](SUMMARY.json) records the aggregate result and scope. The full Node and critical runner reports both record `sourceUnchanged: true`, exact source commit, and runner `treeDigest` `3691bfda273d37d41fccda705b8a88982fada49330434197bd08fb761bf59f14`. This is the runner's source-input digest, not a Git tree object ID; the latter is recorded separately in SUMMARY.json.

Configured **NewApi** lint completed with zero issues: [original XML](lint.xml) and [text](lint.txt). `app/build.gradle.kts` limits this gate to NewApi; this receipt does not claim every possible lint rule was run.

The release APK and AAB built successfully using the unsigned local path: [identity/hash receipt](unsigned.json), [build log](unsigned-log.txt), [APK badging](unsigned-badging.txt). The APK is 84,294,137 bytes, SHA256 `77f41a5e82f55e8c376faba0765b53ec415c5690eed2f3730609e576367cb27a`; the AAB is 45,680,955 bytes, SHA256 `a9b86f26462551af6957c7acf60fedc0fb308a9863a564de7dd9d9aa78c5e117`. Both contain arm64-v8a, armeabi-v7a, x86 and x86_64 native libraries. APK badging confirms package `at.heinz.deviceai.continuumlocal`, versionCode `40`, versionName `0.3.39`, compile SDK `36`. These are **unsigned** artifact hashes; they do not identify the future signed download.

## Parity and the preserved failed invocation

[parity-final.json](parity-final.json) preserves the successful cross-runtime checks:

- Backlog: fresh Node preparation, actual Kotlin test, then one Node verifier invocation; revision14.
- SWB2: 350 rounds, 700 updates, 4113 weights within 1e-8; recorded SWB cross-runtime maximum error `1.3877787807814457e-17`.
- CRE1: seven inference cases and SGD parity across 526,336 weights; maximum error `3.469446951953614e-18`.

The backlog verifier mutates/consumes its fixture while applying the next operations. An inadvertent **second invocation** against the already-consumed first fixture returned exit1 with integrity/revision/state mismatches and invalid transitions. Its unchanged receipt is [parity-second-invocation.json](parity-second-invocation.json). This was not discarded or relabelled as PASS. The final backlog receipt instead binds a fresh preparation, a fresh actual Kotlin test and exactly one verification. [roundtrip-receipt.tar.gz](roundtrip-receipt.tar.gz) preserves preparation, Kotlin JUnit/log, the before-verification snapshot and the resulting fixture. These are synthetic fixtures, not owner data. Do not run the backlog verifier twice on the same completed fixture.

## Skips and host limitations

All **14 Android** and **37 Node** skipped cases, original XML reasons and source/report identifiers are preserved in [SKIPS.json](SKIPS.json) and [SKIPS.md](SKIPS.md). Empty or literal `true` reasons are retained as written by the runner. They are not passing cases.

Android skips comprise six explicitly pending ChiefPattern scenarios, six explicitly pending FirstThinkingRun scenarios, and two opt-in real workspace/Docker proofs. Node skips cover explicitly gated JVM/SDK/Docker/independent harnesses and unavailable host kernel/isolation prerequisites. The production resource, isolation and authorization policy was not weakened to make unavailable proof pass. The critical subset has no skips.

These tests ran on a Linux x64 host with JVM/Robolectric and the local Android toolchain. Native-widget tests exercise actual Android view/activity code under Robolectric; they are not physical Fold7 acceptance, on-device GPU/Filament rendering, voice/acoustic validation, battery/thermal measurement or proof of an installed owner upgrade. No model inference or autonomous Android ARM64 build/isolation success is established. The consumed failed S01 real-model attempt was not repeated, and its unresolved crash/resource barrier remains intact. The host coding service and its read-only MCP surface do not establish a ChatGPT connection or on-device coding milestone.

## Reproduction inputs and integrity

[commands.json](commands.json) records exact original Node argv/cwd from the reports and the Android, unsigned, backend and fresh-roundtrip commands supplied by the integration coordinator. Actual original paths remain in receipts. The three fixture preparation commands are explicitly labelled reproduction inputs derived from the exact-source CLI; [parity-prepare.log](parity-prepare-log.txt) preserves preparation output. [original-inputs.json](original-inputs.json) records the full report hashes, original copied-file hashes and synthetic parity fixture hashes.

Use the exact tested source in an isolated checkout with JDK17, SDK36/build-tools35 and Gradle8.11.1. [run_gradle.py](reproduction/run_gradle.py) and [night-ui.init.gradle](reproduction/night-ui.init.gradle) are the original external harnesses. The wrapper reads inherited proxy variables but contains no literal proxy credentials. The independent Kotlin source list and hashes are preserved in [independent-tests-source.json](reproduction/independent-tests-source.json); copy each repository `source` to the external `independent-tests/<name>` directory and verify its SHA256. Rebase original workspace paths consistently if reproducing elsewhere, prepare fresh synthetic parity directories, and use the recorded commands. No S01/model/service execution is required by this reproduction scope. Do not interpret this documentation as authorization to replay that consumed case.

Original full runner JSON reports contained very large `source.inputsSha256` maps. The compact reports omit those maps and per-case hash lists, retain source commit/treeDigest and execution metadata, and link exact commands separately. Original report identities before compaction:

- Full Node JSON: `9890141c85c1ba068c1659865d2f1513569e083d95e4600bfee3f6435b493407`.
- Critical JSON: `a498c11db08bea353c7cd7eccc754e40a5c103eab97baa2e8b65c31a0ef4f24c`.

Original XML/log/receipt contents are copied byte-for-byte. Raw logs use `.txt` filenames so repository ignore rules cannot omit them; original names remain in the input records and command receipts. Tar members are sorted with uid/gid0, empty owner/group, mode0644 and mtime0; gzip mtime is0 with no filename field. [archive-members.json](archive-members.json) records original member hashes, checked after archive creation. [MANIFEST.sha256](MANIFEST.sha256) covers all bundled files except itself. Generator metadata such as `generatedBy` is preserved from the original runner and is not a claim of a new third-party review.

## Existing Download button and stored-data compatibility

[update-compatibility.json](update-compatibility.json) records the read-only comparison from the documented code39 source `a9709854f48d2fd6589f9ce5ea97f6ec3be0d233` to this candidate. All five updater Activity/Receiver/catalog/downloader/signature-policy source files are byte-identical, with hashes preserved. The old button still requests `https://continuum-owner-v23-release-preflight.onrender.com/continuum-local/latest.json`. Its exact 12-key schema1 catalog requires `OWNER_RELEASE`, approved true, the unchanged package, and the original certificate SHA256 `6354e91dc0ca8df9173885f32402fd817f46e798f369206d4fd710ff3d03ee23`. A code40 catalog is greater than code39; APK URL/hash/length, same allowed HTTPS host and certificate checks must still pass. The final signed bytes and live catalog need separate publisher verification; Android installation remains the owner's device action.

The existing bootstrap preferences namespace, local-model directory, runtime/backlog state paths and learning ledger entry path remain in use. Bot profiles/private conversation, readiness and avatar appearance storage are additive. No source-level applicationId change or wholesale application-data reset was found. This is a source/test assessment; no actual owner file was read or modified during this work.

Behavior changes are explicit:

- **Voice default:** an absent `voice_enabled` preference now defaults to true; an explicitly stored false is preserved. Both `ContinuumFirstVoiceUiTest` cases are included and passed in the full Android run.
- **LEX2 to LEX3:** protected-record overflow above256, or the legacy byte limit, can trigger segmented storage during later app writes. The primary `experiences-v2.bin` becomes a LEX3 manifest; segments live under `experiences-v3`. Original valid legacy bytes are retained as `legacy-v2.bin` (additional distinct originals receive hash-qualified names), and interrupted commits fail safely. This is not a blanket migration performed by installing the APK. An old code39 reader cannot consume the new manifest after conversion, so **lossless APK downgrade is not promised**.
- **Owner fact classification:** historical hypothetical, planned or uncertain statements can be demoted from confirmed/learned/replay-pending to OBSERVED while their content remains stored. They are then withheld from confirmed-fact answers. Additive storage does not mean every old classification remains unchanged.
- **Tool receipt retention:** the format/path remains; when the 512-entry cap is reached, the oldest certainly terminal entries may be evicted. Pending or outcome-uncertain entries remain protected; if they alone fill capacity, the store fails closed. Indefinite retention of every old terminal receipt is not promised.

Existing migration/transfer tests are included in the full Android run: `Lex3LearningExperienceStoreTest`20/20, `LearningExperienceStoreTest`4/4 and `PersonalLearningTransferTest`11/11, all without skips. [migration-test-cases.json](migration-test-cases.json) lists all35 case names and their original report association. Specific original-copy/atomicity cases include `protected257thRecordSurvivesRestartWithoutLosingLegacyBytes`, `restartAfterInterruptedMigrationKeepsOriginalThenSafelyRetries`, `terminatedWriterBeforeManifestCommitLeavesOldCompleteState`, `uncertainFailureAfterCommitLeavesCompleteNewStateAndIdempotentRetry`, `interruptedLaterWriteCannotUndoCommittedRevocation` and `missingOrCorruptCommittedSegmentsNeverFallBackToLegacy`. These synthetic tests support those contracts; they do not inspect, migrate or guarantee the state of the owner's device.

## Next gate

Preserve the final original publisher build result separately, then verify downloaded bytes, package/version/source identity, original signing certificate, strict catalog and the existing Download URL. No main merge, key replacement, automatic installation or S01 replay is part of this checkpoint. See the [integration handoff](../../../research/omega/night-integration/HANDOFF.md) for current state and explicitly historical checkpoints.
