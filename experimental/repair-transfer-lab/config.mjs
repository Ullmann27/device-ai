/** M9 module split, fixed BEFORE task generation. Modules never cross splits; holdout modules are unseen code areas.
 * ARITH faults exist only in the holdout: a cause the experience has never seen as a correct repair. */
export const SPLITS=Object.freeze({
 train:[
  {test:'.github/continuum/repair-diagnostics.test.mjs',module:'.github/continuum/repair-diagnostics.mjs',extra:['.github/continuum/fixtures/node-reporter']},
  {test:'.github/continuum/retrieval-identifiers.test.mjs',module:'.github/continuum/dialogue-retrieval.mjs'},
  {test:'.github/continuum/research-evidence-gate.test.mjs',module:'.github/continuum/research-evidence-gate.mjs'},
  {test:'.github/continuum/coin-wm-v3-numeric-safety.test.mjs',module:'.github/continuum/coin-wm-v3-shadow-model.mjs'},
  {test:'.github/continuum/ansk-baseline-triage.test.mjs',module:'.github/continuum/ansk-baseline-triage.mjs'},
  {test:'.github/continuum/repair-ledger-independent.test.mjs',module:'.github/continuum/repair-ledger.mjs'}],
 dev:[
  {test:'.github/continuum/cefr-shadow-benchmark.test.mjs',module:'.github/continuum/cefr-shadow-benchmark.mjs'},
  {test:'.github/continuum/ansk-recipe-replay.test.mjs',module:'.github/continuum/ansk-recipe-replay.mjs'},
  {test:'.github/continuum/repair-context-experience.test.mjs',module:'.github/continuum/repair-experience.mjs'}],
 holdout:[
  {test:'.github/continuum/dev-loop-kernel.test.mjs',module:'.github/continuum/dev-loop-kernel.mjs'},
  {test:'.github/continuum/repair-cycle-safety.test.mjs',module:'.github/continuum/repair-cycle-safety.mjs'},
  {test:'.github/continuum/ansk-lab-contract.test.mjs',module:'.github/continuum/ansk-lab-contract.mjs'},
  {test:'experimental/ccc1-counterexample-lab/policy.test.mjs',module:'experimental/ccc1-counterexample-lab/policy.mjs'}]
});
export const FAMILIES_BY_SPLIT=Object.freeze({train:['REL','EQ','LOGIC','CONST','NEG'],dev:['REL','EQ','LOGIC','CONST','NEG'],
 holdout:['REL','EQ','LOGIC','CONST','NEG','ARITH']});
export const FAULTS_PER_FAMILY=2, MAX_FAULT_TRIES=30, WINDOW_LINES=5, VISIBLE_BUDGET=10;
