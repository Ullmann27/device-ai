# Bounded dialog release repair

Integration owner: root / PR739, `integration/continuum-cinema-apk53-20261110`. Base: `a07b183b67b9c00192bb96b61a0002f74e23372b`. No new conversation core, model, network service or shared renderer changes.

Three source-confirmed review defects are addressed:

1. Unresolved `vorig*`, `letzt*`, `früher*` corrections now use the existing clarification/reset contract. Whole-word matching preserves compounds such as `Früherkennung` and concrete build labels. Actual pure Kotlin/JUnit RED/GREEN: baseline 5/6, candidate 6/6; baseline fails on `den vorigen`.
2. The original owner research request is recorded once at submission, before asking for consent, at the four callers that previously appended it from the later approval callback. The callback no longer duplicates/reorders it after `Ja, mach`. The existing OwnerResearchCommand path already records first and is unchanged. No approval is granted by recording a request.
3. A speech result still rejects a different non-null current research ticket. A previously captured ticket that has since been completed/declined, leaving no pending ticket, no longer discards current dictation or stops the current hands-free conversation. Generation/session/dialog guards are unchanged.

Five Android regressions are added to the existing ten-test ResearchChatApprovalUiTest suite: actual persisted transcript ordering through the real topic-dialog and direct chat web-request callbacks; completed/declined ticket dictation and hands-free continuation; and a ticket created after microphone start remains protected. Existing replacement-ticket and stale-generation controls remain. Transcript tests shut down the Activity research executor before approval, exercising callback bookkeeping while runResearch's existing admission guard prevents network execution. ASR tests use the existing synthetic Android callback fixture; no real audio is claimed.

Android compilation/tests are NOT_RUN on this patch. Root should compile the combined source and run ResearchChatApprovalUiTest (15 tests), ConversationControlTest (6), existing approval/gate/speech-session/recovery suites from #725, and its resulting-source release checks. The six host tests do not establish Activity or device acceptance. No signing, APK generation, installation, network or model inference was performed by this worker.

## Nonexecution follow-up — PR739 review

The earlier request-order fix exposed a missing visible outcome on declined/cancelled/invalid consent. `confirmWebRequest` now emits one honest assistant notice for its still-current presentation scope. A monotone revision is captured before invalidating the preceding ticket, and the original session is captured too. Superseded ticket/session callbacks cannot write into a newer request/session; finishing/destruction guards remain. A paused current scope retains the text outcome for resume; authorization and speech foreground guards remain unchanged. The composer is never modified by this outcome notice. Gate authority and callback signature are unchanged.

Four additional actual Activity regressions cover decline/cancel/expiry with unchanged unsent draft and single notice; replacement preserving the successor dialog/ticket; a session change suppressing the old outcome; and actual pause/cancel/resume retaining the current text outcome without speech or research. The consent suite now has 19 prepared tests. These follow-up Android cases are NOT_RUN pending root's combined-source test lease. The earlier six pure Context checks remain unchanged; no new build, network or model run occurred. Source hashes and the exact follow-up boundary are in `NONEXECUTION-FOLLOWUP.json`.

## Accepted but not started follow-up

All five direct owner-research callers now request idle admission through the existing confirmation implementation. After a valid positive grant, a busy Activity or shut-down local executor cancels the minted session and emits one reason-specific notice through the existing session/revision guard. No composer write occurs. A rejected admission does not call the owner completion, avoiding fallback execution. The existing four-parameter entry retains the model-waiting-for-approval behavior while busy. The only production executor shutdown call is in onDestroy; no new executor lifecycle is introduced.

Two additional UI tests each exercise both real topic and direct chat callers. They verify a single reason-specific outcome, no active research, cleared pending binding, unchanged unsent draft, and no second outcome after repeated acceptance. The existing busy-model approval positive control remains unchanged. Suite: 21 prepared tests, NOT_RUN; no Gradle, network or model run. Root owns PR739 integration and resulting-source Android tests. Exact source hashes, boundary and rollback are in `ACCEPTED-NOT-STARTED-FOLLOWUP.json`.
