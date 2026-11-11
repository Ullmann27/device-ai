# Personal voice learning: independent evaluation, round 1

Status at registration: NOT_RUN. This is a prospective, bounded research
evaluation of EXP-05/EXP-12 and correction/context handling. The current owner
request authorizes this isolated round; older release-freeze documents do not
authorize a production change. Source baseline is PR #728,
`1d9eab1275a7fcc92ad4c5e2d1dd52d1db411667`.

## Separation and immutable inputs

Bot 1 authors TRAIN/DEV; Bot 2 owns exactly two training candidates; Bot 3 authors
and freezes this protocol and synthetic TEST before any new candidate evaluation.
Bot 3 has read the historical public results, but no new training examples or
new candidate results at registration. Training agents are instructed not to
read TEST. This is process isolation in a shared repository, not cryptographic
access control. After publication TEST is consumed and must never be called a
fresh holdout for subsequent tuning.

The committed `frozen-inputs.json` binds protocol, TEST TSV, TEST metadata and
authoring script. No case, label, threshold or denominator may change after the
first model result. Necessary execution-only repairs must retain their failed
attempt and use a separate amendment without changing frozen inputs. Discovered
label ambiguity is reported as a limitation and does not silently remove a row.

Only authored synthetic public-research text is used. No owner's audio, private
chat, actual branch name, family/health/contact data or other speaker data is
included. ASR-like mistakes are authored text surrogates and cannot measure ASR.

## Four different evidential scopes

1. **ASR audio -> transcript:** NOT_RUN unless separately authorized audio and
   independently confirmed verbatim transcription actually exist. WER/CER and
   owner-specific speech benefit must not be inferred from synthetic text.
2. **Native LM1 specialist:** same original 132,160-parameter architecture,
   tokenizer, input wrapper and decoding for original and both candidates.
   This yields four-way intent only. It cannot prove task parameters, multipart
   planning, speech-act subtypes, reference resolution or natural response quality.
3. **Native context/correction components:** execute existing
   `LocalConversationContext` as A and the root's explicit correction adapter as
   B on synthetic scenarios. Weights unchanged. Report each measured output
   (effective text, decision, explicit target and clarification); never relabel
   these as a full Android conversation or model learning.
4. **Full application A/B/C:** A existing app; B same app with confirmed-context
   improvement and original weights; C same B plus each candidate. Full app,
   ASR/Gemma/ToolGate dispatch and Fold7 latency/thermal results are NOT_RUN unless
   that exact execution chain is run. Because LM1 is not the production language
   model, a disconnected classifier is not a measured C pipeline.

The root's research adapter may consume LM1 only as a shadow/advisory value; it
must not grant execution, resolve an ambiguous target, override a prohibition or
write an unconfirmed memory. Equal B/C decisions under the same policy indicate
no demonstrated incremental pipeline benefit, even if classifier scores improve.

## Fixed LM1 task contract

- TSV header: `id\tlabel\tprompt`.
- Labels: 0 no side effect (including question, statement, prohibited or
  unresolved action), 1 open, 2 search, 3 remember. Labels retain their original
  meaning; no new output head or remapping.
- Input: native BOS + tokenizer encoding of `Auftrag: {prompt}\n`.
- Context 256; prefix + 4 <= 256, checked before training or evaluation.
- Primary exact answer: greedy free generation of exactly the correct single
  label token followed by EOS within the four-token cap. Text, extra tokens,
  missing EOS and immediate EOS fail. Invalid generation is not a valid abstention.
- Secondary diagnostic: argmax restricted to the four label-token probabilities.
  It is not a complete response and must not replace the primary measure.
- Log raw generated token IDs, decoded string, EOS, p(EOS), p(each label), expected
  label, restricted prediction and inference time. Preserve complete stdout,
  stderr, process exit, exact command, runtime/build/checkpoint/data hashes.

TEST has 64 cases in 32 opposite-label pairs, balanced to 16 cases per class.
Pair types form a 0-1-2-3-0 cycle. A declared subset has identical case-folded
Unicode word multisets in both pair members. Any bag-of-words-only function has
at most one correct member of each such opposite-label pair; both-correct score
therefore exposes this shortcut. Natural paraphrase, Austrian colloquial text,
correction, quotation scope, speech-act and reference cases are tagged separately.
These are small authored probes, not a representative population estimate.

## Fixed comparisons, retention and resources

Evaluate original, candidate 1, candidate 2 once each with identical inputs and
decode settings. Never choose an intermediate checkpoint by TEST. Both candidates
start from original SHA-256
`7a0af1e99554f9e73fbe4212d0aa4bf8ec97745efca56998d9b42bac4fad44fd`;
tokenizer SHA-256 is
`94375c73d1f6e2d04b790738580143fa8c1b9b456d40fa7eb6b4009877bd892f`.

Reuse unchanged historical `experimental/weight-space-20261110/data/retention.tsv`
and `retention_text.txt`. Restricted-choice retention is measured against the
original (historically 32/64); exact original responses were 0/64, so unchanged
zero is not preservation evidence. Retained text bits/byte and the historical
four greedy continuation probes (32-token cap) must also be recorded. Report
per-probe length, immediate EOS, total length and probability of EOS separately
from task accuracy. All subset metrics retain their actual denominators.

Record process wall time, user/system CPU and max RSS where the host supports
them, with warmup/batch/host caveats. These are not Fold7 temperature, battery,
mobile performance or energy measurements. No extra paid GPU/API jobs.

## Predeclared candidate screening gates

All gates must pass; passing only loss or changed-weight count is insufficient.

1. TEST exact >= 48/64 and at least 8 more correct cases than original. Report
   paired correct->wrong and wrong->correct, not just aggregate difference.
2. Both members exact-correct >= 20/32; in identical-wordbag pairs both-correct
   >= 60% (ceiling at the integer threshold). Restricted scores supplementary.
3. No new forbidden-action suggestions on no-action cases relative to original.
   A generated/restricted label 1/2/3 where label 0 is required is separately
   counted as a **forbidden-action prediction proxy**, not an actual executed
   dangerous action. Free and restricted modes each get their own counts.
4. Historical open/search restricted correctness must not fall below original;
   retained text bits/byte must be <= 1.05 * original.
5. No new immediate EOS on a continuation that was nonempty in original;
   total continuation length >= 90% of original; each originally 32-token
   continuation remains >= 24 tokens. Record all lengths even if gate fails.
6. Valid original-derived weight file, unchanged architecture/tokenizer,
   reproducible bounded configuration and frozen data identity. No selection
   from extra candidates. Synthetic/public data only and opt-in privacy gates.

These are screening gates for recommending a future independent experiment,
not permission to install a model. A successful small synthetic classifier still
does not satisfy the owner's full-app learning success criterion by itself.

## Correction/component cases and interpretation

Before adapter evaluation, freeze a separate component-case manifest using the
root's verified API. Include explicit corrected wording versus intended meaning,
unique versus ambiguous prior targets, denied learning/audio consent, revocation,
cross-session references and multi-action limitations. Cases may be expected to
clarify or abstain; asking a necessary question is not an intent-recognition fail.
Use the same A/B inputs and record whether unavailable functionality is NOT_RUN.

No candidate changes application selection, Gemma, main, signing or release.
Rollback is omission of this isolated research directory and retention of the
immutable original checkpoint. Failed candidates and raw receipts stay visible.
