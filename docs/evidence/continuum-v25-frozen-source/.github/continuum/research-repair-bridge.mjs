/**
 * Optional research junction for the canonical CCC1 independent OS worktrees,
 * the existing counterexample veto, and synthetic feedback fingerprint lineage.
 * A callback may only be supplied by a trusted worker, never by model output.
 */
import {canonical,sha256Hex} from './development-backlog.mjs';
import {validateCcc1CandidateBinding,runIndependentCcc1PatchVerification,
  CCC1_RESEARCH_OUTCOMES} from './ccc1-independent-verifier.mjs';
import {runCandidateVerifier} from './repair-research-verifier.mjs';
import {evaluateResearchRepairEvidence} from './research-evidence-gate.mjs';

export function runResearchRepairBridge({typedTask,candidate,file,projectRoot,stateDir,
  executor,deadline=Date.now()+900000,candidateVerifier=null,feedbackFingerprint=null}={}){
  const binding=validateCcc1CandidateBinding({typedTask,candidate,file});
  const {task,candidate:bound}=binding;
  const sources=[{file,source:bound.code}];
  const verifierRequestSha256=sha256Hex(canonical({base:task.sourceCommit,sources}));
  // The original CCC1 call verifies canonical owner grant, fixed test profile,
  // branded OS executor and two fresh worktrees. There is no host fallback.
  const ccc1Receipt=runIndependentCcc1PatchVerification({
    typedTask:task,candidate:bound,file,projectRoot,stateDir,executor,deadline
  });
  // No counterexample callback after an interrupted or rejected CCC1 attempt.
  const verdict=ccc1Receipt.status===CCC1_RESEARCH_OUTCOMES.PASS?
    runCandidateVerifier(candidateVerifier,{base:task.sourceCommit,sources,deadline}):null;
  const identity={sourceCommit:task.sourceCommit,taskSha256:bound.taskSha256,
    sourceSha256:bound.sourceSha256,candidateSha256:bound.candidateSha256,
    verifierRequestSha256};
  return evaluateResearchRepairEvidence({identity,ccc1Receipt,
    verifierVerdict:verdict,feedbackFingerprint});
}
