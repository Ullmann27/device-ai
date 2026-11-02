# M2 — actual frozen Kotlin candidate, 55-case reproduction

Status: **COMPLETED_REPRODUCTION_CONSUMED_BLIND_C / RESEARCH_SHADOW / DEVICE_NOT_RUN**.
This reproduces an already consumed Blind-C; it is not a new blind evaluation.
No training, tuning, relabeling, new corpus, app edit or production promotion occurred.

| Split / mode | Exact supported | Correct missing-answer abstentions | Unsupported positives | False source assignments |
| --- | ---: | ---: | ---: | ---: |
| PRIMARY RAW | 20/20 | 5/5 | 0 | 0 |
| PRIMARY VERIFIED | 20/20 | 5/5 | 0 | 0 |
| PRIMARY RULE | 20/20 | 5/5 | 0 | 0 |
| ADVERSARIAL RAW | 5/5 | 23/25 | 2 | 0 |
| ADVERSARIAL VERIFIED | 5/5 | 25/25 | 0 | 0 |
| ADVERSARIAL RULE | 5/5 | 25/25 | 0 | 0 |

RAW remains wrong on **C048 and C049**, both relation controls. It copies a real source span
when the frozen label requires abstention. Thus zero false-source assignments does not
mean zero unsupported claims. The existing verifier rejects both; RULE abstains on both.
No malformed outputs or false abstentions occurred. The scorer's primary gate is true
for all three modes; its same gate is false for the differently sized adversarial split.
Full tuple decisions, paired disagreements and Wilson 95% intervals are in
`independent-score.json` and `reproduction-report.json`.

All **165** fresh mode decisions (55 cases × 3 modes), including both RAW failures,
match the previous session's actual producer output objects exactly. Timings differ and
are excluded from this comparison. The previous raw objects were assembled into the
new scorer's schema without changing their contents, then independently rescored;
split metrics match exactly. The old Python aggregate omitted `decision:SPAN`, so the
comparison correctly uses preserved producer outputs rather than that aggregate file.
`previous-predictions-adapted.json` and `previous-independent-score.json` preserve this check.

The frozen candidate was compiled with the existing probe/verifier source directly;
no cached app bytecode or full Android build was used. Frozen parent source reference is
`7327367851df147fef6226233fc86a22efcfe2fc`; actual supervising Git HEAD at run was
`ec37288555fc29d8af99a276ea7323792a9f4bf8` (recorded in the JSON report). Candidate source SHA-256:
`9c7d33b64d7fb1b13e3ceb22f2b538f3e4e62608d477f7e9868ce1f3b79c5f2e`.
Checkpoint SHA-256:
`629c8319ede2aba3cd60f0448b6469034c8550530e0326739b79c26bfc40939d`
(910 bytes, 11 weights). Corpus SHA-256:
`3bd5bddb76a50d020c9216db9eebf7ba9b717967a7b0947b3415f3a2c0fd5a1c`.
Probe/verifier, scorer, runner and dependency hashes are recorded in the report.
Every file under the original frozen M2 evidence directory retained its initial hash.

The existing independent scorer's **11 tests passed**, including its loop of **100
negative tuple mutation samples**. These are scorer probes, not 100 models or 100
device experiments. Three additional predict-only controls passed: real source-copy,
mutated source text with stale hash, and invalid source revision. Their requests and
outputs are under `controls/`; they are separate from the 55-case score.

Host toolchain: Kotlin 2.0.21, Temurin JDK 21.0.13, Node v24.19.0.
55 fresh JVM processes took **14.378 s** wall time; child user/system CPU was
17.718/2.424 s. RAW selector median was **78.830 ms**, nearest-rank p95 **183.962 ms**;
fresh JVM/class/regex startup affects these measurements. Classifier NLL is
**NOT_RECORDED**: the frozen prediction CLI emits selected tuples rather than class
probabilities. No autoregressive/byte NLL or matched legacy-model improvement is claimed.

Run from the repository root with fresh destinations:

```bash
python3 tools/continuum-span-research/reproduce-cdx2-blind-c.py \
  /workspace/continuum-checks/cdx2-reproduction-evidence \
  --build-dir /workspace/continuum-checks/cdx2-reproduction-classes
```

The runner accepts `--java`, `--kotlinc`, `--jars`, `--node` and `--previous` overrides.
The default paths refer to the session's installed host toolchain. It invokes only the
frozen `predict` CLI, passes `id/question/sources` only, saves actual JSON before
scoring, refuses existing destinations, and verifies frozen-file immutability.
Missing historical workspace outputs are reported as unavailable on another machine;
the committed comparison artifacts retain this session's comparison evidence.

The handcrafted German grammar and 11-weight selector remain a constrained extractive
research candidate. This is no CDX1 generation repair, Android/Fold7 validation,
app runtime integration, model import or release. Rollback is removal of this new
runner and evidence directory; all previous model/input/app files are unchanged.
