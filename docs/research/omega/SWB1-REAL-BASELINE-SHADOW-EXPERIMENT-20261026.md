# SWB1 first verified continuation experiment — 2026-10-26

## Strict source identity
- Repository: Ullmann27/device-ai
- Real persisted neural state: `continuum-state:.continuum/state.json`, GitHub blob SHA `420880e8040a7af3436805c68fc58e65d4d7a10`.
- Original accepted SWB1 model SHA256 `5e577c21b22c6b31ff71274640d45f8900ca646e370bef6179ab608ffb992cd1`, checked against the **actual decoded 32,960 model bytes** with an independent SHA256 implementation (the implementation was checked against the standard `abc` SHA256 digest). Its internal SWB1 checksum also passed.
- Actual `portable-subword.mjs` GitHub blob `b0bebcd265e934ed2aea8439c45b13afb702e3fc`; authored curriculum blob `895df87defb1706ff4e8633fec13c009b9811eac`; experiment `neural-core.mjs` blob `adbaba02251c2146d3f71ee3758d85c614febfcd`. All three original source texts were fetched on this commit branch and directly evaluated after replacing only Node import/export syntax with dependency bindings.
- Execution host: constrained V8 isolate, **not** Node.js native checkout. Pure JS compatibility adapters implement SHA-256, Buffer/DataView binary IO, URL validation, monotonic clock, JSON structured-clone. These are test-only adapters, not production module changes. No filesystem, device, shell, external LLM, network, owner credentials or model import/export occurred. This is an independently checked in-process calculation using original SWB1 math source, **not** a complete native Node acceptance run.

## Frozen actual baseline before each candidate

Rounds 12; accepted 2; updates 13,000; public replay examples 45.
Original exact model digest `5e577c21b22c6b31ff71274640d45f8900ca646e370bef6179ab608ffb992cd1`.
Validation: intent 19/20, loss 0.14535574038800006; semantic 13/20, loss 0.6942719897359286.

Each of three **independent** research candidates restarts from those same exact bytes, `steps=1000`, `now=12`, bounded 28s per run, `report.auditUsedForSelection===false`. They are not consecutive live training rounds.

| Arm | Replay interval / rate | Source updates | Accepted by existing `trainRound` gate | Checkpoint | Intent validation | Semantic validation | Report-only AUDIT intent / semantic |
|---|---|---:|---|---:|---|---|---|
| CONTROL | 8 / .035 | 63 | yes | 1000 | 19/20, loss .14368562427314882 | 13/20, loss .6943438522080181 | 20/20, 12/20 |
| LOW_PUBLIC_REPLAY | 16 / .015 | 32 | no (NO_MEASURED_GAIN) | 0 | 19/20, loss .14438730422873738 | 13/20, loss .694343550341656 | 20/20, 13/20 |
| NO_PUBLIC_REPLAY | 0 / .035 | 0 | yes | 1000 | 19/20, loss .1437639401730433 | 13/20, loss .6942843307157611 | 20/20, 13/20 |

Actual duration on the V8 adapter: about 1.1s / 1.0s / 1.1s respectively; not representative of device performance. All three reported `steps=1000`. All tests rechecked `rounds===12`, `accepted===2`, and the exact baseline SHA256 remained unchanged.

## Interpretation, no premature promotion

The first two original rounds accepted 2 candidates. Rounds **3–12** were rejected 10 times because semantic validation repeatedly dropped from 13/20 to 11/20 after long 12,000-step training, even as intent improved. The next 1,000-step CONTROL and NO_PUBLIC_REPLAY experiments were accepted by existing criteria, without actually increasing the number of correct validation answers. The output is therefore a possible **small loss reduction**, not an intelligence breakthrough. The CONTROL report-only audit semantic 13/20→12/20 suggests risk, but that suite was already exposed during the research, and choosing policy by this particular result would leak report-only audit into model selection. No new weights published or selected.

The report is a single trial per arm, not a statistically reliable estimate. A previously unseen, sealed set of semantic and factual questions and a native exact-HEAD Node run are required before choosing any replay adjustment, and the original `neural-core` defaults deliberately remain unchanged. Avoid treating replay source title/abstract association as confirmed truthful positive semantic labels.

## Local automation status

