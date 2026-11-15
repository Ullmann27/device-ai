/* Focused DOM checks for the two presence instruments; no browser/GPU claims. */
'use strict';
const fs = require('node:fs');
const path = require('node:path');
const assert = require('node:assert/strict');
const { JSDOM, VirtualConsole } = require('../interop/require-dev.cjs')('jsdom');
const base = path.resolve(__dirname, '../continuum-preview');
const html = fs.readFileSync(path.join(base, 'index.html'), 'utf8');
const app = fs.readFileSync(path.join(base, 'app.js'), 'utf8');
const instruments = fs.readFileSync(path.join(base, 'presence-instruments.js'), 'utf8');
const css = fs.readFileSync(path.join(base, 'presence-instruments.css'), 'utf8');

function fixture() {
  const errors = [], scrolled = [];
  const vc = new VirtualConsole();
  vc.on('jsdomError', error => errors.push(error.message));
  const dom = new JSDOM(html, { url: 'https://continuum.local/', runScripts: 'outside-only', pretendToBeVisual: true, virtualConsole: vc });
  const w = dom.window, d = w.document;
  let timer = 0;
  const pending = new Map();
  w.setTimeout = (fn, delay) => { pending.set(++timer, { fn, delay }); return timer; };
  w.setInterval = w.setTimeout;
  w.clearTimeout = w.clearInterval = id => pending.delete(id);
  w.requestAnimationFrame = fn => w.setTimeout(fn, 16);
  w.cancelAnimationFrame = w.clearTimeout;
  w.ContinuumScene = { setState() {}, resize() {}, setIntensity() {}, setQuality() {} };
  w.HTMLElement.prototype.scrollIntoView = function () { scrolled.push(this.id); };
  const style = d.createElement('style'); style.textContent = css; d.head.appendChild(style);
  w.eval(app); w.eval(instruments);
  const q = selector => { const node = d.querySelector(selector); assert.ok(node, `Missing: ${selector}`); return node; };
  const all = selector => [...d.querySelectorAll(selector)];
  const click = selector => q(selector).dispatchEvent(new w.MouseEvent('click', { bubbles: true, cancelable: true }));
  const key = (node, value) => node.dispatchEvent(new w.KeyboardEvent('keydown', { key: value, bubbles: true, cancelable: true }));
  const data = () => w.ContinuumPreview.getVisualizationData();
  const emit = detail => w.dispatchEvent(new w.CustomEvent('continuum:visualizationchange', { detail }));
  return { w, d, q, all, click, key, data, emit, errors, scrolled, close: () => w.close() };
}
const cases = [];
const test = (name, fn) => cases.push({ name, fn });

test('API supplies all six intervals and three grouped source relations', f => {
  const api = f.data(), groups = [...new Set(api.phases.map(p => p.source))];
  assert.equal(api.phases.length, 6); assert.equal(groups.length, 3);
  assert.equal(f.all('.pi-phase-button').length, api.phases.length);
  assert.equal(f.all('.pi-source-button').length, groups.length);
  assert.deepEqual(f.all('.pi-source-button').map(n => n.dataset.source), Array.from(groups));
  const lo = Math.min(...api.phases.map(p => p.start)), hi = Math.max(...api.phases.map(p => p.end));
  api.phases.forEach((p, i) => {
    const bar = f.q(`[data-pi-row="${i}"] .pi-bar`);
    assert.ok(Math.abs(+bar.getAttribute('x') - (10 + (p.start - lo) / (hi - lo) * 220)) < 1e-9);
    assert.ok(Math.abs(+bar.getAttribute('width') - (p.end - p.start) / (hi - lo) * 220) < 1e-9);
    assert.ok(f.q(`.pi-phase-button[data-phase="${i}"]`).getAttribute('aria-label').includes(p.name));
  });
  assert.ok(f.q('[data-presence-left]').textContent.includes('Entwurfswerte'));
  assert.ok(f.q('[data-presence-right]').textContent.includes('Demonstration'));
});

