# Storage and bounded cleanup

Final integration note: incorporated in PR #735; complete Android source compilation
and 155 selected integrated tests pass on published `02120f079ce1bdb1d3311f16f88d1a51bb7fbe09`.
The scoped results below are historical checkpoints; final scope/provenance is in
[the integration handoff](ANDROID-SYSTEM-INTELLIGENCE-HANDOFF-20261110.md).
Physical device validation remains NOT_RUN.

Integration owner: root / Android System Intelligence PR #735, target
`feature/android-system-intelligence-20261110`, then the owner's Cinema integration.
EXP-08/EXP-11; existing ToolGate remains the only tool-approval authority.

## Existing source audit

- `LocalModelHost` supplies `cacheDir/litertlm` to the native Engine. Its opaque
  cache has no audited file-format/ownership inventory. Model weights and native
  caches stay protected, even while unloaded; no guessed extension allowlist.
- `ContinuumOwnerUpdater` owns `cacheDir/owner-update` APK/download staging.
  These files stay protected. This patch does not change its existing cleanup.
- `.tmp` writers in `filesDir` are model, memory, receipt and research transactions.
  A temporary suffix is not evidence of disposable content. They are untouched.
- Renderer caches are currently in-memory Paths/shaders/layers. No synthetic
  disk-render-cache producer or artificial storage workload was introduced.
- No SAF scanner existed in the inspected production tree. Existing file tools
  use shell paths, and existing `DeviceStorageProbe` only reads volume totals.

## Implemented API

`SystemStorageInspector` wraps the existing Android context, ContentResolver and
grants. The root integrator must hold one instance and run it off the UI thread.

- `analyzeGrantedTree(uri, cancelled)` reads a specifically selected tree.
- `analyzeSelectedDocuments(uris, cancelled)` covers individually selected files,
  including Downloads where Android refuses the root tree grant.
- `retainDiagnosis(DiagnosticCacheRecord(...), cancelled)` retains only an actual observed
  numeric diagnostic summary for local history, max 32 records of 1024 bytes each.
  It rejects growth when full; it never evicts records silently.
- `diagnosisHistory()` rechecks the owned numeric records for local review.
- `previewOwnCache(cancelled)` produces an ephemeral, expiring exact file plan.
- `cleanupOwnCache(previewId, planDigest, cancelled)` requires the existing
  ToolGate's per-action user approval. It cannot accept a caller-provided path.
- `restoreOwnCache(undoId, cancelled)` restores only the last cleanup, while the process
  remains alive, for at most ten minutes, and never overwrites a target.

The new history contains timestamps, available volume bytes, app PSS bytes,
battery percentage and thermal status only. It is a small local cache, not a
benchmark baseline, memory store, original measurement report or speedup technique.
The integrator must keep active before/after baselines elsewhere. Retention may
fail independently; a failed cache write must not fail the diagnosis itself.
No data is generated just to demonstrate cleaning. Empty caches report zero.

## Safety and limits

SAF is read-only. No WRITE, MANAGE_EXTERNAL_STORAGE or broad media permission is
added. The picker requests local-only files; the adapter additionally accepts
only the three system-installed Android external-storage/download/media document
providers. Unknown and cloud providers are refused even in online mode. Existing
grants are checked again during operations; no hidden persistence of grants.

Default scan limits: 512 entries, 12 levels, 128 MiB cumulative read budget,
32 MiB per hashed file and 8 seconds. Cursor queries and opens carry a cancellation
signal. One shared single-worker lane performs provider IPC, with a bounded caller
wait of at most 12 seconds. Timeout/STOP revokes results before interrupting the
worker; a stuck provider keeps the lane busy, so replacements cannot accumulate.
One shared cancellation watcher first attempts descriptor closure, then provider
cancellation. Its thread count cannot grow if cancellation itself blocks. Pending
watchers are removed when a scan completes. Non-regular streaming descriptors are
refused. A malfunctioning provider may retain that worker until it returns; Android
provider cooperation and physical timing remain device gates. Final metadata and
EOF completion are followed by another deadline check before accepting a hash.

Equal positive sizes select candidates. Complete SHA-256 streams plus unchanged
metadata establish content matches; names never establish duplicates. Oversized,
changed, inaccessible and incompletely read candidates remain unverified. Multiple
references to one provider document are deduplicated. Personal files are never
deleted, moved or written. Local file names/URIs are excluded from tool summaries;
the full result belongs only in the local UI, never a cloud prompt or tool journal.

