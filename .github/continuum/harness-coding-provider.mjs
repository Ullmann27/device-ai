/** Opt-in Harness LLM-service adapter. The existing CCC1 loop owns ALL actions,
 * budgets, repository context and evidence. No upstream tool/agent/shell plugins.
 */
import fs from 'node:fs';import path from 'node:path';import crypto from 'node:crypto';
import {createRequire} from 'node:module';import {pathToFileURL,fileURLToPath} from 'node:url';import {spawnSync} from 'node:child_process';
import {createCodingCandidateProvider} from './coding-provider.mjs';
import {buildCodingRequest,queryLocalCodingBrain,parseCodingResponse} from '../../scripts/continuum-local-brain.mjs';
const RUNNER=fileURLToPath(new URL('../../scripts/continuum-harness-coding-cli.mjs',import.meta.url));
const sha=b=>crypto.createHash('sha256').update(b).digest('hex');
const versions={'@deepseek-ai/cordis':'4.0.5','@deepseek-ai/dsh-llm':'0.2.1-rc.3'};
export function inspectHarnessRuntime(directory){
 try {
  if(typeof directory!=='string'||!path.isAbsolute(directory)||fs.realpathSync(directory)!==directory||!fs.lstatSync(directory).isDirectory())throw Error();
  const entries={},packages={};
  for(const [name,version] of Object.entries(versions)){
   const packageFile=path.join(directory,'node_modules',name,'package.json');const st=fs.lstatSync(packageFile);
   if(!st.isFile()||st.isSymbolicLink()||st.size>65536)throw Error();
   const p=JSON.parse(fs.readFileSync(packageFile,'utf8'));if(p.version!==version)throw Error();
   const entry=fs.realpathSync(createRequire(path.join(directory,'package.json')).resolve(name));
   if(!entry.startsWith(directory+path.sep))throw Error();entries[name]=entry;packages[name]={version,entrySha256:sha(fs.readFileSync(entry))};
  }
  const lock=path.join(directory,'package-lock.json');const st=fs.lstatSync(lock);
  if(!st.isFile()||st.isSymbolicLink()||st.size>2_000_000)throw Error();
  return Object.freeze({directory,entries:Object.freeze(entries),packages:Object.freeze(packages),lockSha256:sha(fs.readFileSync(lock)),sourceCommitIdentityAttested:false});
 }catch{throw Error('HARNESS_RUNTIME_UNAVAILABLE_OR_UNPINNED');}
}
export async function queryHarnessCodingBrain(input,{runtimeDirectory,fetchImpl=globalThis.fetch}={}){
 const runtime=inspectHarnessRuntime(runtimeDirectory),{paths,body}=buildCodingRequest(input);
 const [{Context},{default:LlmRuntime,LlmAdapter,attributionHeaders}]=await Promise.all([
  import(pathToFileURL(runtime.entries['@deepseek-ai/cordis']).href),import(pathToFileURL(runtime.entries['@deepseek-ai/dsh-llm']).href)]);
 const ctx=new Context(),controller=new AbortController();
 if(!Number.isSafeInteger(input.timeoutMs)||input.timeoutMs<100||input.timeoutMs>300000)throw Error('INVALID_CODING_BOUNDS');
 const timer=setTimeout(()=>controller.abort(),input.timeoutMs);let raw='',finished=false;
 try {
  await ctx.plugin(LlmRuntime);
  class ContinuumAdapter extends LlmAdapter {
   async *stream(options){
    if(options.model!==body.model||options.tools?.length!==0)throw Error('CODING_RESPONSE_INVALID');
    const result=await queryLocalCodingBrain({...input,fetchImpl:(url,opts)=>fetchImpl(url,{...opts,
     headers:{...attributionHeaders(),...opts.headers},signal:AbortSignal.any([controller.signal,options.signal,opts.signal].filter(Boolean))})});
    yield {type:'text-delta',index:0,text:JSON.stringify(result.proposal)};
    yield {type:'finish',reason:{kind:'stop'}};
   }
  }
  ctx.llm.registerAdapter(['continuum-local'],new ContinuumAdapter());
  for await(const c of ctx.llm.stream({provider:'continuum-local',model:body.model,system:body.messages[0].content,
   messages:[{role:'user',content:[{type:'text',text:body.messages[1].content}]}],tools:[],temperature:0,maxTokens:1800,signal:controller.signal})){
   if(c.type==='text-delta'){raw+=c.text;if(raw.length>24000)throw Error('CODING_RESPONSE_INVALID');}
   if(c.type==='finish'){if(c.reason.kind!=='stop')throw Error('CODING_RESPONSE_INVALID');finished=true;}
  }
  if(!finished)throw Error('CODING_RESPONSE_INVALID');
  return parseCodingResponse({choices:[{message:{content:raw}}]},paths);
 } finally {clearTimeout(timer);controller.abort();await ctx.fiber.dispose();}
}
export function createHarnessCodingCandidateProvider({runtimeDirectory,runHarness=spawnSync,...options}={}){
 const runtime=inspectHarnessRuntime(runtimeDirectory);
 if(options.remoteTeacher!=null)throw Error('HARNESS_LOCAL_ONLY');
 const provider=createCodingCandidateProvider({...options,run:(_binary,args,opts)=>{
  if(args.at(-1)!=='coding')throw Error('HARNESS_OPERATION_DENIED');
  return runHarness(process.execPath,[RUNNER],{...opts,env:{...opts.env,CONTINUUM_HARNESS_RUNTIME:runtime.directory}});
 }});
 Object.defineProperty(provider,'harnessRuntime',{value:runtime});return provider;
}
