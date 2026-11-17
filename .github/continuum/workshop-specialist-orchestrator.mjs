/**
 * Continuum dynamic specialist council v1.
 *
 * Roles are bounded work contracts, not separate instantiated LLMs.
 * The caller must provide a real code synthesizer. Critic approval is only a
 * candidate review; independent tests, signing and owner approval are separate.
 */
import crypto from "node:crypto";
import {critique} from "./repair-critic.mjs";

const MAX_STEPS=8;
const MAX_ROUNDS=2;
const MAX_EDITS=3;
const MAX_CODE_BYTES=18_000;
const sha=value=>crypto.createHash("sha256").update(JSON.stringify(value)).digest("hex");

export const SPECIALIST_CATALOG=Object.freeze({
  ANDROID_DEVELOPER:{id:"ANDROID_DEVELOPER",kind:"MODEL_OR_TEMPLATE"},
  FLUTTER_DEVELOPER:{id:"FLUTTER_DEVELOPER",kind:"MODEL_OR_TEMPLATE"},
  GODOT_DEVELOPER:{id:"GODOT_DEVELOPER",kind:"MODEL_OR_TEMPLATE"},
  IOS_DEVELOPER:{id:"IOS_DEVELOPER",kind:"MODEL_OR_TEMPLATE"},
  WEB_DEVELOPER:{id:"WEB_DEVELOPER",kind:"MODEL_OR_TEMPLATE"},
  UI_ACCESSIBILITY:{id:"UI_ACCESSIBILITY",kind:"CONSTRAINTS"},
  DATA_ARCHITECT:{id:"DATA_ARCHITECT",kind:"CONSTRAINTS"},
  SECURITY_GUARD:{id:"SECURITY_GUARD",kind:"HEURISTIC_REVIEW"},
  TEST_DESIGNER:{id:"TEST_DESIGNER",kind:"MODEL_OR_TEMPLATE"},
  RELEASE_JUDGE:{id:"RELEASE_JUDGE",kind:"EVIDENCE_GATE"}
});

export function selectSpecialistRoster(goal){
  if(typeof goal!=="string"||goal.trim().length<8||goal.length>500)throw Error("INVALID_GOAL");
  const lower=goal.toLowerCase();
  const platform=/\b(?:flutter|dart)\b/.test(lower)?"FLUTTER_DEVELOPER":
    /\b(?:godot|gdscript)\b/.test(lower)?"GODOT_DEVELOPER":
    /\b(?:swift|ios|xcode|iphone)\b/.test(lower)?"IOS_DEVELOPER":
    /\b(?:node|webseite|webapp|javascript|typescript|website)\b/.test(lower)?"WEB_DEVELOPER":
    "ANDROID_DEVELOPER";
  const ids=[platform];
  if(/(?:app|ui|ux|design|layout|oberfläche|screen|button|zugänglich|notiz)/i.test(lower))
    ids.push("UI_ACCESSIBILITY");
  if(/(?:bank|zahlung|database|datenbank|speicher|storage|sql|konto|notiz)/i.test(lower))
    ids.push("DATA_ARCHITECT");
  ids.push("SECURITY_GUARD","TEST_DESIGNER","RELEASE_JUDGE");
  return ids.map(id=>SPECIALIST_CATALOG[id]);
}

