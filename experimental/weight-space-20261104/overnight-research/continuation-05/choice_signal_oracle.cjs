"use strict";
/* G1 research-only synthetic choice consistency oracle.
 * Does not authenticate evidence, consume a final test, or authorize training.
 * "evidence" and "pins" MUST come from separately SHA-verified sources.
 */
function blocked(reason) { throw new Error("CHOICE_SIGNAL_BLOCKED: " + reason); }
function nat(v,max=24) { return Number.isInteger(v) && v >= 0 && v <= max; }
function hex(v,n=64) { return typeof v === "string" && new RegExp("^[0-9a-f]{"+n+"}$").test(v); }
function signal(initial, selected) {
  if (!nat(initial) || !nat(selected)) blocked("missing/out-of-range baseline or selected score");
  return selected >= 12 && selected >= initial + 4;
}
function validateCandidate(candidate, evidence, pins) {
  if (!candidate || typeof candidate !== "object" || !evidence || typeof evidence !== "object" ||
      !pins || typeof pins !== "object") blocked("missing independent evidence");
  if (!hex(pins.sourceCommit,40) || !hex(pins.registrationSha256) ||
      !hex(pins.wideLogSha256) || !hex(pins.selectedCheckpointSha256))
    blocked("missing source pins");
  for (const k of ["sourceCommit","registrationSha256","wideLogSha256","selectedCheckpointSha256"])
    if (evidence[k] !== pins[k]) blocked("evidence source identity mismatch: " + k);
  if (!nat(evidence.initialWideCorrect) || !nat(evidence.selectedWideCorrect) ||
      evidence.devTotal !== 24 || !Number.isFinite(evidence.wideAnswerNll) ||
      evidence.wideAnswerNll < 0) blocked("invalid original wide development score");
  const expected = signal(evidence.initialWideCorrect,evidence.selectedWideCorrect) ?
                   "ELIGIBLE" : "NO_DEVELOPMENT_SIGNAL";
  if (candidate.status !== expected) blocked("status contradicts independent baseline");
  if (candidate.status === "NO_DEVELOPMENT_SIGNAL") {
    if (!candidate.selected || !candidate.selected.wide ||
        candidate.selected.wide.devCorrect !== String(evidence.selectedWideCorrect) ||
        candidate.selected.wide.devTotal !== "24" ||
        candidate.sourceCommit !== pins.sourceCommit ||
        candidate.registrationSha256 !== pins.registrationSha256)
      blocked("ineligible candidate selected/identity mismatch");
  } else {
    const p = candidate.freezePayload;
    if (!p || !p.arms || !p.arms.W1 ||
        p.arms.W1.devCorrect !== String(evidence.selectedWideCorrect) ||
        p.arms.W1.checkpointSha256 !== pins.selectedCheckpointSha256 ||
        p.sourceCommit !== pins.sourceCommit ||
        p.registrationSha256 !== pins.registrationSha256)
      blocked("eligible candidate freeze/identity mismatch");
  }
  return {status:expected, correct:evidence.selectedWideCorrect, nll:evidence.wideAnswerNll};
}
function choose(valid) {
  if (!valid || Object.keys(valid).sort().join(",") !== "v2,v3") blocked("two candidates required");
  const e=Object.entries(valid).filter(([,x])=>x.status==="ELIGIBLE");
  if (!e.length) blocked("no eligible study");
  e.sort(([a,x],[b,y])=>y.correct-x.correct || x.nll-y.nll || (a==="v2" ? -1:1));
  return e[0][0];
}
function runSyntheticTests() {
  const A="a".repeat(64),B="b".repeat(64),C="c".repeat(40),D="d".repeat(64);
  const pins={sourceCommit:C,registrationSha256:D,wideLogSha256:A,selectedCheckpointSha256:B};
  const ev={...pins,initialWideCorrect:8,selectedWideCorrect:13,devTotal:24,wideAnswerNll:1.25};
  const mk=(status, e=ev)=>status==="ELIGIBLE" ?
    {status,freezePayload:{sourceCommit:C,registrationSha256:D,arms:{W1:{devCorrect:String(e.selectedWideCorrect),
      checkpointSha256:B}}}} :
    {status,sourceCommit:C,registrationSha256:D,selected:{small:{},wide:{devCorrect:String(e.selectedWideCorrect),devTotal:"24"}}};
  const cases=[];
  const test=(id,fn,pass)=>{let accepted=false,reason="";try{fn();accepted=true;}catch(e){reason=String(e.message)}
    if (accepted!==pass) throw new Error("synthetic fixture "+id+" failed (accepted="+accepted+"; "+reason+")");
    cases.push({id,result:pass?"ACCEPT":"BLOCK"});};
  test("T01-eligible-gain5",()=>validateCandidate(mk("ELIGIBLE"),ev,pins),true);
  test("T02-false-no-signal-gain5",()=>validateCandidate(mk("NO_DEVELOPMENT_SIGNAL"),ev,pins),false);
  const high={...ev,initialWideCorrect:23,selectedWideCorrect:24};
  test("T03-legitimate-no-signal-gain1",()=>validateCandidate(mk("NO_DEVELOPMENT_SIGNAL",high),high,pins),true);
  test("T04-false-eligible-gain1",()=>validateCandidate(mk("ELIGIBLE",high),high,pins),false);
  const low={...ev,initialWideCorrect:0,selectedWideCorrect:11};
  test("T05-legitimate-no-signal-below12",()=>validateCandidate(mk("NO_DEVELOPMENT_SIGNAL",low),low,pins),true);
  test("T06-false-eligible-below12",()=>validateCandidate(mk("ELIGIBLE",low),low,pins),false);
  const edge={...ev,initialWideCorrect:8,selectedWideCorrect:12};
  test("T07-boundary-gain4",()=>validateCandidate(mk("ELIGIBLE",edge),edge,pins),true);
  test("T08-false-no-signal-boundary",()=>validateCandidate(mk("NO_DEVELOPMENT_SIGNAL",edge),edge,pins),false);
  test("T09-missing-baseline",()=>validateCandidate(mk("ELIGIBLE"),{...ev,initialWideCorrect:null},pins),false);
  test("T10-tampered-source-commit",()=>validateCandidate(mk("ELIGIBLE"),{...ev,sourceCommit:"f".repeat(40)},pins),false);
  test("T11-tampered-registration",()=>validateCandidate(mk("ELIGIBLE"),{...ev,registrationSha256:"f".repeat(64)},pins),false);
  test("T12-tampered-checkpoint",()=>validateCandidate(mk("ELIGIBLE"),{...ev,selectedCheckpointSha256:"f".repeat(64)},pins),false);
  test("T13-tampered-wide-log",()=>validateCandidate(mk("ELIGIBLE"),{...ev,wideLogSha256:"f".repeat(64)},pins),false);
  test("T14-nonfinite-nll",()=>validateCandidate(mk("ELIGIBLE"),{...ev,wideAnswerNll:Infinity},pins),false);
  test("T15-v2-v3-tie-prior-v2",()=>{if(choose({v2:{status:"ELIGIBLE",correct:13,nll:1},v3:{status:"ELIGIBLE",correct:13,nll:1}})!=="v2")blocked("tie");},true);
  test("T16-v3-higher-correct",()=>{if(choose({v2:{status:"ELIGIBLE",correct:13,nll:1},v3:{status:"ELIGIBLE",correct:14,nll:2}})!=="v3")blocked("ranking");},true);
  test("T17-nll-breaks-tie",()=>{if(choose({v2:{status:"ELIGIBLE",correct:13,nll:2},v3:{status:"ELIGIBLE",correct:13,nll:1}})!=="v3")blocked("ranking");},true);
  test("T18-no-eligible",()=>choose({v2:{status:"NO_DEVELOPMENT_SIGNAL"},v3:{status:"NO_DEVELOPMENT_SIGNAL"}}),false);
  test("T19-missing-v3",()=>choose({v2:{status:"ELIGIBLE",correct:13,nll:1}}),false);
  test("T20-ineligible-source-identity",()=>validateCandidate({...mk("NO_DEVELOPMENT_SIGNAL",high),sourceCommit:"f".repeat(40)},high,pins),false);
  return cases;
}
module.exports={signal,validateCandidate,choose,runSyntheticTests};
if (typeof require!=="undefined" && require.main===module) {
  const results=runSyntheticTests();
  console.log(JSON.stringify({testCount:results.length,pass:results.length,cases:results},null,2));
}
