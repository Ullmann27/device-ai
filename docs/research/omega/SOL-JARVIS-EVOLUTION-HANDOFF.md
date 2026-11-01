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

## Checkpoint 3 — natural-name learning + immutable research snapshots

4110-parameter residual core reuses entity-proximity inputs for the asked natural person (opt-in only). New flag32 uses explicit checkpointv3; existing v1/v2/default serialization and algorithms remain unchanged. VERSION2 cannot silently load new flags. Exact finite-difference gradients, v1/v2 roundtrip and frozen v3 roundtrip pass.

Fresh authored TRAIN:64 examples, seed77,3200 updates, rate.025, no owner/blind data. On5 previously unused DEV examples: existing digit-only binding4/5, natural binding5/5; these are now consumed DEV. NLL1.45554→.01675 old /→.00957 natural. Run2.69s/2.48s on host, each checkpoint32932bytes. Both complete research checkpoints/results retained under `docs/evidence/jarvis-evolution-20261101`; no active weight file or path changed.

`LocationEvidenceSnapshot.freeze` copies≤4 validated sources/4096chars into an unmodifiable ordered snapshot bound to length-delimited id/revision/hash. `VerifiedLocationSpanResearch` restores a private copy of an opt-inv3 checkpoint, predicts exact source positions, and checks ALL immutable evidence independently. Ranker input capped1024UTF8bytes; oversized/malformed/missing/conflicting/invalid predictions abstain. This is a read-only adapter and remains SHADOW, not normal answer promotion.

Actual host checks:21 span/gradient/legacy/snapshot/provenance JUnit tests PASS;55 unchanged SWB1/decoder/memory tests PASS;8 proof tests PASS (new test executes the actual backend QA command with a minimal independent Node fixture; old invocation fails). Script `continuum-span-host-check.sh` reproduces pure JVM checks and optional authored experiments with the existing external toolchain jars; Render still required.

Independent blind corpus was frozen before candidate: SHA1762e7cb3d969802f2c507e90a5aee7ee725cfd021497ec7a01ce482def079e7,45 cases(20known+5missing primary,20 additional); developer has not opened it. Natural checkpoint frozen SHA c28c44c21e6b79aea5d13254760a0a782714e662294b366826d9d8c940697e92. Results pending, no performance/promote claim.
