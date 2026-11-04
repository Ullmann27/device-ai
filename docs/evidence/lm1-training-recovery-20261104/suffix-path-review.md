# Read-only review: direct layer-0 state retention across the answer suffix

Date: 2026-11-04. Scope: `RESEARCH_SHADOW / ORIGINAL_LAYER0_DIRECT_STATE_PATH_ONLY`.

## Result and its boundary

In the immutable original LM1 checkpoint, the five tokens of `\nAktion:`
multiply an incoming layer-0 state difference by a positive, coordinate-wise
retention factor. Across the 64 coordinates, the factor has median
**0.0034327914719374866** and maximum **0.05431863335836823**.
**49 of 64 coordinates retain less than 0.01 of their incoming difference.**

This is a calculation of the **direct recurrent state path within layer 0 at
the original checkpoint**. It is not a measurement of a full-model gradient,
the complete network Jacobian, or the learned state of an intent model. No v1
or v2 training checkpoint, task example, or sealed test file is read by the
reproduction script. No training is performed.

The result supports a possible future hypothesis about the common suffix
weakening class distinctions. It does not establish that this caused the v1
development result. The registered long-study run keeps its suffix unchanged.

## Exact inputs and source identities

| Input | SHA-256 |
|---|---|
| Original `lm1-baseline.lm1` | `7a0af1e99554f9e73fbe4212d0aa4bf8ec97745efca56998d9b42bac4fad44fd` |
| Original `lm1-tokenizer.lmbp` | `94375c73d1f6e2d04b790738580143fa8c1b9b456d40fa7eb6b4009877bd892f` |
| Reproduction script `suffix_probe.py` | `57bfd97df92dc1f795f83c8adc8f81e3f398b5b54586417bbb8b7caf71ecb7b2` |

The script verifies both complete input hashes before interpreting the files.
It also verifies the checkpoint's embedded body checksum, tokenizer binding,
matrix layout, and finite parameter values. A different or trained checkpoint
is rejected even if it has a compatible shape.

The decoded original configuration is minGRU, vocabulary 512, dimension 64,
two layers, stored `heads=4`, context 256, and MLP width 256. The minGRU branch
does not use the attention-head setting.

The formula and binary layout were checked against the unchanged LM1 sources
from parent commit `30cd3a89810ed14fa84eca95236d3177e016118d`:

| Source under `app/src/main/java/at/heinz/deviceai/learning/lm/` | SHA-256 |
|---|---|
| `LmModel.kt` | `abb4b50cc443344aefe720844aad4a9ca3e3d967973cc31a4e45ca07b732f099` |
| `LmTensor.kt` | `a5497f18d09be50f7afbd201d54d97f012f7508f8c53c834ff6c10ef278290ab` |
| `LmBpeTokenizer.kt` | `fcff707a5a1679244cffbb6583e1594b6084e941c7e918f834d71499029b628e` |
| `LmCheckpointStore.kt` | `09bf68c1c765b6ed625d71672a2aa836fedc3f587546e9197d4b21ee0e446b17` |

## Why this particular path can be calculated without task data

In layer 0, the incoming residual activation is the embedding of the current
token. There are no learned positions in the minGRU branch. For token `u_t`,
the operations relevant to the recurrent state are:

\[
r_t = \operatorname{RMSNorm}(E[u_t],\gamma_0),\qquad
z_t = \sigma(r_t W_z+b_z),\qquad
c_t = r_t W_c,
\]

\[
s_t=(1-z_t)\odot s_{t-1}+z_t\odot c_t.
\]

For two prefixes followed by the same suffix tokens, `r_t`, `z_t` and `c_t`
are identical within layer 0 during the suffix. Therefore:

\[
\Delta s_{\mathrm{end},j}
=\Delta s_{\mathrm{start},j}
\prod_{t\in\mathrm{suffix}}(1-z_{t,j}).
\]

