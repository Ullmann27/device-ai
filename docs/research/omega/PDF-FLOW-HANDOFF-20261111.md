# PDF scene: document and reply path

Canonical integrator: root, `work/pdf-scene-20261111`, PR #761.
Worker branch: `work/pdf-flow-20261111`.
Baseline: local `4e4840a01b7954c416722b048a6e4f1ec85b5a43`, identical tree to
remote `1789fe8990e42c11ecf56b84d1e4fea47612caca` (#747 + #757).

## Router checkpoint

Issue #760's exact published Fold7 ASR text rejected the document route because
the presentation-only instruction `und zeige mir jeden Arbeitsschritt` counted
as another tool command. The existing parser now strips only a bounded,
anchored presentation suffix. Negation is checked before stripping. True extra
commands still reject, and colloquial `zeig` joins that rejection check.

Four real production Kotlin sources and four JUnit classes were compiled with
Kotlin 2.2.21 and JUnit 4.13.2. Initial run: 13 tests, two failures (exact ASR and
presentation variants). Corrected run: 13 tests, zero failures. Raw logs:
`docs/evidence/pdf-scene-20261111/flow/router-{red,green}.txt`.
The new four tests contain 19 inputs; this is not a live Android routing claim.

## Integrated source, Android acceptance pending

- Picker callbacks consume an immutable request snapshot once. STOP, another
  owner message and a different session invalidate the old callback. A result
  delivered while paused waits for the existing `onResume`; a pending picker
  restores its bounded request across activity recreation. Every later picker
  uses a different Android request code from a reserved bounded range: an old
  picker callback cannot consume a newer selection. Codes and pending identity
  survive activity recreation; exhaustion refuses another launch.
- Background reads freeze request/draft/speech policy, drop stale completions,
  and observe cooperative cancellation before file access and between text
  pages. Pause, STOP and a newer request invalidate that work. Native rendering
  already in progress is not claimed to stop mid-call. Failed/cancelled reads
  release any first-page bitmap.
- The full generated draft remains visible in the result and chat. A concise,
  visible personal offer speaks only after the actual result window arrives;
  edit/save are existing owner actions. Original per-request Maya restrictions
  remain bound, and the existing voice toggle, foreground, microphone and
  network checks apply. No extra inference is used for this follow-up.
- The original last-model-reply anchor is preserved when the later offer is
  shown, so presentation does not erase the conversation continuation state.

Dependencies: graphics worker production commit
`936a999e13fd1c1db3f8f0821c17a78e34c41782`,
`JarvisActionScenes.forDocumentResult()` and `JarvisWorkWindow.doOnPresented`.
Independent QA owns picker rejection/recreation/arrival tests and the changed
result-origin assertion. Root must compile/test the combined source before
accepting these Activity/renderer changes.

Two existing Node message-routing/streaming files: 11 tests passed, no skips or
failures, on local `fe9cf17c`. Raw output is preserved in the same evidence
directory. This checks existing source contracts, not Android lifecycle runtime.

Android compilation/UI execution, physical Fold7, actual PDF/native renderer,
live model/audio, Filament GPU and thermals are NOT_RUN at this checkpoint.
No APK/signing/release/version/main changes. No new research/model activation;
existing EXP-05 model provider and EXP-11 orchestration remain unchanged.
Rollback: revert the worker commits on the integration branch.

## Observed-error delivery follow-up

Base: integration `f0349f4cbe4474e6df07ddf4685463688abb6ef2`.
The root Android/Robolectric run reported failures in
`aNormalPauseKeepsQueuedPdfFailureVisible` and
`aNewOwnerMessageKeepsQueuedPdfFailureVisible`. Their original helper blocked
the I/O lane before file access, then expected a missing-file recovery after
the new cancellation gate had legitimately revoked that unread request.

Test-only commit `c5de77156` preserves the positive recovery assertions but
uses a counted ContentProvider that actually throws FileNotFoundException.
The file failure completes while the main looper remains paused; only then
does the fixture pause/resume or add a benign follow-up comment. Seven new
checks cover deferred foreground delivery without re-reading, superseding
picker, superseding read, STOP, another real model task, and pause/message
cancellation before any provider query or open.

The production follow-up only preserves a remembered-file failure observed
while the read was still current. Cancellation is never reported as a missing
file. A single pending UI Runnable can wait for onResume; it cannot restart
the read. Existing session/STOP and work identities still reject stale
recovery. The small PDF request generation distinguishes new picker/read/reuse
requests from a transient pause without changing WorkRecall's shared work
generation. A newer owner comment suppresses speech of the older failure.

Modified-source runtime status: UNVERIFIED. The worker did not compile or run
Android tests while root held the build/runtime lease. `git diff --check`
passed. Root must run the test-only commit for RED, then production for GREEN;
neither the revised fixture nor this repair is claimed passing yet.

A separate small follow-up also binds the earlier asynchronous reuse/journal
lookup to the same read generation. A paused or superseded lookup cannot
start a fresh read later. The reader rejects background entry directly.
An eighth counted-provider check exercises this earlier boundary through the
real owner-input route; it is also UNVERIFIED pending root's runtime lease.
