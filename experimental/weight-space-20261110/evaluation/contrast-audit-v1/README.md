# Frozen two-arm contrast pilot

EXP-05, RESEARCH_SHADOW. Both arms completed exactly 128 native updates;
both fail the original favorable-DEV criterion. Original logs stay unchanged.
The 135 baseline log records are byte-identical between arms.

| Measurement | Original | Answer | Replay |
| --- | ---: | ---: | ---: |
| DEV label plus EOS | 0/64 | 38/64 | 35/64 |
| Both members of contrast pair correct | 0/32 | 6/32 | 3/32 |
| Open/search four-choice diagnostic | 32/64 | 0/64 | 0/64 |
| Text bits per byte | 2.801409 | 2.889189 | 2.646510 |
| Generation probes reaching 32 tokens | 2/4 | 0/4 | 0/4 |

Replay text BPB improves 5.5293% relative to the original. It does not prevent
early EOS or lost open/search classification. Exact open/search is 0/64 in
all arms, a floor effect rather than evidence of retention. All 132,160
numeric weights change in each candidate. Neither model is promoted.

The independent Python audit checks receipt/registration/input/checkpoint
identities, recomputes exact labels from tokens and EOS, verifies all case
IDs, all 128 updates, finite values, and the original aggregate metrics.
13 negative/coverage tests pass. The separate pair analyzer has 9 passing
tests. Coordinator reran both suites after integration. A first coordinator
unittest-discovery invocation for the evaluator failed because its fixture
argument was absent; the supported explicit command below passed 13/13.

From repository root:

```sh
python3 experimental/weight-space-20261110/evaluation/contrast-audit-v1/test_analyze_contrast.py --fixture-root experimental/weight-space-20261110 -v
python3 -m unittest discover -s experimental/weight-space-20261110/data/contrast-pair-audit-v1 -p 'test_*.py' -v
```

`analyze_contrast.py --help` lists explicit inputs for the full artifact
audit. Historical registration includes absolute execution paths; its full
15-input environment recheck requires those original files. Pure log tests
above use repository fixtures. Never rewrite frozen registration to disguise
a different environment; publish a separately bound replay registration.

Next bounded proposal: preserve the original output distribution on the
existing training replay texts using a frozen teacher. This is a new open
DEV experiment, with no tuning sweep and no new architecture or app change.