The derivative of the ending layer-0 state with respect to its incoming state,
holding the suffix inputs and parameters fixed, is the diagonal matrix of
these products. `LmTensor.minGruScan` uses the same factor in its backward
carry: `carry[j] = dh * (1 - z)`.

This statement stops at the layer-0 state. Layer 1 has its own recurrent state;
its inputs, gates, and candidates can depend on layer-0 outputs. Output gates,
projections, residual paths, normalization, and the tied vocabulary head also
participate in the complete network. Consequently the factors reported here
cannot be equated with the gradient of classification loss with respect to an
earlier word or parameter.

## Tokenization and measured values

For the fixed literal `\nAktion:`, the original tokenizer's pretokenization is
exactly the three segments `\n`, `Aktion`, `:`. Applying its registered BPE
merges yields:

| Token | Decoded piece |
|---:|---|
| 10 | newline |
| 65 | `A` |
| 107 | `k` |
| 289 | `tion` |
| 58 | `:` |

The script separately calculates a one-token newline reference. It does not
train or evaluate a model with that alternative prompt.

| Direct layer-0 retention | Five-token `\nAktion:` | One-token newline reference |
|---|---:|---:|
| Minimum coordinate factor | 0.00013912226372482856 | 0.024849609771614656 |
| Median coordinate factor | 0.0034327914719374866 | 0.19000741341985544 |
| Maximum coordinate factor | 0.05431863335836823 | 0.841218493426585 |
| Coordinates below 0.01 | 49 / 64 | 0 / 64 |
| Coordinates below 0.1 | 64 / 64 | 13 / 64 |

For an internal algebra check, the script applies the actual layer-0 gates and
candidates to two artificial starting states, all zeros and all ones. The
ending state difference agrees with the product formula to maximum absolute
error `1.214306433183765e-16` for the five-token suffix. These starting states
are a check of the local affine identity, not a pair of classified task inputs.

Weights are decoded from their original float32 representation; subsequent
arithmetic uses Python float64. This is an analytic numerical reconstruction,
not bit-exact Kotlin runtime execution. The script prints the individual
coordinate products as well as the summary, identities, and limits.

## Reproduction

Run from the research repository root with Python 3.10 or newer. Only the Python
standard library is required. The command writes JSON to stdout and creates no
result files:

```sh
python3 experimental/lm1-training-recovery-20261104/suffix_probe.py \
  --baseline /workspace/scratch/3112c86b5191/training-recovery-inputs/lm1-baseline.lm1 \
  --tokenizer /workspace/scratch/3112c86b5191/training-recovery-inputs/lm1-tokenizer.lmbp
```

The successful reproduction exited with status 0. The original checkpoints,
registered study files, task data, and application sources were not modified.

## Independent long-study harness diff review

The complete text diff between the following files was reviewed:

| Harness | SHA-256 |
|---|---|
| v1 `LmIntentStudy.kt` | `0fb9552ad0d8cc4d2f6cc43ad8700374da27ae9ca2e794f1765fca45c11c2e9d` |
| `long-study/LmIntentLongStudy.kt` | `e1d469e0d702041a12cf250ac86bd5fcde18351347ba05a4824566c79b846fe4` |

Exactly five controlled changes were present:

1. Object name: `LmIntentStudy` to `LmIntentLongStudy`.
2. Update count: 128 to 512.
3. Checkpoint experiment identity: `lm1-intent-20261104` to `lm1-intent-long-20261104`.
4. Internal training-loop deadline: 540 to 840 seconds.
5. Trained development snapshots: 32/64/128 to 128/256/512.

No other training, prompt, masking, optimizer, decoding, or selection code
differs between these two reviewed files. The existing cosine formula depends
on `STEPS`, so its horizon changes with the update count. The long-study protocol
explicitly describes this as a longer fresh run with a stretched schedule;
it does not claim to isolate additional steps while preserving the earlier
learning-rate sequence. The suffix calculation above introduces no change to
that registered comparison.
