# Round 2 sampling protocol — fixed before timing

Baseline: `db3295d51c769afdaf809ac1dfae2ada84a3274f`, the integrated round-one tree. The `RoundOneSamplingReference.sample` body must exactly match this baseline. The older e101 reference remains only as an additional regression test.

One production candidate: replace `order.map { ... }` weight storage with `DoubleArray(order.size) { i -> ...order[i]... }`. Keep `rnd.nextDouble() * w.sum()` and the subtraction/selection loop unchanged. Do not change the greedy branch, floating-point expressions, sort, top-k clamp, model/training/tokenizer or callers. No second candidate is planned if this reduces allocation while preserving behavior.

Functional gates: run the original five sampling tests and the five round-two tests on both source versions. Require matching token selections, exceptions, RNG state/count/timing, raw input bits and tokenizer serialization. Inspect the actual Kotlin 2.0.20 bytecode for boxed versus primitive sums and the compiled candidate for removal of weight boxing.

Timing gate: Root must give the start message. Compile the baseline from `git show` into its own snapshot/jar and the candidate into a separate snapshot/jar, with source/test/harness hashes before compilation. Run one complete baseline process and one complete candidate process. In the final candidate process, pair the unchanged round-one reference with the candidate using the same inputs/seeds. Archive both complete processes; do not select only favorable scenarios or repeat to select better timings.

Fixed scenarios (18):

- Eight unchanged greedy controls: vocabulary 258, 512, 2,048 and 8,192 × random/all-tied distributions, temperature 0 and top-k 1.
- Ten target cases: `(512, random, 1, 50)`, `(512, random, 1, 1)`, `(512, random, 1, 512)`, `(512, ties, 1, 50)`, `(2048, random, .8, 40)`, `(2048, random, 1, 2048)`, `(8192, random, 1, 50)`, `(8192, random, 1, 8192)`, `(512, random, NaN, 50)`, `(512, special, 1, 50)`.

Each scenario uses 32 deterministic arrays, seed `731 + vocab`; each batch uses RNG seed 97. Special arrays insert NaN, ±infinity, signed zero and a negative value after normalization. Four warm-up batches per implementation, then nine alternating A/B pairs. Batch operations: 1,024 at vocabulary 258/512, 384 at 2,048, 128 at 8,192. Record wall time, thread CPU time and allocated bytes; checksum includes selected tokens in order and post-batch RNG state. All cases must agree across implementations and source versions.

Existing local OpenJDK/Kotlin toolchain only, `-Xms256m -Xmx256m`, no downloads, Gradle build or device inference. Host results apply only to this sampler. Preserve and report control-path changes, warm-up/JIT variation and external host load. Do not claim model-wide, Fold7 or Frontier-level gains.
