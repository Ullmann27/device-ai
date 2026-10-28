#!/usr/bin/env node
/** Read-only exact-identity gate for the independent Continuum Lokal candidate.
 * No Git mutation, model execution, private data access or APK build.
 */
import fs from 'node:fs';
import path from 'node:path';
import crypto from 'node:crypto';
import {fileURLToPath} from 'node:url';

const BRIDGES=Object.freeze({
  'scripts/continuum-coding-bridge.mjs':'f9d9a5c7426552a051f884a7597a737e4b0f3c19',
  'scripts/continuum-coding-bridge.test.mjs':'ae33c281f623f7a4782d3a16aacd4421022eb4db',
  'scripts/continuum-selfhosted-coding.mjs':'b2d9259bbbd62137cd55ddeabdf43fe8534a7383',
  'scripts/continuum-selfhosted-coding.test.mjs':'fa6f712d6c5121d20a85985a4a22745795f48b08'
});
function ownedFile(root,relative){
  let current=root;
  for(const part of relative.split('/')){
    current=path.join(current,part);
    const stat=fs.lstatSync(current);
    if(stat.isSymbolicLink())throw Error('SYMLINK_SOURCE_REFUSED');
  }
  const st=fs.statSync(current);
  if(!st.isFile()||st.size===0||st.size>1024*1024)throw Error('UNSAFE_SOURCE_FILE');
  return fs.readFileSync(current);
}
function blobHash(bytes){
  const h=crypto.createHash('sha1');
  h.update('blob '+bytes.length+'\0');h.update(bytes);
  return h.digest('hex');
}
export function assessContinuumIntegration(root){
  const absolute=path.resolve(root);
  if(!fs.statSync(absolute).isDirectory()||fs.lstatSync(absolute).isSymbolicLink()||
     fs.realpathSync(absolute)!==absolute)throw Error('UNSAFE_CHECKOUT_ROOT');
  const actual={};
  for(const [p,expected] of Object.entries(BRIDGES)){
    const hash=blobHash(ownedFile(absolute,p));
    if(hash!==expected)throw Error('BRIDGE_SOURCE_DRIFT_'+path.basename(p).replace(/[^a-zA-Z0-9]/g,'_'));
    actual[p]=hash;
  }
  const gradle=ownedFile(absolute,'app/build.gradle.kts').toString('utf8');
  if(!/applicationId\s*=\s*if\s*\(continuumLocalStandalone\)\s*"at\.heinz\.deviceai\.continuumlocal"\s*else\s*"at\.heinz\.deviceai"/.test(gradle))
    throw Error('MAIN_APP_PACKAGE_IDENTITY_MISSING');
  const standalone=ownedFile(absolute,'scripts/continuum-fold7-standalone-apk.sh').toString('utf8');
  if(!standalone.includes('-PcontinuumLocalStandalone=true')||
     !standalone.includes('at.heinz.deviceai.continuumlocal'))
    throw Error('MAIN_APP_RELEASE_LANE_MISSING');
  const gate=ownedFile(absolute,'scripts/continuum-fold7-owner-update-gate.sh').toString('utf8');
  if(!gate.includes('continuum-fold7-standalone-apk.sh')||
     gate.includes('continuum-local-owner-apk.sh')||
     !gate.includes('CONTINUUM_OWNER_LOCAL_APPROVED_SHA'))
    throw Error('MAIN_APP_GATE_MISSING_OR_LEGACY');
  const catalog=ownedFile(absolute,'app/src/main/java/at/heinz/deviceai/localmodel/LocalModelCatalog.kt').toString('utf8');
  if(!catalog.includes('com.google.ai.edge.litertlm')||!catalog.includes('Model SHA-256 does not match'))
    throw Error('ORIGINAL_LITERT_MODEL_CATALOG_MISSING');
  return Object.freeze({schema:'continuum-source-integration-gate-v1',
    sourceIdentity:'CONTINUUM_LOCAL_STANDALONE_CANDIDATE',
    appId:'at.heinz.deviceai.continuumlocal',bridgeBlobShas:actual,
    modelImportSourcePresent:true,ownerSignerUntouched:true,
    modelActuallyRunning:false,unitTestsExecutedByThisGate:false,
    fullAndroidRegressionVerified:false,fold7DeviceVerified:false,
    apkInstalled:false,ownerReviewRequired:true});
}
if(process.argv[1]&&path.resolve(process.argv[1])===fileURLToPath(import.meta.url)){
  try{
    if(process.argv.length!==3)throw Error('USAGE');
    process.stdout.write(JSON.stringify(assessContinuumIntegration(process.argv[2]),null,2)+'\n');
  }catch(e){
    const reason=typeof e.message==='string'&&/^[A-Z0-9_]+$/.test(e.message)?e.message:'READ_ONLY_GATE_FAILED';
    process.stderr.write('CONTINUUM_INTEGRATION_BLOCKED='+reason+'\n');
    process.exitCode=69;
  }
}