test('Every phase action opens real workspace and synchronizes both hero instruments', f => {
  const nodes = f.all('.pi-phase-button');
  f.data().phases.forEach((phase, i) => {
    f.click(`.pi-phase-button[data-phase="${i}"]`);
    assert.equal(f.data().selectedPhase, i);
    assert.equal(f.data().state, 'working');
    assert.equal(f.q('.pi-phase-button.is-selected').dataset.phase, String(i));
    assert.equal(f.q('.pi-source-button.is-selected').dataset.source, phase.source);
    assert.equal(f.q('.doc-paragraph.selected').id, phase.source);
    assert.equal(f.q('[data-pi-source-proof]').textContent, phase.name);
    assert.equal(f.q('[data-pi-current-end]').textContent, String(phase.end));
    assert.equal(f.q(`.pi-phase-button[data-phase="${i}"]`), nodes[i], 'Phase selection must preserve stable DOM controls');
  });
});

test('Enter and Space preserve focused phase controls through root rendering', f => {
  for (const key of ['Enter', ' ']) {
    f.data().phases.forEach((phase, i) => {
      const node = f.q(`.pi-phase-button[data-phase="${i}"]`);
      node.focus(); f.key(node, key);
      assert.equal(f.d.activeElement, node);
      assert.equal(node.getAttribute('aria-pressed'), 'true');
      assert.equal(f.data().selectedPhase, i);
    });
  }
});

test('Each source button opens and focuses its actual document passage', f => {
  const groups = [...new Set(f.data().phases.map(p => p.source))];
  for (const [index, source] of groups.entries()) {
    const node = f.all('.pi-source-button')[index];
    node.focus(); f.key(node, 'Enter');
    assert.equal(f.d.activeElement.id, source);
    assert.ok(f.scrolled.includes(source));
    assert.equal(f.q('.pi-source-button.is-selected').dataset.source, source);
    assert.equal(f.q('.pi-source-branch[data-selected="true"]').dataset.piSource, source);
    assert.equal(f.q('.doc-paragraph.selected').id, source);
  }
});

test('Scene and tool changes keep diagram values fixed and buttons stable', f => {
  const node = f.q('.pi-phase-button'), chart = f.q('.pi-time-chart');
  const values = f.all('.pi-bar').map(n => [n.getAttribute('x'), n.getAttribute('width')]);
  for (const state of ['idle', 'listening', 'forming', 'working', 'diagram', 'speaking', 'result']) {
    f.w.ContinuumPreview.setScene(state);
    assert.equal(f.q('[data-presence-left]').dataset.instrumentState, state);
    assert.equal(f.q('[data-presence-right]').dataset.instrumentState, state);
    assert.equal(f.q('.pi-phase-button'), node);
    assert.equal(f.q('.pi-time-chart'), chart);
    assert.deepEqual(f.all('.pi-bar').map(n => [n.getAttribute('x'), n.getAttribute('width')]), values);
  }
  for (const section of ['files', 'research', 'agents', 'memory', 'computer', 'settings', 'conversation']) {
    f.w.ContinuumPreview.navigate(section);
    assert.equal(f.q('.pi-phase-button'), node);
    assert.equal(f.w.ContinuumPresenceInstruments.getStatus().connected, true);
  }
});

test('API events rebuild only changed data, retain focus and escape supplied labels', f => {
  const data = f.data();
  data.phases[2].name = '<img src=x onerror="alert(1)">';
  data.phases[2].short = 'Geprüfte Quelle';
  data.phases[2].end += 7;
  data.selectedPhase = 2;
  f.q('.pi-phase-button[data-phase="2"]').focus();
  f.emit(data);
  assert.equal(f.d.activeElement, f.q('.pi-phase-button[data-phase="2"]'));
  assert.equal(f.q('[data-pi-current-end]').textContent, String(data.phases[2].end));
  assert.equal(f.q('[data-pi-source-proof]').textContent, data.phases[2].name);
  assert.equal(f.all('.presence-instrument img').length, 0);
  assert.equal(f.all('.presence-instrument script').length, 0);
  assert.equal(f.data().phases[2].name, 'Licht wird Form', 'Event data must not mutate the app model');
  f.w.ContinuumPresenceInstruments.refresh();
  assert.equal(f.q('[data-pi-current-end]').textContent, String(f.data().phases[2].end));
});

