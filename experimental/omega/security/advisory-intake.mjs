/**
 * OMEGA/JARVIS-MO – read-only defensive vulnerability-intelligence experiment.
 * Only compares an explicitly supplied owner SBOM with OSV-formatted records.
 * No network access, exploit attempts, code execution, patches or promotions.
 * Advisory content is untrusted; the report contains identifiers only.
 */
import {createHash} from 'node:crypto';

const SHA40=/^[a-f0-9]{40}$/;
const NAME=/^[A-Za-z0-9@_.+:/-]{1,200}$/;
const VERSION=/^[A-Za-z0-9][A-Za-z0-9_.+!~:-]{0,79}$/;
const ADVISORY_ID=/^[A-Za-z][A-Za-z0-9._-]{2,90}$/;
const ECOSYSTEMS=new Set(['npm','PyPI','Maven','Go','crates.io','NuGet','RubyGems','Packagist']);
const MAX_COMPONENTS=500,MAX_ADVISORIES=500,MAX_AFFECTED=200,MAX_VERSIONS=2000;
const MAX_ADVISORY_BYTES=512_000;
const digest=value=>createHash('sha256').update(JSON.stringify(value)).digest('hex');
const isRecord=x=>x!==null&&typeof x==='object'&&!Array.isArray(x);
const invalid=()=>{throw Error('OMEGA_ADVISORY_INPUT_INVALID');};
const textOk=(s,re)=>typeof s==='string'&&re.test(s);
const componentKey=c=>`${c.ecosystem}\u0000${c.name}\u0000${c.version}`;

function validateInventory(inventory){
 if(!isRecord(inventory)||inventory.schema!=='CONTINUUM_OWNER_INVENTORY_V1'||
    !textOk(inventory.sourceCommit,SHA40)||!textOk(inventory.project,/^[A-Za-z0-9_-]{1,80}$/)||
    !Array.isArray(inventory.components)||inventory.components.length>MAX_COMPONENTS)invalid();
 const seen=new Set();
 const components=inventory.components.map(c=>{
   if(!isRecord(c)||!ECOSYSTEMS.has(c.ecosystem)||!textOk(c.name,NAME)||
      !textOk(c.version,VERSION)||c.name.split('/').some(s=>s==='..'||s==='.')||
      typeof c.direct!=='boolean')invalid();
   const clean={ecosystem:c.ecosystem,name:c.name,version:c.version,direct:c.direct};
   const key=componentKey(clean);
   if(seen.has(key))invalid();
   seen.add(key);
   return clean;
 }).sort((a,b)=>componentKey(a).localeCompare(componentKey(b),'en'));
 return {project:inventory.project,sourceCommit:inventory.sourceCommit,components};
}

function normalizeAdvisory(raw){
 let serialized;
 try{serialized=JSON.stringify(raw);}catch{invalid();}
 if(typeof serialized!=='string'||Buffer.byteLength(serialized,'utf8')>MAX_ADVISORY_BYTES)invalid();
 if(!isRecord(raw)||!textOk(raw.id,ADVISORY_ID)||!Array.isArray(raw.affected)||
    raw.affected.length>MAX_AFFECTED||
    (raw.withdrawn!==undefined&&(typeof raw.withdrawn!=='string'||!Number.isFinite(Date.parse(raw.withdrawn)))))invalid();
 const entries=raw.affected.map(a=>{
   if(!isRecord(a)||!isRecord(a.package)||!ECOSYSTEMS.has(a.package.ecosystem)||
     !textOk(a.package.name,NAME)||a.package.name.split('/').some(s=>s==='..'||s==='.')||
     (a.versions!==undefined&&(!Array.isArray(a.versions)||a.versions.length>MAX_VERSIONS))||
     (a.ranges!==undefined&&(!Array.isArray(a.ranges)||a.ranges.length>64||
       a.ranges.some(r=>!isRecord(r)||!['GIT','SEMVER','ECOSYSTEM'].includes(r.type)||
         !Array.isArray(r.events)||r.events.length===0||r.events.length>128||
         r.events.some(e=>!isRecord(e)||Object.keys(e).length!==1||
           !['introduced','fixed','last_affected','limit'].includes(Object.keys(e)[0])||
           typeof Object.values(e)[0]!=='string'||Object.values(e)[0].length>160)))))invalid();
   const versions=a.versions??[];
   if(versions.some(v=>!textOk(v,VERSION)))invalid();
   // Ranges require ecosystem-aware version logic. Never guess from strings.
   const hasRanges=(a.ranges?.length??0)>0;
   const rangeSignature=hasRanges?digest(a.ranges):null;
   return {ecosystem:a.package.ecosystem,name:a.package.name,
     versions:[...new Set(versions)].sort(),hasRanges,rangeSignature};
 });
 return {id:raw.id,withdrawn:raw.withdrawn!==undefined,
   affected:entries.sort((a,b)=>(a.ecosystem+'\u0000'+a.name).localeCompare(b.ecosystem+'\u0000'+b.name,'en'))};
}

