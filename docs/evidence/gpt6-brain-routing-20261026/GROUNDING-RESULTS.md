# Independent frozen grounding evaluation – completed 26 October 2026

The already-frozen 60-case synthetic suite from SOL PR151 was **actually executed** in an isolated JavaScript runtime against the exact original GitHub contents of:
- baseline `c391b83244bd2cc945a1b37bcdd829da27e15ba6`, module blob `5e86a37f3f9a59f4e3feb2fc96c0dc2a35fee340`
- candidate `4bf74b1b827aab56bbe03e032c21c03c508484c8`, module blob `0255d1bf113c27ce77c8db24f165e1b1a76ecb25`
- 60-case fixture blob `b39dabeb6bafe8d0ca3ee3a3fb29d643f1f7c2c8`, declared SHA256 `6a1a414786b22eec0573e25dd7f8aaf1972f5f2a5916181173fc612265672c12`.

All 60 cases ran in both revisions, no source-level exceptions; exact-ID decisions are used, not output rank. Report and per-case hit scores are **permanently committed** in `grounding-independent-direct-replay-v1.json` in this directory. The 60-case suite is now CONSUMED; do not modify oracles or tune the candidate on it.

| Scope | Baseline exact | Candidate exact | Baseline false hits | Candidate false hits | Baseline missed positive IDs | Candidate missed |
|---|---:|---:|---:|---:|---:|---:|
| In-scope (48) | 19 | 36 | 37 | 0 | 5 | 12 |
| Challenge (12) | 8 | 6 | 2 | 0 | 2 | 6 |
| All (60) | 27 | 42 | 39 | 0 | 7 | 18 |

On in-scope examples, candidate retained 24 of the baseline's 31 true-positive IDs. It gained 19 exact-decision passes while losing 2 (both `UF-06-work-adverb`/`ST-06-work-adverb`). It has **zero false hits** in this limited synthetic evaluation but does **not** satisfy a nonregression rule for positive recall. Claims of generalized intelligence or a production-ready model are unwarranted.

**Most prominent failure categories (in-scope):** paraphrased positive (1/6 exact; five missed positives), all-explicit-identifiers (2/6; four misses), negation with unrelated clause (2/4; two misses), and multiple qualifying documents (1/2). Challenge generalization worsens (8/12 → 6/12).

### Test environment/limits

- Actual source was retrieved via authorized GitHub connector and executed directly in a fresh V8 isolate after a conservative pure-source/no-import screen. This is real JavaScript source execution, **not** a full private Git checkout, Android or model generation.
- The fixture's GitHub blob identity was verified from the connector; its declared raw SHA256 was **not independently recomputed in the isolate**. The separately committed Node runner `scripts/continuum-grounding-independent-eval.mjs` verifies that checksum when run in an authorized full clone.
- Formal full-checkout Node reproduction and Kotlin parity of these **new** independent 60-case tests remain `NOT_RUN`. Original previously measured focused PR153 tests remain distinct.
- The score and 60 detailed records were archived before discussing further algorithmic changes. **No implementation was tuned after seeing the holdout**.

### Decision and safe continuation

**REVIEW_ONLY – DO NOT MERGE PR153 YET** into main. First independently reproduce this exact archived output in Node on a full checkout, evaluate candidate/unchanged baseline under the Kotlin mirror, and decide whether reducing false matches at the cost of missing more correct statements meets the owner's safety/quality requirements. For further work on paraphrases and multi-ID relations, create **new DEV split fixtures** and a separately frozen **new** untouched holdout; never use this consumed suite for model selection or tuning. The new owner-chat routing repair in PR157 is a separate audited change and remains pending Android Gradle verification.

The source-independent evaluator and full reproduction notes are in `GROUNDING-EVALUATOR.md`.
