# In-app development proposals (PR #19)

The user sees one assistant. Developer, Critic, TestDesigner and Judge are internal, sequential, data-only roles using the existing LocalModelReasoningAdapter. No cloud fallback or worker tool authority exists.

## Review contract

All reviewers receive the selected source snapshots and complete developer output, including replacements. TestDesigner also receives the critic output; Judge receives complete critic and test-design output. Prompt size and cumulative UTF-8 limits remain mandatory: a larger context fails explicitly rather than being clipped. A Judge recommendation conflicting with REVISE, REJECT, BLOCKER findings or empty edits fails closed. A rejected review may still be displayed as a rejected, read-only report. Model approval never grants execution authority.

## Source selection

The first UI slice accepts at most four selected sources, totaling 4,000 UTF-8 bytes by default. Selection now rejects excess content immediately. SAF document display names are snapshot labels, NOT proof of repository location. Their SHA-256 identifies the selected content only, not current GitHub state. There is no apply/push capability. Before any future apply feature, add explicit owner-confirmed repository-relative path mapping and revalidate the live destination hash. Duplicate labels are rejected. Source text remains untrusted evidence.

Changed goals/sources invalidate old proposal export. Launching an asynchronous SAF source read now immediately revokes any previous proposal, blocks Start and repeated imports until the read finishes, and keeps old sources available if the picker yields nothing. The UI provides a clear-selection action, and rejects more than four selected URIs explicitly instead of silently ignoring some. SAF reads are capped at the same 4,000-byte limit enforced by the controller. Active jobs copy the source list. Cancellation is checked again after state notifications and after model calls; elapsed monotonic time bounds the job even if wall-clock time moves backward.

## Verification

Run the real project tasks: `gradle :app:testDebugUnitTest --stacktrace` and `gradle :app:assembleDebug --stacktrace`. The existing android.yml push trigger matches the PR branch; it uploads DeviceAI-v0.3-debug-apk and fails if the APK is missing. A successful standalone stub harness is not a substitute for these tasks. No successful CI/build/device result is claimed by this document. Record the exact tested head and inspect checks after the commit.

DevelopmentReviewRegressionTest includes a scripted increment correction, full review-context checks, conflicting verdicts, empty edits, callback cancellation, wall-clock rollback, mutable inputs, source byte limits and stale exports. These are scripted fakes, not a model-quality benchmark; suggested tests in a proposal are NOT EXECUTED.

## Real-model smoke test (after verified APK)

1. Install the verified PR APK without merging main. Load an owner-selected compatible local model.
2. Open Entwicklungsaufgabe. Select a small Counter.kt containing `fun increment(value: Int): Int = value - 1`.
3. Request: Correct increment(1) to return 2 and suggest a regression test. Start.
4. Record model ID, app commit, role progression, elapsed time, call count, final decision, actual replacement and suggested tests. Do not treat the suggestion as executed.
5. Repeat a cancellation test; inspect visible terminal status. Check that no source was modified.

## Still requires verification

Baseline PR #19 head `e12693ffa41bcb8f9096cb57654901535691263e` passed actual `:app:testDebugUnitTest`, `:app:assembleDebug` and APK upload in workflow run `38376951781`. This is evidence for that head only; inspect a fresh run after further commits. PR descriptions and scripted demonstrations are not test evidence.

SAF/device/lifecycle behavior, real local-model JSON reliability/latency, native cancellation and import/selection UX still need device verification. The Activity's SAF import maps display names only and caps read size separately; oversized selections are rejected by the controller. Restart recovery is unavailable; jobs are not persisted. Applying edits, running generated tests, building/installing APKs or pushing changes from the in-app bots are absent by design. Any future execution requires typed least-privilege owner-authorized capabilities behind ToolGate.

