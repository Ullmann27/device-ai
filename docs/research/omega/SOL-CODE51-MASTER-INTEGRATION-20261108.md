# Code51 master integration

Status: bounded integration milestone HOST-PASS; Draft PRs #650, #653 and #658
on `sol/code51-reconcile-20261108` (see next section).
Overall Code51 remains WIP; no APK or device acceptance. Closing under the
owner's 10% limit instruction; no further projects started.
Owner canon #642, execution #643; read #637-641.
No main merge, signing, release, installation, private data or signer access.

## Research cancellation checkpoint (resume here)

Continuation on the same isolated #658 branch; EXP-06 evidence admission and
EXP-12 lifecycle continuity. No bot-persistence redesign or release work.

- Existing `ApprovedWebToolGate.completeApproval` accepts a decision only from
  the still-pending, unrevoked dialog. A stale positive/negative/cancel callback
  cannot cancel a newer request or start the automatic local-answer fallback.
  Genuine current declines retain that fallback.
- All seven `confirmWebRequest` call sites share foreground/session checks.
  The model-tool caller receives an explicit invalidation callback so a waiting
  model turn is not stranded. Each dialog response is delivered at most once.
- New-session and pause paths revoke pending web requests. Pause additionally
  cancels the active research token and its model synthesis.
- Progress requires the active token, matching conversation, foreground state
  and current consent. Late cancelled/revoked results release the busy lane but
  do not publish an answer, speech or new conversation context.

Focused compilation/JUnit: **14 PASS** across `ApprovedWebToolGateTest` and
`ConversationResearchApprovalIntegrationTest`, including five added regressions
for revocation, replacement, mode changes, one-shot decisions and late results.
Controlled providers only, no live HTTP. Actual Android callback timing and
Fold7 remain NOT_RUN. Full current-source JVM regression is pending at this
checkpoint; the earlier whole-text admission repair remains included.

Rollback: revert this cancellation checkpoint as a unit, not individual dialog
handlers; keep the independent full-text memory admission repair below.

## Review repair checkpoint

Source: `b557652e5eb10844d832d4f91055aa39a67e6ca4`, pushed and remote-verified.
This closes the fact/meta admission regression reported on #658; Fable's
upstream #657 is still pinned at `b1117df179cbc92284d779399f83c381125bda43`
and was not modified.

`BootstrapBrain.decide("Merke dir: ...")` is also the safety gate used by
`AgentActivity.handleNeuralRequest` before neural TEACH. The latter trains on
the **complete** `command.text`, not on `split.fact`. Validating only the
shortened fact let a sensitive or overlong trailing instruction bypass the
original gate. The instruction could also appear in the memory write note.

The existing gate now checks sensitive markers and the 3–320-character limit
against the complete content. Valid content still stores only the fact and
acknowledges the instruction. Rejected content produces no fact, no neural
recall permission and no echoed instruction. No second validator, memory store,
schema migration, deletion of existing records or permission change.
Relevant experiments: EXP-04 learning admission, EXP-06 owner-fact provenance.
Compatibility: existing callers and fact/meta separation are preserved.

Evidence (JDK 21, Kotlin 2.2.21; synthetic inputs only):

- RED on `f1ad10a7`: `BootstrapBrainTest` 19 tests / **3 failures**
  (sensitive instruction, neural TEACH admission, whole-content 321-character
  rejection).
- GREEN on the repaired source: **39/39 PASS**, nine JUnit classes, zero skips.
  Five added tests cover safe fact separation, both memory prefixes and 12
  existing sensitive markers, exact 2/3/320/321 boundaries, rejected neural
  input and accepted safe neural input.
- Current `BootstrapBrain`, separator and display sources compiled against the
  existing SDK-less JVM dependency classes. This is a focused host regression,
  **not** a fresh full Android build or device persistence test.
- Previous full 3568-test PASS below belongs to `a9e17405`, not to this repair.
  Full suite, Node, Gradle/lint/Robolectric/APK and Fold7 were not rerun here.
  The earlier Gradle DNS blocker has not been re-probed.

Executed targeted command (the prior SDK-less runner output supplies dependency
classes; compile the current changed sources first):

