# SOL brain/language continuation — 2026-10-25

Owner priority: **BRAIN → LANGUAGE → REASONING → LEARNING → AUTONOMY → CODING**. UI/APK work is deferred. The owner explicitly requests autonomous implementation; routine design choices below are the implementer's decisions. No additional design-approval round for these bounded repairs.

All previous work/results are remote in Draft PR #150 at `0222622df5595f2931b1cc86a7bacc3414a0acfb`, with tested implementation `31e26ca48d411a73af42ec7d752af9195fd4adc8`. This same official working branch continues; no main merge, signing, user-memory or checkpoint overwrite.

## Design and ordered plan

Use the existing Kotlin/Node memory runtime, byte decoder and local-model contracts. Two complementary paths are needed: repair deterministic evidence selection that currently suppresses real model use, and measure actual neural free generation rather than teacher-forced loss. Larger decoder redesign is contingent on baseline/holdout evidence; existing CDX1/SWB/CRE checkpoints retain their format and bytes.

1. **Exact identifier grounding (EXP-04/05/12):** original query `ORION-47`/`ORION-48` and short `R1`/`R2` lose numeric identity in current content-word features. Read-only independent Node reproduction confirmed identical scores for wrong hyphenated IDs, including SOURCE_TEXT. Add a bounded original-text eligibility check in mirrored retrieval paths without changing ordinary TF-IDF/CRE1 rank semantics. Begin with one explicit letter+digit identifier, case-insensitive literal token identity. Tests: exact positive, wrong number, short IDs, owner/source, revocation, no model/fact mutation, empty evidence/cleared topic and preserved CRE1 candidate set. Multiple-ID comparisons and general entity/relation entailment are separate problems, not silently claimed solved.
2. **CDX1 cause/quality benchmark (EXP-05):** reproduce generation from committed own snapshot and bounded paired training; record loss, actual text, EOS/budget, repetition and exact answers on frozen authored train/dev and separate evaluator cases. Eight-byte context loses the common `Antwort:` prefix's evidence/question; the frozen 16D conditioner is the only initial distinction. Device weights are unavailable, so do not claim reproduction of the exact Fold7 n-state. Protect old checkpoints, independent audit splits and negative results.
3. **Real Gemma host inference (EXP-05):** official PyPI metadata confirms Google LiteRT-LM CLI0.18.1/API package. Inspect official wheels/dependencies/source and checksums, use isolated local toolchain, existing SHA-verified original file, bounded CPU/context/output and synthetic German/logic/coding probes. No external API, personal-data upload, GPU, paid service or device inference claim. Native execution only after actual compatible package discovery; failures are evidence, not success.
4. **Actual main-dialogue intelligence:** independently audit SWB1/SWB2/CRE1, CTX3/CSX1/LM1 and decision routing. Integrate only proven useful components through current canonical dialogue/evidence/tool gates. Preserve negative CTX3/LM1/Synapse findings; do not rename deterministic extraction as neural intelligence.

## Baseline / evidence / stopping rules

Current brain work: ACTIVE / PARTIALLY_HOST_TESTED. The previous full suite is not proof of new changes. Write failing behavioral regressions before fixes, freeze benchmark definitions before candidate measurement, record separate model identities and data provenance. A fall in loss or an increased update counter is not an answer-quality improvement. New weights require separate namespace, old snapshots and explicit promotion conditions; no automatic owner-memory mutation.

At each validated step: exact commit, command, result and limits here; push to PR #150. Stop/rethink a hypothesis that fails, do not endlessly replay the same strategy. No free shell from model text or second backlog/repair framework.

Next entry: exact-ID regression and smallest mirrored fix, then actual decoder and Gemma measurements. Rollback by reverting individual new commits; previous source/weights remain recoverable.


## First measured repair checkpoint

The baseline failed all eight new Node ID cases and both new Kotlin tests. The mirrored original-text eligibility repair now passes 17/17 focused Node cases and 30/30 broader existing retrieval/false-positive/paraphrase/audit cases. Kotlin compilation against real SDK classes and the actual SDK module ABI (`-module-name DeviceAI_app_debug -jvm-target 17`) passes all 17 tests across the new identifier and existing memory dialogue suites, including golden cross-runtime scores, definitions, revocation and conversation reset. An initial mixed-module CLI harness produced NoSuchMethodError; matching the real module ABI fixed that harness issue, without changing product logic.

Actual main-dialog behavior: a wrong single explicit identifier yields no grounded memory IDs, clears its topic and cannot enter CRE1 reranking. Exact matches and original model/fact bytes remain unchanged. This is a bounded deterministic correctness improvement, not new neural capability or general entity/relation entailment. New full Android Gradle verification is pending.

Google's official native Python LiteRT-LM API0.18.1 Linux x86_64 wheel was discovered and SHA256 checked against official PyPI metadata, inspected and installed into an isolated local venv. It successfully imports and opens metadata from the selected Gemma original. This is not inference yet. Frozen synthetic four-case smoke and a source-pinned, tool-free CPU probe are included WIP; run only from a clean committed source under an external wall-time limit. Native process has an 8GiB address-space /180CPU-second bound, 2threads,2048context,128output and32thinking tokens. Existing Android runtime stays0.18.0; host measurements do not validate its Fold7 compatibility.

Archived text logs normalize trailing whitespace only; all diagnostic and result content is retained.


## Actual Gemma inference and independent identifier measurement

At frozen `bc33e09d7f25339b9d7617e90c124a8341726de1`, official LiteRT-LM API0.18.1 loaded the actual SHA-pinned Gemma original with the XNNPACK CPU backend, two threads and2048 context. All four pre-frozen synthetic smoke answers were exactly correct after the predefined punctuation normalization: 85, Wien, UNBEKANNT, NEIN. Native engine load measured4.96s; short answers1.69–2.00s; peak RSS3083192KiB. File hashing preceded loading, so this is NOT a cold-storage/device throughput benchmark. Native metadata reports thinking unsupported and context0 (unspecified); explicit context2048 worked. Report is HOST_ONLY, with no tools/training/promotion. The official wheel SHA and raw diagnostics are archived; GPU/NPU registration messages do not change the explicitly selected CPU backend.

Independent evaluator generated new identifier fixtures AFTER code freeze, without tuning: fixtureSHA29b06521a22dfc443a6a504ef463257244546f46d7d18b3b6b1d8bdcf260c669. Across288 rank decisions (96positive/96negative/96mixed), exact expected result sets improved100→244; wrong-hit queries144→0; all74 previously retrieved positive cases remained retrieved. Absolute positive recall remains74/96, so22 prior misses are NOT concealed. Separate source gate:48/96→80/96 correct, false accepts32→0, positive accepts unchanged32/48. All8 no-ID/golden result objects/scores are identical. Eight separate out-of-scope controls still expose multi-ID mismatch and ordinary owner-name/wrong-relation errors. This is bounded synthetic evidence-selection correctness, not general semantic/neural intelligence. Full fixtures/results and portable evaluator are committed; only checkout/output path selection differs from the executed independent generator.

Next: actual CDX1 free-generation baseline and deterministic-cycle diagnosis, plus Android SDK compilation of the new memory path. No old weights or hidden holdouts are repurposed as training.
