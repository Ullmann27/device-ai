import test from "node:test";
import assert from "node:assert/strict";
import crypto from "node:crypto";
import {
  ATLAS_GRAPH_SCHEMA,createEmptyAtlasGraph,classifyAtlasSource,
  planAtlasQuestions,ingestAtlasDocument,retrieveAtlasEvidence
} from "./worker-atlas-epistemic-graph.mjs";

const hash = text => crypto.createHash("sha256").update(text,"utf8").digest("hex");
const T = 1800000000000;
const claim = "Kotlin coroutines support structured cancellation under a structured concurrency scope.";
const doc = claim+" An isolated independent paragraph.";
const base = {
  topic:"Kotlin coroutines cancellation",
  claimText:claim,sourceText:doc,expectedSourceSha256:hash(doc),
  publishedAtMs:T-10000,now:T
};

test("trusted naming describes publisher origin only, never fact truth",()=>{
  assert.equal(classifyAtlasSource("https://developer.android.com/guide").tier,"OFFICIAL_VENDOR_DOCS");
  assert.equal(classifyAtlasSource("https://arxiv.org/abs/1234").tier,"RESEARCH_PREPRINT");
  assert.equal(classifyAtlasSource("https://www.anyblog.net/post").tier,"PUBLIC_WEB");
  for(const unsafe of [
    "http://developer.android.com/article",
    "https://user:pass@developer.android.com/",
    "https://127.0.0.1/internal",
    "https://metadata.internal/secrets",
    "file:///etc/passwd",
    "https://developer.android.com.evil.example/promo"
  ]) {
    if(unsafe.includes("evil.example")) {
      assert.equal(classifyAtlasSource(unsafe).tier,"PUBLIC_WEB");
    } else {
      assert.throws(()=>classifyAtlasSource(unsafe));
    }
  }
});

test("exact source substring and cryptographic document match are mandatory",()=>{
  const graph=createEmptyAtlasGraph(T);
  const first=ingestAtlasDocument(graph,{...base,sourceUrl:"https://kotlinlang.org/docs/"});
  assert.equal(first.status,"SINGLE_SOURCE_OBSERVATION");
  assert.equal(first.ownerConfirmed,false);
  assert.equal(first.trainingApproved,false);
  assert.equal(first.graph.claims[0].sources[0].documentSha256,hash(doc));
  assert.equal(graph.claims.length,0);
  assert.throws(()=>ingestAtlasDocument(graph,{...base,sourceUrl:"https://kotlinlang.org/docs/",
    expectedSourceSha256:"0".repeat(64)}),/SOURCE_HASH_MISMATCH/);
  assert.throws(()=>ingestAtlasDocument(graph,{...base,sourceUrl:"https://kotlinlang.org/docs/",
    claimText:claim.replace("cancellation","participation")}),/CLAIM_NOT_EXACT_SOURCE_SUBSTRING/);
});

test("two different source domains are corroboration, never verified truth",()=>{
  const url1="https://kotlinlang.org/docs/coroutines-basics.html";
  const url2="https://developer.android.com/kotlin/coroutines";
  let graph=createEmptyAtlasGraph(T);
  let event=ingestAtlasDocument(graph,{...base,sourceUrl:url1});
  graph=event.graph;
  event=ingestAtlasDocument(graph,{...base,sourceUrl:url1,now:T+1});
  assert.equal(event.status,"SINGLE_SOURCE_OBSERVATION");
  assert.equal(event.corroboratingHosts,1);
  graph=event.graph;
  event=ingestAtlasDocument(graph,{...base,sourceUrl:url2,now:T+2});
  graph=event.graph;
  assert.equal(event.status,"MULTISOURCE_OBSERVATION");
  assert.equal(event.corroboratingHosts,2);
  assert.equal(graph.claims[0].ownerConfirmed,false);
  assert.equal(graph.claims[0].trainingApproved,false);
  const results=retrieveAtlasEvidence(graph,"Kotlin coroutines",{limit:4});
  assert.equal(results.observations.length,1);
  assert.equal(results.observations[0].status,"MULTISOURCE_OBSERVATION");
});

test("potential version or negation conflicts get review flags, not silent supersession",()=>{
  const other="Kotlin coroutines do not support structured cancellation under a structured concurrency scope.";
  let g=ingestAtlasDocument(createEmptyAtlasGraph(T),{
    ...base,sourceUrl:"https://kotlinlang.org/docs/"
  }).graph;
  const next=ingestAtlasDocument(g,{
    ...base,claimText:other,sourceText:other,expectedSourceSha256:hash(other),
    sourceUrl:"https://www.example.edu/software",now:T+5
  });
  g=next.graph;
  assert.equal(g.reviewQueue.length,1);
  assert.equal(next.status,"REVIEW_REQUIRED");
  assert.equal(g.claims.filter(x=>x.status==="REVIEW_REQUIRED").length,2);
  assert.equal(g.claims.some(x=>x.status==="SUPERSEDED"),false);
  const found=retrieveAtlasEvidence(g,"coroutines cancellation");
  assert.equal(found.observations.length,0);
  assert.equal(found.reviewRequired.length,2);
});

test("active-counterexample search plans are bounded descriptions, not executable requests",()=>{
  const q=planAtlasQuestions("Android Kotlin security");
  assert.deepEqual(q.map(x=>x.role),["PRIMARY","INDEPENDENT","COUNTER_EVIDENCE"]);
  assert.equal(q.length,3);
  assert.throws(()=>planAtlasQuestions(" "),/INVALID_TOPIC/);
  assert.throws(()=>planAtlasQuestions("x".repeat(170)),/INVALID_TOPIC/);
});

test("invalid input and malformed graph fail closed",()=>{
  const g=createEmptyAtlasGraph(T);
  assert.equal(g.schema,ATLAS_GRAPH_SCHEMA);
  assert.throws(()=>ingestAtlasDocument(g,{...base,sourceUrl:"ftp://example.org/code"}));
  assert.throws(()=>ingestAtlasDocument(g,{...base,sourceUrl:"https://example.org",
    publishedAtMs:T+1}),/INVALID_PUBLICATION_DATE/);
  assert.throws(()=>ingestAtlasDocument(g,{...base,sourceUrl:"https://example.org",
    sourceText:"x".repeat(129*1024)}),/INVALID_SOURCE_TEXT/);
  assert.throws(()=>ingestAtlasDocument({...g,claims:[{id:"untrusted",status:"OWNER_CONFIRMED"}]},
    {...base,sourceUrl:"https://example.org"}),/INVALID_ATLAS_GRAPH/);
  assert.throws(()=>retrieveAtlasEvidence(g,"coroutines",{limit:100}),/INVALID_QUERY/);
});
