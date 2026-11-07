# S2 — context loss and CDX conditioning

Status: HOST_VERIFIED / RESEARCH_SHADOW. Synthetic verified-location task, not neural or general-language performance.

The immutable 72-case protocol predates candidate code (`eefbd231`); input SHA `c29f57dca626901ca65cbba36027b22897794cc73d4e6a35e252da5dd442e3cf`. The author's fixture generator produced all labels; this is prospective but not independently authored blind evaluation. No fitting, hyperparameter selection or productive checkpoint access occurred. All three seeds concern input bindings; these are not three training runs.

| Arm | Correct | Wrong answer | Correct negative abstention | Answerable abstention |
|---|---:|---:|---:|---:|
| Existing parent compiler + existing verifier |48|24|0|0|
| Whole-evidence compiler + same verifier |72|0|24|0|
| Complete original evidence oracle + same verifier |72|0|24|0|
| No evidence + same verifier |24|0|24|48|

24 paired improvements, 0 regressions, 48 unchanged. Each seed: 8 late-negation errors repaired; 16 unchanged positive answers. Full raw rows and source SHA are in the parent/candidate/paired JSONs. No reused historical CDX accuracy is counted. The budget-edge cases happened to fit completely in both arms. They do NOT show absence of recall loss at smaller budgets. A separate unit regression intentionally exceeds budget and verifies whole-group omission; tighter arbitrary budgets can trade recall for correct abstention.

## Diagnostic result that did not confirm the broad hypothesis

The derived 12 novel person/place swap pairs (four per frozen seed) had **0/12 identical SWB1 bucket sets**, **12/12 identical final eight prefix bytes**, conditioning L2 distance 0.003336316212302978–0.007384771506442751. A new **untrained** seeded CDX1 decoder had next-token distribution maximum differences 3.0060435032364455e-7–7.145988126454859e-7. Existing ordered rule/verified bindings distinguished all 12 pairs. This is a comparison of input representations, not learned generation accuracy and not a new 12/12 neural result.

Actual `subwordBuckets` uses cross-word 2–4-character substrings and caps the insertion-ordered set at 128 buckets after a 512-character normalization window. Therefore a broad claim that *every* role swap yields identical bags is false. Earlier exact-collision cases remain valid special cases. Small untrained distribution differences cannot establish the cause of failure in the owner's trained checkpoint. No neural change was promoted; the existing decoder remains unchanged.

## Eight generator questions

1. **Window loss:** CDX1 direct context is eight bytes; SWB1 normalization ends at 512 characters/128 buckets. New compiler tests prove a separate live prompt suffix-loss defect, repaired in S1. These mechanisms must not be conflated.
2. **Conditioning:** new controls show distinguishable but weakly different seeded vectors, so collision is not a complete causal explanation. Measure the actual frozen checkpoint next.
3. **Teacher forcing vs autoregression:** existing loss gates do not prove free-generation correctness; no new trained generation run here.
4. **Token/copy:** byte vocabulary can encode new names but does not supply a positional copy mechanism. Existing pointer lab remains shadow; its supervised span objective differs from generative NLL.
5. **Role binding:** existing independent structural verifier rejects contradictory evidence and preserves exact entity/relation/source offsets within its narrow grammar. No general coreference claim.
6. **EOS:** terminal-token probabilities of the owner's frozen checkpoint were NOT_RUN; no claim that changing stop thresholds repairs collapse.
7. **Loss vs quality:** keep loss and actual answer/negative-abstention audits separate. The new quality metric is a verifier decision on the literal compiler projection, not a loss proxy.
8. **Capacity/data:** 7,746-parameter CDX1 versus the owner's broad goals remains a capacity hypothesis, not a proven sole cause. No synthetic gain justifies production-weight mutation.

Next generator experiment: pin a non-personal copy of the audited checkpoint; freeze independently authored role/copy/negation questions; trace teacher-forced and autoregressive tokens, condition ablation/shuffle and EOS under multiple seeds; retain RAW/RULE/VERIFIED scores separately. Productive memory and weights remain untouched.
