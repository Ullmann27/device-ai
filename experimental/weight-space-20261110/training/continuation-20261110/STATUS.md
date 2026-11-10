# Model weights continuation

Status: WIP / RESEARCH_SHADOW. EXP-05 and EXP-12.
Recovered remote HEAD: `46816f6a232b1b9f5ecf6ce95af8a977af305da8`, PR #728.
The owner's new instruction explicitly continues only model-weight research.

Three helpers independently review training, direct weight evidence and data
contrasts. Coordinator controls sequential native experiments and GitHub writes.
No other development area is part of this continuation.

The original answer arm has 128 completed updates, its raw results and model
already persisted. Its independent quality/weight analysis is being completed.
No rerun or hyperparameter change is authorized by a favorable partial metric.
Replay remains NOT_RUN at this checkpoint. All 15 frozen input hashes match;
its original registered output and evidence paths are unused.

Next: publish the answer weight/quality audit, then execute the unmodified
registered replay arm once. Persist all raw outputs, model bytes and failed
gates before choosing any new experiment. No main merge or model promotion.
Rollback: omit this research directory; original model stays unchanged.
