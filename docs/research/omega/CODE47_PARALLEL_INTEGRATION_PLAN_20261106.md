# Device AI Continuum – Code 47 parallel development (2026-11-06)

## Owner authorization and release isolation

The owner explicitly chose: **ship original-signed Code46 first, develop Code47 in parallel.**
Code47 is a separate experimental integration line; it is **not** a Code46 publication, installed package or proven Fold7 release.
This branch starts from the Code46 integration source. **Do not** merge into main, change the publisher ref, replace the original signer,
alter the package `at.heinz.deviceai.continuumlocal`, use paid runners or promote model weights based on synthetic scores.
Target future versionCode 47 / versionName 0.3.46 only after code-bound QA and an owner's release decision.
Original Filament GLB head, original transparent Continuum orb, dark cyan/blue/violet/magenta glass identity remain mandatory.

## Code46 release remains first

Existing original publisher `srv-dbf1tr2vcj2c73ehpfog`:
- Last confirmed live release: **Code44** at `fbdffc3cf3485f6721c0091b54a6824f0c1b81fa`.
- Code45 source `48251610d9c9519678e9fc5ef652ad8286675b7f` was blocked by full Android QA: 3556 tests; 5 `JarvisWorkWindowDepthTest` errors, 20 skipped.
- Earlier superseded Code46 publisher job `dep-dbn0gj5s25qs73a1vrv0` builds `756bc68d0fb7f8522da8900fd889fd2eae2e8caf`; corrected Code46 job `dep-dbn0u78gs6ms73a7m54g` pins `577f0931e7937ffcac02dc855a540f093facdb3b`, with complete QA, original signing, HTTPS hash and certificate readback outstanding at planning time.
- A mere queued or compiling build is **not** a downloadable Code46 APK. Never substitute an unsigned test artifact or update metadata from Code47.

## Prioritized independent integration milestones

### M0 – conversation regression safety (narrow and testable)
An open GitHub reviewer finding on Code46 PR #575 says a self-contained first user utterance
`Was bedeutet das hier: „Carpe diem“?` is misclassified as a mandatory history follow-up, because
`LocalConversationFollowUpPolicy.consequenceReferent` accepts `das hier` even before inline quotation.
In Code47 only: reproduce with exact real production regex; narrow the expression so forward-pointing
`das hier: <content>`/ `das hier auf Englisch: <content>` remains standalone. Preserve real
`Was bedeutet das hier?` and `Was bedeutet das hier für unseren Plan?` follow-ups.
Add regression tests; don't weaken existing conversation, history or negation gates.

### M1 – verified decision digest into *existing* local model prompt
Read only relevant, not revoked or contradicted items from the existing owner ledger and CockpitWorkJournal.
Provide a **bounded, provenance-labeled, untrusted context section** into the *existing* `runLocal` request.
Never let recalled statements override the user's current message, tool permissions, system policy or STOP.
Do not create a new memory database or assert owner decisions were carried out without journal proof.
RED: revoked and conflicting statements, unrelated decision, unknown history, malicious quote, prompt token budget;
GREEN: exact local model request frame receives only relevant allowed items; test actual LiteRT replies on Fold7.

### M2 – executed tool steps into the existing work journal
Connect actual tool status / `run.taskEvidence?.calls` from `AgentToolLayer`/ToolGate with
`CockpitWorkJournal` using minimal additive, nonbreaking record kinds and episode linkage.
Journal *started, succeeded, failed, stopped and uncertain* results from actual tool receipts only.
OFF/ASK/ALWAYS_ON permissions remain authoritative. RED: no phantom success, double callback, STOP late result,
denied authorization, journal reload, no secret leakage. First acceptance should be a single read-only
`get_storage_info`/device-info operation end-to-end on Fold7; not a generic unsafe autonomous tool chain.

### M3 – complete one genuine holographic work episode
Preserve `AvatarDirector`, original orb and Filament head, existing `JarvisWorkWindow`, frame clocks and
`ToolAppIdentity`. Connect actual tool start -> installed-app identity crystal + light trail -> work window
-> real step status -> result / error -> eye-first-then-head attention -> safe camera reset and deck return.
No CGI-only result claims, no second clock, no colored panels hiding the face or orb.
RED: STOP, postponed callbacks, layout/density/fold transition, no installed app, missing symbol, inaccessible
font-size controls, no detected display. GPU and visual playback on Fold7 are acceptance items.

