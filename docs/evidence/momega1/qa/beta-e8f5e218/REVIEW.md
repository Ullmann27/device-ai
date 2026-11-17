# Independent verifier acceptance at e8f5e218d06d7eb05446b399106119146030143b

Immutable detached checkout under QA-owned build environment. No feature edits.

- Frozen real receipt probes22/22, exit0, caller state preserved.
- New canonical verification probes8/8, exit0: real RED/GREEN, skip/name loss, new non-target failure, process nonzero, deadline, counterfeit executor, malformed proposal identity. Checks exact error reasons and uncloneable evidence brand. Trusted synthetic host fixtures only, no model.
- Exposed PR40026/26, exit0. Exposed PR40744/46, exit1; two longstanding malformed XML parser-only diagnostics remain. Never relabel these failures PASS or unseen.
- Actual existing Docker fixed executor, runc, local FROM-scratch image: same source/patch/task positive control verifies under normal temporary root. Under symlink TMPDIR, it rejects with BASELINE_TEST_IDENTITY_MISSING. This is a confirmed fail-closed availability defect; BETA notified before integration.
- An initial single-verifier-file import attempt failed because new canonical verification imports require the full checkout. Raw import errors retained; the complete immutable checkout was then used. Import errors are harness setup errors, not candidate failures.

No inference, Fold7, worker authenticity or release acceptance follows from these fixture results. Freeze manifest for12 unknown model tasks remains unchanged and unevaluated by a model.

Post-freeze source-consistency challenge: patch mode accepts two mutually consistent but false green source claims (`greenSha` plus `green.sha`): one names the old base commit and one names a nonexistent40-hex commit. Commit mode rejects both. Actual4 observations:2 pass/2 fail, exit1. These are newly authored review probes, not pre-frozen blind model tasks. BETA asked to bind optional green-source claims to the actual patched tree or reject unsupported source claims. Acceptance remains pending this finding and the symlink path correction.
