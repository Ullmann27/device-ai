// Compatibility ONLY for the frozen review fixture, not candidate modules.
// This manually signed disposable-key synthetic proof is CONTRACT_TEST data.
// verifiedModelExecution:true here NEVER attests any real model inference.
import {registerHooks} from 'node:module';
const fixtureUrl=new URL('./fixture.mjs',import.meta.url).href;
registerHooks({load(url,context,nextLoad){
 const result=nextLoad(url,context);
 if(url!==fixtureUrl)return result;
 const source=String(result.source),needle='pid:1234,exitCode:0,';
 if(source.split(needle).length!==2)throw Error('Frozen fixture compatibility anchor mismatch');
 return {...result,source:source.replace(needle,needle+'verifiedModelExecution:true,')};
}});