```sh
prior=/path/to/previous/android-kotlin-unit-check-output
deps=/path/to/android-and-junit-jars
out=/path/to/memory-gate-green
cp="$prior/main:$prior/stubclasses:$deps/junit.jar:$deps/hamcrest.jar:$deps/android-all-15.jar"
s=app/src/main/java/at/heinz/deviceai
t=app/src/test/java/at/heinz/deviceai
kotlinc "$s/BootstrapBrain.kt" "$s/cognitive/OwnerFactMetaSeparator.kt" \
  "$s/learning/OwnerMemoryPerspective.kt" "$t"/BootstrapBrain*Test.kt \
  "$t/cognitive/OwnerFactMetaSeparatorTest.kt" "$t/learning/KnowledgeDialogueTest.kt" \
  "$t/learning/NeuralDialogueTest.kt" "$t/learning/NeuralTeachingAuditTest.kt" \
  -module-name app -Xfriend-paths="$prior/main" -cp "$cp" -jvm-target 17 -nowarn -d "$out/classes"
java -cp "$out/classes:$cp:$KOTLIN_HOME/lib/kotlin-stdlib.jar" org.junit.runner.JUnitCore \
  at.heinz.deviceai.BootstrapBrainTest \
  at.heinz.deviceai.BootstrapBrainNegatedToolRoutingTest \
  at.heinz.deviceai.BootstrapBrainCommaContrastTest \
  at.heinz.deviceai.BootstrapBrainNegationDeEnTest \
  at.heinz.deviceai.cognitive.OwnerFactMetaSeparatorTest \
  at.heinz.deviceai.learning.KnowledgeDialogueTest \
  at.heinz.deviceai.learning.NeuralDialogueTest \
  at.heinz.deviceai.learning.NeuralCommandParserTest \
  at.heinz.deviceai.learning.NeuralTeachingAuditTest
```

Next: carry this gate fix when integrating #657 independently; authorized
Gradle/SDK and Fold7 acceptance remain open. Bot restart persistence is still
unfinished, not started under the closure budget. Rollback: do not revert this
gate alone (that restores the bypass); if fact/meta separation is withdrawn,
retain whole-input admission and its regression tests.

## Reconciliation checkpoint (resume here)

Branch `sol/code51-reconcile-20261108`, stacked on #653 (`cb11db2f`). After the
10% closure, Fable opened #654/#656 that duplicated SOL's #639 navigation and
conversational research. Exactly one recognizer per concern now remains; Fable's
branches were not modified.

| Merge | Pinned SHA | Decision |
|---|---|---|
| #657 memory fact/meta separation (#640) | `b1117df1` | clean merge, no overlap |
| #654 internal navigation (#639) | `c61bf171` | **canonical**: `OwnerInternalNavigationIntent` (18 route keys). SOL `centerTarget`/`localCenter` removed; Code50 exact-title block restored; `OwnerAppLookupIntent.inspect` returns null for internal screens (invariant). Added missing owner cases: `oeffne/offne`, "Arbeitsaufträge"/"Aufgabenübersicht" -> `tasks` |
| #656 chat web research | `1bbdab1a` | **canonical**: `OwnerResearchCommand.parse`. Also owns router-only forms ("Suche die neuesten Quellen zu …", "Prüfe im RIS …") through `PublicKnowledgeTopicRouter.explicitTopic`; second actions (sende/lösche/installiere …) are `Refused` on every phrasing. Activity: Fable OFF/Refused UX + selected `researchMode` + SOL session/foreground/isFinishing guards |
| #655 H4 robust transfer | `e5ece222` | **not merged**: research-only, own gate FAIL |

`sendCurrentInput` order: document intent -> internal navigation -> research
command -> app lookup. `WebToolRequest.fromChat` (explicit Web path) unchanged.

Evidence (JDK 21, Kotlin 2.2.21, SDK-less runner, unprivileged `nobody`):

- RED (Fable recognizers unmodified, same tests): 5/12 fail.
- GREEN targeted: 62/62 PASS (research single entry, OwnerResearchCommand,
  public research/approval, topic router, gate, focus, navigation, app lookup).
- Full: `scripts/android-kotlin-unit-check.sh` -> 635 classes, **OK (3568
  tests)**, Checks PASS (one no-main helper skipped), 5:39, max RSS 2.25 GB.
- Node: no JS/MJS change since `cb11db2f`; previous 202/202 PASS stands.
- NOT run: Gradle/AGP/lint/Robolectric/APK (`dl.google.com` DNS), Fold7.

