# Omega APK continuation — 11 November 2026

> **Current readback, 12 November 2026:** Code54 is already originally signed
> as unpublished device-test draft 442543223, source `9713854b`. The subsequent
> exact `921cc382` Android run is complete: 4,842 PASS / 1 native PDF host FAIL /
> 17 known SKIPs; UI 25/25 and NewApi lint PASS. The complete app tree is
> identical between those sources. See [current continuation](OMEGA-CODE54-RESUME-20261112.md)
> for actual source/run/artifact bindings and remaining gates. The checkpoints
> below are retained historical evidence, not the present build/signing status.

## Resume source and owner request

Continue the owner's requested memory, logic, extension and APK work from PR #768.
Verified remote base: `3cb8df27c42fbc6db03d05b0f9993a8650be43af`,
tree `888938b1a9921a1c2043041d9dbfeecf4cf7d942`.
Canonical inherited protocol: `docs/handoff/OMEGA-WEITERBEARBEITUNG-20261111.md`.
This continuation is stacked on that source; it does not replace the existing evidence.

## Work ownership

- Integrator: `integrate/omega-apk54-20261111`. P1 baseline, integration,
  source-bound regression, lint and the next unsigned ARM64 candidate.
- Memory: `work/omega-memory-20261111`. Finish and independently check the
  prior C candidate (`02d9260c9`), especially issue #758, correction history,
  ambiguity, conversational recall and context budgeting.
- Logic: `work/omega-logic-20261111`. Reproduce prior B tests (`231b8bad2`);
  issues #767/#759/#765, safe diagnosis, negation and direct app lookup.
- Extensions: `work/omega-scene-20261111`. Verified Cinema switch defects
  first; bounded P3 layout/shared-scene preparation with existing fallback.

Each helper uses a separate worktree. Heavy Gradle runs are serialized.
The integrator first runs P1 on the unchanged inherited source while helpers
prepare P2 changes independently. New feature code enters the common candidate
only after its relevant checks. This concurrency is requested by the owner;
it does not waive the integration gates.

## Current evidence

The current host uses an unprivileged user, unlike the prior root baseline.
The previous 26 environment-dependent Node failures must therefore be measured
again, neither inherited as current failures nor declared fixed by code changes.
Backend: 64 PASS, 0 FAIL, 0 SKIP on the inherited source.
The complete inherited core is 97 PASS / 1 FAIL / 0 SKIP (98 total), with only
the strict native PDF host failure. The complete Node baseline is
1,612 PASS / 3 FAIL / 19 SKIP (1,634 total); no full PASS is claimed.
The strict PDF decoder stays strict. Physical Fold7/GPU/audio/thermal
verification remains NOT_RUN.

## Product and release boundaries

The installed reference reported in issue #758 is Code53 / 0.3.52,
source `050a57cb019dcc1b7fe6cfa2fb5c11176b048e05`.
The next candidate is provisionally Code54 / 0.3.53; verify package, source,
version and ARM64 contents before offering it.
Original package, avatar, orb, user data and model weights stay under their
existing contracts. Experimental shared-scene rendering remains default off
until a real GPU check. An unsigned transport APK is not an installable update.
Exact-source original signing and any required owner environment review remain
separate from compilation. No main merge, installation or public release.

## Next checkpoint

Record complete P1 results and exact commands; integrate independently passing
helper changes; update the inherited matrix/register and this handoff.
Do not describe an interrupted run as passed. Do not mark a real device issue
fixed on the Fold7 without its new device result.

## Integration checkpoint

Merged without conflicts into the common candidate:

| Stream | Exact remote source | Verified worker evidence |
|---|---|---|
| Memory #770 | `dd0eda082bfcce624ecee9eb14a327d43d51b66d` (#776 follow-up) | 83/83 Kotlin/JUnit; Android pending |
| Logic #772 | `e20ca3a5656f4ccd81625d8e930e2fea85f48eed` | 94/94 Kotlin/JUnit; Android pending |
| Cinema #771 | `965ad4c51ec4c5a90fdb25ce8fc09d1d1f65202e` | 13/13 Kotlin/JUnit + 13/13 Node; Android pending |
| Master #747 | `c00326cedc6b697d5634dbc33de9ffcdd29fe58c` | Document-only delta reviewed |

The workers' real Activity/Canvas checks are now part of the common source.
They remain NOT_RUN until the integrator executes them. Source review includes
negation scope, foreign app intents, role checks before correction/bot entry,
session/revision invalidation, no UNKNOWN retry, and existing consent paths.

