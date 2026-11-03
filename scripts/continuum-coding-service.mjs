#!/usr/bin/env node
/** Local owner operator entrypoint. No automatic startup on import. */
import path from 'node:path';
import {pathToFileURL} from 'node:url';
import {initializeCodingService,loadCodingServiceConfig,plainBytes,writePrivateJson} from '../.github/continuum/coding-service-store.mjs';
import {createCodingServiceReader,readServiceDescriptor} from '../.github/continuum/coding-service-reader.mjs';
import {prepareServiceTask,submitOwnerTask,cancelOwnerTask,startCodingService,runCodingService,serviceRequest} from '../.github/continuum/coding-service.mjs';
import {sameLiveProcess} from '../.github/continuum/coding-service-store.mjs';

const FLAGS={init:['config','project','profiles','classpath','runtime-env','isolation-config','max-queue','max-records','max-cycles','model-timeout-ms'],
 prepare:['config','task'],review:['config','task'],submit:['config','task','approve'],start:['config'],run:['config'],stop:['config'],
 status:['config'],cancel:['config','task-id'],agents:['config','cursor','limit'],tasks:['config','cursor','limit'],evidence:['config','task-id','evidence-id']};
function argumentsOf(argv){
 const [command,...rest]=argv,options={};
 if(!Object.hasOwn(FLAGS,command))throw Error('SERVICE_COMMAND_INVALID');
 for(let i=0;i<rest.length;i+=2){
  const name=rest[i]?.slice(2),value=rest[i+1];
  if(!rest[i]?.startsWith('--')||!FLAGS[command].includes(name)||Object.hasOwn(options,name)||typeof value!=='string'||!value.length)
   throw Error('SERVICE_ARGUMENT_INVALID');
  options[name]=value;
 }
 if(!options.config||!path.isAbsolute(options.config))throw Error('SERVICE_ABSOLUTE_CONFIG_REQUIRED');
 return {command,options};
}
const number=(value,fallback)=>value===undefined?fallback:/^[1-9][0-9]*$/.test(value)?Number(value):NaN;
const taskFile=file=>{if(!file)throw Error('SERVICE_TASK_FILE_REQUIRED');return JSON.parse(plainBytes(path.resolve(file),16384));};
export async function codingServiceCli(argv){
 const {command,options:o}=argumentsOf(argv),configPath=o.config;
 if(command==='init')return initializeCodingService({configPath,project:o.project,profileDirectory:o.profiles,profileClasspath:o.classpath,
  runtimeEnvPath:o['runtime-env'],isolationConfig:o['isolation-config'],maxQueue:number(o['max-queue'],8),
  maxRecords:number(o['max-records'],1000),maxCycles:number(o['max-cycles'],1000),modelTimeoutMs:number(o['model-timeout-ms'],60000)});
 if(command==='prepare'||command==='review')return prepareServiceTask({configPath,task:taskFile(o.task)});
 if(command==='start')return startCodingService({configPath});
 if(command==='run')return runCodingService({configPath});
 const reader=createCodingServiceReader({configPath});
 if(command==='status')return reader.getStatus();
 if(command==='agents')return reader.listAgents({cursor:o.cursor??null,limit:number(o.limit,20)});
 if(command==='tasks')return reader.listTasks({cursor:o.cursor??null,limit:number(o.limit,20)});
 if(command==='evidence')return reader.getEvidence({taskId:o['task-id'],evidenceId:o['evidence-id']});
 const config=loadCodingServiceConfig(configPath),descriptor=readServiceDescriptor(config),live=descriptor&&sameLiveProcess(descriptor.process);
 if(command==='submit'){
  const task=taskFile(o.task);
  return live?serviceRequest({configPath,operation:'submit',payload:{task,approve:o.approve}}):submitOwnerTask({configPath,task,approve:o.approve});
 }
 if(command==='cancel')return live?serviceRequest({configPath,operation:'cancel',payload:{taskId:o['task-id']}}):cancelOwnerTask({configPath,taskId:o['task-id']});
 if(command==='stop')return live?serviceRequest({configPath,operation:'stop'}):{state:reader.getStatus().state,stopped:false};
 throw Error('SERVICE_COMMAND_INVALID');
}
if(process.argv[1]&&pathToFileURL(path.resolve(process.argv[1])).href===import.meta.url){
 codingServiceCli(process.argv.slice(2)).then(result=>process.stdout.write(JSON.stringify(result)+'\n')).catch(error=>{
  const code=String(error?.message??error).slice(0,160);
  if(process.argv[2]==='run')try{
   const index=process.argv.indexOf('--config'),config=loadCodingServiceConfig(process.argv[index+1]);
   writePrivateJson(path.join(config.stateDir,'start-error.json'),{code,at:new Date().toISOString()});
  }catch{}
  process.stderr.write(JSON.stringify({status:'BLOCKED',code})+'\n');process.exitCode=1;
 });
}
