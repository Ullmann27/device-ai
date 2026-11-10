# Fixed open-development contrast pilot

Status: WIP / NOT_RUN. EXP-05. Authored after the historical length audit,
before any model evaluation on the new dataset. No blind-test claim.

Use the authentic original 132,160-parameter LM1 checkpoint, its unchanged
512-token tokenizer, and the isolated #530 native optimizer correction.
All application sources remain unchanged. Inspect mode only checks inputs,
encoding and target masks; it performs zero updates and no model inference.

Two fixed arms each start from the same original weights and fresh moments:

1. `answer`: 128 AdamW updates, four answer examples per update, two per class.
2. `replay`: the identical answer schedule plus two text-replay sequences.
   Objective is mean answer/EOS loss plus 0.1 times mean replay-token loss.

Common schedule: seed 20261110, learning rate 0.0001, four warmup steps,
cosine decay to ten percent, decay 0.01, clip 1, one CPU thread. This is a
rehearsal tradeoff pilot with additional compute in the second arm, not a
compute-matched proof. Answer positions and EOS are the only supervised
positions of intent examples. Replay uses full next-token loss including EOS.

Measure original and final weights once per arm on openly authored DEV:
unrestricted greedy label-plus-EOS exact match, four-choice classification
as secondary diagnostic, answer NLL, premature EOS, separate untrained
open/search retention probes, text bits-per-byte and four 32-token generation
probes. The latter only measure early stopping, not coherent language quality.
Both arms and every failure are retained. No winner is selected or promoted.

Predeclared favorable DEV signal: exact-match gain of at least 8 cases,
no decline in open/search exact matches, text BPB increase at most 5 percent,
and no decrease in the number of generation probes reaching 32 tokens.
The baseline must itself pass all four 32-token probes before any capacity
gate can be called satisfied. These are exploratory DEV criteria only.

Compile source, input hashes, JAR identity and exact command are published
before either training arm. Maximum 600 seconds inside each native invocation,
external deadline 660 seconds, Java heap 768 MiB, one active native experiment.
Each completed arm is separately committed and published with its model bytes,
logs and an independent weight-delta audit before starting the next arm.

No consumed final-test cases, user-private facts, paid compute, persistent
background loop or productive model change. Rollback: retain the original
checkpoint and omit this research directory.
