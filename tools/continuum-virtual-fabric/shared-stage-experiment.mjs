import { createHash } from 'node:crypto';
import { ComputeReuseBroker } from './reuse-broker.mjs';
import { matrixWork } from './reuse-experiment.mjs';
const sha=s=>createHash('sha256').update(s).digest('hex');
const digest=n=>n.toString(16).padStart(64,'0');

/**
 * 3000 DISTINCT final tasks share 30 expensive deterministic preprocessing stages.
 * This simulates reusable code analysis/embedding preprocessing, NOT actual AST,
 * embedding, neural inference or a measured production workload.
 */
export function runSharedStageExperiment({jobs=3000,sourceVersions=30}={}) {
  if(!Number.isSafeInteger(jobs)||jobs<1||jobs>10_000 ||
    !Number.isSafeInteger(sourceVersions)||sourceVersions<1||sourceVersions>jobs){
    throw new TypeError('Invalid experiment size');
  }
  // Research adapter: SHA protects payload integrity but NOT worker authenticity.
  const broker=new ComputeReuseBroker({
    maxCached:10_000,
    verifyReceipt:(stage,receipt)=>receipt.inputDigest===stage.inputDigest &&
      typeof receipt.value==='string' && sha(receipt.value)===receipt.outputSha256
  });
  const optimized=[];
  let expensiveStages=0;
  for(let i=0;i<jobs;i++){
    const source=i%sourceVersions;
    const inputDigest=digest(source);
    const r=broker.submit({
      id:'stage-'+i,tenantId:'owner',
      modelRevision:digest(42),inputDigest,parametersDigest:digest(43),
      policyRevision:digest(44),memoryEpoch:0,
      taskType:'PURE',deterministic:true
    });
    let sharedResult;
    if(r.action==='CACHE_HIT') sharedResult=r.result;
    else {
      if(r.action!=='EXECUTE') throw new Error('Unexpected pending computation in synchronous experiment');
      // The expensive stage is a 12x12 matrix kernel, not a real code parser.
      sharedResult=matrixWork(source);
      expensiveStages++;
      broker.complete(r.jobId,{inputDigest,value:sharedResult,outputSha256:sha(sharedResult)});
    }
    // Every task executes its OWN unique final transformation.
    optimized.push(sha(sharedResult+':unique-task:'+i));
  }
  for(let i=0;i<jobs;i++){
    const fresh=matrixWork(i%sourceVersions);
    const independent=sha(fresh+':unique-task:'+i);
    if(independent!==optimized[i]) throw new Error('Independent result mismatch: '+i);
  }
  return {jobs,uniqueFinalOutputs:new Set(optimized).size,sourceVersions,
    baselineExpensiveStages:jobs,optimizedExpensiveStages:expensiveStages,
    avoidedExpensiveStages:jobs-expensiveStages,
    heavyStageWorkRatio:jobs/expensiveStages,
    correctAllOutputs:true,
    caveat:'Synthetic shared preprocessing; no measured autonomous coding-agent gain'};
}
if(process.argv[1] && process.argv[1].endsWith('/shared-stage-experiment.mjs')){
  for(const sourceVersions of [30,3000]){
    process.stdout.write(JSON.stringify(runSharedStageExperiment({sourceVersions}),null,2)+'\n');
  }
}
