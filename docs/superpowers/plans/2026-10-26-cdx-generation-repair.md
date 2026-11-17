# CDX generation repair implementation plan

> Execution: parent explicitly authorized native implementation and routine design
> decisions. Continue inline; parent independently reviews/evaluates the freeze.

**Goal:** establish a genuine, bounded free-generation improvement over CDX1
collapse, preserving raw negative evidence and all prior formats/checkpoints.

**Architecture:** first repair the measured weak input signal using only a
caller-side conditioner transform, with matched-data/budget raw controls. If
that fails to generalize, separately design a full-prompt attention/copy neural
research decoder; no deterministic answer templates or active runtime changes.

**Tech stack:** Kotlin/JDK17, actual SDK classes/module ABI, org.json; later
research alternatives remain host-only and individually documented.

**Spec:** parent task and `docs/research/omega/SOL-CDX-HANDOFF-20261026.md`.

## Constraints and review focus

- EXP-05 research-only. Existing CDX1/SWB1/CRE1, owner data and deployed resources
  remain unchanged; no main merge or promotion.
- Use protocol `config/research/cdx-conditioner-repair-v1.json` verbatim; freeze
  source before predictions. Dev strata distinguish wording/memorization from
  changed-fact, novel-entity and negative behavior.
- Snapshot/restore and all generation must be read-only. Record exact source,
  fixture and weight hashes; retain failed arms/stages and every output.
- Check gradients against finite differences and paired train/infer consistency;
  inspect repeated byte embeddings and conditioner gradients separately.
- Never treat earlier16 TEST cases as hidden after tuning; parent creates new
  independent cases after candidate freeze. No test-based selection/retraining.

## Task1: causal reproduction

- [x] Inspect real decoder, encoder, bridge, train loops and archived checkpoint.
- [x] Compile diagnostic source against actual SDK classes and module ABI.
- [x] Execute from committed source; reproduce original6000 training hash and
  all generations; archive30 derivative comparisons and conditioner ablations.
- [x] Commit/push exact commands, results and limitations.

## Task2: controlled conditioner repair

- [x] Use original4 pairs, seed91, rate.035, stages0/6000/30000/120000.
- [x] Implement standalone probe loading the frozen protocol; compare raw,
  L2-unit and train-input-mean-centered×32/clipped conditioners.
- [x] Save every stage under its arm, perform bounded fixed-step training,
  assert generation leaves model bytes unchanged. Report exact+EOS by stratum.
- [x] Choose candidate only by preregistered DEV criterion; report raw matched
  budget even if it beats the candidate. Commit/push before TEST.

## Task3: independent evaluation and handoff

- [ ] Send parent candidate/protocol/source/checkpoint hashes and support limits.
- [ ] Run parent's independent cases once against every frozen comparison arm;
  preserve raw responses and metrics, without adaptation.
- [ ] Verify checkpoint roundtrips, invalid-input rejection, all existing focused
  decoder/bridge tests; parent reviews integration independently.
- [ ] Archive positive/negative evidence, useful checkpoints, hashes and runnable
  commands; final pushed handoff/PR status clearly separates measured scope.

Ruling: no extra approval round because the parent task and owner continuation
explicitly authorized bounded research decisions. The cost of a wrong research
choice is a recorded negative experiment, never an activated model.

Owner stop: Task3 remains NOT_RUN. One separately approved CDXA1 architecture
and pre-prediction data/protocol are secured UNTRAINED; see
`experimental/brain/CDXA1-RESUME.md`. No independent test was opened or consumed.