test('Source grouping follows changed API source IDs without fixed relationships', f => {
  const data = f.data();
  data.phases[0].source = 'test-source-four';
  data.selectedPhase = 0;
  f.emit(data);
  assert.equal(f.all('.pi-source-button').length, 4);
  assert.equal(f.q('.pi-source-button.is-selected').dataset.source, 'test-source-four');
  assert.equal(f.q('.pi-source-branch[data-selected="true"]').dataset.piSource, 'test-source-four');
  f.w.ContinuumPresenceInstruments.refresh();
  assert.equal(f.all('.pi-source-button').length, 3);
});

test('Gaze command and semantic pointer targets reach existing scene consumers unchanged', f => {
  const commands = [], targets = [];
  f.w.addEventListener('continuum:gaze', e => commands.push(e.detail));
  f.d.addEventListener('pointerdown', e => {
    const target = e.target.closest('[data-phase], [data-source]');
    if (target) targets.push(target);
  });
  const command = Object.freeze({ x: -.35, y: .12, durationMs: 2200 });
  f.w.dispatchEvent(new f.w.CustomEvent('continuum:gaze', { detail: command }));
  assert.equal(commands[0], command);
  for (const selector of ['.pi-phase-button .pi-phase-end', '.pi-source-button small']) {
    f.q(selector).dispatchEvent(new f.w.Event('pointerdown', { bubbles: true, cancelable: true }));
  }
  assert.equal(targets.length, 2);
  assert.ok(targets[0].matches('.pi-phase-button'));
  assert.ok(targets[1].matches('.pi-source-button'));
});

test('Controls have 44px minimum CSS dimensions, unique labels and safe SVG structure', f => {
  for (const node of f.all('.pi-phase-button, .pi-source-button')) {
    const computed = f.w.getComputedStyle(node);
    assert.ok(parseFloat(computed.minWidth) >= 44);
    assert.ok(parseFloat(computed.minHeight) >= 44);
    assert.ok(node.getAttribute('aria-label').length > 15);
    assert.equal(node.type, 'button');
  }
  const ids = f.all('[id]').map(n => n.id);
  assert.equal(ids.length, new Set(ids).size);
  assert.equal(f.all('.presence-instrument svg [tabindex]').length, 0);
  assert.equal(f.w.getComputedStyle(f.q('.pi-time-chart')).pointerEvents, 'none');
});

test('Disposed module unsubscribes and reinitialization does not duplicate live controls', f => {
  const old = f.w.ContinuumPresenceInstruments, count = old.getStatus().updates;
  old.dispose(); f.emit(f.data());
  assert.equal(old.getStatus().updates, count);
  assert.equal(old.getStatus().disposed, true);
  f.w.eval(instruments);
  assert.equal(f.all('.pi-phase-button').length, 6);
  assert.equal(f.all('.pi-source-button').length, 3);
  assert.equal(f.w.ContinuumPresenceInstruments.getStatus().connected, true);
});

const report = { suite: 'presence-instruments-dom', runtime: 'jsdom', visualOrGPUVerification: false, cssMinimumsAreNotLayoutMeasurement: true, tests: [] };
for (const item of cases) {
  let f;
  try {
    f = fixture(); item.fn(f); assert.deepEqual(f.errors, [], 'Unexpected DOM runtime errors');
    report.tests.push({ name: item.name, passed: true }); console.log('PASS ' + item.name);
  } catch (error) {
    report.tests.push({ name: item.name, passed: false, error: error.stack }); console.error('FAIL ' + item.name + '\n' + error.stack);
  } finally { f?.close(); }
}
report.passed = report.tests.filter(t => t.passed).length;
report.failed = report.tests.length - report.passed;
fs.writeFileSync(path.join(__dirname, 'presence-instruments-results.json'), JSON.stringify(report, null, 2));
console.log(JSON.stringify({ passed: report.passed, failed: report.failed, total: report.tests.length, visualOrGPUVerification: false }));
process.exitCode = report.failed ? 1 : 0;
