# Verified recall → local model handoff: safe oversized fallback

Status: START_CHECKPOINT / TEST_FIRST_PEND.
Date 2026-10-26.
Exact parent: PR161 source commit `4b569515bb3f43abccf008e1889eac68d570a105`.
Repo: Ullmann27/device-ai, working branch `integration/continuum-grounded-recall-fallback-20261026`.

## Existing real flow
`AgentActivity` receives a confirmed dialogue reply and an explicit synthesis request; `LocalFirstPolicy.afterMemory` selects `LOCAL_MODEL`. For grounded replies, `AgentActivity` currently directly constructs `LocalGroundedRecall(answer,evidence)` on the Android UI thread. The immutable data object requires `answer.isNotBlank() && answer.length<=4000` and `provenance.isNotBlank() && provenance.length<=2000`. Neither is checked at the UI call site. An out-of-bounds retrieved answer/provenance can therefore throw `IllegalArgumentException` in the success handler, losing the grounded answer despite valid local memory.

## Required minimal behavior
Do NOT silently truncate a confirmed memory to trick the LLM into inference. If the bounded grounding handoff is not representable, keep the original direct grounded answer visible and surface a clear local-model skip status. Never promote model output to owner fact. Preserve existing cancellation/session identity, chat safety and no external network.

TDD: add new Kotlin JUnit oversized/blank/valid cases to `LocalGroundedRecallTest.kt` for a safe factory; then implement the factory and update the single `AgentActivity` synthesis call site. Tests and source review before any merge. Real Android Gradle, Fold7, and APK remain NOT_RUN unless executed.

Experiments EXP-05/06/08/12. Review-only, no main changes or owner-data writes; rollback via targeted revert of this branch's source commits.
