// Review SPI: invokes the actual Kotlin admission and real profile store.
// The supplied canonical authority is explicitly a fixture, never a production grant.
import {spawnSync} from 'node:child_process';
export function evaluateCase(fixture){
 const cp=process.env.GENESIS_HANDOFF_CLASSPATH;
 if(!cp)throw Error('GENESIS_HANDOFF_CLASSPATH must point to the compiled exact-source harness');
 const result=spawnSync('java',['-Xmx256m','-cp',cp,'at.heinz.deviceai.chief.HandoffHostChecks','admission-json'],
  {input:JSON.stringify(fixture)+'\n',encoding:'utf8',timeout:10000,maxBuffer:65536,shell:false});
 if(result.status!==0)throw Error(result.stderr||'Kotlin admission bridge failed');
 return JSON.parse(result.stdout);
}

/** Read-only bridge to the actual existing on-disk profile store, for controlled host integration.
 * No writable registry is duplicated in JavaScript; profile edits are seen on each get(). */
export function profileStoreReader(directory,{classpath=process.env.GENESIS_HANDOFF_CLASSPATH}={}){
 if(!classpath||typeof directory!=='string'||!directory.startsWith('/'))throw Error('PROFILE_STORE_BRIDGE_UNAVAILABLE');
 const read=(command,args,maxBuffer=65536)=>{
  const result=spawnSync('java',['-Xmx128m','-cp',classpath,'at.heinz.deviceai.chief.HandoffHostChecks',command,directory,...args],
   {encoding:'utf8',timeout:10000,maxBuffer,shell:false});
  if(result.status!==0)throw Error('PROFILE_STORE_READ_FAILED');
  return JSON.parse(result.stdout);
 };
 return Object.freeze({get(agentId){
  if(!/^[a-z][a-z0-9-]{2,63}$/.test(agentId??''))throw Error('PROFILE_STORE_ID_INVALID');
  return read('get',[agentId]);
 },page({afterAgentId=null,limit=20}={}){
  if(afterAgentId!==null&&!/^[a-z][a-z0-9-]{2,63}$/.test(afterAgentId))throw Error('PROFILE_STORE_ID_INVALID');
  if(!Number.isInteger(limit)||limit<1||limit>100)throw Error('PROFILE_STORE_PAGE_INVALID');
  return read('page',[afterAgentId??'-',String(limit)],512*1024);
 }});
}
