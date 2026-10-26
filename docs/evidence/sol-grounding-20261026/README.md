# Mirrored grounding eligibility evidence — 2026-10-26

Baseline integration source: `c391b83244bd2cc945a1b37bcdd829da27e15ba6`. RED test checkpoint: `1b7d4a2dfb38164e57dadde2eb4252d41fa60e00`. These are host retrieval checks, not new Android APK/device verification, weight training or model intelligence measurements.

`node-baseline.txt`:25/25 existing Node tests pass before changes. `node-red.txt`:42/60 pass and18 fail on the new30-case shared fixture before any implementation. `kotlin-red.txt`:2 new tests fail,17 existing tests pass. The fixture later adds Unicode/follow-up regression cases. `kotlin-unicode-red.txt` records the real JVM-default-whitespace bypass before a shared explicit normalization set fixed it.

`node-green-focused.txt`:91/91 pass (66 policy arms over33 shared cases, plus25 original identifier/false-positive/compound/benchmark cases). `node-green-broader.txt`:16/16 additional retrieval alignment, paraphrase, dialogue audit and CRE1 research boundary tests pass. `consumed-fixture-retention.txt`:all74 previously accepted identifier positives survive, all8 no-ID golden result objects/scores are byte-identical under JSON, and all136/46/62 previously retrieved true-positive targets from the existing TRAIN/DEV/TEST relational fixtures remain in top3. These fixtures were already consumed; this is regression evidence, not an independent new holdout.

Reproduce focused Node:

```sh
node --test .github/continuum/retrieval-location-grounding.test.mjs .github/continuum/retrieval-identifiers.test.mjs .github/continuum/retrieval-false-positive.test.mjs .github/continuum/retrieval-compound.test.mjs .github/continuum/retrieval-benchmark.test.mjs
```

Reproduce host Kotlin using the actual PR150 Android-compiled class dependencies (no product stubs):

```sh
bash docs/evidence/sol-grounding-20261026/run-kotlin-focused.sh "$PWD" /tmp/deviceai-grounding-kotlin /workspace/device-ai/app/build/tmp/kotlin-classes/debug
```

`kotlin-green.txt`:42/42 PASS across5 classes, exit0.

The script compiles current `ContinuumMemoryDialogue.kt` and five real test classes at JVM17 with module identity `DeviceAI_app_debug`, then executes JUnit. The base classes must be available locally; otherwise this standalone check is UNAVAILABLE. Root integration owns the new full Gradle build/check.

Scope limits: exact original-text ID presence is mandatory for every explicit queried ID. A separate grammar recognizes direct `Wo wohnt/wohnen/lebt/leben/arbeitet/arbeiten …?` questions and bounded anchored declarations. Requested subjects must occupy the declaration's subject role under the matching residence/work relation; negated location and work-role-only declarations cannot prove a place. Existing location aliases/inversion preserve prior measured positives. Other query forms retain previous policy; this is not general semantics, free-form proof verification, time-aware consistency or multi-record inference. Source-only lexical anchoring still applies after eligibility. No corpus IDF, weights, facts, ledger schema, revocation, CRE1 pool identity or model routing changed.

Whole project Node command (`env -u NODE_TEST_CONTEXT node --test --test-reporter=tap --test-concurrency=2 .github/continuum/*.test.mjs scripts/*.test.mjs`) was INTERRUPTED_RESOURCE_COORDINATION at parent request while native inference ran on the shared2-core host. Partial raw output is `node-full-interrupted.tap`; no whole-suite pass is claimed and parent owns the integrated rerun.
