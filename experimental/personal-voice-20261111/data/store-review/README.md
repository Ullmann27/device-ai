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
