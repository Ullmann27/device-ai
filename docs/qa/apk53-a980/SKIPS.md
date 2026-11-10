# Node skips — frozen a980a19

31 skipped cases from `apk53-a980-host/full-node/full-node.xml`.
These are not passes. Names/reasons below are technical test metadata, not
fixture input/output. Repeated reasons are listed once.

## Reasons

| ID | Count | Recorded reason / source clarification |
|---|---:|---|
| S01 | 6 | true — skip: process.env.CONTINUUM_WORKSPACE_HOST_PROOF !== '1'; verified at the frozen source. XML records only true. |
| S02 | 1 | NOT_RUN: configure official Kotlin and Shizuku/annotation jars |
| S03 | 1 | NOT_RUN: set CONTINUUM_FABRIC_JVM_DEPS to run the real Kotlin graph |
| S04 | 1 | NOT_RUN: configure official Kotlin/JUnit jars |
| S05 | 1 | Run scripts/continuum-fabric-jvm-check.sh for the actual JVM→Node test |
| S06 | 1 | NOT_RUN: configure actual reviewed Harness SDK |
| S07 | 1 | NOT_RUN: actual SDK required |
| S08 | 1 | Requires explicitly configured Docker; synthetic Gradle reports, not an Android build |
| S09 | 1 | HOST_DOCKER_PROOF_NOT_RUN: fixed Docker client unavailable |
| S10 | 3 | HOST_RESOURCE_PROOF_NOT_RUN: kernel prerequisite inaccessible: ENOENT |
| S11 | 13 | HOST_ISOLATION_PROOF_NOT_RUN: /usr/bin/bwrap fails root-owned regular non-writable tool policy (uid=65534, mode=755); /usr/bin/prlimit fails root-owned regular non-writable tool policy (uid=65534, mode=755); bwrap: loopback: Failed to create NETLINK_ROUTE socket: Operation not permitted |
| S12 | 1 | HOST_ISOLATION_PROOF_NOT_RUN: bwrap: loopback: Failed to create NETLINK_ROUTE socket: Operation not permitted |

## All skipped cases

| # | File | Test name | Reason |
|---:|---|---|---|
| 1 | `.github/continuum/ccc1-bot-workspace-host.test.mjs` | HOST SOFTWARE CONTRACT: real persisted Forge/Lint profiles receive distinct observed containers for builder and both verifier sites | S01 |
| 2 | `.github/continuum/ccc1-workspace-host.test.mjs` | HOST_INTEGRATION: two canonical worker jobs run in separate confined Docker workspaces | S01 |
| 3 | `.github/continuum/ccc1-workspace-lifecycle-host.test.mjs` | HOST_INTEGRATION: actual canonical worker claim admits RED, repaired GREEN and independent Docker runs | S01 |
| 4 | `.github/continuum/ccc1-workspace-lifecycle-host.test.mjs` | HOST_INTEGRATION: live canonical lease clamps the real Docker timeout | S01 |
| 5 | `.github/continuum/ccc1-workspace-lifecycle-host.test.mjs` | HOST_INTEGRATION: in-flight owner revocation suppresses the completed profile result at checkpoint | S01 |
| 6 | `.github/continuum/cognitive-action-integration.test.mjs` | actual Android shell adapters compile against official APIs and the unchanged Binder contract | S02 |
| 7 | `.github/continuum/cognitive-action-integration.test.mjs` | native natural request → actual tool → receipt → persisted episode, with negative regressions | S03 |
| 8 | `.github/continuum/cognitive-action-integration.test.mjs` | actual memory, CRE1 and CTX3 graph preserves provenance and emits content-free traces | S04 |
| 9 | `.github/continuum/compute-fabric-model-adapter.test.mjs` | native JVM export feeds Node byte-for-byte when cross-language runner is configured | S05 |
| 10 | `.github/continuum/harness-coding-provider.test.mjs` | real Harness service preserves the existing strict wire schema without mounting tools | S06 |
| 11 | `.github/continuum/harness-coding-provider.test.mjs` | authority smuggling, no-op and HTTP failure remain rejected without hidden retries | S07 |
| 12 | `.github/continuum/isolated-report-cap.test.mjs` | fixed isolated Android runner rejects 401 reports instead of returning a green subset | S08 |
| 13 | `.github/continuum/momega1-typed-workspace-host.test.mjs` | HOST_INTEGRATION: typed dispatch binds actual canonical repair containers and refuses replay | S01 |
| 14 | `scripts/continuum-local-model-7b.test.mjs` | fixed Docker CLI transport rejects mismatched weights before native launch, with no observation | S09 |
| 15 | `scripts/continuum-local-model-cpu.test.mjs` | actual launcher gives exact private 7B profile two CPU seconds per remaining wall second | S10 |
| 16 | `scripts/continuum-local-model-cpu.test.mjs` | two-thread CPU allowance leaves the existing wall cancellation deadline unchanged | S10 |
| 17 | `scripts/continuum-local-model-cpu.test.mjs` | actual isolated synthetic ELF observes the new kernel CPU limit and unchanged native AS limit | S10 |
| 18 | `scripts/continuum-local-model-observation.test.mjs` | actual namespace binds admitted descriptors and fixture output; never inference evidence | S11 |
| 19 | `scripts/continuum-local-model-observation.test.mjs` | deadline cancels actual isolated process with bounded return | S11 |
| 20 | `scripts/continuum-local-model-observation.test.mjs` | STOP cancels actual isolated process and leaves no trusted observation | S11 |
| 21 | `scripts/continuum-local-model-observation.test.mjs` | malformed/oversized output and missing generation counters are rejected | S11 |
| 22 | `scripts/continuum-local-model-observation.test.mjs` | actual FD mount retains selected inode across model path replacement | S12 |
| 23 | `scripts/continuum-local-model-observation.test.mjs` | prompt evaluation counter alone cannot masquerade as output generation | S11 |
| 24 | `scripts/continuum-local-model-observation.test.mjs` | inference sandbox denies host files, external interfaces, secrets and writable model | S11 |
| 25 | `scripts/continuum-local-model-observation.test.mjs` | seccomp denies fork and rlimits bound native memory/descriptor budgets | S11 |
| 26 | `scripts/continuum-local-model-observation.test.mjs` | changing model during actual execution invalidates otherwise valid output | S11 |
| 27 | `scripts/continuum-local-model-observation.test.mjs` | production provider rejects synthetic fixture receipt even when transport MAC is valid | S11 |
| 28 | `scripts/continuum-local-model-observation.test.mjs` | native worker threads remain permitted while process creation stays denied | S11 |
| 29 | `scripts/continuum-local-model-observation.test.mjs` | process evidence classification cannot claim LOCAL_MODEL_HOST from a configured kind | S11 |
| 30 | `scripts/continuum-local-model-observation.test.mjs` | invalid model JSON retains authenticated process observation without a candidate | S11 |
| 31 | `scripts/continuum-local-model-observation.test.mjs` | provider retains authenticated rejected-response observation and consumed durable budget | S11 |
