# Cinema preview and Node contract correction

Owner: APK53 root integrator, PR #739. Base/source audited:
`a07b183b67b9c00192bb96b61a0002f74e23372b`, tree
`5551010f35d432a9a33790a81f538b0de6011311`.

The #715 preview review is a historical evidence mismatch. The old binding
contains helper SHA256 `a483d275ed9b3ebd7e15b21d9d112462c6c29334b4764d9cddc048e27c63e8c7`;
the later PNG-only helper at the audited source hashes to
`7e8a16677dd31bf9e94dd21f7032ca261cb3180439abfdce7c0c9eb547429db1`.
The old hashes/images are retained. The separate PNG-only check is now
`SOURCE_UNBOUND`, not retroactively attached to the newer helper. The existing
preview runner already hashes every actual compiled input, including its own
helper files when production sources come from another checkout; no runner or
renderer replacement is needed. No new preview/render execution occurred.

The historical Node receipt says 18 tests, not the handoff's unsupported 20.
The handoff now names the historical source explicitly. On the audited source,
the same five-file selection under Node 24.22.1 produced **17 PASS / 1 FAIL**:
`fold7-portrait-reference.test.mjs` expected an obsolete one-line click listener.
The actual listener retains navigation and records the activating control in
`try/finally`. Its source contract now requires both navigation and cleanup.
No Node-version defect was reproduced in this selection.

Reproduce the bounded selection from the repository root:

```sh
node --test \
  .github/continuum/code52-cinema-source-contract.test.mjs \
  .github/continuum/code52-cinema-prism-contract.test.mjs \
  .github/continuum/fold7-portrait-reference.test.mjs \
  .github/continuum/brain-orb-render-allocation.test.mjs \
  .github/continuum/owner-cockpit-voice-original-orb.test.mjs
```

Final corrected-source selection: **18/18 PASS**, no failures/skips. Source
contracts do not prove actual touch/render behavior; the existing native
fixtures remain the runtime gate. No Android source, workflow version pin,
package, APK or signing change. Full combined QA remains with the root.
Rollback: revert this test/evidence correction only.

## Full-Node follow-up

The root's full Node run on `1d61a30b99cdb3dd229ec0674a582d515dcdd7c1`
reported three stale source-contract failures. Its two additional failures
were `android-holographic-home.test.mjs` (the same listener) and
`code52-graphics-branch-intake.test.mjs` (the old shared animation clock).

The listener contract now requires origin capture, navigation and `finally`
cleanup. The graphics contract requires scale and thermal-budget gating,
pressed-only material time for tiles, ambient background time, and both
caller-side motion gates and clock forwarding. Two negative tests reject
11 mutated source snippets: three listener and eight clock/gate mutations.
They verify source-contract sensitivity, not Android runtime behavior.

Run the five-file command above with these two additional filenames:

```sh
.github/continuum/android-holographic-home.test.mjs
.github/continuum/code52-graphics-branch-intake.test.mjs
```

Final seven-file selection on Node 24.22.1: **31/31 PASS**, 0 failures/skips,
including both negative tests. Raw output:
`docs/evidence/cinema-preview-contracts-20261110/node31.txt`.
No product source changed. The root retains responsibility for rerunning the
full Node gate on the integrated APK source; 31 is not a full-suite result.
