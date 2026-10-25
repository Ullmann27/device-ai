import {spawnSync} from 'node:child_process';
import {fileURLToPath} from 'node:url';
import {numericSuite,numericSuiteHash} from './numeric-suite.mjs';
const WORKER=fileURLToPath(new URL('./numeric-worker.mjs',import.meta.url));

export function createNumericVerifier(input){
 const suite=numericSuite(input),suiteSha256=numericSuiteHash(suite);
 const verifier=request=>{
  let result={status:'ABSTAIN',checks:0,counterexample:null};
  const timeout=Math.min(5000,request.deadline-Date.now());
  if(request.base===suite.sourceCommit&&request.sources.length===1&&
     request.sources[0].file===suite.file&&request.sources[0].source.length<=1600&&timeout>0){
   const child=spawnSync(process.execPath,['--max-old-space-size=48',WORKER],{
    shell:false,encoding:'utf8',input:JSON.stringify({suite,source:request.sources[0].source}),
    timeout,maxBuffer:65536,env:{NODE_OPTIONS:'',NODE_V8_COVERAGE:'',NODE_USE_ENV_PROXY:'0'}});
   if(child.status===0&&!child.error&&!child.signal){
    try{result=JSON.parse(child.stdout);}catch{}
   }
  }
  return {schema:'continuum-candidate-verification',version:1,base:request.base,
   candidateSha256:request.candidateSha256,suiteSha256,...result};
 };
 Object.defineProperties(verifier,{suiteSha256:{value:suiteSha256},expectedChecks:{value:suite.cases.length+suite.relations.length}});
 return Object.freeze(verifier);
}
