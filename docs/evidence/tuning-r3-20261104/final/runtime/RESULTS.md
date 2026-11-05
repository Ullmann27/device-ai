# Final actual runtime callers

**PASS:** all five pre-existing `LmRuntimeTest` methods pass against the immutable original source graph and the complete final candidate graph. They are five distinct methods executed twice; the full final verification set contains 31 distinct methods, not 36.

The suites exercise stepwise/full minGRU logits, greedy generation parity, seeded sampling, checkpoint/vocabulary identity, damaged/foreign/truncated/oversize rejection, corrupted store files, generation limits/cancellation, and incomplete UTF-8 suffixes. The unchanged test fixtures include small deterministic toy-model training inside unit tests; no project model was trained or deployed.

`verify-runtime.py` validates all 12 source hashes and dependency hashes, confirms that the existing test matches the immutable baseline, changes only the original package declarations, and compiles both complete source graphs without stubs. Source and test hashes, exact commands and raw outputs are retained. No performance timing is rerun. Android/Gradle/device tests are outside this host-only result.
