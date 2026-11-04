# Memory / brain R3 — C1 integration follow-up

## Current source-bound result

Both bounded repairs are implemented and have actual-source RED/GREEN evidence.
Contradiction boundary: frozen joint baseline 10 tests, 6 FAIL / 4 PASS; repaired
compiler plus retained existing contracts 23/23 PASS. Mapper: baseline 4 tests,
3 FAIL / 1 PASS, including 79,387 of 80,000 writes accounted; repaired mapper
4/4 PASS including exact accounting. No original assertion was removed.
The independent compiler patch review found no confirmed blocker in its stated
scope. See `docs/evidence/memory-brain-r3-20261104/RESULT.md` and `REVIEW.md`.

Tests-before-repair checkpoint is public at
`33706d166afafe8f93d3b9aa1db34190f950eb68`. The repaired source was published at
`3704b62585c6ce6881ab0dfe0c9947cddd1531c2`, full tree
`ed4984ba9ac873e081a66d7a7be871f029ef5be5`, identical to the local tested commit
`ec2543bef908d7d346a9767f9b095e84254090d1`.

Actual full Android gate: **2,638 tests; 2,618 PASS, 1 FAIL, 19 SKIP; exit 1**.
All 14 new memory/mapper regressions passed. The only failure is the existing
landscape layout assertion `832.0 x 700.0 font 1.0 VIEW_AVATAR ≥ 48 dp`.
Lint was not reached by that failed command. A subsequent separate run on the
same unchanged source passed the **2/2 real attached Activity/Handler tests**
and the configured **NewApi-only lint gate with 0 issues**, exit 0.

Current status: **bounded memory fixes verified; whole Android suite blocked by
the landscape layout contract**. No APK, physical-device dialogue, microphone,
TTS, model inference or native-GPU acceptance is claimed. Do not add overlapping
test counts or apply these results to a newer C1 app tree.

The complete raw receipts and before/after source hashes are retained in
`docs/evidence/memory-brain-r3-20261104/android-gates/`. The read-only layout
diagnosis belongs to the handoff to C1/UI; this branch edits no layout or avatar
production file. The initial intake text below is preserved as history.

The original failing layout method was separately reproduced from immutable
copies of the actual Android compilation. At 832 x 700 / font 1, adding 48f to
the represented top and subtracting that top returns 47.999996185302734375f:
one Float predecessor of 48, a 2^-18 dp deficit. The relevant source blobs match
the C1 intake, and no already-fixed layout was found in the inspected newer
sources. Preserve the 48dp contract and let the UI owner correct its numeric
boundary. This diagnosis is in the adjacent evidence `layout-diagnostic/`
directory and does not establish a physical rendering or touch-size defect.

## Latest observed C1 advancement and continuation

C1 advanced from the intake to `1a5302c504a78a50d5e64ca727f2915529bf2b06`
without changing an app file. The later observed head
`a54583816df92bcd0cdf4e75f06cdefffb5dafcc` additionally changes
`avatar3d/AvatarIdleMotion.kt`, adds its boundary test, and updates Node source
contracts and evidence. It changes neither of this patch's two production
files. These comparisons are source observations, not test results for that
later C1 commit. C1 remains the only integrator of the combined candidate.

Next: C1 takes this bounded patch, coordinates the existing landscape failure
with the UI owner, and executes the common candidate's required gates on its
new exact source. Preserve the old full-run failure and all frozen tests.
Full APK construction remains after the common work and acceptance gates.

## Frozen intake and authorization

The owner requested continuation of the existing memory/brain work and already
authorized public project collaboration. The two corrected AGENTS.md permission
paragraphs from #494/#499 did not propagate to the newer common candidate. This
branch reapplies only those existing permission corrections; no new permission
or repository-visibility change is introduced.

Public base: #519 `b04daf5db2cbe40a9fa757499a0d295176a1bfce`.
Local source-equivalent base: `e19bf2768dc24b7c2ea1040538fdd661269a540c`.
Exact complete tree: `aa439516d342a5410d4473275756a4fa85b4c981`.
Exact app subtree: `471dccb8844a9aba34de5fa33b7f7ffc619ee163`.
Own branch: `fix/continuum-brain-integration-r3-20261104`.

All five previous memory production files, the event factory and the bounded
Activity regression test are retained in C1. Do not add another recall observer
or import the old complete Activity. C1 remains the serial integrator of shared
Activity/compiler/factory/speech/worker entry points. This is a small stacked
specialist patch, not another common integration branch.

## Bounded next work and exclusive scope

1. Reproduce and repair the contradiction-context privacy/sanitizer bypass in
   `cognitive/AiContextCompiler.kt`. The current Activity maps CONTRADICTED
   memory records to raw strings before the compiler's normal memory-policy
   projection. The compiler must retain complete allowed corrections while
   applying source-linked privacy/identity and untrusted-text boundaries.
2. Reproduce and repair detach-during-flush and non-atomic write-count handling
   in the `MemoryEventMapper` class within `cockpit/MemoryEventSource.kt`.
   Preserve the existing factory, hub, mapper API and coalescing semantics.
3. Freeze independent regressions first, run actual-source RED and GREEN,
   review the minimal patch, then execute appropriate Android regression gates
   with a bounded shared build lock and unchanged source binding.
4. Publish the exact result and limitations to C1 and the existing memory PR.

Reserved elsewhere: #511 `redactSecrets`/`appendSection` tuning; #505 M2.9
retrieval and `JarvisSequenceView`; #493 bot draft preservation; #520 native
avatar acceptance; C1 whole-Activity and ECHO integration. Do not edit those
reserved sections, model weights, recovery/consent or foreign working trees.

Relevant existing experiments: EXP-06 provenance and EXP-12 continuity. This
repair does not promote an experiment, a model, an unverified fact or a tool
permission. No second memory database or orchestrator is introduced.

## Executed evidence at initial checkpoint

Fresh source/ownership audit completed by `brain_coordination_now`.
`brain_integrity_review` compiled the actual Kotlin source graph and executed
four frozen synthetic contradiction cases: **2 FAIL / 2 PASS**. Sensitive
contradicted text bypassed the normal memory exclusion; allowed contradiction
text retained a raw role-control token. Controls retained a complete negation
and excluded sensitive data when the lossy side projection was absent.
This small harness extracts the unchanged RecallHit data declaration and is
not an Android Activity or device execution. Raw evidence will be committed
with the tested repair.

`brain_lifecycle_review` identified the mapper callback and count interleavings
from source; those new regressions are still **NOT_RUN** at this checkpoint.
No current full Android, lint, APK, speech, GPU or Fold7 result is claimed.
Old #499 test counts are historical inputs, not acceptance of the newer C1 tree.

## Rollback and continuation

Baseline and negative results remain preserved. Revert only this branch's
specific production repair if its regression gates fail; retain the common
candidate and owner data. No main merge, signing, release or installation.
Read the actual remote head and the latest C1 ownership comments before
continuing. The original intake's WIP/NOT_RUN statements above are historical;
the source-bound result at the top is the current specialist status.
