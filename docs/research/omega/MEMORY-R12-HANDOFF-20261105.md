# MEMORY R12 — exact numeric evidence without breaking relative-day cues

5 November 2026. **Isolated Draft; C1/#519 remains the sole common-app integrator.** No new bots, shared Activity/factory/worker/voice writes, main merge or release.

## Source, ownership and test-first sequence

Prior specialist R11 is PR #538, pinned HEAD `915dba534eb7c79b6343636cc6646226a044998f`. At intake C1/#519 was `13c37f55789bf67b07beeb37cc1a5035526e623e`; its central board and the R3/#522 handoff were read. #522's compiler privacy and mapper protections were already the subject of C1's own targeted integration. Search showed no competing named Memory R12 PR/branch; this work uses a new, isolated branch stacked directly on R11.

Frozen **before** production: `MemoryNumericEvidenceR12Test.kt`, commit `7a44d1ef5761713810999d07f46069af009cc17d`, blob `c6fc1b7ed6a8cbbbaf87c46a2e3e685a5878ed8e`. Nine methods are retained unchanged. The original R11 full production file matched published Git blob `b8c4930ce54b7dd6528beb9bee2a1fc84c122301` exactly. Repair commit `c0113a8c85a01121384e4a68574d9a461516230d` changes **one production file only**: `app/src/main/java/at/heinz/deviceai/learning/ContinuumMemoryRetriever.kt`; replacement blob `dcaeed0b13eb3f4f88128e45c250d6d84a319c72`.

## Three bounded improvements

1. **BM25 factual numbers:** An exact question about **2 Kinder** previously returned a confirmed record about **3 Kinder** through the shared word *Kinder*, even after R11 made the digit token searchable. Similarly, a query mentioning **2024** could return a 2025-only record. The candidate now intersects the exact numeric postings for every explicit factual numeral before returning any hit. Absence of the number is not evidence for a different number. No `confirmed` metadata is fabricated or modified.
2. **Character 3–5-gram numeric identities:** The same false hit previously survived cosine matching. Per-document numerical membership is now stored as `BitSet` data **inside the same immutable, volatile-published IndexState** as document IDs, vectors, IDF and existing negation membership. Each explicit numeric term must match that index generation. Approximate index-byte accounting includes the additional BitSets.
3. **Temporal exception:** `vor 2 Tagen` is a relative **time filter**, not necessarily a document stating the number 2. The shared numeric extractor removes only the recognized `vor N Tagen` phrase before collecting exact factual numerals. `vor 2 Tagen habe ich 4 Kinder` still requires an exact 4. Existing temporal scoring is unchanged.

## Actually executed, source-pinned Kotlin/JVM evidence

Toolchain: local `kotlinc-jvm 1.9.0`, OpenJDK 21.0.12.1. The full original and patched production Kotlin sources and frozen tests were checked by actual `git hash-object` against the GitHub blobs. Host-only adapters stand in for JUnit and `EventEmittingRetriever`; **no Android event-bus or Activity execution is implied**.

- **Original R11:** 17 compiled host tests run, **11 PASS / 6 FAIL**, exit 1. The six failures specifically expose numeric mismatch in BM25 and n-gram retrieval. Eight of the nine frozen R12 assertions run; only the helper-only assertion cannot compile before its newly introduced method exists. All nine existing R11 methods run and pass.
- **R12 candidate:** 18 compiled and executed host tests, **18 PASS / 0 FAIL**, exit 0: all nine R12 and all nine unchanged R11 methods.
- **Published earlier parity fixture:** both original and patched sources produced **112/112 identical output lines** for nonnumeric queries, including memory ID, `confirmed` and 12-decimal score, same SHA256 `643a9ac0a14626ce10874947591354e1a702f167c9dca5008664a5514e9f70ff`.
- Complete direct host transcripts and source hashes: [R12 evidence](../../evidence/memory-r12-numeric-20261105/RESULT.json), [RED](../../evidence/memory-r12-numeric-20261105/baseline17.txt), [GREEN](../../evidence/memory-r12-numeric-20261105/patched18.txt). Those two text blobs match the locally executed transcripts. [Pinned reproducer](../../tools/memory-r12/run.sh) verifies published blobs and runs RED/GREEN plus parity. **Its entire all-in-one invocation did not finish in this isolated environment**: two bounded attempts timed out *after* RED/GREEN, before complete parity. The parity phase was independently compiled and executed to successful completion. Do not label the full script as an exit-0 run.

## Risks, unfinished acceptance and C1 integration order

The exact-number eligibility gate deliberately prefers **no potentially contradictory evidence** over a false-positive retrieval. It can lower recall when the document spells out a number (e.g. *zwei*) while the query uses *2*, when a numeric value appears in another form, or when numeral roles are ambiguous. A numeral anywhere in a document is not semantic proof that it describes the user's requested fact. The synthetic parity set contains **no numerical requests**; it does not establish holdout quality for the new behavior.

C1 should review and integrate **R6 → R7 → R8 → R9 → R10 → R11 → R12** once each, preserving #522, #529, newer common work and source ownership. On the *actual combined tree*, execute all frozen Android JUnit methods, prior TemporalCue/negation/atomic-reindex tests, official M2.9 1,000-document/44-question holdout (historical 42/44 Recall@3), and full Node/backend/Android/required UI/NewApi gates. Reject or revise R12 if measured recall, privacy, source provenance or performance regress. External C2 approval, native model inference, Fold7, APK/signature and in-app download release remain **NOT_RUN**.

No original package, signature, update button, owner data, offline switch, model weights, build locks, GitHub Actions, paid services, main or APK was modified. No other worktree was changed.

[Machine continuation state](MEMORY-R12-STATE-20261105.json).