The inherited core completed on clean `3cb8df27` without source changes. Its full
journal, XML archive and log are preserved in the common evidence directory.
Next run focused combined Activity/Canvas checks and the required full Android,
visual and configured NewApi lint gates on the actual published source.
This recorded sequencing avoids two redundant full-suite runs; it removes no
assertion, strict PDF probe or acceptance gate.

### Node failures and retained diagnostics

The three complete-baseline failures concern two scheduler tests and the owner
abort watchdog. All three pass unchanged in isolated reproduction. Their
original cause is not proven. A new run with diagnostic assertion messages
retained an actual `TIMEOUT` receipt from the finite scheduler's 1,000-ms fixture
budget. Its other two cases passed. Only assertion messages changed; production,
limits and expected results did not. Raw evidence is in
`docs/evidence/omega-apk54-20261111/`.

Final Node runs must avoid concurrent Gradle compilation. Do not erase the
baseline failures or call them fixed because an isolated rerun passed.

### Explicitly open scope

P3 is deferred for this immediate candidate: a real common Filament scene also
requires HomeView viewport, avatar mask and coordinate-origin changes. Existing
stage/native fallback stays intact. No unfinished renderer is enabled.
General prompt-budget adaptation is not claimed fixed; direct diagnosis/app
routes and object recall now avoid needless model entry in their supported cases.
The reproduced ledger boundary overflow is repaired in #776 `dd0eda082`:
six objects with six attributes and 24 corrections now yield 1,595 instead of
16,221 UTF-8 bytes for an ordinary related request. Only prompt representation
changed: current values and uncertainty stay visible, omitted history is named,
internal history and historical replies remain intact. RED 2 PASS/3 FAIL;
GREEN 83/83. The real Activity class now has three cases, including this boundary.
Combined Android execution remains pending; this is not a release candidate.
P4–P6 device/account/team/thermal work is still open. Physical Fold7, native PDF,
GPU, audible speech and thermal acceptance remain NOT_RUN.

## Thermal integration and final candidate preparation

Thermal PR #773 `8decff576dc05ed3cdb0b8ded680b6098a1d8537` is integrated into
the common candidate, retaining #776 and the existing worker ancestry. All five
thermal production files, four new tests and the model-load Node contract are
byte-identical to the independently reviewed `1ba7b9458` version. Current-value
memory follow-up is retained exactly. Its coordinator reports 94/94 combined
JVM and 34/34 neighboring Node tests; those are bounded source checks, not the
new common Android or physical Fold7 acceptance.

Three independent integration reviews found no new blocker in single model
verification per load, exact-byte SWB1 status reuse or static/light-path work
reuse. Every load still hashes every model byte; owner statistics/audit remain
fresh. Light cadence and quality, model/thermal settings, weights and guards
are unchanged. The existing large-corrupt-file allocation before SWB1 decoding
is not repaired or introduced here. The 72 exact draw-command comparisons
cover `JarvisEnergyFlows`, not complete frames, GPUs or measured heat.

The external thermal coordinator's Android/lint run uses the shared heavy lock.
Canonical final Node, 25-class focused Android, full Android/visual and configured
NewApi lint will run on the published common source with no competing heavy
build. No original-signing or device gate is implied by source integration.

### Android test API compatibility correction

The thermal coordinator's actual Android compile rejected the new
`LocalModelLoadSourceTest` observer constructor: Android `Provider` accepts a
`Double` version here, not the newer JDK `String` overload used by the isolated
JVM proof. The exact compiler log is retained as
`thermal-android-compile-red.log.gz`. The one-line correction uses `1.0` instead
of `"1.0"`; digest assertions, provider cleanup and production sources do not
change. Canonical combined tests will execute the corrected helper. No Android
PASS is inferred from its earlier JVM success.

## Complete combined Node run and review follow-ups

Exact clean `c36258a853bbc6dacd8fbfa0dbae64f0f95a0d14` completed the full
Node profile without concurrent heavy Gradle: **2,498 total; 2,456 PASS, 5 FAIL,
37 SKIP**; complete evidence, process termination confirmed, source unchanged.
This profile includes `scripts/*.test.mjs` in addition to the inherited baseline's
`.github/continuum` files; different totals/skips are not a regression count.
Backend check and 64 backend cases pass. Raw compressed report/XML, failure
receipts, skipped-case reasons and command are under `node-combined-c36258a8/`.

