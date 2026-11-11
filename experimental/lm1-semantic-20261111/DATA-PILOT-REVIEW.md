# Coordinator review of the open data pilot

Status: SOURCE/SEMANTIC REVIEW COMPLETE; native parity and published data identities still required before training.

The coordinator read the complete new generator, shortcut audit, and 64 representative family/label/position constructions (all eight TRAIN/DEV families, four labels and both chosen positions). Labels follow the explicit live instruction, not the quoted/example/withdrawn alternative. Double negation and conditional selection have internally consistent labels. The generator instantiates these constructions into 192 TRAIN and 96 DEV rows. No holdout content or label was reviewed by the coordinator at this stage.

The pilot balances all four labels and covers every distinct label pair. Each opposite pair swaps the selected and distractor action; word counts, UTF-8 length and native-BPE length are intended to remain equal. This prevents bag-of-words and length-only perfect discrimination within a pair, but does not prevent order-sensitive shortcuts. The generator and audit correctly describe the feature-collision oracle as an upper bound on this finite dataset, not a trained test model.

Classifiers fit TRAIN only with fixed settings. The best reported open DEV control is word-bigram logistic regression, 49/96 rows (51.04%). First/last action rules each solve 24/48 entire pairs (50%); the neural comparison must beat the best pair control as well as the best row control. Passing the less-than-90% pilot gate alone is no evidence of model learning.

Limits: only four training families, four development families, four training objects and two development objects. These are authored synthetic cases; repeated templates create substantial correlation. A train/DEV split by template does not create population-level independence. Evaluator holdout families and objects remain separately controlled.

Historical model architecture was read directly: MINGRU, two layers, hidden dimension 64, vocabulary512, context256, with tied input/output token weights. This architecture can depend on order; no proven order-invariance defect justifies replacing it. Whether the small existing representation learns this task within the stated budget remains empirical.

Loss and data changes in both new candidates form a combined intervention relative to old trials: four supervised classes, lexical contrasts and lower EOS weight. Therefore any old/new difference cannot identify a single causal improvement. A versus B isolates the additionally proposed ability-preservation objective, with extra compute in B explicitly reported.
