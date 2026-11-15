/* Focused deterministic DOM tests; no real browser or hardware measurements. */
'use strict';
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const { JSDOM, VirtualConsole } = require('../interop/require-dev.cjs')('jsdom');
const source = fs.readFileSync(path.join(__dirname, '../continuum-preview/telemetry.js'), 'utf8');
const css = fs.readFileSync(path.join(__dirname, '../continuum-preview/telemetry.css'), 'utf8');
const MiB = 1024 * 1024;

function fixture({ memory = null, longTasks = false, observerThrows = false } = {}) {
  const errors = [], timerDelays = [], virtual = new VirtualConsole();
  virtual.on('jsdomError', error => errors.push(error.message));
  const dom = new JSDOM('<!doctype html><html lang="de"><head></head><body><aside data-telemetry-left></aside><aside data-telemetry-right></aside></body></html>',
    { url: 'https://continuum.local/', runScripts: 'outside-only', pretendToBeVisual: true, virtualConsole: virtual });
  const w = dom.window, d = w.document;
  let now = 0, nextId = 0, observerCallback = null, disconnected = false;
  const frames = new Map(), timers = new Map();
  Object.defineProperty(w.performance, 'now', { value: () => now });
  Object.defineProperty(w.performance, 'memory', { configurable: true, get: () => memory });
  w.requestAnimationFrame = callback => { frames.set(++nextId, callback); return nextId; };
  w.cancelAnimationFrame = id => frames.delete(id);
  w.setTimeout = (callback, delay) => { timerDelays.push(delay); timers.set(++nextId, { callback, at: now + delay }); return nextId; };
  w.clearTimeout = id => timers.delete(id);
  w.HTMLElement.prototype.getBoundingClientRect = () => { throw new Error('Telemetry must not read layout.'); };
  if (longTasks) {
    w.PerformanceObserver = class {
      static supportedEntryTypes = ['longtask'];
      constructor(callback) { observerCallback = callback; }
      observe() { if (observerThrows) throw new Error('Unsupported'); }
      disconnect() { disconnected = true; }
    };
  } else w.PerformanceObserver = undefined;
  const style = d.createElement('style'); style.textContent = css; d.head.appendChild(style);
  w.eval(source); w.ContinuumTelemetry.mount();
  function tick(time, paint = true) {
    now = time;
    if (paint) { const queued = [...frames.values()]; frames.clear(); queued.forEach(callback => callback(now)); }
    for (const [id, item] of [...timers]) if (item.at <= now + 1e-7) { timers.delete(id); item.callback(); }
  }
  function advance(duration, interval = 1000 / 60) {
    const end = now + duration;
    while (now + interval < end - 1e-6) tick(now + interval);
    tick(end);
  }
  const api = w.ContinuumTelemetry;
  const q = selector => { const element = d.querySelector(selector); assert(element, selector); return element; };
  const emit = detail => w.dispatchEvent(new w.CustomEvent('continuum:operation', { detail }));
  const emitTasks = entries => observerCallback?.({ getEntries: () => entries });
  const hide = value => { Object.defineProperty(d, 'hidden', { configurable: true, value }); d.dispatchEvent(new w.Event('visibilitychange')); };
  return { w, d, q, api, tick, advance, emit, emitTasks, hide, errors, timerDelays,
    get pendingFrames() { return frames.size; }, get pendingTimers() { return timers.size; }, get disconnected() { return disconnected; },
    close() { api.destroy(); dom.window.close(); } };
}

const results = [];
function test(name, fn) { fn(); results.push({ name, passed: true }); }

test('default instrument titles and every source label explicitly identify simulation', () => {
  const f = fixture();
  assert.equal(f.api.getStatus().mode, 'simulation');
  assert.equal(f.q('[data-channel=cpu] h3').textContent, 'SIMULATION / CPU');
  assert.equal(f.q('[data-channel=ram] h3').textContent, 'SIMULATION / RAM');
  assert.equal(f.q('[data-channel=cpu] button span').textContent, 'Browsermessung');
  assert(f.api.getStatus().reading.cpuPercent > 0); assert(f.api.getStatus().reading.ramGiB > 0);
  assert.equal(f.api.getStatus().deviceCpuAccess, false); assert.equal(f.api.getStatus().deviceRamAccess, false);
  f.close();
});

