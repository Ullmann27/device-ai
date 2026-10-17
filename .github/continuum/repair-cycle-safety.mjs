/**
 * An inherited repair depth is a recursion boundary, not a loose numeric hint.
 * Invalid values must halt the worker instead of becoming NaN or a negative
 * number that bypasses the nested-session check.
 */
export function strictRepairDepth(raw){
 if(raw===undefined)return 0;
 if(typeof raw!=='string'||!/^(0|[1-9][0-9]*)$/.test(raw))
  throw Error('Invalid CONTINUUM_REPAIR_DEPTH');
 const depth=Number(raw);
 if(!Number.isSafeInteger(depth))throw Error('Invalid CONTINUUM_REPAIR_DEPTH');
 return depth;
}

/** Every exit path from a bounded session needs an auditable end timestamp. */
export function finishRepairReport(report,outcome,finishedAt=new Date().toISOString()){
 report.outcome=outcome;
 report.finishedAt=finishedAt;
 return report;
}
