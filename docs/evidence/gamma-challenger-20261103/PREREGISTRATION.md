# GAMMA independent challenger B — frozen plan

Baseline: `64ec06a73f50cae9ee49035c840710d87777d50f`.
Status: WIP / UNVERIFIED. Candidate implementation, author tests, scores and PR body have not been read.

This bounded assessment reads the baseline API and two storage source files only before freezing executable fixtures. All data are generated synthetic records; OWNER_CONFIRMED is a fixture trust enum, not owner data. No production edits, weights, release, installation, main merge or new infrastructure.

Independent challenges, with exact fixture generation and iteration counts to be frozen in `GammaChallenger.kt` before candidate access:

1. Selection parity with ties in both timestamps, all types/statuses, increasing/decreasing limits, replay type rotation and per-record equality against baseline semantics.
2. Caller ownership: mutate a returned list if it is mutable, then replay again; retained records may not be poisoned by a caller's list edit.
3. Same-instance and independent-instance revocation/reconfirmation, plus a genuine child JVM writer coordinated by files. A completed revocation must not reappear in later replay.
4. A separate process holds the OS lease while replay and mutation wait; release must produce one legal ordering, followed by revocation visibility. This uses real files and OS locks, not mock callbacks.
5. Warm-cache faults in a segment outside the selected tail: same-size, preserved-mtime corruption, deletion, symlink replacement, and corrupt manifest. Replay must fail closed. Restore original bytes and verify recovery without resurrection.
6. Delete the entire store after warming, re-create it with disjoint IDs, and compare with the empty/new generation. Missing primary while segments remain must fail.
7. Existing cap constants remain 4096 records and 32 MiB. Inspect candidate instance retained object graph after replay; no more than 128 unique LearningExperience objects may remain reachable through instance fields. This is a retained-record check, not a heap-peak proof.

Performance comparison: same frozen JVM harness and fixtures against exact baseline/candidate source. Report elapsed time and per-thread allocated bytes for cold construction + replay, stable same-type warm replay, alternating type/limit replay, mutation + replay, and the actual baseline caller sequence shape (source replay48 then intent replay96). Report raw observations and medians; never extrapolate replay-only timings to training/model/device benefit. Fixture generation, disk population and separate process startup are outside replay-only timings. No success threshold is invented after seeing candidate scores; correctness is mandatory, speed is descriptive. Synthetic stable-cache benefit and mutation/type-switch costs must be stated separately.

Initial scope: at most 20 minutes of bounded first assessment. Heavy Gradle and Android/device tests are NOT_RUN. Official `/workspace/continuum-build-env/env.sh` Kotlin/JDK environment only. Freeze source hashes in git before requesting candidate SHA.
