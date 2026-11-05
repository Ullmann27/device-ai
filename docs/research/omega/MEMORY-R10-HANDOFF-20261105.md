# MEMORY R10 — ambiguous typo evidence and indexed negation lookup

**Status: SOURCE-BOUND JVM 7/7 GREEN; baseline RED 6/7; 112/112 targeted parity. Android and official M2.9 holdout NOT_RUN.** 5 November 2026.

## Source and owner boundary

C1 is the only serial integrator of the common Android candidate: [C1 #519](https://github.com/Ullmann27/device-ai/pull/519), observed head `13c37f55789bf67b07beeb37cc1a5035526e623e`. Its central `ASTRA-NUCLEUS-HANDOFF-20261104.md` and specialist [R3 #522](https://github.com/Ullmann27/device-ai/pull/522) were read. C1 already integrated bounded R3 compiler and event-mapper fixes and is separately taking the R4 privacy contribution. There is no competing R10 branch observed among the open PRs at intake.

This isolated stacked R10 branch starts at [R9 #536](https://github.com/Ullmann27/device-ai/pull/536), exact commit `386389e9aaaa44dadd3bbb919f2658dbaf43fa86`, after R6 #532, R7 #533 and R8 #534. The sole changed production path is `app/src/main/java/at/heinz/deviceai/learning/ContinuumMemoryRetriever.kt`; original Git blob `12c1c716c57bc9656897a6428ed0ee1c4f443142` → R10 blob `c7c4d8d701e8fd1d2b886aa0a6b627c1b6a1bd99`. Only two Android JUnit test files plus independent host harness/evidence are new.

Tests were **committed before the production change**: test freezes `dbd15d42cb5ae42a6956aac5809ffffe36ae2647` and `e17bb7420fd5a2ba237de7f8cc7828bb4650ca3e`. Actual production fix `c9d122aa941e9994c56cce62fb808e76b5283e60`. Test blobs `afa93dd899a04ac57e735510be66655fbba8e181` (BM25) and `69b13ff6a3098cca4b98180fed91d366b77acac2` (ngram). Those two final test files match the executed local source byte-for-byte.

## Two bounded code improvements

1. **BM25 fuzzy ambiguity fails closed.** Before R10, query `balk` against one `Ball` and one `Bald` record silently selected one record despite equal edit distance **and** equal document frequency. The frozen regression fails on the original production source. R10 ranks the nearest two candidates, keeps document-frequency priority, uses lexical tie order for determinism, and returns no fuzzy hit if both are equally plausible. Exact matches, unique typos such as `tisck` → `Tisch`, and a frequency-disambiguated candidate remain retrievable with the original `confirmed` value. This changes intended behavior for genuinely ambiguous typos, and C1 must evaluate recall consequences.

2. **Ngram polarity lookup is precomputed and generation-scoped.** Previously each bare `nicht`/`kein`/`ohne` query rescanned all candidate document strings to decide whether the exact negation existed. R10 stores a compact `Map<String, BitSet>` beside `docs`, `vectors` and `idf` in the same `@Volatile IndexState`; no map/BitSet is mutated after publication. Search only computes cosine for eligible documents on a sole-negation query. Reindex resets the membership atomically. Mixed-content queries keep the existing cosine path, and no positive record is promoted to `confirmed`. The approximate index-byte count now includes bitset overhead, increasing 80 reported bytes in the specific 240-document benchmark.

## Actual execution and raw receipts

The full source was reconstructed in an isolated local mirror and checked with `git hash-object` against the **same exact Git blobs** before compilation. Toolchain: `kotlinc-jvm 1.9.0`, JRE `21.0.12.1`. Only `EventEmittingRetriever` and JUnit annotation/assert API are host shims. **All seven methods are the actual frozen Android-project test methods**; this is not Gradle or physical Android testing.

- Original R9: Kotlin compilation succeeds; seven invoked methods → **6 PASS, 1 FAIL**, exit 1. The failing case is the frozen equal-distance/equal-frequency ambiguity.
- R10: Kotlin compilation succeeds; identical seven methods → **7 PASS, 0 FAIL**, exit 0.
- The exact published [`tools/memory-r10/run.sh`](../../../tools/memory-r10/run.sh), Git blob `5e242a1599a5116a8fd7935989806f0e5119746f`, was executed in an exact-source local mirror with explicit R9 baseline path and exited **0** after observing the expected RED and GREEN. A larger four-compilation combined experiment hit a local execution timeout; that incomplete invocation is **not** a successful test and is not used for any result.
- The exact published [`parity.sh`](../../../tools/memory-r10/parity.sh), Git blob `3ad9dfd87b48a6a0c01a0df96fda53cef6f36221`, separately exited **0** on the exact-source mirror: **112/112 identical BM25/char-ngram ID, confirmation and twelve-decimal-score serializations**, 40 deterministic synthetic documents, 56 queries and both retrievers. SHA256 for both 112-line outputs `643a9ac0a14626ce10874947591354e1a702f167c9dca5008664a5514e9f70ff`. This deliberately does not compare changed index byte estimates or include the intentionally different ambiguous typo probe. It does cover 16 bare-negation retrievals.
- Exploratory single-machine JVM benchmark: 240 synthetic long-text documents; five rounds of 75 bare-negation searches, warmup 20. Median per 75 calls **330.945146 ms → 10.397357 ms**, approximately 31.8× for this fixture. Index-estimate 140,816 → 140,896 bytes. The benchmark is one host measurement with JVM/GC variance, not a reproducible Android/device performance guarantee and not a claim of quality improvement.

Actual raw baseline/patch transcripts and both performance transcripts are committed under [`docs/evidence/memory-r10-fuzzy-20261105/`](../../evidence/memory-r10-fuzzy-20261105/). Their byte-exact Git blob IDs and SHA256 are recorded in [`RESULT.json`](../../evidence/memory-r10-fuzzy-20261105/RESULT.json). No raw log or prior negative receipt was removed. The full 112 lines can be regenerated by running `bash tools/memory-r10/parity.sh` in a checkout with the R9 base commit; an exact R9 source file can alternatively be passed as its first argument.

## Reproduce and rejection conditions

At a complete checkout on this branch:

```bash
bash tools/memory-r10/run.sh
bash tools/memory-r10/parity.sh
```

If the pinned baseline SHA is not in the local Git object database, supply a byte-identical original source file as the first argument to each script. A missing/mismatched Git blob, a baseline other than **6/7**, a patch other than **7/7**, or a parity difference must return a nonzero status. The host shim does not test the production event bus.

**C1 acceptance order:** Take R6→R7→R8→R9→R10 only after source/ownership review; first execute the existing and new Android JUnit methods with Gradle, plus full memory/TemporalCue tests and the genuine 1,000-document/44-query retrieval holdout. Historical Recall@3 remains **42/44**, **not verified here**. Reject/roll back the *bounded R10 production hunk* if ranking quality, source contracts or complete required gates fail; preserve the frozen regressions and negative receipts. Only after C1 and independent C2 acceptance, full Node/backend/Android UI/lint, offline/local model/voice/Avatar/Fold7 checks, and owner release approval may the original signed APK path advance.

No writes to C1, Activity/factory/voice/worker entry points, #522 compiler/mapper, owner data, model weights, original package/signature, offline toggle or release channel. No new agents, GitHub Actions, paid runners, main merge, sign, APK, release or installation.
