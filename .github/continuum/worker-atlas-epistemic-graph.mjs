/**
 * Continuum evidence graph v1.
 *
 * The supplied document body must be fetched by an approved source provider
 * and its exact SHA-256 supplied by that provider. No networking, web crawling,
 * filesystem mutation or trust-class promotion occurs in this module.
 *
 * Source agreement is evidence, never proof of factual truth or permission
 * to update OWNER_CONFIRMED memory or neural training examples.
 */
import crypto from "node:crypto";

export const ATLAS_GRAPH_SCHEMA = "continuum-epistemic-evidence";
export const ATLAS_GRAPH_VERSION = 1;
const MAX_CLAIMS = 256;
const MAX_SOURCES_PER_CLAIM = 12;
const MAX_SOURCE_BYTES = 128 * 1024;
const MAX_TEXT = 600;
const SHA256 = /^[a-f0-9]{64}$/;
const sha256 = bytes => crypto.createHash("sha256").update(bytes).digest("hex");

const KNOWN_SOURCES = new Map([
  ["developer.android.com", ["OFFICIAL_VENDOR_DOCS", 4]],
  ["kotlinlang.org", ["OFFICIAL_LANGUAGE_DOCS", 4]],
  ["docs.oracle.com", ["OFFICIAL_VENDOR_DOCS", 4]],
  ["developer.apple.com", ["OFFICIAL_VENDOR_DOCS", 4]],
  ["www.rfc-editor.org", ["STANDARDS_REPOSITORY", 4]],
  ["www.w3.org", ["STANDARDS_REPOSITORY", 4]],
  ["arxiv.org", ["RESEARCH_PREPRINT", 2]],
  ["openalex.org", ["RESEARCH_INDEX", 1]],
  ["api.openalex.org", ["RESEARCH_INDEX", 1]],
  ["doi.org", ["DOI_RESOLVER", 1]],
  ["www.crossref.org", ["METADATA_REGISTRY", 1]],
  ["en.wikipedia.org", ["ENCYCLOPEDIA", 2]],
  ["de.wikipedia.org", ["ENCYCLOPEDIA", 2]],
  ["github.com", ["USER_PUBLISHED_CODE", 1]]
]);

function safeSourceUrl(value) {
  if (typeof value !== "string" || value.length < 12 || value.length > 1200) throw Error("INVALID_SOURCE_URL");
  let url;
  try { url = new URL(value); } catch { throw Error("INVALID_SOURCE_URL"); }
  if (url.protocol !== "https:" || url.username || url.password || url.port ||
      !/^[a-z0-9.-]+$/i.test(url.hostname) ||
      !url.hostname.includes(".") || url.hostname.endsWith(".local") ||
      url.hostname.endsWith(".internal") ||
      /^\d+(?:\.\d+){3}$/.test(url.hostname) || url.hostname.startsWith("127.")) {
    throw Error("INVALID_SOURCE_URL");
  }
  url.hash = "";
  return url.href;
}

export function classifyAtlasSource(url) {
  const canonicalUrl = safeSourceUrl(url);
  const host = new URL(canonicalUrl).hostname.toLowerCase();
  const [tier, sourcePriority] = KNOWN_SOURCES.get(host) ?? ["PUBLIC_WEB", 1];
  return {canonicalUrl, host, tier, sourcePriority};
}

export function createEmptyAtlasGraph(at = Date.now()) {
  if (!Number.isSafeInteger(at) || at < 0) throw Error("INVALID_CLOCK");
  return {schema: ATLAS_GRAPH_SCHEMA, version: ATLAS_GRAPH_VERSION,
    createdAt: at, updatedAt: at, claims: [], reviewQueue: []};
}

function checkedGraph(value) {
  if (!value || value.schema !== ATLAS_GRAPH_SCHEMA ||
      value.version !== ATLAS_GRAPH_VERSION ||
      !Array.isArray(value.claims) || value.claims.length > MAX_CLAIMS ||
      !Array.isArray(value.reviewQueue) || value.reviewQueue.length > MAX_CLAIMS ||
      !Number.isSafeInteger(value.createdAt) ||
      !Number.isSafeInteger(value.updatedAt)) throw Error("INVALID_ATLAS_GRAPH");
  for (const x of value.claims) {
    if (!x || typeof x.id !== "string" || typeof x.text !== "string" ||
        typeof x.topic !== "string" || typeof x.status !== "string" ||
        !Array.isArray(x.sources) || x.sources.length < 1 ||
        x.sources.length > MAX_SOURCES_PER_CLAIM ||
        !Array.isArray(x.potentialConflicts) ||
        !["SINGLE_SOURCE_OBSERVATION","MULTISOURCE_OBSERVATION","REVIEW_REQUIRED"].includes(x.status)) {
      throw Error("INVALID_ATLAS_GRAPH");
    }
  }
  return structuredClone(value);
}
function topicKey(topic) {
  if (typeof topic !== "string" || topic.trim().length < 3 ||
      topic.length > 160 || /[\u0000-\u001f\u007f]/.test(topic)) throw Error("INVALID_TOPIC");
  return topic.trim().toLowerCase();
}

