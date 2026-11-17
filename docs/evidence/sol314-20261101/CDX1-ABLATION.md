# CDX1 controlled HOST ablation — negative result

Source baseline `97b5b900`, guarded implementation `fb45282c`; experiment harness
is committed with this report. JRE21, Kotlin2.0.21, runtime heap cap256 MiB,
sequential execution. No device data, network, owner store or active checkpoint.

Design fixed before execution: context8/32/64 × decoder seeds91/17/203 ×
raw SWB1 / unit-normalized SWB1 / all-zero conditioning; identical authored
48-pair curriculum stage0, exactly2520 updates, learning rate0.035, greedy
generation48 bytes. Encoder seed73 remains frozen. The public25-probe set
(`d1581c1cb67c6957…`) is a **consumed regression set**, not independent TEST.

**All27 variants: 0/20 correct supported answers, 0/5 correct abstentions.**
No variant qualifies; no weights promoted. Larger context changes the output
strings, but does not improve answer correctness under this fixed budget.
Normalizing conditioning changes a few outputs without delivering a correct
answer. Raw-conditioning permutation leaves25/25 outputs unchanged on each
8-byte seed. The JSONL retains every prediction, teacher-forced NLL, first-byte
accuracy, first-step entropy/EOS probability, vector distances, model SHA and
measured runtime. Every evaluation checks unchanged serialized weights.

| Context | Parameters | Serialized bytes | Production format compatible |
|---|---:|---:|---|
|8|7746|62036|yes|
|32|10818|86612|no|
|64|14914|119380|no|

The initial larger-context run failed at the real64-KiB `LearningSnapshot`
bound. The completed run lifts **only the temporary experiment serializer's**
bound, leaving production source, checkpoint formats and atomic bundle limits
unchanged. This exposes a real migration/resource cost of enlarging context.
The incomplete first run is not counted among completed experiments.

Inference: the current first-byte conditioning and short teacher-forced
curriculum fail to establish evidence binding at this budget. The constant
suffix plus largely ignored raw conditioner explains this synthetic failure
path. This does **not** identify every cause in the unavailable2520-update
private device bundle. It does not prove no CDX variant can ever learn.
Greedy-versus-beam, a private pinned-bundle differential, and Gemma comparison
remain unmeasured; no stronger claim is made. Further parameter/context scaling
is rejected pending a mechanism with measured benefit.

For0/20, a95% Wilson interval is[0,0.161]; for0/5,[0,0.434]. Repeated seeds
share the same cases and are not540 independent questions. Paired correctness
has no gains or losses between these variants; no significant gain is claimed.

Reproduce with `CONTINUUM_KOTLIN_COMPILER_CP` (compiler/dependency classpath),
`CONTINUUM_KOTLIN_STDLIB` and
`bash scripts/continuum-cdx1-causal-ablation.sh /new/path/report.jsonl`.
The script refuses an existing output path and leaves no experiment weights.

Relevant primary research, read as design context rather than proof for CDX1:
[Scheduled Sampling](https://research.google/pubs/scheduled-sampling-for-sequence-prediction-with-recurrent-neural-networks/),
[Pointer-Generator Networks](https://aclanthology.org/P17-1099/), and
[LongMemEval](https://arxiv.org/abs/2410.10813). No external code imported.