Next: owner/Fable review of the canonical choice; persistent profile-bound bot
work orders (no restoring ephemeral leases as owner runs); authorized Gradle/SDK
QA; Fold7 speech/TTS/STOP/thermal/navigation/research acceptance.

## Frozen source graph

| Ref / PR | Inspected SHA | Responsibility |
|---|---|---|
| Code50 / #630 | `473f79727e4b52509cc7d1af71e59b80ab9b6be5` | Original source baseline; not main |
| main | `52997ee3d94686add646d53fb11fd0b8d9e1e94f` | 54-file skeleton; not the integration target |
| #646 | `f3f90736f4addb027dcc4f42a1f3cd3300713d3b` | Fable speech recovery / model status / thermal budget |
| #647 | `f93b661ff2652b0783ab6224810edd85b8c368ff` | Cognitive discourse pilot |
| #648 (stacked on #647) | `9b8bf1d947323cecc75d6dd7dd2c1da8e73870b3` | Hypothesis prior / diagnostic pilot |
| #649 (includes #647/#648) | `8688f855e967c72b345debce3cd6c2c90fae5806` | Research-only verified-reuse pilot |
| #651 | `09722adf6c88453ac7607a2a484ae97f00a8fc00` | Fable cinematic workscene / shared render budget |
| #650 | `82590f532d0b070823d82e6007f53e5e825cf70f` | Own routing, approved research and truthful agent checkpoint |
| #653 | `10139f21edfb088ddb8349bcaf987f1211bd1024` | Combined candidate on `sol/code51-pinned-integration-20261108` |

Older #574/#614/#600/#601/#602/#635 metadata inspected; no blind merges.
Experiment contracts #41, AGENTS.md and the experiment register read.
Relevant interfaces: EXP-06 provenance, EXP-08 jobs, EXP-11 delegation,
EXP-12 recovery. No promotion of research experiments or schema migration.

## Integration matrix

| Capability | Existing source / actual UI entry | Execution / missing connection |
|---|---|---|
| Memory | `OwnerAppLookupIntent -> localCenter -> showMemoryCenter` | Manual and bounded natural navigation connected; #639's exact device incident and #640 output remain unverified |
| Logic / planning | `centerTarget -> localCenter -> showLogicCenter` | Natural title and manual dashboard use the same center; no second planning system |
| External apps | `OwnerAppLookupIntent -> OwnerAppActionFlow -> ToolGate` | Discovery is read-only; launch requires permission and fresh foreground receipt |
| PDF | `OwnerDocumentIntent -> openOrReuseDocument` | Named PDF and generic document commands reach the existing Android picker, not a fabricated file result |
| Public research | `PublicKnowledgeTopicRouter -> WebToolRequest -> ApprovedWebToolGate -> DeepResearchEngine` | Natural intake now connected before generic app lookup; OFF/ASK/ALWAYS_ON and private negatives checked with controlled providers, live HTTP/device operation unverified |
| Voice | `ContinuousConversationMode`, Android ASR, local model, Android TTS | Pinned #646 recovery/thermal changes integrated; independent 20-turn handoff tests. Physical playback/restart unverified |
| Avatar / work scenes | `JarvisActionScenes`, cognitive/cockpit events, #651 workscene | Same event/render-budget path; decorative cinematic phases are not evidence of tool progress. #638 reference quality/Fold7 performance remain open |
| Bots | 20 profiles, `ChiefBotTaskIntake -> runLocal -> BoundedAgent -> ToolGate` | Existing explicit assignments retain scoped permissions; denied tools cannot report success. Persistent bot queue/status and restart UI remain incomplete |
| Development | Existing development backlog, teamleader, Android/Termux builder | READY diagnosis is not a completed coding job or APK proof |
| Cognitive pilots | Existing brain plus pinned #647/#648/#649 | Context/reasoning/reuse host assertions checked; optional research is not autonomously enabled or treated as a tool receipt |

## Validation checkpoint

`./gradlew --version`: Gradle 8.11.1 wrapper works, launcher JDK 27.
`bash scripts/android-kotlin-unit-check.sh ...`: UNAVAILABLE initially:
`CONTINUUM_ANDROID_DEPS` unset; Kotlin/JUnit dependencies not installed.
No Android SDK or adb found. Missing prerequisites will be restored where
possible, otherwise exact failures documented. No APK/device claims.