### M4 – actual voice and intelligence acceptance (not simulated)
Measure real de-DE/de-AT voice, `setVoice` selection, emitted audio, word boundaries / text-paced fallback,
latency and thermal budget on Fold7, with source provenance. Measure genuine Gemma LiteRT output against
untouched holdout tasks in `tools/fold7_quality_lab` (no substitution of synthetic scoring as model quality).
Compare only measured tasks; no unsupported Astra/Opus percentages.

### M5 – first bounded Bot workspace, *after* M2
Re-use existing 20 profile registry from #423, but do not call profiles 20 concurrent models.
Enable one scoped, auditable independent task with permissions, cancellation, timeout and receipt,
not broad device control. Keep 16M training support #530 isolated: zeroed moments/fixtures are not language improvement.

## Completion gates and publication

For each milestone: independent RED reproduction, minimum diff, GREEN on the *exact source*, review, and
noninterference with the active Code46 release. No claim of full Android QA until the original publisher
proves it. For a future Code47 owner release: complete Android/Node/backend/lint and ARM64 build,
original signing certificate, original package/version increment, HTTPS catalog SHA/APK check and genuine
Fold7 acceptance. Never merge to main without explicit approval.

## Owner's Fold7 practical checks after Code46 arrives

1. Existing app updates in place, model READY, data retained.
2. Fold open/closed: original head/orb readable and not obscured; no layout clipping.
3. Natural follow-up questions, corrections and recall show real journal data.
4. Select a real text PDF -> open work window -> draft -> edit/save; STOP during the run.
5. Try a permitted read-only device/storage query; verify actual result and permission behavior.
6. Listen to offline voice and observe real lip synchronization; capture latency, heat and drops.

Record exact APK source, Build-Info SHA, step, expected vs actual, timestamp, video/image/log when available.

## Progress receipt — M0/M1 (2026-11-06)

- **M0 source corrected:** the `das hier` forward-inline/follow-up split now protects
  self-contained inline quotations and translation text without losing genuine
  `das hier jetzt/konkret/dann/eigentlich für …` references.
- **M1 source integrated, not device-accepted:** `LocalDecisionPromptDigest` selects
  a topical, optional and provenance-labelled context from the existing ledger and
  `CockpitWorkJournal`; `AgentActivity.runLocal` reads it on the existing owner model path.
  `LocalDecisionPromptFrame` does not sacrifice required dialogue, the owner request,
  prompt tail or previously included internal advisory. Ledger `confirmable` is respected.
- Isolated Kotlin smoke 19 selector/frame checks + 13 regex cases = **32 PASS**,
  using dependency substitutes; real Gradle/JUnit, actual LiteRT responses and Fold7 remain
  **NOT_RUN**. Evidence: `docs/evidence/code47-integration-20261106/decision-digest-host-20261106.md`.
- Review threads identified the false-negative `das hier` adverb family and the
  clipped-advisory boundary; both repaired, replied to and resolved in Draft PR #576.
- **M2/M3/M4/M5 remain open.** Do not confuse a checked-in JUnit test with a completed
  Android test run or this source with an installed Code47 APK. Code46 source/publisher untouched.

## Progress receipt — M2 (2026-11-06)

**Source implemented (not yet full Android accepted).** The existing
`BoundedAgentOrchestrator` now provides an actual `ToolGate` execution-start
callback, consumed by both owner storage-tool routes in `AgentActivity`.
`CockpitWorkJournal.Kind.TOOL` persists only call ID, trusted tool name,
status and optional fresh-result fingerprint on the same serial document
journal lane. A bounded `ToolWorkJournalBridge` classifies real receipts:
STOP/session change/replay/unknown remain UNKNOWN, rejected is not
executed, and success is labeled a fresh tool report, not independently verified.
Process restart recovers running tool entries as unknown. Routine checks have
at most ten recent tool entries and cannot crowd out saved PDF work.

A focused local Kotlin/JVM classification smoke using dependency substitutes
passed **11/11**; the new source JUnit classes are checked in but **NOT_RUN**.
Full Gradle compile, exact-SHA Android regressions, real LiteRT responses,
real Fold7 storage read, system audio and Filament GPU remain unverified.
Evidence: `docs/evidence/code47-integration-20261106/storage-tool-journal-m2-20261106.md`.
M3 camera/window scene, M4 voice and actual model quality, and M5 bounded bot
workspace stay open. Code46 original publisher was not modified.
