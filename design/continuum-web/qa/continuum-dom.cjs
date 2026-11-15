/* Real DOM integration checks. No browser, layout, Canvas or GPU claims. */
'use strict';
const fs = require('node:fs');
const path = require('node:path');
const assert = require('node:assert/strict');
const requireDev = require('../interop/require-dev.cjs');
const { JSDOM, VirtualConsole } = requireDev('jsdom');
const base = path.resolve(__dirname, '../continuum-preview');
const html = fs.readFileSync(path.join(base, 'index.html'), 'utf8');
const script = fs.readFileSync(path.join(base, 'app.js'), 'utf8');

function fixture(options = {}) {
  const errors = [], scrolled = [], sceneCalls = [];
  const virtualConsole = new VirtualConsole();
  virtualConsole.on('jsdomError', error => errors.push(error.message));
  const dom = new JSDOM(html, { url: 'https://continuum.local/', runScripts: 'outside-only', pretendToBeVisual: true, virtualConsole });
  const w = dom.window, d = w.document;
  Object.defineProperty(w, 'innerWidth', { value: options.width || 1440, writable: true });
  if (options.storage) for (const [key, value] of Object.entries(options.storage)) w.localStorage.setItem(key, value);
  let now = 0, nextId = 1;
  const pending = new Map();
  function schedule(fn, delay, repeat, args) {
    const id = nextId++;
    pending.set(id, { id, at: now + Math.max(0, Number(delay) || 0), repeat, fn, args });
    return id;
  }
  w.setTimeout = (fn, delay = 0, ...args) => schedule(fn, delay, 0, args);
  w.clearTimeout = id => pending.delete(id);
  w.setInterval = (fn, delay = 0, ...args) => schedule(fn, delay, Math.max(1, Number(delay) || 1), args);
  w.clearInterval = id => pending.delete(id);
  w.requestAnimationFrame = fn => w.setTimeout(() => fn(now), 16);
  w.cancelAnimationFrame = w.clearTimeout;
  function advance(ms) {
    const until = now + ms;
    let executions = 0;
    while (true) {
      const task = [...pending.values()].filter(t => t.at <= until).sort((a, b) => a.at - b.at || a.id - b.id)[0];
      if (!task) break;
      assert.ok(++executions < 20000, 'Timer runaway');
      now = task.at;
      if (task.repeat) pending.set(task.id, { ...task, at: task.at + task.repeat });
      else pending.delete(task.id);
      task.fn(...task.args);
    }
    now = until;
  }
  const scene = { current: null, intensity: null, quality: null, resizeCount: 0 };
  w.ContinuumScene = {
    setState(value) { scene.current = value; sceneCalls.push(value); },
    resize() { scene.resizeCount++; },
    setIntensity(value) { scene.intensity = value; },
    setQuality(value) { scene.quality = value; }
  };
  w.HTMLElement.prototype.scrollIntoView = function () { scrolled.push(this.id); };
  w.eval(script);
  const q = selector => { const node = d.querySelector(selector); assert.ok(node, `Missing ${selector}`); return node; };
  const click = selector => q(selector).dispatchEvent(new w.MouseEvent('click', { bubbles: true, cancelable: true }));
  const key = (value, target = d) => target.dispatchEvent(new w.KeyboardEvent('keydown', { key: value, bubbles: true, cancelable: true }));
  const submit = text => { q('#command-input').value = text; q('#command-form').dispatchEvent(new w.Event('submit', { bubbles: true, cancelable: true })); };
  const state = () => w.ContinuumPreview.getState();
  return { w, d, q, click, key, submit, advance, state, scene, sceneCalls, errors, scrolled, close: () => dom.window.close() };
}

const cases = [];
const test = (name, fn, options = {}) => cases.push({ name, fn, options });

test('Bootstrap: 7 tools, 7 scenes, 3 instruments and no duplicate IDs', f => {
  assert.equal(f.d.querySelectorAll('.nav-button').length, 7);
  assert.equal(f.d.querySelectorAll('.scene-button').length, 7);
  assert.equal(f.d.querySelectorAll('.instrument-unit').length, 3);
  assert.equal(f.state().state, 'idle');
  const ids = [...f.d.querySelectorAll('[id]')].map(n => n.id);
  assert.equal(ids.length, new Set(ids).size);
});

