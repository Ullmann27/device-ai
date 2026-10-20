import fs from 'node:fs';

/** A measured paired trial is a research observation, never a training signal.
 * Compiler/test times include validation; planning is reported separately.
 * One success is not an estimated generalization rate or a speedup claim.
 */
export function summarizeRepairTransfer(arms) {
 if(!Array.isArray(arms)||arms.length!==2||
    new Set(arms.map(a=>a.name)).size!==2||
    !arms.every(a=>['recipe','control'].includes(a.name)&&
      typeof a.verified==='boolean'&&Number.isFinite(a.planningMs)&&a.planningMs>=0&&
      Number.isFinite(a.validationMs)&&a.validationMs>=0))
  throw Error('Invalid paired repair observations');
 const rows=arms.map(a=>({
  arm:a.name,attempts:1,verifiedRepairs:Number(a.verified),
  failedRepairs:Number(!a.verified),observedFailureRate:Number(!a.verified),
  planningMs:a.planningMs,validationMs:a.validationMs,
  totalMs:a.planningMs+a.validationMs
 }));
 const recipe=rows.find(a=>a.arm==='recipe'),control=rows.find(a=>a.arm==='control');
 return {sampleSizePerArm:1,rows,
  recipeMinusControlMs:recipe.totalMs-control.totalMs,
  conclusion:recipe.verifiedRepairs&&control.verifiedRepairs
   ?'BOTH_VERIFIED_NO_GENERALIZATION_CLAIM':'INCOMPLETE_OR_FAILED_COMPARISON',
  modelTrainingAuthorized:false,productionRankingUpdateAuthorized:false};
}
/** Strategy outcome aggregation (Laplace-ranked). Persisted through repair-ledger.mjs,
 * which keys experiences as "<diagnostic kind>|<strategy>". */
export function mergeExperiences(previous=[],observations=[]) {
 const entries=new Map();
 for(const item of [...previous,...observations]){
  if(!item||typeof item.kind!=='string'||!item.kind.trim()||item.kind.length>80)continue;
  const aggregated=Number.isSafeInteger(item.successes)&&Number.isSafeInteger(item.failures)
    &&item.successes>=0&&item.failures>=0;
  const observed=item.status==='COMPILES'||item.status==='REJECTED';
  if(!aggregated&&!observed)continue;
  const current=entries.get(item.kind)??{kind:item.kind,successes:0,failures:0};
  const successes=current.successes+(aggregated?item.successes:Number(item.status==='COMPILES'));
  const failures=current.failures+(aggregated?item.failures:Number(item.status==='REJECTED'));
  if(!Number.isSafeInteger(successes)||!Number.isSafeInteger(failures))throw Error('Experience counter overflow');
  entries.set(item.kind,{kind:item.kind,successes,failures});
 }
 return [...entries.values()].sort((a,b)=>{
  const scoreA=(a.successes+1)/(a.successes+a.failures+2);
  const scoreB=(b.successes+1)/(b.successes+b.failures+2);
  return scoreB-scoreA||a.kind.localeCompare(b.kind);
 }).slice(0,100);
}
export function summarizeExperiences(observations=[]) {
 const grouped=new Map();
 for(const item of observations){
  if(!item||!['COMPILES','REJECTED'].includes(item.status))continue;
  const key=String(item.kind??'').slice(0,80);
  const record=grouped.get(key)??{kind:key,successes:0,failures:0};
  record[item.status==='COMPILES'?'successes':'failures']++;
  grouped.set(key,record);
 }
 return [...grouped.values()].sort((a,b)=>a.kind.localeCompare(b.kind));
}
export function verifyExperienceFile(data){
 if(!data||data.schemaVersion!==1||!Array.isArray(data.experiences)||data.experiences.length>100)throw Error('Invalid experience store');
 for(const x of data.experiences)if(!x||typeof x.kind!=='string'||x.kind.length>80||!Number.isSafeInteger(x.successes)||!Number.isSafeInteger(x.failures)||x.successes<0||x.failures<0)throw Error('Invalid experience entry');
 return data.experiences;
}
export function loadExperienceFile(file){
 if(!fs.existsSync(file))return [];
 const raw=fs.readFileSync(file,'utf8');
 if(raw.length>50000)throw Error('Experience store too large');
 return verifyExperienceFile(JSON.parse(raw));
}
export function saveExperienceFile(file,entries){
 const payload=JSON.stringify({schemaVersion:1,experiences:entries},null,2)+'\n';
 const tmp=file+'.tmp-'+process.pid;
 fs.writeFileSync(tmp,payload,{flag:'wx'});
 fs.renameSync(tmp,file);
}
