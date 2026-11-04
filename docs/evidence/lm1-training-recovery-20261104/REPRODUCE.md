# Reproduction and rollback

All runs are isolated CPU research. Keep the active application and model
selection unchanged. Do not resume a checkpoint that failed a mandatory gate.

## Exact sources

| Component | Full commit |
|---|---|
| Unchanged LM1 core and growth/capacity kernels | `30cd3a89810ed14fa84eca95236d3177e016118d` |
| Diagnosis and intent v1 execution | `def5acf9cbba82ee51a53cc06fecc52e9cd0324d` |
| Longer intent v2 execution | `fc2eb797210bee25091caf734aacbfd694a3372e` |
| Short-suffix v3 and final development-choice rule | `be4e49600fed1db4c697230bb8e42c150dbfbdaf` |
| Original checkpoint/tokenizer git objects | `d2304a7658e1256b160c8012e1bac28268ff64a5` |
| Original 77 TRAIN document git objects | `1d92db03b87558c0b330dc584fd1f4c025a93c82` |

The later research branch contains the earlier registered files byte-for-byte.
Each registration binds every executable source, all task TSVs, input artifacts,
compiled JAR and Kotlin standard library. The registration is an immutable
snapshot made before its training began; `studyTrainingStarted=false` records
that registration-time fact. Actual execution is documented separately by the
dated process receipts and per-step logs.

## Runtime and compilation

Observed runtime: x86-64 Linux, Java 21.0.13, Kotlin compiler/stdlib 2.0.20,
JVM target 17, nine visible logical CPUs. Each optimizer uses two gradient workers.
The host specifications are in `preflight/environment.json`; no Android device
measurement is claimed.

Exact compilation commands and source hashes are preserved in:

- `preflight/manifest.json` for v1.
- `preflight-long/manifest.json` for v2.
- `preflight-suffix/manifest.json` for v3.

The compiler entry point is `org.jetbrains.kotlin.cli.jvm.K2JVMCompiler` with
`-no-stdlib -no-reflect -jvm-target 17`; the supplied standard-library JAR is
passed explicitly. The archived runtime JARs can be checked directly against the
registrations. If any rebuilt byte differs, stop and investigate the toolchain;
do not edit the historical registration to make an unregistered executable pass.

## Recreate the exact inputs

The original corpus was not archived at the initially queried remote path.
`experimental/lm1-training-recovery-20261104/prepare_inputs.py` reconstructs it
from the 77 original TRAIN git objects in `preflight/input-provenance.json`.
It verifies each document hash, extracts the pinned checkpoint and tokenizer,
and invokes the unchanged corpus builder. It never trains. Its successful
reconstruction receipt and corpus log are included in `preflight/`.

From this repository, with the research runtime archive extracted into a
directory named `LM1_Training_Research_20261104`, use a fresh output directory:

```sh
STUDY_REPO="$PWD"
STUDY_RUN_ROOT="$(mktemp -d)"
STUDY_RUNTIME="$STUDY_REPO/LM1_Training_Research_20261104/runtime"
STUDY_STDLIB="$STUDY_RUNTIME/kotlin-stdlib-2.0.20.jar"
STUDY_INPUTS="$STUDY_RUN_ROOT/inputs"

python experimental/lm1-training-recovery-20261104/prepare_inputs.py \
  --repo "$STUDY_REPO" \
  --classpath "$STUDY_RUNTIME/v1-experiment.jar:$STUDY_STDLIB" \
  --output "$STUDY_INPUTS"
```

The git repository must contain the pinned objects listed above. The helper
does not check out, reset or alter the repository worktree. A missing object or
hash mismatch stops preparation. Each retry must use a new output directory.

## Execute a registered research round

The controllers verify registration, source, JAR, standard library and input
bytes before every process and after it. Training output directories must not
exist. Logs cannot be replayed implicitly. Each model is trained only after a
fresh successful initial capacity process and identity/parity checks.

For v1, use the top-level controller and `v1-experiment.jar`, with execution
source `def5acf9cbba82ee51a53cc06fecc52e9cd0324d`. Its phases are `diagnostics`,
`intent`, `freeze`, `test`. For v2, use `long-study/run_registered_study.py`,
`v2-experiment.jar`, and `fc2eb797210bee25091caf734aacbfd694a3372e`.
For v3, the exact intent command is:

```sh
python experimental/lm1-training-recovery-20261104/suffix-study/run_registered_study.py intent \
  --jar "$STUDY_RUNTIME/v3-experiment.jar" \
  --stdlib "$STUDY_STDLIB" \
  --inputs "$STUDY_INPUTS" \
  --output "$STUDY_RUN_ROOT/v3" \
  --source-commit be4e49600fed1db4c697230bb8e42c150dbfbdaf
```

Never call `test` before a valid selection freeze. In this research
sequence, `suffix-study/choose_development_study.py` first compares the complete
v2/v3 development evidence. It validates the actual post-capacity receipts,
the full 512-step histories, example-schedule equality and the expected token-
schedule change. The chosen controller's `freeze` phase must produce exactly
the payload selected by that choice receipt. Publish both receipts before
the single final evaluation. The archive and final report identify the chosen
version and the publication commit.

The 48 final cases are reserved for the single confirmation evaluation recorded
in the final report. Once evaluated, replaying them can verify that published
result; it is not a new independent confirmation of a later-trained model.
Future development then needs a fresh, independently specified confirmation set.

## Evidence interpretation

`*.receipt.json` records command, source commit, registration/JAR/log hashes,
exit status, elapsed time, deadline and whether registered bytes stayed unchanged.
Capacity-process exit 1 is an explicit failed benchmark and is retained. Training
process failure or timeout never creates a successful candidate.

The capacity benchmark requires its original full 32-token workload. One-digit
intent success does not satisfy it. All individual generated answers, stopping
reasons, class counts and teacher-forced diagnostic losses are retained. A low
online minibatch loss does not replace fixed development or final-test results.
The extra post-selection training-fit audit is explicitly a TRAIN-file
evaluation, even though the shared JVM scorer's record names use `TEST_INPUT`
and `split=test`; its input path and SHA distinguish it from the final test.

Checkpoint bytes are content-addressed independently of filenames. Absolute
paths and elapsed times differ in a new workspace, so new process receipts will
have new hashes. These differences do not justify changing source/data hashes
or silently reusing old receipts for a new run.

## Rollback

Do not adopt the research checkpoints. Retain the original model with SHA-256
`7a0af1e99554f9e73fbe4212d0aa4bf8ec97745efca56998d9b42bac4fad44fd`
and its existing selection. No application/runtime/selection file was changed
by this research branch, so there is no deployed behavior to revert. Keep the
raw evidence when rejecting a model; it explains why a candidate was excluded.
