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
