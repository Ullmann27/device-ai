# Sol JARVIS Evolution — source-only research, 2026-11-01

Base: PR330 `3fdf244ea6f5f3e1386e0653538d06daddc616ad`, parent release328 `081065b7d3628bf6a51239360f1e377f20df3fff` (0.3.35/code36).
Branch: `research/continuum-jarvis-evolution-20261101`. Original317 branch and all 27 negative CDX1 experiments retained unchanged. No owner facts, production weights, signing or release publication changed.

## Checkpoint 1 — VERIFIED_HOST_QA (focused Kotlin/JVM only)

Four new tests fail against330: verifier accepts conflicted/negative evidence or incomplete location, producer accepts embedded speech and presence as residence. Fix anchors assertions at clause boundaries, preserves query relation, and independently reparses **every** source/clause before validating exact source/revision/hash/full-span offsets. Verifier does not call producer or assume its candidate is trustworthy. Invalid UTF-16 is rejected before UTF-8 hashing.

Nine JUnit tests pass (five existing, four new), actual Kotlin2.0.21/JVM21; `git diff --check` clean. Not Android/device acceptance. Scope remains narrow German explicit names/locations; temporal, indirect speech and ambiguous facts abstain. This is SPAN_EXTRACTION, no CDX1_GENERATION improvement/promotion.

## Exact current QA preflight

-328 `081065b7...`: Render `dep-dbje1mdh3lcs738c0i0g` live.
-329 `3064ceb85a84b24dd1d2a4d36c9059fccbf68d24`: Render `dep-dbjfc2dg1s2s73fj9keg` failed at backend TAP receipt (not an accepted architecture proof).
-330 `3fdf244e...`: Render `dep-dbjfdendnq4s73dvpadg` pending at preflight; earlier live1a52606f is not acceptance of3fdf.

## Next work / rollback

Independent reviewer holds a previously unused frozen natural-location corpus; do not use it for training or tune after seeing results. Next: immutable bounded evidence snapshot, controlled residual-span research adapter, counterfactual conditioner measurements, fix329 reporter invocation without weakening strict receipt validation, cumulative independent Render QA. Default Android answer path is unchanged at this checkpoint. Revert this isolated commit to restore330 behavior; never merge automatically to main.

## Checkpoint 2 — architecture-proof integration

Reviewed329's four-file delta and merged exact3064ceb8 on isolated331, preserving330 whitelist and adding only331. Root cause of329 failure: `npm test -- --test-reporter=tap` places reporter after backend test globs on Node24, emits Spec with exit0. Fix invokes `node --test --test-reporter=tap test/*.test.js` in the same backend block. Strict TAP/JUnit verifier remains unchanged. All7 proof negative/positive tests PASS on Node24.19.0; independent reviewer replaying complete backend. New Render cumulative QA required, no use of329 old PASS.
