/**
 * Real offline M4 repair acceptance. Run in a separate Node process at depth 0;
 * ordinary node-continuum checks intentionally skip recursive repair cases.
 * The resulting JUnit is retained outside the checkout and verified by hash.
 */
import fs from 'node:fs';
import path from 'node:path';
import crypto from 'node:crypto';
import {spawnSync} from 'node:child_process';
import {strictRepairDepth} from './repair-cycle-safety.mjs';
import {verifyJUnitReport} from './m4-junit-evidence.mjs';

export const MANDATORY_REPAIR_CASES=Object.freeze([
 'end-to-end: injected fault is reproduced, repaired in isolation, verified and committed',
 'regression search reverts only the culprit hunk and keeps unrelated changes',
 'a module that crashes while loading is located and gets its missing import back'
]);
const XML_NAME='repair-e2e-junit.xml';
const SHA=/^[0-9a-f]{64}$/;
const XML_NAME_REGEX=/<testcase\b((?:[^"]|"[^"]*")*?)(\/>|>([\s\S]*?)<\/testcase>)/g;

function readRegularXml(file){
 const s=fs.lstatSync(file);
 if(!s.isFile()||s.isSymbolicLink()||s.size<32||s.size>20*1024*1024)
  throw Error('Invalid repair JUnit proof file');
 return fs.readFileSync(file);
}

function verifyMandatoryCases(xml){
 const found=new Set();
 for(const entry of xml.matchAll(XML_NAME_REGEX)){
  const name=entry[1].match(/\bname="([^"]*)"/)?.[1];
  if(name&&!/<skipped\b/.test(entry[3]??''))found.add(name);
 }
 for(const name of MANDATORY_REPAIR_CASES){
  if(!found.has(name))throw Error('Missing or skipped mandatory repair E2E case: '+name);
 }
 return MANDATORY_REPAIR_CASES.length;
}

export function verifyRepairE2eRecord(evidenceDirectory,record){
 try{
  if(!record||record.passed!==true||!Number.isSafeInteger(record.executed)||record.executed<3||
     !SHA.test(record.sha256??'')||JSON.stringify(record.mandatoryRepairs)!==JSON.stringify(MANDATORY_REPAIR_CASES))
   return false;
  const file=path.join(evidenceDirectory,XML_NAME);
  const raw=readRegularXml(file);
  if(crypto.createHash('sha256').update(raw).digest('hex')!==record.sha256)return false;
  const summary=verifyJUnitReport(file);
  if(summary.executed!==record.executed||summary.failed!==0)return false;
  return verifyMandatoryCases(raw.toString('utf8'))===MANDATORY_REPAIR_CASES.length;
 }catch{return false;}
}

export function runRepairE2e(worktree,outDirectory,deadline){
 if(strictRepairDepth(process.env.CONTINUUM_REPAIR_DEPTH)!==0)
  throw Error('Nested repair E2E execution is forbidden');
 if(!Number.isSafeInteger(deadline)||deadline<=Date.now())
  throw Error('M4 repair E2E deadline expired');
 const root=fs.realpathSync(worktree);
 const out=fs.realpathSync(outDirectory);
 if(out===root||out.startsWith(root+path.sep))
  throw Error('Repair E2E proof must stay outside checkout');
 const tests=['repair-loop.test.mjs','repair-cycle-safety.integration.test.mjs']
  .map(file=>path.join(root,'.github','continuum',file));
 for(const file of tests){
  const stat=fs.lstatSync(file);
  if(!stat.isFile()||stat.isSymbolicLink())throw Error('Missing real M4 E2E test '+file);
 }
 const reportFile=path.join(out,XML_NAME);
 const available=deadline-Date.now();
 if(available<=0)throw Error('Repair E2E deadline expired');
 const env={...process.env,GIT_TERMINAL_PROMPT:'0',CONTINUUM_REPAIR_DEPTH:'0'};
 delete env.NODE_TEST_CONTEXT;
 const child=spawnSync(process.execPath,[
  '--test','--test-concurrency=1','--test-reporter=junit',
  '--test-reporter-destination='+reportFile,...tests],{
   cwd:root,env,timeout:Math.min(available,20*60_000),encoding:'utf8',
   shell:false,maxBuffer:8*1024*1024
  });
 if(child.status!==0||child.signal||child.error)
  throw Error('Real M4 repair E2E failed: '+String(child.error?.code??child.signal??child.status)+
   ' '+(child.stderr??'').slice(-500));
 const bytes=readRegularXml(reportFile);
 const summary=verifyJUnitReport(reportFile);
 verifyMandatoryCases(bytes.toString('utf8'));
 const record={passed:true,executed:summary.executed,
  mandatoryRepairs:[...MANDATORY_REPAIR_CASES],
  sha256:crypto.createHash('sha256').update(bytes).digest('hex')};
 if(!verifyRepairE2eRecord(out,record))throw Error('Inconsistent real M4 repair E2E evidence');
 return record;
}
