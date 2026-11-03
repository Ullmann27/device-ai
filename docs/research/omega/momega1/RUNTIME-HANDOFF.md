# MΩ-1 ALPHA runtime handoff

Status: WIP / UNVERIFIED. Base ca93fb4f7a17ff7a2a13debd93197ba485594f06.
Branch codex/momega1-runtime-20261103. EXP-00/01/02/03/05/07/08/11/12.
No model download, productive weights, Android, main, signer, owner data or deployment changes.

## Audit and narrow design

Existing coding-provider launches fixed local CLI but only authenticates a loopback
server. Its configured model name/SHA are claims and verifiedModelExecution=false
is honest. Existing GGUF parser/admission and owner-selected configuration gates
are reused; no new GGUF parser.

LocalModelHost is singleton per Android application process, accesses filesDir and
cacheDir. No authenticated Node/Termux inference interface exists. DELTA confirms
sharing would need new authenticated serialized/cancellable IPC and Android-owned
changes; this lane must not invent a localhost or exported-component shortcut.

Proposed isolated direct llama-cli invocation uses explicit owner-approved GGUF
and runtime pins, retained file descriptors, a fail-closed Linux bubblewrap
namespace, bounded prompt/context/tokens/threads/time/output and process-group
cancellation. Hash/stat before/after binds model and runtime; missing files or
isolation block. A fixed trusted CLI returns a nonce-bound HMAC process observation
tying task/source/request/model/runtime/output/proposal. No model process sees the
HMAC secret. This observation is not independent model-quality acceptance.

## Verification

NOT_RUN implementation tests. Real local model unavailable/unsearched outside
workspace; no inference attempted. Android/Fold7 NOT_RUN.

Read current branch SHA and raw logs in docs/evidence/momega1/runtime on resume.
Rollback: omit isolated runtime commits; preserve existing CCC1 and owner gates.