/** No "safe" status: an empty report is not proof of vulnerability absence. */
export function evaluateOwnedOsvSnapshot({inventory,advisories}={}){
 const owner=validateInventory(inventory);
 if(!Array.isArray(advisories)||advisories.length>MAX_ADVISORIES)invalid();
 const ids=new Map();
 for(const raw of advisories){
   const normalized=normalizeAdvisory(raw);
   const checksum=digest(normalized);
   if(ids.has(normalized.id)&&ids.get(normalized.id).checksum!==checksum)invalid();
   ids.set(normalized.id,{record:normalized,checksum});
 }
 const findings=[];
 for(const [id,{record}] of [...ids.entries()].sort(([a],[b])=>a.localeCompare(b,'en'))){
   if(record.withdrawn)continue;
   for(const component of owner.components){
     const relevant=record.affected.filter(a=>a.ecosystem===component.ecosystem&&a.name===component.name);
     if(!relevant.length)continue;
     const exact=relevant.some(a=>a.versions.includes(component.version));
     const rangesUnresolved=relevant.some(a=>a.hasRanges);
     if(!exact&&!rangesUnresolved)continue;
     findings.push({advisoryId:id,ecosystem:component.ecosystem,packageName:component.name,
       installedVersion:component.version,match:exact?'EXACT_LISTED_VERSION':'RANGE_REQUIRES_REVIEW',
       action:'OWNER_SECURITY_REVIEW_REQUIRED'});
   }
 }
 const record={schema:'CONTINUUM_SECURITY_REVIEW_V1',project:owner.project,
   sourceCommit:owner.sourceCommit,inventorySha256:digest(owner),
   advisorySetSha256:digest([...ids.entries()].sort(([a],[b])=>a.localeCompare(b,'en')).map(([id,r])=>[id,r.checksum])),
   checkedComponents:owner.components.length,checkedAdvisories:ids.size,
   findings,reportStatus:'SECURITY_FINDINGS_PENDING_OWNER_REVIEW',
   coverage:'SUPPLIED_ADVISORIES_AND_EXACT_VERSION_LISTS_ONLY',
   vulnerabilityFreeClaim:false,patchesGenerated:0,automaticActions:0};
 return Object.freeze({...record,reportSha256:digest(record)});
}

/** Independent integrity check of the immutable report's public fields. */
export function verifyOwnedSecurityReport(report){
 if(!isRecord(report)||!textOk(report.reportSha256,/^[a-f0-9]{64}$/))return false;
 const {reportSha256,...body}=report;
 return reportSha256===digest(body)&&body.schema==='CONTINUUM_SECURITY_REVIEW_V1'&&
   body.reportStatus==='SECURITY_FINDINGS_PENDING_OWNER_REVIEW'&&
   body.vulnerabilityFreeClaim===false&&body.automaticActions===0&&body.patchesGenerated===0;
}
