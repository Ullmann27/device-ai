import fs from 'node:fs';
import crypto from 'node:crypto';
/** Deterministic bounded repair experience store, independent of paid APIs. */
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

/** Merge this run's verified compiler outcomes with the restored bounded history. */
export function ingestLabReport(report,previous=[]){
 if(!report||report.schemaVersion!==1||report.mode!=='test'||report.appliedToRepository!==false
    ||!Array.isArray(report.candidates)||report.candidates.length>10)throw Error('Invalid repair lab report');
 const observations=report.candidates.filter(item=>
   item&&((item.status==='COMPILES'&&item.exitCode===0)||item.status==='REJECTED'));
 return mergeExperiences(previous,observations);
}
if(process.argv[1]&&import.meta.url===new URL('file://'+process.argv[1]).href){
 const reportFile='developer-agent-report/repair-lab.json';
 const historyFile='developer-agent-report/repair-history.json';
 const report=JSON.parse(fs.readFileSync(reportFile,'utf8'));
 const next=ingestLabReport(report,loadExperienceFile(historyFile));
 saveExperienceFile(historyFile,next);
 console.log('Persisted repair experience kinds:',next.length);
}
