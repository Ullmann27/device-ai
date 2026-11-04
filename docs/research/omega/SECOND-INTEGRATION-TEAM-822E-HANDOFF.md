# Second integration team — verified combined source, 4 November 2026

Status: **SOURCE_INTEGRATED / TARGETED_ANDROID_HOST_PASS**. Three actual helpers completed the bounded connection round. The coordinator combined their work, imported the already verified speech callback test, and executed the checks below on one clean frozen source tree.

This is a review branch, not an installed APK or device acceptance.

## Source of truth

- Review: [PR #512](https://github.com/Ullmann27/device-ai/pull/512).
- Branch: `integration/continuum-followup-connectors-20261104-822e`.
- Frozen parent #506: `8abea828833d8833d40e3d3d36feaa6d3685b82e`.
- Parent product base #491: `e101e393dd6c80803ac9d9ac91fccd9812c4a18d`.
- Actual local combined test commit: `377ac199b1e360cecefbe1e04bf43e68e94695b9`.
- Published equivalent source: [`52fbfc624c9a2846c1da571d330327c7874b1639`](https://github.com/Ullmann27/device-ai/commit/52fbfc624c9a2846c1da571d330327c7874b1639).
- Complete local and published source trees are identical: `2be53764ed72e00e780c7b87ff1401c195ac0a50`.
- Subsequent handoff/evidence commits change documentation only. The source subtree identities are recorded in the coordinator receipt.

Owner scope: connect existing completed components. No new model research, replacement architecture or visual redesign.

## What was combined

| Source | Actual integration |
| --- | --- |
| #506 | Retained its complete personality, task, memory-recall and immutable worker/routine scope integration. Its main Activity already calls the existing memory recall observer and the canonical owner task pipeline. |
| #505 at `4ddbcb092d955226bb83c2a10f7ac52bb34f59eb` | Imported the finished cockpit turn-lifecycle repair, calendar/overflow repair and early QA version preflight with their existing tests. All three production files match the reviewed #505 bytes exactly. |
| #495 at `0e2b9ea59e71255e36580d695b16220d1d2974c6` | Imported the existing three-case `AvatarTtsLiveConnectionUiTest` unchanged. Its three production avatar fixes were already in the product base and were not applied twice. |
| This coordinator | Reconstructed the exact published parent tree, combined the contributions, ran the 45-class Android-host selection and NewApi lint, repeated the 70 QA preflight checks on the final source, and published raw evidence. |

The three imported production corrections are in:
- `app/src/main/java/at/heinz/deviceai/ui/JarvisSequenceView.kt`
- `app/src/main/java/at/heinz/deviceai/learning/ContinuumMemoryRetriever.kt`
- `scripts/render-continuum-owner-continuous-qa.sh`

The cockpit repair prevents unrelated global/navigation/boot events from owning the work-card slot. A bounded history of the 24 most recently completed or displaced turns rejects their late callbacks. The live memory channel retains its existing separate handling.

Calendar repair affects the existing M2.9 learning retriever. The main chat still uses `cognitive.HybridMemoryRetriever`; this round does **not** activate the M2.9 retriever. Its known 44-query ranking result, 43/44 to 42/44 in the top three, remains an open acceptance finding. Calendar correctness is not reported as improved overall retrieval quality.

## The three helpers

| Helper | Delivered contribution | Local contribution commit |
| --- | --- | --- |
| `connect_bot_worker` | Verified existing owner-task, Android WorkerReport and immutable scope paths; 48 existing host-contract tests; no duplicate production connection | `2054e36fd68d86787eed48f87a385149f8a6368f` |
| `connect_memory_brain` | Exact #505 calendar/overflow source, 15-case regression class, existing reproduction script and bounded handoff | `cec5b4227f8485db8fc38151a0a6089bfe3ed4cf` |
| `connect_avatar_cockpit` | Exact #505 cockpit source and 11-case Robolectric regression class; separate read-only review of the coordinator's QA and speech-test imports | `aeb983e962ded6d0cb74c7c8f426792fdbc176a5` |

These are actual development helpers for this task, not evidence of three persistent bots running on the owner's device. The helper commits were integrated into this branch; their source does not remain only in the temporary checkouts.

## Executed verification

| Gate | Result |
| --- | --- |
| Original combined baseline app and test Kotlin compilation | PASS, Gradle exit 0 |
| Final combined Android-host/JVM selection | **305 tests / 45 classes; 0 failures, 0 errors, 0 skipped; exit 0** |
| Imported cockpit lifecycle tests | **11/11 PASS in actual Robolectric**, superseding the earlier #505 NOT_RUN status for these cases on this combined source |
| Imported real TTS-listener connection tests | **3/3 PASS** |
| Imported calendar regression class | **15/15 PASS** |
| Configured lint | **NewApi only: 0 issues; exit 0** |
| Final-source QA preflight and existing publisher/update contracts | **70/70 PASS; no failures/skips; exit 0** |
| Bot-helper existing Node contracts | **48/48 PASS; no failures/skips; exit 0** |
| Source integrity | Clean combined checkout before and after execution; exact local/published complete tree equality |

The 305 total already includes the 11, 3 and 15 subgroups; do not add them again. Earlier PR totals are also not additive to this run.

The 48 bot Node tests ran on local parent `e5c06e6`. The relevant `.github` source subtree is unchanged in the combined source; its identity is recorded below. These are host contracts with test doubles, not successful native-model tasks.

### Evidence and reproduction

- [Coordinator receipt, totals, class list and source hashes](../../evidence/second-integration-team-822e/coordinator/summary.json)
- [Complete Gradle command and log](../../evidence/second-integration-team-822e/coordinator/combined-gradle.log)
- [45 original JUnit XML reports](../../evidence/second-integration-team-822e/coordinator/xml)
- [Final preflight raw output](../../evidence/second-integration-team-822e/coordinator/build-preflight.tap)
- [Bot-helper raw host output](../../evidence/second-integration-team-822e/bot-worker/host-contracts.tap)
- [Evidence hashes](../../evidence/second-integration-team-822e/coordinator/SHA256SUMS.json)

Archived Gradle text logs remove trailing horizontal whitespace only. Values and line order are unchanged; the receipt records the original runtime-log hashes as well as the manifest hashes of the stored bytes.

The original build used an isolated copy of the already working Gradle cache, JDK 17, Gradle 8.11.1 and Android SDK 36. It ran offline with one Gradle worker and a 1-GiB daemon heap. The archived launcher records the invocation and expects its documented sibling toolchain layout; the normal project Gradle entry point can run the same explicit tasks and filters.

This is a selected integration gate, not a fresh execution of the entire regular unit suite. No test thresholds were relaxed, and no disconnected control from another source is presented as a new run here.

## Important remaining boundaries

- Robolectric exercises the real Android-facing classes on the host with simulated Android services. It is not a Fold7, Filament/GPU, audible TTS or visual-reference acceptance.
- The TTS fixture seeds an already accepted request and supplies synthetic PCM/callbacks. It checks the installed listener, scheduled production frame and shown view, not the complete speech submission/consent path.
- The unchanged GLB still has the separately documented `rig_hierarchy_orientation` acceptance failure.
- #510's unavailable live owner/device service and missing negative external CCC1/ECHO completion remain unresolved. The existing Android WorkerReport callback is a different path.
- No native model inference, APK assembly, release, signing, installation or main merge occurred.
- The independent QA import review noted the pre-existing omission of `CONTINUUM_MENTOR_SIGNING_P12_BASE64` from one signing-exclusion enumeration. This import preserves the existing enumeration; its new tests do not claim exhaustive coverage of every signing-variable combination.

## Progress-window reconciliation

The owner's requested recap concerned approximately 18:00–20:00 Europe/Vienna on 4 November.

- #493's twenty-personality implementation predates that interval. Its incorporation into #506 at 19:43 is the new connection in the requested interval.
- #495 published the real callback test, fixture clock correction and final 136-test receipt at 19:38, 19:47 and 19:58.
- #505 published QA preflight at 19:34, cockpit repair at 19:41, calendar repair at 19:47 and final evidence at 19:53.
- #506 published the combined bot/memory/worker source at 19:43. Its own initial handoff remains historical; this child PR supplies the fresh combined 305-test acceptance.
- #507 published real 16M checkpoint verification at 19:40 and the atomic marker/EOS/preflight helper package at 19:59. Its 109 software checks and host inference capacity do not prove trained model quality.
- #508 started the fresh diagnostic/intent study at 19:32; source-bound result publication continued after 20:00. Its published v1 result is NO_DEVELOPMENT_SIGNAL, with 8/24 small and 10/24 wider development answers and no final test evaluation or model promotion.
- #510 published 105 host checks, but 0 submitted live owner jobs; device service reachability remains blocked.
- #509 research and #511 tuning continued during this integration round. They are separate sources, not silently promoted into this combined app.

## Integration decision and rollback

This branch stays open for review against #506. Preserve every other team's branch and the owner's existing update/signing arrangement. Next acceptance concerns the remaining actual device/graphics/voice and external worker gates; successful host tests do not close them.

Each imported correction is independently reversible. Revert the corresponding source change on this review branch; there is no owner-data migration. Do not reset or force-push an active sibling branch. EXP-00/09 build contracts, EXP-01/02/08/11 canonical work contracts, and EXP-06/12 memory/provenance boundaries remain intact.
