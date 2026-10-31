import { createHash } from 'node:crypto';
import { ComputeReuseBroker } from './reuse-broker.mjs';

/**
 * Real local JS matrix multiplication for a synthetic repeated-work benchmark.
 * No LLM training/inference is performed; no new CPU/GPU capacity is created.
 */
export function matrixWork(seed, size=12) {
  if (!Number.isSafeInteger(seed) || seed<0 || seed>100_000 || !Number.isInteger(size) || size<1 || size>32) {
    throw new TypeError('Invalid matrix dimensions or seed');
  }
  const a=Array.from({length:size},(_,i)=>Array.from({length:size},(_,j)=>(seed+i*3+j*7)%19));
  const b=Array.from({length:size},(_,i)=>Array.from({length:size},(_,j)=>(seed*5+i*11+j*2)%17));
  const c=Array.from({length:size},()=>Array(size).fill(0));
  for(let i=0;i<size;i++) for(let j=0;j<size;j++) for(let k=0;k<size;k++) {
    c[i][j]+=a[i][k]*b[k][j];
  }
  return createHash('sha256').update(JSON.stringify(c)).digest('hex');
}
const digest=x=>x.toString(16).padStart(64,'0');
const sha=x=>createHash('sha256').update(x).digest('hex');
export function runReuseExperiment({requests=3000,distinct=30,cacheMax=4096}={}) {
  if(!Number.isSafeInteger(requests)||requests<1||requests>10_000
    ||!Number.isSafeInteger(distinct)||distinct<1||distinct>requests) throw new TypeError('Invalid workload');
  // This verifies payload SHA integrity, not attested worker execution.
  const broker=new ComputeReuseBroker({maxCached:cacheMax,
    verifyReceipt:(job,receipt)=>receipt.inputDigest===job.inputDigest &&
      typeof receipt.value==='string' && sha(receipt.value)===receipt.outputSha256
  });
  let realCalculations=0;
  const optimized=[];
  const start=performance.now();
  for(let i=0;i<requests;i++){
    const seed=i%distinct;
    const inputDigest=digest(seed);
    const r=broker.submit({id:'job-'+i,tenantId:'research',
      modelRevision:digest(100),inputDigest,parametersDigest:digest(101),
      policyRevision:digest(102),taskType:'PURE',deterministic:true});
    if(r.action==='CACHE_HIT') {
      optimized.push(r.result);
    } else {
      if(r.action!=='EXECUTE') throw new Error('Unexpected concurrent join');
      const value=matrixWork(seed);
      realCalculations++;
      broker.complete(r.jobId,{inputDigest,value,outputSha256:sha(value)});
      optimized.push(value);
    }
  }
  const optimizedMs=performance.now()-start;
  const baselineStart=performance.now();
  for(let i=0;i<requests;i++){
    const value=matrixWork(i%distinct);
    if(value!==optimized[i]) throw new Error('Optimized result differs from baseline');
  }
  const baselineMs=performance.now()-baselineStart;
  return {
    requests,distinct,
    baselineCalculations:requests,
    optimizedCalculations:realCalculations,
    avoidedCalculations:requests-realCalculations,
    workReductionRatio:requests/realCalculations,
    byteExactResults:true,
    measuredOptimizedMs:optimizedMs,
    measuredBaselineMs:baselineMs,
    broker:broker.snapshot(),
    caveat:'Synthetic deterministic 12x12 CPU matrix task; NOT LLM speedup or compute generation.'
  };
}
if(process.argv[1] && process.argv[1].endsWith('/reuse-experiment.mjs')){
  for(const distinct of [30,3000]) {
    const result=runReuseExperiment({distinct});
    process.stdout.write(JSON.stringify(result,null,2)+'\n');
  }
}
