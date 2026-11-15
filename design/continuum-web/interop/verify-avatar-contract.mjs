/** Reproducible, renderer-independent original-asset and speech-safety checks. */
import assert from 'node:assert/strict';
import fs from 'node:fs/promises';
import crypto from 'node:crypto';
import vm from 'node:vm';
import { AVATAR_TARGETS, OriginalAvatarMotion } from './avatar-motion.js';

const asset = await fs.readFile(new URL('../continuum-preview/assets/continuum-avatar.glb', import.meta.url));
assert.equal(asset.byteLength, 12038416);
assert.equal(crypto.createHash('sha256').update(asset).digest('hex'),
  'c8b88385c73fa01596a5734d20859440b1ecaef06cb1abea6cd7a47e62b08b9a');
const gltf = JSON.parse(asset.subarray(20, 20 + asset.readUInt32LE(12)).toString('utf8'));
const nodeIndex = name => gltf.nodes.findIndex(node => node.name === name);
for (const name of ['AvatarRoot', 'Neck', 'Head', 'EyeLeft', 'EyeRight', 'Jaw']) assert.notEqual(nodeIndex(name), -1);
assert.ok(gltf.nodes[nodeIndex('Neck')].children.includes(nodeIndex('Head')));
assert.ok(gltf.nodes[nodeIndex('Head')].children.includes(nodeIndex('EyeLeft')));
assert.ok(gltf.nodes[nodeIndex('Head')].children.includes(nodeIndex('EyeRight')));
for (const mesh of gltf.meshes.filter(mesh => mesh.extras?.targetNames)) assert.deepEqual(mesh.extras.targetNames, AVATAR_TARGETS);
assert.equal(gltf.meshes.filter(mesh => mesh.extras?.targetNames).reduce((n, mesh) => n + mesh.primitives.length, 0), 3);

const quiet = new OriginalAvatarMotion();
quiet.setState('speaking');
let maxBlink = 0;
let minBreath = Infinity;
let maxBreath = -Infinity;
for (let now = 0; now < 12000; now += 1000 / 60) {
  const pose = quiet.update(now);
  assert.equal(pose.jawPitch, 0, 'A UI speaking state must not invent speech.');
  for (const name of AVATAR_TARGETS.slice(0, 15).concat('JawOpen')) assert.equal(pose.weights[name], 0);
  maxBlink = Math.max(maxBlink, pose.weights.BlinkLeft);
  minBreath = Math.min(minBreath, pose.breathOffset);
  maxBreath = Math.max(maxBreath, pose.breathOffset);
  assert.ok(Object.values(pose.weights).every(value => Number.isFinite(value) && value >= 0 && value <= 1));
}
assert.ok(maxBlink > .9, 'Original blink morphs must animate.');
assert.ok(maxBreath - minBreath > .001, 'Independent presence must remain active.');

const voice = new OriginalAvatarMotion();
voice.update(0);
assert.equal(voice.setSpeechFrame({ viseme: 'AA', weight: .8, jawOpen: .7, durationMs: 200 }, 0), true);
let pose;
for (let now = 16; now <= 112; now += 16) pose = voice.update(now);
assert.ok(pose.weights.AA > .79);
assert.ok(Math.abs(pose.weights.JawOpen - (.7 - .8125 * .8)) < .00001, 'Authored opening must be compensated once.');
voice.setSpeechFrame({ viseme: 'PP', weight: 1, jawOpen: 1, durationMs: 100 }, 120);
pose = voice.update(136);
assert.equal(pose.jawPitch, 0, 'Bilabial P/B/M closes the jaw despite a loud input.');
assert.equal(pose.weights.AA, 0, 'Outgoing vowel must retire during bilabial closure.');
voice.update(300);
pose = voice.update(316);
assert.equal(pose.jawPitch, 0, 'Stale speech frames must expire.');
assert.equal(voice.setSpeechFrame({ viseme: 'invented' }, 400), false);

const gaze = new OriginalAvatarMotion();
gaze.update(0);
gaze.setGaze(.7, -.3, 1000, 0);
pose = gaze.update(32);
assert.ok(pose.eyeYaw > pose.headYaw + pose.neckYaw, 'Eyes must lead the head after a directed gaze change.');
assert.ok(Math.abs(pose.neckYaw / (pose.neckYaw + pose.headYaw) - .65) < .000001);

// Execute the actual bundled adapter on the no-WebGL path. This is a DOM
// harness, not a browser/GPU rendering test.
const crop = { style: {}, dataset: {}, clientWidth: 272, clientHeight: 299,
  setAttribute() {}, appendChild() {} };
const dom = { readyState: 'complete', baseURI: 'https://preview.invalid/', currentScript: null,
  querySelector: () => crop, addEventListener() {}, removeEventListener() {},
  createElement: () => ({ style: {}, setAttribute() {}, getContext: () => null }) };
const browser = { document: dom, addEventListener() {}, removeEventListener() {}, dispatchEvent() {},
  CONTINUUM_ASSETS: { avatarModel: 'data:model/gltf-binary;base64,AA==', avatarFallback: 'data:image/png;base64,b3JpZ2luYWw=' } };
const context = vm.createContext({ window: browser, document: dom, URL, performance,
  console, CustomEvent: class { constructor(type, options) { this.type = type; Object.assign(this, options); } } });
vm.runInContext(await fs.readFile(new URL('../continuum-preview/avatar3d.js', import.meta.url), 'utf8'), context);
assert.equal(crop.dataset.avatarRenderer, 'original-png');
assert.match(crop.style.backgroundImage, /data:image\/png/);
assert.match(browser.ContinuumAvatar.getStatus().lastError, /WebGL2 unavailable/);
assert.equal(browser.ContinuumAvatar.getStatus().frames, 0);
console.log('PASS: exact GLB SHA-256, original rig and all morph primitives, neutral mouth without actual speech, opening compensation, bilabial closure, stale-cue expiry, eyes-before-head, 65% neck share, live presence, bundled no-WebGL fallback and data-URL override.');
