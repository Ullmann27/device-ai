# Personal voice: native training handoff

**Final status: exactly two candidates completed; both REJECTED.**
EXP-05 / EXP-12. No further weight update in this round. No APK/app integration.

## Verified baseline and implemented work

Research base PR #728 at `1d9eab1275a7fcc92ad4c5e2d1dd52d1db411667`.
Original minGRU LM1: 132,160 parameters, vocabulary 512, dimension 64,
two layers, context 256, tied token/output weights, no separate classifier head.
Original model/tokenizer identities verified against repository provenance.
Kotlin 2.4.0 / Java 21 runs locally; existing pinned native builder, continuity
patch and native KL implementation reused. No new inference architecture.

`experimental/personal-voice-20261111/training/` contains:

- `LmPersonalVoicePilot.kt`: two-arm native trainer and read-only evaluator.
- `run_registered.py`: fixed controls, input hashes, fresh-output enforcement,
  process timeout and resource/log/artifact receipts.
- `PROTOCOL.md` and `registration.json`: immutable pre-run controls.
- `build-evidence/`: source/compiler/JAR identities, compilation, 15 passed
  native KL checks, original-byte and token-length inspection (zero updates).
- `runs/{balanced-replay,balanced-preserve}/`: all 128 per-arm schedules,
  native update logs, stderr and complete resource receipts.
- `artifacts/{balanced-replay,balanced-preserve}/final.lm1`: actual model bytes.
- `training-identity.json`: valid checksums, same shape/tokenizer, all 132,160
  numeric weights changed in each arm, identical two-arm draw schedules.
- `RESULT.md`: complete technical findings, resources and quality rejection.

No personal recording/transcript was used. The 128 contrast TRAIN and 32
simple supervised replay examples are newly authored public synthetic data;
16 original public text replay sequences provide a retention objective.
Training never opened DEV, historical retention or independent holdout files.
A four-form replay grammar correction was made and re-inspected before the
final data freeze; main TRAIN and independent tests did not change.

## Prospective checkpoints and output identity

- Compiled trainer/source: `e83536a324f734eacd76e418a899826b06f408bc`.
- Final data freeze: `565c71ed63ca16c3891b4a7989eca82bf419ac6f` (PR #756).
- Independent protocol freeze: `9deac1792e79736167c5169c916941d45c8f9a96` (PR #755).
- Final registration published before first update:
  `e6dc45cd061a17ca5fa7409fa416ff6d2a61b7c2`.
- Complete run/model publication:
  `13312ef603aacda71222151ff64c80bec52e9e70` (PR #754).

Original model SHA-256:
`7a0af1e99554f9e73fbe4212d0aa4bf8ec97745efca56998d9b42bac4fad44fd`.

Final model SHA-256 values:

- balanced-replay: `e1e231cc2b30f2c8a36af0b0b9c7edcfbc13a19ae6e73faee501448d43f511fe`
- balanced-preserve: `4d0cb91226970737bd3413736b66d8688714f2c65ae0e383501aadd6bd5f763f`

Exactly 128 updates each, exit 0, every registered input unchanged. The second
arm's original teacher/cache hashes stayed unchanged. No third candidate or
duplicate training rerun. Bot 3 captured original historical retention before
training and received both immutable candidates before new holdout inference.

Training JVM totals: 64.880326 wall seconds, 69.039759 user CPU seconds,
0.947456 system CPU seconds, largest process RSS 126,928 KiB (123.953125 MiB).
These exclude compilation/evaluation/assistant compute. No training GPU job
or external teacher API call; no phone energy/temperature/latency measurement.

## Independent quality disposition

Both candidates rejected: exact 17/64 versus original 0 reflects learning the
label/EOS format; restricted accuracy is only 17/64 versus 16/64, complete
opposite pairs 1/32, and identical-word-bag pairs 0/17. Historical retention
32/64 fell to 18/64 or 25/64; text outputs shortened. Each arm introduced 16
valid forbidden-action proxy errors in offline tests. No device action ran.

The independent metric definitions, raw model responses and verdict belong
to PR #755 and the `evaluation/` subtree in the research integration branch.
No semantic-learning success, acoustic ASR gain or live Continuum improvement
is established by this training round. Do not tune on this now-observed test.

## Reproduction, integration and rollback

Rebuild with the existing #728 builder and the exact extra sources/inputs in
`build-evidence/build.json`. Use original checkpoint/tokenizer paths documented
in `PROTOCOL.md`; restore public data from its frozen commit. Archive a new
invocation if execution paths must differ, keeping all content hashes and
controls fixed. Cross-environment byte-identical reproduction is NOT_RUN.

Read-only evaluation (zero updates):

```text
java -Xmx768m -XX:ActiveProcessorCount=1 -cp <recorded-classpath> \
  at.heinz.deviceai.learning.lm.LmPersonalVoicePilot eval \
  <checkpoint> <original-tokenizer> <cases.tsv> <retention_text.txt>
```

Native training/inspection takes seven arguments:
`inspect|balanced-replay|balanced-preserve baseline tokenizer train.tsv
 supervised_replay.tsv replay.txt outputDirectory`.

Keep research sources, data/identity evidence and drafts open for review.
Exclude both weight artifacts from the APK. Preserve original/Gemma weights,
app selectors, main, signing and release settings. The pre-run protocol and
registration remain unchanged because their hashes are registered inputs;
this handoff and RESULT hold the final status. No further experiment starts
without a separate bounded research decision after this rejected result.

Normal HTTPS git push lacked credentials. The authorized GitHub connector
published exact trees; candidate blob IDs and resulting tree IDs were compared
with local Git values, and the actual remote PR HEAD was verified.
