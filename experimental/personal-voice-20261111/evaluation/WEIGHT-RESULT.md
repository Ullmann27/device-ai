# Independent LM1 result: both candidates rejected

The new cases and gates were frozen in remote commit
`9deac1792e79736167c5169c916941d45c8f9a96` before candidate results.
Both final checkpoint hashes were supplied before any new TEST inference.
Exactly 128 TEST+historical-retention cases were then evaluated once for each
of original, replay, and preservation checkpoints. No further training or
checkpoint selection occurred. A separate pre-training original run contained
only already-public historical retention cases, not fresh TEST.

| Metric | Original | Balanced replay | Balanced + preservation |
| --- | ---: | ---: | ---: |
| Exact unknown intent response + EOS | 0/64 | 17/64 | 17/64 |
| Restricted four-label argmax | 16/64 | 17/64 | 17/64 |
| Both opposing cases exact | 0/32 | 1/32 | 1/32 |
| Both identical-wordbag cases exact | 0/17 | 0/17 | 0/17 |
| Historical open/search restricted | 32/64 | 18/64 | 25/64 |
| Historical open/search exact | 0/64 | 14/64 | 21/64 |
| Retained text bits/byte | 2.801409007 | 2.676345915 | 2.713949990 |
| Four continuation lengths | 7 / 4 / 32 / 32 | 0 / 4 / 2 / 1 | 6 / 4 / 3 / 1 |
| Invalid TEST response format | 64/64 | 0/64 | 0/64 |
| Forbidden-action prediction proxy, free output | 0/16 | 16/16 | 16/16 |
| Forbidden-action prediction proxy, restricted | 16/16 | 16/16 | 16/16 |

The principal observed improvement is adoption of the numeric output format.
Restricted semantic accuracy rises by only one case, from the 16/64 balanced
constant-class baseline to 17/64. Both candidates have identical TEST decisions:
37 searches, 26 opens, one no-action, no remember response. The one no-action
prediction is on a different gold class; none of 16 true no-action cases are
recognized. No learned negation/role distinction is established by 0/17
identical-wordbag pairs. Both originally 32-token continuations are severely shortened.

The original's invalid free output is not correct abstention. Changing it into
an executable-looking wrong action label is the reason for the new free-output
proxy count; no device action was executed and LM1 never received execution
authority. The restricted proxy was already bad and did not improve.

Lower retained-text loss does not compensate for lost historical classification
and severe output shortening. Preservation reduces the immediate-EOS problem
on probe 0, but it does not meet preservation gates overall. Both candidates
fail the prospective screening and must stay excluded from production.

## Actual weight and schedule evidence

An independent Python LM1 parser checks file checksum, architecture, tokenizer,
every tensor layout and finite Float32 values. All 132,160 numeric parameters
changed in each arm. Weight decay also contributes, so this count is not a
capability claim. An independent Java-RNG implementation reconstructs all 128
eight-example sampling schedules in each arm exactly. Update counters are
exactly 1..128, both start from the original checkpoint, and the frozen teacher
parameter/cache digest remains unchanged. These checks do not constitute a
second full training reproduction; source/recipe/artifacts enable that later.

Automated TEST-versus-TRAIN/DEV/supervised-replay checks find zero exact prompt
overlap and zero identical-wordbag overlap. Concept families are intentionally
shared; this small synthetic suite is not an owner speech population estimate.

Checkpoint SHA-256:

- Original: `7a0af1e99554f9e73fbe4212d0aa4bf8ec97745efca56998d9b42bac4fad44fd`
- Replay: `e1e231cc2b30f2c8a36af0b0b9c7edcfbc13a19ae6e73faee501448d43f511fe`
- Preservation: `4d0cb91226970737bd3413736b66d8688714f2c65ae0e383501aadd6bd5f763f`

## Resources and limitations

Native evaluation wall times: 6.4134 s original, 3.7583 s replay, 3.2461 s
preservation (total 13.4178 s on this host). Reduced generation length affects
runtime. First-distribution median is 6.592 / 6.955 / 6.273 ms, but occurs **after
teacher-forced NLL** on each case; it is not a cold first-token benchmark. Case
median includes that NLL and greedy decoding: 38.909 / 24.136 / 20.394 ms.

`/usr/bin/time` was unavailable to the evaluation runner; per-evaluation CPU and
RSS were not captured. Training resource receipts are separate and must not be
relabeled as evaluation measurements. No energy, thermal, Fold7 or microphone
measurement occurred. WER/CER, owner-specific personalization and full app A/B/C
are NOT_RUN. Context component results are reported separately and cannot be
attributed to these rejected weights.

Complete raw model outputs, input/build hashes, commands, exit codes, independent
score reconstruction and training audit are under `results-v1/`. The 12 evaluator
mutation/gate tests passed before execution. No failed candidate or unfavorable
case was dropped from the denominator.
