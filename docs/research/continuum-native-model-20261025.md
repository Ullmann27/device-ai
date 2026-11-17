# Continuum Native Language Foundation – Research Contract (25.10.2026)

## Decision

The objective is a **trainable, separately versioned Continuum language model**, not merely a Qwen chatbot wrapper. Qwen3 is an offline teacher and/or **licensed initialization** for a derivative weight model. These paths must never be confused:

1. **Own architecture / random initialization (LM-NATIVE):** all weights belong to a new tokenizer+model and start untrained. Qwen-generated candidate examples may help supervise this model, but the student is not inherently more capable than the teacher.
2. **Qwen-initialized Continuum adaptation (LM-ADAPT):** use a verified, trainable open-weight Qwen3 checkpoint as the starting weights, preserve model/license lineage, perform SFT/LoRA (and optionally tested preference optimization), then quantize/convert for verified LiteRT-LM Android inference. This is a derivative, not a from-scratch pretraining claim.

Do not train directly against a `.litertlm` artifact unless the runtime has a *documented, implemented gradient-training API*. The present Android LiteRT-LM path is an **inference** interface. Training requires a separately validated training representation and hardware budget.

## Why existing LM1 is not a general AI

The verified LM1 research used 428,014 TRAIN bytes; model **LM1-GM-v512 has 132,160 parameters**. Frozen TEST BPB ranged 2.4350–2.4425 across its three seeds, while the Kneser-Ney-7 baseline achieved **2.1504**. Those are next-byte-prediction statistics, **not** general reasoning, instruction-following or coding benchmarks. See `docs/CONTINUUM-LM1-OPUS-20261022.md` and `docs/evidence/lm1-20261022/final-test-20261023.txt`. LM1 is a research platform and must not be presented as a production-scale model.

## Stage A – data provenance (implemented in this branch)

`node scripts/continuum-native-corpus.mjs candidates.jsonl new-output-directory`

Input: UTF-8 JSONL, one object per line:

    {"id":"coding-001","group":"project-api-task-001","prompt":"...","completion":"...","teacher":{"model":"Qwen3-offline","sha256":"<64 lowercase hexadecimal bytes>"},"review":{"kind":"CODE_TEST","referenceSha256":"<64 lowercase hexadecimal bytes>","passed":true}}

- Both `teacher.sha256` and `review.referenceSha256` must be actual SHA-256 strings. **Do not invent hashes.**
- The tool confirms field structure and deterministic partitioning but **does not independently authenticate** review signatures or execute referenced tests. It marks every review `independentlyVerified=false`.
- Group-level split keeps paraphrases/variants in the same TRAIN, VALIDATION or TEST bucket. Duplicate/cross-group conflicts are rejected.
- Basic secret/email patterns are rejected, but this is **not** a full privacy analyzer; do not feed private medical, family, account or device data. Manual review is still required.
- Output directory must not exist; manifest and split files are written once. The manifest is `CANDIDATE_ONLY` and explicitly forbids model promotion.

Stage A collects *candidates*, not verified ground truth. No automatic training from untrusted text, no autonomous permission grants or unreviewed modifications to the app.

## Stage B – model training (not implemented yet)

- Obtain/validate the **actual** Qwen model family, architecture, trainable source checkpoint, tokenizer, model weight SHA-256, license and hardware capacity. Model ID shown in Android is not sufficient; the LiteRT binary must match.
- Require a separately verified grading system: for Kotlin/Android coding, real Gradle/JUnit test receipts bound to task, patch, benchmark and model version; for factual answers, trusted evidence or human review. Never self-certify teacher output.
- Train with appropriate compute and strict train/validation/test isolation. Freeze a holdout *before* any tuning. Record training data, optimizer settings, seed, parameter count, FLOPs if measured, lineage and tensor/checkpoint hashes.
- A LoRA-trained Qwen model is **Continuum-derived-Qwen**. A distinct LM-NATIVE student only becomes a model when its own tokenizer, optimizer, checkpoint and measured generation are published.

## Stage C – promotion gate (not implemented yet)

- Compare source Qwen and Continuum candidate **side by side** on fresh blind held-out coding questions; use compile/test pass rate (pass@1), validated patch acceptance, reasoning accuracy, citation-based factuality, German comprehension, latency, RAM, battery and thermal metrics.
- Benchmark execution and reference outcomes must be independent of model-generated grading. A passing format/lexical test proves only format/lexical behavior.
- Promote **only after** measured superiority on stated target tasks, regression checks on general skills, offline Android execution, owner review, and existing signature/source gates. Better than Qwen in one domain does not imply better than frontier models overall.
- Never modify production `main`, auto-train on the user's private conversations, transfer secrets, override Android permission systems, or grant coding tools permissions from model text.

## Status

Implemented: candidate corpus builder and unit tests on an isolated Git branch.
Not implemented: actual Qwen teacher export, independently authenticated grading receipts, full-scale training, accepted native weights, model conversion, Fold7 benchmarking or release integration. These are intentionally not asserted as achieved.

**Next engineering milestone:** build a local teacher-sample adapter with exact checkpoint provenance and a separate, independently executing coding-grade receipt validator. Then connect its verified dataset to the existing training orchestration. The Android UI should display weights/benchmark provenance, not fabricated training counts.
