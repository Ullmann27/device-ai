import test from 'node:test';
import assert from 'node:assert/strict';
import {isCodingPath,validateCodingTask,validateCodingProposal} from './coding-task.mjs';

const spec=(file,chain='android')=>({
 schema:'continuum-coding-task',version:1,id:'android-scoped-change',
 sourceCommit:'a'.repeat(40),kind:'BUGFIX',
 goal:'Repair one existing source or layout resource',
 allowedPaths:[file],acceptance:['Bounded change passes independent tests'],
 chain,limits:{maxModelCalls:3,maxContextChars:12000}
});

test('Android source tasks can edit scoped Java and ordinary layout XML',()=>{
 for(const path of ['app/src/main/java/at/heinz/deviceai/Safe.java',
                    'app/src/main/res/layout/activity_home.xml',
                    'app/src/main/java/at/heinz/deviceai/Safe.kt']){
  assert.equal(isCodingPath(path),true,path);
  const task=validateCodingTask(spec(path));
  assert.equal(task.allowedPaths[0],path);
  assert.equal(Object.isFrozen(task.allowedPaths),true);
  assert.throws(()=>validateCodingTask(spec(path,'node')),path);
 }
});

test('new scope never grants build scripts, manifests, security XML, JSON or test edits',()=>{
 for(const path of ['app/build.gradle.kts','app/src/main/AndroidManifest.xml',
                    'app/src/main/res/xml/network_security_config.xml',
                    'app/src/main/res/values/strings.xml',
                    'app/src/main/assets/config.json',
                    'app/src/test/java/at/heinz/deviceai/Fake.java',
                    '.github/workflows/release.yaml',
                    '.github/continuum/coding-task.mjs',
                    'app/src/main/res/layout/../AndroidManifest.xml',
                    'app/src/main/res/layout/bad\\name.xml']){
  assert.equal(isCodingPath(path),false,path);
  assert.throws(()=>validateCodingTask(spec(path)),path);
 }
});

test('a scoped XML edit cannot touch a second path or change the chain',()=>{
 const task=validateCodingTask(spec('app/src/main/res/layout/activity_home.xml'));
 const valid={plan:['Change one layout attribute'],hypothesis:'The display attribute is incorrect',
   patches:[{file:task.allowedPaths[0],oldText:'android:visibility="gone"',
     newText:'android:visibility="visible"'}]};
 assert.equal(validateCodingProposal(valid,task).patches.length,1);
 assert.throws(()=>validateCodingProposal({...valid,patches:[
   {...valid.patches[0],file:'app/src/main/AndroidManifest.xml'}]},task));
 assert.throws(()=>validateCodingTask({...task,chain:'node'}));
});