test('frame collection performs no DOM writes and samples at most twice per second', () => {
  const f = fixture(), before = f.d.body.innerHTML;
  f.advance(333);
  assert.equal(f.d.body.innerHTML, before); assert.equal(f.api.getStatus().samples, 0);
  f.advance(167); assert.equal(f.api.getStatus().samples, 1);
  f.advance(1000); assert.equal(f.api.getStatus().samples, 3);
  assert(f.timerDelays.every(delay => delay >= 500));
  f.close();
});

test('browser mode reports supplied frame timing and JS heap without CPU percentages', () => {
  const memory = { usedJSHeapSize: 64 * MiB, totalJSHeapSize: 128 * MiB, jsHeapSizeLimit: 1024 * MiB };
  const f = fixture({ memory, longTasks: true }); f.api.setMode('browser');
  f.advance(500);
  const first = f.api.getStatus();
  assert(Math.abs(first.reading.frameMs - 1000 / 60) < 1e-5);
  assert.equal(first.reading.heapMiB, 64); assert.equal(first.reading.cpuPercent, null); assert.equal(first.reading.ramGiB, null);
  assert.equal(f.q('[data-channel=cpu] h3').textContent, 'FRAMEZEIT / BROWSER');
  assert.equal(f.q('[data-channel=ram] h3').textContent, 'JS-HEAP / BROWSER');
  assert.equal(f.q('[data-channel=ram] .ct-detail').textContent, 'Heap-Schätzung');
  assert.equal(f.q('[data-channel=cpu] .ct-unit').textContent, 'ms');
  memory.usedJSHeapSize = 80 * MiB; f.advance(500); assert.equal(f.api.getStatus().reading.heapMiB, 80);
  f.emitTasks([{ duration: 83, startTime: 990 }, { duration: 49, startTime: 990 }]); f.advance(500);
  assert.equal(f.api.getStatus().reading.longTasks, 1);
  assert(f.q('[data-channel=cpu] .ct-detail').textContent.includes('1'));
  f.close();
});

test('missing, rejected or invalid APIs stay unavailable instead of becoming zero values', () => {
  for (const options of [{}, { memory: { usedJSHeapSize: NaN, totalJSHeapSize: 5, jsHeapSizeLimit: 6 } }, { longTasks: true, observerThrows: true }]) {
    const f = fixture(options); f.api.setMode('browser'); f.advance(500);
    assert.equal(f.api.getStatus().reading.heapMiB, null); assert.equal(f.api.getStatus().reading.longTasks, null);
    assert.equal(f.q('[data-channel=ram] .ct-number strong').textContent, '—');
    assert.equal(f.q('[data-channel=ram] .ct-empty').textContent, 'Nicht verfügbar');
    assert(f.q('[data-channel=cpu] .ct-detail').textContent.includes('keine API')); f.close();
  }
});

test('simulation is deterministic and responds only illustratively to operation events', () => {
  const a = fixture(), b = fixture(); a.advance(500); b.advance(500);
  assert.equal(a.api.getStatus().reading.cpuPercent, b.api.getStatus().reading.cpuPercent);
  assert.equal(a.api.getStatus().reading.ramGiB, b.api.getStatus().reading.ramGiB);
  a.emit({ id: 'pdf-1', type: 'start', kind: 'file', label: 'PDF öffnen', simulated: false });
  a.advance(1000); b.advance(1000);
  assert(a.api.getStatus().reading.cpuPercent > b.api.getStatus().reading.cpuPercent + 10);
  assert(a.api.getStatus().reading.ramGiB > b.api.getStatus().reading.ramGiB);
  assert.equal(a.api.getStatus().reading.source, 'simulation');
  assert.equal(a.api.getStatus().operation.simulated, false);
  a.emit({ id: 'pdf-1', type: 'complete', kind: 'file', label: 'PDF geöffnet', progress: 1 });
  assert.equal(a.api.getStatus().activeOperations, 0);
  a.close(); b.close();
});

test('operation payloads never fabricate live CPU/RAM readings or insert HTML', () => {
  const f = fixture({ memory: { usedJSHeapSize: 64 * MiB, totalJSHeapSize: 128 * MiB, jsHeapSizeLimit: 1024 * MiB } });
  f.api.setMode('browser'); f.advance(500);
  f.emit({ id: 'r', type: 'start', kind: 'research', label: '<img src=x onerror=alert(1)>', progress: .7, simulated: true });
  f.advance(500);
  assert.equal(f.api.getStatus().reading.cpuPercent, null); assert.equal(f.api.getStatus().reading.heapMiB, 64);
  assert.equal(f.d.querySelectorAll('img,script').length, 0); assert.equal(f.api.getStatus().operation.kind, 'research');
  f.emit({ type: 'start', kind: 'diagram' }); f.emit({ type: 'complete', kind: 'diagram' });
  assert.equal(f.api.getStatus().activeOperations, 1, 'anonymous lifecycle must match by kind');
  f.close();
});

