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
