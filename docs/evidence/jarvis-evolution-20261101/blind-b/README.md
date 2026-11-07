# PR330 fresh BLIND-B first execution

Frozen fixture37 cases SHA2564a8314061ca589d19b5043a4ef48869534b2ff8387e7ae787c143117dbb1ae4f. Primary25 contains20known+5missing; additional12 missing controls cover anaphora, negation, time, quotations, interrogatives, coordination, relation drift, corrections, indirect speech, hypotheses and entity substrings. Scope and labels were fixed before execution; developer did not view examples before exact d81be49e851a3c7f2196bcce288bc2fb40c7e22b freeze. Each track was executed once. The data are now consumed regression evidence; no labels were changed and no further training/tuning occurred.

Track is SPAN_EXTRACTION, not CDX/free generation. No normal-chat promotion or owner-state validation is established.

Primary25:
- Baseline3fdf:20/20known,5/5missing,25/25total,0wrongaccepted.
- Final deterministic rule d81:20/20known,5/5missing,25/25total,0wrongaccepted.
- TRAINED_RAW:19/20known,2/5missing,21/25total,4wrongaccepted.
- TRAINED_VERIFIED:19/20known,5/5missing,24/25total,0wrongaccepted.

Additional12 missing controls:
- Baseline4/12; final rule12/12; RAW2/12; VERIFIED12/12.
All37:baseline29/37; final rule37/37; RAW23/37; VERIFIED36/37. All17 missing cases abstain after verification. The raw model accepts14wrong outputs in all37. The verified path rejects them; one known multiword place is shortened by RAW and correctly rejected, so verified recall19/20 remains below the deterministic rule20/20. The entire failure row is retained as negative in all-failures.json; this does not justify a learned-quality/no-regression claim.

Verified known19/20 Wilson95:76.39%–99.11%. Verified missing17/17 Wilson95:81.57%–100%. These are nominal binomial intervals on a small authored synthetic set, not population guarantees. Exact numbers and exploratory paired statistics are in results/confidence-and-paired.json:
- Baseline->final rule:wins8/losses0/ties29, exact two-sided McNemarP0.0078125.
- Baseline->verified:wins8/losses1/ties28,P0.0390625.
- Final rule->verified:wins0/losses1/ties36,P1.0. A strict no-regression requirement fails on that lost case regardless of statistical significance.

Exact source commit d81be49e851a3c7f2196bcce288bc2fb40c7e22b; core SHA daf65ff935390f02684aec4264985435c00db8c16a976e4cc71d0db843762bab; probe/verifier SHA4d94ca8f217c22c0300e7cfa17b2699a4c280a42a356b4ac1b1921845b6190d9; wrapper SHAd362af0ec109e7e746879dc44ff70767e57816a7f410c1a84fa4944f586cf205; model SHAc28c44c21e6b79aea5d13254760a0a782714e662294b366826d9d8c940697e92. Checkpoint and parameters were verified unchanged after prediction. Only restore/predict was called, no training. The checkpoint is deliberately excluded from this no-binary bundle.

Raw first-run host JVM latencies are retained in SUMMARY.json and TSV rows; small-sample cold/mixed case timings are not Android/device resource evidence. The verifier proves source/span/declared-clause binding, not real-world truth or owner authority. Actual Android caller/owner-state tests remain separate. Scope deliberately abstains on unsupported temporal/anaphoric/quoted evidence rather than claiming to resolve it.

Reproduction uses tools/replay.sh with external Kotlin compiler dependencies, stdlib and the separately retained exact checkpoint. Any replay is consumed validation, not a second fresh BLIND-B result. No branch, owner data, production weights, signing or releases were modified by this review.
