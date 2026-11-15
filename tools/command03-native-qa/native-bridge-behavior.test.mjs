import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import {createRequire} from 'node:module';
import {resolve} from 'node:path';
import {fileURLToPath} from 'node:url';
import vm from 'node:vm';
import test from 'node:test';

// npm install --ignore-scripts --prefix /tmp/command03-dom jsdom@26.1.0
// COMMAND03_DOM_MODULE=/tmp/command03-dom/node_modules/jsdom/lib/api.js node --test tools/command03-native-qa/native-bridge-behavior.test.mjs
const require = createRequire(import.meta.url);
const {JSDOM} = require(process.env.COMMAND03_DOM_MODULE || 'jsdom');
const root = resolve(fileURLToPath(new URL('../../', import.meta.url)));
const assetRoot = resolve(root, 'app/src/main/assets/continuum-command03');
const sessionId = '12345678-1234-1234-1234-123456789abc';
const anotherSession = 'abcdefab-1234-1234-1234-123456789abc';

async function fixture() {
  const dom = new JSDOM(readFileSync(resolve(assetRoot, 'index.android.html'), 'utf8'), {
    url: 'https://appassets.androidplatform.net/assets/continuum-command03/index.android.html',
    pretendToBeVisual: true, runScripts: 'outside-only'
  });
  const window = dom.window, navigations = [], scheduled = new Map();
  let serial = 0, now = 100;
  const schedule = fn => {scheduled.set(++serial, fn); return serial;};
  const cancel = id => scheduled.delete(id);
  // Only the Android navigation transport and clock/GPU seams are doubles.
  // Production bridge and original DOM/app/telemetry/motion scripts execute unchanged.
  const proxy = new Proxy(window, {
    get(target, key) {
      if (key === 'location') return {assign: url => navigations.push(new URL(url))};
      if (key === 'performance') return {now: () => now};
      if (['setTimeout', 'setInterval', 'requestAnimationFrame'].includes(key)) return schedule;
      if (['clearTimeout', 'clearInterval', 'cancelAnimationFrame'].includes(key)) return cancel;
      return Reflect.get(target, key);
    }
  });
  window.matchMedia = () => ({matches: false, addEventListener() {}, removeEventListener() {}});
  window.ContinuumScene = {setState() {}, setIntensity() {}, setQuality() {}, resize() {}};
  const context = vm.createContext({
    window: proxy, document: window.document, console, URL, URLSearchParams, TextEncoder,
    CustomEvent: window.CustomEvent, MouseEvent: window.MouseEvent, Event: window.Event,
    MutationObserver: window.MutationObserver, localStorage: window.localStorage,
    performance: {now: () => now}, getComputedStyle: window.getComputedStyle.bind(window),
    requestAnimationFrame: schedule, cancelAnimationFrame: cancel,
    setTimeout: schedule, clearTimeout: cancel, setInterval: schedule, clearInterval: cancel,
    HTMLElement: window.HTMLElement, Element: window.Element, navigator: window.navigator,
    Blob: window.Blob, File: window.File, FileReader: window.FileReader
  });
  for (const name of ['native-bridge.js', 'workspace-motion.js', 'app.js',
    'presence-instruments.js', 'telemetry.js', 'operations.js']) {
    vm.runInContext(readFileSync(resolve(assetRoot, name), 'utf8'), context, {filename: name});
  }
  const style = window.document.createElement('style');
  style.textContent = readFileSync(resolve(assetRoot, 'native-layer.css'), 'utf8');
  window.document.head.appendChild(style);
  await new Promise(resolve => setImmediate(resolve));
  const state = (extra = {}) => window.ContinuumNative.receive({sessionId, busy: false,
    listening: false, cpu: 10, ram: 30, telemetryAtElapsedMs: now,
    status: 'Bereit', chats: [], workRows: [], ...extra});
  const click = element => element.dispatchEvent(new window.MouseEvent('click', {bubbles: true, cancelable: true}));
  const submit = () => window.document.querySelector('#command-form').dispatchEvent(
    new window.Event('submit', {bubbles: true, cancelable: true}));
  const input = text => {
    const field = window.document.querySelector('#command-input');
    field.value = text;field.dispatchEvent(new window.Event('input', {bubbles: true}));
    return field;
  };
  return {dom, window, document: window.document, navigations, state, click, submit, input,
    advance(ms) {now += ms;}, close() {dom.window.close();}};
}

test('a projected real PDF remains visible through periodic state and transcript updates', async () => {
  const f = await fixture();
  try {
    f.state();
    f.window.ContinuumNative.transcript({sessionId, entries: [{user: true, text: 'PDF öffnen'}]});
    f.window.ContinuumNative.document({sessionId, name: 'Beispiel.pdf', pageCount: 2,
      textState: 'EXTRACTED', text: '<script>untrusted</script> echte Textschicht'});
    f.state({cpu: 20});
    f.window.ContinuumNative.message({sessionId, user: false, text: 'Dokument gelesen'});
    const workspace = f.document.querySelector('#workspace');
    assert.equal(workspace.classList.contains('native-document-active'), true);
    assert.equal(workspace.classList.contains('native-work-active'), false);
    assert.notEqual(f.window.getComputedStyle(f.document.querySelector('#native-document-panel')).display, 'none');
    assert.equal(f.document.querySelector('.native-document-excerpt').textContent,
      '<script>untrusted</script> echte Textschicht');
    assert.equal(f.document.querySelector('#native-document-panel script'), null);
    f.window.ContinuumNative.clearDocument();
    assert.equal(workspace.classList.contains('native-work-active'), true);
  } finally {f.close();}
});