test('All seven tool buttons render their actual workspace', f => {
  for (const id of ['conversation', 'files', 'research', 'agents', 'memory', 'computer', 'settings']) {
    f.click(`.nav-button[data-section="${id}"]`);
    assert.equal(f.state().section, id);
    assert.equal(f.q(`.nav-button[data-section="${id}"]`).getAttribute('aria-current'), 'page');
    assert.ok(f.q('#workspace').textContent.trim().length > 100);
  }
});

test('All seven scene controls update content, selection and scene bridge', f => {
  for (const id of ['idle', 'listening', 'forming', 'working', 'diagram', 'speaking', 'result']) {
    f.click(`.scene-button[data-scene="${id}"]`);
    assert.equal(f.state().state, id);
    assert.equal(f.q(`.scene-button[data-scene="${id}"]`).getAttribute('aria-current'), 'step');
    assert.equal(f.scene.current, id === 'diagram' ? 'working' : id);
    assert.ok(f.q('#workspace').textContent.trim().length > 100);
  }
});

test('Argon4 opens only after the 950 ms formation completes', f => {
  f.click('.presence-copy [data-action="open-demo"]');
  assert.equal(f.state().state, 'forming');
  f.advance(949);
  assert.equal(f.state().state, 'forming');
  f.advance(1);
  assert.equal(f.state().state, 'working');
  assert.ok(f.q('.doc-heading').textContent.includes('Argon4.pdf'));
  assert.equal(f.d.querySelectorAll('.chart-point').length, 6);
});

test('Four sidebar examples route to their intended task', f => {
  for (const [chat, section, state] of [['argon', 'conversation', 'working'], ['team', 'agents', 'idle'], ['light', 'settings', 'idle'], ['start', 'conversation', 'idle']]) {
    f.click(`.chat-entry[data-chat="${chat}"]`);
    assert.equal(f.state().currentChat, chat);
    assert.equal(f.state().section, section);
    assert.equal(f.state().state, state);
  }
});

test('New chats keep isolated histories and recover them on selection', f => {
  f.click('[data-action="new-chat"]');
  const first = f.state().currentChat;
  f.submit('Nebel 731');
  assert.ok(f.q('.chat-history').textContent.includes('Nebel 731'));
  f.click('[data-action="new-chat"]');
  const second = f.state().currentChat;
  assert.notEqual(first, second);
  assert.equal(f.d.querySelector('.chat-history'), null);
  f.submit('Werkzeugkiste aus Kupfer');
  f.click(`.chat-entry[data-chat="${first}"]`);
  assert.ok(f.q('.chat-history').textContent.includes('Nebel 731'));
  assert.ok(!f.q('.chat-history').textContent.includes('Werkzeugkiste aus Kupfer'));
  f.click(`.chat-entry[data-chat="${second}"]`);
  assert.ok(f.q('.chat-history').textContent.includes('Werkzeugkiste aus Kupfer'));
  assert.ok(!f.q('.chat-history').textContent.includes('Nebel 731'));
  assert.equal(f.state().chatCount, 6);
});

test('Right instrument phase selection opens and synchronizes the document', f => {
  f.click('.rail-phase-button[data-phase="5"]');
  assert.equal(f.state().state, 'working');
  assert.equal(f.state().selectedPhase, 5);
  assert.equal(f.q('.chart-point.selected').dataset.phase, '5');
  assert.equal(f.q('.rail-phase-button.active').dataset.phase, '5');
  assert.equal(f.q('.doc-paragraph.selected').id, 'source-content');
  assert.equal(f.q('.source-entry.active').dataset.source, 'source-content');
});

test('Right source selection closes drawer and focuses the real passage', f => {
  f.click('[data-action="toggle-instruments"]');
  f.click('.source-entry[data-source="source-formation"]');
  assert.equal(f.state().state, 'working');
  assert.equal(f.state().selectedPhase, 2);
  assert.equal(f.d.activeElement.id, 'source-formation');
  assert.ok(f.scrolled.includes('source-formation'));
  assert.ok(!f.d.body.classList.contains('instruments-open'));
});

