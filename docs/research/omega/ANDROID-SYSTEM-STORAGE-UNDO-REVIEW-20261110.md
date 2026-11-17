# Cache undo index failure and EOF admission

Canonical integration owner: root / PR #735. Support branch and PR #738 remain open.
This is a follow-up to local source `8ffcd665fb447e4ee6cab6b1217bf0d688db218d`
(published equivalent `8d0c0bb6a5179d72dd4da925b2e4dbb056c23786`).

## Reproduced defects

1. An actual filesystem failure when replacing the diagnostic ownership index
   occurred after restored files had been written. `restore()` reported
   `RESTORE_INDEX_FAILED` but removed those records from the in-memory undo set.
   The next attempt returned `UNDO_UNAVAILABLE`; restored files were protected but
   absent from diagnostic history.
2. A candidate whose declared content exactly consumed the remaining hash budget
   was opened and fully read before the required EOF check was refused. This
   wasted the read without producing a valid content result.

## Correction

Undo now distinguishes pending index entries from completed restorations. Bytes
and the exact written inode/mtime/length remain in the bounded undo record when
the index fails. A subsequent individually approved restore checks both identity
and the complete content hash, then commits the index without rewriting that file.
Only an index-confirmed entry is removed from undo. Changed contents, same-content
replacement inodes and conflicting ownership entries are refused. No rollback
deletion or overwrite is used. An evicted/missing file can be recreated using
`CREATE_NEW`, which refuses a newly occupied destination.

While index recovery is pending, new history retention and cache cleanup refuse
to replace the recovery state. STOP leaves the retained state available; normal
partial-restore bookkeeping remains intact. Existing process-local undo lifetime
and ten-minute expiry still apply; this is not durable recovery across process death.

Hash admission now reserves one byte for the EOF probe before opening a document.
The reported byte count includes only bytes actually read, not the reservation.
An exact remaining budget is classified as `HASH_BYTE_LIMIT` without opening the
unverifiable candidate. One extra byte of capacity permits normal EOF verification.

## Validation

- Baseline: **40 JUnit methods, 5 failures**, exit 1. Four reproduce lost undo
  recovery/changed-target handling; one reproduces unnecessary exact-budget reads.
- Candidate: **40 methods, 0 failures**, exit 0. Includes cancellation, same-content
  replacement, symlink/privacy, bounded provider wait, partial undo and new index
  failure cases. The final candidate additionally checks that pending recovery
  blocks retention/cleanup and that STOP does not consume recovery.
- Baseline/candidate source and test hashes, tool versions and terminal outputs:
  `docs/evidence/android-system-storage-20261110/undo-review/`.
  The extra final guard assertions are included in the candidate test fingerprint;
  no claim that the two entire test source files are byte-identical.
- Actual index failure uses temporary real filesystem paths: the index target is
  replaced with an empty directory during restoration, then restored by the fixture.
  There is no injected production test hook, guessed success or deletion of a
  possibly replaced user file.
- Pure Kotlin/JVM run only in this follow-up. No Gradle, new Android compile,
  Android-provider runtime, physical Fold7 or performance gain claim.

Reproduce with the existing `scripts/continuum-system-storage-check.sh` and JUnit4
classpath. No new tool, permission, dispatch, public API, shader or device engine.
The root executor already keeps its undo token on failure and clears it only when
the status is `RESTORED`; no integration API change is required.