Focused read-only security review of the baseline conversation/tool/web
boundaries found no substantiated exploitable vulnerability. No runtime exploit
testing and no complete security audit of #646-648. Local sandbox storage without
extra content encryption is a threat-model limitation, not a proved leak.

Navigation RED: 11 tests / 4 failures on the original source, using identical
new tests and JDK21/Kotlin 2.2.21. The exact owner sentence already passes pure
baseline intake; its device failure is NOT reproduced by that host test.
Confirmed gaps: `Zeig` / possessive / split ASR center title, the conjunction in
`Logik und Planung`, natural work-order overview, and a named `.pdf` picker route.
Candidate now normalizes only supported center names in the existing app
router and dispatches through a shared Activity center mapping. Extended
abbreviations are restricted to internal centers, preserving existing app
negatives. Full Android-source JVM compilation/regressions running; GREEN
not yet asserted. No claim that the original device incident is resolved.

Navigation checkpoint `561a88659208eebf178a8c5a1db16ad333ff5e9a` pushed and
read back from the remote: 11 targeted tests PASS. Additional independent
research RED: 16 tests / 2 failures (natural/addressed commands and questions).
Candidate extraction plus old privacy/explanation/app tests: 21 tests PASS.
Explicit research now precedes generic app discovery and uses the existing
WebToolRequest/approval/engine path. Personal, quoted, compound and offline
requests remain local with a visible explanation. Approval tickets unchanged.

First full main/test Kotlin compile PASS; JUnit 3488 tests / 3 failures:
root bypasses read-only filesystem permissions; existing JDK19+ Unicode
word-boundary failure in app planning; one stale pre-fix `Zeig` regression.
The latter is fixed in checkpoint 561a8865; the planning boundary is now changed
to the same Unicode-lookaround prior art as OwnerDocumentIntent. Full suite
must be rerun on final source; this earlier result is not final GREEN.

Changed Android integration files compiled against the full compiled main
module: PASS. Targeted combined app routing, web approval/engine, private
negatives, freshness/explanation and independent speech hand-off: 55 tests PASS.
Speech checks cover 20 two-chunk replies, STOP/barge-in/stale callbacks,
approval/focus/session suppression; pure checks, not Android ASR/TTS playback.
No competing recovery changes to #646.

Additional P0 RED on the unchanged BoundedAgent: a denied `launch_app` (zero
backend dispatches) followed by a form-only evaluator returned a claimed
successful completion. Candidate outcome guard refuses successful evaluation
when a proposed tool has a failed/rejected observation; unavailable registry
tools explicitly report UNAVAILABLE. No replacement planner, gate or journal.
The existing two tests expecting PARTIAL/accepted prose after rejection now
assert FAILURE/BLOCKED. 36 coupled agent/Chief/tool-plan tests PASS.

One host end-to-end work order is verified through the real
`DeviceStorageProbe -> DeviceToolExecutorRunner -> ToolGate -> evaluator ->
AgentCognitiveEvent -> FileToolExecutionReceiptStore` path, with a controlled
planner: a real app-volume filesystem measurement, checked completion,
persisted output hash, and restart/replay prevention. The existing checkpoint
store restores pre-tool planning; post-proposal state requires operator
review, never automatic replay. This is NOT native Gemma, a running bot on
Fold7, Android/Termux coding, or an independent device acceptance. Bot profile
metadata and volatile UI task status still do not constitute a persistent
autonomous bot backlog; that remaining integration must not be concealed.

During this audit, #649 (`8688f855e967c72b345debce3cd6c2c90fae5806`, stacked on
#648/#647) and visual #651 (`09722adf6c88453ac7607a2a484ae97f00a8fc00`) appeared.
Their scope/SHAs were read; neither changes tool approval. The next step is
an isolated combined candidate with pinned #646/#649/#651, then full final
Kotlin/JVM and targeted Node regressions. No production activation of research.

Restored JDK21, Kotlin 2.2.21 and pinned Maven Central compile/JUnit jars after
actual prerequisite failures. Gradle QA on JDK21 still fails resolving AGP
8.10.0 from Google Maven (`dl.google.com` DNS unavailable). No alternate,
untrusted artifact mirror used. Gradle, lint, Robolectric and APK remain blocked.