test('native file and source controls never start the original demonstration', async () => {
  const f = await fixture();
  try {
    f.state();
    f.click(f.document.querySelector('[data-action="local-file"]'));
    assert.equal(f.navigations.at(-1)?.searchParams.get('value'), 'files');
    assert.equal(f.document.querySelector('input[type="file"]'), null);
    const sequence = f.document.querySelector('[data-action="sequence"]');
    f.click(sequence);
    assert.equal(sequence.classList.contains('active'), false);
    f.click(f.document.querySelector('[data-scene="working"]'));
    assert.equal(f.window.ContinuumPreview.getState().state, 'idle');
    const source = f.document.createElement('button');
    source.dataset.action = 'quick-source';f.document.body.appendChild(source);f.click(source);
    assert.equal(f.navigations.at(-1)?.searchParams.get('value'), 'research');
    assert.equal(f.window.ContinuumOperations.getState(), null);
  } finally {f.close();}
});

test('real telemetry stops the hidden simulation and keeps unknown readings unknown', async () => {
  const f = await fixture();
  try {
    f.state({cpu: null, ram: null});
    assert.equal(f.window.ContinuumTelemetry.getStatus().active, false);
    assert.match(f.document.querySelector('[data-telemetry-left]').textContent, /APP-CPU/);
    assert.match(f.document.querySelector('[data-telemetry-left]').textContent, /—/);
    assert.doesNotMatch(f.document.querySelector('[data-telemetry-left]').textContent, /Beispiellast/);
  } finally {f.close();}
});

test('a command draft survives native rejection and clears only after its matching acceptance', async () => {
  const f = await fixture();
  try {
    f.state();const field = f.input('Bitte prüfe diese Frage.');f.submit();
    const request = f.navigations.findLast(url => url.pathname === '/command');
    assert.ok(request);
    assert.equal(field.value, 'Bitte prüfe diese Frage.');
    const id = request.searchParams.get('id');
    assert.ok(id);assert.equal(request.searchParams.get('sessionId'), sessionId);
    f.window.ContinuumNative.commandResult({id, sessionId, accepted: false, reason: 'busy'});
    assert.equal(field.value, 'Bitte prüfe diese Frage.');
    f.submit();const second = f.navigations.findLast(url => url.pathname === '/command');
    f.window.ContinuumNative.commandResult({id: second.searchParams.get('id'), sessionId,
      accepted: true, reason: ''});
    assert.equal(field.value, '');
  } finally {f.close();}
});

test('a late acceptance does not delete a new draft or a different conversation', async () => {
  const f = await fixture();
  try {
    f.state();f.input('Erster Auftrag');f.submit();
    const request = f.navigations.findLast(url => url.pathname === '/command');
    f.input('Nächster Entwurf');
    f.window.ContinuumNative.commandResult({id: request.searchParams.get('id'), sessionId,
      accepted: true, reason: ''});
    assert.equal(f.document.querySelector('#command-input').value, 'Nächster Entwurf');
    f.state({sessionId: anotherSession, draft: {text: 'Anderer Chat', selectionStart: 5, selectionEnd: 5}});
    f.window.ContinuumNative.commandResult({id: request.searchParams.get('id'), sessionId,
      accepted: true, reason: ''});
    assert.equal(f.document.querySelector('#command-input').value, 'Anderer Chat');
  } finally {f.close();}
});

test('long real commands and input selections use the native session and UTF-8 budget', async () => {
  const f = await fixture();
  try {
    f.state({maxCommandChars: 11500, maxCommandBytes: 11500});
    const field = f.input('a'.repeat(3000));field.setSelectionRange(4, 8);
    field.dispatchEvent(new f.window.Event('select', {bubbles: true}));
    const draft = f.navigations.findLast(url => url.pathname === '/draft');
    assert.equal(draft?.searchParams.get('sessionId'), sessionId);
    assert.equal(draft?.searchParams.get('selectionStart'), '4');
    assert.equal(draft?.searchParams.get('selectionEnd'), '8');
    f.submit();assert.equal(f.navigations.findLast(url => url.pathname === '/command')?.searchParams.get('value').length, 3000);
    const count = f.navigations.filter(url => url.pathname === '/command').length;
    const command = f.navigations.findLast(url => url.pathname === '/command');
    f.window.ContinuumNative.commandResult({id: command.searchParams.get('id'), sessionId,
      accepted: false, reason: 'busy'});
    f.input('ö'.repeat(6000));f.submit();
    assert.equal(f.navigations.filter(url => url.pathname === '/command').length, count);
    assert.equal(field.value, 'ö'.repeat(6000));
  } finally {f.close();}
});
