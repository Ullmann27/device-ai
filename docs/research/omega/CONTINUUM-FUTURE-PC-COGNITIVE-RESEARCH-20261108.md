# Device AI Continuum — advanced cognitive research roadmap · 2026-11-08

STATUS: RESEARCH PROPOSALS / NO APP RELEASE EFFECT. Independent branch, separate from Sonnet's Code 50 QA.

## Objective
Develop evidence-constrained local intelligence that can remember contradictions, predict consequences, autonomously propose safe tests, learn from verified outcomes and defer compute-heavy work to an optional future workstation. Novelty is not assumed: each concept requires prior-art review and same-harness baselines. A 4B model with memory and agents does not thereby become an AGI.

## Tracks (ordered by earliest value)
1. Hypothesis laboratory: HypothesisWorkspace + KnowledgeGapDetector + EpistemicArbitrator. First verify permutation invariance and information-gain/energy-cost decisions, then compare against round-robin and random probes on held-out synthetic software incidents. Read-only; no permissions.
2. Proof-linked temporal causal memory: LightweightJtmsEngine + CausalEpistemicLedger + ProvenancePreservingConsolidation + HybridMemoryRetriever + temporal stores. Tests: revisions, retractions, dependency cascades, stale claims, deletion, correct abstention. Benchmarks: LongMemEval, MemoryAgentBench and MEME-like edits. Preserve owner-only privacy rules.
3. Reversible world model for device/UI: CounterfactualWorldOverlay + current real ToolWorkJournalBridge receipts. Predict action consequences in a sandbox, compare with independently observed execution, score action success and incorrect tool proposals. No real action without ToolGate/owner authorization. Compare closed-loop success versus direct-agent baseline.
4. Continual learning without regressions: ExperiencePatternMaturity + AdaptiveReasoningStrategyMemory + current ModelReputationStore, then workstation-side frozen-backbone PEFT/QLoRA experiments. Only verified outcomes become training candidates; immutable holdouts, rollback and forgetting measurements before adapter promotion. Never silently update installed user weights.
5. Controlled autonomous science: bounded research scheduler, explicit hypotheses and pre-registered test metrics; isolated runner with immutable input/output hashes and negative controls; external code execution requires an independent sandbox. Benchmark against FIRE-Bench or known rediscovery experiments.
6. Dual-compute intelligence: smartphone remains offline-functional; optional workstation conducts training, evaluations and heavy simulation. Opt-in data transfers, authenticated worker messages, signed receipt provenance, versioned model/adapter artifacts and fail-closed disconnect.

## Future-workstation design envelope (not a purchasing quote)
Prioritize GPU VRAM and driver/toolchain compatibility, then 128 GB-class RAM (depending on experiment scale), fast NVMe storage, cooling, backups and power budget. 24 GB GPU VRAM can support certain smaller quantized adapter experiments; 48–96 GB increases feasible model/context scale, subject to measured batch size, optimizer memory and runtime compatibility. Full pretraining of frontier-scale models is not achievable on an ordinary single workstation. Reassess at purchase time.

## Measurement contract
Each track needs a documented baseline, frozen data split, multiple seeds, adversarial cases, run hashes, wall-time, memory, energy where relevant, confidence calibration, failure counts and explicit NO-RESULT cases. For privacy-sensitive memory, test only synthetic or specifically consented data. Report negative as well as positive outcomes.

## Prior research (ideas are not exclusive novelty)
- Hindsight long-term agent memory (ACL 2026): https://aclanthology.org/2026.acl-demo.27/
- LongMemEval (ICLR 2025): https://proceedings.iclr.cc/paper_files/paper/2025/hash/d813d324dbf0598bbdc9c8e79740ed01-Abstract-Conference.html
- MemoryAgentBench (ICLR 2026): https://mlanthology.org/iclr/2026/hu2026iclr-evaluating/
- Counterfactual Planning (AAAI 2026): https://ojs.aaai.org/index.php/AAAI/article/view/40184
- Computer-Using World Model (ICLR 2026 workshop): https://www.microsoft.com/en-us/research/publication/computer-using-world-model/
- World-In-World closed-loop evaluation (ICLR 2026): https://proceedings.iclr.cc/paper_files/paper/2026/hash/5b4263be85820683d78675cc18d2efc7-Abstract-Conference.html
- FIRE-Bench scientific rediscovery (ICML 2026): https://proceedings.mlr.press/v306/wang26d.html
- AI Scientist-v2 (2025): https://arxiv.org/abs/2504.08066
- AlphaEvolve 2026 follow-up: https://deepmind.google/blog/alphaevolve-impact/
- Continual fine-tuning via program memory (EMNLP 2026): https://aclanthology.org/2026.findings-emnlp.1048/
- PEFT/QLoRA reproducible training: https://huggingface.co/docs/trl/peft_integration

## Explicit independence
- Do not merge this research PR into Code 50 while Sonnet is conducting release QA.
- Do not touch main, staging, owner data, signers, or approved tool permissions.
- This pilot is a diagnostic experiment, not a new working AI feature or proven improvement.
