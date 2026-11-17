# Actual isolated-executor capability and final security handoff

Ready external config: `/workspace/scratch/isolation-config.json`.

```json
{
  "image": "sha256:f9cb3de7fe3810796e00cec72f29142e2d3a4beada075443c5614024efdca182",
  "nodeHome": "/workspace/scratch/isolation-node",
  "jdkHome": "/usr/lib/jvm/java-21-openjdk-amd64",
  "timeoutMs": 30000,
  "memoryMb": 512,
  "pids": 32,
  "cpus": 1
}
```

The image was built from the repository's `scripts/continuum-isolation.Dockerfile` copied into `/workspace/scratch/isolation-image`; it is FROM scratch and required no download/networked build step. Docker daemon uses the local Unix socket and server 28.4.0. Cloud-runtime Docker/network references were read first; injected Docker configuration and credentials were preserved, never printed. Every candidate/test run below used `createIsolatedExecutor` and the actual container, not free host execution.

`nodeHome` is a dedicated public tool directory containing only `bin/node`, Node24.19.0. Node binary SHA256: `bc17c508ffeed0ec622934f9b7fa72f8e78da65350e63c3eceb56fa688aa5e12`. Java21 runtime root is the concrete tool-only `/usr/lib/jvm/java-21-openjdk-amd64`; `bin/java` SHA256 `6698f6f10143ed8463b06062281c727152d2e0ae2a3569b1716fbb2c6cedf0ba`. This is not an Android JDK17/SDK toolchain claim. Kotlin was not configured or tested by this subagent; increase memory/PID/time limits deliberately before compiler work.

Actual proofs:

- `/workspace/scratch/isolation-proof.json` from existing `scripts/continuum-isolation-proof.mjs`: 4 genuine container tests passed. Host owner read/write blocked and owner data unchanged; readonly source; synthetic host env canary absent; Docker socket inaccessible; unprivileged UID1000, no-new-privileges and zero capabilities; expected kernel memory/PID limits; unshare denied; host loopback/PID namespace inaccessible; excess child spawning actually denied. Runaway container timed out and was removed in1354ms. Memory max was observed; no OOM stress or CPU throttling benchmark was performed.
- `/workspace/scratch/isolation-node-proof.json` and matching `.mjs`: 3 JUnit tests passed directly through `executor.execute('node-continuum', ...)`. Ordinary nonnumeric string normalization worked. Host owner read/write and external host-marker creation denied, Docker socket inaccessible, source writes denied, synthetic inherited env absent, and remote socket attempt returned a no-route/access error. Host marker absent, owner and fixture source unchanged. Fixture `/workspace/scratch/isolation-node-fixture/source` retained.
- `/workspace/scratch/isolation-runprofile-proof.json` and matching `.mjs`: actual current integrated `runProfile` used the branded executor, retained isolation metadata, parsed all3 genuine JUnit tests and passed the same denial probes. This was the in-progress integration immediately before the lead's freeze; it was not presented as a test of the older `aa8780a` base.
- `/workspace/scratch/isolation-owner-multifile-proof.json` and matching `.mjs`: controlled infrastructure-only owner REPAIR using two string modules, `lib/tags.mjs` and `lib/summary.mjs`. Baseline3tests/2failures; fixture provider called once; candidate3passed; independent3passed; statusDONE/approvedForMergefalse; original source unchanged; ledger0experiences/PENDING_OWNER_REVIEW. Default unsupported abort mode rejected before claim; explicit CHECKPOINT_BOUNDARY_ONLY accepted. This provider returned a fixed patch and is not real model inference. Fixture/report/patch retained under `/workspace/scratch/isolation-owner-multifile-fixture`.

After initial direct proofs, Docker showed no remaining `continuum-*` containers. Each executor generation independently performs inspected kill/remove teardown and rejects uncertainty. No repository files were edited by this subagent; all fixtures/config/proofs are external.

Final focused review: initial source audit identified disconnected/ignored executor and missing real model invocation in owner/CCC1. The lead integrated these changes and froze source at `2c985fba1556a7b7133defa1a4601e4e4df53fdb`. No new critical false-success issue was found in the final focused review. The lead confirmed independent isolation metadata preservation was fixed after the initial two-file proof had exposed its omission. The lead's separate real-model and frozen-source runs are separate evidence, not these infrastructure fixtures.

Remaining material limits:

- The candidate can control runtime output inside its container. Two runs of the same code and candidate-supplied JUnit do not independently certify semantics. Keep `untrustedReports:true`, review-only status and broad-candidate no-learning/no-replay boundaries; immutable independently authored tests and holdouts remain necessary.
- Docker in this host is optional capacity. No Android/Fold7 or on-device Docker availability is established; report an unavailable mobile execution capability honestly.
- Isolated abort currently requires explicit checkpoint-boundary mode. An active step is bounded by timeout; it does not claim the host watchdog's prompt in-step cancellation.
- Toolchain binaries/config are trusted owner/admin inputs; image identity alone does not pin mounted toolchains. Record their hashes and keep them outside model scope. Source snapshot includes tracked files only and rejects symlinks; new untracked source/contract files need explicit trusted admission.
- OS confinement protects host state, not correctness or general coding competence. No APK, installation, production release or neural promotion was performed here.
