# Rescue BOM / output bound follow-up

WIP / UNVERIFIED. EXP-12, EXP-00 / EXP-09. Baseline PR218 final checkpoint:
0b2a9dddfe172eac82923092fcdaa974afc6e25a.
Isolated branch: codex/generation16-rescue-bom-bound-20261029.
Only rescue shell helper/test are owned; no app/Kotlin, authorization, model or release changes.

Two source issues addressed (not runtime verified):
1. TextDecoder default removes a leading UTF8 BOM from each field. Kotlin's UTF8 String decoder
preserves U+FEFF, and the record hash includes it. A correctly hashed original ledger can be refused.
Source fix: ignoreBOM:true preserves BOM as field data, retaining fatal malformed-UTF8 rejection.
2. Node spawnSync maxBuffer is checked after appending a whole read chunk; returned partial stdout
can exceed the option. Current capture save writes the entire returned buffer.
Node20.19 src/spawn_sync.cc OnRead appends before IncrementBufferSizeAndCheckOverflow.
Source fix: explicitly cap retained partial stdout at512KiB+1 without accepting any overflow.
Each captureDetails entry records returnedBytes, retainedBytes and truncated. Existing
checked(result) remains authoritative: ENOBUFS/read failures still yield CAPTURE_1_FAILED,
not a truncation success. Both source originals and the bounded local prefix are retained.

No runtime is available. New/updated process regressions, Bash/Node20/22/24 and device checks
are NOT_RUN. Executed source evidence: required instruction/schema/source reads; Node20.19 public source/docs;
Node22.16 and24.0 spawn_sync.cc also append before checking maxBuffer. Those are source checks,
not measurements in any Node runtime. API review found no new Node20/22/24 API requirement:
TextDecoder ignoreBOM/fatal, Buffer.subarray/every, fs synchronous fd APIs, built-in node:test,
net listener and child_process spawn are established built-ins. Root CI's existing uid65534
test children still require permission to execute the current Node binary.
Authored26 process cases total: new valid256-record leading-BOM identity capture and
recomputed-checksum invalid UTF8 refusal; strengthened oversized-output regression requires an
exact512KiB+1 all-A prefix, explicit truncated metadata, and original CAPTURE_1_FAILED.
These cases remain NOT_RUN.
Source/test checkpoint:5d99eda49c15a831afc5da643eb6d8dc45ea42b5.
Embedded Node body/test source syntax compiled in functions V8 after removing ESM imports
only for the test-source syntax check: PASS. This is not node --check or a process test.
Opus filename-only conflict checks before follow-up commits read active ref
opus/continuum-brain-research-20261029 at2d46cde0dd2d5f0bda1ab7b8f27c424fa00bedbd:
no overlap with these rescue paths. No EWM1 algorithm/dev/test dataset contents were read.
No phone, private ledger, signer or Keystore data was accessed.
Next: root independently reviews/selects the two changed scripts, then executes these checks
in an available clean isolated runtime. Runtime commands remain:
bash -n scripts/continuum-generation16-rescue.sh
node --check scripts/continuum-generation16-rescue.test.mjs
node --test scripts/continuum-generation16-rescue.test.mjs
Rollback: discard this isolated follow-up; no source device or private data is changed.
