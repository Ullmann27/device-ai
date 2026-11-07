# Independent stateful acceptance supplement v1

Frozen before inspecting the Genesis builder's implementation. Baseline remains
4b881f81ddfbe38729a551d9e460e818c6b3b715. This supplements the 22 existing admission
fixtures, which alone cannot prove execution, atomic budgets or crash continuity.

## Budget, delivery and restart

S01. Two simultaneous reservations each fitting individually but exceeding the
same parent's remaining model/tool budget together: at most one succeeds; exactly
one debit; denied work never enters the mailbox, executor or model.
S02. Mailbox-full rejection: no false delivered status and no execution. Retry may
reuse a still-valid canonical unused reservation or make a new authorized debit,
but cannot mint tokens, double-run or silently forget an accepted effect.
S03. Duplicate message after draining the mailbox, after coordinator eviction and
after process restart: canonical job/attempt authority still blocks repeated work.
The mailbox's bounded deduplication set is not the replay authority.
S04. Send before expiry, consume after expiry: no executor/model call. Repeat with
revocation, source change and changed attempt between send and consume.
S05. Crash/unknown execution with no final verified receipt: fail closed; loading
profiles, reinitializing a port or receiving a late DONE does not resume the effect.
S06. Cancelling the parent while a child is queued/starting/running/verifying cannot
reactivate the task from a late callback. Exactly one terminal outcome. Physical
termination is UNKNOWN unless the executor observed teardown.

## Independent execution and actual identity

S07. Builder and verifier IDs must be distinct stable registered principals; same
ID with different display names or two capabilities is not independence.
S08. Positive proof uses actual separate fresh-worktree verification through the
canonical repair path. Both real executor observations retain the same canonical
job/request/source binding while their authorized worker IDs differ. Fresh sandbox
IDs alone do not prove different bot principals.
S09. Optional verifierExecutor cannot silently fall back to the Builder-assigned
executor in the new bot flow. Legacy callers may retain explicitly legacy behavior,
which cannot be labelled a multi-bot independent-principal result.
S10. Copied/deserialized/hand-edited assignment brands and mismatched task/job/
request/fingerprint/source/worker observations reject. A trusted-looking flag,
model prose or caller-supplied receipt hash cannot manufacture admission.
S11. Swapping the prepared task, profile or verifier executor after authorization
must not change captured immutable scope. A mutable caller-owned Set or object
cannot expand tools, limits, identity or workspace association after admission.
S12. Distinct executor instances share the original job deadline, resource budgets
and canonical claim. The verifier's separate process/container does not reset total
model calls, profile calls, CPU/wall budget or output allowance.
S13. Revocation/expiry between primary GREEN and independent verification blocks
verification or suppresses success according to the existing canonical state;
post-verification recheck remains mandatory before experience admission.
S14. Cancellation reaches the currently active verifier path as well as Builder;
no leaked sandbox/process after reported teardown. Non-cooperative/unknown outcomes
remain unknown and cannot become a stored success.

## Memory and presentation

S15. Mnemos calls existing branded recordTechnicalExperience; no new untrusted
record constructor, self-written successful reflection or profile metadata bypass.
Current-grant, ancestry and full-store revocation negatives must continue to pass.
S16. Echo receives provenance/status/evidence references only; it cannot relabel
HOST_INTEGRATION as local inference/Fold7 success. Scripted proposals stay labelled.
S17. Persistent workspace association and ephemeral sandbox observation remain
separate. No claim that this adapter implements private persistent bot files or
Android OS isolation unless those additional paths are actually exercised.

Method: inspect candidate diff and transparent bridge, run existing frozen admission
fixtures, then use controlled latches/process restarts and actual canonical host
integration where available. No heavy build/container/model run during Chief's
reserved local-inference window. Preserve all failures and exact source hashes.
No expected success claim from the Builder is an independent test result.

## Explicit fixture schema adjustment after first static review

At source b66534f0 the implementation lacked canonical stage-sender mapping and
profile-cap checks. The builder accepted these findings before executable review.
The 22 original fixture outcomes/mutations remain unchanged. Their positive fixture
now carries an explicit fixed trusted `stageSenders` mapping and per-profile budget,
so the stricter API can be tested without inventing authority from the message.
`B22`–`B27` supplement these with registered-but-unassigned sender, separate profile
caps, missing sender authority and message-provided authority spoofing. These are
independent admission counterexamples, not a new unknown intelligence benchmark.
