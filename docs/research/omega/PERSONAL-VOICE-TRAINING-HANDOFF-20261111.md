# Personal voice: native training handoff

Status: WIP / NOT_RUN. Research base PR #728 at
`1d9eab1275a7fcc92ad4c5e2d1dd52d1db411667`.

Owner scope: bounded personal speech understanding research, exactly two
weight candidates only after data and independent preregistration gates.
EXP-05 / EXP-12. See
`experimental/personal-voice-20261111/training/PROTOCOL.md`.

Verified original model and tokenizer hashes against repository provenance.
Native minGRU has 132,160 parameters, context 256, no separate classifier
head. Existing source-pinned builder and KL implementation are reusable.
Compiler Kotlin 2.4.0 / Java 21 runs locally. Model updates so far: zero.

Next: implement isolated trainer and read-only scorer; compile/inspect;
receive data hashes and research-lead budget; register and push before run.
No independent holdout data or evaluation errors may be inspected by training
agent until both candidates are complete. No productive configuration changes.

Commands and actual receipts will be appended once executed. No Android,
Fold7, APK or general language-quality improvement is claimed.

## Compiled source checkpoint

Implemented `LmPersonalVoicePilot.kt` over the existing native model and a
strict two-arm `run_registered.py`. Exact compiler/JAR/source evidence in
`training/build-evidence`; 15 existing native KL checks passed. Weight updates
still zero. No holdout files opened. Await training data quality and remote
evaluation freeze, then inspect lengths and publish full run registration.

Native evaluator (read-only):

```text
java -Xmx768m -XX:ActiveProcessorCount=1 -cp <recorded-classpath> \
  at.heinz.deviceai.learning.lm.LmPersonalVoicePilot eval \
  <checkpoint> <original-tokenizer> <cases.tsv> <retention_text.txt>
```

Training/inspection uses the same entry with seven arguments:
`inspect|balanced-replay|balanced-preserve baseline tokenizer train.tsv
 supervised_replay.tsv replay.txt outputDirectory`.

Remote first checkpoint `433b1f7696b8fcbdf665075255f6752f1ee3982e`, Draft
PR #754. Normal git push lacked HTTPS credentials; authorized GitHub connector
published the exact local tree, with independent remote commit identity.
