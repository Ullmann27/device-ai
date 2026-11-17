# Original frozen real-model benchmark

**4/12 independently verified repairs; 8 abstentions. The 8/10 goal is not met.** No successful restart recall on this source.

Implementation: `19474261c15a3503a9f041244b4b4c3eaed4636f`. Official Qwen2.5-Coder-1.5B-Instruct Q4_K_M and independently rebuilt/reviewed llama.cpp pins are recorded in every request. Frozen manifest: `1055d66300b0f4664978f103d1e5adb73a9619a337539ad41e898b7217a92407`.

20 reserved calls; 18 admitted genuine executions. A schema-accepted proposal is not necessarily an applicable or correct patch. Two nonzero child exits have no admitted model observation and remain failures. No extra retries, teacher patches, model tuning, source changes or test edits were used.

| Task | Verified repair | Calls | Admitted | Task seconds | Observation |
|---|---:|---:|---:|---:|---|
| u01 | No | 2 | 2 | 54.189 | Tests stayed RED; repeated patch |
| u02 | No | 2 | 2 | 53.407 | Non-exact source edits |
| u03 | Yes | 1 | 1 | 37.055 | Independent GREEN |
| u04 | Yes | 1 | 1 | 29.091 | Independent GREEN |
| u05 | Yes | 1 | 1 | 27.508 | Independent GREEN |
| u06 | No | 2 | 2 | 46.475 | Non-exact source edits |
| u07 | No | 2 | 1 | 61.330 | Tests stayed RED; child exit nonzero |
| u08 | No | 2 | 1 | 62.062 | No-op; child exit nonzero |
| u09 | No | 2 | 2 | 58.718 | Tests stayed RED; repeated patch |
| u10 | No | 2 | 2 | 54.077 | Both candidates failed tests |
| u11 | No | 2 | 2 | 58.417 | Tests stayed RED; repeated patch |
| u12 | Yes | 1 | 1 | 28.213 | Independent GREEN |

All source/test identities stayed unchanged. Successful patches have real isolated baseline RED, candidate GREEN and independent GREEN receipts, with hashes linked to the original native model observation. Patches remain pending review; nothing was merged or installed.

The four recorded technical experiences could not be recalled after restart. Read-only MAC checks found the persisted-proof serialization defect; the later fix and its fresh diagnostic/paired experiment are excluded from this score. Original invalid proofs are not repaired or re-signed. Reflection is the pre-verification proposal hypothesis, explicitly limited in the persisted record; no post-GREEN model reflection or neural training is claimed.

Summed task duration: 570.542 seconds, excluding gaps and setup. Actual executable-inode sampling observed roughly 2 GiB native RSS, at most 2,079,300 KiB. PSS and sampled cumulative CPU are in each raw receipt; wrapper RSS is excluded. Sampling can miss transients and CPU at exit. Canonical random request/job IDs affect prompts, so later memory arms cannot support a strict single-variable causal claim. Other host CPU activity during u01 was not exhaustively instrumented; no latency advantage is claimed.

Private runtime keys are excluded from published artifacts. Raw Docker XML, verification/proposal hashes, model requests, native samples, source identities and all failure records are included. Device: `FOLD7_NOT_RUN`.
