# Independent runtime assessment

Candidate: 37e16cefac4ee3b794d79540214a69f1c6181b82. Base: ca93fb4f7a17ff7a2a13debd93197ba485594f06.
Reviewed in detached /workspace/momega1-challenger-candidate; checkout remained clean.
Author evidence a4fc5450 was not used as independent test success.
Independent criteria freeze 071ef7f3; C probe freeze bc40a831; neither changed.
Full binary-capable source diff retained as runtime-full.diff.

**Verdict: PROCESS_BOUNDARY_HOST_VERIFIED for the tested synthetic cases;
CONDITIONAL_SOURCE_ACCEPTANCE; BLOCKED_LOCAL_MODEL_UNAVAILABLE;
FOLD7_NOT_RUN. No FIRST_COGNITIVE_LOOP_VERIFIED or learning claim.**

35/35 independent checks PASS, 0 FAIL, 0 SKIP, exit 0 on Node v24.19.0.
Nine original frozen provider checks are unchanged. The three baseline RED
receipt-claim cases now pass. Twenty-six additional review checks exercise actual
compiled C ELF processes or exact public provider interfaces. This is synthetic
CONTRACT_TEST / HOST_INTEGRATION evidence, not actual model inference. The frozen
C source remains unchanged; an explicit review adapter adds synthetic decoding
counters to successful fixtures so later rejection gates can be exercised.

## Proven observations

- Actual bwrap/prlimit process receives retained read-only model/runtime descriptors.
  Modes 0/5/6/8 pass, including actual model descriptor read and loopback-only
  interface enumeration. Wrong pins, symlinks, hardlinks and same-byte inode
  replacement during execution fail closed, for both model and runtime.
- TERM-ignoring loops time out; AbortSignal cancels and joins. The descendant
  attempt encounters kernel fork denial; this test is not proof of killing an
  unrestricted descendant tree. Post-suite pgrep -x momega1-probe returned 1
  (no matching owned fixture process). No native thread/process leak observed.
- Child cannot read a synthetic host sentinel, see the configured synthetic
  credential names, or open the admitted model for writing. Actual RLIMIT_AS
  <=4GiB, CPU<=3s for this fixture, NOFILE<=64, FSIZE<=1MiB are observed inside
  the native process. Fork is denied. These are per-launch limits, not a global
  memory/process quota across concurrent requests.
- Output flood, nonzero exit and missing decoding counters are rejected. Recorded
  counters are still text emitted by an approved executable, not neural evidence.
- Strict provider rejects unobserved injected replies, forged receipt/MAC and
  explicit verifiedModelExecution=true. Honest CONTRACT_FIXTURE result is refused
  by production provider. Accepted process observation is frozen and private-
  branded; JSON clone is untrusted. Source/prompt/proposal digests and HMAC are
  checked; accepted proposal digest independently recomputed by challenger.
- Bounded recall enters prompt as trusted:false historical hints, its serialized
  size shares the context budget, and its raw text is absent from durable trace.
- Failed requests remain consumed after provider restart when a durable evidence
  directory is supplied. Expired deadlines/wrong source invoke no injected child.

## A/B counterexample: process brand is not proof of inference

The exact same challenger fixed-output ELF and structural dummy GGUF are used in
both arms. A: _RUNTIME_KIND=CONTRACT_FIXTURE => production refusal. B: change only
trusted configuration _RUNTIME_KIND=LLAMA_CPP => fixed CLI launches ELF, observes
synthetic counter text, produces valid HMAC, provider returns a frozen branded
modelObservation with evidenceClass LOCAL_MODEL_HOST. Both have no neural weights
and perform no inference. trace.verifiedModelExecution remains false.

Relevant candidate locations: scripts/continuum-local-model-observation.mjs:124,
:175, :180; .github/continuum/coding-provider.mjs:248 and :270.

This is not an HMAC exploit or untrusted-model authorization bypass: trusted
caller chose the executable pin and kind. It conclusively limits what the brand
and LOCAL_MODEL_HOST string establish. ALPHA's handoff correctly describes this
boundary. Any downstream promotion of that brand alone to actual inference,
FIRST_COGNITIVE_LOOP_VERIFIED, or model-learning evidence is unsound. Chief must
require independently observed actual approved llama.cpp/weights execution before
such claims. No real runtime/model was available or downloaded for this review.

## Remaining acceptance gaps / requests to integration owner

1. **P1 inference/experience promotion gate:** preserve process-only semantics.
   Audit BETA/GAMMA consumers of isTrustedLocalModelObservation against the A/B
   counterexample. A transport MAC, configured kind, SHA and private brand do not
   attest neural computation. Source-only hardening can integrate; actual loop
   acceptance remains blocked until real model evidence exists.
2. **P2 end-to-end replay/revoke:** direct queryPinnedLocalCoding accepts the same
   binding nonce twice and launches twice. It has no revocation authority. The
   provider can be constructed without durable evidenceDir; whole-task grants,
   replay, crash UNKNOWN and revoke remain existing worker responsibilities. The
   candidate baseline orchestrator does supply evidenceDir, but this lane did
   not certify later BETA implementation. Require its independent durable gate
   tests before end-to-end acceptance.
3. **P2 shared capacity/cancellation:** two actual helper calls run concurrently,
   each with independent per-launch budgets. No shared chat/coding capacity gate
   was observed. Direct helper supports AbortSignal; synchronous production
   provider does not expose that signal and blocks its own JS event loop while
   waiting for fixed CLI. CLI has SIGTERM/SIGINT handlers and process watchdog;
   this does not establish owner/UI cancellation or revoke during generation.
   Require upstream admission/STOP coverage and resource measurements. Actual
   Android chat plus coding was NOT_RUN.
4. **Platform/native compatibility:** actual approved llama.cpp CLI flags, real
   decoding, x64 loaded dependency set, ARM64 seccomp, Termux kernel/bwrap and
   Android native lifecycle remain unverified. System loader libs plus root-owned
   bwrap/prlimit are trusted compute base. The Linux namespace test is not Android
   isolation proof. No authenticated shared LiteRT IPC exists in audited baseline.

No production changes were authored. No model download, remote inference, device
connection, install, signing, main change, owner-data access or paid action.
User confirmed device unavailable; FOLD7_NOT_RUN remains final for this wave.

## Reproduction

```
MOMEGA1_TARGET=/absolute/checkout/of/37e16cef node --test \
  tools/momega1-challenger/provider-contract.test.mjs \
  tools/momega1-challenger/runtime-review.test.mjs
sha256sum -c docs/evidence/momega1/challenger/freeze.sha256
sha256sum -c docs/evidence/momega1/challenger/supplemental-freeze.sha256
```

Requirements: native Linux Node, cc, root-owned /usr/bin/bwrap and prlimit, working
user/PID/network namespaces. Tests fail rather than skip missing dependencies.
All runtime fixture data are disposable synthetic files and fixed JSON replies.
