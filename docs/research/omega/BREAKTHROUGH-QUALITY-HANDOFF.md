# Independent breakthrough QA — 2026-11-03

Base:64ec06a73f50cae9ee49035c840710d87777d50f. Current prior milestone evidence is in Quality PR396 commit e934ba31; unsigned Code40 artifact remains unchanged. This is a fresh independent QA branch, not an implementation lane.

Frozen independent synthetic cases:64 neural location/role/evidence cases,12 invalid transport controls,24 owner-context cases,16 replay state programs,16 research claim/prompt programs,12 executor receipt/identity programs. Public commitment `docs/evidence/breakthrough-quality-20261103/FREEZE.json` fixes every payload hash and domain/count before candidate source/test/output inspection. Authors supplied schemas/domains and status metadata; these are independent unseen-to-author synthetic challenges, not external population benchmarks. Corrections/revocations are explicitly separated stress/OOD groups for the static location model.

Cases and expected answers are sealed in `frozen-cases.enc` during candidate freeze. Local working originals and temporary seal key are restricted to `/workspace/continuum-breakthrough-private`; no owner data or production credential is involved. Candidate authors receive domain/schema/count/hash only. After their exact source/checkpoint freeze, Quality executes them without giving labels to authors, then publishes all fixtures and result details. Do not inspect candidate source/tests or unseal holdout for authors before that boundary. The ciphertext and plaintext archive hashes preserve the same frozen artifacts across this short sealed phase; no case retuning after scores.

A preparation-only transport-fixture generator index error occurred before the full freeze; immutable already-written neural cases were retained byte-for-byte when generation resumed. No candidate source or output was read during preparation.

Quality owns shared `/workspace/continuum-build-env/env.sh`, authentic Gradle/SDK/JDK/Kotlin harness and one-at-a-time heavy Android gate. New diagnostic runner paths will stay in this lane. Current source/features, APK signing, production promotion, main and device actions remain untouched.
