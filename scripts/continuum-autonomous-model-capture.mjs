/** Explicit experiment-only observer, loaded with --import by the research CLI.
 * Records the local HTTP response unchanged, never headers, credentials or an
 * alternative answer. Ordinary product inference does not import this module.
 */
import fs from 'node:fs';import path from 'node:path';
const output=process.env.CONTINUUM_EXPERIMENT_RAW_RESPONSE;
if(!output||!path.isAbsolute(output))throw Error('EXPERIMENT_CAPTURE_PATH_REQUIRED');
const nativeFetch=globalThis.fetch;
globalThis.fetch=async(url,options)=>{
 if(String(url)!=='http://127.0.0.1:8089/v1/chat/completions')throw Error('EXPERIMENT_CAPTURE_LOCAL_ONLY');
 const response=await nativeFetch(url,options);
 const raw=await response.clone().text();
 if(Buffer.byteLength(raw)>65536)throw Error('EXPERIMENT_RAW_RESPONSE_TOO_LARGE');
 fs.writeFileSync(output,raw,{flag:'wx',mode:0o600});
 return response;
};
