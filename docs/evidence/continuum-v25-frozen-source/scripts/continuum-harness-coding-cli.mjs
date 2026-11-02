/** Fixed data-only subprocess; no task execution or model-generated commands. */
import {queryHarnessCodingBrain} from '../.github/continuum/harness-coding-provider.mjs';
let raw='';try{
 process.stdin.setEncoding('utf8');for await(const b of process.stdin){raw+=b;if(Buffer.byteLength(raw)>180000)throw Error('BOUNDS');}
 const input=JSON.parse(raw);
 const reply=await queryHarnessCodingBrain({...input,model:process.env.CONTINUUM_LOCAL_BRAIN_MODEL??'local',apiKey:process.env.CONTINUUM_LOCAL_BRAIN_API_KEY},
  {runtimeDirectory:process.env.CONTINUUM_HARNESS_RUNTIME});
 process.stdout.write(JSON.stringify(reply)+'\n');
}catch(error){
 const message=String(error?.message??'');
 const safe=/^(CODING_RESPONSE_INVALID|CODING_NOOP_PATCH):[a-f0-9]{64}$/.test(message)?message:'HARNESS_UNAVAILABLE_OR_INVALID';
 process.stderr.write('LOCAL_MODEL_UNAVAILABLE_OR_INVALID: '+safe+'\n');process.exitCode=1;
}
