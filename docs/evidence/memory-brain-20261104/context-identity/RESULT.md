# Identity resolution before context projection

Three added integration cases reproduce a caller-boundary gap. The compiler
filtered STALE/CONTRADICTED records before invoking the corrected retriever,
which could hide a contradictory historical alias with the same evidence ID.
A hidden historical alias could also leave a public current copy eligible.

RED: compilation passed; 3 cases, 2 expected failures. GREEN: **47 tests
passed**, including all 3 cases, the unchanged independent 25-case holdout,
exact baseline score/order characterization and context integrity tests.

The compiler now applies the same internal identity resolver before projecting
current states. Retrieval itself keeps its public signature and uses that
same resolver before privacy filtering. Unique historical evidence remains
available for audits and never consumes current-context result slots. The
lookup collection is not written back to any store. Original benchmark results
remain bound to their recorded candidate; this helper extraction is later.
