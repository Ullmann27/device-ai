# Independent protected-store review

Read-only review of the coordinator's initial protected-store implementation;
all writes during the probe were synthetic temporary records. The generator,
TRAIN/DEV/replay files and independent holdouts were unchanged.

The runnable probe used the actually compiled privacy-checks.jar and produced
before.stdout.txt. It demonstrates:

1. Consent revocation on one store object left a second object for the same
   directory/key able to obtain a training batch and stage new records.
2. Deleting a record did not invalidate an old approval object; replaying it
   could reinsert the same record. Content equality alone is not a freshness
   guard against stale UI callbacks after a deletion.
3. At the 256-record limit, replacing one reviewed record with a freshly
   reviewed revision returned CAPACITY_REACHED although total count need not
   increase. The old record was preserved; no data-loss claim is made.

These are initial red findings, not repaired outcomes. The coordinator owns the
store code and any fix. A later passing run must identify the replacement class
bytes and preserve the original observation. This probe deliberately prints
observations rather than pretending all initial behaviors passed a safety gate.

Reproduce with a supplied jar built from the coordinator's native source:

```sh
kotlinc IndependentStoreReview.kt -cp privacy-checks.jar \
  -jvm-target 17 -include-runtime -d review.jar
java -Xmx256m -cp review.jar:privacy-checks.jar IndependentStoreReview
```

The consent/API contract may change during repair; a revised probe must describe
that change and retain this pre-fix source rather than erasing the red evidence.

## Independent verification after repair

The coordinator introduced one authenticated file-backed policy/revision shared
across instances and processes, content/store/revision-bound approvals, atomic
inspectForReview snapshots and a replacement-aware capacity calculation.
`IndependentStoreReviewAfterFix.kt` uses the new three-argument approval API.
All four independent assertions passed against the rebuilt actual classes:
revocation blocks both instances, deleted data rejects a previous approval,
replacement succeeds while remaining at 256 records, and an old form cannot
issue a fresh approval after policy revision. Exact output and jar/source hashes
are recorded beside this file. This is host-JVM verification, not Android UI,
Keystore hardware or physical Fold7 verification.

## Separate UI consent race review

The evaluator subsequently found two races in the initial UI consent toggle,
which are separate from the three store defects above. The original red
observations are preserved under evaluation/ui-consent-race-before on PR #755.
The coordinator added consentForReview and setTextTrainingConsent with mandatory
expected revision. The revised UI and three passing native-store interleaving
checks are under ../../ui/. Compilation against Android API classes passed;
Android event-loop and hardware tests remain NOT_RUN.
