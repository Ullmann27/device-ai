/**
 * Manual, one-request NVIDIA public coding trial.
 *
 * node scripts/continuum-nvidia-coding-lab.mjs --check
 * NVIDIA_API_KEY=<owner secret in OS secure environment> \
 *   node scripts/continuum-nvidia-coding-lab.mjs --run
 *
 * Never place the API key in source control, chat, logs or command arguments.
 */
import {makeNvidiaPublicCodingChallenge,runNvidiaPublicCodingLab,
 NVIDIA_CODING_LAB_MODEL,NVIDIA_CODING_LAB_ENDPOINT}
 from '../.github/continuum/nvidia-nim-public-coding-lab.mjs';

const op=process.argv[2];
if(process.argv.length!==3||!['--check','--run'].includes(op)){
 process.stderr.write('Usage: node scripts/continuum-nvidia-coding-lab.mjs --check|--run\n');
 process.exitCode=2;
}else if(op==='--check'){
 const challenge=makeNvidiaPublicCodingChallenge();
 process.stdout.write(JSON.stringify({
  schema:'continuum-nvidia-coding-lab-preflight-v1',
  publicSynthetic:true,sourceCommitIsReal:false,
  model:NVIDIA_CODING_LAB_MODEL,endpoint:NVIDIA_CODING_LAB_ENDPOINT,
  cases:challenge.expectedFiles.length,
  promptSha256:challenge.promptSha256,
  credentialsPresent:Boolean(process.env.NVIDIA_API_KEY),
  credentialsFormatValid:/^nvapi-[A-Za-z0-9_-]{12,256}$/.test(process.env.NVIDIA_API_KEY??''),
  localCodeOrMemoryTransferred:false,
  requestSent:false
 },null,2)+'\n');
}else if(!process.env.NVIDIA_API_KEY){
 process.stderr.write('NVIDIA_AUTH_REQUIRED: generate an NVIDIA API key, store it in an environment secret, not in chat or GitHub.\n');
 process.exitCode=2;
}else{
 try{
  const result=await runNvidiaPublicCodingLab({apiKey:process.env.NVIDIA_API_KEY});
  process.stdout.write(JSON.stringify(result,null,2)+'\n');
  if(!result.allExactGolden)process.exitCode=3;
 }catch(error){
  const reason=String(error?.message??'');
  const whitelist=new Set(['NVIDIA_AUTH_REQUIRED','NVIDIA_CODING_LAB_CONFIG_INVALID',
   'NVIDIA_REQUEST_TOO_LARGE','NVIDIA_RESPONSE_INVALID',
   'NVIDIA_RESPONSE_TOO_LARGE','NVIDIA_PROPOSAL_FORMAT_INVALID']);
  process.stderr.write((whitelist.has(reason)||/^NVIDIA_ENDPOINT_UNAVAILABLE_HTTP_[0-9]{3}$/.test(reason)?
   reason:'NVIDIA_CODING_LAB_UNAVAILABLE')+'\n');
  process.exitCode=2;
 }
}