# Continuum Coding V1 – Rejection-feedback integration

Source of truth: `Ullmann27/device-ai`, Sol baseline `c46ad26bbb7982f8c602ac8fc528658f56f3747e` (draft PR #125).

## Narrow change and motivation

The checked-in frozen local Qwen repetition in Sol's evidence ended `ABSTAINED`. Its model returned an unchanged `oldText == newText` patch, which the local JSON/schema gate rightly rejected. Previously the coding CLI discarded the reason and the repair-loop swallowed the error. Three model calls could therefore run without a specific rejection message to guide the next revision.

This follow-up does **not** relax the owner/critic/commit/file gates, create an independent repair framework, merge a branch, sign an APK, install anything, promote model weights, or grant free-form shell commands.

## Implemented in this branch

1. `scripts/continuum-local-brain.mjs`: invalid coding-model responses carry a fixed `CODING_RESPONSE_INVALID:<sha256(raw-response)>` identifier across the trusted local CLI boundary. The raw rejected answer is not placed in this error.
2. `.github/continuum/coding-provider.mjs`: interpret **only** the exact CLI rejection format and return a structured `error.code` and `error.responseSha256`. Malformed stdout/schema replies get a digest of the bounded CLI response. Unknown stderr and process failures remain `CODING_MODEL_UNAVAILABLE`.
3. `.github/continuum/repair-loop.mjs`: bounded typed rejection entries persist in `report.inference.rejections`; the next revision receives a short fixed diagnostic and a bounded history entry. Raw error strings are never copied into a report or prompt. If two invalid model answers have the same full SHA-256, stop repeating requests. Preexisting candidate-critique, fixed tests, isolated worktree and owner review remain unchanged.
4. `.github/continuum/coding-rejection-feedback.test.mjs`: add regression tests for no-op SHA-256 redaction, CLI/provider propagation, successful correction after a rejected response, early stop on duplicate rejected output, and unknown-error redaction.

## Validation and reproducibility

Syntax-only V8 compilation of the four touched JavaScript modules, with ESM import/export boundaries removed **for parsing only**, completed without a syntax error. **This is not execution of the repository test suite.** The tests added in this branch have not been executed against the full checkout in this connector-only session.

Run in a properly provisioned isolated clone of **this exact branch**:

```sh
node --test .github/continuum/coding-rejection-feedback.test.mjs \
  .github/continuum/scoped-repair-pipeline.test.mjs \
  .github/continuum/coding-provider.test.mjs \
  scripts/continuum-coding-brain.test.mjs
env -u NODE_TEST_CONTEXT node --test .github/continuum/*.test.mjs scripts/*.test.mjs
```

Before promotion, perform Sol's independent Docker proof and a NEW frozen-source real-model run. Record commit, exact model hash, rejection SHA-256s, actual test counts, and whether the model solved a non-pretrained task. Do not interpret the tests above as proof of model capability.

## Next proven gaps (not part of this patch)

- The Android general coding draft is not yet sent through the existing Termux bridge; Fold7 execution is not proven.
- `FEATURE` tasks still inherit a repair-only, failing-baseline requirement.
- Real generative Kotlin and multi-file model repairs remain unproven.
- Crash replay between mobile receipt and CCC1 receipt needs explicit new negative tests.

Leave PR #125 and this follow-up as drafts until a clean isolated real runtime test is green and the owner separately approves integration.
