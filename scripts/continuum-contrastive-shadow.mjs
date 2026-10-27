#!/usr/bin/env node
import fs from 'node:fs';import path from 'node:path';import {fileURLToPath} from 'node:url';import {execFileSync} from 'node:child_process';
import {SubwordEncoder,sha256} from '../.github/continuum/portable-subword.mjs';
import {POLICIES,runContrastiveTrial,measurements,frozenIntentView} from '../experimental/swb1-contrastive-20261027/contrastive-lab.mjs';
const root=path.resolve(path.dirname(fileURLToPath(import.meta.url)),'..');
const fixture=path.join(root,'experimental/swb1-contrastive-20261027/fixtures');
const EXPECTED='5e577c21b22c6b31ff71274640d45f8900ca646e370bef6179ab608ffb992cd1';
const read=(split)=>JSON.parse(fs.readFileSync(path.join(fixture,split+'.json'),'utf8'));
const json=(out,name,data)=>fs.writeFileSync(path.join(out,name),JSON.stringify(data,null,2)+'\n',{flag:'wx'});
function main(){
 const [mode,inputArg,outArg]=process.argv.slice(2);if(!['--train','--final'].includes(mode)||!inputArg||!outArg)throw Error('USAGE: --train /readonly/state.json /new/out OR --final /train/out /new/final-out');
 const out=path.resolve(outArg);if(out===root||out.startsWith(root+path.sep))throw Error('OUTPUT_MUST_BE_EXTERNAL');
 fs.mkdirSync(out,{recursive:false,mode:0o700});if(fs.realpathSync(out)!==out)throw Error('OUTPUT_SYMLINK_REFUSED');
 const seal=JSON.parse(fs.readFileSync(path.join(fixture,'seal.json')));
 for(const [split,meta] of Object.entries(seal.files))if(sha256(fs.readFileSync(path.join(fixture,split+'.json')))!==meta.sha256)throw Error('FIXTURE_SEAL_MISMATCH');
 const codeSha=execFileSync('git',['rev-parse','HEAD'],{cwd:root,encoding:'utf8'}).trim();
 if(mode==='--train'){
  const source=fs.readFileSync(inputArg);const state=JSON.parse(source).neural;if(state.modelHash!==EXPECTED)throw Error('UNEXPECTED_BASELINE');
  const starting=Buffer.from(state.model,'base64');if(sha256(starting)!==EXPECTED)throw Error('BASELINE_CHECKSUM');
  fs.writeFileSync(path.join(out,'baseline.swb1'),starting,{flag:'wx'});
  const train=read('train'),validation=read('validation');json(out,'baseline-validation.json',measurements(SubwordEncoder.restore(starting),validation));
  const reports=[];
  for(const policy of POLICIES)for(const seed of seal.seeds){
   const {report,bytes}=runContrastiveTrial(state,train,validation,{policy,seed});
   const name=policy+'-'+seed;fs.writeFileSync(path.join(out,name+'.bin'),bytes,{flag:'wx'});json(out,name+'.json',{...report,codeSha,fixtureSeal:seal.files});reports.push({name,...report});
   console.log(JSON.stringify({policy,seed,steps:report.stepsCompleted,eligible:report.researchGatePassed,newSemantic:report.checkpoints.at(-1)?.newValidation.semantic.correct,newIntent:report.checkpoints.at(-1)?.newValidation.intent.correct,format:report.format}));
  }
  if(!source.equals(fs.readFileSync(inputArg)))throw Error('ORIGINAL_STATE_FILE_CHANGED');
  // Selection is frozen before opening final labels: median development accuracy,
  // all three schedule seeds must pass the same pre-registered safety gate.
  const candidates=POLICIES.map(policy=>({policy,runs:reports.filter(r=>r.policy===policy)})).filter(x=>x.runs.every(r=>r.researchGatePassed&&(r.format==='SWB1'||r.inferenceContract==='SWB1_INTENT_PLUS_SWB2_SEMANTIC_RESEARCH')));
  const score=x=>x.runs.map(r=>r.checkpoints.find(c=>c.steps===r.selectedSteps).newValidation.semantic.correct).sort((a,b)=>a-b)[1];
  candidates.sort((a,b)=>score(b)-score(a)||a.policy.localeCompare(b.policy));
  json(out,'selection.json',{schema:1,codeSha,baselineHash:EXPECTED,fixtureSeal:seal.files,models:reports.map(r=>({name:r.name,hash:r.outputModelHash,researchGatePassed:r.researchGatePassed,format:r.format,inferenceContract:r.inferenceContract,intentEncoderHash:r.intentEncoderHash})),selectedPolicy:candidates[0]?.policy??null,rule:'all3seeds pass; highest median validation semantic count; SWB1 or explicit research dual contract, no productive migration',finalOpened:false,productionPromotion:false});
 }else{
  const selection=JSON.parse(fs.readFileSync(path.join(inputArg,'selection.json')));if(selection.finalOpened!==false)throw Error('INVALID_SELECTION');
  const final=read('final'),base=fs.readFileSync(path.join(inputArg,'baseline.swb1'));if(sha256(base)!==EXPECTED)throw Error('BASELINE_CHANGED');
  const evaluate=(bytes,record={})=>{const semantic=SubwordEncoder.restore(bytes);if(record.inferenceContract==='SWB1_INTENT_PLUS_SWB2_SEMANTIC_RESEARCH'&&record.intentEncoderHash!==EXPECTED)throw Error('INTENT_BASELINE_BINDING');const m=record.inferenceContract==='SWB1_INTENT_PLUS_SWB2_SEMANTIC_RESEARCH'?frozenIntentView(SubwordEncoder.restore(base),semantic):semantic;return {...measurements(m,final),dialogue:final.dialogue.map(d=>{const p=m.similarity(d.turns.join(' '),d.claim);return {id:d.id,expected:d.consistent,similarity:p,correct:(p>=.5)===d.consistent};})};};
  const runs=[];
  for(const record of selection.models){const bytes=fs.readFileSync(path.join(inputArg,record.name+'.bin'));if(sha256(bytes)!==record.hash)throw Error('CANDIDATE_CHANGED');runs.push({name:record.name,validationEligible:record.researchGatePassed,...evaluate(bytes,record)});}
  json(out,'final-report.json',{schema:1,codeSha,selectionSource:selection.codeSha,selectionHash:sha256(JSON.stringify(selection)),selectedPolicyBeforeFinal:selection.selectedPolicy,baseline:evaluate(base),runs,finalUsedForSelection:false,productionPromotion:false,legacy52UsedForSelection:false,meaning:'synthetic semantic discrimination and context consistency probes, not Android dialogue answer quality'});
  console.log(JSON.stringify({selectedPolicy:selection.selectedPolicy,baseline:evaluate(base).semantic,runs:runs.map(r=>({name:r.name,intent:r.intent.correct,semantic:r.semantic.correct,falseSimilar:r.falseSimilar,missedSimilar:r.missedSimilar}))}));
 }
}
try{main();}catch(e){console.error('CONTRASTIVE_FAILED: '+e.message);process.exitCode=1;}
