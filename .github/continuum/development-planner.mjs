import {analyzeSources} from './code-analyzer.mjs';

/**
 * Offline backlog planner: turns structural findings into small, reviewable
 * investigation tasks. No AI API, no writes, and no claim that a finding is a bug.
 */
export function prioritizeFindings(report,limit=12) {
  if(!report||!Array.isArray(report.findings))throw Error('Invalid analysis report');
  const priorities={DEAD_CLUSTER:3,TEST_ONLY:2,UNREFERENCED:1};
  return report.findings.filter(f=>f&&typeof f.kind==='string')
    .map(f=>({kind:f.kind,symbol:String(f.symbol??f.name??'').slice(0,120),
      file:String(f.file??f.definedIn?.[0]??'').slice(0,250),
      confidence:f.confidence??'unknown',
      score:priorities[f.kind]??0,
      action:'INVESTIGATE_ONLY'}))
    .filter(f=>f.score>0)
    .sort((a,b)=>b.score-a.score||a.file.localeCompare(b.file)||a.symbol.localeCompare(b.symbol))
    .slice(0,Math.max(0,Math.min(30,limit)));
}
export function planBacklog(root,limit=12){
  const result=analyzeSources(root);
  return {schemaVersion:1,mode:'review-required',analyzedAt:new Date().toISOString(),
    tasks:prioritizeFindings(result,limit)};
}
