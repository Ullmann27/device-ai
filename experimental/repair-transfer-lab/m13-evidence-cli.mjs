import fs from 'node:fs';
import path from 'node:path';
import {auditRecordedM13} from './m13-evidence-contract.mjs';
import {auditM13ModuleReplication} from './m13-module-replication.mjs';

const FILES={summary:'summary.json',hypotheses:'hypotheses.json',trainRows:'train-rows.json',devRows:'dev-rows.json'};
export function checkRecordedDirectory(directory,sourceSha,seal){
  if(typeof directory!=='string'||!path.isAbsolute(directory)||!fs.lstatSync(directory).isDirectory())
    throw Error('REGULAR_ABSOLUTE_DIRECTORY_REQUIRED');
  const parsed={};
  for(const [field,name] of Object.entries(FILES)){
    const full=path.join(directory,name);
    const flags=fs.constants.O_RDONLY|fs.constants.O_NONBLOCK|fs.constants.O_NOFOLLOW;
    if(typeof fs.constants.O_NOFOLLOW!=='number')throw Error('NOFOLLOW_UNSUPPORTED');
    const fd=fs.openSync(full,flags);
    try{
      const st=fs.fstatSync(fd);
      if(!st.isFile()||st.size<=0||st.size>16*1024*1024)throw Error('INVALID_EVIDENCE_FILE');
      parsed[field]=JSON.parse(fs.readFileSync(fd,{encoding:'utf8'}));
    }finally{fs.closeSync(fd)}
  }
  const recorded=auditRecordedM13({...parsed,expectedSourceSha:sourceSha,expectedSeal:seal});
  if (!recorded.valid) return recorded;
  // This is explicitly a second research assessment, not an execution or promotion approval.
  const moduleReplication=auditM13ModuleReplication(parsed);
  return Object.freeze({...recorded,moduleReplication});
}

if(process.argv[1] && path.resolve(process.argv[1])===path.resolve(new URL(import.meta.url).pathname)){
  try{
    const [dir,sha,seal]=process.argv.slice(2);
    const report=checkRecordedDirectory(dir,sha,seal);
    console.log(JSON.stringify(report,null,2));
    if(!report.valid)process.exitCode=2;
  }catch(e){console.error(JSON.stringify({valid:false,status:'UNVERIFIED',reason:String(e?.code||e?.message||e)}));process.exitCode=2}
}