Own-cache cleanup is limited to exact numeric records registered by this producer
in the app-private ownership index. Unknown files, perfectly formatted unregistered
copies, model/update files, user documents, symlinks and replacement inodes are
protected. Plans bind IDs, SHA-256, sizes, timestamps and inode identity; they expire
after two minutes, are consumed once and are invalidated by a new history write.
All selected entries are revalidated before the first deletion. Iteration is bounded,
with cancellation checks and a five-second cooperative work budget.

Deletion requires `SecureDirectoryStream` support and anchored relative operations.
Each entry is moved to an unpredictable holding name, rechecked, then unlinked.
Unsupported providers return `SECURE_DELETE_UNAVAILABLE` without deletion. An
unexpected post-move change leaves the held entry protected and returns
`QUARANTINED_REVIEW_REQUIRED`; it does not risk overwriting another file to restore
it automatically. The result's `quarantineId` names the retained entry under
`cacheDir/system-diagnostics`; `.cleanup-<original-record-id>-<UUID>` also retains
the original record mapping after process death. The original name is
`summary-<original-record-id>.cdiag`. Inspect that exact protected entry and restore
only after verifying its contents and an absent destination. No automatic unknown
content recovery or deletion is claimed. Android support must be measured on the
actual device.

Retain/restore check STOP after synchronized admission and before each new file
mutation. Partial restore keeps the remaining undo set; ownership bookkeeping for
already completed writes is finalized to report the actual outcome. These ordinary
private-cache writes use path checks; only deletion uses anchored directory handles.
This does not claim protection against arbitrary malicious code with the same UID
racing all private-directory or file-descriptor operations.

`logicalBytesRemoved` counts verified record bytes; `availableBytesDelta` is a
separate volume observation that can be zero or negative under concurrent I/O.
Neither establishes CPU, latency or thermal improvement. Preview
`protectedEntryCount` counts bounded top-level cache entries and unowned diagnostic
entries, not recursively counted files inside opaque model/update directories.

## Validation and promotion

Focused host tests use real temporary files, hashes, replacement inodes, symlinks,
cleanup and restoration; document-provider cases inject bounded streams/metadata.
They are not real Android-provider or Fold7 tests. Standalone compile against the
installed Android API 36 jar is distinct from a full Android application build.

Final review run: 34/34 focused host tests PASS; four production files compiled
against the Android API 36 jar. Includes real hung-worker timeout/STOP controls,
replacement-worker refusal, final-metadata expiry, monitor-wait STOP, partial undo,
same-content inode replacement, symlink protection and content-duplicate negatives.
No full Gradle application build, real document provider or Fold7 test in this subtask.

Reviewer-proposed deadline case was also run against the earlier scanner from
`f45ce8733166758039caa906f1dc1ef724d8443c`: 12 test methods, one expected failure
because final metadata returned after the deadline but its hash was still accepted.
The corrected 34-test run includes the unchanged failing case. Baseline source
SHA-256: `f9070a0f6481f8087cba07c739db8888131c2f09d029093ca23a932537a59d10`.
The matched test SHA-256 is in `sources.sha256`; retained baseline output is
`docs/evidence/android-system-storage-20261110/review-baseline-junit.txt`.

Published source: PR #738 / `57f4e1c9bd9806e335331b536281383b5c36706e`.
Full tree `ac9950d6b9725d31077b009addcc176e9e2bba56` exactly matches local
`9a6ed9c1a510f09a36efb5a96ee2c9ef19238162`; differing SHA is connector commit
transport on the equivalent published parent. Root is incorporating these commits
in the single canonical feature PR #735; this support branch remains open.

Reproduce:

```sh
KOTLINC=/path/to/kotlinc \
CONTINUUM_JUNIT_JARS=/path/to/junit.jar:/path/to/hamcrest.jar \
ANDROID_JAR=/path/to/android-36/android.jar \
bash scripts/continuum-system-storage-check.sh /tmp/system-storage-check
```

Root acceptance: exact-source ToolGate denial/approval tests, Android integration,
local picker/revocation tests, no cloud transfer, and real Fold7 cancellation,
cleanup/restore/measurement verification. No main merge, signing or installation.
Rollback: revert feature integration; preserve existing numeric history if desired.

## Official references checked 2026-11-10

- https://developer.android.com/training/data-storage/shared/documents-files
- https://developer.android.com/reference/android/provider/DocumentsContract
- https://developer.android.com/reference/android/content/ContentResolver
- https://developer.android.com/reference/android/content/Intent

Android 11+ does not permit selecting the Download root using the tree picker.
The selected-document path is provided explicitly for that platform restriction.
