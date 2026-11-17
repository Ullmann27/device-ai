/** Fixed worker for the ALREADY RESTRICTED numeric grammar. node:vm is not an
 * OS sandbox. No general JS, objects, imports, calls, loops or properties are
 * admitted by isLocalNumericPatch; inputs are primitive finite numbers only. */
import fs from 'node:fs';
import vm from 'node:vm';
import {numericSuite} from './numeric-suite.mjs';
import {isLocalNumericPatch} from '../../../.github/continuum/local-numeric-patch.mjs';

let checks=0;
let result={status:'ABSTAIN',checks:0,counterexample:null};
try{
 const chunks=[];let bytes=0;
 for(;;){const b=Buffer.alloc(4096);const n=fs.readSync(0,b,0,b.length,null);if(!n)break;
  bytes+=n;if(bytes>65536)throw Error('INPUT_LIMIT');chunks.push(b.subarray(0,n));}
 const {suite:raw,source}=JSON.parse(Buffer.concat(chunks).toString('utf8'));
 const suite=numericSuite(raw);
 if(!isLocalNumericPatch({file:suite.file,oldText:source,newText:source}))throw Error('GRAMMAR');
 const signature=/^\s*export\s+function\s+([A-Za-z_][A-Za-z0-9_]*)\s*\(([^()]*)\)/.exec(source);
 if(signature[1]!==suite.exportName||signature[2].split(',').length!==suite.arity)throw Error('EXPORT');
 // One bounded context for the complete frozen suite, instead of up to512
 // separate contexts/watchdogs. Candidate grammar/capability restrictions stay
 // unchanged; only this trusted wrapper iterates the finite primitive inputs.
 // 1s is the aggregate VM limit, within the unchanged5s/48MiB subprocess limit.
 const inputs=[...suite.cases.map(c=>c.args),...suite.relations.flatMap(r=>[r.left,r.right])];
 const script=new vm.Script('(()=>{const compute=('+source.replace(/^\s*export\s+/,'')+');return __inputs.map(args=>compute(...args));})()');
 const values=script.runInNewContext({__inputs:inputs},{timeout:1000,contextCodeGeneration:{strings:false,wasm:false}});
 if(!Array.isArray(values)||values.length!==inputs.length)throw Error('RESULT_SHAPE');
 let index=0;
 const evaluate=()=>{
  const v=values[index++];
  return typeof v==='number'&&Number.isFinite(v)&&Math.abs(v)<=1e12?v:null;
 };
 const fail=(id,inputs,actual,expected)=>({status:'FAIL',checks,
  counterexample:{id,file:suite.file,inputs,actual,expected}});
 result={status:'PASS',checks:0,counterexample:null};
 for(const c of suite.cases){
  const actual=evaluate();checks++;
  if(actual===null||actual!==c.expected){result=fail(c.id,c.args,actual,c.expected);break;}
 }
 if(result.status==='PASS')for(const r of suite.relations){
  const a=evaluate(),b=evaluate();checks++;
  if(a===null||b===null||!(r.relation==='equal'?a===b:a<=b)){
   result=fail(r.id,[...r.left,...r.right],a,b);break;
  }
 }
 result.checks=checks;
}catch(error){
 // Diagnostic only: no source, values, private context or additional evidence fields.
 process.stderr.write(error?.code==='ERR_SCRIPT_EXECUTION_TIMEOUT'?'OMEGA_VM_TIMEOUT\n':'OMEGA_WORKER_REJECTED\n');
 result={status:'ABSTAIN',checks:0,counterexample:null};
}
process.stdout.write(JSON.stringify(result)+'\n');
