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

export function verifyJUnitReport(file){
 const info=fs.lstatSync(file);
 if(!info.isFile()||info.isSymbolicLink()||info.size<32||info.size>20*1024*1024)
  throw Error('Missing or unsafe Node JUnit report');
 const xml=fs.readFileSync(file,'utf8');
 if(!/^\s*<\?xml\b/.test(xml)||!/<testsuites(?:\s[^>]*)?>[\s\S]*<\/testsuites>\s*$/.test(xml))
  throw Error('Malformed Node JUnit document');
 const testcase=/<testcase\b((?:[^>"]|"[^"]*")*?)(\/>|>([\s\S]*?)<\/testcase>)/g;
 let total=0,skipped=0,failed=0,placeholders=0;
 for(const match of xml.matchAll(testcase)){
  total++;
  const attrs=match[1],body=match[3]??'';
  if(/\bname="[^"]*\.test\.m?js"/.test(attrs))placeholders++;
  if(/<skipped\b/.test(body))skipped++;
  if(/<(?:failure|error)\b/.test(body))failed++;
 }
 const declared={
  tests:declaredCount(xml,'tests'),pass:declaredCount(xml,'pass'),
  fail:declaredCount(xml,'fail'),cancelled:declaredCount(xml,'cancelled'),
  skipped:declaredCount(xml,'skipped')
 };
 const executed=total-skipped-placeholders;
 if(total!==declared.tests||total===0||executed<=0||
    failed!==declared.fail||skipped!==declared.skipped||
    failed!==0||declared.cancelled!==0||
    declared.pass!==executed||
    (xml.match(/<testcase\b/g)??[]).length!==total)
  throw Error('Node JUnit has incomplete, failing or inconsistent executed-test evidence');
 return {file:path.resolve(file),total,executed,failed,skipped,placeholders};
}

if(process.argv[1]&&path.resolve(process.argv[1])===fileURLToPath(import.meta.url)){
 try{
  if(process.argv.length!==4)throw Error('Usage: m4-junit-evidence.mjs <continuum.xml> <backend.xml>');
  const reports=process.argv.slice(2).map(verifyJUnitReport);
  process.stdout.write(JSON.stringify({schema:'continuum-m4-test-evidence',version:1,reports})+'\n');
 }catch(error){
  console.error('M4 regression evidence rejected:',error.message);
  process.exitCode=1;
 }
}
