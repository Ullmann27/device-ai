import { solve, applyProgram, runTypedModel, oracle } from '../mastercode-shadow/search.mjs';
import { fixtures } from '../mastercode-shadow/fixtures.mjs';
import { executeShadowEvolutionCycle, evolutionDigest, summarizeExistingLedger } from './evolution-core.mjs';
import { verifyLedger } from '../../.github/continuum/repair-ledger.mjs';

/** Read-only connection to the authoritative CCC1 repair-ledger schema. */
export function reviewVerifiedCcc1Ledger(ledger) {
  return summarizeExistingLedger(verifyLedger(structuredClone(ledger)));
}

/** The adapter is intentionally a single opt-in, non-persistent research cycle; no worker is started. */
export function runMastercodeSelfPlayOnce(trustedSnapshot, { cycleId = 1, maxEvaluations = 6 } = {}) {
  // MASTER-EXP fixtures are disclosed synthetic examples, not real CCC1 patches.
  const healthy = fixtures.slice(0, 3).map(t => ({
    id: t.id, policy: {sliceEndAdjustment: 0, clampNonnegative: true},
    visible: t.visible, blind: t.blind
  }));
  return executeShadowEvolutionCycle({
    snapshot: trustedSnapshot, baselineTasks: healthy, maxTasks: 3, maxEvaluations, cycleId,
    solver: (solverView, budget) => solve(solverView, { maxDepth: 2, maxEvaluations: budget.maxEvaluations,
      crystallizeMacros: false }),
    independentVerifier: (task, candidate) => {
      if (!candidate?.solved || !Array.isArray(candidate.chain)) return null;
      const patched = applyProgram(task, candidate.chain);
      if (!patched) return null;
      let failed = 0;
      for (const tc of task.blind) {
        // An independently specified reference oracle is checked for consistency.
        if (oracle(tc.input) !== tc.expected) throw Error('INVALID_HOLDOUT_ORACLE');
        if (runTypedModel(patched, tc.input) !== tc.expected) failed++;
      }
      return {
        evaluator: 'independent', taskHash: evolutionDigest(task),
        candidateHash: evolutionDigest(candidate), testSetHash: evolutionDigest(task.blind),
        executedTests: task.blind.length, failedTests: failed,
        exitCode: failed ? 1 : 0, blindPass: failed === 0,
        evidenceComplete: true, runnerError: false
      };
    }
  });
}
