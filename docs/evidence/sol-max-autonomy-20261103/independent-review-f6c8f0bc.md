# Independent review — PR #362, first autonomy checkpoint

Reviewed range: `a68d86fe63fec5ed6e418016e3c5dc64987eda0b..f6c8f0bc301c982ac39098afcfe28e8cb6c3596e`.
Branch supplied: `research/continuum-sol-max-autonomy-20261103`.
Review date: 2026-11-03. This report applies to that exact HEAD, before any response to the findings.

The review was read-only on the checkout. I did not change repository files, the index, HEAD, branches, or Git configuration; did not run Gradle or Node suites; and did not access a device, remote publisher, weights, signing material, or private owner data. This report is the only file I wrote. After the implementer announced a forthcoming fix, further source inspection used the pinned Git object.

## Strengths

- The production caller actually invokes the new `LocalToolContinuation.submit` helper ([AgentActivity.kt](/workspace/continuum-jarvis/app/src/main/java/at/heinz/deviceai/AgentActivity.kt:6454)). The change uses the existing runtime and brain; it adds no model, worker, database, network path, permissions, or persistence.
- The lazy encoding change is correctly scoped at the helper level: `forceFresh` streams the supplied prompt without evaluating `responses()`; other turns evaluate it once, use `respondToTools` for nonempty responses, and use `stream` for empty responses ([LocalToolContinuation.kt](/workspace/continuum-jarvis/app/src/main/java/at/heinz/deviceai/localmodel/LocalToolContinuation.kt:12)).
- Actual continuation bounds remain intact: 8,000 serialized characters per response, 16,000 input bytes, four responses, cumulative native context checks, and output reserve. LiteRT still constructs `Content.ToolResponse` / `Message.tool` for continuations ([LiteRtLmLocalModelRuntime.kt](/workspace/continuum-jarvis/app/src/main/java/at/heinz/deviceai/localmodel/LiteRtLmLocalModelRuntime.kt:192), [context admission](/workspace/continuum-jarvis/app/src/main/java/at/heinz/deviceai/localmodel/LiteRtLmLocalModelRuntime.kt:341)).
- Synchronous encoder exceptions now occur inside `generateLocal`'s existing `runCatching` and are returned through `generation.failure` to the established error UI. I found no new exception-delivery or retry regression ([AgentActivity.kt](/workspace/continuum-jarvis/app/src/main/java/at/heinz/deviceai/AgentActivity.kt:7306)).
- The eight previous tests remain, and four new tests cover the helper's screen, overflow, multilingual correction, and empty-observation branches. The supplied RED XML records 12 executed / 1 failure / 0 skips; GREEN XML records 12 executed / 0 failures / 0 skips. All ten artifact hashes in `files.sha256.json` matched independently. These are inspected historical run records, not suites rerun by this reviewer.
- Device acceptance is honestly marked NOT_RUN. The prepared procedure uses real existing entrypoints, distinguishes default deterministic CCC1 repair from model-origin repair, preserves executor isolation, and correctly states that the Android registry has no general `read_file`. The verified Code-38 artifact is explicitly separated from the research branch and a future authorized update.

## Issues

### Critical

None found.

### Important — the real screen path still fails before the new helper

Primary location: [AgentActivity.kt:6447](/workspace/continuum-jarvis/app/src/main/java/at/heinz/deviceai/AgentActivity.kt:6447).
Related new test: [LocalToolContinuationTest.kt:117](/workspace/continuum-jarvis/app/src/test/java/at/heinz/deviceai/localmodel/LocalToolContinuationTest.kt:117).
Related claim: [handoff:75](/workspace/continuum-jarvis/docs/research/omega/SOL-MAX-AUTONOMY-HANDOFF-20261103.md:75) and [acceptance:141](/workspace/continuum-jarvis/docs/research/omega/SOL-MAX-FOLD7-AUTONOMY-ACCEPTANCE-20261103.md:141).

`ScreenObservationPrompt.frame` always creates a multiline string. The caller puts it into a JSON string field using `observationData` at lines 6419–6420, so the full prompt contains escaped newlines and quotes. The final integrity gate nevertheless searches for the unescaped frame using `prompt.contains(it)`. A valid frame therefore throws `SCREEN_CONTEXT_EXCEEDS_MODEL_BUDGET` before `submit` can run.

I independently invoked the existing compiled production helpers in an in-memory Java source probe, using the new positive test's exact valid snapshot fixture and `LocalPromptFrame`. No source or class files were written. Results:

