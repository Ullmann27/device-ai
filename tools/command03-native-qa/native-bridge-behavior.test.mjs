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
const pageId = '11111111-1111-4111-8111-111111111111';
const viewId = '22222222-2222-4222-8222-222222222222';
const anotherView = '33333333-3333-4333-8333-333333333333';
const sourceSetId = '55555555-5555-4555-8555-555555555555';
const sourceId = '66666666-6666-4666-8666-666666666666';
const documentId = '77777777-7777-4777-8777-777777777777';
const anotherDocument = '88888888-8888-4888-8888-888888888888';
const binding = {sessionId, pageId, viewId, viewRevision: 1};
const controlState = (f, extra = {}) => f.state({...binding, draftRevision: 1,
  ackEditRevision: 0, viewKind: 'CENTRAL', viewLabel: 'JARVIS', ...extra});
// Four-by-three synthetic JPEG, generated locally; no owner or device image is stored.
const frameDataUri = 'data:image/jpeg;base64,/9j/4AAQSkZJRgABAQAAAQABAAD/2wBDAAgGBgcGBQgHBwcJCQgKDBQNDAsLDBkSEw8UHRofHh0aHBwgJC4nICIsIxwcKDcpLDAxNDQ0Hyc5PTgyPC4zNDL/2wBDAQkJCQwLDBgNDRgyIRwhMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjL/wAARCAADAAQDASIAAhEBAxEB/8QAHwAAAQUBAQEBAQEAAAAAAAAAAAECAwQFBgcICQoL/8QAtRAAAgEDAwIEAwUFBAQAAAF9AQIDAAQRBRIhMUEGE1FhByJxFDKBkaEII0KxwRVS0fAkM2JyggkKFhcYGRolJicoKSo0NTY3ODk6Q0RFRkdISUpTVFVWV1hZWmNkZWZnaGlqc3R1dnd4eXqDhIWGh4iJipKTlJWWl5iZmqKjpKWmp6ipqrKztLW2t7i5usLDxMXGx8jJytLT1NXW19jZ2uHi4+Tl5ufo6erx8vP09fb3+Pn6/8QAHwEAAwEBAQEBAQEBAQAAAAAAAAECAwQFBgcICQoL/8QAtREAAgECBAQDBAcFBAQAAQJ3AAECAxEEBSExBhJBUQdhcRMiMoEIFEKRobHBCSMzUvAVYnLRChYkNOEl8RcYGRomJygpKjU2Nzg5OkNERUZHSElKU1RVVldYWVpjZGVmZ2hpanN0dXZ3eHl6goOEhYaHiImKkpOUlZaXmJmaoqOkpaanqKmqsrO0tba3uLm6wsPExcbHyMnK0tPU1dbX2Nna4uPk5ebn6Onq8vP09fb3+Pn6/9oADAMBAAIRAxEAPwDiKKKK9g8s/9k=';
const computerFrame = (extra = {}) => ({sessionId, generation: 1, sequence: 1, active: true,
  status: 'LIVE', reason: 'Gerätebild verfügbar', taskId: 'synthetic-task', captureSession: 'synthetic-capture',
  sourceEpoch: 1, ageMs: 20, staleAfterMs: 1500, frameDataUri, width: 4, height: 3, ...extra});
const speechFrame = (extra = {}) => ({sessionId, generation: 1, sequence: 1, active: true,
  utteranceId: 'synthetic-utterance', viseme: 'AA', weight: .7, jawOpen: .45,
  durationMs: 100, source: 'PCM_ENVELOPE', ...extra});
const acknowledgements = f => f.navigations.filter(url => url.pathname === '/frame-presented');
const invalidations = f => f.navigations.filter(url => url.pathname === '/frame-hidden');