Four failures are in `local-worker-process.test.mjs` (idle lease/STOP, finite
cycle, SIGTERM descendant restart, TIMEOUT descendant setup). The fifth is the
unreadable-younger-process fixture in `continuum-owned-process.test.mjs`.
The finite-cycle receipt proves TIMEOUT at its 1,000-ms fixture limit; this does
not prove every other failure has the same cause. Root checks both unchanged
files separately. No timeout, assertion or production guard has been relaxed.

Two newly retrieved review findings are being repaired on helper follow-ups:

- App verb-final matching misroutes plural conversational sentences such as
  “Wir starten”; preserve explicit app commands and existing exact-installed
  fallback/consent. Logic follow-up #778.
- The compact ledger projection omits history for several past correction/change
  phrasings; preserve compact current/planning tasks and explicit uncertainty.
  Memory follow-up, with independent extension-agent counterexamples.

The previously queued 25-class Android phase on c362 was explicitly superseded
BEFORE it acquired the lock or started Gradle. Its evidence-directory guard
prevents that obsolete phase from launching. No Android test was interrupted.
The replacement common source will include both verified fixes and 27 selected
classes (adding OwnerAppVerbFinalIntentTest and OwnerObjectStateLedgerHistoryPromptTest).
Full Android/visual, NewApi lint, unsigned Code54 build and artifact verification
remain to be executed on that actual published source.

## Reviewed follow-ups integrated before common Android QA

- #778 `258639206227371271d324cd1e4c3c7a5a379a7a`: verb-final/plural app
  routing. RED 10 cases: 3 PASS/7 FAIL on unchanged c362; GREEN 45/45 across
  six app/consent classes. Only LookupIntent and Clarification production
  files change; existing Activity fallback and action gates are reused.
- #779 `4485ef71a3e00c93656dcede7788a7ad1af4e8a8`: historical prompt selection.
  RED 12 cases: 3 PASS/9 FAIL; GREEN 95/95 including 83 memory/budget neighbors.
  Large ordinary related prompt remains 1,595 UTF-8 bytes. Internal facts,
  answer authority, uncertainty and truncation behavior are unchanged.

Both follow-ups have independent source review. Integration compared all 31
production/test input hashes against the green manifests: zero mismatches.
Four additional actual Activity cases are included; common Android execution
is still NOT_RUN. The focused harness now lists 27 mandatory classes.

The two Node process files pass unchanged in isolation: **37 PASS, 0 FAIL,
0 SKIP**, 56.02 seconds, exact clean c362 source unchanged. This does not prove
a shared cause for the five full-run failures. The final full Node invocation
will preserve all 354 files in the same order, the existing process supervisor,
outcome evaluator, inner worker concurrency, every assertion and every timeout
(including the 30-minute outer limit). Only outer test-file concurrency is one
instead of two. `harness/full-node-serial.mjs` checks the exact file inventory
and sources before/after and labels that execution variant explicitly. The
previous concurrency-two FAIL remains part of the evidence, not retroactively green.

## Complete final serial Node result / common Android started

Frozen common source `5b935b4feaf960bb6973dd4d630f02744e41913f`, tree `246ff9ffbbb3d17e59147806c90bb0b47a7743ef`. Full 354-file ordered Node profile completed at 17:14 UTC: **2455 PASS / 6 FAIL / 37 SKIP (2498 total)**. Source unchanged, not cancelled, termination confirmed, evidence complete. Backend check and64cases pass. Serialization did not fix the complete gate; previous five-failure concurrency-two evidence remains. Six current failures cover typed observation/code-index CLI output, overlapping MCP reads, and three process-start/cleanup fixtures. Root requested source-bound read-only triage; no common-cause assumption or test weakening. Evidence: `docs/evidence/omega-apk54-20261111/node-serial-5b935b4f/`.

Focused 27-class Android plus NewApi lint started at 17:14:02 UTC on this exact source; full unfiltered Android is queued behind it and requires focused PASS. No APK has been produced at this checkpoint. The unsigned build runner/validator passed independent source review including final artifact/build-output hash equality; this is preparation, not a build, install, device or release result.

## Completed first common Android phase

Exact clean5b935b4f: **205tests,198PASS,7FAIL,0SKIP**, all27selectedclasses executed, source unchanged. Configured NewApi lint completed with0issues. Raw journal, XML archive, Gradlelog and lint outputs: `docs/evidence/omega-apk54-20261111/android-focused-5b935b4f/`.

