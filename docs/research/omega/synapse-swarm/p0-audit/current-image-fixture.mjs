// Test-configuration-only substitution for this machine's locally built scratch image.
// No production module or test expectation is transformed. Old digest is not spoofed.
import {registerHooks} from 'node:module';
import path from 'node:path';
import {pathToFileURL} from 'node:url';
const root=process.env.SYNAPSE_P0_TARGET;
const current=process.env.SYNAPSE_P0_IMAGE;
if(!root||!/^sha256:[a-f0-9]{64}$/.test(current??''))throw Error('Explicit target and actual image digest required');
const allowed=new Set([
 '.github/continuum/momega1-typed-workspace-host.test.mjs',
 '.github/continuum/ccc1-workspace-lifecycle-host.test.mjs',
 '.github/continuum/ccc1-workspace-host.test.mjs',
 'tools/momega1-workspace-review/claim-adversarial.test.mjs',
].map(relative=>pathToFileURL(path.join(root,relative)).href));
const old='sha256:3f44842e8a052beda7e358b9fb8d8acc3e180a7f58aaceff7e1b49ad7f3d46e1';
registerHooks({load(url,context,nextLoad){
 const result=nextLoad(url,context);
 if(!allowed.has(url))return result;
 const source=String(result.source);
 if(source.split(old).length!==2)throw Error('Expected exactly one original fixture pin: '+url);
 return {...result,source:source.replace(old,current)};
}});
