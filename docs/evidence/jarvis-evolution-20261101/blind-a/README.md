# PR330 independent natural-location span review

This bundle contains fictional authored evidence, frozen gold labels, first-run output, source snapshots and executable Kotlin harnesses. No production files, weights, owner data, signing, branches, tools or release state were changed by the reviewer. No class files or model checkpoint are included.

Frozen fixture SHA256:1762e7cb3d969802f2c507e90a5aee7ee725cfd021497ec7a01ce482def079e7. Exactly45 cases: primary25=20known+5missing; additional20=4known+16missing. Scope and labels were fixed before execution; developers did not see examples/results until both rule and learned candidates were frozen. The initial unexecuted authoring snapshot had four negative sentences rewritten into idiomatic German before developer handoff; labels/scope were unchanged. All reported executions use the final hash above exactly once per track. After result disclosure this corpus is CONSUMED: future use is regression/validation, not a fresh blind TEST.

SPAN_EXTRACTION only. These measurements do not measure CDX1 free generation, neural chat, general language understanding, AGI, Android integration or device performance.

Baseline rule source:3fdf244ea6f5f3e1386e0653538d06daddc616ad, SHA7095b9273d794cb4f7375ab8879c7c0ea43f2dde1494a11c70ffdc694568ff83. Repaired rule source:ae2da0e7622bba9ed759bedbc16e2dd71e7192a1, SHAfb14985dcac0487ffb953cb1bf1f1c1d1995c001f6744a62ace0416484ee02ae. Learned source snapshots match published a329d5c4 (full SHA in results/published-source-identity.json). Core SHA daf65ff935390f02684aec4264985435c00db8c16a976e4cc71d0db843762bab; wrapper SHA3783355d944950635d507cd60e7d8f8af3226d4e72911c5ece9e544918b65702; verifier is the same repaired ae2 source. Immutable trained checkpoint SHA c28c44c21e6b79aea5d13254760a0a782714e662294b366826d9d8c940697e92. It has4110 parameters and3200updates; reviewer used restore/predict only and verified parameters unchanged. The checkpoint is intentionally not packaged.

Before seeing learned predictions the reviewer mistakenly associated an earlier source reading with a later fingerprint; an atomic Git/current/snapshot comparison corrected the interpretation. The compiled source snapshots were always the correct separately exported baseline and candidate. No wrong verifier source was executed and no expectations changed.

Primary25 first-run results (correct known / correct missing; wrong accepted):
- Baseline rule:20/20 +5/5;0 wrong;25/25 total.
- Repaired rule:20/20 +5/5;0 wrong;25/25 total.
- TRAINED_RAW:19/20 +2/5;4 wrong;21/25 total.
- TRAINED_VERIFIED:19/20 +5/5;0 wrong;24/25 total.

Additional20 first-run results:
- Baseline rule:4/4 +7/16;9 wrong;11/20 total.
- Repaired rule:4/4 +13/16;3 wrong;17/20 total.
- TRAINED_RAW:4/4 +2/16;14 wrong;6/20 total.
- TRAINED_VERIFIED:4/4 +15/16;1 wrong;19/20 total.

All45:baseline36/45,repaired42/45,RAW27/45,VERIFIED43/45. The learned verified path improves aggregate accuracy versus the rule by a net one case but loses one previously correct primary answer: it chooses only Sankt from Sankt Gallen, and the verifier correctly rejects that partial span. This fails a strict no-regression claim. RAW also answers missing-location cases with verbs such as besitzt/arbeitet/lernt. Confidence is model softmax, not a calibrated factual guarantee.

The one remaining verified blind false acceptance is a preregistered relation-scope disagreement: the fixture does not allow a residence fact to establish generic current Wo-ist presence, whereas the prototype explicitly allows all location predicates for ist. This policy difference is retained rather than relabeling the test after observation. For a user-visible factual answer either preserve the actual evidence relation or bind the question to that relation; do not claim current presence from residence alone.

Separate forged-verifier suite (not the blind45):22 cases. The repaired verifier fixes negation/conflicts, embedded speech/hypotheticals, wrong grammatical holders, partial spans, provenance/budget forgeries and standing-to-residence drift. The20-case bounded subset excludes the separately labeled presence-policy and anaphoric-denial limitations: repaired18/20 versus baseline3/20. Two definite holes remain:
1. A question in source evidence (Lukas wohnt in Zürich?) is accepted as an assertion because punctuation is discarded before verification.
2. Zürich und Bremen is accepted as one place. Global case-insensitive matching weakens the uppercase-word place grammar and there is no conjunction guard.
Both actual producer and independently invoked verifier accept these cases. They should block factual-answer promotion until fixed. The wider anaphoric-denial case is explicitly outside the narrow named-clause scope and is not counted as supported general NLP. Do not combine it with the20-case bounded pass count.

All failures, raw strings, source identities, exact gold, rejection reasons and first-run latencies are retained in results/. Host times are small-sample cold/mixed JVM case timings only. The largest model input is explicitly bounded to1024UTF8bytes/128tokens/16clauses/192candidate spans, source snapshots to4sources/4096characters. No Android cancellation/thermal/RAM result is claimed.

The checkpoint/source were frozen before outcomes were released to the developer. The fixture had no training access: authored training came from the developer's separate64-example script, and the reviewer never called train or a model-generation API. Training script provenance is retained from the matching published commit. The already observed45 examples must not be used to claim a second independent success after repair/tuning.

Reproduce with an external Kotlin compiler/dependency classpath and stdlib. tools/run-probes.sh replays both source snapshots; tools/run-learned.sh requires the separately retained checkpoint with the exact hash above. Both scripts mark replays as consumed validation. No CDX-generation results are included.