## Scope and recovery

Reproduce #639, preserve negative file/app/compound cases, wire only existing
centers and the existing approved research path. Keep speech/cognitive changes
owned by their current PRs. Commit/push each meaningful checkpoint.
Rollback is reverting this branch's commits, never rewriting others' branches.

## Combined candidate checkpoint

All five current team heads were read back immediately before integration and
matched the pins above. Three explicit merge commits (`3539fcd2`, `838605a7`,
`10139f21`) combine #646, #649 and #651 on #650; no conflicts, main changes or
production promotion. Draft #653 is stacked on #650.

Exact candidate `10139f21` native Node selection: 202 PASS, 0 FAIL, 0 SKIP.
This includes routing/lifecycle/builder/backlog/independent-verifier controls,
virtual-fabric tests and the verified-reuse research negative controls.
Original-source context pilot: 20 PASS. Reasoning batch pilot: 17 PASS,
768 tested permutations with matching priors, 26 negative controls.
These are synthetic host assertions, not device intelligence or energy proof.

Full combined Kotlin main/test compile: PASS; 631 JUnit classes, 3544 executed
tests, 2 failures. Executed as an unprivileged user so the existing read-only
filesystem negative is meaningful. One additional coupled native-model bridge
test still expected PARTIAL after a denied tool; it now asserts FAILURE,
blocker and absence of the fabricated success text (8 bridge tests PASS).
The other failure is the scorer's generated `app/build/continuum-eval` report
being root-owned from the earlier run, not a product error. Final full rerun
with writable generated-report artifacts is pending; no suite-wide GREEN claim.
Four executable Checks programs PASS; one Checks helper has no main and is
covered by JUnit rather than being an executable program.

## Final acceptance evidence and restart handoff

Validated immutable source:
`f25dc510b8b94c9008191b361567f6cb0d9c79fd`.
This contains the three pinned merges and the final adapter assertion fix.
The final handoff commit changes documentation only; obtain its exact pushed
SHA from #653 or `git ls-remote origin refs/heads/sol/code51-pinned-integration-20261108`.

| Check | Final result |
|---|---|
| Full Kotlin main/test compilation, JVM17 target on JDK21 | PASS, including merged Android source against compile-only stubs |
| Existing complete JVM runner | PASS: 631 JUnit classes, `OK (3544 tests)`, zero failures |
| Checks programs | PASS: four executables; one no-main helper skipped as an executable, covered by JUnit |
| Native Node regression/research selection | PASS: 202 tests, zero failures/cancellations/skips |
| Original-source context pilot | PASS: 20 tests |
| Original-source reasoning batch pilot | PASS: 17 tests, 768 permutations, 26 controls |
| Gradle QA on restored JDK21 | FAIL / environmental: AGP 8.10.0 resolution, `dl.google.com` DNS unavailable |
| AGP/SDK36, lint, Robolectric UI, APK | BLOCKED / NOT_VERIFIED; no Android SDK and unavailable Google Maven |
| ARM64 native Gemma, Fold7 / Android17 speech/TTS/UI/thermal/security acceptance | NOT_RUN; no device/adb |

The normal JUnit runner retains repository Ignore/Assume behavior; its OK
count is not a claim that disabled tests ran. The read-only filesystem
negative passed as user `nobody`. The scorer's previously root-owned generated
report directory/file were made writable for that user; no source-test
semantics or broad workspace permissions were weakened. Both earlier
complete-suite failures are resolved on the final run.

Actual complete-run output:

```text
f25dc510b8b94c9008191b361567f6cb0d9c79fd
classes=631 checks=5 junit: OK (3544 tests)
KOTLIN_UNIT_TESTS_CHECKED: all JUnit classes and Checks programs pass (outside Gradle; not lint, not an APK, not a device)
f25dc510b8b94c9008191b361567f6cb0d9c79fd
Elapsed: 5:11.62; maximum resident set size: 2114420 kbytes; exit status: 0
```

The resource numbers include host compilation and tests, not Android runtime
memory, thermal behavior, energy savings or simultaneous model/GPU load.
Full logs persist in the session's `final-qa-location.txt` location
(`final-result/`, `final-resources.txt`) and `node-final-f25dc510.tap`.
The evidence and source pins needed for takeover are in this pushed document.

