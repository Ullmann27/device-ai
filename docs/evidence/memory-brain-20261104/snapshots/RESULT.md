# Memory and experience snapshot isolation

RED source commit: `6d2fe2d0`. Six new behavioral tests compiled successfully;
five failed against the frozen 578bdfd1 baseline. Mutating returned collections
changed stored tags, ancestry, independent evidence, past failure records and
evaluator scores. One test confirmed the pre-existing input-copy protection.

The correction freezes defensive collection copies once during put/store.
Records returned by get/all/similar can consequently be shared without mutable
aliases or an additional deep copy for every recall. No archive schema, record
hash algorithm, public API or on-disk format changes.

GREEN: **35 tests passed, 0 failures**, including all six new cases and the
unchanged MemoryEcology, CognitivePersistence, AgentExperienceRecall,
AgentExperiencePairedBenchmark and ContinuumIntelligencePhase2 suites. The
paired benchmark explicitly uses a mock model and is not model-quality or
device-performance evidence. This result is host Kotlin/JUnit, with the
baseline classpath frozen from the actual successful Android compilation.

Raw logs and exact compiled source hashes are included alongside this note.
