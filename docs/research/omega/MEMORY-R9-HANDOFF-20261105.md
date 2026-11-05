# MEMORY R9 — negation-only search must not invent positive evidence

Status **BOUND-JVM-HOST-20/20 PASS / Android & project holdout NOT_RUN / C1 pending**. Date: 2026-11-05.

## Ownership and immutable intake
- Existing C1 integration is [#519](https://github.com/Ullmann27/device-ai/pull/519), observed at `13c37f55789bf67b07beeb37cc1a5035526e623e`; its central `ASTRA-NUCLEUS-HANDOFF-20261104.md` was read. C1 already integrated the separate #522 memory/mapper fixes. R4/#529 and R5/#531 are separate pending specialist contributions.
- R6/#532 `5073cdc0ee96687d3b17cfefa8710174c8514b96` → R7/#533 `a1e0ceca7fbbbc378e508f16868bcac2f676a222` → R8/#534 `6b79a9c001f7607db1511d2398b62b744e362e50` → **this stacked R9 draft**. C1 alone integrates the shared tree. No changes to Activity, factory, voice, worker, privacy/mapper, model, signing, owner data, offline or release.
- First test freeze `1fec1fc258449e629ee4a6e5ab89f1381fcc0994` (five R9 methods, blob `70b70b0ab8ca316c3c835cbc0857562f91a30047`) **before** production edit. Production fix `1ca1edca7865ef804dac307c82814b8167e2c5a5`: only `app/src/main/java/at/heinz/deviceai/learning/ContinuumMemoryRetriever.kt`, original blob `b0200e5246130d056ce02eb570f69dbcfa641ae5` → blob `12c1c716c57bc9656897a6428ed0ee1c4f443142`.
- Four independent near-neighbor holdouts were added in `21866a8561897e4d8ca78d7792472a7b4f1f30bc` after the production edit and **then executed on both untouched R8 baseline and R9 patch**; they are not mislabeled as pre-fix commit. Their blob is `bf2fa57a1976dde44e20b1aada5f2e5d2e5e8a71`.

## Actual defects and bounded repairs
1. **BM25 polarity term fuzzy alias:** `GermanText.stem("nicht") == "nich"` and `stem("Nische") == "nisch"`; `editDistance("nisch","nich",1)==1`. Similarly `kein` can be autocorrected to `Keim`. If no exact negation posting existed, `resolve` could fabricate hits from unrelated positive text. Following exact-vocabulary lookup, R9 returns **no fuzzy correction** for known negation stems; nonnegation typo correction remains active.
2. **Character n-gram partial negation match:** `"nicht"` and `"Licht"` (also `"kein"`/`"Keim"`) share 3–5-grams, so an exclusively negating query could previously return positive evidence with a nonzero cosine. For a query containing **exactly one non-stopword stem that is an explicit negation**, only documents containing that **same stem** are eligible; other queries retain existing ranking. The source of truth is still each `MemoryDoc`; `confirmed` remains copied, never promoted.
3. **Regression boundary:** Both index snapshots (R6), both atomic index generations (R7), R8 negation/tokenization and `k<=0` remain unchanged. Ordinary typo recovery and positive-evidence retrieval are separately asserted.

The initially frozen BM25 test using `Licht` was already green on R8 because `resolve` requires the first character to match. This was **not** a BM25 failure; the later `Nische` and `Keim` holdouts demonstrate the actual same-initial-letter failure. Its original test was retained unchanged.

## Measured execution
Read the **complete** 198-line R9 Kotlin production file and unchanged R8 production file from GitHub; independently reconstructed local files have verified exact Git object hashes `12c1c716c57bc9656897a6428ed0ee1c4f443142` and `b0200e5246130d056ce02eb570f69dbcfa641ae5`. All five frozen R6/R7/R8/R9 test source files were likewise byte-exact against GitHub. Compiled with **kotlinc-jvm 1.9.0, JRE 21.0.12.1**, real complete retriever production file, original project test method bodies and a restricted host stub only for `EventEmittingRetriever` and the JUnit annotation/assert methods. A reflection runner invoked every method.

- Unchanged R8 baseline with all 20 R6–R9 cases: **compile exit 0; 16 PASS / 4 FAIL; test exit 1**.
- R9 production fix, same 20 test files/methods: **compile exit 0; 20 PASS / 0 FAIL; test exit 0**.
- Four baseline failures are exactly bare `nicht`→Licht (n-gram), `nicht`→Nische (BM25), `kein`→Keim (BM25), and `kein`→Keim (n-gram). The original first BM25/Licht test passes both baseline and fix.
- Source-bound Kotlin runner published at `tools/memory-r9-exact-negation/run.sh` for subsequent execution in a complete checkout. Transcripts are Git-blob identical to the measured local files. This published script was **not itself executed** from the GitHub repository in this run; the equivalent Kotlin compilation/reflection tests above were.
- Raw RED and GREEN: `docs/evidence/memory-r9-negation-20261105/{host-red20.txt,host-green20.txt}`; provenance, SHA256 transcript hashes and test source identities: `RESULT.json`. No fabricated Gradle or device logs.

## Boundaries, fail criteria, next handoff
This is not broad natural-language contradiction detection. Multiple-content-word negative queries can still return partially matching positive documents, and morphological equivalence outside the enumerated stem set is not promised. Added filtering may change the 44-question retrieval holdout ranking; **C1 must measure that before accepting this patch**. No claim that C1 has merged R6/R7/R8/R9.

C1 action order: independently take R6, R7, R8 and only then R9 into the current common source with file/blob review; compile/execute the original Android JUnit methods and full TemporalCue/retrieval tests; run the 1,000-document/44-question holdout with ranking comparison (historical Recall@3 42/44); then complete critical, full-node, backend, full Android Unit/UI without class filters, NewApi and eventual Fold7. At any red gate, do not promote this specialist patch. Roll back only R9's bounded production diff; retain frozen tests/negative receipts. C2 ACK and APK gates remain separate.

No paid resources, actions, sub-bots, main merge, signing, install, release, APK or owner changes.
