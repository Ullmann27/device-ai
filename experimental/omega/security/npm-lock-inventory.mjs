/**
 * Offline, read-only extraction of an npm package-lock v2/v3 owner inventory.
 * Versions without a verified comparable release cannot be silently treated as safe.
 */
import {createHash} from 'node:crypto';
const SOURCE=/^[a-f0-9]{40}$/;
const PROJECT=/^[A-Za-z0-9_-]{1,80}$/;
const NAME=/^[A-Za-z0-9@_.+:/-]{1,200}$/;
const VERSION=/^[A-Za-z0-9][A-Za-z0-9_.+!~:-]{0,79}$/;
const invalid=()=>{throw Error('OMEGA_NPM_LOCK_INVALID');};
const record=x=>x!==null&&typeof x==='object'&&!Array.isArray(x);
const key=c=>c.ecosystem+'\0'+c.name+'\0'+c.version;

export function inventoryFromOwnedNpmLock(lock,{project,sourceCommit}={}){
 if(!record(lock)||![2,3].includes(lock.lockfileVersion)||!record(lock.packages)||
    !record(lock.packages[''])||!PROJECT.test(project??'')||!SOURCE.test(sourceCommit??''))invalid();
 const entries=Object.entries(lock.packages);
 if(entries.length>25000)invalid();
 const root=lock.packages[''];
 const rootDependencies=[root.dependencies,root.devDependencies,root.optionalDependencies];
 if(rootDependencies.some(d=>d!==undefined&&!record(d)))invalid();
 const direct=new Set(rootDependencies.flatMap(d=>Object.keys(d??{})));
 const components=new Map();let unresolved=0;
 for(const [location,pkg] of entries){
   if(location==='')continue;
   if(!record(pkg)||typeof location!=='string'||location.length>800)invalid();
   if(!location.startsWith('node_modules/')&&!location.includes('/node_modules/')){
     unresolved++;continue;
   }
   const name=location.slice(location.lastIndexOf('node_modules/')+'node_modules/'.length);
   if(!NAME.test(name)||name.split('/').some(s=>s==='..'||s==='.')||name.split('/').filter(Boolean).length>2)invalid();
   if(pkg.link===true||typeof pkg.version!=='string'||!VERSION.test(pkg.version)){
     unresolved++;continue;
   }
   const component={ecosystem:'npm',name,version:pkg.version,direct:direct.has(name)};
   const id=key(component);
   if(components.has(id))components.get(id).direct ||= component.direct;
   else components.set(id,component);
 }
 const inventory={schema:'CONTINUUM_OWNER_INVENTORY_V1',project,sourceCommit,
   components:[...components.values()].sort((a,b)=>key(a).localeCompare(key(b),'en'))};
 return {inventory,lockfileSha256:createHash('sha256').update(JSON.stringify(lock)).digest('hex'),
   unresolvedEntries:unresolved,coverage:unresolved===0?'NPM_LOCK_PACKAGES_ENUMERATED':'INCOMPLETE_INVENTORY_REVIEW_REQUIRED'};
}
