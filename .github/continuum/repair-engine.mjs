import fs from 'node:fs';
import path from 'node:path';
import {spawnSync} from 'node:child_process';

/** Bounded, deterministic repair engine. Never runs shell from logs. */
export function proposeRepairs(log, root) {
  const findings=[];
  const pattern=/^e:\s*(?:file:\/\/)?([^\n:]+\.kt):(\d+):(\d+):\s*(?:error:\s*)?Unresolved reference(?::|\s+)['`]?([A-Za-z_][A-Za-z_0-9]*)/gm;
  for(const m of log.slice(0,200000).matchAll(pattern)){
    const absolute=path.resolve(root,m[1].replace(/^\/+/,'')); 
    const relative=path.relative(root,absolute);
    if(relative.startsWith('..')||path.isAbsolute(relative))continue;
    findings.push({file:relative,line:Number(m[2]),column:Number(m[3]),symbol:m[4],kind:'UNRESOLVED_REFERENCE'});
    if(findings.length>=30)break;
  }
  return findings;
}
export function safeReplace(file, oldText, newText) {
  if(oldText===newText||!oldText||oldText.length>200||newText.length>200)throw Error('Unsafe replacement');
  if(!file.endsWith('.kt')||file.includes('..'))throw Error('Invalid Kotlin path');
  const source=fs.readFileSync(file,'utf8');
  if(source.split(oldText).length!==2)throw Error('Expected exactly one match');
  return source.replace(oldText,newText);
}
/** Only predefined compiler fixes. Human review required before applying. */
export function knownFixes(source) {
  const fixes=[];
  const pattern=/\.toSet\(\)\.takeLast\(/g;
  if(pattern.test(source))fixes.push({kind:'SET_TAKELAST',from:'.toSet().takeLast(',to:'.toSet().toList().takeLast('});
  return fixes;
}
export function validateInWorktree(directory, command=['./gradlew',':app:compileDebugUnitTestKotlin','--no-daemon']) {
  if(!Array.isArray(command)||command[0]!=='./gradlew')throw Error('Only Gradle allowed');
  const result=spawnSync(command[0],command.slice(1),{cwd:directory,timeout:180000,encoding:'utf8',maxBuffer:2000000,shell:false});
  return {passed:result.status===0,exitCode:result.status,output:(result.stdout??'').slice(-8000)+(result.stderr??'').slice(-8000)};
}
