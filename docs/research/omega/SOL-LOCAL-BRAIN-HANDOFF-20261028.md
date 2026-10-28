# Continuum Lokal — bounded import candidate

Own branch research/continuum-local-brain-sol-20261028 created and independently verified at f39fbe31a677d8c5020df11805b901abc4cedb73. Parent PR205, main, installed apps, weights and signing keys unchanged. Target remains at.heinz.deviceai.continuumlocal; old at.heinz.deviceai.mentorpaired is not an update target. Existing local signer must be retained.

## Targeted architecture findings
LocalModelHost already owns one catalog/runtime per process. AgentActivity commit360bfe62 already restores picker metadata and reports import progress/errors: do not duplicate it. Catalog already enforces size/hash and native text/CPU admission. Current owner reports no model loaded; actual model/532 private records/device state are unavailable here. The complete brain call graph was not reaudited. Existing SWB1/CRE1/CTX3/CDX1/knowledge archive/ToolGate/CCC1 stay unchanged. Three open transitions: real picker->verified native model load; authorized private knowledge->model-backed answer; model candidate->repeatable independent local repair.

## Candidate, not installed fix
The patch adds interrupt checks before/after stream read and IOException after16 consecutive zero-byte reads, reset by positive progress. No public API, byte limit, hash validation, inspector or persistence tail changes. It cannot forcibly cancel a provider already blocked in read(), is not a total time limit and is NOT an established cause/fix of the actual Fold7 picker symptom. No stream, model, token or signing data is published.

Eight JUnit methods are provided under experimental/; they must be placed in the existing app test source set only on a review checkout when applying the patch. They use a synthetic injected inspector, not real LiteRT inference. This WIP commit stores a patch, does NOT apply it to production source and does not make PhaseB complete. PhasesC/D/E remain open; no simulator, new scheduler, model downloads or training were added.

## Actual checks
Original catalog Git blob0581247a8f97dec795099214b91ac1b747b70c6f verified byte-for-byte against GitHub. git apply --check EXIT0 and actual apply in isolated selected-file tree produced candidate blobbdcf0c14eb2e204176044d58084dea1a2d762b3b. Eight static Node source contracts PASS. Java and kotlinc unavailable: JUnit, Kotlin compile, full Android regression, SAF/lifecycle, Fold7, real Gemma and resource measurements NOT_RUN. No test behavior or device success inferred from static checks. Source/test/patch/report and raw static proof are in the separately delivered source archive.

## Next executable handoff
On an authenticated existing JDK17/SDK36/Gradle8.11.1 checkout verify current base and original catalog blob, review/apply model-import-bounds.patch, place LocalModelImportStreamTest.kt under app/src/test/java/at/heinz/deviceai/localmodel/, then run existing LocalModelCatalogTest plus new LocalModelImportStreamTest with the documented native ARM64 toolchain flags. Reproduce baseline vs candidate; fix real compile/test failures, then one necessary full regression. Keep NOT_RUN until actual results exist. Do not install an APK or sign without separate owner approval. On successful model import, test actual hash/model identity, native inference, correction and restart. No holdout threshold changes or automatic model promotion.

## Source hashes
Candidate catalog SHA256 0ca6cdff28632835f60185ef4339957c9cd5dc1a3395dbb697a7257ea2e284de. Test SHA256 3c2ff45b841f2c1806fd81c67552cfef9c2a6b57af1690332a45c8662d98dfcf. Before publication no source commit existed; use the actual returned commit/PR identity, never invent one. All files remain plain editable UTF-8. Rollback this research patch only; no reset/force-push or changes to existing owner data.

## Follow-up integration on the same Draft PR (28 October 2026)

This report's earlier "candidate only" wording describes the ORIGINAL WIP commit, not the current integrated branch. Later authorized commits applied a reviewed bounded streaming patch directly to `app/src/main/java/at/heinz/deviceai/localmodel/LocalModelCatalog.kt` (without changing catalog IDs, the SHA-256 gate, native `ModelInfo` inspection, or atomic metadata persistence). The Kotlin tests were added under the actual Android JUnit source set `app/src/test/java/at/heinz/deviceai/localmodel/LocalModelImportStreamTest.kt`.

Two additional interruption checks now reject cancellation after the stream-copy phase and after native inspection, before the immutable accepted model is committed; a ninth Kotlin test checks that an interrupted native-inspection result never publishes a model. The original isolated patch artifact and eight-JUnit research fixture remain unchanged for auditability and are **not** the current release source of truth.

A separate local-update entry point was added: `scripts/continuum-fold7-update.sh` with `scripts/continuum-fold7-update.test.mjs`. Its checks cover non-production branches, Git fast-forward only, an existing local P12 and certificate, cross-runtime parity and full Android unit/lint regression before using the signed local APK builder. **Actual local test execution:** all eight Node wrapper tests passed on a separate Linux shell with Node 22.16 and Bash, after Git blob-hash comparison of both downloaded-exact source texts (`0eddc65508e5cff1341cf97eddd8e8deea5e5bbb` and `b6e73a33de9384957886723b5acf56cb3a120253`). This validates wrapper control flow and Bash syntax, **not** an Android build.

**Remaining NOT_RUN as of this report:** 9 Kotlin/JUnit stream tests, Android Kotlin compilation, full Fold7 regression for the changed code, local APK version 27 release, native LiteRT-LM import, and genuine Gemma inference. No Android installation or model/owner data has been touched. The Draft PR MUST remain unmerged until the device-run gates pass. The real Fold7 picker problem's root cause remains unproven.
