# Overnight owner APK integration — 2026-11-03

**Current status: LOCAL_GATES_PASS / PUBLISHER_BUILD_RUNNING.**

Exact tested application source: `0ff2cef550db8fc84c32690793921edf3a7830e9`, branch `release/continuum-night-integration-20261103`. Original publisher service `srv-dbf1tr2vcj2c73ehpfog`, deployment `dep-dbl70arm8hqs73ch40pg`, is building that source. The guard-environment update started this existing deployment; no second deploy is needed. This checkpoint does not claim a verified live code40 release, original signature on newly downloaded bytes, or a device installation. Root preserves publisher/readback evidence separately after the deployment completes.

## Current exact-source evidence

Durable repository evidence: [local receipt and scope](../../../evidence/night-integration/0ff2cef5/README.md), [machine summary](../../../evidence/night-integration/0ff2cef5/SUMMARY.json), [file integrity manifest](../../../evidence/night-integration/0ff2cef5/MANIFEST.sha256), [exact commands](../../../evidence/night-integration/0ff2cef5/commands.json).

| Completed local gate | Exact result |
|---|---|
| Full Android/JVM, widget and independent review tests | 2501 total; 2487 PASS; 0 FAIL/errors; 14 SKIP |
| Full Node | 2306 total; 2269 PASS; 0 FAIL/errors; 37 SKIP; sourceUnchanged=true |
| Critical Node subset rerun | 135 PASS; 0 FAIL; 0 SKIP; sourceUnchanged=true |
| Backend | 64 PASS; 0 FAIL; 0 SKIP |
| Configured NewApi lint | 0 issues |
| Original-package unsigned APK/AAB | Built; package at.heinz.deviceai.continuumlocal, code40, name0.3.39; hashes preserved |
| Cross-runtime parity | Fresh backlog Node→Kotlin→Node, SWB/SWB2 and CRE1 verification PASS |

Critical and fresh roundtrip reruns overlap full-suite coverage; do not add them to a unique total. All original Android XML is in a deterministic archive, full Node/backend XML is preserved, and compact report summaries retain original full-report hashes plus exact source commit/treeDigest. The exact [skip list](../../../evidence/night-integration/0ff2cef5/SKIPS.md) remains part of acceptance. No APK, keys or owner data is added to this evidence directory.

The backlog verifier consumes/mutates its fixture. An inadvertent second call against the earlier completed fixture failed; its original [failure receipt](../../../evidence/night-integration/0ff2cef5/parity-second-invocation.json) remains preserved. A fresh Node preparation and actual fresh Kotlin roundtrip, followed by a single verify invocation, produced the [final parity receipt](../../../evidence/night-integration/0ff2cef5/parity-final.json). The before/after synthetic fixture, preparation and native JUnit/log are retained. Do not rerun verify against an already-consumed fixture and interpret that as a source regression.

## Integrated behavior and update compatibility

PR410 reference geometry, avatar/chat/readiness/camera/effects/planning and lifecycle guards remain integrated with PR456 persistent bot selection/personality/private conversation/delegation and roster access. Normal layouts retain the PR410 composition; enlarged fonts/narrow windows use the adaptive layout. A single HomeView intendedGeometry drives home/chat transitions. Chief/JARVIS status remains available during active bot work without switching the selected bot or submitting another model task. The new geometry, large-font and busy-status UI regressions are part of the passing full Android run.

The read-only code39→candidate review is recorded in [update compatibility](../../../evidence/night-integration/0ff2cef5/update-compatibility.json). The five updater source files remain byte-identical to documented code39 source a9709854f48d2fd6589f9ce5ea97f6ec3be0d233. The old Download catalog URL, exact schema, package and required original signer remain unchanged. Candidate code40/name0.3.39 is higher; final live catalog and signed bytes still require publisher verification.

Existing preferences/models/runtime/backlog paths remain in use; bot/readiness/avatar data is additive. An absent voice preference now defaults on, while a saved mute choice is retained. LEX2 overflow can later migrate the learning store to a segmented LEX3 manifest with original legacy bytes retained; an old APK cannot read that new manifest, so no blanket lossless downgrade promise is made. Existing hypothetical/uncertain owner statements can become OBSERVED rather than confirmed facts without deleting their content. At receipt capacity, oldest certainly terminal receipts can be removed while pending/uncertain records remain protected. No owner files were inspected or changed by this source review or evidence assembly.

