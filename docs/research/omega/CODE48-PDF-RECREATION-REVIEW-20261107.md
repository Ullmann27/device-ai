# Code48 integration review: PDF after restored CHAT

Status: LOCAL_ONLY / NOT_PUSHED. Reviewed integration: `3a7b7bad35873009358ccb14057e5a0cbc3cfec9`.
EXP-02 / EXP-07 repair verification; EXP-05 / EXP-12 inference, storage and identity contracts unchanged.

The prior PDF routing fix correctly restored START from an already measured cockpit. An independent actual Activity test found one remaining lifecycle case: restoring directly into CHAT leaves the hidden shell at width zero. `presentDocumentScene` made it visible, then asked for geometry synchronously before Android's first layout; the work window was discarded and the chat fallback hid the cockpit again. The new regression failed at the missing-window assertion on the integrated source.

The Activity now waits once for the existing cockpit's actual layout before consuming the PDF result. The listener is removed before presentation; the same document and bitmap reach the existing work-window host. Existing session and STOP generation checks reject stale results, and detachment removes the listeners and releases the unused page bitmap. No forced measuring, second renderer, new periodic task, storage change or model call was introduced.

Executed against Android SDK36-compiled current production plus cached dependencies, Robolectric SDK28/native Canvas:

- RED: restored-CHAT PDF case 1 test / 1 failure.
- GREEN: restored-CHAT PDF, STOP before first layout, and existing fresh-shell-geometry control: 3 tests / 0 failures.
- STOP prevents the deferred scene and chat report, and the unused bitmap is recycled.
- Raw logs, exact commands and source digests: `docs/evidence/code48-pdf-recreation-20261107/`.
- `git diff --check`: PASS.

The host supplies a synthetic `PdfDocumentIntake.Document`; this does not claim physical PDF decoding or Fold7/GPU acceptance. Full integrated Gradle/APK remains the coordinator's gate. No additional optional work was started after this review finding.

Portable follow-up: use the same configured Gradle UI-test flags as the other Code48 handoffs and run `:app:testDebugUnitTest --tests at.heinz.deviceai.ContinuumDocumentSceneUiTest`. This broader command was not run in this subtask. Rollback: revert this isolated Activity/test change; owner files and persisted checkpoints are unaffected.
