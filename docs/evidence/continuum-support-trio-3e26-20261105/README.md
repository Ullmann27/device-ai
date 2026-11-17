# Independent support trio baseline

Exact tested source: PR #543, `89ae402d88b80ca5c48ab241e20c0369b7db517d`, tree `70c3d700a36e4e88850843fa9b392db92d99a388`. Tracked source remained unchanged. These are newly executed original repository tests on Node v24.22.1; both pre-existing shared machine locks were held.

| Execution | Tests | Pass | Fail | Skip | Exit |
|---|---:|---:|---:|---:|---:|
| Complete Node suite, 327 files | 2397 | 2360 | 0 | 37 | 0 |
| Initial backend, missing Express | 64 | 57 | 7 | 0 | 1 |
| Backend, available runtime dependencies | 64 | 64 | 0 | 0 | 0 |

## Raw Node evidence

`node-full.tap.gz` is a lossless gzip archive of the complete original 519,854-byte TAP log. Inspect with `gzip -dc node-full.tap.gz`. Raw SHA256: `52a55be5b07852e983dbfd1d762a5dc8da8cd36c27e006dc1895d044f154f625`. The 37 skip records remain in that log and include Docker, unavailable kernel isolation, native model and unconfigured JVM profiles; skips are not passes. No gate or policy was disabled to turn them green.

Commands and source bindings are in `RESULT.json`. Node runtime: v24.22.1. Full Node duration: 154.8 seconds. Execution finished on 2026-11-05.

## Backend evidence limit

The first backend run failed because Express was absent in the new checkout. A second run used read-only links to the existing local dependency installation and passed all 64 original tests. Express 5.3.0 and Helmet 8.3.0 match the lock, but installed express-rate-limit 8.7.3 differs from locked 8.7.1. This is runtime-dependency evidence, not exact-lock release reproduction. No source or lockfile was changed.

After those executions, the previously accessible workspace and /tmp source/evidence directories were observed absent. The Node archive had already been uploaded and is preserved here. The backend logs and local launcher had not yet been uploaded. Therefore the backend outcomes and recorded hashes are tool observations, not an archived backend transcript; no reconstruction is represented as the original log.

## Remaining acceptance

The primary implementation team owns #544/#545/#546. Its reported AvatarExpression.WARM compiler mismatch, speech/consent integration and ambient restart require product fixes and new tests on the resulting exact combined source. This support branch does not overwrite those files. These results establish no Android compilation, full JUnit/Robolectric, NewApi lint, GPU rendering, audible conversation, enrolled identity recognition, original-signed APK or physical Fold7 acceptance. Main, signing, installed app, update channel and user files were not changed.


## Final source-review handoffs (not additional runtime tests)

See `docs/research/omega/CONTINUUM-SUPPORT-TRIO-3E26-20261105.md`, the PCM intake, and ambient lifecycle matrix. `update-audit.json` records eight gate groups at 7ecdafac and the unchanged audited-file boundary to 08aa76e. `render-readonly-observation.json` preserves root's independent service/deploy readback. None of these source/API reviews extends the #543 test result to a later candidate, validates live APK bytes, or establishes physical Fold7 acceptance.