function validateEdits(value,{tests=false}={}){
  if(!Array.isArray(value)||value.length<1||value.length>MAX_EDITS)throw Error("INVALID_EDIT_SET");
  let total=0;
  for(const e of value){
    if(!e ||typeof e.file!=="string"||typeof e.oldText!=="string"||
      typeof e.newText!=="string"||e.file.length>220||
      e.file.includes("..")||e.file.startsWith("/")||
      !/^[A-Za-z0-9_./-]+$/.test(e.file))throw Error("INVALID_EDIT");
    if(e.oldText===e.newText || !e.newText.trim())throw Error("EMPTY_EDIT");
    total+=Buffer.byteLength(e.newText,"utf8");
    const isTest=/(^|\/)(test|androidTest)\//.test(e.file)||/\.test\.m?js$/.test(e.file);
    if(tests!==isTest)throw Error("WRONG_EDIT_CLASS");
  }
  if(total>MAX_CODE_BYTES)throw Error("EDIT_BYTE_LIMIT");
  if(new Set(value.map(e=>e.file)).size!==value.length)throw Error("DUPLICATE_EDIT_PATH");
  return structuredClone(value);
}
function heuristicSecurity(goal,edits){
 const findings=[];
 for(const edit of edits){
  const s=edit.newText;
  if(/http:\/\//i.test(s))findings.push({code:"CLEARTEXT_URL",file:edit.file});
  if(/MODE_WORLD_READABLE|MODE_WORLD_WRITEABLE/.test(s))
    findings.push({code:"WORLD_READABLE_STORAGE",file:edit.file});
  if(/(?:Log\.[divew]|println|console\.log)\s*\([^)]*(?:password|token|secret|iban|pin)\b/i.test(s))
    findings.push({code:"CREDENTIAL_LOGGING",file:edit.file});
  if(/(?:bank|payment|zahlung|finanz|konto)/i.test(goal) && /SharedPreferences/.test(s))
    findings.push({code:"BANKING_STORAGE_REQUIRES_SPECIALIST_REVIEW",file:edit.file});
 }
 const assessment=critique({strategy:"WORKSHOP_CANDIDATE",prior:0.5,edits});
 for(const reason of assessment.reasons)findings.push({code:"REPAIR_CRITIC",detail:reason});
 return {passed:findings.length===0,findings,
   manualSecurityReviewRequired:/(?:bank|payment|zahlung|finanz|konto)/i.test(goal)};
}

export function runSpecialistWorkshop({
 taskId,goal,developerSynthesizer,testSynthesizer,
 now=Date.now
}={}){
 if(typeof taskId!=="string"||!/^[-a-z0-9]{5,64}$/.test(taskId)||
  typeof developerSynthesizer!=="function"||
  typeof testSynthesizer!=="function")throw Error("INVALID_WORKSHOP_CONTRACT");
 const roster=selectSpecialistRoster(goal);
 const transcript=[];
 const record=(role,state,payload)=>{
  if(transcript.length>=MAX_STEPS)throw Error("SPECIALIST_STEP_BUDGET");
  transcript.push({step:transcript.length+1,role,state,at:now(),
    payloadSha256:sha(payload)});
 };
 if(roster.some(x=>x.id==="UI_ACCESSIBILITY"))
   record("UI_ACCESSIBILITY","ADVISORY",{accessibility:"Manual controls and 48dp target checks required"});
 if(roster.some(x=>x.id==="DATA_ARCHITECT"))
   record("DATA_ARCHITECT","ADVISORY",{storage:"Atomic migration and data retention tests required"});
 let edits=null,security=null;
 const developer=roster[0].id;
 for(let round=1;round<=MAX_ROUNDS;round++){
  let candidates;
  try {
    candidates=validateEdits(developerSynthesizer({
      goal,round,priorFindings:security?.findings??[],targetPlatform:developer
    }));
  }catch(e) {
    record(developer,"INVALID_CANDIDATE",{round,reason:String(e?.message??e).slice(0,100)});
    return {status:"INVALID_CANDIDATE",candidateReady:false,buildVerified:false,
      autoMergeAllowed:false,transcript};
  }
  record(developer,"CANDIDATE_DRAFTED",{round,hash:sha(candidates)});
  security=heuristicSecurity(goal,candidates);
  record("SECURITY_GUARD",security.passed?"HEURISTIC_PASS":"BLOCKED",security);
  if(security.passed){edits=candidates;break;}
 }
 if(!edits)return {status:"BLOCKED_BY_SECURITY",candidateReady:false,buildVerified:false,
   autoMergeAllowed:false,transcript,findings:security.findings};
 let testEdits;
 try {testEdits=validateEdits(testSynthesizer({goal,edits}),{tests:true});}
 catch(e){
   record("TEST_DESIGNER","MISSING_TESTS",{reason:String(e?.message??e).slice(0,120)});
   return {status:"MISSING_TESTS",candidateReady:false,buildVerified:false,
     autoMergeAllowed:false,transcript};
 }
 record("TEST_DESIGNER","PROPOSED",{tests:testEdits.map(x=>x.file)});
 const verification={sourceChecks:"HEURISTIC_ONLY",compilerExecuted:false,
   unitTestsExecuted:false,independentWorktreeVerified:false,
   ownerApprovalReceived:false,requiresManualSecurityReview:security.manualSecurityReviewRequired};
 record("RELEASE_JUDGE","PENDING_BUILD_VERIFICATION",verification);
 return {
  status:"CANDIDATE_READY_FOR_SANDBOX",
  candidateReady:true,buildVerified:false,autoMergeAllowed:false,
  platformRole:developer,roster:roster.map(x=>x.id),
  edits,testEdits,verification,transcript
 };
}
