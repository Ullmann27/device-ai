# Completion team checkpoint — work in progress

Date: 4 November 2026, approximately 20:00 UTC. PR #517 remains a draft. This checkpoint persists verified patches and current failures while the Android test source is still frozen. It does not establish an APK-ready result.

## Exact baseline and responsibilities

- Source: `20f888660a967aa1d6b5446e892540538372d319`; full tree `ce1baced5aa5fa1c9c6648ef5dae84c831f806d3`.
- Build helper: full Android compilation, ordinary unit tests plus the optional UI source set, and lint. The first Gradle daemon exited unexpectedly; the resumed run compiled successfully, reported two UI failures and then stopped producing test/render progress while its JVM remained active. Diagnosis is in progress; no completed full-suite result is claimed.
- Regression helper: the complete 322-file Node run and the disjoint 25-file tool run, followed by individual failure classification and minimal test/QA fixes.
- Integration helper: finished branch review, negative ECHO review, and obsolete UI source-contract diagnosis against the later documented design.

## Observed baseline results

| Run | Tests | PASS | FAIL | SKIP | Meaning |
| --- | ---: | ---: | ---: | ---: | --- |
| Main Node/shell suite | 2342 | 2273 | 32 | 37 | Finished; stale contracts and actual prerequisites are separately diagnosed |
| Disjoint tools suite | 296 | 219 | 72 | 5 | Finished; 38 isolation-unavailable failures remain explicit; memory/brand fixtures are being repaired |
| Backend, all 13 files | 64 | 64 | 0 | 0 | Finished after installing declared dependencies; source and lockfiles unchanged |
| Root's two existing source-contract files | 11 | 11 | 0 | 0 | Corrected static contracts only; original baseline had two failures |
| Negative ECHO package | 9 | 9 | 0 | 0 | Real host Git/journal/grant fixtures; no model or Docker inference claim |
| Avatar signed-zero boundary | 2 | 2 | 0 | 0 | Actual Kotlin class; original new upstream code had one ArrayIndexOutOfBounds failure |

These are different scopes and source combinations. They must not be added together or represented as one completed final regression result.

## Reviewable prepared changes

1. Finished #513 at `28b228698aef8c52e46c72b71fdd5e1915d6e76c`: 21 code/test/tool files fetched at immutable source, every Git blob hash verified. The five production deltas cover speech callback admission, the actual audio-frame caller, preserved original-time reattachment cues and bounded cosmetic motion. The upstream 352-test report is upstream evidence, not our full-suite result.
2. Root found `AvatarIdleMotion.update(-0.0f)` indexing -1. A two-test actual Kotlin/JUnit reproduction failed once before and passed twice after normalizing both signed zeros before binary search. The attached patch applies after #513 and includes its regression test. This is a boundary defect; an ordinary device session hitting it is not claimed.
3. Four-file negative ECHO package from #515 `572e12ecc4ce5f7c79f2634a5b5773047b6d674f`: baseline canonically reached ABSTAINED but lacked `error.botReport`. With the package, all nine upstream negative tests pass. Original strict-model rejection and canonical assignment/journal validation remain required. Branch-discovery/scheduler changes are not part of this package.
4. QA signing-material guard: the missing `CONTINUUM_MENTOR_SIGNING_P12_BASE64` variable was independently reproduced. The production line matches finished #505 `84cd8cfc05dedef5d4feb28ecfa103530e95f7ff`; the prepared 43-case preflight covers all six variables in both modes. Never combine this finding with the claim that any signing material was accessed: only synthetic canaries were used.
5. Existing UI wiring contracts are being migrated to their actual current owner handlers, reference/shell layout and preserved live avatar. Root's final patch requires #513's actual audio-frame forwarding. Four earlier disconnection/branch mutations and one additional missing-frame mutation each fail the intended test. Static checks do not replace Android runtime or device acceptance.

## Open gates at this checkpoint

- Two real Android failures: `ContinuumAdaptiveCockpitUiTest.renderPhoneFoldAndTabletAtNormalAndLargeFonts` and `ContinuumAvatar3dLifecycleUiTest.actualFailurePathRetains2dAndOriginalSingleLifecycleClock`.
- The documented 2D fallback has not been shown to be withdrawn; a failed 3D selection must not be accepted merely by weakening its pixel test. A minimal product correction is under review, not yet executed.
- Investigate the apparent Android-test stall with per-test progress, a bounded diagnostic run and an in-process-namespace thread dump. A running JVM is not proof of test progress.
- Apply independently reviewed fixes to one exact candidate and rerun the complete appropriate gates. The preparation snapshot is not the final tested combination.
- Missing host isolation and several optional integration prerequisites remain explicit unavailable/not-run gates. No permission, model provenance or sandbox validator is relaxed.
- No APK packaging, owner signing, installation or Fold7/native voice/GPU/model acceptance has occurred. Existing package, signer and update contract remain the authority; the QA build configuration is not an owner-installable release.
- A persistent GitHub completion-event automation was attempted twice and rejected by connector validation (`Webhook connectors could not be validated before creation`). No task was created; no perpetual background watch is claimed. The pre-existing hourly planning task was left unchanged.

## Continuation and rollback

Continue in `integration/continuum-finalization-20261104-18845`, draft PR #517. Preserve exact source hashes before and after each run. Pending patches are attached as evidence to prevent transient-workspace loss. Apply only after the live baseline process is finished or explicitly cancelled and its integrity result has been captured.

Relevant existing experiment boundaries are EXP-00/02/07/08/09/11; none is marked completed by this checkpoint. These repairs extend existing tests, callers and the canonical reporting path. They do not activate a second worker, promote model weights or change owner authorization. Rollback consists of reverting the isolated integration commits; no migration or owner data deletion is involved.
