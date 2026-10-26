# SOL five-core checkpoint — owner-requested stop, 2026-10-26

Official parent Draft PR151 continues PR150 at ad14f8b82f9be2cd0026bc16eef71a16bec41424. Owner reported20%usage and ordered immediate careful commit/push/handoff. No new experiment or broad test run was started after that instruction. Source repairs from sibling PRs are preserved separately, not silently integrated into the parent or main.

## Actually measured on parent source

Frozen executable source: **cf96c19fb22c5a3b1b06f259c3b2e92866077c07**.

- Main-dialogue plumbing: failing regressions proved grounded retrieval blocked explicit synthesis and512-char memory truncation discarded the remainder of long model tasks. Bounded fixes plus labelled retrieval/error fallback implemented. Nine focused actual JVM cases pass; the actual AgentActivity Android SDK compilation and a physical-device test are **NOT_RUN**. New whole-project regression and source-review clearance are also NOT_RUN. Earlier PR150 suite cannot establish these later changes.
- Frozen independent20-task German benchmark: selected actual Gemma bytes, API0.18.1 **18/20**, API0.18.0 **18/20**,0runtime errors, identical raw answers. Both code-output cases fail (DE-P01:−1 instead10; DE-P02:13 instead21). Do not claim reliable coding competence. All other18 short synthetic cases pass. Models run on hostCPU only, no tools/training/promotion.
- Public reference Qwen3-0.6B actual bytes, API0.18.0: **11correct/8incorrect/19completed of20**, one unfinished. Process exited−9(SIGKILL); exact cause not independently established, fixed240CPU-second/8GiB limits in effect. Raw report is deliberately retained with its unfinishedRUNNING status; closure manifest marks INTERRUPTED_NATIVE_SIGKILL_NOT_COMPLETE. Do not report a completed Qwen benchmark. No retry after owner stop. Reference SHA differs from unknown owner-installed model identity, so no exact Fold7 old-model comparison claimed.
- Gemma peak RSS3370248/3371564KiB, Qwen recorded1994268KiB. Warm-cache load and run timings are raw observations under shared host conditions, not independent cold/device throughput claims. Native Python successful return does not expose exact EOS-vs-token-budget reason.
- Official Google release comparison shows no changed Kotlin API files;0.18.1 patch is documented as an EmbeddingGemma2 ABI fix. Android remains0.18.0. Host same-output compatibility does NOT prove Android ARM64/Fold7 import, load, thermal or performance acceptance.

Read `language-matrix-summary.json`, process manifest, all raw JSON/text and runtime/wheel/model provenance. Original Gemma and public Qwen weights are reproducibly pinned in `config/research/local-language-runtime-matrix-v1.json`; large model files are not duplicated into Git. Existing owner data/checkpoints are untouched.

## Independent tests preserved before use

Gemma fixtureSHAea8f83cb45765a9034fd777954e4e62a36fd35647c41d9922bbe2fe0b0d841a7 was committed before all predictions, no tuning. This20-case test is now consumed.

CDXA1 fixtureSHA58bcb0d6393b10f9af0f064692a71458f1ff8978759fb9b5e86d1b8b0a4e0e7c:32main+8separatechallenges, fixed by another author without model/train/code inspection, **NOT_EVALUATED**. File under `config/research/cdxa1-independent-test32-v1.json`, provenance here. Do not load its labels during candidate training/selection.

Grounding fixturesSHA6a1a414786b22eec0573e25dd7f8aaf1972f5f2a5916181173fc612265672c12:60cases,48scope+12challenges, frozen after candidate4bf74b1b. Owner stop occurred before evaluation began. Directory `grounding-independent-not-run/` includes generator, byte-preserved fixtures, null predictions, unknown metrics and checksums. **NOT_RUN_OWNER_STOP**, no inferred improvement or source-review approval.

## Reproduction / next work

Use a clean checkout of cf96c19f, exact CONTINUUM_PROBE_SOURCE_SHA, official checksum-verified litert-lm-api0.18.1 or0.18.0 isolated environment, and the exact actual file matching the matrix SHA/size. Keep external300s timeout; script additionally sets240CPU-second/8GiB address-space limit. Never overwrite old reports.

```bash
CONTINUUM_PROBE_SOURCE_SHA=cf96c19fb22c5a3b1b06f259c3b2e92866077c07 \
  timeout 300s python scripts/continuum-local-language-benchmark.py \
  gemma-selected 0.18.0 /path/to/gemma-4-E4B-it.litertlm /scratch/NEW-report.json
```

The frozen matrix is the authority. Process manifest preserves actual executed argv. Text logs normalize trailing whitespace only; raw JSON/weights remain unchanged.

Resume from the parent handoff `docs/research/omega/SOL-BREAKTHROUGHS-HANDOFF-20261026.md`. First review and SDK-compile the parent dialogue change, independently evaluate PR153, then selectively integrate verified source commits from sibling DraftPRs. No main merge, owner release, paid compute, Qwen deletion or device installation authorized by these host results.
