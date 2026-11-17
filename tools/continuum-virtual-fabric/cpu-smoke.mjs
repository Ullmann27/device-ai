/**
 * Deterministic CPU smoke experiment for ContinuumExpertMesh.
 * 100 logical tasks share one physical CPU slot. Does NOT run AI inference.
 */
import { createHash } from 'node:crypto';
import { ContinuumExpertMesh } from './expert-mesh.mjs';

export function matrixChecksum(seed, size=8) {
  if(!Number.isSafeInteger(seed)||seed<0||seed>1_000_000) throw new TypeError('Invalid seed');
  if(!Number.isSafeInteger(size)||size<1||size>32) throw new TypeError('Invalid matrix size');
  const a=Array.from({length:size},(_,i)=>Array.from({length:size},(_,j)=>(seed+i*3+j*7)%17));
  const b=Array.from({length:size},(_,i)=>Array.from({length:size},(_,j)=>(seed*2+i*11+j)%13));
  const result=Array.from({length:size},()=>Array(size).fill(0));
  for(let i=0;i<size;i++) for(let j=0;j<size;j++) for(let k=0;k<size;k++) result[i][j]+=a[i][k]*b[k][j];
  return createHash('sha256').update(JSON.stringify(result)).digest('hex');
}

export function runHundredCpuTasks({prototypeCount=100,taskCount=100,now=()=>1_000}={}){
  if(!Number.isSafeInteger(taskCount)||taskCount<1||taskCount>100) throw new TypeError('Invalid task count');
  const mesh=new ContinuumExpertMesh({prototypeCount,now});
  // This is ONE explicitly declared CPU reservation slot, not many invented CPUs.
  mesh.registerAuthorizedNode({id:'local-cpu-prototype',cpuSlots:1,gpuSlots:0,ramMb:4096,vramMb:0,expiresAtMs:1_000_000});
  for(let i=0;i<taskCount;i++){
    mesh.request({component:'coding-agent',taskId:'cpu-task-'+i,maxRuntimeMs:10_000});
  }
  let peakRunning=mesh.snapshot().running;
  const digests=[];
  for(let i=0;i<taskCount;i++){
    const id='cpu-task-'+i;
    const job=mesh.job(id);
    if(job.status!=='RUNNING') throw new Error('Task is not scheduled: '+id);
    // Successful scheduler reservations are not a proof of model inference.
    digests.push(matrixChecksum(i));
    mesh.complete(id);
    peakRunning=Math.max(peakRunning,mesh.snapshot().running);
  }
  return {prototypeCount,taskCount,peakRunning,digests,snapshot:mesh.snapshot()};
}

if (process.argv[1] && process.argv[1].endsWith('/cpu-smoke.mjs')) {
  const result=runHundredCpuTasks();
  process.stdout.write(JSON.stringify({
    logicalModels:result.snapshot.logicalModels,completed:result.snapshot.completed,
    peakRunning:result.peakRunning,firstSha256:result.digests[0],
    lastSha256:result.digests.at(-1),claim:'ONE physical CPU slot'
  },null,2)+'\n');
}
