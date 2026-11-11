# Personal Voice Learning — bounded research registration

Status: bounded research completed, 2/2 registered candidates rejected. No production or general voice-understanding claim. Original scope and ceilings below remain unchanged; see RESULT.md and the completion notes. Research only; EXP-05 and EXP-12.

## Verified baseline

Repository Ullmann27/device-ai; PR #728 HEAD 1d9eab1275a7fcc92ad4c5e2d1dd52d1db411667.
LM1 132160 parameters, minGRU, vocab512, dim64, two layers, context256.
Original checkpoint SHA-256 7a0af1e99554f9e73fbe4212d0aa4bf8ec97745efca56998d9b42bac4fad44fd.
Tokenizer SHA-256 94375c73d1f6e2d04b790738580143fa8c1b9b456d40fa7eb6b4009877bd892f.
PR728 candidates failed: no production adoption.

## Ownership and order

Exactly three agents: speech_data owns public synthetic data/schema and data audits; model_training owns native bounded training and model artifacts; independent_eval owns prospectively frozen independent tests and quality verdict. Coordinator owns integration, opt-in correction bridge and report. No additional bots.

1. Audit existing native/speech/context/learning components.
2. Validate synthetic train/development data and consent/provenance schema; freeze independent evaluation before candidate output inspection.
3. Execute unchanged-weight component baselines; distinguish host components from the full Android pipeline.
4. Only if data/registration gates pass: exactly two candidates, at most 128 native updates each, both initialized from the original bytes, seed 20261111, max 600 seconds per candidate, JVM max heap 768 MiB. No paid compute or cloud teacher.
5. Same four native labels: 0=no action, 1=open, 2=search, 3=remember. Parameters, discourse act, references and multiple actions are separately evaluated; the small specialist is not a general language model.
6. Arm A balanced supervised labels plus representative replay; arm B same data/order/budget plus frozen original-model KL preservation. Exact configuration must be committed before training. No third candidate or post-test tuning.
7. Compare baseline A, reviewed-context B with unchanged weights, and identical B plus candidates C. Any unavailable Android/ASR/device arm is NOT_RUN, never simulated as a live result.
8. Independent checks: unknown phrasing and word-order/scope contrasts, old-skill retention, unsafe-action errors, exact output and EOS/length, resource usage and reproducibility. Only extra benefit attributable to weights counts as weight learning.

## Data gate

128 balanced contrast TRAIN rows plus 32 newly authored simple open/search supervised replay rows; 32 disjoint-family DEV rows; 12 richer pipeline diagnostics excluded from single-label LM1 training. Historical retention stays evaluation-only. Bot1 mutation/privacy checks: 17 PASS, independently rerun by coordinator. Native token preflight: maximum 68 prompt tokens, below 252 limit. Independent evaluation committed remotely before training: 9deac1792e79736167c5169c916941d45c8f9a96, PR #755.

## Personal-data boundary

No personal recordings/transcripts are used or published in this run. Public examples are synthetic and labelled as such. Text correction and intent correction remain separate. Audio retention and use for training need separate explicit grants. Production capture defaults off; no cloud export or direct tool execution. Corrections are reviewed and deduplicated before any later batch. Existing memory facts are not rewritten from model suggestions.

## Repository boundary

Separate research integration and three agent branches. Checkpoint meaningful progress, no force pushes, main changes, signing, APK release, Gemma replacement, or changes to active Cinema/Avatar/APK branches. Rollback is exclusion of this research branch and selection of unchanged original weights.

This initial registration fixes scope and resource ceilings; independent_eval owns immutable metric/holdout registration before training.

## Completion notes — 2026-11-11

Both registered 128-update runs were executed from the original checkpoint. Independent frozen evaluation rejected both. No third candidate, test-driven data revision, production integration or model adoption followed. The integrated report separates format learning, restricted-class decisions, retained skills, context rules and human-supplied corrections.

The executed context comparison used the actual older research-base LocalConversationContext with 24 frozen component cases; it finished after the weight runs. This is a sequencing deviation from the intended pre-training end-to-end baseline, not evidence that the planned full A/B/C pipeline was completed. Full current-app/ASR/Gemma A/B/C, acoustic WER/CER, Android UI/Keystore runtime and Fold7 measurements remain NOT_RUN. The LM1 holdout and acceptance thresholds were prospectively frozen and were not altered by the later component or privacy work.

Final privacy evidence: 34 coordinator JVM checks; four independent store-repair checks; three independent consent-interleaving checks. The final Android review dialog and native store compile against real Android API classes, with source hashes bound in ui/compile-receipt.json. These checks support isolated research components only.
