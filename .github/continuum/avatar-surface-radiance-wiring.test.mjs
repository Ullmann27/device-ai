import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';
import {fileURLToPath} from 'node:url';
const root=path.resolve(path.dirname(fileURLToPath(import.meta.url)),'../..');
const glb=fs.readFileSync(path.join(root,'app/src/main/java/at/heinz/deviceai/avatar3d/AvatarGlbContract.kt'),'utf8');
const renderer=fs.readFileSync(path.join(root,'app/src/main/java/at/heinz/deviceai/avatar3d/FilamentAvatarRenderer.kt'),'utf8');
const gain=fs.readFileSync(path.join(root,'app/src/main/java/at/heinz/deviceai/avatar3d/AvatarSurfaceRadiance.kt'),'utf8');
const light=fs.readFileSync(path.join(root,'app/src/main/java/at/heinz/deviceai/avatar3d/CinemaWindowLightState.kt'),'utf8');
const assertAccentFallback=(rendererSource,lightSource)=>{
 assert.match(rendererSource,/val flyIn = CinemaWindowLightState\.snapshot\s*val accent = flyIn\.accentOr\(windowAccentArgb\)/);
 assert.match(lightSource,/fun accentOr\(fallback: Int\?\): Int\? = if \(open\) accentArgb \?: fallback else fallback/);
};
test('Face glow reuses original glTF emission',()=>{
 assert.match(glb,/SurfaceEmission/);
 assert.match(glb,/DeepIndigoSkin/);
 assert.match(glb,/Authored face emission missing/);
 assert.match(renderer,/metadata\.surfaceEmission/);
 assert.match(renderer,/materialInstances\.firstOrNull/);
 assert.match(renderer,/hasParameter\("emissiveFactor"\)/);
 assert.match(renderer,/applySurfaceRadiance\(coreEnergy, cinematicLighting && motion\)/);
 assert.match(gain,/MAX_GAIN = 1\.32f/);
});
test('Face glow remains a safe optional layer',()=>{
 assert.match(renderer,/applyCoreLight\(coreEnergy, cinematicLighting && motion, accent, open, side, elevation\)/);
 assertAccentFallback(renderer,light);
 assert.match(renderer,/val open = windowOpen \|\| flyIn\.open/);
 assert.match(renderer,/windowAccentArgb: Int\?, windowOpen: Boolean, gazeX: Float/);
 assert.match(renderer,/SpatialWindowLight\.tint\(windowAccentArgb, energy,/);
 assert.match(renderer,/light\.setColor\(coreLightInstance, target\.red, target\.green, target\.blue\)/);
 assert.match(renderer,/catch \(_: RuntimeException\) \{\s*faceMaterial = null/);
 assert.match(renderer,/faceMaterial = null\s*faceEmission = null/);
 assert.doesNotMatch(renderer,/materialInstances\.single/);
});
test('window accent contract rejects lost fallback or closed-window tint',()=>{
 assertAccentFallback(renderer,light);
 const mutations=[
  ['renderer','accentOr(windowAccentArgb)','accentOr(null)'],
  ['light','accentArgb ?: fallback','accentArgb'],
  ['light','else fallback','else accentArgb'],
  ['light','if (open)','if (true)']
 ];
 for(const [name,before,after] of mutations){
  const inputs={renderer,light};
  assert.ok(inputs[name].includes(before),before);
  inputs[name]=inputs[name].replace(before,after);
  assert.throws(()=>assertAccentFallback(inputs.renderer,inputs.light),assert.AssertionError);
 }
});
