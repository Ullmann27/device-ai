import {createHash} from 'node:crypto';
export const STRATEGIES=Object.freeze(['JS_TYPO_REFERENCE','JS_MISSING_IMPORT']);
const CONTEXT=new Set(['node.js.value','node.js.call']);
const FEATURE=new Set(['NO_HELPER_EXPORT','BOTH_AVAILABLE']);
const VALID=new Set(STRATEGIES),TOKEN=/^[a-z][a-z0-9-]{2,63}$/;
export const sha256=s=>createHash('sha256').update(s).digest('hex');
function canonical(x) {
 if(Array.isArray(x))return '['+x.map(canonical).join(',')+']';
 if(x&&typeof x==='object')return '{'+Object.keys(x).sort().map(k=>JSON.stringify(k)+':'+canonical(x[k])).join(',')+'}';
 return JSON.stringify(x);
}
export function validateFixture(f) {
 if(!f||!TOKEN.test(f.id??'')||!CONTEXT.has(f.contextKey)||!FEATURE.has(f.feature)||
  typeof f.source!=='string'||typeof f.helper!=='string'||f.source.length>2048||f.helper.length>2048||
  !Number.isSafeInteger(f.expected)||!f.candidates||Object.keys(f.candidates).length!==2||
  STRATEGIES.some(s=>typeof f.candidates[s]!=='string'||f.candidates[s].length>2048))
  throw Error('INVALID_FIXTURE');
 const match=/^import \{([a-z]+)\} from '\.\/helper\.mjs';\n/.exec(f.candidates.JS_MISSING_IMPORT);
 if(!match)throw Error('INVALID_IMPORT_CANDIDATE');
 const available=new RegExp('\\bexport\\s+(?:const|function)\\s+'+match[1]+'\\b').test(f.helper);
 if((available?'BOTH_AVAILABLE':'NO_HELPER_EXPORT')!==f.feature)throw Error('FORGED_SOURCE_FEATURE');
 return f;
}
export function sealFixtures(fixtures) {
 if(!Array.isArray(fixtures)||fixtures.length<10||fixtures.length>100||
  new Set(fixtures.map(f=>f.id)).size!==fixtures.length)throw Error('INVALID_HOLDOUT');
 fixtures.forEach(validateFixture);
 return sha256(canonical(fixtures.map(f=>({...f})).sort((a,b)=>a.id.localeCompare(b.id))));
}
export function validateObservation(r) {
 if(!r||!TOKEN.test(r.task??'')||!CONTEXT.has(r.contextKey)||!FEATURE.has(r.feature)||
  !VALID.has(r.strategy)||!/^[a-f0-9]{64}$/.test(r.sourceSha256??'')||
  typeof r.passed!=='boolean'||r.exitCode!==(r.passed?0:1)||r.executedTests!==1||
  r.processExited!==true||!/^[a-f0-9]{64}$/.test(r.runId??''))throw Error('UNSUPPORTED_OR_INCOMPLETE_ORACLE');
 return r;
}
/** One frozen acceptance test: the exit status and both TAP counters must tell the same story, else no oracle. */
export function tapOracle(status,log) {
 if(typeof log!=='string'||!/^# tests 1$/m.test(log))throw Error('TEST_ORACLE_INCOMPLETE');
 const has=line=>new RegExp('^# '+line+'$','m').test(log);
 if(status===0&&has('pass 1')&&has('fail 0'))return true;
 if(status===1&&has('pass 0')&&has('fail 1'))return false;
 throw Error('TEST_ORACLE_INCONSISTENT');
}
/** Research-only rule; locally generated observations never authorize CCC1 learning. */
export function learnConservativeGuards(observations,minimumCases=2) {
 if(!Array.isArray(observations)||observations.length>500||!Number.isSafeInteger(minimumCases)||
  minimumCases<2||minimumCases>20)throw Error('INVALID_GUARD_INPUT');
 const grouped=new Map(),seen=new Set(),sourceByTask=new Map();
 for(const record of observations) {
  validateObservation(record);
  const unique=record.task+'|'+record.strategy;
  if(seen.has(unique))throw Error('DUPLICATE_ORACLE_RECORD');
  seen.add(unique);
  if(sourceByTask.has(record.task)&&sourceByTask.get(record.task)!==record.sourceSha256)
   throw Error('PAIR_SOURCE_MISMATCH');
  sourceByTask.set(record.task,record.sourceSha256);
  const key=record.contextKey+'|'+record.feature;
  if(!grouped.has(key))grouped.set(key,new Map());
  const byTask=grouped.get(key);
  if(!byTask.has(record.task))byTask.set(record.task,new Map());
  byTask.get(record.task).set(record.strategy,record.passed);
 }
 const guards={};
 for(const [key,cases] of [...grouped].sort(([a],[b])=>a.localeCompare(b))) {
  const complete=[...cases.values()].filter(row=>STRATEGIES.every(s=>row.has(s)));
  const wins=STRATEGIES.map(strategy=>complete.filter(row=>row.get(strategy)===true&&
   row.get(STRATEGIES.find(s=>s!==strategy))===false).length);
  const choice=complete.length>=minimumCases&&wins.includes(complete.length)?
   STRATEGIES[wins.indexOf(complete.length)]:null;
  guards[key]=Object.freeze({choice,trainingCases:complete.length,
   disagreement:complete.length>=minimumCases&&choice===null});
 }
 return Object.freeze(guards);
}
/** Reject ambiguity; no output here changes rankCandidates, tests, or permissions. */
export function guardedShadowChoice(task,ranked,guards) {
 validateFixture(task);
 if(!Array.isArray(ranked)||ranked.length!==2||new Set(ranked.map(c=>c.strategy)).size!==2||
  ranked.some(c=>!VALID.has(c.strategy)))throw Error('INVALID_RANKING');
 const p=guards[task.contextKey+'|'+task.feature];
 if(!p||!VALID.has(p.choice))return Object.freeze({mode:'SHADOW',choice:null,
  reason:p?.disagreement?'CONTRADICTORY_COUNTEREXAMPLES':'INSUFFICIENT_EVIDENCE',
  writeAllowed:false,learningAllowed:false});
 return Object.freeze({mode:'SHADOW',choice:p.choice,reason:'FIXTURE_CONSENSUS_ONLY',
  writeAllowed:false,learningAllowed:false});
}
export function summarizeTrial(rows,expectedSha,actualSha) {
 if(!/^[a-f0-9]{64}$/.test(expectedSha)||actualSha!==expectedSha||!Array.isArray(rows)||
  rows.length<10||rows.length>100||new Set(rows.map(r=>r.task)).size!==rows.length||
  rows.some(r=>!TOKEN.test(r.task??'')||typeof r.baselinePassed!=='boolean'||
   typeof r.broadPassed!=='boolean'||typeof r.contextPassed!=='boolean'||
   !(typeof r.guardedPassed==='boolean'||r.guardedPassed===null)))
  throw Error('INVALID_OR_UNSEALED_TRIAL');
 const arms=Object.fromEntries(['baseline','broad','context','guarded'].map(arm=>{
  const tested=rows.filter(r=>r[arm+'Passed']!==null);
  const passed=tested.filter(r=>r[arm+'Passed']===true).length;
  return [arm,{attempts:tested.length,passed,failed:tested.length-passed,
   abstentions:rows.length-tested.length}];
 }));
 return Object.freeze({schema:1,mode:'RESEARCH_SHADOW_ONLY',verifiedHoldoutSha256:actualSha,
  tasks:rows.length,arms,productionActivationAllowed:false,ownerApprovalGranted:false,
  limitations:['Selected synthetic JS fixtures only','No generative coding or OS sandbox',
   'No authenticated external oracle','No Fold7 or Android execution',
   'No CCC1 ledger learning or release authorization']});
}
