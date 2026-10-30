/** M3 · pure bookkeeping for the transfer trial; no I/O, no authority. */
export const ARMS=Object.freeze(['baseline','broad','context','guarded','guardedVerify','verifyOnly']);

/** Independent verification under a test budget: try candidates in order, stop at the first that passes.
 * Returns {passed:true|false|null, runs}; null means nothing was tried (an abstention), never a success. */
export function verifyWithinBudget(order,runTest,budget) {
 if(!Array.isArray(order)||!Number.isSafeInteger(budget)||budget<0||budget>4||typeof runTest!=='function')
  throw Error('INVALID_VERIFY_REQUEST');
 let runs=0;
 for(const strategy of order.slice(0,budget)){
  runs++;
  const passed=runTest(strategy);
  if(typeof passed!=='boolean')throw Error('ORACLE_NOT_BOOLEAN');
  if(passed)return {passed:true,runs,strategy};
 }
 return {passed:runs===0?null:false,runs,strategy:null};
}

/** Per arm and per family: attempts, passes, failures, abstentions and test runs. A negative-control task that
 * any arm reports as passed is impossible by construction and aborts the summary. */
export function summarizeTransfer(rows,familyOf) {
 if(!Array.isArray(rows)||rows.length<10||new Set(rows.map(r=>r.task)).size!==rows.length)throw Error('INVALID_ROWS');
 const cell=()=>({attempts:0,passed:0,failed:0,abstentions:0,testRuns:0});
 const arms={},byFamily={};
 for(const arm of ARMS)arms[arm]=cell();
 for(const r of rows){
  const family=familyOf(r.task);
  if(!family)throw Error('UNKNOWN_FAMILY');
  byFamily[family]??=Object.fromEntries(ARMS.map(a=>[a,cell()]));
  for(const arm of ARMS){
   const v=r[arm];
   if(!v||!(v.passed===null||typeof v.passed==='boolean')||!Number.isSafeInteger(v.runs)||v.runs<0)
    throw Error('INVALID_ARM_RESULT');
   if(family.startsWith('negative')&&v.passed===true)throw Error('NEGATIVE_CONTROL_REPORTED_SUCCESS');
   for(const c of [arms[arm],byFamily[family][arm]]){
    if(v.passed===null)c.abstentions++;else{c.attempts++;c[v.passed?'passed':'failed']++;}
    c.testRuns+=v.runs;
   }
  }
 }
 return Object.freeze({schema:1,mode:'RESEARCH_SHADOW_ONLY',tasks:rows.length,arms,byFamily,
  productionActivationAllowed:false,learningAllowed:false});
}
