# New bounded location controls — 2026-11-02

Ten newly authored synthetic cases, seed 20261102: five supported static statements and five required abstentions. Corpus frozen as SHA256 `6c73763bf77cf8c1defaeb04574ef39f18156b6bcbc4a597dc20a860a26811e0` before this auditor inspected candidate code/checkpoint/predictions. The auditor had already read the existing location rule/reflex for M1; this is a small new control, not a claim of complete blindness to the architecture. Generated names/values had zero overlaps across 537 scanned prior corpus/evidence/resource JSON files, including available A/B and issue340 TRAIN/DEV/C material. Templates need not be novel.

Actual existing predict-only CLI and unchanged 910-byte checkpoint `629c8319ede2aba3cd60f0448b6469034c8550530e0326739b79c26bfc40939d`:

| Mode | Supported exact tuples | Required abstentions | False positives |
|---|---:|---:|---:|
| RAW | 5/5 | 5/5 | 0 |
| VERIFIED | 5/5 | 5/5 | 0 |
| RULE | 5/5 | 4/5 | 1 |

All outputs and the failure remain intact. Case N08 states a person's location, followed by `seit gestern ist der Ort unbekannt.` The frozen rule emits the earlier place despite the later time-scoped uncertainty. RAW and VERIFIED abstain. No candidate, rule, checkpoint or case was tuned after freeze.

Gold includes exact source ID, revision, SHA256, text and UTF-16 start/end offsets. Prediction requests contain only ID, question and sources. Generator, frozen corpus, requests, raw outputs, process commands, result rows and SHA256 inventory are included. Classes are the already independently rebuilt existing candidate/rule from the sibling M2 reproduction, with hashes captured separately.

These ten controls are not pooled into the consumed 55-case Blind-C and do not establish the requested 20/5 target, general language ability, statistical superiority or production readiness. Host JVM only; no Android/Fold7 run, training or promotion.
