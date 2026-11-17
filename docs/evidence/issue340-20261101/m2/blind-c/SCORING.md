# Issue340 Blind-C scoring, frozen before candidate evaluation

The independent curator authored 55 synthetic cases with seed 34020261101: PRIMARY C001–C025 contains 20 SUPPORTED and 5 honestly MISSING facts. ADVERSARIAL C026–C055 contains 5 cases each of negation, conflict, time, role, relation and same-name/source-boundary controls. The controls contain 5 supported role-binding cases and 25 required abstentions. These are static explicit-relation evidence labels, not real-world truths. Unknown, contradictory, temporally scoped, mixed-relation and ambiguous source assertions require abstention under the existing conservative evidence contract. A matching location string alone is insufficient.

All evidence and labels remain plain editable files. Blinding is supervisory discipline: root and implementer receive only corpus hash/counts/path until candidate source and checkpoint hashes are frozen. No candidate source or output is read during curation. After unblinding, no labels, thresholds, training examples, hyperparameters or source fixes may be changed and reported as the same blind run. Invalid fixture discovery must be reported separately without revising the frozen score.

The corpus contains original UTF-8 source text with SHA-256 over those exact bytes. Offsets count UTF-16 code units, matching Kotlin String positions; endExclusive is exclusive. Every positive label copies the exact 1–3-word original source substring and binds its sourceId, revision and digest. No Unicode or whitespace normalization, trimming, synonym replacement, partial credit or answer-only scoring is allowed in the primary exact metric. Non-BMP prefixes are intentionally represented correctly by UTF-16 offsets.

## Required per-case output

Record one result for each C001–C055, preserving order or stable ID. Evaluate RAW learned selector, RULE baseline and LEARNED_VERIFIED output independently. HYBRID is optional and must be separately named. Each mode emits JSON null for abstention or an object containing text, sourceId, revision, sha256, start and endExclusive. Extra diagnostics may be retained separately. Producer-native logs may be adapted mechanically after freeze; adapters must not use expectations to construct or repair predictions. Malformed/missing predictions count incorrect, never as abstention. Missing/duplicate/unexpected case IDs invalidate a purported complete run and must be reported.

## Frozen metrics

For each mode and split report:

- Exact supported count: all six expected fields match exactly, with integer types for revision/offsets.
- Correct abstention: JSON null on a null expectation.
- Unsupported positive claims: any nonnull answer when expectation is null, or a nonnull answer which is not the exact supported answer.
- False source assignments: any positive prediction whose sourceId/revision/sha256 does not identify an input source, whose span is out of UTF-16 bounds or does not copy its claimed source, or whose sourceId/revision/sha256 differs from the expected positive provenance. Wrong location text with valid provenance remains an unsupported positive claim, even when it is not a false source assignment.
- Malformed prediction count and false abstention count reported explicitly.
- Wilson 95% intervals for supported exact accuracy and null-expectation abstention rate. No intelligence percentages.
- Full per-case expected/predicted/decision record after unblinding; paired disagreement IDs and exact 2×2 paired counts between each pair of modes, split separately. No statistical pooling of adversarial controls into the primary 20/5 acceptance target.

The predeclared target is at least 16/20 supported exact, 5/5 missing correct abstention, and zero false source assignments for each mode being claimed to meet it. All modes remain individually visible; verified successes cannot replace the raw score. Adversarial families are safety diagnostics reported separately, without a post-hoc aggregate acceptance target.

NLL is mean negative natural log probability assigned by the frozen classifier to the frozen exact gold span or NOANSWER, reported separately for PRIMARY and ADVERSARIAL. If the gold candidate was not enumerated, its probability is zero and NLL is infinite, not excluded. If the producer does not emit the required distribution, report NOT_RECORDED; do not invent byte-generation loss or substitute selected-answer confidence. Report candidate enumeration coverage separately when available.

Report model/checkpoint/source/corpus hashes, bytes, bounded host CPU/wall time and per-case latency when recorded. These are host measurements, not Android/Fold7 measurements. Preserve mutated-source and input-immutability checks as separate harness outputs; do not modify frozen cases or infer they passed from corpus validation.

## Scope of work

This freeze ran only deterministic data generation and internal data consistency/overlap checks. No training, candidate execution, application test suite, network action, owner-memory access, app/code edit or production checkpoint action occurred. Legacy A/B and Oracle are public consumed corpus references, not fresh baselines. Oracle exact-disjointness is established by absence of every generated synthetic proper token from the complete Oracle generator source, including its exhaustive name/place literals.

Artifacts are LOCAL_ONLY / NOT_PUSHED; publication remains the supervising root's responsibility after preserving the blind protocol.
