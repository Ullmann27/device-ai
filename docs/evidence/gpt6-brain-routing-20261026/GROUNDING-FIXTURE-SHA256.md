# Independent SHA-256 recheck — frozen grounding fixture (2026-10-26)

The same exact UTF-8 text obtained from the authenticated GitHub file at
`sol/continuum-brain-breakthroughs-20261026:docs/evidence/sol-breakthroughs-20261026/grounding-independent-not-run/grounding-decision-fixtures-v1.json`
was separately processed byte-for-byte with a standalone SHA-256 implementation in a fresh JavaScript isolate.

- Git blob SHA-1: `b39dabeb6bafe8d0ca3ee3a3fb29d643f1f7c2c8`
- Exact UTF-8 length: **60,413 bytes**
- SHA-256 independently computed: `6a1a414786b22eec0573e25dd7f8aaf1972f5f2a5916181173fc612265672c12`
- Original declared freeze SHA-256: `6a1a414786b22eec0573e25dd7f8aaf1972f5f2a5916181173fc612265672c12`
- **MATCH**. The SHA-256 implementation was separately self-checked against the standard SHA-256("abc") vector `ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad`.

This establishes the frozen fixture's source-text checksum independently of the data's declared hash. The already committed `grounding-independent-direct-replay-v1.json` was not rewritten; at that earlier run's instant the independent SHA256 step was still pending, as faithfully recorded there.

The replay executed original source logic on a pinned V8 runtime, not in an Android SDK/JVM context. A second Node run via the checked-in full-checkout script remains a separate verification gate.
