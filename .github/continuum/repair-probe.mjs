import fs from 'node:fs';
import {collectKotlin} from './code-analyzer.mjs';
import {knownFixes} from './repair-engine.mjs';
const root='app/src';
const files=collectKotlin(root);
const candidates=[];
for(const file of files){
 for(const fix of knownFixes(file.content)){
  candidates.push({file:file.path,kind:fix.kind,oldText:fix.from,newText:fix.to,
   approval:'REQUIRED',reason:'Static heuristic only; verify types and compile before applying'});
 }
}
fs.mkdirSync('developer-agent-report',{recursive:true});
fs.writeFileSync('developer-agent-report/repair-candidates.json',
 JSON.stringify({schemaVersion:1,candidates:candidates.slice(0,100)},null,2)+'\n');
console.log('Identified',candidates.length,'candidate compiler fixes; no source changes applied');
