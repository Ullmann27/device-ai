# Bounded dialog release repair

Integration owner: root / PR739, `integration/continuum-cinema-apk53-20261110`. Base: `a07b183b67b9c00192bb96b61a0002f74e23372b`. No new conversation core, model, network service or shared renderer changes.

Three source-confirmed review defects are addressed:

1. Unresolved `vorig*`, `letzt*`, `früher*` corrections now use the existing clarification/reset contract. Whole-word matching preserves compounds such as `Früherkennung` and concrete build labels. Actual pure Kotlin/JUnit RED/GREEN: baseline 5/6, candidate 6/6; baseline fails on `den vorigen`.
2. The original owner research request is recorded once at submission, before asking for consent, at the four callers that previously appended it from the later approval callback. The callback no longer duplicates/reorders it after `Ja, mach`. The existing OwnerResearchCommand path already records first and is unchanged. No approval is granted by recording a request.
3. A speech result still rejects a different non-null current research ticket. A previously captured ticket that has since been completed/declined, leaving no pending ticket, no longer discards current dictation or stops the current hands-free conversation. Generation/session/dialog guards are unchanged.

Five Android regressions are added to the existing ten-test ResearchChatApprovalUiTest suite: actual persisted transcript ordering through the real topic-dialog and direct chat web-request callbacks; completed/declined ticket dictation and hands-free continuation; and a ticket created after microphone start remains protected. Existing replacement-ticket and stale-generation controls remain. Transcript tests shut down the Activity research executor before approval, exercising callback bookkeeping while runResearch's existing admission guard prevents network execution. ASR tests use the existing synthetic Android callback fixture; no real audio is claimed.

Android compilation/tests are NOT_RUN on this patch. Root should compile the combined source and run ResearchChatApprovalUiTest (15 tests), ConversationControlTest (6), existing approval/gate/speech-session/recovery suites from #725, and its resulting-source release checks. The six host tests do not establish Activity or device acceptance. No signing, APK generation, installation, network or model inference was performed by this worker.
