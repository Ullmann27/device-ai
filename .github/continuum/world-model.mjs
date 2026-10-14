import crypto from "node:crypto";

export function createWorldModel(now=Date.now()){
  return {schemaVersion:2,createdAt:now,updatedAt:now,hypotheses:[],history:[]};
}

export function migrateWorldModel(value,now=Date.now()){
  if(!value||![1,2].includes(value.schemaVersion))return createWorldModel(now);
  const m=structuredClone(value);
  m.schemaVersion=2;
  m.hypotheses=Array.isArray(m.hypotheses)?m.hypotheses.slice(-160):[];
  m.history=Array.isArray(m.history)?m.history.slice(-256):[];
  for(const h of m.hypotheses){
    h.supportEvidence=Array.isArray(h.supportEvidence)?h.supportEvidence.slice(-32):[];
    h.contradictEvidence=Array.isArray(h.contradictEvidence)?h.contradictEvidence.slice(-32):[];
    h.inconclusiveEvidence=Array.isArray(h.inconclusiveEvidence)?h.inconclusiveEvidence.slice(-32):[];
    recomputeHypothesis(h);
  }
  return m;
}

export function proposeHypothesis(modelInput,{claim,beliefId=null,citationUrls=[],confidence=.4,now=Date.now()}){
  const model=migrateWorldModel(modelInput,now);
  const text=clean(claim,650);
  if(text.length<35)return {model,hypothesis:null};
  const id="hyp-"+sha256(text.toLocaleLowerCase("de-DE")).slice(0,20);
  let h=model.hypotheses.find(x=>x.id===id);
  if(!h){
    h={
      id,claim:text,beliefIds:beliefId?[beliefId]:[],
      citationUrls:[...new Set(citationUrls)].slice(0,12),
      baseConfidence:clamp(confidence,.05,.85),confidence:clamp(confidence,.05,.85),
      status:"PROVISIONAL",attempts:0,
      supportEvidence:[],contradictEvidence:[],inconclusiveEvidence:[],
      createdAt:now,lastTestedAt:null,
      testQuestions:[
        "Finde unabhängige Evidenz, die diese Hypothese widerlegen oder stark begrenzen könnte: "+text,
        "Unter welchen Randbedingungen oder Gegenbeispielen gilt diese Hypothese nicht: "+text
      ]
    };
    model.hypotheses.push(h);
  }else{
    if(beliefId&&!h.beliefIds.includes(beliefId))h.beliefIds.push(beliefId);
    h.citationUrls=[...new Set([...h.citationUrls,...citationUrls])].slice(0,12);
    h.baseConfidence=Math.max(h.baseConfidence,clamp(confidence,.05,.85));
  }
  model.hypotheses=model.hypotheses.slice(-160);
  model.updatedAt=now;
  return {model,hypothesis:h};
}

export function selectHypothesisTest(modelInput,now=Date.now()){
  const model=migrateWorldModel(modelInput,now);
  const candidates=model.hypotheses.filter(h=>!["RETIRED","SUPPORTED"].includes(h.status));
  return candidates.map(h=>{
    const age=h.lastTestedAt?Math.min(1,(now-h.lastTestedAt)/86400000):1;
    const uncertainty=1-h.confidence;
    const exploration=1/Math.sqrt(1+h.attempts);
    return {hypothesis:h,score:uncertainty*.65+age*.2+exploration*.35};
  }).sort((a,b)=>b.score-a.score||a.hypothesis.id.localeCompare(b.hypothesis.id))[0]?.hypothesis??null;
}

export function evaluateHypothesisEvidence(modelInput,hypothesisId,{text,citations=[],now=Date.now()}){
  const model=migrateWorldModel(modelInput,now);
  const h=model.hypotheses.find(x=>x.id===hypothesisId);
  if(!h)return {model,classification:"MISSING",hypothesis:null};
  const overlap=tokenOverlap(h.claim,text);
  const negationDiff=hasNegation(h.claim)!==hasNegation(text);
  const domains=[...new Set(citations.map(x=>domainOf(x.url)).filter(Boolean))];
  const sourceKeys=[...new Set(citations.map(citationKey).filter(Boolean))];
  let classification="INCONCLUSIVE";
  if(overlap>=.24)classification=negationDiff?"CONTRADICT":"SUPPORT";
  const evidence={
    id:"ev-"+sha256([hypothesisId,text,domains.join(",")].join("\0")).slice(0,20),
    classification,overlap,domains,sourceKeys,text:clean(text,900),createdAt:now
  };
  const bucket=classification==="SUPPORT"?h.supportEvidence:
    classification==="CONTRADICT"?h.contradictEvidence:h.inconclusiveEvidence;
  if(!bucket.some(x=>x.id===evidence.id))bucket.push(evidence);
  h.attempts+=1;h.lastTestedAt=now;
  recomputeHypothesis(h);
  model.history.push({at:now,hypothesisId,classification,confidence:h.confidence,status:h.status});
  model.history=model.history.slice(-256);
  model.updatedAt=now;
  return {model,classification,hypothesis:h};
}

