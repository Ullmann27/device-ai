/**
 * Safe manual NVIDIA NIM synthetic benchmark.
 * node scripts/continuum-nvidia-nim-benchmark.mjs --check
 * NVIDIA_API_KEY (in local env only) node scripts/continuum-nvidia-nim-benchmark.mjs --run
 */
import {runNvidiaNimSyntheticBenchmark,NVIDIA_NIM_MODELS,NVIDIA_NIM_ENDPOINT}
 from '../.github/continuum/nvidia-nim-synthetic-benchmark.mjs';

const option=process.argv[2];
if(option==='--check'){
 process.stdout.write(JSON.stringify({schema:'continuum-nvidia-nim-preflight-v1',
  endpoint:NVIDIA_NIM_ENDPOINT,
  models:NVIDIA_NIM_MODELS,
  apiKeyPresent:Boolean(process.env.NVIDIA_API_KEY),
  apiKeyValidFormat:/^nvapi-[A-Za-z0-9_-]{12,256}$/.test(process.env.NVIDIA_API_KEY??''),
  requestSent:false,inferenceVerified:false,
  needsOwnerDeveloperAccount:true,needsExplicitRun:true
 },null,2)+'\n');
}else if(option==='--run'&&process.argv.length===3){
 if(!process.env.NVIDIA_API_KEY){
  process.stderr.write('NVIDIA_AUTH_REQUIRED: create an NVIDIA Developer API key; keep it outside GitHub and chat.\n');
  process.exitCode=2;
 }else{
  try{
   const result=await runNvidiaNimSyntheticBenchmark({apiKey:process.env.NVIDIA_API_KEY});
   process.stdout.write(JSON.stringify(result,null,2)+'\n');
   if(!result.independentlyVerified)process.exitCode=3;
  }catch(error){
   // Fixed error status only; never print provider body, prompt or credential.
   const codes=new Set(['NVIDIA_AUTH_REQUIRED','NVIDIA_BENCHMARK_CONFIG_INVALID',
    'NVIDIA_REQUEST_TOO_LARGE','NVIDIA_RESPONSE_INVALID','NVIDIA_RESPONSE_TOO_LARGE']);
   const msg=String(error?.message??'');
   const reason=codes.has(msg)?msg:/^NVIDIA_NIM_BENCHMARK_UNAVAILABLE_HTTP_\d{3}$/.test(msg)?
    msg:'NVIDIA_NIM_BENCHMARK_UNAVAILABLE';
   process.stderr.write(reason+'\n');process.exitCode=2;
  }
 }
}else{
 process.stderr.write('Usage: node scripts/continuum-nvidia-nim-benchmark.mjs --check|--run\n');
 process.exitCode=2;
}