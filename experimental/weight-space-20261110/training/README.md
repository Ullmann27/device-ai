# Native LM1 experiment build

Source-only support for the new weight study; production files are unchanged.
Read `PLAN.md` before running the original-weight continuity experiment.

## Compile

The builder verifies four original Kotlin files and three #530 support files,
then copies and patches them only inside the new output directory. It invokes
an already available Kotlin compiler; it downloads no dependencies.

```sh
python3 experimental/weight-space-20261110/training/build_experiment.py \
  --repo "$PWD" \
  --compiler /workspace/scratch/5900afa6af29/qa-installed-tools/kotlinc/bin/kotlinc \
  --output /workspace/scratch/87c6edbcb340/weights-build-resume-v2 \
  --extra "$PWD/experimental/weight-space-20261110/training/LmOriginalResumeExperiment.kt"
```

`--extra` may repeat. The evaluator or another research harness can use the
same builder with its own fresh output directory and Kotlin source.
The original four files remain in `app/src/main/java/.../learning/lm`;
the isolated patched files are in the build's `source/` subtree.

The resulting `build.json` records compiler/JAR/stdlib/source identities,
the exact command, classpath and combined `sourceBindingSha256`. This new
Kotlin 2.4.0 build is not the historical Kotlin 2.0.20 runtime.
Compilation performs zero model updates.

## Inspect or run

The JVM entry is `at.heinz.deviceai.learning.lm.LmOriginalResumeExperiment`.
Its seven arguments are:

1. `inspect` or `run`.
2. Original checkpoint path.
3. Its independently expected SHA-256.
4. Original tokenizer path.
5. Expected parameter count: `132160` for the registered available model.
6. `sourceBindingSha256` from the frozen build manifest.
7. Fresh output directory.

`inspect` validates and loads the input without inference or updates.
`run` is permitted only after the remote source/protocol freeze; use
`java -Xmx768m -cp <recorded-classpath> <entry> <seven-arguments>` with a
120-second process deadline. Four updates total, two per compared arm.
Keep stdout/stderr and a process receipt. Any failure rejects this experiment;
do not weaken criteria or silently retry under changed settings.

Both original files are available in this session under
`/workspace/scratch/87c6edbcb340/weights-inputs/` as `baseline.lm1` and
`tokenizer.lmbp`. Their repository origin and hashes are in `PLAN.md`.
These paths are temporary execution locations, not promises of future access.

The new LM1C and LM1T files are technical research outputs. They must not
replace the application's active model or be described as improved language
weights. Independent evaluation must compare actual exported parameters,
moments and state identities.
