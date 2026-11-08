# Code51 master integration

Status: WIP / host integration; Draft PRs #650 and #653. No device acceptance.
Owner canon #642, execution #643; read #637-641.
No main merge, signing, release, installation, private data or signer access.

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

## Initial integration matrix

| Capability | Existing source / actual UI entry | Execution / missing connection |
|---|---|---|
| Memory | `showMemoryCenter`, confirmed facts / learning core | Manual UI connected; free-language routing must be reproduced |
| Logic / planning | `showLogicCenter`, dashboard action `logic` | Manual UI connected; no natural-language alias in current chat center switch |
| External apps | `OwnerAppLookupIntent -> OwnerAppActionFlow -> ToolGate` | Discovery is read-only; launch requires permission and fresh foreground receipt |
| PDF | `OwnerDocumentIntent -> openOrReuseDocument` | Android picker; distinguish file names from internal centers |
| Public research | `PublicKnowledgeTopicRouter -> WebToolRequest -> ApprovedWebToolGate -> DeepResearchEngine` | Existing real HTTP pipeline; narrow prefix recognition misses natural research commands |
| Voice | `ContinuousConversationMode`, Android ASR, local model, Android TTS | Existing half-duplex integration; #646 owns recovery. Physical playback/restart unverified here |
| Avatar / work scenes | `JarvisActionScenes`, cockpit events / task effects | Existing scene pipeline; #638 reference quality and Fold7 performance remain open |
| Bots | 20 profiles, local conversation / `ChiefWorkerPool` status diagnosis | Profiles are not executable capabilities; worker/job boundaries to audit |
| Development | Existing development backlog, teamleader, Android/Termux builder | READY diagnosis is not a completed coding job or APK proof |
| Cognitive pilots | Existing memory/context/reasoning plus #647/#648 | Research/holdout evidence is not a tool execution receipt |

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