test('mode changes synchronize both instruments, preserve DOM controls and drop demo history', () => {
  const f = fixture(), cpuButton = f.q('[data-channel=cpu] button'), ramButton = f.q('[data-channel=ram] button');
  const path = f.q('[data-channel=cpu] .ct-trace'); cpuButton.focus(); cpuButton.click();
  assert.equal(f.api.getStatus().mode, 'browser'); assert.equal(f.d.activeElement, cpuButton);
  assert.equal(f.q('[data-channel=cpu] button'), cpuButton); assert.equal(f.q('[data-channel=ram] button'), ramButton);
  assert(f.api.getStatus().history.ram.every(value => value === null));
  f.advance(1500); assert.equal(f.q('[data-channel=cpu] .ct-trace'), path);
  ramButton.click(); assert.equal(f.api.getStatus().mode, 'simulation');
  assert.equal(f.api.setMode('cpu-device'), false); assert.equal(f.api.getStatus().mode, 'simulation');
  assert.equal(f.w.localStorage.getItem('continuum-telemetry-mode'), 'simulation');
  f.close();
});

test('hidden pages stop collectors and avoid treating the hidden gap as frame time', () => {
  const f = fixture(); f.api.setMode('browser'); f.advance(500); const count = f.api.getStatus().samples;
  f.hide(true); assert.equal(f.pendingFrames, 0); assert.equal(f.pendingTimers, 0);
  f.tick(20500); assert.equal(f.api.getStatus().samples, count);
  f.hide(false);
  assert(f.api.getStatus().history.cpu.every(value => value === null), 'old history must not be compressed across a hidden gap');
  assert.equal(f.api.getStatus().reading.frameMs, null);
  f.advance(500);
  assert(Math.abs(f.api.getStatus().reading.frameMs - 1000 / 60) < 1e-5);
  assert.equal(f.api.getStatus().paused, false); f.close();
});

test('controls keep 44px hit height, numbers are at least 24px and labels at least 11px', () => {
  const f = fixture();
  const height = parseFloat(f.w.getComputedStyle(f.q('.ct-mode-toggle')).minHeight);
  assert(height >= 44);
  assert(parseFloat(f.w.getComputedStyle(f.q('.ct-number strong')).fontSize) >= 24);
  for (const selector of ['.ct-heading h3', '.ct-tag', '.ct-unit', '.ct-caption span', '.ct-caption small', '.ct-meta', '.ct-mode-toggle']) {
    assert(parseFloat(f.w.getComputedStyle(f.q(selector)).fontSize) >= 11, selector);
  }
  const style = f.w.getComputedStyle(f.q('.ct-panel'));
  const rows = [...style.gridTemplateRows.matchAll(/([\d.]+)px/g)].map(match => +match[1]);
  const nominalHeight = rows.reduce((sum, value) => sum + value, 0) + (rows.length - 1) * parseFloat(style.rowGap)
    + parseFloat(style.paddingTop) + parseFloat(style.paddingBottom) + parseFloat(style.borderTopWidth) + parseFloat(style.borderBottomWidth);
  assert(nominalHeight <= 200, `declared minimum panel height ${nominalHeight}px exceeds the intended instrument budget`);
  assert.equal(f.errors.length, 0, f.errors.join('\n')); f.close();
});

test('destroy disconnects observers, callbacks, event listeners and only its own DOM', () => {
  const f = fixture({ longTasks: true }); f.api.destroy();
  assert.equal(f.pendingFrames, 0); assert.equal(f.pendingTimers, 0); assert.equal(f.disconnected, true);
  assert.equal(f.d.querySelectorAll('.ct-panel').length, 0); assert.equal(f.d.querySelectorAll('aside').length, 2);
  f.emit({ id: 'ignored', type: 'start', kind: 'agent' }); assert.equal(f.api.getStatus().operation, null);
  f.close();
});

const report = { runtime: 'Node + jsdom with explicitly supplied measurement fixtures; no device or browser performance claim', tests: results };
fs.writeFileSync(path.join(__dirname, 'telemetry-report.json'), JSON.stringify(report, null, 2) + '\n');
console.log(JSON.stringify(report, null, 2));
