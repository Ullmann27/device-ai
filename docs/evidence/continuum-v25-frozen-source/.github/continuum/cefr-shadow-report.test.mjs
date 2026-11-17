import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import {auditCefrShadowWinner} from './cefr-shadow-benchmark.mjs';
import {collectCefrShadowReports,parseCefrShadowReportArgs} from './cefr-shadow-report.mjs';

function fixture(fn){
 const base=fs.mkdtempSync(path.join(os.tmpdir(),'continuum-cefr-summary-'));
 const stateDir=path.join(base,'state'),reports=path.join(stateDir,'reports');
 const dir=path.join(reports,'repair-'+'a'.repeat(12));
 fs.mkdirSync(dir,{recursive:true});
 try{return fn({base,stateDir,reports,dir,reportFile:path.join(dir,'report.json')});}
 finally{fs.rmSync(base,{recursive:true,force:true});}
}
function realLookingReport(){
 const report={outcome:'VERIFIED',base:'a'.repeat(40),
  verified:{candidate:'fixed',patchSha256:'b'.repeat(64)},
  independent:{passed:true,evidenceSufficient:true,profiles:[
   {profile:'node-continuum',passed:true,executed:18,failed:0}
  ]},
  cefrShadow:{mode:'CEFR_DEV_SHADOW',modelSha256:'c'.repeat(64),
   candidateCount:3,productionOrder:['wrong','fixed','revert'],
   shadowOrder:['fixed','wrong','revert']}};
 report.cefrShadowAudit=auditCefrShadowWinner({report,independent:report.independent});
 return report;
}

test('a checked worker report creates an advisory-only offline cohort',()=>fixture(f=>{
 fs.writeFileSync(f.reportFile,JSON.stringify(realLookingReport()));
 const outcome=collectCefrShadowReports({stateDir:f.stateDir,minimum:20});
 assert.equal(outcome.totalReports,1);
 assert.equal(outcome.totalAudits,1);
 assert.equal(outcome.cohorts[0].samples,1);
 assert.equal(outcome.cohorts[0].status,'INSUFFICIENT_INDEPENDENT_CASES');
 assert.equal(outcome.cohorts[0].productionPromotionAuthorized,false);
 assert.equal(outcome.advisoryOnly,true);
}));

test('a tampered winner or independent check cannot be silently counted',()=>fixture(f=>{
 const report=realLookingReport();
 report.verified.candidate='unobserved';
 fs.writeFileSync(f.reportFile,JSON.stringify(report));
 assert.throws(()=>collectCefrShadowReports({stateDir:f.stateDir}),
  /INVALID_OR_STALE_CEFR_SHADOW_AUDIT/);
 report.verified.candidate='fixed';
 report.independent.passed=false;
 fs.writeFileSync(f.reportFile,JSON.stringify(report));
 assert.throws(()=>collectCefrShadowReports({stateDir:f.stateDir}),
  /INVALID_OR_STALE_CEFR_SHADOW_AUDIT/);
}));

test('a symlinked report cannot redirect the offline evaluator to other files',()=>fixture(f=>{
 const outside=path.join(f.base,'outside.json');
 fs.writeFileSync(outside,JSON.stringify(realLookingReport()));
 fs.symlinkSync(outside,f.reportFile);
 assert.throws(()=>collectCefrShadowReports({stateDir:f.stateDir}),
  /INVALID_CEFR_REPORT_FILE/);
}));

test('symlinked report directory is refused rather than followed',()=>fixture(f=>{
 fs.rmdirSync(f.dir);
 const outside=path.join(f.base,'outside');
 fs.mkdirSync(outside);
 fs.symlinkSync(outside,f.dir,'dir');
 assert.throws(()=>collectCefrShadowReports({stateDir:f.stateDir}),
  /UNSAFE_CEFR_REPORT_DIRECTORY/);
}));

test('oversized report rejected before parsing',()=>fixture(f=>{
 fs.writeFileSync(f.reportFile,Buffer.alloc(2*1024*1024+1));
 assert.throws(()=>collectCefrShadowReports({stateDir:f.stateDir}),
  /INVALID_CEFR_REPORT_FILE/);
}));

test('CLI requires a fixed option and absolute state directory',()=>{
 assert.deepEqual(parseCefrShadowReportArgs(['--state-dir','/tmp/state']),
  {stateDir:'/tmp/state'});
 assert.throws(()=>parseCefrShadowReportArgs(['--state-dir','relative']),
  /Usage:/);
 assert.throws(()=>parseCefrShadowReportArgs(['--delete','/tmp/state']),
  /Usage:/);
});
