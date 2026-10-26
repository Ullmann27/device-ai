# Continuum research feedback — cross-process review safety (2026-10-26)

## Source and scope

Repository Ullmann27/device-ai, base PR #175 at 08bfe94f21a9741a2144e9e4bae72b341fa68332.
Review branch chatgpt/continuum-feedback-process-safety-20261026. This is a research-only safety fix to the existing synthetic feedback ledger, not a new learning system. Main and the frozen v25 release source manifest, signing settings, owner data and model weights remain unchanged.

## Gap and repair

PR #175's monitor serializes threads inside one JVM, not independent worker processes. A second process could validate the same OBSERVED review digest before the first one persists CONFIRMED. The new ResearchFeedbackFileLock uses an OS file lock alongside its process-wide monitor for complete propose/review/read/fingerprint transactions. The existing FileLearningExperienceStore, status semantics, source/provenance tags and binary format remain unchanged. An internal unlocked candidate-list method avoids nested OS lock acquisition. On lock failure there is no fallback to unprotected access. Direct symlinks on root, feedback directory and lock file are refused.

## Regressions and observed limitations

- New ResearchFeedbackMultiprocessTest: two genuine JVM children race on one candidate digest after a start barrier; exactly one confirmation must persist.
- Additional checks: interruption releases the lock; root and directory symlinks cannot redirect the review ledger.
- Existing scripts/continuum-feedback-review-jvm.sh now compiles both feedback sources and runs both JUnit classes with source hashes.
- Development-host smoke: new helper compiled with installed Kotlin compiler; separate OS-process interleaving check PASS; exception-release PASS; symlink-root rejection PASS. This is a limited native test of the helper, not of the complete ledger.
- Full updated JUnit source graph, Android Gradle, Fold7, Termux and APK signing: NOT_RUN here. No live owner feedback, no model weight training or unlearning was performed.

## Next verification gates

1. Run CONTINUUM_FABRIC_JVM_DEPS=<existing-Kotlin-JUnit-jars> bash scripts/continuum-feedback-review-jvm.sh <external-output-dir> with the true repo source and inspect all JUnit results.
2. Review any parent-directory symlink policy for app-private storage and execute Android SDK tests before considering this patch for the app.
3. Preserve the old release manifest; the present review source must not be silently signed or promoted.
4. Subsequent integration: existing VH-SR candidate-veto is research-only, CCC1 is the sole authorized independent repair path, and owner-approved feedback must be authenticated before it can be fed to any training decision. Keep learningEligible false until actual independent authenticated semantic evidence exists.
5. Keep PR #174's rejected dialogue selector and ineffective SWB/CRE checkpoints out of the productive branch.

## Rollback

Discard or selectively revert this draft PR. No migration was introduced; only an empty .review.lock coordination file is added within the dedicated feedback research directory.
