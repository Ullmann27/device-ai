# CDX1 role-binding information collision — controlled source experiment

Actual production Kotlin classes; encoder seed73 frozen, decoder seed91, same authored5-TRAIN reference corpus and2520 updates as the earlier diagnostic. No owner state/checkpoint read; no weights promoted. Three additional counterfactual pairs are AUDIT ONLY, never passed to training. Full source, vectors, bucket sets, first258-way distributions, free generation and teacher-forced losses are retained in cdx1-role-collisions-with-span.json.

Ask `Wo ist Anna?` in each pair:

- `Anna ist in Perg. Emma ist in Berg.` vs `Anna ist in Berg. Emma ist in Perg.`
- same template with Bern/Kern;
- same template with Salzburg/Würzburg.

Each pair changes the correct answer. Actual SWB1 bucket sets are identical:75/76/88 distinct buckets, respectively, all below the128 truncation cap. The bag of character2..4-gram hashes therefore contains no role-binding distinction for these examples. `encodeBuckets` computes tanh(mean(embeddings)); in real arithmetic equal sets produce equal vectors **for any embedding weights**. Floating insertion-order differences leave only3.7–5.5e-18 L2 noise in this frozen run. This is a representation collision, not evidence of insufficient training steps.

All prefixes also end in the same8bytes `ntwort: `. Holding conditioner fixed and swapping the actual prompt yields bit-identical first-token probabilities. With each prompt's own conditioner, the trained reference model's first-probability difference is also0.0 for all three pairs. It generates `Wien` for both sides in every pair (0/6 audit answers). The position-aware frozen span model and deterministic rule copy all six different correct answers with independently checked offsets. These authored diagnostic controls are NOT a new independent blind benchmark; B remains the separate37-case result.

For balanced Perg/Berg examples, a real-arithmetic representation collision makes the first target P/B unidentifiable. The best expected first-byte NLL is at least ln2; pooled across the four answer bytes plus EOS the theoretical irreducible average is at least ln2/5≈0.13863, even if later teacher-forced bytes are perfect. Teacher forcing supplies previous GOLD answer bytes after the first step, which can mask first-byte failure in the average. A low pooled training loss is consequently not evidence of correct free answers.

This proves a specific causal failure of the current representation/context on controlled examples. It does not prove that every Fold7 collapse is caused only by this mechanism, nor does it replace owner-checkpoint measurements. Floating-rounding artifacts are not a reliable role representation. The original20+5 generative gate and all27 historical failures remain untouched.

Next falsifiable experiment: a separate, explicitly versioned position/role-aware conditioner or pointer model, with fixed encoder/decoder checkpoint pairing and a new unseen relational corpus. Measure ordered binding vs bag baseline, first-token separation, per-position teacher-forced NLL, free exact generation, missing-fact abstention and protected SWB1/EWM2 behavior. Do not retrain/promote the owner's SWB1 simply to remove a research decoder bottleneck.