export function worldModelStatus(modelInput){
  const model=migrateWorldModel(modelInput);
  return {
    total:model.hypotheses.length,
    provisional:model.hypotheses.filter(x=>x.status==="PROVISIONAL").length,
    supported:model.hypotheses.filter(x=>x.status==="SUPPORTED").length,
    disputed:model.hypotheses.filter(x=>x.status==="DISPUTED").length,
    retired:model.hypotheses.filter(x=>x.status==="RETIRED").length,
    evidenceTests:model.history.length
  };
}

function recomputeHypothesis(h){
  const supportEvents=independentEvidenceEvents(h.supportEvidence);
  const contradictEvents=independentEvidenceEvents(h.contradictEvidence);
  const supportDomains=new Set(h.supportEvidence.flatMap(x=>x.domains??[])).size;
  const contradictDomains=new Set(h.contradictEvidence.flatMap(x=>x.domains??[])).size;
  let confidence=clamp(
    Number(h.baseConfidence??.35) +
      Math.min(.28,supportEvents*.10+supportDomains*.025) -
      Math.min(.48,contradictEvents*.18+contradictDomains*.06) -
      Math.min(.12,(h.inconclusiveEvidence?.length??0)*.015),
    .05,.95
  );
  if(supportEvents<2)confidence=Math.min(confidence,.84);
  h.confidence=confidence;
  h.status=contradictEvents>=1?"DISPUTED":
    supportEvents>=2&&confidence>=.68?"SUPPORTED":
    confidence<.18?"RETIRED":"PROVISIONAL";
  h.independentSupportEvents=supportEvents;
  h.independentContradictEvents=contradictEvents;
}

function independentEvidenceEvents(events){
  const sorted=[...(events??[])].sort((a,b)=>(a.createdAt??0)-(b.createdAt??0));
  const seenDomains=new Set();
  let independent=0;
  for(const event of sorted){
    const domains=[...new Set((event.domains??[]).filter(Boolean))];
    if(!domains.length)continue;
    const addsNovelDomain=domains.some(d=>!seenDomains.has(d));
    if(addsNovelDomain)independent++;
    domains.forEach(d=>seenDomains.add(d));
  }
  return independent;
}

function citationKey(citation){
  const title=String(citation?.title??"").toLocaleLowerCase("en-US")
    .replace(/[^\p{L}\p{N}]+/gu," ").trim();
  if(title.length>=18)return "title:"+title.slice(0,240);
  try{
    const u=new URL(String(citation?.url??""));
    if(u.hostname.toLocaleLowerCase("en-US")==="doi.org")return "doi:"+decodeURIComponent(u.pathname).toLocaleLowerCase("en-US");
    return "url:"+u.hostname.toLocaleLowerCase("en-US")+u.pathname.replace(/\/$/,"");
  }catch{return "";}
}

function tokenOverlap(a,b){
  const A=tokens(a),B=tokens(b);if(!A.size||!B.size)return 0;
  const inter=[...A].filter(x=>B.has(x)).length;
  return inter/Math.min(A.size,B.size);
}
function tokens(text){return new Set(String(text).toLocaleLowerCase("de-DE").split(/[^\p{L}\p{N}]+/u).filter(x=>x.length>=4&&!STOP.has(x)));}
const STOP=new Set(["dass","dieser","diese","eine","einer","einen","sind","wird","werden","with","that","this","from","into","using","über","unter","auch","nicht"]);
function hasNegation(text){return /\b(nicht|kein|keine|never|no|not|cannot|fails?|unmöglich|widerlegt)\b/i.test(String(text));}
function domainOf(url){try{return new URL(url).hostname.replace(/^www\./,"");}catch{return "";}}
function sha256(v){return crypto.createHash("sha256").update(String(v)).digest("hex");}
function clean(v,max){return String(v??"").replace(/[\u0000-\u001f\u007f]/g," ").replace(/\s+/g," ").trim().slice(0,max);}
function clamp(v,min,max){return Math.min(max,Math.max(min,Number.isFinite(Number(v))?Number(v):min));}
