# Native model prerequisite interruption — latest status

2026-11-03. This checkpoint supersedes HANDOFF.md's earlier waiting-for-CPU status.
Last fully observed prerequisite commit: ddb683c2249b9046a2b9d0b6e4ee650a8f172329.
The GitHub connector remains available; this file was committed directly to this
isolated documentation branch after the execution environment became unavailable.

## Last actual observation

Chief released the CPU gate. The exact staged CMake configuration completed with
exit0 in0.924 seconds. The official llama-completion build started with --parallel2.
The executor then disconnected and its recovery timed out after45 seconds. A fresh
exec attempt reported no usable execution environment. The environment provider
reported running/connected, which does not override the failed actual exec channel.

Build process/session61866 completion: UNKNOWN. Runtime binary existence/size/hash:
UNKNOWN. runtime-env.json was NOT CREATED by this lane. No inference was started,
no frozen task content read and no benchmark answer or patch supplied. The expected
runtime digest is not an observed build result.

## Already verified before the interruption

The approved model file was fully downloaded and hashed:1117320768 bytes,
SHA256 cc324af070c2ecbfd324a30884d2f951a7ff756aba85cb811a6ec436933bb046,
GGUFv3. Official model revision/license, clean runtime source/tree, exact CMakewheel,
and nine compiler/CMake/support-library hashes are recorded in the adjacent JSON.
Those records are remote and durable; availability of the outside-Git artifacts
AFTER environment recovery must be checked again, not assumed.

## Recovery order

1. Require working actual exec. Do not restart or reconfigure the environment on
   the basis of this document. Inspect before starting a replacement build.
2. Check /workspace/synapse-local-model/metadata/build-replication.json, build.exit,
   build.txt, configure.exit, configure.txt and runtime-verified.json if present.
   Old session61866 may no longer be addressable. Check current processes first;
   never launch a competing duplicate build.
3. Recheck the model file and pinned official source, tool hashes and permissions.
   If files are missing, repeat only the already approved exact downloads using
   the saved revision/size/digest. Never substitute another model or runtime.
4. If the previous build did not finish, use replication-recipe.json after Chief
   releases the resource gate again. Configuration and build commands are vectors,
   CPU/static profile, maximum2 compiler jobs. No parallel whole-app build.
5. The resulting build-cpu/bin/llama-completion must be12476968 bytes with SHA256
   c2c13a2a27c5a85137b6d083074fa83e0d990db129e715f8452352e3730cbf5a and clean tracked
   source6cb3e672e8d10fd4feab1a048bf2967c40623133. Investigate any mismatch; do not
   modify the reviewed pair registry to make a different binary pass.
6. Only then create /workspace/synapse-local-model/runtime-env.json with public
   model/runtime pins and actual local paths, plus the existing explicit alternate
   coding model consent. Use the canonical six-field GGUF manifest schema. Do not
   put observation/HMAC secrets, job grants or benchmark answers in this file.
7. Hand the verified paths to independent canonical-task QA. No direct proposal
   smoke is a replacement for the full authorized CCC1 RED/GREEN/verifier cycle.

No APK signing, main merge, owner-data change, model weights in Git, installation,
paid service or Fold7 success is part of this lane. Weekly quota remains NOT_VISIBLE.
