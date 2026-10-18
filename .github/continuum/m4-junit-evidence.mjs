#!/usr/bin/env node
/** Fail-closed evidence gate for locally executed Continuum and backend Node tests. */
import fs from 'node:fs';
import path from 'node:path';
import {fileURLToPath} from 'node:url';

function declaredCount(xml,name){
 const match=xml.match(new RegExp('<!--\\s*'+name+'\\s+(\\d+)\\s*-->'));
 if(!match)throw Error('Missing Node JUnit '+name+' total');
 const n=Number(match[1]);
 if(!Number.isSafeInteger(n))throw Error('Invalid Node JUnit '+name+' total');
 return n;
}

export function verifyJUnitXml(xml){
 if(typeof xml!=='string'||xml.length<32||Buffer.byteLength(xml,'utf8')>20*1024*1024)
  throw Error('Missing or unsafe Node JUnit document');
 if(!/^\s*<\?xml\b/.test(xml)||!/<testsuites(?:\s[^>]*)?>[\s\S]*<\/testsuites>\s*$/.test(xml))
  throw Error('Malformed Node JUnit document');
 const testcase=/<testcase\b((?:[^>"]|"[^"]*")*?)(\/>|>([\s\S]*?)<\/testcase>)/g;
 let caseCount=0,skipped=0,todo=0,failed=0,placeholders=0;
 for(const match of xml.matchAll(testcase)){
  caseCount++;
  const attrs=match[1],body=match[3]??'';
  if(/\bname="[^"]*\.test\.m?js"/.test(attrs))placeholders++;
  const skippedTag=body.match(/<skipped\b([^>]*)\/?\s*>/);
  if(skippedTag){
   if(/\btype="todo"/.test(skippedTag[1]))todo++;
   else skipped++;
  }
  if(/<(?:failure|error)\b/.test(body))failed++;
 }
 const declared={
  tests:declaredCount(xml,'tests'),pass:declaredCount(xml,'pass'),
  fail:declaredCount(xml,'fail'),cancelled:declaredCount(xml,'cancelled'),
  skipped:declaredCount(xml,'skipped'),todo:declaredCount(xml,'todo')
 };
 // Node's JUnit reporter emits a <testsuite> for each nested parent test.
 // Those parents count in its declared 'tests' and 'pass' totals, even though
 // only their children are represented by <testcase> entries.
 const groupCount=[...xml.matchAll(/<testsuite\b(?=[\s>])/g)].length;
 const total=caseCount+groupCount;
 const executed=total-skipped-todo-placeholders;
 if(total!==declared.tests||caseCount===0||executed<=0||
    failed!==declared.fail||skipped!==declared.skipped||todo!==declared.todo||
    failed!==0||declared.cancelled!==0||
    declared.pass!==executed||
    (xml.match(/<testcase\b/g)??[]).length!==caseCount)
  throw Error('Node JUnit has incomplete, failing or inconsistent executed-test evidence');
 return {total,executed,failed,skipped,todo,placeholders,groupCount};
}

/** Parse a local JUnit report. Byte-consistent consumers can call verifyJUnitXml
 * with an already-opened document instead of reopening it after hashing. */
export function verifyJUnitReport(file){
 const info=fs.lstatSync(file);
 if(!info.isFile()||info.isSymbolicLink()||info.size<32||info.size>20*1024*1024)
  throw Error('Missing or unsafe Node JUnit report');
 const xml=fs.readFileSync(file,'utf8');
 return {file:path.resolve(file),...verifyJUnitXml(xml)};
}

/** Critical M4 acceptance must prove an actual broken-code -> verified-patch cycle. */
const MANDATORY_REPAIRS=Object.freeze([
 'end-to-end: injected fault is reproduced, repaired in isolation, verified and committed',
 'regression search reverts only the culprit hunk and keeps unrelated changes',
 'a module that crashes while loading is located and gets its missing import back'
]);

export function verifyM4AcceptanceEvidence(repairFile,nodeFile,backendFile){
 const reports=[repairFile,nodeFile,backendFile].map(verifyJUnitReport);
 const xml=fs.readFileSync(repairFile,'utf8');
 const executed=new Set();
 const cases=/<testcase\b((?:[^>"]|"[^"]*")*?)(\/>|>([\s\S]*?)<\/testcase>)/g;
 for(const item of xml.matchAll(cases)){
  const name=item[1].match(/\bname="([^"]*)"/)?.[1];
  if(name&&!/<skipped\b/.test(item[3]??''))executed.add(name);
 }
 for(const name of MANDATORY_REPAIRS){
  if(!executed.has(name))throw Error('Missing or skipped mandatory M4 repair: '+name);
 }
 return {schema:'continuum-m4-test-evidence',version:2,
  reports,mandatoryRepairs:[...MANDATORY_REPAIRS]};
}

if(process.argv[1]&&path.resolve(process.argv[1])===fileURLToPath(import.meta.url)){
 try{
  if(process.argv.length!==5)throw Error('Usage: m4-junit-evidence.mjs <repair.xml> <continuum.xml> <backend.xml>');
  const evidence=verifyM4AcceptanceEvidence(...process.argv.slice(2));
  process.stdout.write(JSON.stringify(evidence)+'\n');
 }catch(error){
  console.error('M4 regression evidence rejected:',error.message);
  process.exitCode=1;
 }
}
