import assert from 'node:assert/strict';
import test from 'node:test';
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import {RelationalEncoder} from './cre-encoder.mjs';
import {sha256Hex} from './development-backlog.mjs';
import {runPostResearchShadowCycle} from './research-shadow-worker.mjs';

function tempRun(callback){
  const root=fs.mkdtempSync(path.join(os.tmpdir(),'continuum-shadow-'));
  try{return callback(root)}finally{fs.rmSync(root,{recursive:true,force:true})}
}
function oneApprovedSpan(){
  const claim='A reproducible software test checks its original source text and records independently measured regression evidence.';
  const sourceText=claim+' Additional independent context from the original document.';
  return {query:'reproducible software test source verification',claim,sourceText,
    sourceSha256:sha256Hex(Buffer.from(sourceText,'utf8')),
    url:'https://example.org/research/reviewed',trainingPermitted:true};
}
test('public metadata may trigger a check but cannot alone train an autonomous model',()=>{
  tempRun(root=>{
    const dir=path.join(root,'cre1-research');
    const result=runPostResearchShadowCycle({researchResult:{success:true},stateDir:dir});
    assert.equal(result.status,'AWAITING_VERIFIED_ORIGINAL_DOCUMENTS');
    assert.equal(fs.existsSync(path.join(dir,'cre1-research-v1.cre1')),false);
    assert.equal(fs.existsSync(path.join(dir,'shadow-audit.json')),false);
  });
});
test('worker requires a successful research event and a local approved bundle',()=>{
  tempRun(root=>{
    const dir=path.join(root,'cre1-research');fs.mkdirSync(dir);
    const outside=path.join(root,'other.json');fs.writeFileSync(outside,'[]');
    assert.equal(runPostResearchShadowCycle({
      researchResult:{success:false},stateDir:dir
    }).status,'SKIPPED_NO_NEW_RESEARCH');
    assert.throws(()=>runPostResearchShadowCycle({
      researchResult:{success:true},stateDir:dir,sourcePath:outside
    }),/isolated/);
    assert.equal(fs.existsSync(path.join(dir,'cre1-research-v1.cre1')),false);
  });
});
test('insufficient original documents are persisted as rejected, never promoted',()=>{
  tempRun(root=>{
    const dir=path.join(root,'cre1-research');fs.mkdirSync(dir);
    const input=path.join(dir,'approved-extractive-spans.json');
    fs.writeFileSync(input,JSON.stringify([oneApprovedSpan()]));
    const result=runPostResearchShadowCycle({
      researchResult:{success:true},stateDir:dir,budgetMs:30_000
    });
    assert.equal(result.status,'SHADOW_REJECTED');
    assert.equal(result.checkpointWritten,false);
    assert.equal(result.report.authority,'NONE');
    assert.equal(result.report.productionPromotion,false);
    assert.equal(fs.existsSync(path.join(dir,'cre1-research-v1.cre1')),false);
    assert.equal(fs.existsSync(path.join(dir,'research-shadow.lock')),false);
    const next=runPostResearchShadowCycle({
      researchResult:{success:true},stateDir:dir
    });
    assert.equal(next.status,'UNCHANGED_VERIFIED_CORPUS');
  });
});
test('bad source hashes do not replace existing CRE1 and leave no stale owned lock',()=>{
  tempRun(root=>{
    const dir=path.join(root,'cre1-research');fs.mkdirSync(dir);
    const checkpoint=path.join(dir,'cre1-research-v1.cre1');
    const initial=new RelationalEncoder(73).save();
    fs.writeFileSync(checkpoint,initial);
    const bad={...oneApprovedSpan(),sourceSha256:'0'.repeat(64)};
    fs.writeFileSync(path.join(dir,'approved-extractive-spans.json'),JSON.stringify([bad]));
    assert.throws(()=>runPostResearchShadowCycle({
      researchResult:{success:true},stateDir:dir
    }),/SHA-256/);
    assert.equal(Buffer.compare(initial,fs.readFileSync(checkpoint)),0);
    assert.equal(fs.existsSync(path.join(dir,'research-shadow.lock')),false);
    assert.equal(fs.existsSync(path.join(dir,'shadow-audit.json')),false);
  });
});
test('concurrent or stale unknown locks fail closed; no forced takeover',()=>{
  tempRun(root=>{
    const dir=path.join(root,'cre1-research');fs.mkdirSync(dir);
    fs.writeFileSync(path.join(dir,'approved-extractive-spans.json'),
      JSON.stringify([oneApprovedSpan()]));
    fs.writeFileSync(path.join(dir,'research-shadow.lock'),'unknown owner');
    assert.equal(runPostResearchShadowCycle({
      researchResult:{success:true},stateDir:dir
    }).status,'LOCKED_REQUIRES_REVIEW');
    assert.equal(fs.readFileSync(path.join(dir,'research-shadow.lock'),'utf8'),'unknown owner');
  });
});
