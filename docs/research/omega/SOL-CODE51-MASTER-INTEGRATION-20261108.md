# Code51 master integration

Status: WIP / UNVERIFIED; Draft PR #650. Owner canon #642, execution #643; read #637-641.
No main merge, signing, release, installation, private data or signer access.

## Frozen source graph

| Ref / PR | Inspected SHA | Responsibility |
|---|---|---|
| Code50 / #630 | `473f79727e4b52509cc7d1af71e59b80ab9b6be5` | Original source baseline; not main |
| main | `52997ee3d94686add646d53fb11fd0b8d9e1e94f` | 54-file skeleton; not the integration target |
| #646 | `f3f90736f4addb027dcc4f42a1f3cd3300713d3b` | Fable speech recovery / model status / thermal budget |
| #647 | `f93b661ff2652b0783ab6224810edd85b8c368ff` | Cognitive discourse pilot |
| #648 (stacked on #647) | `9b8bf1d947323cecc75d6dd7dd2c1da8e73870b3` | Hypothesis prior / diagnostic pilot |
| This branch | `sol/code51-master-integration-20261108` | Existing UI/router/web integration and independent regression tests |

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

Restored JDK21, Kotlin 2.2.21 and pinned Maven Central compile/JUnit jars after
actual prerequisite failures. Gradle QA on JDK21 still fails resolving AGP
8.10.0 from Google Maven (`dl.google.com` DNS unavailable). No alternate,
untrusted artifact mirror used. Gradle, lint, Robolectric and APK remain blocked.

## Scope and recovery

Reproduce #639, preserve negative file/app/compound cases, wire only existing
centers and the existing approved research path. Keep speech/cognitive changes
owned by their current PRs. Commit/push each meaningful checkpoint.
Rollback is reverting this branch's commits, never rewriting others' branches.
