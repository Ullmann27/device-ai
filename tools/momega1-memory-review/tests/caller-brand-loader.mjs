// Tests only: intercept exactly coding-provider.mjs at explicitly extracted candidate root.
import {registerHooks} from 'node:module';
import {pathToFileURL} from 'node:url';
import path from 'node:path';
const candidate=pathToFileURL(path.join(process.env.MOMEGA1_CANDIDATE_ROOT,'.github/continuum/coding-provider.mjs')).href;
const fixture=new URL('./caller-fixture-runtime.mjs',import.meta.url).href;
registerHooks({load(url,context,next){if(url!==candidate)return next(url,context);return {format:'module',shortCircuit:true,source:`export * from ${JSON.stringify(fixture)};`};}});
