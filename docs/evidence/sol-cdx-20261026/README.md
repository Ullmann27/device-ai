# CDX repair evidence — 2026-10-26

Research-only EXP-05. Source freeze `1f18e6bc008508225216f58fbaaa7c0f5d192f40`,
protocol freeze `f2406ff6`. No app/owner checkpoint, main, memory or deployed
resource changed. All12 comparison snapshots and raw outputs are preserved.

## Cause and matched-budget comparison

The original6000-update synthetic checkpoint is reproduced **bit for bit**:
binarySHA`9662f026df9d65d05d91f3d49539111c469149f8c8e007af6a4a68e0065e8145`.
Teacher-forced samples and generation use identical forward probabilities.
Central differences on30 nonzero derivatives across all5 weight arrays agree
with SGD to maximum absolute error`5.845318673536326e-11`. This rules out those
sampled gradient errors; it is not a proof of every possible derivative.

The eight visible context bytes at the first answer are always`ntwort: `.
The sole differing input is the frozen averaged16D conditioner (norm0.074–0.189).
It changes any initial token probability by at most0.00030 versus zero;
correct, zero, swapped and ×32 inference-only conditioners all generate
`Nora ist Ärztin.` for all4 examples. Training on previous answer bytes thus
learns cheap teacher-forcing continuations while ignoring evidence distinctions.

| Arm / updates | TRAIN exact+EOS | Known-fact new wording DEV | Changed facts | New entities/facts | Unsupported |
|---|---:|---:|---:|---:|---:|
| Raw /6000 |1/4|2/8|0/4|0/4|0/4|
| Raw /30000 |1/4|2/8|0/4|0/4|0/4|
| Raw /120000 |1/4|2/8|0/4|0/4|0/4|
| Unit-L2 /120000 |2/4|2/8|0/4|0/4|0/4|
| Train-mean centered×32 /6000 |3/4|2/8|0/4|0/4|0/4|
| Train-mean centered×32 /30000 |4/4|4/8|0/4|0/4|0/4|
| Train-mean centered×32 /120000 |4/4|4/8|0/4|0/4|0/4|

The frozen DEV rule selects centered×32 at30000 updates. This is a narrow
known-fact wording gain and training-collapse repair; **it still fails all novel
facts and abstentions**. Its binarySHA and every arm/stage are in the summary.
No independently measured generalization improvement is claimed at this point.

The real generation regression failed3/4 before, passed4/4 after, and confirms
that neither generation changed its model bytes. Falling loss is not the gate.
The old16 TEST fixture was not used in this comparison and is already consumed.

## Reproduction

Compile `experimental/brain/Cdx1CausalDiagnosis.kt` and
`experimental/brain/Cdx1ConditionerRepairProbe.kt` with installed Kotlin2.4.0,
`-module-name DeviceAI_app_debug -jvm-target 17 -include-runtime` and the real
SDK classes plus org.json20240303 on the classpath. Run with JDK17/768MiB,
`-XX:ActiveProcessorCount=1` and external90s timeout. No Gradle job was needed.

The diagnosis CLI arguments are SWB1base64, archived6000CDX1base64, NEW report,
exact sourceSHA. Comparison CLI arguments are SWB1base64,
`config/research/cdx-conditioner-repair-v1.json`, NEW output directory, exactSHA.
The exact RED/GREEN CLI invocations/exitcodes are in`quality-regression.json`.
The classes used are the already-built actual SDK classes from the official
checkout, not substitute Android stubs. Their relevant decoder/encoder sources
are unchanged in this branch.

The selected conditioner is caller-side research code only. OldCDX1 format and
weights remain unchanged. No repair has been connected to productive chat.
