// CCC1 ToolCapabilityGate: decides whether a reasoner TEST_REQUEST (contract test or repair) may run. Permissions come only from OWNER entries
// (the GOAL's grant plus GRANTED approvals); a reasoner can never widen them. The coding agent's own critic is asked
// with exactly the contract it would render: what the critic refuses (e.g. tests on the agent's control-plane code)
// stays refused even with owner approval. Rounds, deadline and CPU budget are enforced before every execution.
import {critique} from './repair-critic.mjs';
import {renderOwnerNodeContract} from './contract-test-template.mjs';
import {CAPABILITY,REPAIR_CAPABILITY} from './ccc1-envelope.mjs';
import {REPAIR_CHAINS} from '../../scripts/continuum-mobile-coding.mjs';

/** Would the existing coding agent accept a contract test for this module/export/cases at all? */
export function capabilityCheck(module,exportName,casesJson){
 const file=String(module).replace(/\.mjs$/,'.contract.test.mjs');
 const text=renderOwnerNodeContract(file,exportName,casesJson);
 if(text===null)return {allowed:false,reasons:['cases outside the contract grammar']};
 const v=critique({strategy:'SYNTHESIZE_NODE_CONTRACT_TEST',edits:[{file,oldText:'',newText:text}],contractSpec:{file,exportName,casesJson}});
 return {allowed:v.approved,reasons:v.reasons};
}

/** Owner permissions in force: the GOAL's grant plus every GRANTED approval's needs. */
export function effectivePermissions(entries){
 const goal=entries[0];
 const granted=new Set(goal?.permissions??[]);
 for(const a of entries.filter(e=>e.sender==='OWNER'&&e.kind==='APPROVAL'&&e.payload.decision==='GRANTED')){
  const req=entries.find(e=>e.messageId===a.payload.approvalRequest);
  for(const n of req?.payload.needs??[])granted.add(n);
 }
 return granted;
}

export const permissionFor=module=>CAPABILITY+':'+module;
/** A repair request names a fixed test chain; the owner grants node-repair (any chain) or node-repair:<chain>. */
export const repairPermissionFor=chain=>REPAIR_CAPABILITY+':'+chain;

/**
 * Decision for one TEST_REQUEST payload: ALLOW, NEEDS_APPROVAL (owner must grant `needs`) or DENY (with reasons).
 * usage: {rounds, cpuMillis} already spent in this task; now: epoch ms.
 */
export function gateTestRequest(payload,entries,{rounds,cpuMillis,now=Date.now()}){
 const goal=entries[0];
 const reasons=[];
 if(now>=goal.deadline)reasons.push('deadline passed');
 if(rounds>=goal.maxRounds)reasons.push('round limit '+goal.maxRounds+' reached');
 if(cpuMillis!==null&&cpuMillis>=goal.maxCpuMillis)reasons.push('CPU budget '+goal.maxCpuMillis+' ms used up');
 if(now+payload.budgetMinutes*60_000>goal.deadline)reasons.push('request budget exceeds the remaining time');
 if(payload.repair){
  if(!REPAIR_CHAINS.includes(payload.repair.chain))reasons.push('outside the worker capability: unknown test chain');
  if(reasons.length)return {decision:'DENY',reasons,needs:[]};
  const granted=effectivePermissions(entries);
  const need=repairPermissionFor(payload.repair.chain);
  if(granted.has(REPAIR_CAPABILITY)||granted.has(need))return {decision:'ALLOW',reasons:[],needs:[]};
  return {decision:'NEEDS_APPROVAL',reasons:['repair of this chain is not covered by the owner grant'],needs:[need]};
 }
 const capability=capabilityCheck(payload.module,payload.export,JSON.stringify(payload.cases));
 if(!capability.allowed)reasons.push('outside the worker capability: '+capability.reasons.join('; '));
 if(reasons.length)return {decision:'DENY',reasons,needs:[]};
 const granted=effectivePermissions(entries);
 const need=permissionFor(payload.module);
 if(granted.has(CAPABILITY)||granted.has(need))return {decision:'ALLOW',reasons:[],needs:[]};
 return {decision:'NEEDS_APPROVAL',reasons:['module not covered by the owner grant'],needs:[need]};
}
