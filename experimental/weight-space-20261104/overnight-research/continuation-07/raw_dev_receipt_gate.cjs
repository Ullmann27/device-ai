"use strict";
function fail(code){throw new Error(code);}
function rows(log,type){
  if(typeof log!=="string" || !log.endsWith("\n"))fail("BAD_LOG");
  return log.split("\n").filter(line=>line.startsWith(type+"\t")).map(line=>{
    const r={};
    for(const part of line.split("\t").slice(1)){
      const i=part.indexOf("=");
      if(i<1)fail("BAD_FIELD");
      const k=part.slice(0,i);
      if(Object.prototype.hasOwnProperty.call(r,k))fail("DUP_FIELD");
      r[k]=part.slice(i+1);
    }
    return r;
  });
}
function nat(s){if(typeof s!=="string" || !/^(0|[1-9][0-9]*)$/.test(s) ||
  !Number.isSafeInteger(Number(s)))fail("BAD_NUMBER");return Number(s);}
function verify(log,receipt,pin,sha256){
  if(typeof sha256!=="function" || !pin || !["v1","v2","v3"].includes(pin.version) ||
    !["small","wide"].includes(pin.arm))fail("BAD_ARGUMENT");
  if(!receipt || receipt.label!=="intent-"+pin.arm || receipt.returncode!==0 ||
    receipt.timeout!==false || receipt.registeredBytesUnchanged!==true)fail("BAD_RECEIPT");
  if(receipt.sourceCommit!==pin.sourceCommit ||
    receipt.registrationSha256!==pin.registrationSha256 ||
    receipt.jarSha256!==pin.jarSha256)fail("BAD_IDENTITY");
  if(receipt.logSha256!==sha256(log) || pin.logSha256!==receipt.logSha256)fail("BAD_HASH");
  const checkpoints=rows(log,"CHECKPOINT");
  const scores=rows(log,"SCORE");
  const selected=rows(log,"SELECTION");
  const manifest=rows(log,"MANIFEST");
  const gate=rows(log,"INITIAL_CAPACITY_GATE");
  const steps=pin.version==="v1"?[0,32,64,128]:[0,128,256,512];
  function checkSteps(items){if(items.length!==4 ||
    items.map(x=>nat(x.step)).sort((a,b)=>a-b).some((x,i)=>x!==steps[i]))fail("BAD_STEPS");}
  checkSteps(checkpoints);checkSteps(scores);
  if(manifest.length!==1 || manifest[0].variant!==pin.arm ||
    nat(manifest[0].steps)!==steps[3] ||
    manifest[0].testReadDuringTraining!=="false")fail("BAD_MANIFEST");
  if(gate.length!==1 || gate[0].pass!=="true" ||
    gate[0].checkpointSha256!==checkpoints.find(x=>x.step==="0").sha256)fail("BAD_INITIAL_GATE");
  if(selected.length!==1)fail("BAD_SELECTION");
  for(const s of scores)if(s.split!=="dev" || nat(s.correct)>24 ||
    nat(s.total)!==24 || !Number.isFinite(Number(s.answerNll)) ||
    s.answerNll==="" || Number(s.answerNll)<0)fail("BAD_SCORE");
  const baseline=scores.find(x=>x.step==="0");
  const best=scores.filter(x=>x.step!=="0").sort((a,b)=>
    nat(b.correct)-nat(a.correct) ||
    Number(a.answerNll)-Number(b.answerNll) || nat(a.step)-nat(b.step))[0];
  const choice=selected[0];
  if(choice.step!==best.step || choice.devCorrect!==best.correct ||
    choice.devTotal!=="24" || Number(choice.devAnswerNll)!==Number(best.answerNll) ||
    choice.testRead!=="false" || choice.inputsUnchanged!=="true")fail("BAD_SELECTION");
  if(checkpoints.find(x=>x.step===choice.step).sha256!==choice.checkpointSha256)fail("BAD_CHECKPOINT_ID");
  const initial=nat(baseline.correct),chosen=nat(choice.devCorrect);
  return {arm:pin.arm,version:pin.version,initial,chosen,step:nat(choice.step),
    developmentSignal:chosen>=12 && chosen>=initial+4,modelRun:false,
    finalTestAuthorized:false};
}
function study(s,w,pin,sha256){
  const small=verify(s.log,s.receipt,{...pin.small,arm:"small"},sha256);
  const wide=verify(w.log,w.receipt,{...pin.wide,arm:"wide"},sha256);
  const status=wide.developmentSignal?"ELIGIBLE":"NO_DEVELOPMENT_SIGNAL";
  if(pin.claimedStatus && pin.claimedStatus!==status)fail("WRONG_CLAIM");
  return {status,small,wide,finalTestAuthorized:false};
}
if(typeof module!=="undefined")module.exports={verify,study};