function candidateConflict(a, b) {
  if (a === b) return false;
  const negate = s => /\b(?:nicht|kein|keine|niemals|not|never|cannot|deprecated|removed)\b/i.test(s);
  const versions = s => (s.match(/\b\d+(?:\.\d+)+\b/g) ?? []).join("|");
  // Hints, not proof: negation may refer to unrelated parts of a sentence.
  const va = versions(a),vb = versions(b);
  return negate(a) !== negate(b) || Boolean(va && vb && va !== vb);
}

export function planAtlasQuestions(subject) {
  const topic = topicKey(subject);
  if (topic.length > 140) throw Error("TOPIC_TOO_LONG");
  return [
    {role:"PRIMARY",query:topic+" official documentation specification"},
    {role:"INDEPENDENT",query:topic+" independent evaluation reproducible tests"},
    {role:"COUNTER_EVIDENCE",query:topic+" known limitations deprecated counterexample vulnerability"}
  ];
}

export function ingestAtlasDocument(graphInput, input) {
  const graph = checkedGraph(graphInput);
  const {topic,claimText,sourceUrl,sourceText,expectedSourceSha256,
    publishedAtMs=null,now=Date.now()} = input ?? {};
  const key = topicKey(topic);
  if (!Number.isSafeInteger(now) || now < graph.updatedAt) throw Error("INVALID_CLOCK");
  if (typeof sourceText !== "string" ||
      Buffer.byteLength(sourceText,"utf8") > MAX_SOURCE_BYTES ||
      typeof claimText !== "string") throw Error("INVALID_SOURCE_TEXT");
  const claim = claimText.trim();
  if (claim.length < 25 || claim.length > MAX_TEXT ||
      !sourceText.includes(claim)) throw Error("CLAIM_NOT_EXACT_SOURCE_SUBSTRING");
  if (typeof expectedSourceSha256 !== "string" ||
      !SHA256.test(expectedSourceSha256) ||
      sha256(Buffer.from(sourceText,"utf8")) !== expectedSourceSha256)
    throw Error("SOURCE_HASH_MISMATCH");
  if (publishedAtMs !== null && (!Number.isSafeInteger(publishedAtMs) ||
      publishedAtMs < 0 || publishedAtMs > now)) throw Error("INVALID_PUBLICATION_DATE");

  const origin = classifyAtlasSource(sourceUrl);
  const id = "clm-"+sha256(Buffer.from(key+"\u0000"+claim,"utf8")).slice(0,24);
  const source = {
    url:origin.canonicalUrl,host:origin.host,tier:origin.tier,
    documentSha256:expectedSourceSha256,publishedAtMs,observedAt:now
  };
  let record = graph.claims.find(x=>x.id===id);
  if (!record) {
    if (graph.claims.length >= MAX_CLAIMS) throw Error("ATLAS_CLAIM_LIMIT");
    record={id,topic:key,text:claim,createdAt:now,updatedAt:now,
      status:"SINGLE_SOURCE_OBSERVATION",sources:[],distinctHosts:0,
      potentialConflicts:[],ownerConfirmed:false,trainingApproved:false};
    graph.claims.push(record);
  }
  if (!record.sources.some(x=>x.documentSha256===source.documentSha256 &&
        x.url===source.url)) {
    if (record.sources.length >= MAX_SOURCES_PER_CLAIM) throw Error("ATLAS_SOURCE_LIMIT");
    record.sources.push(source);
  }
  record.distinctHosts=new Set(record.sources.map(x=>x.host)).size;
  record.status=record.distinctHosts>=2?"MULTISOURCE_OBSERVATION":"SINGLE_SOURCE_OBSERVATION";
  record.updatedAt=now;
  for (const other of graph.claims) {
    if (other.id===record.id || other.topic!==key || !candidateConflict(other.text,claim)) continue;
    if (!record.potentialConflicts.includes(other.id)) record.potentialConflicts.push(other.id);
    if (!other.potentialConflicts.includes(record.id)) other.potentialConflicts.push(record.id);
    if (!graph.reviewQueue.some(pair=>pair.includes(record.id)&&pair.includes(other.id))) {
      graph.reviewQueue.push([record.id,other.id].sort());
    }
    other.status="REVIEW_REQUIRED";
    record.status="REVIEW_REQUIRED";
  }
  graph.updatedAt=now;
  return {graph,claimId:id,status:record.status,
    ownerConfirmed:false,trainingApproved:false,
    corroboratingHosts:record.distinctHosts,potentialConflicts:record.potentialConflicts.length};
}

export function retrieveAtlasEvidence(graphInput, query, {limit=5}={}) {
  const graph=checkedGraph(graphInput);
  if (typeof query !== "string" || query.length > 180 ||
      !Number.isSafeInteger(limit) || limit < 1 || limit > 20) throw Error("INVALID_QUERY");
  const words=new Set(query.toLowerCase()
    .split(/[^\p{L}\p{N}]+/u).filter(s=>s.length>=3));
  if (words.size===0) return {observations:[],reviewRequired:[]};
  const scored=graph.claims.map(x=>{
    const target=(x.topic+" "+x.text).toLowerCase();
    const matches=[...words].filter(w=>target.includes(w)).length;
    return {matches,record:x};
  }).filter(x=>x.matches>0)
    .sort((a,b)=>b.matches-a.matches||a.record.id.localeCompare(b.record.id))
    .slice(0,limit).map(x=>x.record);
  return {
    observations:scored.filter(x=>x.status!=="REVIEW_REQUIRED"),
    reviewRequired:scored.filter(x=>x.status==="REVIEW_REQUIRED")
  };
}
