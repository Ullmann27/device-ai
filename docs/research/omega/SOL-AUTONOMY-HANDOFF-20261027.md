# Continuum autonomy and contrastive semantic learning

START_CHECKPOINT / RESEARCH_ONLY. Own branch sol/continuum-autonomy-contrastive-20261027, base PR177 e736664abb91542da7e6cc7dba91e1b1473ebb3e; PR176 verified3585d99cc71b9f9d238db317256e7461f2110c6d. Mergebase ec4b4caecd64835e56af924dae1f7ad825059bfc. PR176 has2143changed files relative to this old Node-only base; no blind full merge. Relevant EXP01/04/05/07/12; current contract/register and Issue41 read from existing advanced checkout, as PR177 does not yet contain these files.

Initial exact Node24.19.0 checks:18PASS/0FAIL. First actual CLI worker cycle and its limitations are being investigated. Safe process tests and new regression cases follow. Existing posted 52transfer examples are already observed and will not select a strategy. A new synthetic train/validation/final suite will be frozen before any new candidate training.

Persisted SWB1 state must remain untouched: known public snapshot hash5e577c21b22c6b31ff71274640d45f8900ca646e370bef6179ab608ffb992cd1;12rounds,2accepted,13000updates,45public observations. Any state extraction uses a read-only local copy outside Git; publish only the already public weight checkpoint and synthetic fixtures/metrics, no private state.

GitHub Actions current read-only evidence again reports failed watch job121823529657, zero steps, repair/publish skipped, log404BlobNotFound. Root cause UNKNOWN; account/runner APIs will be checked if available, no blind retries or billing changes.

Next: reproduce/fix native worker report parsing, process interruption and durable lock/restart behavior; true failed→successful cycles; investigate positive-only replay with source-compatible gradients and independent new evaluation. No main/release/state/owner/APK/production-weight changes. No paid APIs or new model downloads.

## First native repairs checkpoint
Four initial full-suite failures reproduced: duplicate runProfile import, needless CRE1 single-word re-normalization rounding, inherited NODE_TEST_CONTEXT suppressing nested reporter output, and Node-only Teamleader discovery trying absent app/src. Exact targeted rerun34PASS. Native worker process RED reproduced failure counters missing and duplicate summaries accepted; implementation now explicitly requests TAP, removes inherited parent context, records raw/hash-bound evidence, handles SIGTERM and OS-flock-serialized persistent process identities. First9helper/process checks PASS (real failed→successful fixture cycles and signal restart). Further scheduler-idle lease, timeout, STOP and crash tests pending; not yet a full lifecycle acceptance.

Actions read-only check annotations now identify an account-level start block: recent payments failed OR spending limit needs increase. This disjunction is GitHub's exact message; which case applies remains unknown. Self-hosted runner listing403(integration scope). No billing changes or retrigger. Raw annotations saved.

## New evaluation preregistration — before new model trials
Public authored logical fixtures now separate TRAIN(20intent/72semantic/2dialogue), VALIDATION(12/36/2), FINAL(20/60/6). Final domains and names differ; logical construction families overlap and the author knows the fixtures, so this is not an independently blinded certification. The old52transfer cases are never used to choose a new policy. The new final set remains unscored until a candidate/model-policy freeze.

Fixed profiles: CONTROL_REPLAY, VERIFIED_BALANCED, HEAD_ORTHOGONAL, SEMANTIC_ONLY, SWB2_SHADOW;8000update budget;1000-step checkpoints;30sCPU wall limit/trial;3fixed schedule seeds73/101/211;every trial starts at the same immutable5e577c21SWB1 bytes. Selection sees only new validation plus old protected TRAIN/VALIDATION/ANDROID retention gates. Require at least2additional correct semantic validation cases, no correct-count regression in protected abilities,loss tolerance0.015. Final evaluation never selects a policy. SWB2 feature ABI is research-only, never a silent Android migration. Counts/loss/confusion and snapshot hashes mandatory; no productive state changes regardless of result.

## Worker lifecycle follow-up
New real-process RED cases found3additional defects: scheduler released its checkout lease while sleeping, output storage exceeded2MiB, and cycles/ could be redirected into source. Fixed and rerun14helper/lifecycle tests PASS. Timeout and successful retry, SIGTERM, SIGKILL with verified residual-child cleanup, duplicate starts, prompt STOP during idle, output bound and path redirection all execute actual OS processes. POSIX flock plus own-process /proc identity required; unavailable capability fails closed. Heap limits are per Node process, concurrency2, not a claim of aggregate RSS or Android battery enforcement. Native whole-checkout cycle next.

Saved checkpoint independently read from continuum-state230cfb88732349f25530640c720cb6cf36513a88: public binary matches exactly32960bytes and known5e577c21hash.12rounds/2accepted/45public observations unchanged. Full state is not uploaded; only permitted public weight bytes and metadata will be published.
