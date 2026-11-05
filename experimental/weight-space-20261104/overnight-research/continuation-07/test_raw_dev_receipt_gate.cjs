"use strict";
const crypto=require("node:crypto");
const api=require("./raw_dev_receipt_gate.cjs");
const sha=s=>crypto.createHash("sha256").update(s,"utf8").digest("hex");
function make(o={}){
 const ver=o.version||"v3",arm=o.arm||"wide";
 const steps=ver==="v1"?[0,32,64,128]:[0,128,256,512];
 const counts=o.counts||[0,16,15,14],nll=[9,1.4,1.5,1.6];
 const cps=steps.map((n,i)=>"CHECKPOINT\tstep="+n+"\tsha256="+(i+1));
 const ss=steps.map((n,i)=>"SCORE\tsplit=dev\tstep="+n+"\tcorrect="+counts[i]+"\ttotal=24\tanswerNll="+nll[i]);
 const j=o.index===undefined?1:o.index;
 let lines=["MANIFEST\tvariant="+arm+"\tsteps="+steps[3]+"\ttestReadDuringTraining=false",...cps,
 "INITIAL_CAPACITY_GATE\tpass=true\tcheckpointSha256=1",...ss,
 "SELECTION\tstep="+steps[j]+"\tdevCorrect="+counts[j]+"\tdevTotal=24\tdevAnswerNll="+nll[j]+
 "\ttestRead=false\tinputsUnchanged=true\tcheckpointSha256="+(j+1)];
 if(o.mutate)lines=o.mutate(lines);
 const log=lines.join("\n")+"\n",pin={arm,version:ver,sourceCommit:"source",jarSha256:"jar",
 registrationSha256:"registration",logSha256:sha(log)};
 const receipt={label:"intent-"+arm,returncode:0,timeout:false,registeredBytesUnchanged:true,
 sourceCommit:"source",jarSha256:"jar",registrationSha256:"registration",logSha256:sha(log)};
 return {log,pin,receipt};
}
function run(){
 let pass=0;
 function yes(f){f();pass++;}
 function no(f,c){let actual="";try{f();}catch(e){actual=e.message;}if(actual!==c)throw Error("want "+c+" got "+actual);pass++;}
 function mod(prefix,fn){return lines=>lines.map(line=>line.startsWith(prefix)?fn(line):line);}
 yes(()=>{const f=make();const r=api.verify(f.log,f.receipt,f.pin,sha);if(r.chosen!==16||r.initial!==0||!r.developmentSignal)throw Error("clean");});
 yes(()=>{const f=make({counts:[14,16,15,14]});if(api.verify(f.log,f.receipt,f.pin,sha).developmentSignal)throw Error("relative");});
 yes(()=>{const f=make({version:"v1",counts:[0,6,8,10],index:3});if(api.verify(f.log,f.receipt,f.pin,sha).developmentSignal)throw Error("absolute");});
 no(()=>{const f=make();f.receipt.logSha256="corrupt";api.verify(f.log,f.receipt,f.pin,sha);},"BAD_HASH");
 no(()=>{const f=make();f.log=f.log.replace("correct=0","correct=19");api.verify(f.log,f.receipt,f.pin,sha);},"BAD_HASH");
 no(()=>{const f=make();f.log=f.log.replace("correct=0","correct=19");f.receipt.logSha256=sha(f.log);api.verify(f.log,f.receipt,f.pin,sha);},"BAD_HASH");
 no(()=>{const f=make();f.receipt.sourceCommit="wrong";api.verify(f.log,f.receipt,f.pin,sha);},"BAD_IDENTITY");
 no(()=>{const f=make({mutate:lines=>[...lines,...lines.filter(x=>x.startsWith("SCORE\tsplit=dev\tstep=0"))]});api.verify(f.log,f.receipt,f.pin,sha);},"BAD_STEPS");
 no(()=>{const f=make({mutate:mod("SCORE\tsplit=dev\tstep=0",x=>x.replace("split=dev","split=test"))});api.verify(f.log,f.receipt,f.pin,sha);},"BAD_SCORE");
 no(()=>{const f=make({mutate:mod("SCORE\tsplit=dev\tstep=0",x=>x.replace("total=24","total=25"))});api.verify(f.log,f.receipt,f.pin,sha);},"BAD_SCORE");
 no(()=>{const f=make({mutate:mod("SELECTION\t",x=>x.replace("devCorrect=16","devCorrect=17"))});api.verify(f.log,f.receipt,f.pin,sha);},"BAD_SELECTION");
 no(()=>{const f=make({mutate:mod("SELECTION\t",x=>x.replace("step=128","step=256"))});api.verify(f.log,f.receipt,f.pin,sha);},"BAD_SELECTION");
 no(()=>{const f=make({mutate:mod("SELECTION\t",x=>x.replace("checkpointSha256=2","checkpointSha256=3"))});api.verify(f.log,f.receipt,f.pin,sha);},"BAD_CHECKPOINT_ID");
 no(()=>{const f=make({mutate:mod("INITIAL_CAPACITY_GATE\t",x=>x.replace("pass=true","pass=false"))});api.verify(f.log,f.receipt,f.pin,sha);},"BAD_INITIAL_GATE");
 no(()=>{const f=make({mutate:mod("MANIFEST\t",x=>x.replace("testReadDuringTraining=false","testReadDuringTraining=true"))});api.verify(f.log,f.receipt,f.pin,sha);},"BAD_MANIFEST");
 no(()=>{const f=make({mutate:mod("SELECTION\t",x=>x+"\tstep=128")});api.verify(f.log,f.receipt,f.pin,sha);},"DUP_FIELD");
 no(()=>{const small=make({arm:"small"}),wide=make();api.study(small,wide,{small:small.pin,wide:wide.pin,claimedStatus:"NO_DEVELOPMENT_SIGNAL"},sha);},"WRONG_CLAIM");
 return {pass,fail:0};
}
if(require.main===module)console.log(JSON.stringify(run()));
module.exports={run};
