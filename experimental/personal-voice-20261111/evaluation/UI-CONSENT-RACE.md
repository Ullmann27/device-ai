# Independent privacy review: stale UI consent mutations

The initial Android review dialog used a queued callback equivalent to:
`val current = store.consent(); store.setConsent(current.copy(textTraining = wanted))`.
Its generation guard suppresses a stale UI callback display, but does not bind
the earlier consent click to the policy version or stop the queued mutation.

Two deterministic unfavorable interleavings were executed against the actual
encrypted native store, compiled from source SHA-256
`ad973100396872dddc55e21fcf454d4832a7f1d745c30373ca493449363c87f7`.
The pre-fix UI source was local Bot 1 commit
`b16a4160e6cbaecd0d121a74f45ea86b518b0296`. This reproduction executes the old
callback's store calls; Android widgets/lifecycle are not themselves executed.

| Sequence | Required | Actual before correction |
| --- | --- | --- |
| Older queued text-enable click; newer explicit revoke; older callback executes | textTraining=false | true: failure reproduced |
| UI reads text=false/audio=true; independent audio revoke; UI writes old copy with text=true | audioRetention=false | true: failure reproduced |

`UiConsentRaceReproduction.kt` and `ui-consent-race-before/` retain the executable
reproduction, exact command, source identity, raw output and successful process
exit. Exit 0 indicates the reproducer completed; the two privacy expectations
**failed**. Synthetic policy values and a temporary key/directory are used. No
audio, user transcript, cloud operation or model update occurs.

The root and Bot 1 accepted the finding. The requested repair is an atomic
consent-plus-revision snapshot and an atomic, revision-checked text-consent setter
that preserves the current independent audio choice. An old enable callback must
not be reinterpreted as a new consent after a later revoke. Bot 1 owns the
independent post-fix tests and UI binding; their later receipts are separate from
this historical failure evidence.

This does not alter the frozen language tests, the two rejected weight candidates,
or the 24-case native context result. Those results remain bound to the source
hashes they actually executed. New consent/store source must be judged by its own
subsequent validation, not by the earlier context compile-only receipt.
