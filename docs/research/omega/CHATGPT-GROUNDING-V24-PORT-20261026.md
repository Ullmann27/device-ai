# EXP-04/06/07 grounding candidate integration — 2026-10-26

Status: WIP / UNVERIFIED ON NEW V24 HEAD. This is a *separate review branch*; it may not ship in the signed owner app without fresh Kotlin/Android/Node and independent 60-case holdout tests.

Owner source v24 branch parent: 38f978e29e345c49f4f50adffe1e940074926a26. Source PR153 frozen candidate: 4bf74b1b827aab56bbe03e032c21c03c508484c8 (historical final branch b41408b3cae889b4727b00648a0340419298b1f0).

Independent exact-context dry-run on current code: 4/4 .github/continuum/dialogue-retrieval.mjs hunks and 3/3 ContinuumMemoryDialogue.kt hunks matched exactly once; no fuzzy or blind application. The 3 new testcase/source-fixture files must be copied by identical Git blob SHA. Do not rewrite owner memories or learning weights.

Historical targeted source tests 91 focused + 16 broader Node and 42 Kotlin passed at PR153's frozen candidate. Do not apply these results to the current port. Independent 60-case prospective evaluation remained NOT_RUN due owner stop. Current port native tests and device Gradle are NOT_RUN. Source freeze/manifest of v24 PR171 is NOT VALID for this experimental port: do not sign or merge this branch into any release without new manifest and approved builder.

Experiment gates: Exact ID, subject, relation eligibility; no new authority from model text; preserve CRE1 pool and revocation. Rollback: revert experimental port commits on its own review branch; no force-push or changes to main.
