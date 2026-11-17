/**
 * Independent acceptance oracle for bounded Kotlin operator proposals.
 * Compiles real source before and after a proposed method and executes
 * owner-specified values. The compiler itself is local; no cloud, eval,
 * generated shell, filesystem writes to the source tree or false DONE.
 *
 * This is a leaf-class RED/GREEN gate, not an Android build replacement.
 */
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import {spawnSync} from 'node:child_process';

const IDENT=/^[A-Za-z_][A-Za-z0-9_]{0,63}$/;
const PACKAGE=/^[a-zA-Z_][a-zA-Z0-9_]*(?:\.[a-zA-Z_][a-zA-Z0-9_]*)*$/;
const INTEGER=v=>Number.isSafeInteger(v)&&Math.abs(v)<=1000000000;
const MESSAGE_LIMIT=1200;

export function parseKotlinIntCases(casesJson){
 if(typeof casesJson!=='string'||casesJson.length>300)return null;
 let decoded;
 try{decoded=JSON.parse(casesJson);}catch{return null;}
 if(!Array.isArray(decoded)||decoded.length<1||decoded.length>4)return null;
 if(!decoded.every(c=>c&&typeof c==='object'&&!Array.isArray(c)&&
    INTEGER(c.input)&&INTEGER(c.expected)&&
    Object.keys(c).length===2))return null;
 return decoded;
}

function exec(cmd,args,cwd,timeoutMs){
 const r=spawnSync(cmd,args,{cwd,encoding:'utf8',timeout:timeoutMs,
  maxBuffer:1024*1024,shell:false,
  env:{...process.env,GIT_TERMINAL_PROMPT:'0'}});
 return {exitCode:r.status,error:r.error?.code??null,signal:r.signal??null,
  stdout:(r.stdout??'').slice(-MESSAGE_LIMIT),
  stderr:(r.stderr??'').slice(-MESSAGE_LIMIT)};
}
function succeeded(r){
 return r.exitCode===0&&!r.error&&!r.signal;
}
function compile(dir,source,acceptance){
 const sourceFile=path.join(dir,'Target.kt');
 const mainFile=path.join(dir,'Acceptance.kt');
 const jar=path.join(dir,'acceptance.jar');
 fs.writeFileSync(sourceFile,source,{mode:0o600});
 fs.writeFileSync(mainFile,acceptance,{mode:0o600});
 // Compilation has no dependency on installed Gradle or Android SDK.
 return exec('kotlinc',[sourceFile,mainFile,'-include-runtime','-d',jar],dir,120000);
}

export function assessKotlinDelegate({
 original,generated,className,methodName,casesJson
}={}){
 const cases=parseKotlinIntCases(casesJson);
 if(typeof original!=='string'||typeof generated!=='string'||
    original.length>300000||generated.length>300000||
    !IDENT.test(className??'')||!IDENT.test(methodName??'')||
    !cases||generated===original)
  return {passed:false,reason:'INVALID_ACCEPTANCE_SPEC'};
 const packageNames=[original,generated].map(s=>{
  const matches=[...s.matchAll(/^package\s+([^\s;]+)\s*$/gm)];
  return matches.length===1?matches[0][1]:null;
 });
 if(!packageNames[0]||packageNames[0]!==packageNames[1]||
    !PACKAGE.test(packageNames[0]))
  return {passed:false,reason:'UNSUPPORTED_PACKAGE'};
 const name=packageNames[0]+'.'+className;
 const lines=['import '+name,'','fun main() {'];
 for(const {input,expected} of cases)
  lines.push('    check('+className+'().'+methodName+'('+input+') == '+expected+')');
 lines.push('    println("CONTINUUM_KOTLIN_ACCEPTANCE_OK")','}','');
 const acceptance=lines.join('\n');
 const dir=fs.mkdtempSync(path.join(os.tmpdir(),'continuum-kotlin-accept-'));
 try{
  // The absent method must be the specific failing diagnostic; an arbitrary
  // red build is not evidence that the synthesis filled the requested gap.
  const before=compile(dir,original,acceptance);
  if(before.error==='ENOENT')
   return {passed:false,reason:'KOTLINC_NOT_AVAILABLE'};
  // A missing method is evidence only if it is the sole compiler error.
  // Do not credit an unrelated red baseline as a successful RED/GREEN cycle.
  const diagnostics=before.stderr.split(/\r?\n/)
   .filter(line=>/\berror\s*:|^e:\s/i.test(line));
  const missingMethod=new RegExp('\\b'+methodName+'\\b');
  const specificallyMissing=diagnostics.length>0&&diagnostics.every(line=>
   line.includes('Acceptance.kt')&&
   /unresolved reference/i.test(line)&&missingMethod.test(line));
  if(before.exitCode===0||before.error||before.signal||!specificallyMissing)
   return {passed:false,reason:'BASELINE_NOT_SPECIFICALLY_RED',before};
  const after=compile(dir,generated,acceptance);
  if(!succeeded(after))
   return {passed:false,reason:'CANDIDATE_KOTLIN_COMPILE_FAILED',before,after};
  const actual=exec('java',['-jar',path.join(dir,'acceptance.jar')],dir,30000);
  if(!succeeded(actual)||!actual.stdout.includes('CONTINUUM_KOTLIN_ACCEPTANCE_OK'))
   return {passed:false,reason:'CANDIDATE_BEHAVIOR_FAILED',before,after,actual};
  return {passed:true,reason:'RED_GREEN_KOTLIN_ACCEPTANCE',executed:cases.length,
   before:{exitCode:before.exitCode},after:{exitCode:after.exitCode},
   actual:{exitCode:actual.exitCode},androidBuildVerified:false};
 }finally{
  fs.rmSync(dir,{recursive:true,force:true});
 }
}
