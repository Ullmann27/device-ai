/** Nonbrowser verification of the actual original Canvas port. */
import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';
import requireDev from './require-dev.cjs';
import { fileURLToPath } from 'node:url';
import { BrowserContinuumPainter, createContinuumOriginalRenderer, ContinuumOrbitalRing,
  ContinuumHologramPlexus, ContinuumOriginalClock, HologramAudioEnvelope,
  GEOMETRY, MODE, MODE_ENERGY, mountContinuumOriginalOrb, ORIGINAL_COMMIT } from './orb-original-source.js';

const { createCanvas } = requireDev('continuum-native-canvas', '@napi-rs/canvas');
const here = path.dirname(fileURLToPath(import.meta.url));
const qa = path.join(here, 'qa-orb'); fs.mkdirSync(qa, { recursive: true });
const report = { sourceCommit: ORIGINAL_COMMIT, runtime: 'Node + @napi-rs/canvas; no browser', checks: [] };
const passed = (name, data = {}) => report.checks.push({ name, passed: true, ...data });

// Spatial closure and front/back splitting: every endpoint must continue at a
// matching vertex; neither half may disappear as a tilted ring turns.
const ring = new ContinuumOrbitalRing(1.42, .34, -11);
for (const phase of [.4, 1.2192, 2.0384, 3.6768, 99]) {
  ring.project(phase, 1, 206, 117, 101);
  assert(ring.frontSize > 0 && ring.backSize > 0);
  const points = [...ring.front.slice(0, ring.frontSize), ...ring.back.slice(0, ring.backSize)];
  assert(points.every(Number.isFinite)); assert(points.length / 4 >= 72 && points.length / 4 <= 74);
  for (let i = 0; i < points.length; i += 4) {
    let connected = false;
    for (let j = 0; j < points.length; j += 4) if (Math.abs(points[i + 2] - points[j]) < .001 && Math.abs(points[i + 3] - points[j + 1]) < .001) connected = true;
    assert(connected, `ring endpoint must be connected, phase ${phase}`);
  }
}
passed('continuous depth-split orbit geometry at five phases');

for (const [name, geometry] of Object.entries(GEOMETRY)) {
  const plexus = new ContinuumHologramPlexus(geometry.haloNodes), degrees = new Uint16Array(plexus.nodeCount);
  assert.equal(plexus.nodeCount, 176 + geometry.haloNodes); assert.equal(plexus.dustCount, 420);
  assert(plexus.edgeCount < plexus.nodeCount * 4);
  for (let i = 0; i < plexus.edgeCount; i++) { const a = plexus.edgeA[i], b = plexus.edgeB[i]; assert(a < b && b < plexus.nodeCount); degrees[a]++; degrees[b]++; }
  for (let i = 0; i < 176; i++) assert(degrees[i] > 0, `unlinked original shell node ${i}`);
  plexus.project(.064, .3, 206, 117, 101, .4); const old = plexus.sx.slice(), links = plexus.edgeA.slice();
  plexus.project(.916, .3, 206, 117, 101, 2);
  assert(plexus.sx.filter((x, i) => Math.abs(x - old[i]) > 4).length > plexus.nodeCount / 2);
  assert.deepEqual(plexus.edgeA, links); assert([...plexus.sx, ...plexus.sy, ...plexus.depth].every(Number.isFinite));
  passed(`fixed graph with spatial motion: ${name}`, { nodes: plexus.nodeCount, edges: plexus.edgeCount, dust: plexus.dustCount });
}

const clock = new ContinuumOriginalClock(); clock.advance(0);
for (let t = 16; t <= 4000; t += 16) clock.advance(t);
assert(Math.abs(clock.frame.phase - 2.0384) < .000001); assert.equal(clock.frame.audio, 0);
const phaseBefore = clock.frame.phase; clock.setState('working');
assert.equal(clock.frame.phase, phaseBefore); assert.equal(clock.frame.weights[MODE.THINKING], 0);
clock.advance(4016); assert(clock.frame.weights[MODE.THINKING] > 0 && clock.frame.weights[MODE.THINKING] < 1);
clock.setState('speaking'); clock.advance(4032); assert.equal(clock.frame.audio, 0);
clock.submitAudio(.36, 4032); clock.advance(4100); assert(clock.frame.audio > 0);
clock.advance(7000); assert.equal(clock.frame.audio, 0);
assert.equal(clock.setState('unknown-state'), false); assert.equal(clock.state, 'speaking');
const envelope = new HologramAudioEnvelope(); envelope.submit(.7, 0); assert(envelope.valueAt(80) > .5); assert.equal(envelope.valueAt(2500), 0);
clock.frame.phase = 999.99; clock.advance(7100); assert(clock.frame.phase > 1000, 'a long session must not restart all motion layers');
passed('original state crossfades, uninterrupted phase, measured-only speech and stale-audio release');

