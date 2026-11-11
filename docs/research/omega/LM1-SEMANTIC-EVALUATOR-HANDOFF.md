# LM1 semantic evaluator handoff

Research basis: `1d9eab1275a7fcc92ad4c5e2d1dd52d1db411667` (PR #728).
Branch: `research/continuum-lm1-semantic-evaluator-20261111`.
Owner: independent evaluator, Bot 3. Coordinator: parent agent.
Status: PRE-TRAINING PROTOCOL; model evaluation NOT_RUN.

Only new evaluation files and this handoff are owned by this branch.
The explicit current owner experiment supersedes historical research freezes;
all product, tool, signing, and main-branch protections remain intact.

Protocol: `experimental/lm1-semantic-20261111/evaluation/PROTOCOL.md`.
Input contract: class 0 none / 1 open / 2 search / 3 note; native BOS and
`Auftrag: {prompt}\n`; one class token plus EOS. Do not read independent
holdout contents during training. Shared storage offers organizational, not
technical blinding. No other agents may be created.

Read-only finding from old raw stdout: original retention predictions are
56 search and 8 open, with 28/32 correct search and 4/32 correct open.
Therefore 32/64 is not proof of strong task understanding. Preserve measured
behavior while reporting this limitation and the zero exact-output baseline.

Checks so far: exact worktree branch and basis verified; clean initial tree;
historical confusion reconstructed from actual TSV. No native model invocation.
Next: author and freeze disjoint holdout, build bounded native inference
harness, audit legacy evidence, evaluate only after candidate freeze.
Rollback: omit this isolated research branch; no product paths were edited.
