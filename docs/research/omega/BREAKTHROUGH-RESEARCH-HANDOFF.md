# Breakthrough research handoff — 2026-11-03

Branch: `codex/breakthrough-research-20261103`, Draft [PR401](https://github.com/Ullmann27/device-ai/pull/401), base `codex/team-chief/omega-20261103` at `64ec06a73f50cae9ee49035c840710d87777d50f`.

R1 code frozen at [6308ccade5c482a412030a2cebafa1143f920451](https://github.com/Ullmann27/device-ai/commit/6308ccade5c482a412030a2cebafa1143f920451). ResearchEngine.kt SHA256 `b51c363635aa829f9f8bd7c1443524957ea902cf520b07ffec84ac517515b2c6`. Status: **NARROW_JVM_PASS; independent QA pending; MODEL/DEVICE_NOT_RUN**. Quota **NOT_VISIBLE**.

## Change and existing connection

EXP-06/R1 binds extracted claims in the existing `DeepResearchEngine` to the actual supplying, hash-checked document. Only a trimmed literal substring is admitted; engine-owned external provenance, default confidence and retrieval time replace extractor assertions. A proposed source list cannot manufacture corroboration from another fetched page. Exact quote variants aggregate; the source count now uses the same distinct-domain heuristic as validation. Claim/relationship IDs and text pass through the existing prompt sanitizer; graph SOURCE numbers derive from displayed evidence URLs.

Only production file changed: `app/src/main/java/at/heinz/deviceai/cognitive/ResearchEngine.kt`. No new scheduler/helper/store. Existing path: `AgentActivity.runResearch` or `createToolGate` → `ApprovedWebResearchToolRunner` → `DeepResearchEngine` → `ResearchPromptBoundary` → existing synthesis. Historical `boundedWebResearch` symbol is absent at this baseline. Current production has only `BaselineClaimExtractor`, which already emits literal extracts. No existing paraphrase extractor was removed. Consent, ToolGate, RIS providers, owner memory, CCC1, CDX2, weights, signing and release files were not edited.

Source references and method review: `docs/evidence/breakthrough-research-20261103/SOURCE-REVIEW.md`. Actual fetch dates/URLs/licenses/hashes are in the neighboring JSON manifests. ALCE primary literature/open MIT evaluation implementation and W3C quote-selector standard informed attribution-versus-entailment scope; no external code/model/dataset was imported and no published score was claimed as our result.

## Pre-registration and reproducible results

- [5a9f287671dfa6b40aa8fd39469c11e97e1fad35](https://github.com/Ullmann27/device-ai/commit/5a9f287671dfa6b40aa8fd39469c11e97e1fad35): preregistration + first remote checkpoint before code.
- `17c7a34d`: 10 frozen development JUnit cases; two initial Claim/List return-type harness mistakes corrected before any baseline evaluation in `6ff9a183`. The failure log is retained, not called a product failure.
- Baseline **44/50**, candidate **50/50** using identical test bytes. Within this total, 10 new development cases improve **4→10**; 40 existing Research/Engine/Sanitizer/Negation/QuestionFocus/UntrustedEvidence regressions remain **40/40**. Positive literal Unicode/negative statements, real corroboration, mirror suppression and bad-hash rejection are retained. Development cases are authored, not blind.
- Initial candidate test compile failed because the narrow runner omitted the freshly compiled directory from Kotlin friend paths; harness fixed with no production change. Raw logs retained.
- QA separately froze **16 unknown document/claim/prompt programs** at `0ca2b075`, fixture SHA256 `1d3fadb7c3a6fdbb9a31f440f7c756161847280f74eaf031d50cc4d95d9f848c`, before inspecting candidate outputs/code. Candidate freeze has been delivered. Results pending; no tuning on holdout.

`docs/evidence/breakthrough-research-20261103/comparison.json`, `baseline-all-junit.log`, `candidate-all-junit.log`, compiler/environment logs, and `development-fixtures.sha256` contain the raw accounting. Full original baseline ResearchEngine.kt was separately recompiled; both runs use dependency classes from the independently verified baseline64ec Android build in `/workspace/continuum-quality/app/build/tmp/kotlin-classes/debug`. Those shared classes were used only as dependency/friend paths; candidate ResearchEngine.kt overrides them on the classpath.

Reproduce narrow candidate check:

```bash
docs/evidence/breakthrough-research-20261103/run-research-jvm.sh \
  /workspace/breakthrough-research /workspace/breakthrough-research \
  /workspace/continuum-quality/app/build/tmp/kotlin-classes/debug \
  /tmp/research-r1-recheck
```

This runner requires the provided `/workspace/continuum-build-env/env.sh` and baseline dependency classes from a separately verified full build. It is a JVM check, not a substitute for the combined Android/Gradle/lint gate. No heavy Gradle was started by this lane. `git diff --check` passes at code freeze.

## Boundaries and continuation

Literal occurrence does not prove entailment, factual truth, freshness or correct interpretation of negation/conditional context. Domain/mirror heuristics do not prove separately owned sources. Sanitization closes a known raw prompt surface but does not guarantee LLM injection immunity. The existing VERIFIED enum still represents the engine's limited corroboration heuristic. Real local synthesis, live research sessions, model accuracy, latency, energy, Fold7 and signing/install remain NOT_RUN.

The strict literal grouping uses exact-text claim IDs rather than case-folded IDs; claims are recomputed by the existing engine, with no file-format migration or memory writes. Any future paraphrase extractor needs its own evidence contract.

Next step: record untouched QA results for frozen6308, then Chief review/integration against the exact source and full combined gate. If independent cases fail, keep the result visible; any subsequent repair needs a fresh independent holdout. Rollback is a normal revert of isolated R1 production/test changes; no state or weight rollback is required. No main merge, branch deletion, APK publication or installation is authorized here.
