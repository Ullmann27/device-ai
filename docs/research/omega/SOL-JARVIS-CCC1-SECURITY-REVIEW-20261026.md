# CCC1 independent read-only review and closure disposition

Reviewer: independent `ccc1_security_audit` agent. Repository branch: PR #169,
initially `04e984562d5755922b5b16fd72c61ab0f3f06be1`. Final report received
before the owner's 20% closure instruction. No product edits by the reviewer.

The initial delegated adversarial-execution audit was rejected by the platform's
automatic safety review. The task was reduced to read-only source review; no
adversarial code was generated/executed by that agent, and no malicious green
counterexample is claimed. Existing positive development proofs are separate.

## Findings and disposition

1. **Post-GREEN HEAD drift was not checked.** Existing `changedOnly` compared a
   patch against current HEAD instead of the approved base. A concurrent host Git
   commit could escape the final scope check. Docker snapshots omit `.git` and
   expose read-only host binds; this is not an observed sandbox escape.
   **FIXED in closure:** production `assertCcc1WorktreePatchScope` verifies the
   exact base, and original source is rechecked before returning review status.
   A real temporary Git repository reproduces the old missing rejection, then
   passes after correction. See `ccc1-head-signal-red.tap` / green evidence.

2. **Interrupted RED could lose signal/error information.** `runProfile` discarded
   SIGKILL/SIGINT/non-timeout errors; `measured` allowed null exit code alongside
   parsed failing tests. **FIXED:** signal/error retained, measured runs require
   normal exit 0/1 and no interruption; sanitized ledger retains termination
   metadata and cannot learn a negative from such a process. Native RED→GREEN
   regression covers SIGKILL, SIGINT, ENOBUFS, ENOMEM, null/127 exit, timeout.
   These tests use structured boundary fixtures, not actual OOM/SIGKILL injection.

3. **Isolated Android report enumeration silently caps at 400 files.**
   `isolated-profile-runner.mjs` uses `.slice(0,400)` with no omission flag; the
   isolated executor cannot distinguish the subset from a full report set.
   **OPEN / NOT_REPRODUCED_NATIVELY:** does not affect current node-only research
   proof. Next change must fail closed before truncation or propagate an explicit
   incomplete marker, with a native fixed-runner regression using >400 synthetic
   report files. No Android/Gradle success can be inferred in the meantime.

## Confirmed boundaries

- Five measured public-development arm files contain RED/GREEN/fresh counts and
  hashed identities. All remain `learningEligible:false`,
  `semanticRepairVerified:false`, `testEvidenceAuthenticated:false`.
- Report enumeration now continues past 60 diagnostics. Oversized/omitted/linked
  host JUnit evidence is incomplete and cannot pass or train a negative.
- Broad isolated/scoped model patches are excluded from learned success even
  with an asserted independent flag. **Do not say all project learning is
  globally disabled**: legacy bounded strategies intentionally retain learning.
- Raw JUnit/report hashes do not authenticate a semantic test oracle. Tests and
  candidate execute within the same container; no blanket tamper-proof repair
  guarantee is established. Research proofs remain owner-review proposals.
- Registry transport and model/locality offers are untrusted planning metadata.
  Existing owner consent, registry, provider and execution gates remain required.
- No additional ordinary validation defect was found in the new immutable
  registry snapshot adapter during this review. This is a bounded review,
  not a proof that no defects exist.