The full Android result includes all35 existing learning migration/transfer tests: Lex3LearningExperienceStoreTest20, LearningExperienceStoreTest4 and PersonalLearningTransferTest11, all PASS/no skips. Their exact original-copy, interrupted-migration, atomic-commit, revocation and export cases are listed in [migration-test-cases.json](../../../evidence/night-integration/0ff2cef5/migration-test-cases.json).

## Boundaries and next step

Physical Fold7, GPU/avatar likeness, voice/acoustics, thermals, actual device upgrade, native ARM64 autonomous build/isolation and real-model success remain unverified by these host receipts. The consumed failed S01 real-model case was not rerun; its unresolved crash/resource barrier is unchanged. No main merge, key replacement or installation was performed. The host coding service/read-only MCP surface is not proof of an established ChatGPT connection or a completed on-device coding milestone.

Continue with the existing publisher deployment only. Preserve its exact-source result and verify the downloaded APK's package, higher versionCode, original certificate, hash/length and strict old-button catalog. Keep publisher outcome separate from the completed local gates and retain any failure literally. Evidence-only documentation commits after 0ff2cef5 do not retroactively change the tested application-source identity.

---

## Historical checkpoints — superseded by the current status above

The following original checkpoint text is retained as history. Its WIP labels, environment availability, preliminary counts, old catalog observations and future-tense next steps describe earlier times in this integration, not the current state.

Status: WIP / UNVERIFIED. No new build, test or publication is claimed by this checkpoint.

## Owner request and exact inputs
The owner explicitly requested autonomous continuation without questions, integration of available app/brain/agent work, reference-based design continuation, and an original-signed update through the existing application's Download button. Do not merge main, replace keys, change package identity, migrate owner facts, or install on the device.
- App/avatar: PR410, `250b8a29fea799f39a995dc1758fd92bf3e11693`.
- Local coding service: PR456, `546e056648d04a2d084f3c1cd99d725a35496156`.
- The failed consumed S01 case must not be rerun; preserve its crash/resource barrier.
- Existing package: `at.heinz.deviceai.continuumlocal`.
- Original signer SHA256: `6354e91dc0ca8df9173885f32402fd817f46e798f369206d4fd710ff3d03ee23`.
- Existing publisher: `srv-dbf1tr2vcj2c73ehpfog`; verify current release metadata before selecting the next strictly greater versionCode.

## Execution and verification plan
1. Compare both exact source heads, read current closeouts and original source gates, and integrate on this isolated branch while preserving all existing provenance and barriers.
2. Reproduce incomplete combined test results on the exact integrated source. Diagnose production defects and stale harness expectations separately. Use local/Render verification; do not trigger GitHub Actions.
3. Continue existing UI/avatar connections using the owner's references, retaining current layout, colors, typography, orb fallback, owner/developer options and opt-in permissions. Do not equate host renders with native device output.
4. Build using the existing original signing/publisher path only after required runnable source gates pass. Verify package, versionCode, signer, APK hash, source SHA and old download endpoint.
5. Record exact results, all skips and unavailable device checks. A downloadable owner candidate does not establish Fold7 end-to-end acceptance or a solved autonomous coding milestone.

## Parallel audit lanes
- Design references and exact existing visual gaps (read-only).
- Release/publisher/QA-host audit (read-only).
- Source ancestry, integration gaps and failed combined QA investigation (read-only).
Chief owns source integration, new branch publication and any release action.

## Current environment
Fresh working checkout is being acquired. GitHub connector and public git read succeeded. Local Node/Java are present; Android SDK, Gradle cache and Docker are not yet verified available. No native model execution is authorized or started.

## Continuation
Read the latest version of this document and both upstream handoffs before acting. Preserve exact failing runs. Never infer successful device execution from a synthetic test, a UI effect, a saved receipt or an earlier source's result.

