# DELTA follow-up assessment

Exact follow-up source: `8ab7b6811ec3713bc8b0ce16e48394e9c27fe928`.
All **42/42 actual execution/receipt observations pass**. The 17 reported
metadata, schema/authority, and duplicate resolved-target findings are closed
under the unchanged frozen cases. All eight legitimate receipt checks continue
to pass. Caller HEAD, status and worktree lists were preserved for all 41 receipt
calls. No remaining actual execution/receipt blocker was found in this set.

The unchanged 46-case harness reports **44 PASS / 2 FAIL** and exits 1. The two
failures remain `D18-truncated` and `D18-unclosed`, both explicitly parser-only:
malformed XML containing a complete testcase still produces one parsed case.
These expectations were not weakened, skipped or relabeled as passing. They are
pre-existing parser diagnostics, not demonstrated bypasses of the actual fixed
Node profile. Report absence remains unexercised through that profile.

This is a **consumed-case follow-up regression assessment**, not a new blind
evaluation. The author saw the first assessment before implementing this repair.
The frozen manifest and harness are unchanged from the first assessment at
`b1bca66f54f80ef627ab17201e29335ee3210f86`.

| Observation scope | Baseline 64ec06a7 | Initial 799dd763 | Follow-up 8ab7b681 |
|---|---:|---:|---:|
| Actual fixed-profile / receipt | 13 / 42 | 25 / 42 | 42 / 42 |
| Parser-only diagnostics | 2 / 4 | 2 / 4 | 2 / 4 |
| Combined unchanged harness | 15 / 46 | 27 / 46 | 44 / 46 |

The source repair compares supplied ancillary run claims with actual observations,
rejects duplicate resolved target identities, and applies the same receipt
envelope checks in commit and patch modes. It does not authenticate arbitrary
worker text or add an OS sandbox. The actual execution evidence remains the
verifier's fixed Node/JUnit runs in detached worktrees.

Legacy compatibility was checked separately on this same exact source using its
real-profile test cases. **11 selected tests pass, 0 fail, 0 skipped**, exit 0.
The selection includes the ordinary genuine commit repair, patch repair, and
the omitted-metadata/unique-alias positive, plus eight coverage tests selected
because their titles also contain "patch modes". The omitted-metadata case deletes
command, exitCode, skipped, timedOut and cancelled from both claimed runs; deletes
red.passedTargets and the three authority flags; and uses a unique classname
alias. Both entry points still return VERIFIED. It retains schema/version,
required counts/failing arrays, GREEN passedTargets, and commit SHA bindings.
These are author-supplied regression tests independently executed here, not newly
blind challenger cases.

Caller-contract source inspection: `Ccc1PatchReceipt.kt` emits schema/version,
false authority flags and the complete run fields. Its strict parser still
requires those full Kotlin fields. The optional omission compatibility checked
above concerns direct JavaScript verifier callers; this does not claim the Kotlin
parser accepts an incomplete receipt. Source search found the JavaScript API
references in its tests and CLI, not an independently executed Android caller.
Android/Kotlin integration remains with the separate QA gate.

Commands (Node v24.19.0):

```sh
node tools/delta-challenger/challenge.mjs /workspace/delta-challenger-followup/.github/continuum/ccc1-redgreen-verifier.mjs docs/evidence/delta-challenger-20261103/followup-results.json
cd /workspace/delta-challenger-followup
node --test --test-name-pattern='Delta follow-up preserves|genuine RED|patch mode' .github/continuum/ccc1-redgreen-verifier.test.mjs
```

Source/evidence binding:

- Verifier SHA-256: `c2d2b16a36f3c43179c589d94ed514a677aa6d23d56bc234f871531c8048c78c`.
- Unchanged challenger harness SHA-256: `79c1dea750cc704049d0a5cea91af54d80932a56a49a0e965a409cba68c50cdb`.
- Source regression test SHA-256: `810d9a9eed5767a7fabbc816c0e147929a0c66e7e5e1e6a02d7d76fc045dbd82`.
- `followup-binding.json` records exact source commits, artifact hashes, caller
  source hash, counts, commands and the two remaining diagnostics.
- `followup-results.json` preserves fixture commits, source/test/patch hashes,
  actual profile observations, every complete receipt and each attestation.
- `followup.log` and `followup-legacy.log` preserve execution output.

No production code, frozen expectations, main branch, release/signing/install,
owner data or productive weights were changed. Independent QA retains the full
integration gate. Rollback of this evidence is to omit this challenger branch.
