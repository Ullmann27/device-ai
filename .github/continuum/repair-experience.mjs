import fs from 'node:fs';
import crypto from 'node:crypto';
/** Deterministic bounded repair experience store, independent of paid APIs. */
export function mergeExperiences(previous=[],observations=[]) {
 const entries=new Map();
 for(const item of [...previous,...observations]){
  if(!item||!['COMPILES','REJECTED'].includes(item.status)||typeof item.kind!=='string')continue;
  const key=item.kind.slice(0,80);
  const current=entries.get(key)??{kind:key,successes:0,failures:0};
  current[item.status==='COMPILES'?'successes':'failures']++;
  entries.set(key,current);
 }
 return [...entries.values()].sort((a,b)=>(b.successes/(b.successes+b.failures))-(a.successes/(a.successes+a.failures))).slice(0,100);
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
