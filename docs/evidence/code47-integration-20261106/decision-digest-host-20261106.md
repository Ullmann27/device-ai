# Code47 M1 – optional model-decision context, host verification (2026-11-06)

Source before this receipt: `f26d1063b25c65c22b8d411789d1733c92e3ad72`.
Branch: `codex/continuum-code47-integration-20261106`; Draft PR #576, based on Code46 integration.
**Code46 release branch, original signer, package ID and main remain unchanged.**

## Production changes on Code47 only

- `LocalDecisionPromptDigest` extracts an optional, bounded (<=640 bytes UTF-8) decision/work hint
  from the existing `WorkDecisionRecall.Item` view of owner-confirmed statements and
  `CockpitWorkJournal` episodes. It excludes unrelated items, unsafe/secret-like text,
  revoked topics and conflicting active decisions; confirmed decisions newer than an explicit
  revocation may be used. Journal state remains separately marked as saved or not finished.
- `AgentActivity.runLocal` refreshes the decision items in the actual owner-model path,
  waiting briefly behind queued document-journal writes on the existing document I/O lane.
  It excludes coding-only and delegated-bot contexts, uses the unchanged original request
  for tool authorization, and fails open only for optional context: if unavailable, inference
  uses the prior essential prompt with no fabricated decision.
- `LocalDecisionPromptFrame` wraps the existing `LocalPromptFrame` so required owner
  request, required conversation, prompt tail and previously admitted router advisory
  cannot be displaced by the optional digest. Original context-budget and tool gates remain.
- Owner decision import now honors the source ledger's `confirmable` flag before accepting
  positive CONFIRMED/LEARNED statements. REVOKED tombstones remain visible to suppress old entries.
- Separate conversational M0 regression fixes false mandatory backreferences for
  `das hier: „Carpe diem“` and `das hier auf Englisch: ...`, while preserving
  `das hier jetzt/konkret/dann/eigentlich für ...` and comma backreferences.

## Actually performed verification

An isolated host Kotlin 1.9.0 + JDK 21 executable was compiled and run in the local
container using the copied decision selector/frame implementation and the exact relevant
`LocalPromptFrame`/advisory composition behavior. Lightweight dependency substitutes were
used for `WorkDecisionRecall.Item` and the router class. **19/19 selector/frame tests
passed**: relevance, revocation, later reconfirmation, conflicting entries, journal state,
secret/injection exclusion, Unicode/size bounds, essential history fallback, and advisory
nontruncation; the ExecutorService generic `submit<List<...>>` form compiled and ran.

A separate Kotlin regex smoke in that same host executable passed **13/13**:
9 true `das hier` backreferences and 4 self-contained forward inline questions.
**Total: 32/32 isolated Kotlin smoke checks; zero failures.**

These are deliberately **not** the full actual Gradle/JVM/Android test classes,
no Robolectric/SDK build was run on this Code47 SHA, and no Fold7, LiteRT
model answers, GPU, microphone, battery or temperature measurements were obtained.
The production `AgentActivity` class was inspected and modified through GitHub,
but its entire 12k-line Android dependency graph was **not compiled in this host smoke**.
The new JUnit regression files exist in `app/src/test/java/` and must be executed
against the final exact source under the normal verified Android QA toolchain.
No quality percentage or Astra/Opus equivalence is implied.

## Next gates

1. Real Gradle compile and focused `LocalDecisionPromptDigestTest`,
   `LocalDecisionPromptFrameTest`, `ConnectedConversationCatalogTest` and
   existing conversation/fact-revocation regressions on the same Code47 SHA.
2. Model-prompt capture with current owner decisions and real journal content;
   verify required history, advisory, redaction and authorization survive.
3. Actual loaded Gemma LiteRT answers on Fold7, followed by M2 tool-journal work.
4. Never promote Code47 through the owner publisher until the separate Code46
   release is verified and the owner approves the next signed update.
