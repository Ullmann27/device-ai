import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import {spawnSync} from 'node:child_process';
import {createIsolatedExecutor,loadIsolationConfig} from './isolated-executor.mjs';

// Real Docker/fixed runner, synthetic Gradle/JDK/SDK capabilities only.
// Does not build Android or infer a model.
test('fixed isolated Android runner rejects 401 reports instead of returning a green subset',t=>{
 const config=process.env.CONTINUUM_TEST_ISOLATION_CONFIG;
 if(!config){t.skip('Requires explicitly configured Docker; synthetic Gradle reports, not an Android build');return;}
 const dir=fs.mkdtempSync(path.join(os.tmpdir(),'continuum-report-cap-'));
 t.after(()=>fs.rmSync(dir,{recursive:true,force:true}));
 const root=path.join(dir,'repo');fs.mkdirSync(root);
 const tools={};
 for(const name of ['gradle','jdk','android','modules-2']){
  tools[name]=path.join(dir,name);fs.mkdirSync(tools[name]);
 }
 for(const name of ['gradle','jdk'])fs.mkdirSync(path.join(tools[name],'bin'));
 fs.mkdirSync(path.join(tools.android,'build-tools'));
 fs.writeFileSync(path.join(tools.jdk,'bin/java'),'test capability, never called');
 const script=`#!/tools/node/bin/node
const fs=require('node:fs');const d='/work/app/build/test-results/testDebugUnitTest';
fs.mkdirSync(d,{recursive:true});
for(let i=0;i<Number(fs.readFileSync('/work/count','utf8'));i++)fs.writeFileSync(d+'/TEST-'+i+'.xml','<testsuite><testcase classname="fixture" name="case-'+i+'"/></testsuite>');
`;
 fs.writeFileSync(path.join(tools.gradle,'bin/gradle'),script,{mode:0o755});
 fs.writeFileSync(path.join(root,'count'),'401');
 for(const args of [['init','-q'],['add','.']]){
  const r=spawnSync('git',args,{cwd:root,encoding:'utf8'});assert.equal(r.status,0,r.stderr);
 }
 const policy=loadIsolationConfig(config);
 const executor=createIsolatedExecutor({...policy,jdkHome:tools.jdk,gradleHome:tools.gradle,
  androidHome:tools.android,gradleModules:tools['modules-2']});
 assert.throws(()=>executor.execute('android-unit',root,path.join(dir,'output')),
  /ISOLATION_EXECUTION_FAILED.*|ISOLATION_INVALID_RESULT/);
 fs.writeFileSync(path.join(root,'count'),'400');
 const valid=executor.execute('android-unit',root,path.join(dir,'accepted'));
 assert.equal(valid.status,0);
 assert.equal(valid.junitFiles.length,400,'valid bounded report set still reaches the verifier');
});
