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

## Checkpoint 4 — BLIND-A, adversarial repairs, actual Android lab connection

Independent45-case BLIND-A frozen SHA1762e7cb... was run once on original frozen candidates. Complete portable corpus/scope/source snapshots/row-level errors retained in `docs/evidence/jarvis-evolution-20261101/blind-a` (manifestcc2141760cbde9cbd550121fdd9ea11627011393425f892e8f2e8e7936653708).

|Frozen candidate|Primary20known|Primary5missing|Additional20correct|Total45correct|
|---|---:|---:|---:|---:|
|330 rule|20|5|11|36|
|ae2 rule|20|5|17|42|
|Trained RAW|19|2|6|27|
|Trained VERIFIED|19|5|19|43|

These ORIGINAL scores will not be overwritten by post-blind fixes. No primary benchmark CDX1 generation upgrade; this is extraction. Extended set still exposed unsafe answers, so primary threshold alone is insufficient for promotion.

Follow-up regression fixes (first RED, then GREEN): interrogative evidence is not an assertion; location conjunctions are not one place; residence does not prove current presence. A second independent reviewer found cross-sentence pronoun corrections/retractions and quotation scope holes. This static grammar now refuses entire sources containing unsupported anaphoric/retraction/quoted context; it does NOT claim to resolve those temporal/discourse problems. Snapshot now validates exactly its copied list (deterministic changing-caller regression); checkpoint size is bounded before copying and the same frozen bytes supply restore+hash.

Runtime: existing `AgentActivity.showCdxPromptTest`→`NaturalLocationLabComparison`→immutable snapshot→v3 learned ranker→independent verifier. Frozen32932-byte research model is packaged under assets/continuum-research, separate from ALL owner/live checkpoints. Bounded read consumes exactly32932 bytes plus EOF check. Reference answer is NOT an inference argument. Original CDX1/CSX1 outputs remain visible, side-by-side; no normal-chat routing, tool authorization, training or owner persistence added. This is SOURCE_INTEGRATED_LAB, not VERIFIED_DEVICE and not CDX1 repair.

Focused cumulative Kotlin/JVM:27 tests PASS, including legacy compatibility, exact gradients, all new negative regressions, asset identity/length checks, swapped names and original positions. Strict8 Node proof tests PASS. Architecture receipt now requires five additional actual span/snapshot/lab JUnit suites; no existing gate removed. Full independent Render at the next pushed SHA remains required.

Historical full Render b86f0a99 PASS independently read from HTTPS: saved STATUS and ARCHITECTURE-PROOF; Android/lint/backend/unsigned artifacts accepted for that SHA only.3303fdf itself subsequently reached Render live(dep-dbjfdendnq4s73dvpadg). No signed0.3.35 or Fold7 acceptance implied.

## Checkpoint 5 — second independent blind evaluation and causal counterexamples

Final answer-source freeze: d81be49e851a3c7f2196bcce288bc2fb40c7e22b. BLIND-B37 was independently authored, disjoint from A and64TRAIN, frozen before execution SHA4a8314061ca589d19b5043a4ef48869534b2ff8387e7ae787c143117dbb1ae4f. Portable bundle manifest3e267cf81d01b5b18e364186414050da6303ad35900d647547014c5cf06fa399 retained under blind-b, all errors included; no further model/verifier training/tuning.

B results:330baseline29/37; final deterministic37/37; learned RAW23/37; learned VERIFIED36/37(19/20known+17/17missing,0wrongaccepted). The verifier rejects one shortened multiword place, so deterministic recall remains stronger. Strict no-regression vs rule fails and learned promotion stays blocked. Wilson95 known76.39–99.11%, missing81.57–100%; exploratory paired gains/losses rule8/0(p.0078125), verified8/1(p.0390625). Small authored corpus, no population/AGI guarantee. A's original45results remain unchanged despite later hardening.

New causal result: exact role-binding bucket collisions under actual SWB1 mean-of-ngram representation (Perg/Berg, Bern/Kern, Salzburg/Würzburg swaps, same asked person). Equal75/76/88-bucket sets below128cap, vectors differ only≈3.7–5.5e-18rounding. Same8-byte CDX context and same first probabilities; trained reference free output Wien for both sides. Frozen position-aware span model copies6/6 correct audit spans, independently verified. These are authored causal diagnostics, not blind scores. Full distributions/teacher-forcing/free outputs plus mathematical limits in CDX1-CAUSAL-COLLISION.md and cdx1-role-collisions-with-span.json. The owner's checkpoints were not accessed.

Host-only resource run (JVM21amd64,25warmups,200lab compares incl restore/snapshot/verifier): P50≈1.52ms,P95≈5.46ms,457310thread-allocatedbytes/compare; repeat P95≈4.99ms/459011bytes. Heap before/after is NOT peak RAM, allocations are NOT retained/native memory. Checkpoint32932bytes. Fold7 RAM/native/energy/temperature/background results UNAVAILABLE (no connected device/adb or paired PC endpoint). Existing battery/thermal guards unchanged.

## Checkpoint 6 — exact unsigned binary identity gate

New real aapt2 readback of the built unsigned release APK before STATUS/proof. Schema3 requires actual package at.heinz.deviceai.continuumlocal, code36, name0.3.35 and retains hashed badging+artifact hashes. Wrong package/code/name, absent/duplicate/prefixed metadata fields are rejected. New regression first fails on old proof (8/9); corrected parser with real attribute boundaries passes9/9; independent review reproduced prefixed-field defect and fix. No signing, key creation or publication.

Independent full Render d81 is PASS at dep-dbjfs4ajnfac73fsi2l0,9requiredJUnit suites/38executed requiredtests, AndroidJUnit/lint +Node/backend+unsignedAPK/AAB. Saved actual HTTPS receipt/status; d81unsignedAPK54553583bytes SHAe6b3fc738fc9ec9c9888af8bd11e2699f3faba5acaaed15ec74083a20c882006. This earlier receipt has schema2 and does NOT attest to the new schema3 binary gate. Final source SHA needs a new full Render deployment after commit.

## Final integration / next session

Detailed actual callers, statuses, ten requested areas and exact next actions: [SOL-JARVIS-EVOLUTION-MATRIX.md](SOL-JARVIS-EVOLUTION-MATRIX.md). This round changed answer functionality only inside the existing explicit research lab; normal chat/owner state/live SWB1/CDX1/Gemma weights/permissions/signing are retained. New research checkpoint is separate and opt-in. Do not confuse local lab integration with normal answer promotion or device proof.

Use PR331 and its exact latest HEAD/deploy. Coordinate330/329 integration via same-repo isolated branch, never push over their branches. Next science: role/position-aware conditioning and multiword span recall against the stronger deterministic baseline; freeze another unseen corpus before changes. Do not rerun the27blind parameter variants, train on A/B, claim extraction as generation, or silently replace model schemas. Next normal runtime: existing active/revoked Core/EWM2 facts+source revision recheck, intent/session identity and adverse chronological dialogue tests, then independent Android/Fold7 acceptance. Next worker/fabric: existing typed owner-scoped chain and signed node interfaces, actual connected endpoints required. Originalsigner/publisher/device gates remain open; no signed release was produced.

Final portable rerun:27JUnit PASS; fixed3200-step research checkpoint reproduces byte-identically(c28c44...), reference2520-step diagnostics reproduce, no additional candidate tuning. All three host-resource samples are retained; last200comparisons P50=1.9116ms,P95=3.7769ms,462708allocatedbytes/compare. Do not select the fastest run or translate these to Fold7. Final source commit is intentionally recorded in the PR/deploy receipt after this commit, avoiding self-referential source metadata.