test('Source paragraph selection updates both diagrams', f => {
  f.click('.chat-entry[data-chat="argon"]');
  f.click('#source-attention');
  assert.equal(f.state().selectedPhase, 0);
  assert.equal(f.q('.chart-point.selected').dataset.phase, '0');
  assert.equal(f.q('.rail-phase-button.active').dataset.phase, '0');
});

test('Keyboard selection retains focus for rail, phone buttons and SVG', f => {
  f.click('.chat-entry[data-chat="argon"]');
  for (const [selector, i] of [['.rail-phase-button', 4], ['.phase-choice', 1], ['.chart-point', 3]]) {
    const before = f.q(`${selector}[data-phase="${i}"]`);
    before.focus();
    assert.equal(f.d.activeElement, before);
    f.key('Enter', before);
    assert.equal(f.state().selectedPhase, i);
    assert.equal(f.d.activeElement, f.q(`${selector}[data-phase="${i}"]`));
  }
}, { width: 430 });

test('Phone drawers are exclusive; Escape closes drawer without cancelling task', f => {
  f.click('.chat-entry[data-chat="argon"]');
  f.click('[data-action="toggle-chats"]');
  assert.ok(f.d.body.classList.contains('chats-open'));
  f.click('[data-action="toggle-instruments"]');
  assert.ok(f.d.body.classList.contains('instruments-open'));
  assert.ok(!f.d.body.classList.contains('chats-open'));
  assert.equal(f.q('[data-action="toggle-chats"]').getAttribute('aria-expanded'), 'false');
  assert.equal(f.q('[data-action="toggle-instruments"]').getAttribute('aria-expanded'), 'true');
  f.key('Escape');
  assert.ok(!f.d.body.classList.contains('instruments-open'));
  assert.equal(f.state().state, 'working');
}, { width: 430 });

test('Cancel during formation prevents the late document opening', f => {
  f.click('.presence-copy [data-action="open-demo"]'); f.advance(300);
  f.click('#workspace [data-action="cancel"]');
  assert.equal(f.state().state, 'idle');
  f.advance(2000);
  assert.equal(f.state().state, 'idle');
  assert.equal(f.d.querySelector('.document-layout'), null);
});

test('Closing followed by navigation clears returning and cancels stale navigation', f => {
  f.click('.chat-entry[data-chat="argon"]');
  f.click('#close-button');
  assert.ok(f.q('.workspace-shell').classList.contains('returning'));
  f.advance(200); f.click('.nav-button[data-section="files"]');
  assert.ok(!f.q('.workspace-shell').classList.contains('returning'));
  f.advance(1000);
  assert.equal(f.state().section, 'files');
  assert.ok(!f.q('.workspace-shell').classList.contains('returning'));
});

test('Negated opens and PDF stop commands never open a document', f => {
  for (const command of ['Öffne Argon4 nicht', 'Bitte nicht die PDF öffnen', 'PDF abbrechen', 'Stop Argon4']) {
    f.submit(command); f.advance(1100);
    assert.equal(f.state().state, 'idle', command);
    assert.equal(f.d.querySelector('.document-layout'), null, command);
  }
});

test('Nicht abbrechen preserves the actual in-flight formation timer', f => {
  f.click('.presence-copy [data-action="open-demo"]'); f.advance(100);
  f.submit('nicht abbrechen');
  assert.equal(f.state().state, 'forming');
  f.advance(849); assert.equal(f.state().state, 'forming');
  f.advance(1); assert.equal(f.state().state, 'working');
});

test('Escape terminates agent state and all pending handoffs', f => {
  f.click('.nav-button[data-section="agents"]');
  f.click('[data-action="run-agents"]');
  assert.equal(f.d.querySelectorAll('.agent-unit.running').length, 1);
  assert.equal(f.scene.current, 'working');
  f.advance(500); f.key('Escape');
  assert.equal(f.state().state, 'idle');
  assert.equal(f.state().section, 'conversation');
  assert.equal(f.scene.current, 'idle');
  f.advance(6000);
  assert.equal(f.scene.current, 'idle');
  assert.equal(f.d.querySelectorAll('.agent-unit.running').length, 0);
});