```text
validFrame=true
promptBytes=8543
productionPromptContainsFrame=false
JSONRoundTripEqualsFrame=true
encodedContinuationRefused=true
```

The new positive test omits the real caller's final admission check. Adding the caller's `check(prompt.contains(output))` immediately after its prompt assembly reproduces the failure before the recording adapter is called. Its current GREEN result proves the helper branch, not an admitted production screen turn.

**Attribution:** the faulty containment check is already present in BASE; this diff did not introduce it. It is still a substantive blocker to the explicit objective that a permitted full screen prompt reaches the engine. The new test and handoff currently overstate that objective's coverage.

**Recommendation:** preserve the atomic-frame integrity gate, but compare the same serialized representation actually inserted into the prompt. A full `JSONObject.quote(output)` membership check is sufficient for this integrity-only gate while `LocalPromptFrame` preserves the entire required body and authority remains separately checked; matching the complete serialized observation would tie it more precisely to the intended field. Exercise that exact production gate in RED→GREEN coverage, including missing, truncated, changed late-negation, and merely nested/escaped-copy controls. Narrow the initial helper-test claim and record the separate caller correction.

The implementer acknowledged this finding and announced a fix during review. I have not reviewed or verified that later change; this report's assessment remains pinned to f6c8f0bc.

### Minor

No additional formal findings.

## Recommendations and checked boundaries

1. Resolve the Important caller admission blocker before claiming the screen-turn repair complete. Reuse the valid screen fixture for an actual-continuation negative control as well; the present continuation negative uses a separate oversized string, though static inspection and my probe confirm that the screen fixture itself is also refused by the encoder.
2. Retain the original RED and GREEN evidence, and add separately identified evidence for the final caller gate. Do not reinterpret the original 12-test run as Android-window or native-inference validation.
3. Keep complete-suite outcomes separate from this review. The handoff accurately says they were pending at this checkpoint. The implementer reported an external harness report-directory failure during review; I did not rerun or diagnose that suite and do not count the report as a production regression or a PASS.
4. Optional reproducibility improvement: make `run-focused-gradle.sh` resolve its checked-in init filter beside the script instead of requiring `/workspace/autonomy-20261103/screen-focused.init.gradle`. The README already requires host-path adaptation, so this is not a blocker.
5. Grant/source/hash/expiry validation and pre-/post-generation epoch checks remain in the caller. STOP still cancels the shared cancellation token, orchestrator, reasoning router, and runtime. UNKNOWN/replay still returns before another model step and cannot become fresh learning evidence. The change adds no bypass; device timing was not tested.
6. Model-neutral compatibility remains an interface-level dispatch choice. Default text adapters keep their existing bounded fallback, and LiteRT keeps its native tool channel. No model identity special case was added.

## Declined to judge

- Actual Fold7 inference quality, tokenization, throughput, temperature, battery impact, screen-window capture, JNI cancellation timing, and OS permission behavior: no device/weights were available; these remain NOT_RUN.
- End-to-end autonomous model-generated CCC1 repair, Android task execution, worker cycles, and learning quality: this checkpoint prepares owner tests and changes only screen/tool handoff; host adapters are not evidence of those milestones.
- Current installed APK/model identity and the live publisher's current catalog: I inspected the documentation's claim boundaries but did not contact the device or re-download the artifact.
- Full Gradle/Node/lint/assembly results and the implementer's later harness failure/correction: no suites were run by this reviewer; original focused logs were inspected only.
- The implementer's forthcoming fix after f6c8f0bc: outside the reviewed Git range; requires its own RED→GREEN verification by the implementer.
- The previously reported QA root-alias portability defect and directory-sync Windows limitation: unchanged by this diff; no Windows execution or new portability audit was performed.
- Legacy `render-continuum-v37-qa.sh`'s unpinned JDK download: unchanged in BASE/HEAD and not the current v38 publisher path. The latter calls `render-continuum-integration-qa.sh`, which pins the JDK URL/SHA and checks the runtime version at lines 52–72. A general historical QA cleanup is outside this change.
- Main merge, release publication, signing, installation, owner authorization, or device acceptance approval: not requested or provided by this review.

## Assessment

**With fixes for the stated research objective.** The helper-level change is small and preserves existing continuation, privacy, and cancellation contracts, but the unchanged real caller gate prevents the advertised screen turn from reaching it. Correct that gate, cover it through the production path, and update the evidence claim before treating the screen repair as complete. This review provides no main-merge or release approval.
