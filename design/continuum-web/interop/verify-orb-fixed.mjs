/** Focused native checks for the requested fixed orb and layered atmosphere. */
import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';
import vm from 'node:vm';
import { fileURLToPath } from 'node:url';
import requireDev from './require-dev.cjs';
import { mountContinuumOriginalOrb } from './orb-original-source.js';

const { createCanvas } = requireDev('continuum-native-canvas', '@napi-rs/canvas');
const here = path.dirname(fileURLToPath(import.meta.url));
const qa = path.join(here, 'qa-orb-fixed'); fs.mkdirSync(qa, { recursive: true });
const results = [];

function hostFixture({ width = 1440, height = 980, noCanvas = false } = {}) {
  let now = 0, scheduled = null;
  const host = new EventTarget(), doc = new EventTarget(), children = [], bodyChildren = [];
  const rect = { left: 500, top: 140, width: 420, height: 390, bottom: 530 };
  const anchor = { dataset: { orbMotion: 'fixed' }, getBoundingClientRect: () => ({ ...rect }),
    get clientWidth() { return rect.width; }, get clientHeight() { return rect.height; },
    appendChild(node) { children.push(node); node.parentElement = anchor; } };
  function element(tag) {
    const node = tag === 'canvas' ? createCanvas(1, 1) : {};
    if (tag === 'canvas' && noCanvas) node.getContext = () => null;
    node.style = {}; node.dataset = {}; node.setAttribute = () => {};
    node.remove = () => { const i = children.indexOf(node); if (i >= 0) children.splice(i, 1); };
    return node;
  }
  doc.createElement = element; doc.querySelector = () => anchor; doc.hidden = false;
  doc.body = { appendChild: node => bodyChildren.push(node) };
  Object.assign(host, { document: doc, performance: { now: () => now }, innerWidth: width, innerHeight: height, devicePixelRatio: 3, CustomEvent,
    requestAnimationFrame: callback => { scheduled = callback; return 1; }, cancelAnimationFrame: () => { scheduled = null; },
    ContinuumScene: { getStatus: () => ({ state: 'idle', quality: 1, intensity: 1, audioLevel: null, audioSampleAt: 0 }),
      setOrbVisible: value => assert.equal(value, false) } });
  return { host, doc, rect, anchor, children, bodyChildren, step(time) { now = time; const callback = scheduled; scheduled = null; callback?.(time); } };
}

const fixture = hostFixture(), orb = mountContinuumOriginalOrb({ window: fixture.host });
fixture.step(0);
const canvas = fixture.children[0], context = canvas.getContext('2d');
const originalPixels = Buffer.from(context.getImageData(0, 0, canvas.width, canvas.height).data);
const localPosition = { left: canvas.style.left, top: canvas.style.top };
assert.equal(canvas.parentElement, fixture.anchor); assert.equal(canvas.style.position, 'absolute'); assert.equal(canvas.style.zIndex, '0');
assert.equal(fixture.bodyChildren.length, 0); assert.equal(orb.getStatus().motion, 'fixed'); assert.equal(orb.getStatus().phase, .4);
for (let i = 1; i <= 180; i++) fixture.step(i * 1000 / 60);
assert.equal(orb.getStatus().frames, 1, 'idle fixed mode must reuse its original raster');
fixture.host.dispatchEvent(new CustomEvent('continuum:scenechange', { detail: { state: 'working', intensity: 1, quality: 1, audioLevel: null, audioSampleAt: 0 } }));
for (let i = 181; i <= 300; i++) fixture.step(i * 1000 / 60);
assert(orb.getStatus().exposure > 1.05, 'real state may change light softly');
assert.equal(orb.getStatus().frames, 1, 'a light response must not redraw or deform the orb');
fixture.host.dispatchEvent(new CustomEvent('continuum:scenechange', { detail: { state: 'speaking', intensity: .9, quality: 1, audioLevel: .4, audioSampleAt: 5000 } }));
for (let i = 301; i <= 360; i++) fixture.step(i * 1000 / 60);
assert.deepEqual(Buffer.from(context.getImageData(0, 0, canvas.width, canvas.height).data), originalPixels);
assert.equal(orb.getStatus().phase, .4); assert(orb.getStatus().clockPhase > .4);
assert.deepEqual({ left: canvas.style.left, top: canvas.style.top }, localPosition);
fixture.rect.left -= 90; fixture.rect.top -= 50; fixture.rect.bottom -= 50;
fixture.host.dispatchEvent(new Event('scroll')); fixture.step(6100);
assert.deepEqual({ left: canvas.style.left, top: canvas.style.top }, localPosition, 'scroll must be inherited locally, without a body overlay');
assert.equal(orb.getStatus().frames, 1);
fixture.host.innerWidth = 430; fixture.rect.width = 300; fixture.rect.height = 280;
fixture.host.dispatchEvent(new CustomEvent('continuum:sceneresize')); fixture.step(6200);
assert.equal(orb.getStatus().frames, 2); assert.equal(orb.getStatus().phase, .4); assert(orb.getStatus().pixelRatio <= 1.5);
assert.equal(orb.setMotionMode('invalid'), false); assert.equal(orb.setMotionMode('anchored'), true);
assert.equal(orb.setMotionMode('live'), true); fixture.step(6300); assert.equal(orb.getStatus().frames, 3);
assert.equal(orb.setMotionMode('fixed'), true); fixture.step(6400); assert.equal(orb.getStatus().phase, .4);
orb.dispose(); assert.equal(fixture.children.length, 0);
results.push({ check: 'fixed geometry pixels, ring orientation, position, cache, light-only state response, local layering, resize and phone DPR', passed: true });