The seven failures belong to three UI classes. STOP test includes the intended generation invalidation in its lookup delta; four Memory cases eagerly evaluate an assistant-transcript diagnostic before the first reply; repeated Home navigation and ordinary plural conversation routing are under repair in #781/#782. All original assertions/failures remain recorded. No full Android run has started: its queued driver rejected the failed prerequisite before invoking Gradle. Next: integrate independently reviewed repairs, verify the three affected Activity classes on the new frozen source, then execute unfiltered Android and the unsigned APK build/validation.

## Integrated fixes after the complete Android RED

#781 `32f217480915f45fb48c0d3952bcff117c5788d6` repairs a real Safety routing defect: complete simple collective statements such as “Wir starten” reach conversation, while mixed requests, suffix imperatives, role/negation gates and tool authority remain protected. Final pure Kotlin proof is **101/101**, all21 input hashes match the integrated files. Independent review rejected two broader intermediate candidates; their RED evidence is retained. Only `OwnerDeviceRequestSafety.kt` changes production behavior. STOP explicitly expects generation+1 and later no additional lookup; Planning enters CHAT only when necessary.

#782 `69cd1cb66ef1d48d7fb867ec12b1b9efd2472413` fixes Memory UI setup: timeout diagnostics are evaluated lazily, and subsequent turns stay in the active chat. Runtime, memory, prompt and10-second-wait assertions are unchanged. All three merged UI files match the independent review hashes. These repaired Activity tests have not yet run together.

The committed runner `harness/run-android-phase.py` verifies an exact clean SHA, all harness hashes, a fresh complete source-bound journal and stable source/driver. Three entire failing UI classes plus configured NewApi lint run first. The full phase requires that same-source UI PASS, has no class filters, retains the unchanged Code52 XML gate, and additionally requires exactly one SUCCESS for the real native PDF probe; a skipped native probe can never become full PASS. This closes a review finding about inherited JVM allowances without changing any product test.

Next: freeze/publish the integrated source; run UI recheck then full Android/visual; build the actual unsigned Code54 artifact and validate package, ARM64 libraries, assets, alignment, signature absence and frozen build provenance. Full Node remains the documented2455/6/37 FAIL, not retroactively green. Original signing, installation and physical Fold7 PDF/GPU/audio/heat acceptance remain separate pending gates.

## Complete c163 UI recheck and bounded fixture follow-ups

Exact clean `c163641b400f1fcdd02c1d2952fde1c8e76a0ad1` finished the three complete UI classes: **25 total,11 PASS,14 FAIL,0 SKIP**. Source unchanged; all three required classes observed. Configured NewApi lint completed successfully. Its analysis tasks executed on c163; Gradle reused identical `lintReportDebug` outputs as UP-TO-DATE. Their original timestamps and hashes are preserved, not labeled newly emitted XML. Full Android rejected the failed UI prerequisite before Gradle and remains NOT_RUN. Complete journal, XML, failure records and log: `android-ui-recheck-c163641b/`.

Thirteen failures occur before submitting the request: the new strict state checks precede Android View's posted PerformClick in the PAUSED SDK28 fixture. The app callback itself sets CHAT and active conversation. The next helper revision waits for both conditions with the existing10-second helper, then retains both assertions and all request/model/memory checks. The other failure is the expected memory-center dialog left open by navigation; its close button must be used before sending the next request, while pending-name invalidation is asserted before that close. Existing5-second wait and every no-lookup/no-launch/no-dialog assertion remain. Independent source review found no blocker; actual repaired UI execution is still pending.

The owner's requested contents overview is retained under `contents-c163641b/`, with48 independently checked old/new Git-blob comparisons against installed-source050a57cb. Cooling mode/ThermalGuard/LocalModelHost are byte-identical to that baseline; new thermal work removes duplicate computation. No measured Fold7 temperature claim or completed shared3D-world claim follows. No APK has yet been built.

Integrated follow-ups: #783 `724331330f7ee3f676357b449c888f506645b681` and #784 `288a351e27c1dd91502bb8037cdcb19ca86db57f`. All three UI SHA256 values match the independent review, and all21 final pure Safety inputs remain byte-identical. There is no production-source delta from c163. Next frozen candidate must execute all25 UI cases, then the full unfiltered suite; previous failures stay recorded.
