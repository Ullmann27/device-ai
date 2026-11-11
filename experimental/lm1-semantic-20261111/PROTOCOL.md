# LM1 semantic transfer: bounded experiment

Status: PREPARATION, no new optimizer updates. User authorized this isolated experiment on 2026-11-11 despite older release-priority documents. EXP-05 / EXP-12.

## Fixed scope

Exactly three helpers: data, training, independent evaluation. Coordinator owns protocol, data gate, source integrity, review and decision. No recursive agents, third candidate, automatic second trial, new architecture, GPU purchases or product integration. Every branch starts at PR728 commit `1d9eab1275a7fcc92ad4c5e2d1dd52d1db411667`. Existing historical files and all product paths remain unchanged.

## Order and identity

1. Read original sources, artifacts and negative results. Pin original model, tokenizer and native source identities.
2. Publish a small synthetic TRAIN/DEV data pilot and train-only shortcut controls. Reject a pilot if a trivial classifier reaches at least 90% DEV accuracy. Publish every pilot revision and its reason.
3. Evaluator publishes the independent holdout identity and full prospective protocol before model evaluation. Root and trainers do not inspect holdout content or labels before candidate freeze. Shared filesystem prevents technical blinding; organizational separation is the explicit limit.
4. Freeze training source, optimizer settings, data and retention targets; coordinator records the actual gate decision before any candidate update.
5. At most A and B, at most 128 updates each, same authentic original checkpoint. Early collapse may stop a candidate. No holdout feedback for tuning.
6. Freeze resulting weights, optimizer state and logs before evaluator compares original, all three old candidates and both available new candidates on identical tasks.
7. Review effects and uncertainty; preserve negative findings, never auto-promote.

## Prospective minimum evidence

Evaluator details and exact counts live in its committed protocol. Proposed fixed confirmatory set: 128 rows in 64 contrasting pairs, four equally frequent labels, eight unseen syntax templates across four broader construction groups with unseen objects. Primary restricted-choice semantic accuracy must be at least 60%; fully correct pairs at least 40%. Both must improve by at least ten percentage points versus original, best old candidate and best preregistered trivial control, with paired 97.5% bootstrap lower bounds greater than zero. Pair resampling is primary; family sensitivity exposes template correlation. Report free exact label+EOS separately, never equate restricted scores with usable free generation.

Old open/search retention: at most five percentage points aggregate regression and at least 90% of originally correct individual cases retained. This old baseline is weak (32/64 restricted; 56 search and eight open predictions); it does not establish broad command understanding. Report class confusion and unseen retention separately. Continuation median length at least 90% of original, no systematic early-EOS increase, text bits/byte no more than 5% worse; individually track originally long continuation probes. Length alone does not establish coherent language. All integrity and product-protection checks are mandatory.

## Publication and rollback

Each useful checkpoint is committed and immediately published to its assigned research branch. Native Git credential failure may be handled through the authorized GitHub Git-data connector with identical tree verification and explicit local-to-remote commit mapping. No force-push or unchecked merge. Final evidence aggregation is reviewed and restricted to new research paths. Rollback is to leave all research weights unselected; original sources/weights remain available.

## Pre-training clarification

The evaluator distinguishes eight syntax templates from four broader construction groups. These are not eight independent semantic populations. Additional four-construction sensitivity is required; thresholds remain unchanged. This clarification and the holdout identity were published before any A/B update or holdout inference. See ROOT-GATE.json for the precise source and data bindings.
