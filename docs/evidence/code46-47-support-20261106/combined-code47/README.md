# Reviewed Code47 integration candidate

This commit combines primary source `9ee7535bb131504cc85419afd30f446089a40171` with the verified M1 decision-conflict fix and two additional reviewed corrections.

| Correction | Actual targeted evidence |
|---|---|
| Active decision conflicts before display filtering | Same two JUnit classes: baseline17 PASS/2 FAIL; corrected19 PASS |
| Tool journal start after committed dispatch | Baseline7 tests/4 failures; corrected43/43, seven classes |
| Trusted clock in the actual prompt tail | Baseline8 tests/1 failure; corrected8/8; five negative source mutations rejected |

These runs overlap and are not a combined full-suite count. Production/test sources match the reviewed file hashes in their raw receipts. M2 preserves the original four-argument ToolGate ABI and pre-dispatch cancellation callback. The clock correction changes one test file; production and its six wrapper tests are unchanged.

The previous complete f26d run remains3556 PASS/1 FAIL/20 skips and did not reach lint. Full QA of this combined source remains NOT_RUN. The owner subsequently supplied actual Code46 feedback requiring a broader runtime-wiring repair before a47 release: natural app actions, visible work scenes, conversation/context integrity, identity and response completion. See the main support handoff.

Code46 remains the independently verified original-signed live APK. No47 APK, device installation, native model result or GPU acceptance is claimed here.