GitHub Hourly Watch run `40387465683` failed with **zero reported steps**, with repair/publish skipped and logs unavailable. Cause not confirmed; this is not a demonstrated JavaScript failure. Added a local owner-computer worker to perform reproducible CI-independent *read-only* health checks hourly with a fail-closed git guard, and no GitHub Actions/paid AI API requirement. It is saved on a review branch and not installed on the owner's local machine.

## Future gate

1. Native Node reproduce exact source with official Node APIs and verify the above 3-arm scores; use real untouched GitHub state.
2. Build a *new*, sealed multi-topic, out-of-distribution semantic set before choosing a replay policy, against candidate and saved model; no peek/retune.
3. Only then decide if the **next** training round can be accepted. Record the new round honestly as potentially 3/13, **not** magically 3/12, with comparison to fixed baseline and owner-approved state write.
4. Provision owner-local worker on powered machine and run one real full suite; investigate GitHub Actions account-level status for no-step runs, without making risky `main` changes.

## Post-freeze transfer falsification (new authored suite, SHA pinned)

After the original three-arm 1,000-step results had been frozen, a new balanced `TRANSFER_SUITE_V1` was authored and committed as a separate file before its first model evaluation. Source: `.github/continuum/neural-fresh-transfer-20261026.mjs`, GitHub blob **`cd09e9dc8be70b6b36628beb2659ddba4503e102`**. It contains 20 fresh intent queries (10 greetings/10 questions) and 32 new paraphrase/mismatch judgements (16 positive/16 paired counterexamples) across software, devices, utilities and daily-life topics. The exact source audit confirmed **zero** prior TRAIN/VALIDATION/AUDIT/ANDROID_VALIDATION phrase collisions and **zero** duplicate evaluation pairs. This is an **authored, once-used probe**, not externally audited hidden science; subsequent tuning must not use it for selection and still requires separate sealed evaluation.

With the **same real accepted model hash**, baseline transfer was **18/20 intent, 16/32 semantic**, losses 0.2812700307345184 / 0.6938738915626814. The three 1,000-step variants were rerun using actual fetched mathematical source and test-only V8 adapters. Results:

| Model | Fresh intent | Fresh semantic | Fresh intent loss | Fresh semantic loss | Previously accepted on original validation |
|---|---:|---:|---:|---:|---|
| Original, unchanged | 18/20 | 16/32 | .2812700307 | .6938738916 | saved 2/12 |
| CONTROL 1k | **18/20** | **16/32** | .2795461610 | .6938626745 | yes, current bounded gate |
| LOW_REPLAY 1k | **18/20** | **16/32** | .2812700307 | .6938738916 | no; model retained baseline |
| NO_REPLAY 1k | **18/20** | **16/32** | .2800976034 | .6938468597 | yes, current bounded gate |

**Conclusion:** No increase in correct answers; semantic transfer on this balanced probe is at 16/32 and loss near .693, compatible with chance-like discrimination. This indicates the existing semantic representation is not robust, rather than proving that more rounds are useful. No accepted weights published and no training state changed. **Do not claim 3/12 or general reasoning progress.**

The new shadow runner reports this exact fixed transfer suite separately under `transferIntentBefore/After` and `transferSemanticBefore/After`; each profile declares `transferUsedForSelection:false` and the whole report `transferSelectionForbidden:true`. This supports observation, not model promotion. Tests enforce fixture balance, no sample reuse, and no transfer-based acceptance.

## Implementation safety review correction

The first shadow test inadvertently expected two output rows after the default had grown to three policy arms. Test fixed to explicitly request one policy. The total default research grid is now limited to **nine trials (3 step budgets x 3 bounded replay arms)**, and all calls verify exact baseline JSON, model hash and weights before/after. The full 7-step budget grid remains an allowlist but combinations above nine are refused. This prevents runaway local CPU use. All production-default math was verified unchanged relative to the pinned original via a source-reversion comparison.

The local developer worker now continues scheduling after individual cycle exceptions rather than stopping forever, returns nonzero exit codes for failed/incomplete single-run tests, checks checkout integrity at both ends, rejects output-directory redirection into the repository, and refuses symlinked/unsafe report paths. A fatal process kill can still leave a stale lock requiring a controlled manual cleanup; no unsafe automatic lock takeover was added.

**Remaining mandatory native gates**: official Node.js full actual checkout, Android tests, owner-computer worker lifecycle and true held-out language benchmarks. These GitHub changes are review-only; the existing published APK and `continuum-state` remain untouched.
