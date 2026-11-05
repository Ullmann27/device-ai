# MEMORY R11 — single-digit evidence and duplicate bare-negation boundaries

**5 November 2026. Isolated specialist Draft; C1 remains sole common-app integrator.**

## Ownership, exact sources, conflict avoidance

- Prior R10: [#537](https://github.com/Ullmann27/device-ai/pull/537), HEAD `3e1e465bf425d3eafd7155da51aa7cb2fc59b808`; R11 directly branches from that exact commit. No competing R11 PR among the open-PR search at intake.
- C1 [#519](https://github.com/Ullmann27/device-ai/pull/519), observed `13c37f55789bf67b07beeb37cc1a5035526e623e`; central board `docs/research/omega/ASTRA-NUCLEUS-HANDOFF-20261104.md` was read at that ref. C1 already took the bounded [R3/#522](https://github.com/Ullmann27/device-ai/pull/522) compiler privacy and event mapper repairs; R4/#529 remains separate C1 intake. No shared integration entry point is owned here.
- Frozen JUnit tests **before changing production**: `aca5d2f6cd6bb266468e882bb39a55faa3d17844` and `6ac97959b53e66c9ab325b0c9198be71f7095024`, exact blobs `efeee016465f7eb4f1fca00801ba121b9cada180` and `6764f1af7a6c744bda7a009784f33174f88408c0`.
- Single production path `app/src/main/java/at/heinz/deviceai/learning/ContinuumMemoryRetriever.kt`; R10 original Git blob `c7c4d8d701e8fd1d2b886aa0a6b627c1b6a1bd99` → R11 blob `b8c4930ce54b7dd6528beb9bee2a1fc84c122301`, source-fix commit `20349fbd2a7c2ed86a4ad7903f3249bc1ee4c1e7`. Exactly two production expressions changed; no other production file changed.

## Two independently reproduced defects and bounded corrections

1. **Single-digit evidence was silently discarded.** `GermanText.rawTokens` previously discarded all one-character tokens, so memory `2 Kinder` and `3 Kinder` had indistinguishable BM25 content; a query for `2 Kinder` incorrectly returned the first `3 Kinder` record, which was `confirmed=true`. A standalone query for `2` returned no BM25 or n-gram hit. The filter now preserves ASCII single-digit tokens 0–9, but still discards single-letter alphabetic tokens and retains existing longer tokens. A fact's `confirmed` value is never elevated or rewritten; the intended exact numeric record is retrieved instead.
2. **Repeated bare negation bypassed the n-gram evidence gate.** R10's `soleNegation` detected only one stem in the entire list; `nicht nicht` and `ohne ohne` fell back to unconstrained cosine matches on positive `Licht` and `Bohnen` memories. R11 applies `distinct()` before the existing `singleOrNull` gate, so repeated copies of the **same** negation use R10's exact, index-generation-bound polarity membership. Mixed-word queries and different negation stems retain previous scoring behavior. Reindex still replaces eligibility atomically.

## Actual JVM evidence (not Android)

The **complete original Kotlin production file** was reconstructed from GitHub and checked by `git hash-object` against the published original R10 blob. The two frozen Android-JUnit source files were likewise verified byte-for-byte. Toolchain was local `kotlinc-jvm 1.9.0` and OpenJDK 21.0.12.1. Only `EventEmittingRetriever` and the JUnit annotation/assert API were host-only compile adapters; this did **not** execute the Android project or real event bus.

- Unchanged R10 source + nine R11 tests: **compile exit 0, 2 PASS / 7 FAIL, test exit 1**, actual failures in both numerical and repeated-negative evidence paths.
- Patched R11 source + exact same nine tests: **compile exit 0, 9 PASS / 0 FAIL, test exit 0**.
- Published R10 synthetic unchanged-query parity fixture (`ParityR10.kt`, Git blob `982dfc1597a9530e2a3a0323b084b69e09f11a6c`): **112/112 identical** (40 docs × 56 queries × 2 retrievers), including memory ID, original confirmation and score to 12 decimals. Both output SHA256 `643a9ac0a14626ce10874947591354e1a702f167c9dca5008664a5514e9f70ff`. This intentionally does not cover the changed numeric/duplicated-negation semantics; the frozen regressions cover those separately.

Full raw transcripts and SHA256 source bindings: [R11 results](../../evidence/memory-r11-numeric-negation-20261105/RESULT.json), [original RED](../../evidence/memory-r11-numeric-negation-20261105/baseline.txt), [patched GREEN](../../evidence/memory-r11-numeric-negation-20261105/patched.txt). Original raw transcripts were hash-compared after public upload.

Two executable, **pinned** follow-up scripts are provided: `bash tools/memory-r11/run.sh` and `bash tools/memory-r11/parity.sh`. A baseline file path can be supplied when the R10 commit is not in a local checkout. The scripts verify the exact current and original Git blobs and reject unanticipated results. **Do not represent these scripts as end-to-end executed here:** the combined source-mirror invocation hit the local execution limit after the baseline/earlier GREEN output; the direct exact-source RED, GREEN and parity runs above were separately completed. The supplied files are re-execution artifacts, not a substituted Android gate.

Local direct Git clone also failed with `Could not resolve host: github.com` (git exit 128). GitHub connector reads/writes succeeded; no repository or network permissions were bypassed and no full Android SDK/Gradle source checkout was claimed.

## C1 acceptance and hard boundaries

C1 must independently review R6→R7→R8→R9→R10→R11 in order, verify that two-line R11 production patch against actual integrated parent bytes, compile the frozen JUnit tests in the real Android project, then execute complete Temporal/retrieval regressions and the actual **1,000-doc/44-query M2.9 holdout**. The previously reported 42/44 Recall@3 is historical, **not measured for R11**. Numeric-token indexing may change relevance, memory usage and token statistics; reject or roll back only this bounded R11 hunk if quality, privacy or other required gates regress. Do not weaken the privacy classifier or R10's refusal to invent evidence.

**NOT_RUN:** full Android/Gradle/JUnit and configured NewApi lint, complete Node/backend gates, official 44-query holdout, local model inference, microphone/TTS, physical Fold7, APK, original signature/update channel. No new agents, Activity/factory/worker/voice edits, model/owner-file/offline/signing changes, paid runners, Actions, main merge, release, or installation.
