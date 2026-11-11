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

## Work in progress

The next checkpoint binds Android picker callbacks and asynchronous reads to
the actual owner request, cancels stale/STOP/background work, and defers the
personal next-step reply until the real result window arrives. It reuses the
existing native picker, PDF renderer, document journal, model path and voice
queue. Graphics dependency: `JarvisActionScenes.forDocumentResult()` and
`JarvisWorkWindow.doOnPresented` from the graphics worker.

Android compilation/UI execution, physical Fold7, actual PDF/native renderer,
live model/audio, Filament GPU and thermals are NOT_RUN at this checkpoint.
No APK/signing/release/version/main changes. No new research/model activation;
existing EXP-05 model provider and EXP-11 orchestration remain unchanged.
Rollback: revert the worker commits on the integration branch.
