# MΩ-1 ALPHA runtime handoff

Status: SOURCE_INTEGRATED / independently reviewed LOCAL_MODEL_HOST for the exact approved host pair; no repair, learning, device or release acceptance. Base ca93fb4f7a17ff7a2a13debd93197ba485594f06.
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
Process-only verifiedModelExecution stays false. The exact independently reviewed
model/runtime tuple may carry true after actual native inference. HMAC authenticates
the fixed CLI transport; the reviewed build/run establishes conventional inference
provenance, not inference quality or device/scientific acceptance.

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
At the initial process-only phase real approved model/runtime was absent and no
inference was attempted. That historical LOCAL_MODEL_HOST gate was NOT_RUN; see
the approved genuine-model phase below. Android/Termux, ARM64 and Fold7 NOT_RUN.
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

Independent reviewer challenged the process-only candidate and then the genuine
model pair/source separately; ALPHA does not self-certify.

Read current branch SHA and raw logs in docs/evidence/momega1/runtime on resume.
Rollback: omit isolated runtime commits; preserve existing CCC1 and owner gates.

## Approved genuine-model phase

Chief explicitly relayed owner approval for the exact Qwen1.5B Q4_K_M file and
pinned official llama.cpp CPU build. Baseline95b6cb1f merged before work; prior
reviewed process-only HOST_INTEGRATION semantics preserved. See
LOCAL-MODEL-RUNTIME-ADMISSION.md. Download/build artifacts outside Git; independent
challenger must approve reviewed tuple before true-inference admission is enabled.

Actual-model phase checkpoint: weights match official approved1117320768bytes,
SHA256 cc324af070c2ecbfd324a30884d2f951a7ff756aba85cb811a6ec436933bb046;
existing GGUF parser admits qwen2 architecture. Official source clean tree
42065e172024f95eb242763caa06812c09d363e7 at6cb3e672. Initial llama-cli target
unavailable with server disabled; approved classic llama-completion target build
is progressing outside Git with same pinned source and2jobs. Registry remains
CANDIDATE; no actual-model true receipt can be minted yet.
Rejected model replies now retain a signed bounded failure envelope with hashes,
decoded-token count and INVALID_MODEL_RESPONSE status; no raw rejected reply or
candidate. Provider authenticates it, preserves private-branded observation on
error.modelObservation/error.trace and durable trace, then throws the typed model
response error. Inference failure is distinct from unavailable model. Config-only
ELF fixtures remain HOST_INTEGRATION/verifiedModelExecution=false.

Compatibility freeze8425b119e1b6485527de285dd3375f233b27fdf5:137/137 exact-source
Node runtime/coding/GGUF checks PASS, zero skip. Real approved official Qwen and
CPU/static binary load/decode observed; own valid123-token synthetic proposal
returned via default CLI within60s, before final provenance-field freeze. Native
counter RED, pinned trailer and JSON/schema/template repair evidence archived.
Independent second build reported byte-identical c2c13a2a...cbf5a. Await untouched
challenger blind rerun and explicit evidence hash before candidate registry can
advance. See LOCAL-MODEL-RUNTIME-ADMISSION.md; no current genuine true flag yet.


## Final actual host admission — independently accepted

Production code frozen9e28d24bcf9ef11d9b19f5fec1be60efe2c6ab96.
Only the exact modelcc324af0...bb046 / binaryc2c13a2a...cbf5a tuple is now
REVIEWED_LOCAL_MODEL_HOST, following challenger approval certificate SHA256
88e886df6bd60e953605f49e2768529002c9925430a88b5d1467cf95ee40c9fe.
Registry status/review SHA were changed only after explicit independent approval.
The artifact certificate is copied under actual-model/independent-approved-pair-review.json;
its primary source is the challenger branch. Unknown binaries, dummy GGUF,
configured kind/boolean, injected transport and JSON clone cannot gain genuine proof.

Challenger then independently accepted finalsource9e28: actual DEFAULT fixed CLI
provider generated142tokens in26.562s, verifiedModelExecution=true in both nested
frozen observation and trace, private brand true, JSON clone false.50/50 controls
PASS, zero fail/skip, including forged valid-MAC+exact-pair injected transport,
consumed repeat/restart budgets and earlier real-model cancel/path-swap controls.
No more author model inference was run after the frozen final review. Own final
exactsource coding/runtime/GGUF regressions137/137PASS, zero fail/skip.

Independent untouched blind response was bounded and normal-exit but passed6/8
correctness checks with2regressions. That is NOT a verified repair or benchmark
success. No patch applied by ALPHA; first cognitive loop, learning gain, Android,
Fold7 and release remain separate gates. No weights/native executables in Git.

Current Draft PR for this genuine phase: #427 (old process-only PR412 was merged).
Source/app interface errors preserve original branded error.modelObservation and
error.trace on CODING_RESPONSE_INVALID/CODING_NOOP_PATCH. responseStatus is
INVALID_MODEL_RESPONSE and proposalSha256=null, so rejected text cannot become a
candidate or experience. Normal proposal responseStatus is PROPOSAL. Exact
reviewedPairId/modelSha256/runtimeSha256 must remain bound across the task.
Abnormal native termination without final counters never synthesizes completion.

Resume: verify codeSHA9e28 plus latest evidence-only head; inspect independent
review evidence and MODEL-ADMISSION.md. Actual runtime env remains outside Git in
/workspace/momega1-local-model/runtime-env.json, containing only public pins/local
paths. Route real synthetic unknown tasks through existing strict CCC1 orchestration,
not a direct ungranted worker. Rollback: remove/revert exact registry admission and
isolated runtime commits; keep rejected/cancelled journals and owner gates intact.
