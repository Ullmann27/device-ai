# Fold source contract for Compact

TEST_ONLY. Base `975bea824077e5e21d21367b1499850fc800fd87`. Only the Node source-contract file changes outside this evidence directory; production and Android UI test files are unchanged.

## Source-bound correction

The full Node run's sole failure expected the previous direct `reference(...) else reflow(... includeBottomNavigation)` expression. The actual pinned `JarvisShellLayout.create` still resolves the shared `JarvisWindowProfile`, then obtains the original reference composition and applies `compactReference` only when requested; otherwise it returns the same reference result. The non-reference branch forwards `compactPresentation` into `reflow`. Source blob `ddd7f939420a88815be4824024d7586140663720`.

The revised assertions require the exact new branch syntax, including `if (compactPresentation) compactReference(it) else it`, and require the compact flag as the final reflow argument. Additional exact assertions retain the `compactPresentation = false` API default and the flag's forwarding from `forContent` into `create`. The shared-profile decision assertion and all existing width/font, one-rail, source, geometry and live-surface checks remain. No unrestricted wildcard or assertion removal substitutes for either branch. Updated test blob `94cc90cf330e800f8bab445da2a70ba112b579b6`.

## Actual focused verification

Command: `node --test --test-reporter=tap .github/continuum/fold7-portrait-reference.test.mjs`

Node `v24.22.1`; **5 total / 5 PASS / 0 FAIL / 0 SKIP**. Raw output: `focused-node.tap`, SHA-256 `ba3c719814bdbcb2c2583d5947c5545dd4e57157a0d06f0b809f7a558d526030`. The 11 source fixtures were hydrated solely from the pinned base commit.

`git diff --check`: PASS. No Gradle/Android run in this helper; no full Node rerun here. Root owns the final combined gates.
