/** Native CPU/Canvas diagnostic. These measurements are not browser/GPU/Fold fps. */
import fs from 'node:fs/promises';
import vm from 'node:vm';
import assert from 'node:assert/strict';
import crypto from 'node:crypto';
import { performance as clock } from 'node:perf_hooks';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import requireDev from './require-dev.cjs';
const { createCanvas } = requireDev('continuum-native-canvas', '@napi-rs/canvas');
const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const sourcePath = process.env.CONTINUUM_SCENE_SOURCE ? path.resolve(process.env.CONTINUUM_SCENE_SOURCE) : path.join(root, 'continuum-preview/scene.js');
const source = await fs.readFile(sourcePath, 'utf8');
const report = { scope: 'Native Canvas CPU only; no browser layout engine, GPU timing or Fold hardware measurement',
  sourcePath, sourceSha256: crypto.createHash('sha256').update(source).digest('hex'),
  frameCompletion: 'Read one pixel after each frame to finish native raster work', profiles: [] };
for (const [width, height, dpr] of [[1280, 720, 2], [390, 844, 3]]) {
  if (process.env.CONTINUUM_BUDGET_TRACE) console.error('profile', width, height);
  const canvas = createCanvas(width, height);
  let layoutReads = 0, resizeEvents = 0, changeEvents = 0, current = 0, timer = clock.now(), id = 0;
  const queue = new Map();
  const counters = Object.create(null);
  const counts = new Set(['drawImage', 'fillRect', 'stroke', 'fill', 'createLinearGradient', 'createRadialGradient']);
  function instrument(surface) {
    const context = surface.getContext('2d');
    const wrapped = new Proxy(context, { get(target, key) {
      const value = target[key];
      if (typeof value !== 'function') return value;
      return (...args) => { if (counts.has(key)) counters[key] = (counters[key] || 0) + 1; return value.apply(target, args); };
    }, set(target, key, value) { target[key] = value; return true; } });
    surface.getContext = () => wrapped;
    return surface;
  }
  instrument(canvas);
  canvas.getBoundingClientRect = () => { layoutReads++; return { width, height, left: 0, top: 0 }; };
  const orb = { dataset: { orbMotion: 'fixed' }, getBoundingClientRect() { layoutReads++; return { left: width * .3, top: 90, width: width * .4, height: 310 }; } };
  const work = { getBoundingClientRect() { layoutReads++; return { left: width * .1, top: 410, width: width * .8, height: 180 }; } };
  const host = { innerWidth: width, innerHeight: height, devicePixelRatio: dpr,
    addEventListener() {}, removeEventListener() {}, dispatchEvent(event) { if (event.type === 'continuum:sceneresize') resizeEvents++; if (event.type === 'continuum:scenechange') changeEvents++; },
    matchMedia: () => ({ matches: false, addEventListener() {} }) };
  const doc = { readyState: 'complete', hidden: false,
    getElementById: () => canvas,
    querySelector: selector => selector === '[data-orb-anchor]' ? orb : selector === '[data-work-anchor]' ? work : null,
    createElement: () => instrument(createCanvas(1, 1)), addEventListener() {}, removeEventListener() {} };
  const native = vm.createContext({ window: host, document: doc,
    performance: { now: () => current + clock.now() - timer },
    requestAnimationFrame(fn) { queue.set(++id, fn); return id; }, cancelAnimationFrame(key) { queue.delete(key); },
    CustomEvent: class { constructor(type, options) { this.type = type; Object.assign(this, options); } }, console });
  vm.runInContext(source, native);
  if (process.env.CONTINUUM_BUDGET_TRACE) console.error('scene initialized');
  const api = host.ContinuumScene;
  api.setQuality('high'); api.setState('working');
  const times = [], cpuTimes = [];
  let stableLayoutReads = null;
  for (let index = 0; index < Number(process.env.CONTINUUM_BUDGET_FRAMES || 84); index++) {
    current = index * 1000 / 60; timer = clock.now();
    const cpu = process.cpuUsage();
    const pending = [...queue.values()]; queue.clear();
    for (const fn of pending) fn(current);
    canvas.getContext('2d').getImageData(0, 0, 1, 1);
    const used = process.cpuUsage(cpu);
    if (process.env.CONTINUUM_BUDGET_TRACE && index % 12 === 0) console.error('frame', index, 'ms', clock.now() - timer);
    if (index === 11) { stableLayoutReads = layoutReads; for (const key of Object.keys(counters)) counters[key] = 0; }
    if (index >= 12) { times.push(clock.now() - timer); cpuTimes.push((used.user + used.system) / 1000); }
  }
  const measuredReads = layoutReads - stableLayoutReads;
  const meanCalls = Object.fromEntries(Object.entries(counters).map(([key, value]) => [key, value / times.length]));
  const beforeRepeatedResize = resizeEvents;
  const beforeRepeatedState = changeEvents;
  for (let i = 0; i < 10; i++) { api.resize(); api.setState('working'); }
  current += 1000 / 60; timer = clock.now();
  const pending = [...queue.values()]; queue.clear(); pending.forEach(fn => fn(current));
  const sorted = [...times].sort((a, b) => a - b);
  report.profiles.push({ width, height, devicePixelRatio: dpr, measuredFrames: times.length,
    meanCpuMs: times.reduce((a, b) => a + b) / times.length,
    meanProcessCpuMs: cpuTimes.reduce((a, b) => a + b) / cpuTimes.length,
    medianCpuMs: sorted[Math.floor(sorted.length * .5)], p95CpuMs: sorted[Math.floor(sorted.length * .95)],
    physicalPixels: canvas.width * canvas.height,
    steadyFrameLayoutReads: measuredReads,
    repeatedSameSizeResizeEvents: resizeEvents - beforeRepeatedResize,
    repeatedSameStateEvents: changeEvents - beforeRepeatedState,
    meanCalls,
    status: api.getStatus() });
  if (process.env.CONTINUUM_BUDGET_VERIFY && width === 390) {
    assert.equal(measuredReads, 0);
    assert.equal(resizeEvents - beforeRepeatedResize, 0);
    assert.equal(changeEvents - beforeRepeatedState, 0);
    const before = api.getStatus();
    const firstPixels = Buffer.from(canvas.getContext('2d').getImageData(0, 0, canvas.width, canvas.height).data);
    // A synthetic 30 Hz callback cadence exercises adaptation, without claiming
    // the native harness or any user device actually runs at that frame rate.
    for (let index = 0; index < 300; index++) {
      current += 1000 / 30; timer = clock.now();
      const callbacks = [...queue.values()]; queue.clear();
      callbacks.forEach(fn => fn(current));
      canvas.getContext('2d').getImageData(0, 0, 1, 1);
    }
    const after = api.getStatus();
    assert.ok(after.resolutionScale < before.resolutionScale, 'High detail must still adapt its resolution.');
    assert.equal(after.quality, 1, 'Selected detail density must stay high.');
    assert.equal(after.renderedFrames - before.renderedFrames, 300, 'Every scheduled motion frame must render.');
    assert.ok(after.elapsedSeconds - before.elapsedSeconds > 9.9, 'The global timebase must continue.');
    assert.equal(after.atmosphereLayers, 3);
    assert.equal(after.orbVisible, false);
    const finalPixels = Buffer.from(canvas.getContext('2d').getImageData(0, 0, canvas.width, canvas.height).data);
    assert.notEqual(crypto.createHash('sha256').update(firstPixels).digest('hex'), crypto.createHash('sha256').update(finalPixels).digest('hex'));
    report.adaptiveContract = { passed: true, callbacks: 300, renderedFrames: after.renderedFrames - before.renderedFrames,
      initialResolutionScale: before.resolutionScale, finalResolutionScale: after.resolutionScale,
      density: after.quality, activeDepths: after.atmosphereLayers, elapsedAdvanceSeconds: after.elapsedSeconds - before.elapsedSeconds };
    await fs.writeFile(path.join(root, 'qa/command-atmosphere-native.png'), canvas.toBuffer('image/png'));
  }
  api.destroy();
}
const destination = process.argv[2];
if (destination) await fs.writeFile(path.resolve(destination), JSON.stringify(report, null, 2));
console.log(JSON.stringify(report, null, 2));
