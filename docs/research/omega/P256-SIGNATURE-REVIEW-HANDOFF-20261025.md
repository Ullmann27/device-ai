# JARVIS-MO: Isolated P-256 signature-review laboratory

Base: PR #135 at `93b92c03cc8bc5df840edeaf0d48590bfeaed13b`.
Independent branch: `research/continuum-p256-signature-review-chatgpt-20261025`.

## Published code
- `experimental/omega/owner-consent/signature-review.mjs`: only validates one SHA-256+P-256/IEEE-P1363 signed envelope against host-supplied expected owner/device/audience/task SHA/action/key ID/policy/version and validity window; strict schema and public-key pinning; reports `SIGNATURE_VALID_NOT_AUTHORIZED` with `grantCreationAllowed:false`.
- `experimental/omega/owner-consent/signature-review.test.mjs`: 13 deterministic Node test cases covering valid non-authorizing signature, action mismatch, tampered nonce, expiry/not-yet-valid, revocation, private-key misuse, fake ownerApproved, key swap, task-hash mismatch, malformed/invalid signature, duration bound, and explicitly unprevented replay.

## Test evidence and limits
- Separate earlier isolated local smoke assessment of Sol-proposed signature verifier reported 12/12, **not** evidence that these exact GitHub test bytes have been run.
- Actual GitHub-published 13-test suite: **NOT_RUN in this handoff**. Run: `node --test experimental/omega/owner-consent/signature-review.test.mjs` and compare source blob hashes with published files.
- Full OMEGA security suite: Sol independently recorded **80/80 passed** from unmodified PR #135 security files, see `research/sol61-owner-auth-lab-20261025:docs/research/omega/SOL61-HANDOFF.md`. Not a pass for this new module.
- Java/Kotlin/Android compilation, real Android Keystore, biometric enrollment, authenticated caller, deployment, APK, device, CCC1 grant wiring: **NOT_RUN/NOT_IMPLEMENTED**.

## Security boundary
- This lab does NOT enroll or authenticate an Android owner. Its trusted public key, time, revocation and expected binding MUST originate outside user-provided task envelope.
- Does not durably consume nonce. A valid signed envelope may be re-verified and is deliberately **NOT AUTHORIZED**. Never wire this review result directly to `.github/continuum/coding-orchestrator.mjs::prepareCodingTask()` because that function creates a CCC1 grant.
- Next research: durable crash-safe nonce ledger; test concurrent replay, restart, truncated/corrupted ledger and fail-closed policy; then design host-verifiable owner grants with separate Android Keystore/BiometricPrompt integration and non-bypassable grant enforcement.
- Never modify `main`, owner signing keys, release APKs or productive data. Do not merge laboratory prototype into production authorization.
