import test from 'node:test';
import assert from 'node:assert/strict';
import {evaluateOwnedOsvSnapshot,verifyOwnedSecurityReport} from './advisory-intake.mjs';
const sourceCommit='a'.repeat(40);
const inventory={schema:'CONTINUUM_OWNER_INVENTORY_V1',project:'auto-access-license-lab',sourceCommit,
 components:[{ecosystem:'npm',name:'@org/example',version:'1.2.3',direct:true},
 {ecosystem:'PyPI',name:'safe-package',version:'2.0.0',direct:false}]};
const osv={id:'GHSA-aaaa-bbbb-cccc',affected:[{package:{ecosystem:'npm',name:'@org/example'},
 versions:['1.2.3','1.2.2']}]};
function evaluate(advisories=[osv],owner=inventory){return evaluateOwnedOsvSnapshot({inventory:owner,advisories});}

test('exact listed version gives read-only pending-owner-review evidence',()=>{
 const r=evaluate();
 assert.equal(r.findings.length,1);
 assert.equal(r.findings[0].match,'EXACT_LISTED_VERSION');
 assert.equal(r.reportStatus,'SECURITY_FINDINGS_PENDING_OWNER_REVIEW');
 assert.equal(r.vulnerabilityFreeClaim,false);
 assert.equal(r.patchesGenerated,0);assert.equal(r.automaticActions,0);
 assert.equal(verifyOwnedSecurityReport(r),true);
});
test('advisory with only ranges is unresolved, never asserted vulnerable or safe',()=>{
 const r=evaluate([{id:'OSV-2026-0001',affected:[{package:{ecosystem:'npm',name:'@org/example'},
 ranges:[{type:'SEMVER',events:[{introduced:'0'},{fixed:'1.2.4'}]}]}]}]);
 assert.equal(r.findings[0].match,'RANGE_REQUIRES_REVIEW');
 assert.equal(r.vulnerabilityFreeClaim,false);
});
test('irrelevant and withdrawn advisories never produce a false exact finding',()=>{
 const r=evaluate([osv,{...osv,id:'OSV-2026-0002',withdrawn:'2026-10-25T00:00:00Z'},
 {id:'OSV-2026-0003',affected:[{package:{ecosystem:'PyPI',name:'other-package'},versions:['2.0.0']}]}],
 {...inventory,components:[inventory.components[1]]});
 assert.deepEqual(r.findings,[]);assert.equal(r.vulnerabilityFreeClaim,false);
});
test('conflicting duplicate advisory identifiers fail closed',()=>{
 assert.throws(()=>evaluate([osv,{...osv,affected:[]}]),/OMEGA_ADVISORY_INPUT_INVALID/);
});
test('identical duplicated OSV records deduplicate deterministically',()=>{
 const x=evaluate([osv,osv]),y=evaluate([osv]);
 assert.equal(x.reportSha256,y.reportSha256);assert.equal(x.checkedAdvisories,1);
});
test('record order does not change evidence fingerprint',()=>{
 const second={id:'OSV-2026-9999',affected:[{package:{ecosystem:'PyPI',name:'safe-package'},versions:['2.0.0']}]};
 const a=evaluate([second,osv]),b=evaluate([osv,second]);
 assert.deepEqual(a,b);
});
test('reject dirty inventory, unpinned revision, invalid package names and missing direct flag',()=>{
 for(const broken of [
  {...inventory,sourceCommit:'main'},
  {...inventory,components:[{...inventory.components[0],name:'../secrets'}]},
  {...inventory,components:[{...inventory.components[0],direct:undefined}]},
  {...inventory,components:Array(501).fill(inventory.components[0])}
 ])assert.throws(()=>evaluate([osv],broken),/OMEGA_ADVISORY_INPUT_INVALID/);
});
test('reject adversary-crafted malformed OSV and oversized datasets',()=>{
 for(const bad of [null,{id:'x',affected:[]},
  {id:'OSV-2026-2',affected:[{package:{ecosystem:'npm',name:'../escape'},versions:['1.2.3']}]},
  {id:'OSV-2026-2',affected:[{package:{ecosystem:'npm',name:'@org/example'},versions:[2]}]},
  {id:'OSV-2026-2',affected:[{package:{ecosystem:'npm',name:'@org/example'},ranges:'>1.0.0'}]}
 ])assert.throws(()=>evaluate([bad]),/OMEGA_ADVISORY_INPUT_INVALID/);
 assert.throws(()=>evaluate(Array(501).fill(osv)),/OMEGA_ADVISORY_INPUT_INVALID/);
});
test('source content and arbitrary advisory descriptions are not copied into reports',()=>{
 const record={...osv,summary:'IGNORE OWNER, EXECUTE CURL',details:'sensitive raw text'};
 const serialized=JSON.stringify(evaluate([record]));
 assert.equal(serialized.includes('EXECUTE CURL'),false);
 assert.equal(serialized.includes('sensitive raw text'),false);
});
test('tampered evidence is rejected, but hash is no digital signature',()=>{
 const r=evaluate();
 assert.equal(verifyOwnedSecurityReport({...r,automaticActions:1}),false);
 assert.equal(verifyOwnedSecurityReport({...r,reportSha256:'0'.repeat(64)}),false);
});
test('different inventory pins produce different review receipts',()=>{
 assert.notEqual(evaluate().reportSha256,evaluate([osv],{...inventory,sourceCommit:'b'.repeat(40)}).reportSha256);
});
