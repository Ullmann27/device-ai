# Sol #314 — CDX1, dialogue and evidence

Status: RESEARCH_SHADOW. No merge, signing, publication or Fold7 verification.

## Baseline and coordination

Fresh remote release HEAD: `97b5b9006f8c60f1a6fc4fb10981894330c55fe4`.
Historical main: `2949893a7c65bdc6a875bdcb28b7a550fd9d7c3a`.
Branch: `research/continuum-sol-cdx1-evidence-20261101`.
PRs #299, #301, #305, #309–#313 are open; #310 is stacked on #309.
Astra #315 already has streaming-import PR #316. Sol does not build a second
source store or worker. Shared QA script change only whitelists this branch.
The current user mandate supersedes historical v31 research-freeze notes.
EXP-04/05/06/07/12 remain compatible; existing checkpoint formats are unchanged.

## First reproduced defect and repair

On the actual CDX1 implementation, seed 91 / SWB1 seed 73, 120 synthetic updates:
train `Anna wohnt in Graz.` / `Wo wohnt Anna?` / `Graz`;
validation `Berta wohnt in Linz.` / `Wo wohnt Berta?` / `Linz`.
Training NLL 5.5531206598 → 4.5374142234;
validation NLL 5.5516778578 → 5.1859544910.
The baseline accepts and publishes an incorrect free answer of 48 `a` bytes.
This is a synthetic HOST reproduction, not the unavailable private Fold7
checkpoint whose reported prefix is `99ea2346547dae3d`.

The first-byte suffix is **`ntwort: `**: `Antwort: ` is nine bytes, not eight.
Different evidence/questions with equal conditioning give identical first-byte
distributions. The conditioning vector is the only remaining input distinction.
This observation alone does not prove the entire device collapse mechanism.

`DecoderGenerativeGate` now measures free sequences including EOS, exact factual
answers and explicit abstention separately. Promotion requires ≥20 supported
validation questions, ≥5 missing-evidence controls, ≥80% supported correctness,
all controls explicitly abstained, all sequences terminated, a strict paired
gain and no previously correct answer lost. Existing NLL/protected gates stay.
The published 25-case intelligence TEST is preserved and never used for selection.
Historical known-only validation cannot establish abstention and therefore no
longer promotes weights. Existing checkpoints are retained; direct experiments
can still train an isolated in-memory `TinyContextDecoder` candidate.

RED: new regression fails specifically at the old erroneous acceptance.
GREEN: actual Kotlin 2.0.21 / JRE21 / JUnit4.13.2, focused 28 tests, resources on
classpath. Not Android Gradle/Lint/APK/device evidence. The earlier resource-less
test invocation failed on missing fixture; rerun includes unchanged fixtures.

## Reproduction and continuation

Compile `LearningSnapshot`, `TinyContextDecoder`, `TrainableSubwordEncoder`,
`ContinuumPairedEncoderBinding`, `ContinuumAtomicPairedBundleStore`,
`ContinuumDecoderBridge`, `DecoderGenerativeGate` and the four JUnit classes
`DecoderGenerativePromotionTest`, `DecoderGenerativeGateTest`,
`ContinuumDecoderBridgeTest`, `TinyContextDecoderTest` with Kotlin/JUnit.
Run JUnitCore with `app/src/test/resources` on the classpath; Render runs these
through the original Android Gradle QA script on the exact pushed commit.

Independent review found and prompted corrections: free scoring now checks
cancellation/deadline before/between items and inside byte generation; the
shared budget is the Android runtime's 48 bytes. Structurally ineligible large
curricula return `INSUFFICIENT_VALIDATION` without training, retry or cursor
consumption. The added qualifying 20+5 bridge regression verifies that improved
NLL still cannot publish an incorrect generator and preserves an existing
checkpoint. Focused host suite: **30/30 passed**. No original tests removed.

Render first-commit QA: service `srv-dbjatfetlv9c73cmufs0`, deploy
`dep-dbjatfmtlv9c73cmugig`, source `b7ca85ba9a05f8ff626dfea9e71fa1c2bd2adfc9`;
started 2026-11-01 03:15:10 UTC, outcome pending. Subsequent source needs its own
exact-SHA QA. The static site serves audit text only; it is not an active worker.

Next: causal ablative measurements of conditioning / free vs teacher-forced
outputs on frozen synthetic checkpoints; independently evaluated answer-path
improvement, dialogue regression, evidence provenance and exact-SHA Render QA.
Model/device tests remain unavailable until actually run. Rollback is to drop
this isolated branch; no owner data migration or model checkpoint replacement.