Reproduction (set `QA` to a writable artifact directory, `KOTLIN_HOME` to the
official Kotlin 2.2.21 compiler and `DEPS` to the pinned dependency jars):

```bash
export JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 LC_ALL=C.UTF-8
export PATH="$JAVA_HOME/bin:$KOTLIN_HOME/bin:$PATH" JAVA_OPTS=-Xmx3g
export CONTINUUM_ANDROID_DEPS="$DEPS"
export CONTINUUM_JUNIT_JARS="$DEPS/junit.jar:$DEPS/hamcrest.jar"
bash scripts/android-kotlin-unit-check.sh "$QA"
bash scripts/continuum-context-pilot.sh "$QA/context"
bash scripts/continuum-reasoning-pilot.sh "$QA/reasoning" batch
node --test \
  scripts/continuum-owner-local-routing.test.mjs \
  scripts/continuum-ui-routing.test.mjs \
  scripts/continuum-jarvis-mission-control-contract.test.mjs \
  scripts/continuum-local-chat-streaming.test.mjs \
  scripts/continuum-gemma-picker-lifecycle.test.mjs \
  scripts/android-kotlin-compile-check.test.mjs \
  scripts/continuum-mobile-builder.test.mjs \
  scripts/continuum-session-evidence-routing.test.mjs \
  .github/continuum/development-backlog.test.mjs \
  .github/continuum/ccc1-redgreen-verifier.test.mjs \
  .github/continuum/ccc1-independent-verifier.test.mjs \
  .github/continuum/ccc1-experience-durability.test.mjs \
  tools/continuum-virtual-fabric/*.test.mjs \
  tools/continuum-cognitive-pilots/verified-reuse-pilot.test.mjs
```

For the permission negative, run the complete suite without root and ensure
`app/build/continuum-eval` is writable by the test user. No SDK-less result
substitutes for Gradle QA (`:app:compileDebugKotlin :app:testDebugUnitTest
:app:lintDebug`, with the repository's existing owner-authorized QA settings).
The attempted Gradle validation stopped at plugin resolution, before these
tasks could run; no new Code51 signing/CI exception was assumed.
Dependencies restored from official artifacts: android-all
`15-robolectric-13954326`, OkHttp 4.12.0, Okio 3.6.0, Shizuku 13.1.5,
Filament/gltfio 1.56.0, OpenCV 4.13.0, JUnit 4.13.2, Hamcrest 1.3.
LiteRT's stand-in is compile-only and never a native inference claim.

Verified RED -> GREEN checkpoints:
navigation `561a8865` (11 / 4 failures -> 11 PASS);
research `25b7aeea` (16 / 2 failures -> 21 PASS; combined integration 55 PASS);
agent truth `82590f53` (3 / 1 failure -> coupled 36 PASS);
native adapter `f25dc510` (stale PARTIAL expectation -> 8 PASS).
All useful code checkpoints were committed, pushed and read back.

Lifecycle/defensive boundary evidence remains bounded: Activity pause cancels
ASR/web sessions, stops TTS/camera and marks rendering background; destroy
invalidates bot/tool actions and closes subscriptions/executors. #651 reuses
the render-budget subscription and releases it on close/failed open.
Manifest disables backup/cleartext; private jobs/update/callback components
are not exported, accessibility and Shizuku services have platform permissions.
These reads and negative tests are not a complete Android exploit/leak audit.
No signing material, owner permissions or model/update trust roots changed.

**Next handoff, not started:** authorized release-host Gradle/SDK validation
at the pinned candidate, followed by real Fold7 STOP/recovery/TTS/thermal and
natural-navigation/research acceptance. Astra receives #647-649 unchanged and
the context/reasoning results; Fable receives the pinned speech/visual merge
and independent regressions. SOL's combined candidate is #653.

Remaining functional work: persistent profile-bound bot work orders/status
and recovery UI; memory output #640; original #639 device reproduction.
Do not simply enable ordinary checkpoints for bot turns: their ephemeral
profile/permission leases must not be restored as an unrestricted owner run.
Reuse the existing scoped intake, checkpoint and receipt mechanisms with
fresh authorization; interrupted post-proposal actions need review, not replay.
No main merge, signing, publication or product acceptance is authorized by
this handoff. Working area closes with these limitations explicit.
