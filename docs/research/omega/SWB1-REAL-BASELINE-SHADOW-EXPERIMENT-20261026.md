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