test('Escape terminates computer state and all pending steps', f => {
  f.click('.nav-button[data-section="computer"]');
  f.click('[data-action="run-computer"]');
  f.advance(700);
  assert.equal(f.d.querySelectorAll('.line-complete').length, 1);
  f.key('Escape');
  assert.equal(f.scene.current, 'idle');
  f.advance(6000);
  assert.equal(f.scene.current, 'idle');
  assert.equal(f.state().section, 'conversation');
});

test('Full seven-scene sequence completes and clears play status', f => {
  f.click('#sequence-button');
  const steps = [['idle', 0], ['listening', 2000], ['forming', 2500], ['working', 950], ['diagram', 3550], ['speaking', 3500], ['result', 4000]];
  for (const [id, delta] of steps) { f.advance(delta); assert.equal(f.state().state, id); }
  assert.ok(!f.q('#sequence-button').classList.contains('active'));
});

test('HTML input is literal text in both message and sidebar title', f => {
  f.click('[data-action="new-chat"]');
  const payload = '<img src=x onerror="window.__xss=true"> & "Zitat"';
  f.submit(payload);
  assert.ok(f.q('.chat-message.user').textContent.includes(payload));
  assert.equal(f.d.querySelector('.chat-message img'), null);
  assert.equal(f.d.querySelector('.chat-entry img'), null);
  assert.equal(f.w.__xss, undefined);
});

test('Local settings affect bridge and survive a new DOM session', f => {
  f.click('.nav-button[data-section="settings"]');
  f.q('#light-setting').value = '46';
  f.q('#light-setting').dispatchEvent(new f.w.Event('input', { bubbles: true }));
  f.q('#density-setting').value = 'ruhig';
  f.q('#density-setting').dispatchEvent(new f.w.Event('change', { bubbles: true }));
  f.q('#accent-setting').value = 'violet';
  f.q('#accent-setting').dispatchEvent(new f.w.Event('change', { bubbles: true }));
  assert.equal(f.scene.intensity, .46);
  assert.equal(f.scene.quality, 'low');
  assert.equal(f.d.documentElement.style.getPropertyValue('--cyan'), '#b1a2ff');
  const stored = f.w.localStorage.getItem('continuum-preview-settings');
  const again = fixture({ storage: { 'continuum-preview-settings': stored } });
  try {
    again.click('.nav-button[data-section="settings"]');
    assert.equal(again.q('#light-setting').value, '46');
    assert.equal(again.q('#density-setting').value, 'ruhig');
    assert.equal(again.q('#accent-setting').value, 'violet');
    assert.equal(again.scene.intensity, .46);
    assert.deepEqual(again.errors, []);
  } finally { again.close(); }
});

test('Successful agent and computer examples finish without claiming backend work', f => {
  f.click('.nav-button[data-section="agents"]');f.click('[data-action="run-agents"]');f.advance(4350);
  assert.equal(f.d.querySelectorAll('.agent-unit.running').length, 0);
  assert.ok([...f.d.querySelectorAll('.agent-state')].every(n => n.textContent === 'Demo abgeschlossen'));
  f.click('.nav-button[data-section="computer"]');f.click('[data-action="run-computer"]');f.advance(3250);
  assert.equal(f.d.querySelectorAll('.line-complete').length, 5);
  assert.ok(f.q('#toast').textContent.includes('keine Geräteaktionen'));
});

const report = { suite: 'continuum-dom', runtime: 'jsdom', visualOrGPUVerification: false, tests: [] };
for (const t of cases) {
  let f;
  try {
    f = fixture(t.options);t.fn(f);assert.deepEqual(f.errors, [], 'Unhandled DOM runtime errors');
    report.tests.push({ name: t.name, passed: true });
    console.log('PASS ' + t.name);
  } catch (error) {
    report.tests.push({ name: t.name, passed: false, error: error.stack });
    console.error('FAIL ' + t.name + '\n' + error.stack);
  } finally { f?.close(); }
}
report.passed = report.tests.filter(t => t.passed).length;
report.failed = report.tests.length - report.passed;
fs.writeFileSync(path.join(__dirname, 'continuum-dom-results.json'), JSON.stringify(report, null, 2));
console.log(JSON.stringify({ passed: report.passed, failed: report.failed, total: report.tests.length, visualOrGPUVerification: false }));
process.exitCode = report.failed ? 1 : 0;
