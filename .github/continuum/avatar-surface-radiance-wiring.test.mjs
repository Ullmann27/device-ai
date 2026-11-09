import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';
import {fileURLToPath} from 'node:url';
const root=path.resolve(path.dirname(fileURLToPath(import.meta.url)),'../..');
const glb=fs.readFileSync(path.join(root,'app/src/main/java/at/heinz/deviceai/avatar3d/AvatarGlbContract.kt'),'utf8');
const renderer=fs.readFileSync(path.join(root,'app/src/main/java/at/heinz/deviceai/avatar3d/FilamentAvatarRenderer.kt'),'utf8');
const gain=fs.readFileSync(path.join(root,'app/src/main/java/at/heinz/deviceai/avatar3d/AvatarSurfaceRadiance.kt'),'utf8');
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
 assert.match(renderer,/applyCoreLight\(coreEnergy, cinematicLighting && motion\)/);
 assert.match(renderer,/catch \(_: RuntimeException\) \{\s*faceMaterial = null/);
 assert.match(renderer,/faceMaterial = null\s*faceEmission = null/);
 assert.doesNotMatch(renderer,/materialInstances\.single/);
});
