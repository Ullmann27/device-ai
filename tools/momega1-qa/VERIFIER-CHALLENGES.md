# Independent MΩ-1 verifier reserve

Frozen before BETA candidate inspection. Existing PR400/407 cases are exposed regressions, never unseen tasks. New observations will be added to separate QA-only tests and reported individually:

1. Duplicate source files with same basename but different directories retain independent test identity through red/green evidence.
2. Actual child stdout containing fake XML must not add target identities or hide a real failure.
3. Real nonzero process termination after passing test events cannot create GREEN acceptance.
4. A new failure outside targetTests must reject an otherwise repaired target.
5. Parent/child suites with delimiter-bearing names must not collide with a different nesting structure.
6. Unknown receipt fields that claim authorization must never alter the verifier result or owner scope.
7. Receipt nested commit/hash substitutions must be rejected for each documented alias, including optional metadata presence.
8. Candidate test source changes disguised as a rename must not be treated as unchanged acceptance tests.
9. Empty/disabled target test results must not establish RED→GREEN.
10. A symlink temporary root should not reject a valid fixed-profile repair or change its stable identity.

Items7/10 overlap public PR400 feedback and are explicitly public regressions. Each result will distinguish real execution from parser-only diagnostics. These verifier probes are separate from the 12 unknown model repair tasks; no inference score comes from this reserve.
