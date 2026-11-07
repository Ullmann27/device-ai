# Operation Continuum Ω — Team 02 memory

Status: **HOST_VERIFIED_BOUNDED_MILESTONE**. Android/device acceptance remains open.

- Base #390: `b4f61ae9765e26dd401db928b76fa9f233c0742a`.
- Branch: `codex/team-02-memory-omega-20261103`.
- Frozen source: [`e39881e01062a16fd04b8776ba1700936012f5e0`](https://github.com/Ullmann27/device-ai/commit/e39881e01062a16fd04b8776ba1700936012f5e0).
- Draft [PR #397](https://github.com/Ullmann27/device-ai/pull/397), target `fix/continuum-code40-router-race-review-20261103`.
- EXP-12 continuity; EXP-04 replay boundaries preserved. The current master authorized isolated implementation, tests, push and draft PR; historical v31 research freeze is superseded. No main/release/keys/version/Activity/model changes or real owner-data use.

## Runtime and migration design

`ContinuumLearningCore` already constructs `FileLearningExperienceStore` directly. Its LEX2 ledger cannot retain a 257th protected record. The M2 research proposal identified this limit; this work extends the existing contract, without another fact store or new learning algorithm.

Ordinary LEX2 observation churn retains its historical 256-record bound. Protected overflow beyond 256, or a payload exceeding LEX2's 512 KiB byte limit, automatically selects LEX3 within the same store. LEX3 uses immutable, content-addressed 32-record segments, reusing the existing validated LEX2 record codec. Its hard bounds are **4096 records, 32 MiB segment bytes, and 4096 records per appendMissing batch**. Protected records and revocation tombstones cannot be evicted; overflow fails before changing committed state.

One checksummed versioned manifest replaces `experiences-v2.bin` atomically. Its LEX3 magic makes old readers reject the file rather than ignore a sidecar. Segment data is fsynced before publication; directories are fsynced around commit. No non-atomic fallback is permitted for LEX3. Complete transactions use the existing JVM monitor plus an OS file lease. Every normally returning mutation, including idempotent no-op retries, reasserts root and parent directory durability before acknowledging success.

The original LEX2 primary is preserved byte-for-byte as `experiences-v3/legacy-v2.bin`; a changed original after an interrupted migration is additionally retained by digest. The original `.lkg` is never overwritten after migration. Neither becomes an automatic recovery source. Missing or corrupt committed manifests/segments fail closed. Cleanup only removes unreferenced segment artifacts under the lease.

Portable CLPT retains schema 1 and review-only semantics. Its bounded cap is 33 MiB so every accepted 32 MiB LEX3 ledger can export fully. Relative overhead is `12*N - 40*S + 48 + packageUtf8Bytes`; N <= 4096 and package <= 256 ASCII bytes imply extra <= 49,456 bytes. No partial export, record truncation, automatic fact promotion or revocation reactivation was introduced.

## Frozen verification

All fixtures are synthetic and confined to disposable test directories. Source `e39881e0`:

| Check | Actual result |
| --- | --- |
| Store, LEX3, portable transfer, historical read-only tests | **40/40 PASS**, JDK 17, official Kotlin 2.4.0 and JUnit 4.13.2, 256 MiB heap |
| Existing research feedback and real multiprocess tests | **6/6 PASS**; explicit source-list harness updated to include Lex3 |
| Real Linux strace fsync(EIO) retry regressions | **4/4 PASS**: status no-op, duplicate append, empty batch and matching no-op; each retries two successful directory fsync calls |
| Independent syscall review | Transient EIO retry passes; persistent EIO still throws, with no success acknowledgment |
| Independent near-byte-capacity export review | All 4096 records and all fields roundtrip after the cap fix |
| Android SDK, Fold7, physical power loss, model quality | **NOT_RUN by this lane**; broader compilation belongs to Quality |

The 40 tests include the protected 257th record, restart, 1000 synthetic days at four records/day, hard count and byte bounds, large Unicode records, checksum/unknown-version/missing-file failures, same-size cache corruption, symlinks, concurrent real JVM writers, an actually terminated writer, revocation retention, uncertain post-commit errors and complete near-capacity transfer.

Evidence: `docs/evidence/omega-memory-20261103/compile-e39881e0.txt`, `focused-e39881e0.txt`, `feedback-e39881e0.txt`, `durability-e39881e0-receipt.json`, raw syscall traces and independent review outputs. The receipt includes source hashes. Later evidence/documentation commits do not change the tested source.

Final synthetic 4000-record measurement: append **14.83 ms**, seven full reads **p50 49.49 ms / max 56.16 ms**, disk **829186 bytes**. An earlier same-host run measured p50 86.83 ms; load/JIT variance prevents a speedup claim. The entire 40-test JVM ran 5.992 seconds with peak child RSS **365732 KiB**, including JVM/native overhead and extreme byte-limit fixtures. This is not Android RAM or thermal evidence.

The maximum-package boundary fixture used 4096 records / **33,553,407 segment bytes** and exported **33,597,743 bytes** with a 256-character package. All record fields were retained.

## Failures found and corrected

- `d9869c11`: three new tests compiled and failed exactly at protected capacity. Raw RED log retained.
- `28c77782`: 26/27 passed; the seeded holdout oracle incorrectly expected a timestamp refresh on an unchanged status. Independent Quality reproduced it. Only the oracle changed; existing no-op status semantics remain.
- `04f9db6b`: 39/39 passed, but independent review found a valid 32 MiB store could exceed the initial 32 MiB CLPT limit. The exact reproducer and failure are retained; 33 MiB export cap plus maximum-package roundtrip fixed it in `eabda7ff`.
- Independent real syscall injection found post-rename EIO followed by a no-op retry could acknowledge without another fsync. The two store sources compiled at `04f9db6b` were byte-identical at `eabda7ff`. Original hashes: ContinuumLearningExperience.kt `0a656170e3700fef344c807c38f50c68c125eb45e848f6c5bda97ea663cd5bbe`; Lex3LearningExperienceStore.kt `aaf74faec3b22ce623fa9fda8e7e0481db5a8aa81d0041b230f445bfa01ccc63`. `e39881e0` adds the write-return durability barrier. Both transient and persistent fault rechecks passed. This demonstrates syscall handling, not observed power-loss data loss.

## Limits, rollback and next entry

This is a **4096-record milestone**, not long-term tens-of-thousands scale. `all()`, stats and replay still materialize the bounded ledger; replay output remains 1..128. Cached snapshots hash every referenced segment even on a cache hit. Increasing the constant alone is inappropriate: the next milestone needs bounded query/index APIs in existing consumers, then Android latency/RAM evidence.

Trusted app-private parent directories and current-protocol writers remain assumptions. Direct root/primary/backup/lock/segment symlinks are refused. Checksums cannot authenticate deliberate valid-file rollback or a concurrently running old binary that ignores the new lease. Do not downgrade or restore an older primary after migration without separately reconciling revocations. Before any migration, code rollback leaves LEX2 usable; afterward, preserved originals are diagnosis/review evidence only. A post-rename fsync exception means uncertain outcome, so retry idempotently and never restore stale consent.

Independent review reported no remaining concrete source blocker for draft integration. Real Fold7, atomic-move/directory-fsync filesystem support, physical power loss, app heap under loaded models, SAF backup UX, release signing and model quality remain open. No release or device claim is made.

Reproduce syscall regressions with the official JVM dependencies prepared by Quality:

```sh
python3 docs/evidence/omega-memory-20261103/verify-durability-retry.py \
  --deps /workspace/continuum-build-env/jvm-deps \
  --output /tmp/continuum-memory-durability-fresh
```

Use a fresh external output directory. Missing strace fails explicitly as UNAVAILABLE. Exact focused compiler/JUnit commands and hashes are in the frozen logs. Existing feedback harness: `CONTINUUM_FABRIC_JVM_DEPS=<jars> bash scripts/continuum-feedback-review-jvm.sh <fresh-output>`.
