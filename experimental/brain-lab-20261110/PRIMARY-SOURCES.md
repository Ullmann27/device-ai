# Verified primary sources and implementation boundaries

Checked 2026-11-10. Only the following narrow claims are carried forward;
user-pasted citation identifiers are not treated as verification.

| Primary source | Verified claim | Boundary here |
|---|---|---|
| Alex Graves, [Adaptive Computation Time for Recurrent Neural Networks](https://arxiv.org/abs/1603.08983v6), 2016/2017 | ACT learns the number of recurrent computation steps; the abstract also reports no large language-model performance gain in its character-level experiment. | No ACT network, learned halting, training or character-model replication is implemented. |
| Dehghani et al., [Universal Transformers](https://arxiv.org/abs/1807.03819v3), ICLR 2019 | Parallel-in-time recurrence and dynamic per-position halting are architecture-level mechanisms. | Repeated prompt calls do not recreate hidden-state recurrence. |
| Geiping et al., [Scaling up Test-Time Compute with Latent Reasoning: A Recurrent Depth Approach](https://proceedings.neurips.cc/paper_files/paper/2025/hash/3b01972cf31e6fa0fe29e4b8b5c2a0a1-Abstract-Conference.html), NeurIPS 2025 | The authors train a recurrent-block language model and increase its inference depth. | Neither this model nor its architecture is loaded or trained. The ordinary Gemma adapter has no recurrent-state API. |
| Fedus, Zoph, Shazeer, [Switch Transformers](https://jmlr.org/papers/v23/21-0998.html), JMLR 23(120), 2022 | Sparse learned MoE routes examples to different parameter subsets; the paper evaluates large trained models. | Existing logical expert reservations are not learned neural experts and imply no model speedup. |
| Rao and Ballard, [Predictive coding in the visual cortex](https://www.nature.com/articles/nn0199_79), Nature Neuroscience 2, 79–87, 1999, DOI 10.1038/4580 | Their hierarchical visual model passes prediction residuals between modeled levels. | A scalar residual and leaky threshold probe are not a replication, trained predictive-coding network or biological cortex. |

The analytic surprise value is binary-outcome negative log likelihood with
an explicit numerical floor. Supplied probabilities are fixture inputs,
not a demonstrated calibrated model. Leaky accumulation is an elementary
signal filter, with no claim of spiking-neural equivalence or energy benefit.
No performance number from these papers is transferred to Continuum.
