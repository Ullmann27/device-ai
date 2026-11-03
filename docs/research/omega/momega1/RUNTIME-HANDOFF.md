# MΩ-1 ALPHA runtime handoff

Status: SOURCE_INTEGRATED / independent review pending; no model/device acceptance. Base ca93fb4f7a17ff7a2a13debd93197ba485594f06.
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

## Implemented interface

`createCodingCandidateProvider` remains synchronous. Local `coding` now uses only
`queryPinnedLocalCoding` in scripts/continuum-local-model-observation.mjs; legacy
chat/patch endpoints and explicitly selected remote teacher remain separate.
There is no local-to-remote fallback. Actual local CLI receives no bearer secret.

Required explicit env: CONTINUUM_LOCAL_BRAIN_MODEL; _RUNTIME=llama.cpp;
_MODEL_FORMAT=gguf; _MODEL_SHA256; _MODEL_PATH; _MODEL_MANIFEST (existing six-field
GGUF manifest); _RUNTIME_PATH; _RUNTIME_SHA256; _RUNTIME_KIND=LLAMA_CPP; and
CONTINUUM_ALLOW_ALTERNATE_CODING_MODEL=OWNER_APPROVED_ALTERNATE_GGUF.
CONTRACT_FIXTURE runtime kind always produces CONTRACT_TEST and is refused by the
production provider even with a valid transport MAC. Config is not owner consent
by itself; trusted owner approval remains required upstream.

`isTrustedLocalModelObservation` exported only by coding-provider.mjs consults a
private WeakSet. Only the fixed native spawnSync CLI path can brand a frozen
trace.modelObservation after validating HMAC, nonce, task/source/request/context,
actual serialized runtime prompt, model/runtime/output and canonical proposal
hashes. Injected run transports and JSON clones cannot brand. BETA must retain the
original object; GAMMA authenticates durable proof with existing CCC1 runtime key.
proposalSha256 = sha256Hex(canonical({plan,hypothesis,patches})).
verifiedModelExecution stays false: HMAC authenticates the fixed CLI transport,
not model authorship, inference quality or a device/scientific result.

The child uses root-owned /usr/bin/prlimit and /usr/bin/bwrap, unshares namespaces,
clears env, drops capabilities, binds admitted model/runtime FDs read-only and
copies the transient prompt from FD. Read-only system loader libraries are
visible; checkout/HOME/credentials are absent. Seccomp rejects fork/vfork and
clone3 (ENOSYS), permitting clone only with CLONE_THREAD. x64 and arm64 filters;
other architectures block. Memory address space 4 GiB, CPU <= timeout rounded
seconds, 64 FDs, 1 MiB individual file output, 1800 output tokens, 8192 context,
2 native threads, no GPU/context shift, 64 KiB total captured stdout/stderr.
Deadline/STOP uses process-group SIGKILL and waits for close. Model/runtime are
hashed/stat-checked before and after; rename/substitution/change invalidates output.
Native decoding counters are required separately from prompt-evaluation counters.

## Verification

RED at 253cfe03 (unchanged baseline production): 0/3 pass, exit 1, committed
93f11336. Raw docs/evidence/momega1/runtime/red.tap. Baseline CLI attempted endpoint
without file identity; missing gate/helper were reproduced before implementation.
Initial frozen df45a926 exact-source run: 127/127 PASS, exit 0, zero skips (18 new runtime tests plus
existing provider/remote/local-brain/GGUF/preflight suites). Real compiled C ELF
fixtures exercise actual bubblewrap namespaces and FD mounts, modified bytes,
host-file/network denial, fork denial, native thread compatibility, memory/FD
budgets, timeout/STOP, malformed/flood output and synthetic-evidence refusal.
Fixtures have no neural weights and never certify inference.
Real approved model/runtime absent; no inference attempted. LOCAL_MODEL_HOST
acceptance, native llama.cpp invocation, Android/Termux, ARM64 and Fold7 NOT_RUN.
Bubblewrap support on this Linux host is not evidence of Android support; Termux
without these kernel/tools is BLOCKED_LOCAL_ISOLATION_UNAVAILABLE.

## Active-path integration follow-up

BETA now passes technicalRecall:string and requireLocalModelObservation:boolean.
Their missing behavior reproduced 3/3 RED against df45a926, committed7974b30e
before repair. Provider now validates <=4096-character recall, reserves its actual
serialized size within maxContextChars while retaining at least2048 source-context
characters, and labels it trusted:false / HISTORICAL_TECHNICAL_HINTS. Optional
hints exceeding the remaining budget are bounded/omitted; source budget and full
prompt deadline remain unchanged. Trace stores only recall hash and length.
Strict observation rejects remote choice at construction and rejects unobserved
injected replies. Production native path already always requires observation.
Final source37e16cefac4ee3b794d79540214a69f1c6181b82:130/130 PASS, zero fail/skip, exit0. Raw final-exact-source-green.tap and manifest record full command and source. This remains CONTRACT_TEST; actual-model inference and Fold7 are NOT_RUN.

## Remaining boundaries and review

Pinned runtime binary and OS loader/bubblewrap/prlimit form the trusted compute
base; pins are integrity and explicit selection, not publisher authenticity. A
malicious owner-approved ELF could emit fabricated native counters; this source
cannot attest neural computation cryptographically. There is no actual-model
acceptance without an independently observed run of approved real runtime/weights.
The private WeakSet is in-process ingress protection, not a durable signature.
Existing CCC1 grants, journals, verifier and repair loop remain authoritative.
No FIRST_COGNITIVE_LOOP_VERIFIED, Fold7, learning benefit or release claim.

Independent reviewer must challenge frozen candidate; ALPHA does not self-certify.

Read current branch SHA and raw logs in docs/evidence/momega1/runtime on resume.
Rollback: omit isolated runtime commits; preserve existing CCC1 and owner gates.