## Integration checkpoint (2026-11-03, overnight continuation)

The two exact owner-provided heads have been merged locally. The newer PR410 avatar rig, mouth mapping, head/eye motion, bloom and lifecycle checks are retained byte-for-byte. PR456 supplies persistent bot profiles, private conversations, delegation, the independent coding service and its read-only MCP surface. The consumed S01 fixture is not replayed; the unresolved execution barrier is not changed or cleared.

Normal phone/Fold layouts retain the PR410 reference composition. PR456's adaptive cards are connected only for enlarged fonts above 1.3 or narrow windows from 240 to below 320 dp. A single HomeView geometry selection is shared with the Activity so chat/home transitions cannot switch adaptive views into the reference portrait renderer. The same hologram/director remains in use.

Verification so far is preliminary and is not the final combined-source acceptance:

- Toolchain archives were SHA256-verified; SDK36, build-tools35, JDK17, Kotlin2.4 and Gradle8.11.1 are available.
- Actual reference geometry probe failed against the automatic layout merge and passes after reconciliation.
- 13 pure reference/adaptive geometry and element tests passed.
- 37 focused layout/navigation source-wiring tests passed.
- Independent integration reviewer reports 43 focused wiring checks passed; this scope overlaps some layout wiring checks and is not added into a unique aggregate count.
- Initial native Android product compilation and 39 targeted native UI/lifecycle tests passed with 0 failures and 0 skips. Subsequent geometry/status/large-font regression additions still require the final combined-source run.
- Resource suite: 15 cases, 11 PASS, 4 explicit SKIP, 0 FAIL on this host. The independent test-only capability probe verifies real kernel prerequisites; production memory/CPU/isolation enforcement is unchanged. Missing success-path kernel/Docker proofs are not model or host acceptance.
- The existing Docker workspace integration probe now scopes its observations to its own canonical workspace-job labels, avoiding unrelated parallel containers. Actual Docker execution is unavailable here and remains NOT_RUN.

The existing original release endpoint still serves code39 / 0.3.38 from a9709854f48d2fd6589f9ce5ea97f6ec3be0d233. The configured publisher branch is an ancestor of this integration, so a tested commit can be fast-forwarded with an exact expected-head lease. Auto-deploy is off. No service settings, signing keys, installed data or main branch have been changed. Code40 is the next candidate, subject to a fresh live-catalog check immediately before release.

Final acceptance must bind the combined immutable source: full Node/backend checks, Android JUnit including independent UI fixtures, lint, original-package unsigned build, existing original-signer release pipeline, and downloaded-byte/package/version/certificate verification. Physical Fold7, exact video likeness and the failed S01 operating proof remain separate unverified items.

The independent review additionally found a status-routing bug during active bot work. The existing read-only Chief/JARVIS status branch now runs before bot conversation routing and can answer without changing the selected bot, cancellation binding or occupied model lane. Four real Activity/worker regression cases were added. Five real avatar navigation cases cover adaptive phone, wide and portrait layouts, narrow windows and the reference portrait. The adaptive action label now reserves a measured gap below its icon at the largest font size. These additions remain pending native execution at this checkpoint.

## Initial full run and final host-test reconciliation

Combined app tests completed:2501 total,2484 PASS,0 FAIL,17 SKIP; all15 new adaptive/navigation/status cases passed. Configured NewApi lint0 issues, backend64/64 PASS. Three parity skips have now received deterministic Node/Kotlin fixtures for the final run. No production app source changed after09dced7a.

Full Node on immutable09dced7a was RED (2267PASS/15FAIL/23SKIP). Its original evidence is retained, not overwritten. The stale streaming assertion was corrected against the actual output-budget/cancellation loop. Fourteen native host proofs now have independently measured capability gates plus an always-on real refusal assertion; they remain explicit SKIP on this host. Scoped host contracts41total/23PASS/18SKIP/0FAIL and streaming5/5PASS. The release/QA scripts and all production model/security enforcement remain unchanged. Next step: fresh complete Node/critical and Android/parity gates on the next immutable checkpoint, then unsigned and original-signed release verification.
