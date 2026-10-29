# Rescue BOM / output bound follow-up

WIP / UNVERIFIED. EXP-12, EXP-00 / EXP-09. Baseline PR218 final checkpoint:
0b2a9dddfe172eac82923092fcdaa974afc6e25a.
Isolated branch: codex/generation16-rescue-bom-bound-20261029.
Only rescue shell helper/test are owned; no app/Kotlin, authorization, model or release changes.

Two source issues under review:
1. TextDecoder default removes a leading UTF8 BOM from each field. Kotlin's UTF8 String decoder
preserves U+FEFF, and the record hash includes it. A correctly hashed original ledger can be refused.
Fix will preserve BOM as field data, retaining fatal malformed-UTF8 rejection.
2. Node spawnSync maxBuffer is checked after appending a whole read chunk; returned partial stdout
can exceed the option. Current capture save writes the entire returned buffer.
Node20.19 src/spawn_sync.cc OnRead appends before IncrementBufferSizeAndCheckOverflow.
Fix will explicitly cap retained partial stdout at512KiB+1 without accepting any overflow.

No runtime is available. New/updated process regressions, Bash/Node20/22/24 and device checks
are NOT_RUN. Only required instruction/schema/source reads are available at this checkpoint.
No phone, private ledger, signer or Keystore data was accessed.
Next: author these narrow source changes/regressions, read back the GitHub blobs and run static
JavaScript syntax compilation. Runtime commands remain:
bash -n scripts/continuum-generation16-rescue.sh
node --check scripts/continuum-generation16-rescue.test.mjs
node --test scripts/continuum-generation16-rescue.test.mjs
Rollback: discard this isolated follow-up; no source device or private data is changed.
