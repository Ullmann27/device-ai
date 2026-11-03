# Independent Android review

Status: WIP / UNVERIFIED. Reviewer branch begins at ca93fb4f7a17ff7a2a13debd93197ba485594f06. Final Android candidate not yet inspected. Independent reviewer is distinct from feature author and QA. Scope is review artifacts only; no feature edits.

## Frozen adversarial cases (before candidate inspection)

1. Full approved task: display every source path, all acceptance criteria, exact full source SHA, fixed chain, bounded limits and task identity. Long values must remain available; do not authorize omitted content.
2. Alter each tuple field after preparation: task ID, job ID, source SHA, source list, criteria, limits, chain, serialized snapshot, snapshot digest and nonce. Reject stale/tampered authorization before command execution.
3. Approval: absence, mismatch, stale identity, task switching, app recreation and process death must fail closed; successful approval is consumed once.
4. Replay and concurrency: duplicate taps, duplicate callback, old callback arriving during a new job, mismatched command ID/action/nonce/job, malformed result and prepare/run interleaving must not issue a second command or accept stale state.
5. Cancellation: canceled/replaced task cannot dispatch or resurrect success. Callback and cancellation must correlate to exact CCC1 job target and current command; a cancellation request alone must not imply terminal cancellation.
6. Lifecycle: destruction/recreation, process death, missing in-memory state and pending callback races must preserve uncertainty rather than restore execution authorization.
7. Production call paths: validate actual manual task draft → full approval → bridge → builder → fixed Termux RUN_COMMAND → callback and canonical CCC1 correlation. Existing runLocal/model path must not be described as model-authored task integration without a real caller.
8. Callback provenance and atomicity: external/forged result, wrong result type and partially persisted tuple cannot establish valid prepared state; updates and callback consumption must be atomic enough to avoid mixed identity.

## Validation boundaries

QA owns every Gradle schedule; this reviewer will coordinate focused host checks. All commands, exact source SHAs, exits, case counts and hashes will be recorded. Contract/stub tests do not establish Android lifecycle or Termux/device operation. No device access: FOLD7_NOT_RUN. No local-model inference established.

## Next step and rollback

Obtain final candidate freeze and prior actual RED evidence from QA, then inspect actual source and execute independently written focused adversarial checks. Rollback is omission of this review-only branch. No main merge, signature, installation, owner data or deployment changes.
