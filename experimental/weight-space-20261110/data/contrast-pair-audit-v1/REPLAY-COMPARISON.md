# Fixed replay arm: pair comparison

The unchanged diagnostic was run over the completed replay-arm raw log.
Raw INPUT hashes, exact-output flags, 64-case coverage at both steps and
pair metadata passed validation. Baseline pair/group results are identical
between arms. This analysis performs no model inference or weight updates.

| Final DEV measure | Answer only | Answer plus replay |
|---|---:|---:|
| Exact rows | 38/64 | 35/64 |
| Both opposing labels correct | 6/32 pairs | 3/32 pairs |
| Identical output within pair | 26/32 pairs | 29/32 pairs |
| Exact class 0 | 28/32 | 28/32 |
| Exact class 3 | 10/32 | 7/32 |
| Directive first | 16/32 | 16/32 |
| Directive last | 22/32 | 19/32 |
| Short content | 19/32 | 17/32 |
| Long content | 19/32 | 18/32 |

This fixed replay arm does not improve the tested contrast-pair behavior.
Three fewer pairs are completely correct; class-3 recall falls by three
cases. No causal claim about replay in general follows from one seed and
this narrow, shortcut-prone open DEV set. Generation and retention must be
assessed separately; pair accuracy is not a model-promotion criterion.

Source and input SHA-256 bindings are recorded in `replay-pair-audit.json`.
The existing nine-test diagnostic was unchanged; this run is a new input
analysis, not a claim of nine additional tests.
