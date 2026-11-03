# Independent workspace and handoff review

Reviewer branch `codex/genesis-workspace-review-20261103`, PR #441. No production
files changed. These are software contract tests, not model intelligence scores.

## Verified source checkpoints

| Exact candidate | Independent result | Scope |
| --- | --- | --- |
| `b66534f0` | 8/11 Kotlin tests passed; 3 defects reproduced | Actual adapter, mailbox, worker and profile store; synthetic canonical authority/executor port |
| `0297b2680b2c44980086186ec7429bcda42a809a` | 11/11 same Kotlin tests passed | Sender authority, recipient cap and fixed execution scope corrected |
| `0297b2680b2c44980086186ec7429bcda42a809a` | Original 22/22 admission cases; supported expanded 28/28 | Thin bridge invokes compiled actual Kotlin admission and actual persisted profiles |
| `396ade51faf0bedc834391c7fcc693628c62d102` | 9/9 independent Node identity probes | Shared expiry, current profile removal/cap reduction, immutable task binding and process-only origin |
| `396ade51faf0bedc834391c7fcc693628c62d102` | 1/1 Docker integration, zero skips, 12.04 seconds test duration | Actual persisted Forge/Lint identities, builder plus both independent verification sites |
| `396ade51faf0bedc834391c7fcc693628c62d102` | 6/6 independent canonical boundary tests; 50/50 existing memory regressions | Unknown execution recovery, source binding, copied evidence rejection, current grant and full-store withdrawal |
| `40508126` | 6/8 independent Echo tests; 2 defects reproduced | Final grant task association and dirty source recheck missing; correction pending at this checkpoint |

The 28 supported cases include the original 22; they are not 50 distinct cases.
`B25-profile-lifetime-cap` is **NOT_TESTABLE** through the current profile API,
which has no budget edit operation. The bridge initially ignored a supplied
500ms budget and created the default profile. This must not be counted as policy
evidence. `B28` separately tests the real default 120000ms limit. Builder was
notified to make unsupported fixture setup explicit. No production setter was
added merely to satisfy a fixture.

The Docker rerun uses actual image
`sha256:4838093b14e654fcf3b69decfe8b8a1fe75ef6c288ea5dadc4e169131716494c`.
One builder and three verifier observations have four different container IDs,
matching task/job/request hashes, and confirmed teardown. The candidate patch is
explicitly **SCRIPTED_FIXTURE**. There were no local or external model calls.
The fixture uses the actual canonical owner-repair driver; it is not a run of the
strict model-admission path and does not establish MΩ-1 by itself.

## Reproduced changes and honest limits

The first implementation admitted a registered sender without task assignment,
allowed a two-call request for a one-call profile, and constructed a worker for
an undeclared fixed execution profile. Independent tests reproduce all three
failures, and the corrected source passes the same behavior checks.

Kotlin tests deliberately provide a synthetic atomic authority. Concurrent tests
show that adapter instances respect refusal by that authority; they do not prove
cross-process durability, canonical replay persistence or a production atomic
budget implementation. The new Kotlin authority remains a required trusted port,
without a permissive implementation. Durable task/claim authority remains CCC1.
There is no new scheduler, registry, truth database or Android transport here.

Node assignment binds persisted identity to existing canonical execution views.
Both actual verifier sites use the separate verifier view, under the same original
claim and deadline. The logical workspace ID is not a container security boundary.
The new assignment is process-private and must be reconstructed by trusted
composition after restart; copied JSON cannot reactivate it. Per-profile aggregate
tool quotas and full app-to-host transport are not established by these tests.

Echo source `40508126` passes deterministic hashing, replay without journal
mutation, initial dirty-source rejection, profile removal, expiry, artifact tamper
and symlink controls. A real fixture grant's canonical task ID can change during
reporting while its fingerprint stays fixed; a real source file can become dirty
while HEAD stays fixed. Both escaped final revalidation. They are confirmed source
defects under frozen E02/E03, not failed model reasoning. Corrected source must be
rerun before accepting reporter integration.

## Evidence and reproducibility

Raw logs, exact candidate hashes, Docker observations and JVM dependency hashes
are under `evidence/`. Kotlin uses an archived exact candidate source set and a
recorded previously compiled application dependency graph; this is a focused host
compile, **not an Android Gradle build**. It tests the V1 profile schema at that
candidate; later V2 changes require a new compile and integration check.

Commands:

```sh
bash tools/genesis-workspace-review/run-kotlin-review.sh <SHA> <fresh-output-dir>
GENESIS_REVIEW_TARGET=<exact-checkout> node --test tools/genesis-workspace-review/assignment-independent.test.mjs
GENESIS_REVIEW_TARGET=<exact-checkout> node --test tools/genesis-workspace-review/reporter-independent.test.mjs
GENESIS_HANDOFF_CLASSPATH=<compiled-candidate-classpath> node tools/genesis-workspace-review/run-adapter-cases.mjs <actual-adapter-bridge>
```

The first corrected-source Kotlin run failed only because the reviewer asserted
`IllegalArgumentException` while safe rejection used `IllegalStateException`.
After widening the exception assertion to `RuntimeException`, **both** baseline
and candidate were rerun: 8/11 versus 11/11. Initial and normalized logs remain.
An initial Echo fixture used an unsupported root-level coding path; all eight
setup failures are retained separately. Changing only the synthetic path to
`lib/example.mjs` exposed the two product failures above. Setup errors are not
counted as product RED→GREEN.

Statuses: SOURCE_IMPLEMENTED and HOST_TESTED for the named checkpoints. The
reviewer independently executed the stated tests. No ANDROID_BUILD_VERIFIED,
FOLD7_VERIFIED, release, durable bot work scheduling or intelligence-gain claim
follows from this evidence. Original 22 disclosed cases are regression material,
not a hidden generalization holdout.
