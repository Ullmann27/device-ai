# LM1 continuation: prospective EOS and optimization diagnosis

Status before execution: `REGISTERED_NOT_RUN`. Scope: EXP-04, EXP-05, EXP-07, EXP-12; isolated host research only.

## Sources and hypothesis

Parent source is PR #492 at `30cd3a89810ed14fa84eca95236d3177e016118d`. Its strengthened G1 gates and historical evidence remain unchanged. The new diagnostic uses the same existing model, tokenizer, weight-surgery implementation and trainer in a new output directory.

The width-8 transformation divides final norm gains and replicated input weights by eight to preserve the initial function. A uniform absolute Adam step need not preserve equivalent effective update sizes. Two interventions test that hypothesis. It is not an established explanation of the observed immediate EOS, and scaling only final-norm updates is not a full muP implementation.

Primary research:

- Chen, Goodfellow, Shlens, *Net2Net*, https://arxiv.org/abs/1511.05641 — function-preserving expansion, symmetry-breaking and continuation training.
- Yang et al., *Tensor Programs V*, https://arxiv.org/abs/2203.03466 — width-dependent parameterization and transfer of optimization settings, including tied input/output embeddings.
- Eldan, Li, *TinyStories*, https://arxiv.org/abs/2305.07759 — motivation for a separate bounded task-learning study; not evidence that this minGRU already has those abilities.

## Fixed four-arm comparison

| Arm | Parameters | Learning rate | Applied final-norm update |
| --- | ---: | ---: | ---: |
| small-standard | 132160 | 0.0001 | unchanged |
| wide-standard | 6562304 | 0.0001 | unchanged |
| wide-nf-scaled | 6562304 | 0.0001 | one eighth of the original trainer update |
| wide-rate-scaled | 6562304 | 0.0000125 | unchanged |

Every arm starts independently from the original or its freshly generated width-8 transformation. Seed 20261104, width noise 0.001, batch 4, 16 updates, warmup 2, weight decay 0.01, gradient clipping 1 and two workers are fixed. All use the same 3255 target tokens. Final-norm scaling preserves the original Adam moments; it adjusts the actual parameter delta after the step. In the global-rate arm the rate also changes the effective decoupled weight-decay update; do not attribute its entire effect exclusively to final normalization.

Required input SHA256 values:

- Original: `7a0af1e99554f9e73fbe4212d0aa4bf8ec97745efca56998d9b42bac4fad44fd`.
- Tokenizer: `94375c73d1f6e2d04b790738580143fa8c1b9b456d40fa7eb6b4009877bd892f`.
- Historical TRAIN corpus: `27e67455e23dcc410855b2fd0f0292f9a00556d72d62aefb018e9eab4be034c9`.
- Actual token schedule: `8e0d6ef53de6c84e48c74ecc4dbd4b70e25632caadc624f31ad0ac4aad681fee`.

The registration manifest additionally binds development data, every compiled source, the executable JAR, Kotlin runtime and new experiment sources/protocols. Compiler/JVM versions are recorded. This is a new within-environment comparison; identical historical wall times or byte-identical compiler outputs are not presumed.

## Gates and observations

Before optimization: exact input identity, finite/model-layout checks, full and recurrent initial parity, then the unchanged capacity benchmark in a fresh JVM with 1 GiB heap and a 120-second process limit. A failed initial gate stops the arm before any training update. The original baseline is never overwritten; the new experiment does not resume failed historical runs or import their success flags.

Each training process has a 3 GiB heap ceiling and an external 600-second limit. Raw stdout/stderr and process exit status are retained even on failure. Sources and inputs must retain their registered hashes.

At steps 0, 1, 4 and 16, record:

- Content NLL/BPB and terminal-EOS NLL separately on the fixed development texts.
- NLL on the same first minibatch, so changing training batches cannot masquerade as a learning curve.
- EOS probability and EOS-minus-best-other-token margin on the original capacity prompt, three complete development texts and their actual half-length token prefixes.
- Proposed and applied final-norm update magnitude, finite values and actual target-token count.

After training, rerun the original capacity benchmark separately on every final checkpoint. Do not suppress EOS, force a minimum length or relax the original full 32-token workload. Report all four arms, including unsuccessful ones.

## Prospectively defined interpretation

A limited successful continuation requires a completed arm, an independently verified final checkpoint, a passing unchanged capacity benchmark, and development content BPB lower than its own step-0 value by more than 0.00001. A smaller norm update alone is not a learning success. A passing workload alone is not a quality gain. A development gain is not an independent held-out generalization result.

If the interventions do not prevent the EOS failure, report that result and narrow the hypothesis. Do not relabel failure as intelligence growth. The separate intent study has its own fresh split, criteria and freeze; none of the old disclosed holdouts is used to tune this diagnosis.

Rollback: retain original model and all historical evidence, discard unsuccessful new candidates. No production runtime edit, model promotion, paid AI API, release, installation or Android-performance claim.
