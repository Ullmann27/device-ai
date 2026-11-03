// TEST-ONLY WeakSet brand. Never a production runtime authenticator or inference proof.
const brands=new WeakSet();
export function mintObservation(fields,{frozen=true}={}){const value={...fields};if(frozen)Object.freeze(value);brands.add(value);return value;}
export const isTrustedLocalModelObservation=value=>!!value&&brands.has(value);
export function createCodingCandidateProvider(){throw Error('Synthetic brand fixture cannot create real inference');}