async function fixture({realAvatar = false} = {}) {
  const dom = new JSDOM(readFileSync(resolve(assetRoot, 'index.android.html'), 'utf8'), {
    url: 'https://appassets.androidplatform.net/assets/continuum-command03/index.android.html',
    pretendToBeVisual: true, runScripts: 'outside-only'
  });
  const window = dom.window, navigations = [], scheduled = new Map(), scenes = [], decodings = [];
  const speechFrames = [], speechResults = [];
  let serial = 0, now = 100, speechClears = 0, occluded = false;
  const schedule = (fn, delay = 0, type = 'timer', repeat = false) => {
    scheduled.set(++serial, {fn, at: now + Math.max(0, delay), type, repeat, delay});return serial;
  };
  const cancel = id => scheduled.delete(id);
  const clock = {setTimeout: (fn, delay) => schedule(fn, delay), clearTimeout: cancel,
    setInterval: (fn, delay) => schedule(fn, delay, 'timer', true), clearInterval: cancel,
    requestAnimationFrame: fn => schedule(fn, 16, 'raf'), cancelAnimationFrame: cancel};
  // Only the Android navigation transport and clock/GPU seams are doubles.
  // Production bridge and original DOM/app/telemetry/motion scripts execute unchanged.
  const proxy = new Proxy(window, {
    get(target, key) {
      if (key === 'location') return {assign: url => navigations.push(new URL(url))};
      if (key === 'performance') return {now: () => now};
      if (key in clock) return clock[key];
      return Reflect.get(target, key);
    }
  });
  window.matchMedia = () => ({matches: false, addEventListener() {}, removeEventListener() {}});
  window.ContinuumScene = {setState(value) {scenes.push(value);}, setIntensity() {}, setQuality() {}, resize() {}};
  // The decoder and viewport are explicit browser seams. Tests never claim decoded GPU pixels.
  window.HTMLImageElement.prototype.decode = function() {
    return new Promise((resolve, reject) => decodings.push({image: this, resolve, reject}));
  };
  const originalBounds = window.HTMLImageElement.prototype.getBoundingClientRect;
  window.HTMLImageElement.prototype.getBoundingClientRect = function() {
    return this.classList.contains('native-computer-image') ?
      {x: 20, y: 100, left: 20, top: 100, right: 340, bottom: 340, width: 320, height: 240} :
      originalBounds.call(this);
  };
  window.document.elementFromPoint = () => occluded ? window.document.body :
    window.document.querySelector('.native-computer-image');
  if (realAvatar) window.HTMLCanvasElement.prototype.getContext = () => null;
  const context = vm.createContext({
    window: proxy, document: window.document, console, URL, URLSearchParams, TextEncoder,
    CustomEvent: window.CustomEvent, MouseEvent: window.MouseEvent, Event: window.Event,
    MutationObserver: window.MutationObserver, localStorage: window.localStorage,
    performance: {now: () => now}, getComputedStyle: window.getComputedStyle.bind(window),
    ...clock,
    HTMLElement: window.HTMLElement, Element: window.Element, navigator: window.navigator,
    Blob: window.Blob, File: window.File, FileReader: window.FileReader
  });
  for (const name of ['native-bridge.js', ...(realAvatar ? ['avatar3d.js'] : []), 'workspace-motion.js', 'app.js',
    'presence-instruments.js', 'telemetry.js', 'operations.js']) {
    vm.runInContext(readFileSync(resolve(assetRoot, name), 'utf8'), context, {filename: name});
  }
  const style = window.document.createElement('style');
  style.textContent = readFileSync(resolve(assetRoot, 'native-layer.css'), 'utf8');
  window.document.head.appendChild(style);
  await new Promise(resolve => setImmediate(resolve));
  const avatar = window.ContinuumAvatar;
  window.ContinuumAvatar = {...avatar,
    setSpeechFrame(frame) {
      speechFrames.push(JSON.parse(JSON.stringify(frame)));
      const result = avatar ? avatar.setSpeechFrame(frame) : true;
      speechResults.push(result);return result;
    },
    clearSpeech() {speechClears++;avatar?.clearSpeech();}
  };
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
  const flush = () => new Promise(resolve => setImmediate(resolve));
  return {dom, window, document: window.document, navigations, scenes, state, click, submit, input,
    speechFrames, speechResults, get speechClears() {return speechClears;}, decodings,
    advance(ms) {now += ms;},
    async decode(index = decodings.length - 1, succeeds = true) {
      const pending = decodings[index];assert.ok(pending, 'The production adapter requested image decoding');
      if (succeeds) {
        Object.defineProperties(pending.image, {
          naturalWidth: {value: pending.image.width || 4, configurable: true},
          naturalHeight: {value: pending.image.height || 3, configurable: true},
          complete: {value: true, configurable: true}
        });
        pending.resolve();
      } else pending.reject(new Error('Decoder refused the frame'));
      await flush();
    },
    async paint() {
      now += 16;
      for (const [id, entry] of [...scheduled]) if (entry.type === 'raf') {
        scheduled.delete(id);entry.fn(now);
      }
      await flush();
    },
    async timers(ms) {
      now += ms;
      for (const [id, entry] of [...scheduled]) if (entry.type === 'timer' && entry.at <= now) {
        scheduled.delete(id);entry.fn();
        if (entry.repeat) scheduled.set(id, {...entry, at: now + entry.delay});
      }
      await flush();
    },
    occlude(value) {occluded = value;window.dispatchEvent(new window.Event('scroll'));},
    close() {dom.window.close();}};
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

test('actual busy, listening and speaking transitions drive the original scene without demo timers', async () => {
  const f = await fixture();
  try {
    f.state();f.state({busy: true});assert.equal(f.scenes.at(-1), 'working');
    f.click(f.document.querySelector('[data-scene="speaking"]'));
    assert.equal(f.scenes.at(-1), 'working');
    f.state({listening: true});assert.equal(f.scenes.at(-1), 'listening');
    f.state({speaking: true});assert.equal(f.scenes.at(-1), 'speaking');
    f.state();assert.equal(f.scenes.at(-1), 'idle');
    assert.equal(f.document.querySelector('#orb-status').textContent, 'BEREIT');
    assert.equal(f.window.ContinuumOperations.getState(), null);
  } finally {f.close();}
});

test('telemetry history advances only for new actual measurements and preserves unavailable gaps', async () => {
  const f = await fixture();
  try {
    f.state({telemetryAtElapsedMs: 100, cpu: 10});
    const panel=f.document.querySelector('[data-telemetry-left] .ct-panel');
    assert.ok(panel, 'The original chart panel is retained');
    assert.equal(panel.dataset.samples, '1');
    f.state({telemetryAtElapsedMs: 100, busy: true});assert.equal(panel.dataset.samples, '1');
    f.state({telemetryAtElapsedMs: 5100, cpu: null});
    f.state({telemetryAtElapsedMs: 10100, cpu: 40});
    const trace=panel.querySelector('.ct-trace').getAttribute('d');
    assert.equal((trace.match(/M/g)||[]).length, 2, 'Unknown CPU sample splits the trace');
    assert.equal((trace.match(/L/g)||[]).length, 0);
    f.state({telemetryAtElapsedMs: 50100, cpu: 50});
    assert.equal((panel.querySelector('.ct-trace').getAttribute('d').match(/M/g)||[]).length, 3,
      'A long collection gap is never presented as continuous measured work');
  } finally {f.close();}
});

test('empty native history and native-ready captions replace demo content while retaining button icons', async () => {
  const f = await fixture();
  try {
    const button=f.document.querySelector('#hero-action'), icon=button.querySelector('svg');
    const badgeIndicator=f.document.querySelector('.preview-badge i');
    assert.ok(f.document.querySelector('#chat-list [data-chat]'), 'Original fixture has sample chats');
    f.state();
    assert.equal(f.document.querySelector('#chat-list').children.length, 0);
    assert.match(button.textContent, /Recherche öffnen/);assert.equal(button.querySelector('svg'), icon);
    assert.equal(f.document.querySelector('.preview-badge i'), badgeIndicator);
    assert.equal(f.document.querySelector('#workspace-footnote').textContent, 'Bereit für deinen Auftrag');
    assert.doesNotMatch(f.document.querySelector('.input-hint').textContent, /VORSCHAU/);
    assert.doesNotMatch(f.document.querySelector('.sidebar-note').textContent, /Beispielchats/);
  } finally {f.close();}
});

test('late message and document callbacks cannot cross conversation boundaries', async () => {
  const f = await fixture();
  try {
    f.state();f.window.ContinuumNative.message({sessionId, user: false, text: 'Private alte Antwort'});
    assert.match(f.document.querySelector('#native-live-panel').textContent, /Private alte Antwort/);
    f.state({sessionId: anotherSession});
    assert.equal(f.document.querySelector('#native-live-panel'), null);
    f.window.ContinuumNative.message({sessionId, user: false, text: 'Verspätete alte Antwort'});
    f.window.ContinuumNative.document({sessionId, name: 'Alt.pdf', pageCount: 1, textState: 'EXTRACTED', text: 'Alte Daten'});
    assert.equal(f.document.querySelector('#native-live-panel'), null);
    assert.equal(f.document.querySelector('#native-document-panel'), null);
  } finally {f.close();}
});

test('chunked transcripts preserve long model responses and user scroll nodes on status refresh', async () => {
  const f = await fixture();
  try {
    const response='Das ist der vollständige Text. '.repeat(450);
    f.state();
    f.window.ContinuumNative.transcript({sessionId, append: false, entries: [{user: true, text: 'Frage'}]});
    f.window.ContinuumNative.transcript({sessionId, append: true, entries: [{user: false, text: response}]});
    const row=f.document.querySelector('.native-assistant p');
    assert.equal(row.textContent,response);assert.ok(row.textContent.length>6000);
    f.state({cpu: 23});assert.equal(f.document.querySelector('.native-assistant p'),row,
      'A CPU refresh must not replace messages and lose selection or scroll state');
    assert.equal(f.document.querySelectorAll('.native-chat-line').length,2);
  } finally {f.close();}
});

test('native restore does not overwrite a local draft and oversize commands keep their synchronized draft', async () => {
  const f = await fixture();
  try {
    f.state({draft:{text:'Gespeicherter Entwurf',selectionStart:4,selectionEnd:8}});
    const field=f.document.querySelector('#command-input');
    assert.equal(field.value,'Gespeicherter Entwurf');assert.equal(field.selectionStart,4);assert.equal(field.selectionEnd,8);
    f.input('Meine neue Eingabe');
    f.state({draft:{text:'Alter Snapshot',selectionStart:0,selectionEnd:0}});
    assert.equal(field.value,'Meine neue Eingabe');
    const large='ö'.repeat(6000);f.input(large);f.submit();
    const draft=f.navigations.findLast(url=>url.pathname==='/draft');
    assert.equal(draft.searchParams.get('value'),large);
    assert.equal(f.navigations.filter(url=>url.pathname==='/command').length,0);
    assert.equal(field.value,large);
  } finally {f.close();}
});

test('selecting a stored conversation closes the existing mobile drawer without replacing its controls', async () => {
  const f = await fixture();
  try {
    f.state({chats:[{id:sessionId,title:'Gespeicherter Chat'}]});
    const toggle=f.document.querySelector('[data-action="toggle-chats"]');
    f.click(toggle);assert.equal(f.document.body.classList.contains('chats-open'),true);
    const entry=f.document.querySelector('[data-native-chat]');f.click(entry);
    assert.equal(f.document.body.classList.contains('chats-open'),false);
    assert.equal(toggle.getAttribute('aria-expanded'),'false');
    assert.equal(f.document.querySelector('[data-action="toggle-chats"]'),toggle);
    assert.equal(f.navigations.at(-1).pathname,'/select-chat');
    assert.equal(f.navigations.at(-1).searchParams.get('value'),sessionId);
  } finally {f.close();}
});

test('the original composer slot supports exact multiline paste, submit and native restore', async () => {
  const f = await fixture();
  try {
    const text='Erste Zeile\n'+ 'a'.repeat(3200)+'\nDritte Zeile\tmit Tab';
    f.state({draft:{text,selectionStart:3,selectionEnd:20}});
    const field=f.document.querySelector('#command-input');
    assert.equal(field.tagName,'TEXTAREA');assert.equal(field.rows,1);
    assert.equal(f.document.querySelectorAll('#command-input').length,1);
    assert.equal(field.parentElement.className,'command-field');
    assert.equal(field.value,text);assert.equal(field.selectionStart,3);assert.equal(field.selectionEnd,20);
    field.dispatchEvent(new f.window.Event('blur'));
    assert.equal(f.navigations.findLast(url=>url.pathname==='/draft').searchParams.get('value'),text);
    f.submit();const command=f.navigations.findLast(url=>url.pathname==='/command');
    assert.equal(command.searchParams.get('value'),text);
    assert.equal(field.value,text,'No newline or pasted text is lost before native acceptance');
    const next='Neue Frage\nZweite Zeile';f.input(next);
    f.window.ContinuumNative.commandResult({id:command.searchParams.get('id'),sessionId,accepted:true});
    assert.equal(field.value,next);
  } finally {f.close();}
});

test('typing before Android is ready is synchronized as soon as the first native session arrives', async () => {
  const f = await fixture();
  try {
    const text='Schon getippt\nvor der Verbindung';f.input(text);
    assert.equal(f.navigations.length,0);
    f.state({draft:{text:'Alter nativer Entwurf',selectionStart:0,selectionEnd:0}});
    const draft=f.navigations.findLast(url=>url.pathname==='/draft');
    assert.ok(draft);assert.equal(draft.searchParams.get('value'),text);
    assert.equal(draft.searchParams.get('sessionId'),sessionId);
    assert.equal(f.document.querySelector('#command-input').value,text);
  } finally {f.close();}
});

test('native draft revisions cannot restore consumed text or reset a newer local selection', async () => {
  const f = await fixture();
  try {
    f.state({draftRevision:1,draft:{text:'Wiederhergestellt',selectionStart:1,selectionEnd:1}});
    const field=f.document.querySelector('#command-input');field.setSelectionRange(5,5);
    f.state({draftRevision:1,draft:{text:'Wiederhergestellt',selectionStart:1,selectionEnd:1}});
    assert.equal(field.selectionStart,5,'Same revision must not rewind a selection');
    f.submit();const command=f.navigations.findLast(url=>url.pathname==='/command');
    f.state({commandPending:true,draftRevision:2,draft:{text:'',selectionStart:0,selectionEnd:0}});
    assert.equal(field.value,'Wiederhergestellt','A pending snapshot is not a send acknowledgement');
    f.window.ContinuumNative.commandResult({id:command.searchParams.get('id'),sessionId,accepted:true});
    assert.equal(field.value,'');
    f.state({draftRevision:1,draft:{text:'Wiederhergestellt',selectionStart:1,selectionEnd:1}});
    assert.equal(field.value,'');
    f.state({draftRevision:2,draft:{text:'Wiederhergestellt',selectionStart:1,selectionEnd:1}});
    assert.equal(field.value,'');
    f.state({draftRevision:3,draft:{text:'Neuer nativer Entwurf',selectionStart:2,selectionEnd:4}});
    assert.equal(field.value,'Neuer nativer Entwurf');assert.equal(field.selectionEnd,4);
  } finally {f.close();}
});

test('computer frames use the original window and acknowledge only decoded visible frames after its one flight', async () => {
  const f = await fixture();
  try {
    f.state();f.window.ContinuumNative.computer(computerFrame());
    const panel = f.document.querySelector('#native-computer-panel');
    assert.ok(panel?.classList.contains('projected-computer'));
    assert.ok(panel.querySelector('.op-browser-frame'));
    assert.equal(panel.querySelector('.native-computer-image'), null);
    assert.equal(acknowledgements(f).length, 0);
    const motion = f.window.ContinuumWorkspaceMotion.getStatus();
    assert.equal(motion.kind, 'open');assert.equal(motion.duration, 950);
    await f.decode();
    const image = panel.querySelector('.native-computer-image');
    assert.equal(image.getAttribute('src'), frameDataUri);
    assert.equal(acknowledgements(f).length, 0, 'Decode does not prove a visible paint');
    f.advance(950);await f.paint();await f.paint();
    assert.equal(acknowledgements(f).length, 0, 'The first RAF is still before its paint');
    await f.paint();
    assert.equal(acknowledgements(f).length, 1);
    const ack = acknowledgements(f)[0];
    assert.equal(ack.searchParams.get('sessionId'), sessionId);
    assert.equal(ack.searchParams.get('generation'), '1');assert.equal(ack.searchParams.get('sequence'), '1');
    f.window.ContinuumNative.computer(computerFrame({sequence: 2}));await f.decode();
    assert.equal(f.document.querySelector('#native-computer-panel'), panel);
    assert.equal(f.window.ContinuumWorkspaceMotion.getStatus().id, motion.id,
      'A subsequent frame never starts a second window animation');
    await f.paint();await f.paint();
    assert.equal(acknowledgements(f).length, 2);
    assert.equal(f.document.querySelectorAll('.native-computer-image').length, 1);
  } finally {f.close();}
});

test('late decode and frame replay cannot reopen a stopped or rebound computer source', async () => {
  const f = await fixture();
  try {
    f.state();f.window.ContinuumNative.computer(computerFrame());
    f.window.ContinuumNative.computer({sessionId, generation: 1, sequence: 2, active: false, status: 'ENDED'});
    await f.decode(0);f.advance(950);await f.paint();await f.paint();await f.paint();
    assert.equal(f.document.querySelector('#native-computer-panel'), null);
    assert.equal(acknowledgements(f).length, 0);
    f.window.ContinuumNative.computer(computerFrame({sequence: 3}));
    assert.equal(f.decodings.length, 1, 'A terminal generation cannot resume');
    f.window.ContinuumNative.computer(computerFrame({generation: 2, sequence: 1, sourceEpoch: 2}));
    f.window.ContinuumNative.computer(computerFrame({generation: 1, sequence: 99}));
    assert.equal(f.decodings.length, 2);
    await f.decode(1);
    assert.equal(f.document.querySelector('.native-computer-image').dataset.generation, '2');
    assert.ok(invalidations(f).length > 0);
  } finally {f.close();}
});

test('a computer image expires without new frames and unavailable status removes pixels immediately', async () => {
  const f = await fixture();
  try {
    f.state();f.window.ContinuumNative.computer(computerFrame());await f.decode();
    await f.timers(1501);
    assert.equal(f.document.querySelector('.native-computer-image'), null);
    assert.doesNotMatch(f.document.querySelector('#native-computer-panel .op-mode-label').textContent, /^LIVE$/);
    assert.ok(invalidations(f).length > 0);
    f.window.ContinuumNative.computer(computerFrame({sequence: 2}));await f.decode();
    f.window.ContinuumNative.computer({sessionId, generation: 1, sequence: 3,
      active: true, status: 'COVERED', reason: 'Quelle verdeckt'});
    assert.equal(f.document.querySelector('.native-computer-image'), null);
    assert.match(f.document.querySelector('#native-computer-panel').textContent, /Quelle verdeckt/);
  } finally {f.close();}
});

test('PDF and chat remain intact while a covered computer invalidates its previous render receipt', async () => {
  const f = await fixture();
  try {
    f.state();f.window.ContinuumNative.message({sessionId, user: false, text: 'Erhaltene Antwort'});
    f.window.ContinuumNative.computer(computerFrame());await f.decode();
    f.advance(950);await f.paint();await f.paint();await f.paint();
    assert.equal(acknowledgements(f).length, 1);
    f.window.ContinuumNative.document({sessionId, name: 'Echt.pdf', pageCount: 1,
      textState: 'EXTRACTED', text: 'Erhaltener PDF-Inhalt'});
    assert.ok(invalidations(f).length > 0, 'Opening a PDF revokes the old computer receipt immediately');
    f.window.ContinuumNative.computer(computerFrame({sequence: 2}));await f.decode();
    f.advance(950);await f.paint();await f.paint();await f.paint();
    assert.equal(acknowledgements(f).length, 1, 'A computer behind a PDF cannot be acknowledged');
    const workspace = f.document.querySelector('#workspace');
    assert.equal(workspace.classList.contains('native-document-active'), true);
    assert.equal(workspace.classList.contains('native-computer-active'), false);
    f.state({busy: true, cpu: 44});
    assert.equal(f.document.querySelector('.native-document-excerpt').textContent, 'Erhaltener PDF-Inhalt');
    f.window.ContinuumNative.computer({sessionId, generation: 1, sequence: 3, active: false, status: 'ENDED'});
    f.window.ContinuumNative.clearDocument();
    assert.match(f.document.querySelector('#native-live-panel').textContent, /Erhaltene Antwort/);
  } finally {f.close();}
});

test('invalid computer payloads and callbacks from an old conversation never become visible frames', async () => {
  const f = await fixture();
  try {
    f.state();
    for (const bad of [{width: 641}, {height: 0}, {ageMs: 1501}, {staleAfterMs: 1501},
      {frameDataUri: 'https://example.invalid/frame.jpg'}, {frameDataUri: frameDataUri + 'A'.repeat(174787)},
      {sequence: NaN}, {sessionId: anotherSession}, {sourceEpoch: -1}, {captureSession: 42}]) {
      f.window.ContinuumNative.computer(computerFrame(bad));
    }
    assert.equal(f.decodings.length, 0);
    f.window.ContinuumNative.computer(computerFrame());
    f.state({sessionId: anotherSession});await f.decode();
    assert.equal(f.document.querySelector('#native-computer-panel'), null);
    f.window.ContinuumNative.computer(computerFrame({sequence: 2}));
    f.state();f.window.ContinuumNative.computer(computerFrame({sequence: 3}));
    assert.equal(f.decodings.length, 1, 'Returning to the old session does not revive its retired generation');
    assert.equal(acknowledgements(f).length, 0);
  } finally {f.close();}
});

test('occlusion, page hiding and failed decoding never produce a computer render acknowledgement', async () => {
  const f = await fixture();
  try {
    f.state();f.window.ContinuumNative.computer(computerFrame());await f.decode();
    f.occlude(true);f.advance(950);await f.paint();await f.paint();await f.paint();
    assert.equal(acknowledgements(f).length, 0);
    assert.ok(invalidations(f).length > 0);
    f.window.ContinuumNative.computer(computerFrame({sequence: 2}));await f.decode(1, false);
    assert.equal(f.document.querySelector('.native-computer-image'), null);
    assert.equal(acknowledgements(f).length, 0);
    f.window.ContinuumNative.computer(computerFrame({sequence: 3}));
    Object.defineProperty(f.document, 'visibilityState', {value: 'hidden', configurable: true});
    f.document.dispatchEvent(new f.window.Event('visibilitychange'));
    await f.decode(2);f.advance(950);await f.paint();await f.paint();await f.paint();
    assert.equal(f.document.querySelector('.native-computer-image'), null);
    assert.equal(acknowledgements(f).length, 0);
  } finally {f.close();}
});

test('native speech frames reach the original viseme API with their actual source and expire to silence', async () => {
  const f = await fixture();
  try {
    f.state({speaking: true});
    f.window.ContinuumNative.speech(speechFrame());
    assert.deepEqual(f.speechFrames.at(-1), {viseme: 'AA', weight: .7, jawOpen: .45, durationMs: 100});
    assert.equal(f.document.querySelector('.avatar-crop').dataset.speechSource, 'PCM_ENVELOPE');
    f.window.ContinuumNative.speech(speechFrame({sequence: 2, viseme: 'PP', weight: .9, jawOpen: 0,
      source: 'WORD_RANGE_FALLBACK'}));
    assert.equal(f.speechFrames.at(-1).viseme, 'PP');
    assert.equal(f.document.querySelector('.avatar-crop').dataset.speechSource, 'WORD_RANGE_FALLBACK');
    const before = f.speechClears;await f.timers(101);
    assert.ok(f.speechClears > before, 'No new real pose means the mouth is cleared');
    assert.equal(f.document.querySelector('.avatar-crop').dataset.speechActive, 'false');
  } finally {f.close();}
});

test('speech rejects invalid morphs, late utterances, stopped generations and different sessions', async () => {
  const f = await fixture();
  try {
    f.state({speaking: true});
    for (const bad of [{viseme: 'UNKNOWN'}, {weight: NaN}, {weight: 1.1}, {jawOpen: -.1},
      {durationMs: 501}, {source: 'SIMULATED'}, {sessionId: anotherSession}]) {
      f.window.ContinuumNative.speech(speechFrame(bad));
    }
    assert.equal(f.speechFrames.length, 0);
    f.window.ContinuumNative.speech(speechFrame());
    f.window.ContinuumNative.speech(speechFrame());
    assert.equal(f.speechFrames.length, 1);
    f.window.ContinuumNative.speech({sessionId, generation: 1, sequence: 2, active: false});
    f.window.ContinuumNative.speech(speechFrame({sequence: 3}));
    assert.equal(f.speechFrames.length, 1);
    f.window.ContinuumNative.speech(speechFrame({generation: 2, utteranceId: 'new-utterance'}));
    f.window.ContinuumNative.speech(speechFrame({generation: 1, sequence: 99}));
    assert.equal(f.speechFrames.length, 2);
    f.state({sessionId: anotherSession});
    f.window.ContinuumNative.speech(speechFrame({generation: 2, sequence: 2, utteranceId: 'new-utterance'}));
    assert.equal(f.speechFrames.length, 2);
    assert.equal(f.document.querySelector('.avatar-crop').dataset.speechActive, 'false');
  } finally {f.close();}
});

test('the byte-locked bundled avatar accepts the native visemes through its public fallback-mode API', async () => {
  const f = await fixture({realAvatar: true});
  try {
    f.state({speaking: true});f.window.ContinuumNative.speech(speechFrame());
    assert.equal(f.speechResults.at(-1), true);
    assert.equal(f.window.ContinuumAvatar.getStatus().speechSource, 'external-viseme-events-only');
    assert.equal(f.window.ContinuumAvatar.getStatus().renderer, 'original-png');
    f.window.ContinuumNative.speech({sessionId, generation: 1, sequence: 2, active: false});
    assert.ok(f.speechClears > 0);
  } finally {f.close();}
});

test('covering a decoded image with a newer pending frame withdraws both exact receipts', async () => {
  const f = await fixture();
  try {
    f.state();f.window.ContinuumNative.computer(computerFrame());await f.decode();
    f.advance(950);await f.paint();await f.paint();await f.paint();
    assert.equal(acknowledgements(f).length, 1);
    f.window.ContinuumNative.computer(computerFrame({sequence: 2}));
    assert.equal(f.document.querySelector('.native-computer-image').dataset.sequence, '1',
      'Decoded old pixels remain until their replacement is decoded, without relabelling their receipt');
    f.window.ContinuumNative.document({sessionId, name: 'Vordergrund.pdf', pageCount: 1,
      textState: 'EXTRACTED', text: 'Die aktuelle Arbeitsfläche ist diese PDF.'});
    assert.deepEqual(invalidations(f).map(url => url.searchParams.get('sequence')), ['1','2']);
    await f.decode(1);await f.paint();await f.paint();
    assert.equal(acknowledgements(f).length, 1);
    assert.equal(f.document.querySelector('.native-computer-image'), null);
    assert.equal(f.document.querySelector('#workspace').classList.contains('native-document-active'), true);
  } finally {f.close();}
});

test('an expired pose cannot transfer its generation to another utterance or return after page hiding', async () => {
  const f = await fixture();
  try {
    f.state({speaking: true});f.window.ContinuumNative.speech(speechFrame());
    await f.timers(101);
    f.window.ContinuumNative.speech(speechFrame({sequence: 2, utteranceId: 'different-utterance'}));
    assert.equal(f.speechFrames.length, 1);
    f.window.ContinuumNative.speech(speechFrame({sequence: 2, source: 'TEXT_PACE_FALLBACK'}));
    assert.equal(f.speechFrames.length, 2, 'A fresh pose from the same utterance can resume after a pose gap');
    assert.equal(f.document.querySelector('.avatar-crop').dataset.speechSource, 'TEXT_PACE_FALLBACK');
    Object.defineProperty(f.document, 'visibilityState', {value: 'hidden', configurable: true});
    f.document.dispatchEvent(new f.window.Event('visibilitychange'));
    Object.defineProperty(f.document, 'visibilityState', {value: 'visible', configurable: true});
    f.document.dispatchEvent(new f.window.Event('visibilitychange'));
    f.window.ContinuumNative.speech(speechFrame({sequence: 3}));
    assert.equal(f.speechFrames.length, 2);
    f.window.ContinuumNative.speech(speechFrame({generation: 2, utteranceId: 'resumed-utterance'}));
    assert.equal(f.speechFrames.length, 3);
  } finally {f.close();}
});

test('native control acknowledgements admit real dictation and consumption without replacing newer local edits', async () => {
  const f = await fixture();
  try {
    controlState(f, {draft: {text: '', selectionStart: 0, selectionEnd: 0}});
    const field = f.input('Mein Entwurf');
    controlState(f, {draftRevision: 2, ackEditRevision: 1,
      draft: {text: 'Mein Entwurf mit Diktat', selectionStart: 22, selectionEnd: 22}});
    assert.equal(field.value, 'Mein Entwurf mit Diktat', 'A confirmed native dictation is a real newer edit');
    f.input('Meine jüngere Änderung');
    controlState(f, {draftRevision: 3, ackEditRevision: 1,
      draft: {text: '', selectionStart: 0, selectionEnd: 0}});
    assert.equal(field.value, 'Meine jüngere Änderung', 'An older acknowledgement cannot erase a newer local edit');
    controlState(f, {draftRevision: 4, ackEditRevision: 2,
      draft: {text: '', selectionStart: 0, selectionEnd: 0}});
    assert.equal(field.value, '', 'Actual native consumption clears the confirmed visible draft');
    const edits = f.navigations.filter(url => url.pathname === '/draft');
    assert.deepEqual(edits.map(url => url.searchParams.get('clientEditRevision')), ['1', '2']);
    for (const edit of edits) {
      assert.equal(edit.searchParams.get('pageId'), pageId);
      assert.equal(edit.searchParams.get('viewId'), viewId);
    }
  } finally {f.close();}
});

test('native control selection acknowledgements preserve a newer caret and never echo a restored draft', async () => {
  const f = await fixture();
  try {
    controlState(f, {draft: {text: 'Ein Text', selectionStart: 1, selectionEnd: 3}});
    const field = f.document.querySelector('#command-input');
    field.setSelectionRange(4, 7);field.dispatchEvent(new f.window.Event('select', {bubbles: true}));
    const edit = f.navigations.findLast(url => url.pathname === '/draft');
    assert.equal(edit.searchParams.get('clientEditRevision'), '1');
    controlState(f, {draftRevision: 2, ackEditRevision: 0,
      draft: {text: 'Ein Text', selectionStart: 1, selectionEnd: 3}});
    assert.equal(field.selectionStart, 4);assert.equal(field.selectionEnd, 7);
    controlState(f, {draftRevision: 3, ackEditRevision: 1,
      draft: {text: 'Ein neuer Text', selectionStart: 5, selectionEnd: 5}});
    assert.equal(field.value, 'Ein neuer Text');assert.equal(field.selectionStart, 5);
    field.dispatchEvent(new f.window.Event('select', {bubbles: true}));
    assert.equal(f.navigations.filter(url => url.pathname === '/draft').length, 1,
      'Applying native text/selection must not start an echo loop');
  } finally {f.close();}
});

test('native control view instances replace bot histories and reject old snapshots messages and command acknowledgements', async () => {
  const f = await fixture();
  try {
    controlState(f);
    f.window.ContinuumNative.transcript({...binding, entries: [{user: false, text: 'Zentraler Verlauf'}]});
    f.input('Alter Auftrag');f.submit();
    const command = f.navigations.findLast(url => url.pathname === '/command');
    const bot = {...binding, viewId: anotherView, viewRevision: 2};
    controlState(f, {...bot, viewKind: 'BOT', viewLabel: 'Atlas', draftRevision: 2,
      draft: {text: 'Bot-Entwurf', selectionStart: 11, selectionEnd: 11}});
    f.window.ContinuumNative.transcript({...bot, entries: [{user: false, text: 'Privater Bot-Verlauf'}]});
    assert.doesNotMatch(f.document.querySelector('#workspace').textContent, /Zentraler Verlauf/);
    f.window.ContinuumNative.message({...binding, user: false, text: 'Verspätete zentrale Nachricht'});
    f.window.ContinuumNative.commandResult({...binding, id: command.searchParams.get('id'), accepted: true});
    controlState(f, {draftRevision: 99, draft: {text: 'Veralteter Snapshot', selectionStart: 0, selectionEnd: 0}});
    assert.equal(f.document.querySelector('#command-input').value, 'Bot-Entwurf');
    assert.match(f.document.querySelector('#workspace').textContent, /Privater Bot-Verlauf/);
    assert.doesNotMatch(f.document.querySelector('#workspace').textContent, /Verspätete zentrale Nachricht/);
    assert.match(f.document.querySelector('#command-input').getAttribute('aria-label'), /Atlas/);
    controlState(f, {viewId: '44444444-4444-4444-8444-444444444444', viewRevision: 3,
      draftRevision: 3, draft: {text: '', selectionStart: 0, selectionEnd: 0}});
    f.window.ContinuumNative.message({...bot, user: false, text: 'Verspätete Bot-Nachricht'});
    assert.doesNotMatch(f.document.querySelector('#workspace').textContent, /Privater Bot-Verlauf|Verspätete Bot-Nachricht/);
  } finally {f.close();}
});

test('native control focus reaches the visible composer only for the current explicitly selected view', async () => {
  const f = await fixture();
  try {
    controlState(f);
    const field = f.document.querySelector('#command-input');
    assert.notEqual(f.document.activeElement, field);
    f.window.ContinuumNative.section({...binding, name: 'chat'});
    assert.equal(f.document.activeElement, field);
    field.blur();
    f.window.ContinuumNative.section({...binding, pageId: anotherSession, name: 'chat'});
    assert.notEqual(f.document.activeElement, field);
    controlState(f, {cpu: 22, draftRevision: 2});
    assert.notEqual(f.document.activeElement, field, 'Periodic status must never steal focus');
  } finally {f.close();}
});

test('native control microphone long press uses the existing native action once and cancellation suppresses it', async () => {
  const f = await fixture();
  try {
    controlState(f, {voiceMode: {active: true, paused: false}, longPressTimeoutMs: 500});
    const mic = f.document.querySelector('#listen-button');
    const pointer = type => {
      const event = new f.window.MouseEvent(type, {bubbles: true, cancelable: true, button: 0});
      Object.defineProperty(event, 'pointerId', {value: 1});mic.dispatchEvent(event);
    };
    pointer('pointerdown');await f.timers(501);pointer('pointerup');f.click(mic);
    const actions = f.navigations.filter(url => url.pathname === '/route');
    assert.equal(actions.length, 1);
    assert.equal(actions[0].searchParams.get('value'), 'voice-input-long-press');
    assert.equal(actions[0].searchParams.get('pageId'), pageId);
    pointer('pointerdown');pointer('pointercancel');await f.timers(501);
    assert.equal(f.navigations.filter(url => url.pathname === '/route').length, 1);
    pointer('pointerdown');f.window.dispatchEvent(new f.window.Event('blur'));await f.timers(501);
    assert.equal(f.navigations.filter(url => url.pathname === '/route').length, 1);
    assert.match(mic.getAttribute('aria-label'), /Gesprächsmodus/);
  } finally {f.close();}
});

test('native control source cards use native identifiers and cannot survive a view switch', async () => {
  const f = await fixture();
  try {
    controlState(f);
    f.window.ContinuumNative.message({...binding, user: false, text: 'Antwort mit Quelle'});
    f.window.ContinuumNative.sources({...binding, sourceSetId, items: [
      {id: sourceId, title: '<script>Quelle</script>', url: 'https://example.org/beleg'}]});
    const button = f.document.querySelector('[data-native-source]');assert.ok(button);
    assert.equal(button.textContent.includes('<script>Quelle</script>'), true);
    assert.equal(button.querySelector('script'), null);
    f.click(button);
    const action = f.navigations.at(-1);
    assert.equal(action.pathname, '/open-source');assert.equal(action.searchParams.get('value'), sourceId);
    assert.equal(action.searchParams.has('url'), false);
    assert.equal(action.searchParams.get('viewId'), viewId);
    controlState(f, {viewId: anotherView, viewRevision: 2});
    f.window.ContinuumNative.sources({...binding, sourceSetId: anotherSession, items: [
      {id: sourceId, title: 'Alte Quelle', url: 'https://example.org/alt'}]});
    assert.equal(f.document.querySelector('[data-native-source]'), null);
  } finally {f.close();}
});

test('native control PDF draft action identifies only the currently displayed document and honours native availability', async () => {
  const f = await fixture();
  try {
    controlState(f);
    const doc = {...binding, documentId, name: 'Echt.pdf', pageCount: 1,
      textState: 'EXTRACTED', text: 'Echter Dokumenttext', canDraft: true, draftStatus: 'Entwurf möglich'};
    f.window.ContinuumNative.document(doc);
    let draft = f.document.querySelector('[data-native-pdf="draft"]');assert.ok(draft);
    f.click(draft);
    assert.equal(f.navigations.at(-1).pathname, '/document-draft');
    assert.equal(f.navigations.at(-1).searchParams.get('value'), documentId);
    assert.equal(f.navigations.at(-1).searchParams.get('pageId'), pageId);
    f.window.ContinuumNative.document({...doc, documentId: anotherDocument, canDraft: false,
      draftStatus: 'Lokales Modell nicht geladen'});
    await f.timers(1000);await f.paint();
    draft = f.document.querySelector('[data-native-pdf="draft"]');
    assert.ok(!draft || draft.disabled);
    const count = f.navigations.length;if (draft) f.click(draft);
    assert.equal(f.navigations.length, count);
    assert.match(f.document.querySelector('#native-document-panel').textContent, /Lokales Modell nicht geladen/);
  } finally {f.close();}
});

// The final integration gate requires fresh JSON captured from the actual Kotlin
// Stage evaluateJavascript output. Its image decoder remains the explicit seam above.
if (process.env.COMMAND03_REQUIRE_NATIVE_PACKET === '1' || process.env.COMMAND03_NATIVE_COMPUTER_PACKET) {
  test('the actual Kotlin Stage computer JSON reaches the original window and its exact render acknowledgement', async () => {
    assert.ok(process.env.COMMAND03_NATIVE_COMPUTER_PACKET,
      'Final gate requires COMMAND03_NATIVE_COMPUTER_PACKET from the fresh Stage UI-test sentinel');
    const packet=JSON.parse(readFileSync(process.env.COMMAND03_NATIVE_COMPUTER_PACKET,'utf8'));
    assert.equal(typeof packet.captureSession,'string','The actual Kotlin Long must preserve its string wire type');
    const f = await fixture();
    try {
      f.state({sessionId:packet.sessionId});f.window.ContinuumNative.computer(packet);
      assert.equal(f.decodings.length,1,'The unchanged producer payload must pass the adapter contract');
      await f.decode();assert.equal(f.document.querySelector('.native-computer-image').src,packet.frameDataUri);
      f.advance(950);await f.paint();await f.paint();await f.paint();
      const ack=acknowledgements(f).at(-1);assert.ok(ack);
      assert.equal(ack.searchParams.get('sessionId'),packet.sessionId);
      assert.equal(ack.searchParams.get('generation'),String(packet.generation));
      assert.equal(ack.searchParams.get('sequence'),String(packet.sequence));
    } finally {f.close();}
  });
}