// Render all modes at actual anchor aspect ratios plus a large inspection size.
const samples = [];
for (const [label, width, height] of [['desktop', 225, 217], ['phone', 125, 150], ['inspection', 450, 434]]) {
  for (const [name, mode] of Object.entries(MODE)) {
    const bleed = Math.min(width, height) * .48, dpr = 2;
    const canvas = createCanvas(Math.ceil((width + bleed * 2) * dpr), Math.ceil((height + bleed * 2) * dpr));
    const ctx = canvas.getContext('2d'); ctx.setTransform(dpr, 0, 0, dpr, bleed * dpr, bleed * dpr);
    const painter = new BrowserContinuumPainter(ctx), renderer = createContinuumOriginalRenderer(); renderer.layout(width, height);
    const weights = new Float32Array(6); weights[mode] = 1;
    painter.begin(); renderer.draw(painter, { phase: .4, energy: MODE_ENERGY[mode], audio: 0, weights }, true);
    const pixels = ctx.getImageData(0, 0, canvas.width, canvas.height).data;
    let alphaSum = 0; for (let i = 3; i < pixels.length; i += 4) alphaSum += pixels[i];
    assert(alphaSum > canvas.width * canvas.height * 5, `${label}/${name} must contain a rendered orb`);
    assert.equal(pixels[3], 0, 'outer corner remains transparent');
    samples.push({ label, mode: name, canvas });
    if (name === 'READY') fs.writeFileSync(path.join(qa, `original-orb-${label}.png`), canvas.toBuffer('image/png'));
    if (label === 'inspection' && name === 'READY') fs.writeFileSync(path.join(here, '../continuum-preview/assets/continuum-orb-original.png'), canvas.toBuffer('image/png'));
  }
}
passed('18 native raster frames, six original modes, phone and desktop anchors, transparent bounds');
const sheet = createCanvas(1500, 950), sheetCtx = sheet.getContext('2d'); sheetCtx.fillStyle = '#040914'; sheetCtx.fillRect(0, 0, 1500, 950);
sheetCtx.font = '20px sans-serif'; sheetCtx.fillStyle = '#beddec';
samples.filter(x => x.label === 'desktop').forEach((sample, i) => {
  const x = i % 3 * 500, y = Math.floor(i / 3) * 475;
  sheetCtx.fillText(sample.mode, x + 30, y + 35);
  sheetCtx.drawImage(sample.canvas, x + 15, y + 40, 465, 455);
});
fs.writeFileSync(path.join(qa, 'original-orb-six-modes.png'), sheet.toBuffer('image/png'));

// Exercise mounting, resize, scroll, state events and original-only fallback
// with a lightweight DOM contract backed by the same native Canvas API.
function fakeHost(noContext = false) {
  let now = 0, scheduled = null;
  const host = new EventTarget(), doc = new EventTarget(), children = [];
  const rect = { left: 700, top: 120, width: 225, height: 217, bottom: 337 };
  const anchor = { dataset: {}, getBoundingClientRect: () => ({ ...rect }), appendChild: node => children.push(node) };
  const element = tag => {
    const node = tag === 'canvas' ? createCanvas(1, 1) : {};
    if (tag === 'canvas' && noContext) node.getContext = () => null;
    node.style = {}; node.dataset = {}; node.setAttribute = () => {}; node.remove = () => children.splice(children.indexOf(node), 1);
    return node;
  };
  doc.createElement = element; doc.querySelector = () => anchor; doc.hidden = false;
  doc.body = { appendChild: () => { throw new Error('The orb must stay inside its local anchor.'); } };
  Object.assign(host, { document: doc, performance: { now: () => now }, innerWidth: 1440, innerHeight: 980, devicePixelRatio: 2, CustomEvent,
    requestAnimationFrame: callback => { scheduled = callback; return 1; }, cancelAnimationFrame: () => { scheduled = null; },
    ContinuumScene: { getStatus: () => ({ state: 'idle', quality: 1, intensity: 1, audioLevel: null, audioSampleAt: 0 }),
      setOrbVisible: visible => assert.equal(visible, false, 'procedural replacement must never reappear') } });
  return { host, rect, children, step(time) { now = time; const next = scheduled; scheduled = null; next?.(time); } };
}
const h = fakeHost(), adapter = mountContinuumOriginalOrb({ window: h.host });
h.step(0); h.step(16); assert.equal(adapter.getStatus().ready, true); const mountedPhase = adapter.getStatus().phase;
h.rect.width = 125; h.rect.height = 150; h.rect.left = 210; h.rect.top = 240; h.rect.bottom = 390;
h.host.dispatchEvent(new CustomEvent('continuum:sceneresize')); h.step(32);
assert.equal(adapter.getStatus().width, 125); assert(adapter.getStatus().phase > mountedPhase);
h.host.dispatchEvent(new CustomEvent('continuum:scenechange', { detail: { state: 'working', quality: .6, intensity: .7, audioLevel: null, audioSampleAt: 0 } }));
h.step(48); assert.equal(adapter.getStatus().state, 'working'); assert.equal(adapter.getStatus().audio, 0);
adapter.dispose(); assert.equal(h.children.length, 0);
const noCanvas = fakeHost(true), fallback = mountContinuumOriginalOrb({ window: noCanvas.host }); noCanvas.step(0);
assert.equal(fallback.getStatus().renderer, 'original-png'); assert.equal(noCanvas.children.length, 2); fallback.dispose();
passed('DOM contract: own layer, anchor resize, state events, continuous clock, cleanup and original-only fallback');

fs.writeFileSync(path.join(qa, 'verification.json'), JSON.stringify(report, null, 2) + '\n');
console.log(JSON.stringify(report, null, 2));
