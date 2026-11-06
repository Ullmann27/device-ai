# Code47 context and identity repair — 2026-11-06

Status: focused real-source Kotlin/JUnit PASS, 160/160. Activity integration and full SDK verification are separate Root gates. DEVICE_NOT_RUN / MODEL_NOT_RUN / APK_NOT_BUILT.

## Source and ownership

Base: `5572edfbb02570ae479a8f0f72179f5ca33027c5`, PR #578.
Branch: `codex/continuum47-context-identity-20261106`.
Target: `codex/continuum-code47-qa-candidate-20261106`.
Experiments: EXP-05 (local model context), EXP-12 (identity and checkpoint continuity); no research promotion.

This branch owns conversation retention/transcript restore, bounded follow-up framing, compiler identity, Bootstrap identity and focused tests. AgentActivity is edited only by the action/UI agent; BoundedAgent only by the output agent. Integration contracts are supplied separately. No model training, deployment, signing, real external message or main merge.

## Repair order

1. Preserve complete admitted owner requests through persistence. Never restore a clipped legacy prefix as complete evidence; missing/oversized/corrupt turns must not resurrect an earlier contradicted assertion.
2. Keep useful bounded dialogue/continuation context, with explicit completeness and the actual final answer section. Preserve the existing total prompt/runtime budgets and tool/privacy boundaries.
3. Project one truthful app identity, validated chosen name and actual loaded model into mandatory context; align fixed self-description. Pure memory recall retains the dialogue; revocation remains invalidating.

## Final interfaces

- `LocalAssistantIdentity.safeName(chosenName: String?): String` for Bot1's name-aware input routing; no second name store.
- Version2 transcript rows carry explicit request/response completeness, model/non-model origin and whether the real answer end is retained. `restore(id,turn)` consumes all metadata without recording the same row again. `onTurnRecorded(id,turn)` persists model, partial, unanswered and informational owner turns once.
- `composeFrame` returns a task and `ownerTurnsIn(promptBody)`. Owner text is omitted from the history only when the full generated task survived unchanged in the actual required prompt body. Failed/changed/redacted frames provide no deduplication proof.
- The budgeted render overload takes `maxBytes`, `requireCompleteOwnerHistory`, `continuation` and `ownerTurnsInTask`. The original render overload remains available. The actual remaining character/UTF8 capacity comes from `LocalPromptFrame.historyBudget` after the required clock tail is built.
- Partial model text is untrusted dialogue only, never confirmed memory, action success or owner evidence.

## Verification

Actual JDK17.0.20.1+1 / Kotlin2.4.0 / JUnit4.13.2 focused run: 12 real changed production sources, 28 test classes, 160 tests, zero failures. Dependencies are Root's complete Android36 SDK production classes from source `24547da95790ca43b0293ed06b4f2bd6e5a7814c`, compiled with the true SDK before this run; no production stubs. The relevant original regression sources are byte-identical between that SDK source and this branch's base5572edf.

Raw compiler/JUnit outputs, the exact source/test manifests, source SHA256 values and baseline-class manifest digest are under `docs/evidence/code47-context-identity-20261106/final/`. Earlier RED/GREEN logs remain under `transcript/` and `intermediate/`.

Baseline RED: two of two long transcript roundtrips fail; continuation intent fails one of two controls; the2048 Bootstrap gate fails one of three long-dialogue controls; actual retained answer-end plus mandatory identity fail three of four tests while private-bot isolation passes. All are included in the final160-test GREEN run. Its first run had two assertion-fixture issues: the deliberately changed greeting wording and a byte-boundary sample below the compiler's minimum1024. Corrected wording and additional required sample context preserve both contracts; the production sources did not change between those two focused runs.

Target controls: late negation beyond 1,000 characters, legacy clipped rows, malformed rows, reset/revocation, Unicode byte boundaries, continuation after a long/partial answer, required clock tail, name/profile injection, true model binding and memory-recall context retention.

## Resume and rollback

Read this branch's actual remote HEAD and diffs. Apply `CODE47-CONTEXT-ACTIVITY-INTEGRATION-20261106.md` in Bot1's Activity, then run Root's full combined SDK verification. Revert this branch's commits before integration to return to the candidate; do not delete or rewrite existing transcripts. Legacy clipped rows remain conservative when read by this repair; no transcript rewrite or migration runs.

## Checkpoint 1 — transcript completeness

Real Kotlin2.4.0/JDK17/JUnit4.13.2 regression: original Store source from candidate5572edf fails both new long-request roundtrip tests; repaired Store passes both plus its five existing tests (7/7). Synthetic late negations and multilingual tails are preserved. This is a Store-only proof, not yet the Activity/retained-context integration.

Implemented WIP: version2 context records carry requestComplete, responseComplete, modelResponse and responseHasEnd. Full admitted owner requests share the existing7900-character/UTF-8-byte task bound. Legacy records at the old1000/2000 clipping boundaries remain explicitly incomplete; malformed/unknown context rows invalidate older context. New per-turn callback includes failed/unanswered owner turns so later Activity integration can persist them. RAM restore consumes metadata rather than treating prefixes as originals.

This was an intermediate Store-only checkpoint. The final focused run now covers metadata/corruption/restore, budgeted context, identity and compiler behavior as described above; it does not replace the combined Activity/SDK gate.

## Completed bounded repairs

- Owner text: full admitted requests retain late negations across the real context callback, JSONL storage and a newly constructed context. Over-limit or legacy1000-character rows cannot revive shorter old assertions. Failed/unanswered corrections are persisted too. Explicit resets and memory changes still invalidate prior context.
- Dialogue: complete owner units take precedence. Optional history keeps a contiguous newest suffix, never skips a large newer correction to reveal old claims. Long model replies use their actual final section with an excerpt/completeness label; legacy2000-character prefixes cannot become continuation bases. Ordinary short units are retained whole. No blind increase of the7900 task,15500 prompt or16000 runtime caps.
- Follow-up duplication: owner text already admitted in the actual whole task is replaced by an explicit reference in history. A modified/redacted/clipped task does not suppress original history; strict `requireHistory`, byte limits and the required clock tail remain enforced.
- Identity: the validated chosen name and actual imported model id/version/SHA are required compiler data. A bounded profile projection admits only enumerated communication preferences and existing numeric traits. App name does not change with model choice. Fixed identity replies receive current runtime readiness; owner identity stays out of private bot frames.
- Memory read: `MEMORY_RECALL` retains prior dialogue and records a fixed informational label, not retrieved memory text. Real memory changes and revocation keep their invalidation behavior.
- Bootstrap long dialogue: original `BootstrapBrain.decide` rejected every input beyond2048 before `presentBootstrapDecision` could call `runContinuumDialogue`. Normal complete input now follows that existing path through `allowNeuralRecall=true`, `LocalFirstPolicy.localModelQuestion`, `runLocalAgent` and the unchanged7900 task gate. Explicit memory/training/revision commands and unsafe control text keep the narrow blocked path; the current long request remains intact, including its late negation.

## Remaining verification boundary

No actual Fold7/model behavior was executed here. These tests prove construction, retention, admission and bounded data flow; they do not prove model attention, factual quality or an actual model switch. Root owns the complete combined Activity build, full test suite, signed APK and release readback after integration.
