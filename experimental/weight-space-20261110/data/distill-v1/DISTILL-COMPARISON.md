# EXP-12: completed pair and data-flow audit

The unchanged pair diagnostic was run on the completed distill-v1 raw log.
Raw log SHA-256 matches the execution receipt, which records exit code 0
and unchanged inputs. The reviewer ran no model inference or optimization.

| Final DEV measure | Answer only | Replay | Distill-v1 |
|---|---:|---:|---:|
| Exact rows | 38/64 | 35/64 | 35/64 |
| Both opposing labels correct | 6/32 pairs | 3/32 pairs | 3/32 pairs |
| Identical output within pair | 26/32 pairs | 29/32 pairs | 29/32 pairs |
| Exact class 0 | 28/32 | 28/32 | 28/32 |
| Exact class 3 | 10/32 | 7/32 | 7/32 |

All reported final pair/group metrics match the replay arm. This is not a
claim that learned weights or probability distributions are identical.
Baseline pair/group metrics match across all three arms. Distill-v1 adds
no measured contrast-pair improvement over replay on this consumed DEV set.

The independent log audit checked all 128 SCHEDULE entries: all 512 selected
answer IDs belong to TRAIN, with two examples per class per update. No DEV
or known-task retention IDs enter that schedule. Replay indices match the
fixed cyclic schedule; each of the 16 replay sequences is selected 16 times.

TEACHER before/after records report identical parameter and cache hashes,
16 examples, 915 token-position rows and source `replay.txt`. The 915 rows
match independent frozen-tokenizer counts including BOS for every replay
sequence. Hash equality is verified from native digest emissions/assertions;
the teacher/cache arrays were not independently exported and rehashed by
the reviewer. Static source review binds the logged schedule to loss inputs.

The required original successful continuation IDs 2 and 3 finish at only
12 and 18 tokens, below their original 32. Known-task restricted accuracy
falls from 32/64 to 0/64. These fail the prospective preservation gates even
though retained-text BPB improves from 2.801409006940589 to 2.723916624060788.
No candidate is promoted, and no further run was initiated by this review.

Evidence: `distill-pair-audit.json` and `distill-runtime-dataflow.json`.
Same open DEV/known-TRAIN limitations remain. The existing nine-test
diagnostic was unchanged; this is new input analysis, not nine new tests.