const failure = hostFixture({ noCanvas: true }), fallback = mountContinuumOriginalOrb({ window: failure.host }); failure.step(0);
assert.equal(fallback.getStatus().renderer, 'original-png'); assert.equal(failure.bodyChildren.length, 0);
assert(failure.children.every(node => node.parentElement === failure.anchor)); fallback.dispose();
results.push({ check: 'original-only fallback stays inside the orb anchor', passed: true });

const atmosphereSource = fs.readFileSync(path.join(here, '../continuum-preview/scene.js'), 'utf8');
for (const [label, width, height] of [['phone', 430, 932], ['desktop', 1440, 980]]) {
  let now = 0, scheduled = null;
  const host = new EventTarget(), doc = new EventTarget(), room = createCanvas(width, height);
  const orbAnchor = { dataset: { orbMotion: 'fixed' }, getBoundingClientRect: () => ({ left: width * .3, top: 160, width: width * .4, height: 320 }) };
  const workAnchor = { getBoundingClientRect: () => ({ left: width * .2, top: height * .65, width: width * .6, height: 250 }) };
  room.getBoundingClientRect = () => ({ width, height });
  doc.getElementById = () => room; doc.querySelector = selector => selector.includes('orb') ? orbAnchor : workAnchor;
  doc.createElement = () => createCanvas(1, 1); doc.readyState = 'complete';
  Object.assign(host, { document: doc, innerWidth: width, innerHeight: height, devicePixelRatio: 3,
    matchMedia: () => ({ matches: false }), performance: { now: () => now } });
  const raf = callback => { scheduled = callback; return 1; };
  const cancel = () => { scheduled = null; };
  vm.runInNewContext(atmosphereSource, { window: host, document: doc, performance: host.performance, requestAnimationFrame: raf, cancelAnimationFrame: cancel, CustomEvent });
  function step(time) { now = time; const callback = scheduled; scheduled = null; callback?.(time); }
  step(16);
  const roomContext = room.getContext('2d'), before = roomContext.getImageData(0, 0, room.width, room.height).data;
  fs.writeFileSync(path.join(qa, `atmosphere-${label}-start.png`), room.toBuffer('image/png'));
  step(10000);
  const after = roomContext.getImageData(0, 0, room.width, room.height).data;
  let changed = 0;
  for (let i = 0; i < before.length; i += 4) if (before[i] !== after[i] || before[i + 1] !== after[i + 1] || before[i + 2] !== after[i + 2]) changed++;
  assert(changed > room.width * room.height * .005, 'visible atmosphere must continue to change independently of fixed orb');
  const status = host.ContinuumScene.getStatus();
  assert.equal(status.atmosphereLayers, 3); assert.equal(status.dustLayers, 3); assert.equal(status.rayCount, label === 'phone' ? 3 : 5);
  assert(status.pixelRatio <= (label === 'phone' ? 1.4 : 1.65));
  host.ContinuumScene.setOrbVisible(true); assert.equal(host.ContinuumScene.getStatus().orbVisible, false);
  host.ContinuumScene.setState('working'); assert.equal(host.ContinuumScene.getStatus().elapsedSeconds, 10);
  step(11000); assert.equal(host.ContinuumScene.getStatus().elapsedSeconds, 11);
  fs.writeFileSync(path.join(qa, `atmosphere-${label}-later.png`), room.toBuffer('image/png'));
  host.ContinuumScene.destroy();
  results.push({ check: `native atmosphere ${label}`, passed: true, cssWidth: width, cssHeight: height,
    pixelRatio: status.pixelRatio, layers: 3, rayCount: status.rayCount, changedPixelsAfter10Seconds: changed,
    changedPixelRatio: Number((changed / (room.width * room.height)).toFixed(4)), retiredOrbCannotReturn: true });
}

const report = { runtime: 'Node + native Canvas; no browser or server', results };
fs.writeFileSync(path.join(qa, 'verification.json'), JSON.stringify(report, null, 2) + '\n');
console.log(JSON.stringify(report, null, 2));
