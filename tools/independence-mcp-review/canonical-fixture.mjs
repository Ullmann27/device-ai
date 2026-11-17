// Synthetic control-plane fixture using actual canonical modules and Kotlin store.
// No inference, task worker, executable patch or successful-repair receipt is fabricated.
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import crypto from 'node:crypto';
import {spawnSync} from 'node:child_process';
import {pathToFileURL} from 'node:url';

export async function canonicalFixture({candidate,classpath,runtimeEnvPath,copyProfileClasspath=false}={}){
  const load=file=>import(pathToFileURL(path.join(candidate,'.github/continuum',file)));
  const store=await load('coding-service-store.mjs'),{createCodingServiceReader}=await load('coding-service-reader.mjs');
  const {startTask}=await load('ccc1-orchestrator.mjs'),{DurableTaskJournal,runtimeKey}=await load('ccc1-journal.mjs');
  const {envelope}=await load('ccc1-envelope.mjs'),{taskFingerprint}=await load('coding-task.mjs');
  const {canonical,sha256Hex}=await load('development-backlog.mjs');
  const root=fs.mkdtempSync(path.join(os.tmpdir(),'canonical-mcp-review-'));fs.chmodSync(root,0o700);
  const project=path.join(root,'project'),profiles=path.join(root,'profiles'),state=path.join(root,'state');
  const run=(cmd,args,opts={})=>{
    const r=spawnSync(cmd,args,{encoding:'utf8',shell:false,timeout:15000,maxBuffer:1048576,...opts});
    if(r.status!==0)throw Error(r.error?.message||r.stderr||'FIXTURE_COMMAND_FAILED');return r.stdout.trim();
  };
  const write=(file,data)=>{fs.mkdirSync(path.dirname(file),{recursive:true,mode:0o700});
    fs.writeFileSync(file,typeof data==='string'?data:JSON.stringify(data)+'\n',{mode:0o600});fs.chmodSync(file,0o600);};
  try{
    let classpathDirectory=null;
    if(copyProfileClasspath){
      const entries=classpath.split(path.delimiter),index=entries.findIndex(p=>fs.statSync(p).isDirectory());
      if(index<0)throw Error('FIXTURE_CLASSPATH_DIRECTORY_REQUIRED');
      classpathDirectory=path.join(root,'actual-compiled-profile-classes');fs.cpSync(entries[index],classpathDirectory,{recursive:true});
      entries[index]=classpathDirectory;classpath=entries.join(path.delimiter);
    }
    fs.mkdirSync(project);fs.mkdirSync(profiles);fs.mkdirSync(path.join(project,'lib'));
    fs.writeFileSync(path.join(project,'lib','value.mjs'),'export const value = 1;\n');
    run('git',['init','-q',project]);run('git',['-C',project,'add','.']);
    run('git',['-C',project,'-c','user.name=Continuum independent fixture','-c','user.email=fixture@example.invalid','commit','-qm','synthetic reader baseline']);
    const sourceCommit=run('git',['-C',project,'rev-parse','HEAD']);
    run('java',['-Xmx128m','-cp',classpath,'at.heinz.deviceai.chief.HandoffHostChecks','seed',profiles]);
    const isolation=path.join(root,'isolation-fixture.json');write(isolation,{fixture:'read-only test; never executes'});
    const configPath=path.join(state,'service-config.json');
    store.initializeCodingService({configPath,project,profileDirectory:profiles,profileClasspath:classpath,runtimeEnvPath,isolationConfig:isolation});
    const config=store.loadCodingServiceConfig(configPath),ccc1=path.join(state,'ccc1');
    const task={schema:'continuum-coding-task',version:1,id:'independent-read-case',sourceCommit,kind:'BUGFIX',
      goal:'Synthetic read-projection check; do not execute code',allowedPaths:['lib/value.mjs'],
      acceptance:['Only reader projection is being tested'],chain:'node',limits:{maxModelCalls:1,maxContextChars:2048}};
    const fingerprint=taskFingerprint(task);
    const ccc1TaskId=startTask({project,stateDir:ccc1,goal:{text:'Synthetic reader fixture [task '+fingerprint+']',repair:{chain:'node'}},
      repoFullName:'review/fixture',worktreeId:'reader',permissions:['node-repair:node'],deadlineMinutes:30});
    const key=runtimeKey(ccc1),journal=DurableTaskJournal.forTask(ccc1,ccc1TaskId,key);
    const append=(sender,kind,payload,options={})=>journal.append(envelope({binding:journal.entries()[0],sender,kind,payload,sequence:journal.entries().length,...options})).entry;
    append('REASONER','ABSTAIN',{reason:'Synthetic fixture contains no model execution or repair evidence'});
    const delivery={id:task.id,fingerprint,reviewSha256:'7'.repeat(64),delivery:'RETIRED',ccc1TaskId};
    write(path.join(ccc1,'coding-grants',task.id+'.json'),{task,fingerprint,ccc1TaskId});
    store.saveDeliveries(config,[delivery]);
    const reportFile='bot-report-'+crypto.randomBytes(32).toString('hex')+'.json';
    const reportPath=path.join(ccc1,'coding-evidence',task.id,reportFile);
    const report={schema:'continuum-bot-result-report',version:1,taskId:task.id,canonicalTaskId:ccc1TaskId,taskFingerprint:fingerprint,
      sourceCommit,journalHead:journal.entries().at(-1).hash,reporterId:'bot-echo',status:'ABSTAINED',interpretation:'NO_CONFIRMED_REPAIR'};
    write(reportPath,report);
    const metrics={schema:'continuum-service-result',version:1,taskId:task.id,ccc1TaskId,taskFingerprint:fingerprint,
      sourceCommit,journalHead:journal.entries().at(-1).hash,resultStatus:'ABSTAINED',
      modelExecution:{verifiedCalls:0,acceptedProposals:0,rejectedResponses:0,identity:{
        modelSha256:config.runtimeEnv.CONTINUUM_LOCAL_BRAIN_MODEL_SHA256,
        runtimeSha256:config.runtimeEnv.CONTINUUM_LOCAL_BRAIN_RUNTIME_SHA256,reviewedPairId:'synthetic-read-fixture'}},
      technicalExperienceStatus:'NOT_RECORDED',technicalRecallCount:0,elapsedMs:0,
      resourceUsage:{scope:'NODE_CONTROL_PROCESS_NOT_NATIVE_CHILD_TOTAL',userCpuMicros:0,systemCpuMicros:0,maxRssKiB:0},
      reportFile,reportSha256:sha256Hex(fs.readFileSync(reportPath)),environment:'HOST',deviceVerified:false,approvedForMerge:false};
    const resultPath=path.join(state,'public-evidence',task.id+'.json');
    const sign=()=>write(resultPath,{...metrics,metricsMac:crypto.createHmac('sha256',key).update(canonical(metrics)).digest('hex')});sign();
    return {root,project,profiles,state,ccc1,task,delivery,configPath,config,classpathDirectory,journal,key,append,metrics,sign,resultPath,
      report,reportPath,reader:createCodingServiceReader({configPath}),store,write,run,sha256Hex,
      cleanup:()=>fs.rmSync(root,{recursive:true,force:true})};
  }catch(error){fs.rmSync(root,{recursive:true,force:true});throw error;}
}
