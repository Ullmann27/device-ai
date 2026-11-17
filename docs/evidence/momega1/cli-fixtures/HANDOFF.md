# Final QA CLI admission checker repair

Base Chief87839335. Scope: scripts/continuum-coding-brain.test.mjs,
scripts/continuum-m2-model-preflight.test.mjs and this new evidence directory.
No production source, runtime, schema or wiring file changes.

The current production CLI coding command calls queryPinnedLocalCoding, whose
first guard requires explicit model/runtime/manifest paths, runtime hash and kind.
Missing pins fail BLOCKED_LOCAL_MODEL_UNAVAILABLE before authentication, request
binding or legacy model-alias configuration. There is no loopback/cloud fallback.
Therefore the three older LOCAL_AUTH_REQUIRED/Gemma/unspecified error assertions
were stale. This inspection found no production defect for the tested scenarios.

Tests now explicitly remove pin variables to avoid host-environment dependence
and assert exact blocked stderr, exit1, empty stdout, no spawn errors/signals and
no private prompt/key echo. UTF-8 input within the CLI byte budget must reach
model-unavailable admission; an oversized multibyte input must instead fail
LOCAL_PATCH_REQUEST_TOO_LARGE. The pure M2 negative matrix still checks every
specific Gemma/unspecified/format/hash/consent refusal, and the separate harness
Gemma gate remains unchanged. No broad allowed-error regex was introduced.

Unchanged focused RED:32tests,29PASS/3FAIL/0SKIP. Focused GREEN:32/32PASS,0SKIP.
Command: node --test scripts/continuum-coding-brain.test.mjs scripts/continuum-m2-model-preflight.test.mjs

original-three-failures-645b2215.tap retains the exact three original QA blocks;
manifest.json pins the original full raw log, old/base test blobs, unchanged
production blobs, patched test hashes and RED/GREEN evidence. These are CLI
admission contracts, not actual inference, model quality, Android or device proof.
Root and QA independently review and integrate. No heavy QA job was started.
Rollback: revert only this test patch.
