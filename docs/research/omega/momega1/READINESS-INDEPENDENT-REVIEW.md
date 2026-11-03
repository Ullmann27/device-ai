# Independent readiness/camera review

Status: WIP / UNVERIFIED. Review base5feb1a93ffe5db29163e33e4a0ea002ff25252fe; candidate source d32fe38ad2f6464bfaefa8001bfed1f2469165e7, documentationc659f739. Additional cases frozen from the public handoff before implementation/test-result inspection. Reviewer owns tests/docs/evidence only. AgentActivity hooks are not part of this candidate and need separate exact-source combined review.

## Frozen independent challenges

1. Admission requires current explicit opt-in AND foreground AND Android CAMERA permission AND supported front hardware. No start from guide, permission callback, return/resume, old persisted preference or unsupported clock/domain.
2. Capture time must derive from REALTIME sensor metadata, not callback arrival. Reject missing/negative/future time and stale age>750ms; exact threshold, submillisecond conversion and out-of-order capture must not refresh heartbeat or gaze freshness.
3. Zero/multiple faces, low confidence, invalid/empty/overflow/outside bounds, malformed dimensions and unsupported rotation must clear observations. Never select owner identity or infer emotion/true eye direction; ordinary head-position mapping only.
4. Rotations0/90/180/270, front mirroring and smoothing must stay finite/bounded; elapsed-time changes and burst callbacks cannot create speed jumps or turn stale data into current data. Test near-boundary timestamps and coordinate values.
5. Stop/pause/error/permission loss revokes session immediately; every pending open/configure/capture callback carries generation. A late old handle is closed, never rebound. Old callbacks cannot deliver after restart or steal the newer process lease.
6. Close every acquired image on success/failure, and close session/device/reader/worker exactly safely despite repeated stop or initialization failure. No image pixels read, disk/network transmission or detector download. Keep resource-creating callbacks on a live main looper until stale handles can close.
7. Resource selection obeys actual advertised YUV support, at most640×480, at most30fps, bounded buffers and one process-wide camera lease. Empty/invalid capabilities fail closed; multiple instances cannot open concurrently.
8. Readiness guide distinguishes UNKNOWN, unsupported, missing permission, owner-disabled, starting and actual observations. No PASS/connected/deviceverified inference from animation, installed files, granted permission or successful async start. Conflicting snapshots must not overstate readiness.
9. Speech sample matching is whole bounded normalized input, not substring/fuzzy command authorization; reject appended commands, overlong input, ambiguous punctuation/control/Unicode. Voice availability never proves audible test completion or enables owner preference.
10. No app-account connector is inferred from ChatGPT connections, installed apps or OS permissions. Actions remain presentation requests, not grants. Existing model/ToolGate/owner data and native avatar rendering are outside reviewer changes.

Execution: coordinate one bounded compiler/test task with QA; inspect true call paths and source, then write independent adversarial tests against exact candidate. Report original compile-only author RED accurately, not as executed failures. Root's later Activity hooks require an independent caller review before end-to-end source acceptance. FOLD7_NOT_RUN; camera hardware, audible voice and on-device speech remain NOT_RUN unless actually observed.

## Independent execution on d32fe38a

Bounded exact-source compilation and JUnit/Robolectric execution ran with QA's explicit scheduler allocation:1 CPU,512MiB heap,cached dependencies only. The first22 independent methods executed:21PASS/1FAIL/0ignored/0assumption skips; compile exit0, test exit1. All6 actual-adapter controlled Camera2 callback/close/lease tests passed. Raw identities/logs/source hashes are in `docs/evidence/momega1/readiness-review/d32fe38a-first`.

**CR-1 — P2 / REQUEST_CHANGES at policy API boundary:** `AvatarCameraPolicy.acceptFrame` replaces an absent/null capture timestamp with callback `now`. The independent case returned a TRACKING sample at elapsed1000 despite unknown capture time. This contradicts the missing-capture-time fail-closed contract. The native Camera2 adapter currently passes-1 for missing SENSOR_TIMESTAMP, so this is not a demonstrated native missing-timestamp bypass. Requested fix: reject missing capture time in the policy while preserving explicitly captured timestamps. Reviewer made no feature edits.

Additional native-boundary cases were subsequently added for unsupported sensor clock and foreground return after a pending open. They extend the original frozen challenge groups and do not replace the retained22-case RED result. Controlled Camera2 shadows prove calls to close and generation/lease behavior in actual adapter code, not physical resource release, vendor correctness or live capture quality.
