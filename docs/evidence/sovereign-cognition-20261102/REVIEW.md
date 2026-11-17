# Fresh-context whole-branch review

Reviewer: independent fresh-context code-review agent, read-only; range `6fc252ecc05d49508023efe2835a983c14299074..0e22bdb75124e46dce14f4d46977bedcb235751c`. No Critical findings; three Important findings; no additional Minor findings.

1. New synchronous ToolGate observation hook could swallow interruption and let the backend start after a terminal timeout. Independent scratch reproduction: TIMED_OUT returned with 0 backend runs; after observer release backend runs became 1. Fix: per-execution start eligibility invalidated before timeout/cancel; recheck after observer, including interruption consumption.
2. Existing ChatReasoningRouter still clipped complete ungrounded context to 2500 characters and task to 3000 before optional internal model calls. Independent probe found all four internal prompts retained a positive but omitted the late negation. This predates the patch but leaves S1 incomplete. Skip the optional advisory when the complete task/context cannot fit.
3. Activity still shortened resolvedQuestion to 7900 before compilation, particularly exposing compiler-null fallback. Clarify/reject before starting busy/model work rather than passing a prefix.

Reviewer explicitly declined claims about native Android/Fold7/model/Gradle/APK/signature/install, coordinator-run final suites, intermittent owner-location root cause, trained neural quality/general intelligence/weight improvement, and later untracked architecture artifacts. Coordinator ruling: these remain NOT_RUN/UNKNOWN or explicitly source-only; none is inferred from host tests.

One fix pass follows these findings, each with real RED/GREEN regression and complete-source host verification. No second review is claimed. Full original review is recorded in the session; this file preserves its material findings and limits in the repository.

Coordinator fix verification: five genuine failures reproduced across 13 cases before repair; focused context/router/privacy/budget suite 30 PASS and task/event/race suite 12 PASS afterward. A separate redaction-boundary regression is included. The new source-contract QA then exposed one old assertion literally requiring `resolvedQuestion.take(7_900)`; it was replaced with stricter checks for whole-request retention, explicit fit guard before activation, clarification and unchanged memory-write separation. Original safety assertions retained. Full critical/full-node rerun follows; original red output retained in review-critical-red.
