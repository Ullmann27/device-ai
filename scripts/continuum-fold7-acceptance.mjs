import fs from 'node:fs';
import path from 'node:path';
const SHA1=/^[a-f0-9]{40}$/,SHA256=/^[a-f0-9]{64}$/;
const tests=Object.freeze([
 ['offline-gemma','Realer Offline-Dialog durch native Gemma-Inferenz'],
 ['context-persons','Personenbezug und Pronomen bei mehreren Referenten'],
 ['context-correction','Korrekturen und zeitlicher Bezug nach Rückfrage'],
 ['owner-revocation','Widerruf einer Erinnerung im selben und neuen Dialog'],
 ['restart-memory','Kaltstart ohne unzulässiges Vergessen oder Datendrift'],
 ['fold-while-streaming','Falten/Entfalten während laufender Inferenz'],
 ['rotation','Drehung und mehrfacher Activity-Neuaufbau'],
 ['background-resume','Hintergrund, Taskwechsel und Rückkehr'],
 ['cancel-restart','Abbrechen und erneuter Start ohne doppelte Aktion'],
 ['toolgate-denial','Abgelehnte Werkzeugfreigabe bleibt abgelehnt'],
 ['shizuku-boundary','Keine Operation außerhalb der Shizuku-/Besitzerrechte'],
 ['thermal-30min','30 Minuten Temperatur-, Speicher- und Latenzbeobachtung']
]);
export function createFold7AcceptanceTemplate(sourceCommit){
 if(!SHA1.test(sourceCommit??''))throw Error('INVALID_SOURCE_COMMIT');
 return {schema:1,device:'Samsung Galaxy Z Fold7',package:'at.heinz.deviceai.continuumlocal',
  testedSourceCommit:sourceCommit,installedApkSha256:null,modelSha256:null,
  capturePolicy:'NO_PRIVATE_DIALOGUE_OR_OWNER_KEYS',
  tests:tests.map(([id,description])=>({id,description,status:'NOT_RUN',
   evidenceSha256:null,elapsedMs:null})),independentReview:'NOT_PERFORMED'};
}
export function assessFold7Acceptance(report,sourceCommit){
 if(!SHA1.test(sourceCommit??'')||!report||report.schema!==1||
  report.device!=='Samsung Galaxy Z Fold7'||
  report.package!=='at.heinz.deviceai.continuumlocal'||
  report.testedSourceCommit!==sourceCommit||
  report.capturePolicy!=='NO_PRIVATE_DIALOGUE_OR_OWNER_KEYS'||
  !Array.isArray(report.tests)||report.tests.length!==tests.length||
  report.independentReview!=='NOT_PERFORMED')throw Error('INVALID_DEVICE_REPORT');
 const required=tests.map(t=>t[0]),ids=report.tests.map(x=>x.id);
 if(ids.some((id,i)=>id!==required[i])||new Set(ids).size!==required.length)
  throw Error('MISSING_OR_REORDERED_DEVICE_CASE');
 for(const t of report.tests){
  if(!['NOT_RUN','PASS','FAIL'].includes(t.status)||
   (t.status==='NOT_RUN'&&(t.evidenceSha256!==null||t.elapsedMs!==null))||
   (t.status!=='NOT_RUN'&&(!SHA256.test(t.evidenceSha256??'')||
    !Number.isSafeInteger(t.elapsedMs)||t.elapsedMs<0)))throw Error('INVALID_DEVICE_TEST_EVIDENCE');
 }
 const completed=report.tests.filter(t=>t.status!=='NOT_RUN').length;
 const failures=report.tests.filter(t=>t.status==='FAIL').length;
 const identity=SHA256.test(report.installedApkSha256??'')&&SHA256.test(report.modelSha256??'');
 const status=failures?'REPORTED_FAILURE':completed!==required.length?'INCOMPLETE':
   identity?'REPORTED_COMPLETE_NOT_ATTESTED':'MISSING_APK_OR_MODEL_PROVENANCE';
 return {schema:1,status,requiredCases:required.length,completed,failures,
  provenanceSubmitted:identity,deviceExecutionIndependentlyVerified:false,
  releaseAuthorized:false,productionMergeAuthorized:false,
  limitation:'Self-submitted receipts cannot authenticate hardware, native inference, signature or owner review'};
}
if(process.argv[1]&&path.resolve(process.argv[1])===path.resolve(new URL(import.meta.url).pathname)){
 try{
  if(process.argv.length===4&&process.argv[2]==='--template')
   console.log(JSON.stringify(createFold7AcceptanceTemplate(process.argv[3]),null,2));
  else if(process.argv.length===5&&process.argv[2]==='--check'){
   const report=JSON.parse(fs.readFileSync(process.argv[3],'utf8'));
   console.log(JSON.stringify(assessFold7Acceptance(report,process.argv[4]),null,2));
   process.exitCode=2;
  }else throw Error('USAGE: --template <codeSha> OR --check <report.json> <codeSha>');
 }catch(e){console.error('FOLD7_ACCEPTANCE_REJECTED: '+e.message);process.exitCode=1;}
}
